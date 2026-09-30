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
