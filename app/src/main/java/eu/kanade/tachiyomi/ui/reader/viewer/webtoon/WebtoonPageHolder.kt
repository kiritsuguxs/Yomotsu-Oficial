package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.content.res.Resources
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import eu.kanade.tachiyomi.databinding.ReaderErrorBinding
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressIndicator
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.translation.data.TranslationFont
import eu.kanade.translation.model.ManualTranslationPosition
import eu.kanade.translation.presentation.WebtoonTranslationsView
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import okio.Buffer
import okio.BufferedSource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.translation.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Holder of the webtoon reader for a single page of a chapter. */
class WebtoonPageHolder(
    private val frame: ReaderPageImageView,
    viewer: WebtoonViewer,
    translationPreferences: TranslationPreferences = Injekt.get(),
    private val font: TranslationFont = TranslationFont.fromPref(translationPreferences.translationFont()),
    readerPreferences: ReaderPreferences = Injekt.get(),
) : WebtoonBaseHolder(frame, viewer) {

    private var showTranslations = true
    private var translationsView: WebtoonTranslationsView? = null
    private var translationPageIndex: Int? = null

    private val progressIndicator = createProgressIndicator()
    private lateinit var progressContainer: ViewGroup
    private var errorLayout: ReaderErrorBinding? = null
    private val parentHeight get() = viewer.recycler.height
    private var page: ReaderPage? = null
    private val scope = MainScope()
    private var loadJob: Job? = null

    init {
        refreshLayoutParams()
        frame.onImageLoaded = { onImageDecoded() }
        frame.onImageLoadError = { error -> setError(error) }
        frame.onScaleChanged = { viewer.activity.hideMenu() }

        showTranslations = readerPreferences.showTranslations.get()
        readerPreferences.showTranslations.changes().onEach {
            showTranslations = it
            if (it) translationsView?.show() else translationsView?.hide()
        }.launchIn(scope)
    }

    fun bind(page: ReaderPage) {
        // A WebtoonPageHolder is recycled. Always detach the overlay from the previous
        // page before binding a new one; otherwise an old translation can remain visible
        // while the new image/translation is loading.
        clearTranslationsView()
        this.page = page
        loadJob?.cancel()
        loadJob = scope.launch { loadPageAndProcessStatus() }
        refreshLayoutParams()
    }

    fun manualTranslationPosition(viewX: Float, viewY: Float): ManualTranslationPosition? =
        frame.manualTranslationPosition(viewX, viewY)

    fun refreshTranslationOverlay() {
        val currentPage = page ?: return
        if (translationsView == null) {
            addTranslationsView(currentPage)
        } else {
            translationsView?.refreshContent()
        }
        if (showTranslations) translationsView?.show()
    }

    private fun refreshLayoutParams() {
        frame.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            if (!viewer.isContinuous) bottomMargin = 15.dpToPx
            val margin = Resources.getSystem().displayMetrics.widthPixels * (viewer.config.sidePadding / 100f)
            marginEnd = margin.toInt()
            marginStart = margin.toInt()
        }
    }

    override fun recycle() {
        loadJob?.cancel()
        loadJob = null
        clearTranslationsView()
        page = null
        removeErrorLayout()
        frame.recycle()
        progressIndicator.setProgress(0)
        progressContainer.isVisible = true
    }

    private fun clearTranslationsView() {
        translationsView?.let { frame.removeView(it) }
        translationsView = null
        translationPageIndex = null
    }

    private suspend fun loadPageAndProcessStatus() {
        val page = page ?: return
        val loader = page.chapter.pageLoader ?: return
        supervisorScope {
            launchIO { loader.loadPage(page) }
            page.statusFlow.collectLatest { state ->
                // Ignore emissions from a page that no longer owns this recycled holder.
                if (this@WebtoonPageHolder.page !== page) return@collectLatest
                when (state) {
                    Page.State.Queue -> setQueued()
                    Page.State.LoadPage -> setLoading()
                    Page.State.DownloadImage -> {
                        setDownloading()
                        page.progressFlow.collectLatest { value ->
                            if (this@WebtoonPageHolder.page === page) progressIndicator.setProgress(value)
                        }
                    }
                    Page.State.Ready -> {
                        setImage(page)
                        addTranslationsView(page)
                    }
                    is Page.State.Error -> setError(state.error)
                }
            }
        }
    }

    private fun setQueued() {
        progressContainer.isVisible = true
        progressIndicator.show()
        removeErrorLayout()
    }

    private fun setLoading() {
        progressContainer.isVisible = true
        progressIndicator.show()
        removeErrorLayout()
    }

    private fun setDownloading() {
        progressContainer.isVisible = true
        progressIndicator.show()
        removeErrorLayout()
    }

    private suspend fun setImage(expectedPage: ReaderPage) {
        progressIndicator.setProgress(0)
        val streamFn = expectedPage.stream ?: return
        try {
            val (source, isAnimated) = withIOContext {
                val source = streamFn().use { process(Buffer().readFrom(it)) }
                Pair(source, ImageUtil.isAnimatedAndSupported(source))
            }
            withUIContext {
                // The holder may have been rebound while image decoding was running.
                if (page !== expectedPage) return@withUIContext
                frame.setImage(
                    source,
                    isAnimated,
                    ReaderPageImageView.Config(
                        zoomDuration = viewer.config.doubleTapAnimDuration,
                        minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH,
                        cropBorders = viewer.config.imageCropBorders,
                    ),
                )
                removeErrorLayout()
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
            withUIContext {
                if (page === expectedPage) setError(e)
            }
        }
    }

    private fun process(imageSource: BufferedSource): BufferedSource {
        if (viewer.config.dualPageRotateToFit) return rotateDualPage(imageSource)
        if (viewer.config.dualPageSplit) {
            val isDoublePage = ImageUtil.isWideImage(imageSource)
            if (isDoublePage) {
                val upperSide = if (viewer.config.dualPageInvert) ImageUtil.Side.LEFT else ImageUtil.Side.RIGHT
                return ImageUtil.splitAndMerge(imageSource, upperSide)
            }
        }
        return imageSource
    }

    private fun rotateDualPage(imageSource: BufferedSource): BufferedSource {
        val isDoublePage = ImageUtil.isWideImage(imageSource)
        return if (isDoublePage) {
            val rotation = if (viewer.config.dualPageRotateToFitInvert) -90f else 90f
            ImageUtil.rotateImage(imageSource, rotation)
        } else imageSource
    }

    private fun setError(error: Throwable?) {
        progressContainer.isVisible = false
        initErrorLayout(error)
        translationsView?.hide()
    }

    private fun onImageDecoded() {
        progressContainer.isVisible = false
        removeErrorLayout()
        if (translationPageIndex == page?.index) translationsView?.show()
    }

    private fun addTranslationsView(expectedPage: ReaderPage) {
        if (page !== expectedPage) return
        val translation = expectedPage.translation ?: run {
            clearTranslationsView()
            return
        }
        clearTranslationsView()
        translationsView = WebtoonTranslationsView(
            context = context,
            translation = translation,
            font = font,
            editor = expectedPage.translationEditor,
        )
        translationPageIndex = expectedPage.index
        if (!showTranslations) translationsView?.hide()
        frame.addView(translationsView, MATCH_PARENT, MATCH_PARENT)
    }

    private fun createProgressIndicator(): ReaderProgressIndicator {
        progressContainer = FrameLayout(context)
        frame.addView(progressContainer, MATCH_PARENT, parentHeight)
        val progress = ReaderProgressIndicator(context).apply {
            updateLayoutParams<FrameLayout.LayoutParams> { updateMargins(top = parentHeight / 4) }
        }
        progressContainer.addView(progress)
        return progress
    }

    private fun initErrorLayout(error: Throwable?): ReaderErrorBinding {
        if (errorLayout == null) {
            errorLayout = ReaderErrorBinding.inflate(LayoutInflater.from(context), frame, true)
            errorLayout?.root?.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, (parentHeight * 0.8).toInt())
            errorLayout?.actionRetry?.setOnClickListener { page?.let { it.chapter.pageLoader?.retryPage(it) } }
        }
        val imageUrl = page?.imageUrl
        errorLayout?.actionOpenInWebView?.isVisible = imageUrl != null
        if (imageUrl != null && imageUrl.startsWith("http", true)) {
            errorLayout?.actionOpenInWebView?.setOnClickListener {
                context.startActivity(WebViewActivity.newIntent(context, imageUrl))
            }
        }
        return errorLayout!!
    }

    private fun removeErrorLayout() {
        errorLayout?.let {
            frame.removeView(it.root)
            errorLayout = null
        }
    }
}
