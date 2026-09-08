package eu.kanade.tachiyomi.extension.pt.projectnox

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Activity to handle deep links from Project Nox website.
 *
 * Accepts URLs like:
 * - https://manga.project-nox-awerkori.workers.dev/obra/{slug}
 *
 * And redirects them to the Mihon/Tachiyomi/Yomotsu app to open the manga details.
 */
class ProjectNoxUrlActivity : Activity() {

    private val tag = "ProjectNoxUrlActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pathSegments = intent?.data?.pathSegments

        if (pathSegments != null && pathSegments.size >= 2) {
            val slug = pathSegments[1]
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "${ProjectNox.SEARCH_PREFIX}$slug")
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e(tag, "Could not start activity", e)
            }
        } else {
            Log.e(tag, "Could not parse URI from intent: ${intent?.data}")
        }

        finish()
        exitProcess(0)
    }
}
