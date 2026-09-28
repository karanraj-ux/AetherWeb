# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Ktor
-keep class io.ktor.** { *; }
-keep class kotlin.** { *; }
-keepclassmembers class * {
    kotlin.coroutines.Continuation *;
}
-dontwarn io.ktor.**
-dontwarn org.slf4j.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

# Moshi
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# General
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, Exceptions

# Netty Optional Dependencies (Ktor)
-dontwarn io.netty.internal.tcnative.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.eclipse.jetty.npn.**
-dontwarn org.eclipse.jetty.alpn.**
-dontwarn reactor.blockhound.integration.**
-dontwarn io.netty.util.internal.logging.**

-keep class io.netty.** { *; }

# More Netty / Ktor optional dependencies
-dontwarn com.barchart.udt.**
-dontwarn com.fasterxml.aalto.**
-dontwarn com.jcraft.jzlib.**
-dontwarn com.ning.compress.**
-dontwarn com.oracle.svm.**
-dontwarn lzma.sdk.**
-dontwarn net.jpountz.**
-dontwarn org.jboss.marshalling.**
-dontwarn reactor.blockhound.**
-dontwarn sun.security.x509.**

# More optional dependencies (compression/protobuf for Netty)
-dontwarn com.aayushatharva.brotli4j.**
-dontwarn com.github.luben.zstd.**
-dontwarn com.google.protobuf.**
-dontwarn io.netty.handler.codec.protobuf.**

# Room Database & SQLite
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.migration.Migration
-dontwarn androidx.room.paging.**

# Protocol & Domain Models
-keep class com.example.protocol.** { *; }
-keepclassmembers class com.example.protocol.** { *; }
-keep class com.example.data.** { *; }
-keepclassmembers class com.example.data.** { *; }
-keep class com.example.models.** { *; }
-keepclassmembers class com.example.models.** { *; }

# Serialization / JSON
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @kotlinx.serialization.Serializable <fields>;
}

# Android Architecture Components & Services
-keep public class com.example.MeshForegroundService { *; }
-keep public class com.example.NotificationReceiver { *; }
-keep public class com.example.BootReceiver { *; }
-keep public class com.example.MainActivity { *; }

# WebServer & Embedded Engine
-keep class com.example.WebServerManager** { *; }
-keep class com.example.WebPortalTemplate** { *; }
-keep class com.example.HighSpeedFileTransferManager** { *; }
-keep class com.example.WifiClusterBridgeManager** { *; }
-keep class com.example.CallManager** { *; }
-keep class com.example.LiveVoiceManager** { *; }
-keep class com.example.LiveVideoManager** { *; }
-keep class com.example.AudioJitterBuffer** { *; }

# Keep Line Numbers for Stacktraces in R8
-keepattributes SourceFile,LineNumberTable

