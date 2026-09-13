package eu.kanade.tachiyomi.extension.novel.runtime

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import eu.kanade.tachiyomi.network.NetworkHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelNativeApi(
    private val client: OkHttpClient = Injekt.get<NetworkHelper>().client,
) : NovelJsRuntime.NativeApi {

    private var nextHandle = 1
    private val handles = ConcurrentHashMap<Int, Element>()

    private fun getElement(handle: Int): Element =
        handles[handle] ?: throw IllegalArgumentException("Invalid handle $handle")

    private fun store(element: Element): Int {
        val h = nextHandle++
        handles[h] = element
        return h
    }

    override fun fetch(url: String, optionsJson: String?): String {
        val requestBuilder = Request.Builder().url(url)
        var bodyParams: RequestBody? = null
        var method = "GET"

        if (optionsJson != null) {
            try {
                val json = Injekt.get<Json>().parseToJsonElement(optionsJson) as? JsonObject
                method = json?.get("method")?.jsonPrimitive?.content ?: "GET"
                val headers = json?.get("headers") as? JsonObject
                headers?.keys?.forEach { key ->
                    requestBuilder.addHeader(key, headers[key]?.jsonPrimitive?.content ?: "")
                }
                val bodyStr = json?.get("body")?.jsonPrimitive?.content
                if (bodyStr != null) {
                    val mediaType = (headers?.get("content-type")?.jsonPrimitive?.content ?: "application/x-www-form-urlencoded").toMediaTypeOrNull()
                    bodyParams = bodyStr.toRequestBody(mediaType)
                }
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }

        requestBuilder.method(method, if (method != "GET" && method != "HEAD") bodyParams ?: "".toRequestBody() else null)

        val response = client.newCall(requestBuilder.build()).execute()
        val responseBody = response.body?.string() ?: ""
        val finalUrl = response.request.url.toString()

        val jsonResult = buildJsonObject {
            put("status", response.code)
            put("statusText", response.message)
            put("url", finalUrl)
            put("body", responseBody)
            put("headers", buildJsonObject {
                response.headers.names().forEach { name ->
                    put(name, response.headers[name] ?: "")
                }
            })
        }
        return jsonResult.toString()
    }

    override fun fetchBinary(url: String, optionsJson: String?): String = fetch(url, optionsJson)
    override fun fetchProto(url: String, configJson: String, optionsJson: String?): String = fetch(url, optionsJson)

    override fun storageGet(key: String): String? = null
    override fun storageSet(key: String, value: String) {}
    override fun storageRemove(key: String) {}
    override fun storageClear() {}
    override fun storageKeys(): String = "[]"

    override fun localStorageGet(key: String): String? = null
    override fun localStorageSet(key: String, value: String) {}
    override fun localStorageRemove(key: String) {}
    override fun localStorageClear() {}
    override fun localStorageKeys(): String = "[]"

    override fun sessionStorageGet(key: String): String? = null
    override fun sessionStorageSet(key: String, value: String) {}
    override fun sessionStorageRemove(key: String) {}
    override fun sessionStorageClear() {}
    override fun sessionStorageKeys(): String = "[]"

    override fun resolveUrl(url: String, base: String?): String = if (base != null) URI(base).resolve(url).toString() else url
    override fun getPathname(url: String): String = URI(url).path ?: ""
    override fun select(html: String, selector: String): String = ""
    override fun aesGcmDecrypt(keyB64: String, ivB64: String, cipherB64: String): String = ""
    override fun urlEncode(value: String, charsetName: String?): String = java.net.URLEncoder.encode(value, charsetName ?: "UTF-8")
    override fun urlDecode(value: String, charsetName: String?): String = java.net.URLDecoder.decode(value, charsetName ?: "UTF-8")

    override fun domLoad(html: String): Int = store(Jsoup.parse(html))
    override fun domSelect(handle: Int, selector: String): String = getElement(handle).select(selector).map { store(it) }.joinToString(",")
    override fun domParent(handle: Int): Int = getElement(handle).parent()?.let { store(it) } ?: -1
    override fun domChildren(handle: Int, selector: String?): String = (if (selector != null) getElement(handle).children().select(selector) else getElement(handle).children()).map { store(it) }.joinToString(",")
    override fun domNext(handle: Int, selector: String?): Int = getElement(handle).nextElementSibling()?.let { store(it) } ?: -1
    override fun domPrev(handle: Int, selector: String?): Int = getElement(handle).previousElementSibling()?.let { store(it) } ?: -1
    override fun domNextAll(handle: Int, selector: String?): String = getElement(handle).nextElementSiblings().map { store(it) }.joinToString(",")
    override fun domPrevAll(handle: Int, selector: String?): String = getElement(handle).previousElementSiblings().map { store(it) }.joinToString(",")
    override fun domSiblings(handle: Int, selector: String?): String = getElement(handle).siblingElements().map { store(it) }.joinToString(",")
    override fun domClosest(handle: Int, selector: String): Int = getElement(handle).parents().firstOrNull { it.`is`(selector) }?.let { store(it) } ?: -1
    override fun domContents(handle: Int): String = getElement(handle).childNodes().mapNotNull { if (it is Element) store(it).toString() else null }.joinToString(",")
    override fun domIs(handle: Int, selector: String): Boolean = getElement(handle).`is`(selector)
    override fun domHas(handle: Int, selector: String): Boolean = getElement(handle).selectFirst(selector) != null
    override fun domNot(handle: Int, selector: String): String = ""
    override fun domHtml(handle: Int): String = getElement(handle).html()
    override fun domOuterHtml(handle: Int): String = getElement(handle).outerHtml()
    override fun domXml(handle: Int): String = getElement(handle).html()
    override fun domText(handle: Int): String = getElement(handle).text()
    override fun domAttr(handle: Int, name: String): String? = getElement(handle).attr(name).takeIf { it.isNotEmpty() }
    override fun domSetAttr(handle: Int, name: String, value: String) { getElement(handle).attr(name, value) }
    override fun domRemoveAttr(handle: Int, name: String) { getElement(handle).removeAttr(name) }
    override fun domAttrs(handle: Int): String = "{}"
    override fun domHasClass(handle: Int, className: String): Boolean = getElement(handle).hasClass(className)
    override fun domData(handle: Int, key: String): String? = getElement(handle).dataset()[key]
    override fun domVal(handle: Int): String? = getElement(handle).`val`()
    override fun domTagName(handle: Int): String = getElement(handle).tagName()
    override fun domIsTextNode(handle: Int): Boolean = false
    override fun domReplaceWith(handle: Int, html: String) {}
    override fun domBefore(handle: Int, html: String) {}
    override fun domAfter(handle: Int, html: String) {}
    override fun domAppend(handle: Int, html: String) {}
    override fun domPrepend(handle: Int, html: String) {}
    override fun domEmpty(handle: Int) { getElement(handle).empty() }
    override fun domRemove(handle: Int) { getElement(handle).remove() }
    override fun domAddClass(handle: Int, className: String) { getElement(handle).addClass(className) }
    override fun domRemoveClass(handle: Int, className: String) { getElement(handle).removeClass(className) }
    override fun domRelease(handle: Int) { handles.remove(handle) }
    override fun domReleaseAll() { handles.clear(); nextHandle = 1 }

    override fun consoleLog(message: String) { println(message) }
    override fun consoleError(message: String) { System.err.println(message) }
    override fun consoleWarn(message: String) { System.err.println(message) }
}
