package eu.kanade.translation.detection

import android.content.Context
import android.os.Build
import android.os.Process
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.translation.recognizer.MlKitOcrEngine
import eu.kanade.translation.recognizer.OcrEngine
import eu.kanade.translation.recognizer.TextRecognizerLanguage

internal fun createExperimentalDbnetOcrEngine(
    context: Context,
    existing: OcrEngine,
    enabled: () -> Boolean,
): ExperimentalDbnetOcrEngine {
    val applicationContext = context.applicationContext
    return ExperimentalDbnetOcrEngine(
        context = applicationContext,
        existing = existing,
        enabled = enabled,
        deviceSupported = { Process.is64Bit() && "arm64-v8a" in Build.SUPPORTED_ABIS },
        createClient = { DbnetClient(it) },
        createOwnedMlKit = { MlKitOcrEngine(applicationContext, TextRecognizerLanguage.ENGLISH) },
        notify = { message -> DbnetNotification.post { applicationContext.toast(message) } },
        emitDiagnostic = { diagnostic -> android.util.Log.i("YomotsuDBNet", diagnostic) },
    )
}
