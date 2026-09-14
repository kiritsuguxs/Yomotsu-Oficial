package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import tachiyomi.domain.telegram.TelegramPreferences
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsTelegramCloudScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_telegram_cloud // We will add this string resource

    @Composable
    override fun getPreferences(): List<Preference> {
        val preferences = remember { Injekt.get<TelegramPreferences>() }

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                pref = preferences.enableTelegramCloud,
                title = "Ativar Nuvem Telegram",
                subtitle = "Enviar capítulos baixados automaticamente para o Telegram",
            ),
            Preference.PreferenceItem.EditTextPreference(
                pref = preferences.botToken,
                title = "Bot Token",
                subtitle = "Token gerado pelo @BotFather",
            ),
            Preference.PreferenceItem.EditTextPreference(
                pref = preferences.chatId,
                title = "ID do Chat / Canal",
                subtitle = "ID do canal privado (ex: -1001234567890) ou chat para onde enviar",
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = preferences.deleteLocalAfterUpload,
                title = "Apagar arquivo local após upload",
                subtitle = "Libera espaço no dispositivo assim que o capítulo for salvo na nuvem",
            )
        )
    }
}
