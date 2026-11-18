# ProGuard / R8 rules para release.
# Mantenemos las clases serializadas y los modelos de Room.

# Kotlinx Serialization: mantener los @Serializable y sus generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.seend.app.**$$serializer { *; }
-keepclassmembers class com.seend.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.seend.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room: las entidades y DAOs generados ya están cubiertos por las rules
# estándar de Android. Aseguramos que las entidades no se ofusquen para
# que las consultas SQL sigan funcionando.
-keep class com.seend.app.data.local.** { *; }

# OkHttp: usa sus propias reglas incluidas en el consumer-rules del AAR.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Coil: mantiene el registro de los decoders.
-dontwarn coil.**
