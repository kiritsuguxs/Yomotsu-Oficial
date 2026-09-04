package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext

class TranslationFontMigration : Migration {
    override val version: Float = 76f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val preferenceStore = migrationContext.get<PreferenceStore>() ?: return@withIOContext false
        migrate(preferenceStore.getInt("translation_font", 0))
        true
    }

    internal fun migrate(preference: Preference<Int>) {
        if (preference.get() !in SUPPORTED_VALUES) {
            preference.set(0)
        }
    }

    private companion object {
        val SUPPORTED_VALUES = setOf(0, 1, 3, 4, 5)
    }
}
