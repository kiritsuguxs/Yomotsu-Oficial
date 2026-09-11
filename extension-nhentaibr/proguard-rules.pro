# Keep extension class and entry points
-keep class eu.kanade.tachiyomi.extension.pt.nhentaibr.** { *; }

# Keep Tachiyomi extension interfaces and classes
-keep class eu.kanade.tachiyomi.source.** { *; }

# Ignore warnings for classes provided at runtime by the host app
-dontwarn rx.**
-dontwarn okhttp3.**
-dontwarn org.jsoup.**
-dontwarn eu.kanade.tachiyomi.**
-dontwarn android.**
-dontwarn java.lang.invoke.**
