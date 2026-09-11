package eu.kanade.tachiyomi.extension.pt.nhentaibr

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import kotlin.system.exitProcess

class DeepLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent?.data?.path
        if (!path.isNullOrEmpty() && path != "/") {
            val intent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", path)
                putExtra("filter", packageName)
            }
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                // Ignore
            }
        }
        finish()
        exitProcess(0)
    }
}
