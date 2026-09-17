package eu.kanade.tachiyomi.data.profile

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ProfilePreferences(context: Application = Injekt.get()) {
    private val prefs: SharedPreferences = context.getSharedPreferences("yomotsu_profile_prefs", Context.MODE_PRIVATE)

    fun getEquippedTitleId(): String? = prefs.getString("equipped_title_id", null)
    fun setEquippedTitleId(id: String) = prefs.edit().putString("equipped_title_id", id).apply()

    fun getBannerUri(): String? = prefs.getString("banner_uri", null)
    fun setBannerUri(uri: String?) = prefs.edit().putString("banner_uri", uri).apply()

    fun getAvatarUri(): String? = prefs.getString("avatar_uri", null)
    fun setAvatarUri(uri: String?) = prefs.edit().putString("avatar_uri", uri).apply()
}
