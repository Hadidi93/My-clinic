# kotlinx.serialization: keep generated serializers for our DTOs.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.myclinic.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.myclinic.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.myclinic.app.**$$serializer { *; }

# Ktor / OkHttp optional dependencies
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**

# Our plain-Kotlin models (core/domain) are read from the server's JSON.
-keep class com.myclinic.domain.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# SQLCipher (encrypted offline database) is called from native code.
-keep class net.zetetic.** { *; }

# Optional platform pieces some libraries mention.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**

# Release builds write nothing to the phone's log (defence in depth: the app
# never logs patient data anyway). Errors are kept for crash diagnosis.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
