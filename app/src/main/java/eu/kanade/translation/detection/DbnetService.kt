package eu.kanade.translation.detection

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.Process
import java.io.File

/** Non-exported, separate-process detector. No chapter/database/cache/translator access. */
class DbnetService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var worker: Handler
    private var session: DbnetSession? = null
    private var busy = false
    private val lease = DbnetLease<IBinder>()
    private val main = Handler(Looper.getMainLooper())
    private val messenger = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            when (message.what) {
                DbnetWire.STOP -> if (lease.ownedBy(message.replyTo?.binder)) terminate()
                DbnetWire.DETECT -> {
                    @Suppress("DEPRECATION")
                    val image = message.data.getParcelable<ParcelFileDescriptor>("image")
                    val replyTo = message.replyTo
                    val id = message.arg1
                    if (image == null || replyTo == null || busy || !lease.claim(replyTo.binder)) {
                        image?.close()
                        replyTo?.let { reply(it, id, DetectionResult.Failure("Solicitação DBNet inválida ou ocupada")) }
                    } else {
                        busy = true
                        worker.post {
                            val timings = DbnetWorkerTimingRecorder()
                            val result = image.use { fd ->
                                try {
                                    detect(fd, timings)
                                } catch (error: Throwable) {
                                    DetectionResult.Failure(
                                        "Detector DBNet: ${error.javaClass.simpleName}",
                                        timings.snapshot(),
                                    )
                                }
                            }
                            main.post {
                                busy = false
                                reply(replyTo, id, result)
                            }
                        }
                    }
                }
            }
            true
        },
    )

    override fun onCreate() {
        super.onCreate()
        check(DbnetProcess.isWorker(this)) { "DBNet must run outside the reader process" }
        thread = HandlerThread("DBNet serial inference").apply { start() }
        worker = Handler(thread.looper)
    }

    override fun onBind(intent: Intent): IBinder = messenger.binder

    private fun detect(fd: ParcelFileDescriptor, timings: DbnetWorkerTimingRecorder): DetectionResult {
        val prepared = timings.measure(DbnetWorkerStage.PREPARATION) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFileDescriptor(fd.fileDescriptor, null, bounds)
            val plan = DbnetResizePlan.create(bounds.outWidth, bounds.outHeight)
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = DbnetPixels.sampleSize(plan.originalWidth, plan.originalHeight, 2048)
            }
            val bitmap = checkNotNull(BitmapFactory.decodeFileDescriptor(fd.fileDescriptor, null, options)) {
                "Imagem DBNet inválida"
            }
            val canvas = Bitmap.createBitmap(plan.inputWidth, plan.inputHeight, Bitmap.Config.ARGB_8888)
            val pixels = try {
                val scaled = Bitmap.createScaledBitmap(bitmap, plan.resizedWidth, plan.resizedHeight, true)
                try {
                    Canvas(canvas).drawBitmap(scaled, 0f, 0f, null)
                } finally {
                    if (scaled !== bitmap) scaled.recycle()
                }
                IntArray(plan.inputWidth * plan.inputHeight).also {
                    canvas.getPixels(it, 0, plan.inputWidth, 0, 0, plan.inputWidth, plan.inputHeight)
                }
            } finally {
                bitmap.recycle()
                canvas.recycle()
            }
            val models = File(filesDir, "models/dbnet-v3")
            val engine = session ?: DbnetSession(
                DbnetNativeBackend,
                File(models, "dbnet_detect.ncnn.param").absolutePath,
                File(models, "dbnet_detect.ncnn.bin").absolutePath,
            ).also { session = it }
            PreparedDetection(engine, DbnetPixels.toChw(pixels), plan)
        }
        val output = timings.measure(DbnetWorkerStage.INFERENCE) {
            prepared.engine.infer(prepared.chw, prepared.plan.inputWidth, prepared.plan.inputHeight)
        }
        val result = timings.measure(DbnetWorkerStage.POSTPROCESS) {
            val shape = output.dimensions
            DbnetPostprocessor.process(
                output.db,
                shape[0],
                shape[1],
                shape[2],
                shape[3],
                shape[4],
                shape[5],
                output.mask,
                prepared.plan,
            )
        }
        if (result is DetectionResult.Success) {
            val shape = output.dimensions
            timings.shape(
                DbnetWorkerShape(
                    inputWidth = prepared.plan.inputWidth,
                    inputHeight = prepared.plan.inputHeight,
                    dbWidth = shape[2],
                    dbHeight = shape[1],
                    maskWidth = shape[5],
                    maskHeight = shape[4],
                ),
            )
        }
        val measured = timings.snapshot()
        return when (result) {
            is DetectionResult.Success -> result.copy(workerTimings = measured)
            is DetectionResult.Failure -> result.copy(workerTimings = measured)
        }
    }

    private fun reply(to: Messenger, id: Int, result: DetectionResult) {
        val response = Message.obtain(null, DbnetWire.RESULT).apply {
            arg1 = id
            data = Bundle().apply {
                result.workerTimings?.let { putLongArray("workerTimings", DbnetWire.encodeWorkerTimings(it)) }
                when (result) {
                    is DetectionResult.Failure -> putString("error", result.reason.take(200))
                    is DetectionResult.Success -> {
                        putInt("width", result.width)
                        putInt("height", result.height)
                        putFloatArray("regions", DbnetWire.encode(result))
                        putInt("maskWidth", result.mask.width)
                        putInt("maskHeight", result.mask.height)
                        putInt("maskInputWidth", result.mask.inputWidth)
                        putInt("maskInputHeight", result.mask.inputHeight)
                        putFloat("maskRatio", result.mask.ratio)
                        putByteArray("mask", DbnetWire.encodeMask(result.mask))
                    }
                }
            }
        }
        try {
            to.send(response)
        } catch (_: Exception) {
            terminate()
        }
    }

    private fun terminate() {
        // Killing this disposable worker also interrupts a stuck native call and reclaims weights.
        // Never release a handle concurrently with a running native call in another thread.
        if (DbnetProcess.isWorker(this)) Process.killProcess(Process.myPid())
    }

    override fun onDestroy() {
        super.onDestroy()
        terminate()
    }

    private data class PreparedDetection(
        val engine: DbnetSession,
        val chw: FloatArray,
        val plan: DbnetResizePlan,
    )
}
