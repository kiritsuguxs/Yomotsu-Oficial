package eu.kanade.translation.detection

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** Owns one bound worker per OCR session. Fatal native faults terminate only that worker. */
internal class DbnetClient(
    context: Context,
    private val serviceIntent: Intent = Intent(context, DbnetService::class.java),
) : DbnetDetectionClient {
    private val context = context.applicationContext
    private val requests = Mutex()
    private val stateLock = Any()
    private var connection: ServiceConnection? = null
    private var connected: CompletableDeferred<Messenger>? = null
    private var remote: Messenger? = null
    private var pending: DbnetReply? = null
    private var requestId = 0
    private val receiver = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            if (message.what == DbnetWire.RESULT) {
                synchronized(stateLock) {
                    if (message.arg1 == requestId) {
                        val result = try {
                            val bundle = message.data
                            val error = bundle.getString("error")
                            val workerTimingValues = bundle.getLongArray("workerTimings")
                            if (error != null) {
                                val timings = workerTimingValues?.let(DbnetWire::decodeWorkerTimings)
                                if (workerTimingValues != null && timings == null) {
                                    DetectionResult.Failure("Métricas DBNet inválidas")
                                } else {
                                    DetectionResult.Failure(error.take(200), timings)
                                }
                            } else {
                                val values = bundle.getFloatArray("regions")
                                if (values == null) {
                                    DetectionResult.Failure("Resposta DBNet vazia")
                                } else {
                                    val mask = bundle.getByteArray("mask")
                                    if (mask == null) {
                                        DetectionResult.Failure("Resposta DBNet sem máscara")
                                    } else if (workerTimingValues == null) {
                                        DetectionResult.Failure("Resposta DBNet sem métricas")
                                    } else {
                                        DbnetWire.decode(
                                            bundle.getInt("width"),
                                            bundle.getInt("height"),
                                            values,
                                            bundle.getInt("maskWidth"),
                                            bundle.getInt("maskHeight"),
                                            bundle.getInt("maskInputWidth"),
                                            bundle.getInt("maskInputHeight"),
                                            bundle.getFloat("maskRatio"),
                                            mask,
                                            workerTimingValues,
                                        )
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            DetectionResult.Failure("Resposta DBNet inválida")
                        }
                        pending?.complete(result)
                    }
                }
            }
            true
        },
    )

    override suspend fun detect(file: File): DetectionResult = requests.withLock {
        val reply = DbnetReply()
        synchronized(stateLock) {
            pending = reply
            requestId++
        }
        try {
            val messenger = withTimeoutOrNull(15_000) { connect().await() }
                ?: return@withLock DetectionResult.Failure("Tempo limite ao conectar DBNet").also { close() }
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                val message = Message.obtain(null, DbnetWire.DETECT).apply {
                    arg1 = synchronized(stateLock) { requestId }
                    replyTo = receiver
                    data = Bundle().apply { putParcelable("image", fd) }
                }
                messenger.send(message)
            }
            reply.await(120_000).also { if (it is DetectionResult.Failure) close() }
        } catch (cancelled: CancellationException) {
            close()
            throw cancelled
        } catch (error: Exception) {
            close()
            DetectionResult.Failure("Falha de comunicação DBNet: ${error.javaClass.simpleName}")
        } finally {
            synchronized(stateLock) { if (pending === reply) pending = null }
        }
    }

    private fun connect(): CompletableDeferred<Messenger> = synchronized(stateLock) {
        connected?.let { return@synchronized it }
        val ready = CompletableDeferred<Messenger>()
        connected = ready
        val callback = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                synchronized(stateLock) {
                    if (connection !== this) return
                    try {
                        service.linkToDeath({ died() }, 0)
                        val messenger = Messenger(service)
                        remote = messenger
                        ready.complete(messenger)
                    } catch (error: Exception) {
                        ready.completeExceptionally(error)
                        died()
                    }
                }
            }
            private fun died() {
                synchronized(stateLock) {
                    if (connection !== this) return
                    pending?.complete(DetectionResult.Failure("Processo DBNet encerrado; OCR original preservado"))
                    ready.completeExceptionally(IllegalStateException("Processo DBNet encerrado"))
                    remote = null
                }
            }
            override fun onServiceDisconnected(name: ComponentName) = died()
            override fun onBindingDied(name: ComponentName) = died()
            override fun onNullBinding(name: ComponentName) = died()
        }
        connection = callback
        if (!context.bindService(serviceIntent, callback, Context.BIND_AUTO_CREATE)) {
            connection = null
            ready.completeExceptionally(IllegalStateException("Serviço DBNet indisponível"))
        }
        ready
    }

    override fun close() {
        synchronized(stateLock) {
            pending?.complete(DetectionResult.Failure("Sessão DBNet encerrada"))
            connected?.cancel()
            connected = null
            try {
                remote?.send(Message.obtain(null, DbnetWire.STOP).apply { replyTo = receiver })
            } catch (
                _: Exception,
            ) { }
            remote = null
            val bound = connection
            connection = null
            if (bound != null) {
                try {
                    context.unbindService(bound)
                } catch (_: IllegalArgumentException) { }
            }
        }
    }
}
