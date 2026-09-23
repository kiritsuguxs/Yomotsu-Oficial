package eu.kanade.translation.translator

import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

internal object TranslationNetworkHelper {

    private val retryInterceptor = Interceptor { chain ->
        var request = chain.request()
        var response = chain.proceed(request)
        var tryCount = 0
        while (!response.isSuccessful && (response.code == 429 || response.code >= 500) && tryCount < 3) {
            tryCount++
            response.close()
            Thread.sleep(2000L * tryCount)
            response = chain.proceed(request)
        }
        response
    }

    val sharedClient: OkHttpClient by lazy {
        Injekt.get<NetworkHelper>().client.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .addInterceptor(retryInterceptor)
            .build()
    }
}
