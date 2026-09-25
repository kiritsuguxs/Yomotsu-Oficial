package eu.kanade.domain.items.episode.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.items.episode.repository.EpisodeRepository

class SetSeenStatus(
    private val episodeRepository: EpisodeRepository,
) {
    sealed interface Result {
        data object Success : Result
        data object NoEpisodes : Result
        data class InternalError(val error: Throwable) : Result
    }

    suspend fun await(seen: Boolean, vararg episodes: Episode): Result = withNonCancellableContext {
        val episodesToUpdate = episodes.filter {
            when (seen) {
                true -> !it.seen
                false -> it.seen || it.lastSecondSeen > 0
            }
        }
        if (episodesToUpdate.isEmpty()) {
            return@withNonCancellableContext Result.NoEpisodes
        }

        try {
            episodeRepository.updateAll(
                episodesToUpdate.map {
                    EpisodeUpdate(
                        seen = seen,
                        lastSecondSeen = if (!seen) 0 else null,
                        id = it.id,
                    )
                },
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }

        Result.Success
    }
}
