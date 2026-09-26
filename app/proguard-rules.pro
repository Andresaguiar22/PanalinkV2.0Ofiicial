# Project specific ProGuard rules for Panalink
# These rules harden the app against reverse engineering and modification.

# General obfuscation
-optimizationpasses 5
-allowaccessmodification
-overloadaggressively
-repackageclasses ''

# Keep line numbers for debugging but rename source files
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute Panalink

# Moshi and JSON serialization protection
-keep class com.example.data.model.** { *; }
-keepclassmembers class com.example.data.model.** {
    @com.squareup.moshi.Json <fields>;
}

# Room database protection
-keep class com.example.data.database.** { *; }

# Retrofit protection
-keepattributes Signature, InnerClasses, EnclosingMethod
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-dontwarn retrofit2.**

# OkHttp protection
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# Keep our security and crypto managers but obfuscate internal methods if possible
# We keep the object itself to ensure its singleton nature isn't broken by some tools
-keep class com.example.util.CryptoManager
-keep class com.example.util.SecurityManager
-keep class com.example.util.Resilience

# Prevent tampering with constants in SupabaseClient
-keepclassmembers class com.example.data.supabase.SupabaseClient {
    public static final java.lang.String supabaseUrl;
    public static final java.lang.String supabaseAnonKey;
}

# Supabase / Postgrest: los DTO y los clientes se resuelven por reflexion
-keep class com.example.data.supabase.** { *; }
-dontwarn com.example.data.supabase.**

# AndroidX and Material protection
-keep class androidx.** { *; }
-dontwarn androidx.**
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# FFmpeg decoder extension (media3): los renderers se cargan via reflection
# desde DefaultRenderersFactory y los metodos nativos van contra libffmpegJNI.so
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keepclasseswithmembernames class androidx.media3.decoder.ffmpeg.** {
    native <methods>;
}

# LiveKit SDK (WebRTC + protobuf internals loaded reflectively)
-keep class io.livekit.** { *; }
-keep class org.webrtc.** { *; }
-keep class livekit.** { *; }
-dontwarn org.webrtc.**
-dontwarn io.livekit.**

# --- Serializacion / reflexion (necesarias al activar R8 en release) ---------

# kotlinx.serialization: cada @Serializable genera un <Clase>$$serializer y un
# Companion que R8 solo puede resolver por reflexion; sin estas reglas la
# ofuscacion rompe la deserializacion en runtime (fallos silenciosos de parseo).
-keepattributes *Annotation*, InnerClasses, AnnotationDefault, RuntimeVisibleAnnotations
-dontnote kotlinx.serialization.**
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Moshi: los adapters generados por KSP y los qualifiers anotados se localizan
# por reflexion.
-keep @com.squareup.moshi.JsonQualifier interface *
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}
-keepclassmembers @com.squareup.moshi.JsonClass class * extends java.lang.Enum {
    <fields>;
    **[] values();
}
-dontwarn okio.**
-dontwarn javax.annotation.**

# Enums serializados por nombre (Moshi/kotlinx): el ofuscado de valores rompe
# cualquier round-trip de datos ya persistidos o servidos por la API.
-keepclassmembers enum com.example.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Kotlin reflection / moshi-kotlin (KotlinJsonAdapterFactory) ------------
# SupabaseClient.<clinit> construye el Moshi con KotlinJsonAdapterFactory, que usa
# kotlin-reflect (Class.la kotlinMetadata, constructors, params) para generar adapters
#en runtime. Sin estos keeps, R8 elimina/ofusca el metadata y el <clinit> falla
#con NoClassDefFoundError: <clinit> failed for class ...SupabaseClient al
# arrancar la app. (Caso real reportado por usuarios en release v1.3.59/code 86.)
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeVisibleTypeAnnotations,AnnotationDefault,InnerClasses,EnclosingMethod,Signature,Exceptions

# kotlin-reflect: el motor de reflexion de Moshi (klass.metadata, constructors, etc.)
-keep class kotlin.reflect.** { *; }
-keep class kotlin.reflect.jvm.** { *; }
-keep class kotlin.reflect.full.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.reflect.**

# Kotlin intrinsics/metadata internals necesarios para leer @Metadata
-keepclassmembers class kotlin.** {
    public static *** getMetadata(...);
}
-dontwarn kotlin.**

# Moshi runtime + factories resueltos por reflexion
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**

# moshi-kotlin (KotlinJsonAdapterFactory) y sus internals
-keep class com.squareup.moshi.kotlin.** { *; }
-dontwarn com.squareup.moshi.kotlin.**

# Los adapters generados por KSP (moshi-kotlin-codegen) tienen forma <Clase>JsonAdapter
# y R8 los necesita para la factory anotada; conservarlos TODOS enteros
-keep class **JsonAdapter { *; }
-keepclasseswithmembers class * {
    @com.squareup.moshi.Generated *;
}
-keep class com.squareup.moshi.Generated { *; }

# Retrofit + converters (reflexion en create())—— refuerzo explicito
-keep class retrofit2.** { *; }
-keep class retrofit2.converter.moshi.** { *; }
-dontwarn retrofit2.**

# OkHttp internals usados por el stack realtime/websocket
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Clases de modelo del dominio con @JsonClass se resuelven por reflexion generica
-keep @com.squareup.moshi.JsonClass class * { *; }
