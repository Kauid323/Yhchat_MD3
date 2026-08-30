# Yhchat Canary release R8 / ProGuard rules

# Keep generic metadata used by Retrofit / Gson / Kotlin
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keep class kotlin.Metadata { *; }

# Retrofit / OkHttp
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Gson model fields accessed reflectively
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class * extends com.google.gson.TypeAdapter { *; }
-keep class * implements com.google.gson.TypeAdapterFactory { *; }
-keep class * implements com.google.gson.JsonSerializer { *; }
-keep class * implements com.google.gson.JsonDeserializer { *; }
-dontwarn sun.misc.**

# Hilt / DI
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-dontwarn dagger.hilt.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class androidx.room.RoomDatabase_Impl
-dontwarn androidx.room.paging.**

# Protobuf lite
-keep class com.google.protobuf.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# WebView JS bridge
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Security crypto / websocket / tink
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**
-dontwarn com.google.crypto.tink.**
-dontwarn org.java_websocket.**

# Compose Multiplatform / Miuix / Resources
-dontwarn org.jetbrains.compose.**
-keep class org.jetbrains.compose.** { *; }
-keep class top.yukonga.miuix.** { *; }

# Coil / media stack optional warnings
-dontwarn coil.**
-dontwarn okhttp3.internal.platform.**

# Keep parcelable creators when generated
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile