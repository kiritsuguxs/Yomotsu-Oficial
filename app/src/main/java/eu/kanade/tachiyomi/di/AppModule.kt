package eu.kanade.tachiyomi.di

import android.app.Application
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.db.SqlDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import com.eygraber.sqldelight.androidx.driver.FileProvider
import eu.kanade.domain.track.store.DelayedTrackingStore
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.AndroidSourceManager
import eu.kanade.translation.TranslationManager
import eu.kanade.translation.data.TranslationProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.storage.AndroidStorageFolderProvider
import tachiyomi.data.Chapters
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.History
import tachiyomi.data.Mangas
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.source.local.image.LocalCoverManager
import tachiyomi.source.local.io.LocalSourceFileSystem
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.lang.ref.WeakReference

private val lock = Any()

class AppModule(val app: Application) : InjektModule {

    private var sqlDriverRef: WeakReference<SqlDriver>? = null

    override fun InjektRegistrar.registerInjectables() {
        addSingleton(app)
        addSingleton<Context>(app)

        addSingletonFactory<SqlDriver> {
            synchronized(lock) {
                sqlDriverRef?.get()?.let { return@synchronized it }

                AndroidxSqliteDriver(
                    driver = BundledSQLiteDriver(),
                    databaseType = AndroidxSqliteDatabaseType.FileProvider(app, "tachiyomi.db"),
                    schema = Database.Schema,
                    configuration = AndroidxSqliteConfiguration(
                        isForeignKeyConstraintsEnabled = true,
                    ),
                )
                    .also { sqlDriverRef = WeakReference(it) }
            }
        }
        addSingletonFactory {
            Database(
                driver = get(),
                historyAdapter = History.Adapter(
                    last_readAdapter = DateColumnAdapter,
                ),
                mangasAdapter = Mangas.Adapter(
                    genreAdapter = StringListColumnAdapter,
                    update_strategyAdapter = UpdateStrategyColumnAdapter,
                    memoAdapter = MemoColumnAdapter,
                ),
                chaptersAdapter = Chapters.Adapter(
                    memoAdapter = MemoColumnAdapter,
                ),
            )
        }

        val sqlDriverAnime = AndroidxSqliteDriver(
            driver = BundledSQLiteDriver(),
            databaseType = AndroidxSqliteDatabaseType.FileProvider(app, "yomotsu.anime.db"),
            schema = tachiyomi.mi.data.AnimeDatabase.Schema,
            configuration = AndroidxSqliteConfiguration(
                isForeignKeyConstraintsEnabled = true,
            ),
        )

        addSingletonFactory {
            tachiyomi.mi.data.AnimeDatabase(
                driver = sqlDriverAnime,
                animehistoryAdapter = dataanime.Animehistory.Adapter(
                    last_seenAdapter = DateColumnAdapter,
                ),
                animesAdapter = dataanime.Animes.Adapter(
                    genreAdapter = StringListColumnAdapter,
                    update_strategyAdapter = tachiyomi.data.AnimeUpdateStrategyColumnAdapter,
                    fetch_typeAdapter = tachiyomi.data.FetchTypeColumnAdapter,
                    memoAdapter = MemoColumnAdapter,
                ),
                episodesAdapter = dataanime.Episodes.Adapter(
                    memoAdapter = MemoColumnAdapter,
                ),
            )
        }

        addSingletonFactory<tachiyomi.data.handlers.anime.AnimeDatabaseHandler> {
            tachiyomi.data.handlers.anime.AndroidAnimeDatabaseHandler(get(), sqlDriverAnime)
        }
        addSingletonFactory { eu.kanade.tachiyomi.data.cache.AnimeCoverCache(app) }
        addSingletonFactory { eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache(app) }
        addSingletonFactory<tachiyomi.domain.source.anime.service.AnimeSourceManager> {
            eu.kanade.tachiyomi.source.anime.AndroidAnimeSourceManager(app, get(), get())
        }
        addSingletonFactory { eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager(app) }
        addSingletonFactory { eu.kanade.tachiyomi.data.download.anime.AnimeDownloadProvider(app) }
        addSingletonFactory { eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager(app) }
        addSingletonFactory { eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache(app) }

        addSingletonFactory {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }
        addSingletonFactory<XML> {
            XML.v1 {
                policy {
                    ignoreUnknownChildren()
                    autoPolymorphic = true
                }
                xmlDeclMode = XmlDeclMode.Charset
                xmlVersion = XmlVersion.XML10
                setIndent(2)
            }
        }
        addSingletonFactory<ProtoBuf> {
            ProtoBuf
        }

        addSingletonFactory { ChapterCache(app, get()) }
        addSingletonFactory { CoverCache(app) }

        addSingletonFactory { NetworkHelper(app, get()) }
        addSingletonFactory { JavaScriptEngine(app) }

        addSingletonFactory<SourceManager> { AndroidSourceManager(app, get(), get()) }
        addSingletonFactory { ExtensionManager(app) }
        addSingletonFactory { eu.kanade.tachiyomi.extension.novel.NovelExtensionManager(app, get(), get(), get()) }

        addSingletonFactory { DownloadProvider(app) }
        addSingletonFactory { DownloadManager(app) }
        addSingletonFactory { DownloadCache(app) }

        //TachiyomiAT
        addSingletonFactory { TranslationProvider(app) }
        addSingletonFactory { TranslationManager(app) }


        addSingletonFactory { TrackerManager() }
        addSingletonFactory { DelayedTrackingStore(app) }
        addSingletonFactory { eu.kanade.domain.track.anime.store.DelayedAnimeTrackingStore(app) }
        addSingletonFactory { aniyomi.core.common.torrent.TorrentServerApi(get(), get()) }
        addSingletonFactory { aniyomi.core.common.torrent.TorrentServerUtils(get(), get()) }
        addSingletonFactory { eu.kanade.tachiyomi.ui.player.utils.TrackSelect(get(), get()) }

        addSingletonFactory { ImageSaver(app) }

        addSingletonFactory { AndroidStorageFolderProvider(app) }
        addSingletonFactory { LocalSourceFileSystem(get()) }
        addSingletonFactory { LocalCoverManager(app, get()) }
        addSingletonFactory { StorageManager(app, get()) }
        addSingletonFactory { eu.kanade.tachiyomi.data.telegram.TelegramCloudManager(app) }

        // Asynchronously init expensive components for a faster cold start
        ContextCompat.getMainExecutor(app).execute {
            get<NetworkHelper>()

            get<SourceManager>()

            get<Database>()

            get<DownloadManager>()
        }
    }
}
