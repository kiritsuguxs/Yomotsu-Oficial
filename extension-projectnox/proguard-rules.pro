# Keep extension class and entry points
-keep class eu.kanade.tachiyomi.extension.pt.projectnox.** { *; }

# Keep Tachiyomi extension interfaces and classes
-keep class eu.kanade.tachiyomi.source.** { *; }

# Keep kotlinx.serialization reflection and generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# Ignore warnings from compileOnly libraries
-dontwarn okhttp3.**
-dontwarn org.jsoup.**
-dontwarn eu.kanade.tachiyomi.**
-dontwarn android.**
