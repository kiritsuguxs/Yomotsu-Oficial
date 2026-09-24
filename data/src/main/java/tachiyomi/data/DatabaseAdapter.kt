package tachiyomi.data

import app.cash.sqldelight.ColumnAdapter
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.Date

object DateColumnAdapter : ColumnAdapter<Date, Long> {
    override fun decode(databaseValue: Long): Date = Date(databaseValue)
    override fun encode(value: Date): Long = value.time
}

private const val LIST_OF_STRINGS_SEPARATOR = ", "
object StringListColumnAdapter : ColumnAdapter<List<String>, String> {
    override fun decode(databaseValue: String) = if (databaseValue.isEmpty()) {
        emptyList()
    } else {
        databaseValue.split(LIST_OF_STRINGS_SEPARATOR)
    }
    override fun encode(value: List<String>) = value.joinToString(
        separator = LIST_OF_STRINGS_SEPARATOR,
    )
}

object UpdateStrategyColumnAdapter : ColumnAdapter<UpdateStrategy, Long> {
    override fun decode(databaseValue: Long): UpdateStrategy =
        UpdateStrategy.entries.getOrElse(databaseValue.toInt()) { UpdateStrategy.ALWAYS_UPDATE }

    override fun encode(value: UpdateStrategy): Long = value.ordinal.toLong()
}

object AnimeUpdateStrategyColumnAdapter : ColumnAdapter<eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy, Long> {
    override fun decode(databaseValue: Long): eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy =
        eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy.entries.getOrElse(databaseValue.toInt()) {
            eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy.ALWAYS_UPDATE
        }

    override fun encode(value: eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy): Long = value.ordinal.toLong()
}

object FetchTypeColumnAdapter : ColumnAdapter<eu.kanade.tachiyomi.animesource.model.FetchType, Long> {
    override fun decode(databaseValue: Long): eu.kanade.tachiyomi.animesource.model.FetchType =
        eu.kanade.tachiyomi.animesource.model.FetchType.entries.getOrElse(databaseValue.toInt()) {
            eu.kanade.tachiyomi.animesource.model.FetchType.Episodes
        }

    override fun encode(value: eu.kanade.tachiyomi.animesource.model.FetchType): Long = value.ordinal.toLong()
}

object MemoColumnAdapter : ColumnAdapter<JsonObject, ByteArray> {
    override fun decode(databaseValue: ByteArray): JsonObject {
        return Json.decodeFromString<JsonObject>(databaseValue.decodeToString())
    }

    override fun encode(value: JsonObject): ByteArray {
        return value.toString().encodeToByteArray()
    }
}
