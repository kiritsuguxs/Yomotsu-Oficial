package tachiyomi.domain.telegram

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class TelegramPreferences(
    preferenceStore: PreferenceStore,
) {
    val enableTelegramCloud: Preference<Boolean> = preferenceStore.getBoolean("pref_enable_telegram_cloud", false)
    
    val botToken: Preference<String> = preferenceStore.getString("pref_telegram_bot_token", "")
    
    val chatId: Preference<String> = preferenceStore.getString("pref_telegram_chat_id", "")
    
    val deleteLocalAfterUpload: Preference<Boolean> = preferenceStore.getBoolean("pref_telegram_delete_after_upload", false)
}
