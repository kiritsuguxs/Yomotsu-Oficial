package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.Preference
import tachiyomi.domain.telegram.TelegramPreferences
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsTelegramCloudScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_telegram_cloud

    @Composable
    override fun getPreferences(): List<Preference> {
        val preferences = remember { Injekt.get<TelegramPreferences>() }
        val navigator = LocalNavigator.currentOrThrow

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = preferences.enableTelegramCloud,
                title = "Ativar Nuvem Telegram",
                subtitle = "Enviar capítulos baixados automaticamente para o Telegram",
            ),
            Preference.PreferenceItem.EditTextPreference(
                preference = preferences.botToken,
                title = "Bot Token",
                subtitle = "Token gerado pelo @BotFather",
            ),
            Preference.PreferenceItem.EditTextPreference(
                preference = preferences.chatId,
                title = "ID do Chat / Canal",
                subtitle = "ID do canal privado (ex: -1001234567890) ou chat para onde enviar",
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = preferences.deleteLocalAfterUpload,
                title = "Apagar arquivo local após upload",
                subtitle = "Libera espaço no dispositivo assim que o capítulo for salvo na nuvem",
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = preferences.restoreToLocalSource,
                title = "Salvar downloads na Fonte Local",
                subtitle = "Ao restaurar da nuvem, salva direto na pasta da Fonte Local com capa e organização",
            ),
            Preference.PreferenceItem.TextPreference(
                title = "Gerenciador de Obras da Nuvem",
                subtitle = "Ver lista de todas as obras salvas no Telegram e baixar em lote para a Fonte Local",
                onClick = {
                    navigator.push(TelegramCloudManagerScreen())
                },
            ),
        )
    }
}
