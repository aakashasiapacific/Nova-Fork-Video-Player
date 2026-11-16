# R8 for release builds (owner: as snappy as ChatGPT; Compose runs much faster optimised).
# Only what R8 cannot see is kept here; everything else is shrunk and optimised.

# libVLC: its native code calls back into these classes and fields by name.
-keep class org.videolan.** { *; }
-dontwarn org.videolan.**

# SMB shares (smbj) load protocol pieces by reflection; its optional crypto/logging may be absent.
-keep class com.hierynomus.** { *; }
-keep class net.engio.mbassy.** { *; }
-dontwarn com.hierynomus.**
-dontwarn net.engio.mbassy.**
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**
-dontwarn javax.security.auth.**

# kotlinx.serialization: generated serializers are found through companions.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontnote kotlinx.serialization.**

# OkHttp's optional TLS providers.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Crash reports read better with real line numbers.
-keepattributes SourceFile, LineNumberTable
