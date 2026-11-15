# Yhchat Canary release R8 / ProGuard rules
#
# 减体积的思路：不是往里加 -keep，而是把「整库全保留」的宽规则收窄，
# 再让 R8 多跑几轮优化。
#
# 旧版里这几条是最大的浪费，它们让对应库一个字节都删不掉：
#     -keep class com.google.protobuf.** { *; }
#     -keep class org.jetbrains.compose.** { *; }
#     -keep class top.yukonga.miuix.** { *; }
#     -keep class androidx.room.RoomDatabase_Impl
# 下面逐条换成了「只保住真正靠反射/类名访问的那部分」。
#
# 刻意没开 -repackageclasses 'c'：它能再压一点，但会把 Room 的 <DB>_Impl
# 反射查找等一切按类名找类的地方置于风险中，而这套代码没有可自动验证的
# 构建环境，不值得为几十 KB 赌一次"首次打开数据库就崩"。想试的话在末尾加：
#     -repackageclasses 'c'
# 然后必须完整回归：冷启动、打开数据库、进直播间、收推送、WebDAV、扫码。

# ---------------------------------------------------------------------------
# 优化强度
#
# optimizationpasses 默认只跑 1 遍。对 Compose + Hilt + Protobuf 这种生成代码
# 很多的工程，1 遍远不够 —— 内联之后才暴露出来的类合并机会需要多轮才收敛。
# 5 遍是常见收益拐点，再往上基本只有构建时间变长。
#
# allowAccessModification 允许 R8 放宽访问修饰符，跨类内联才生效，
# 是这里性价比最高的一条。前提是没有反射改私有成员，本项目没有这种用法。
# ---------------------------------------------------------------------------
-optimizationpasses 5
-allowaccessmodification

# 行号留着（崩溃栈才有意义），原始文件名抹掉（它会进字符串常量池，也没用）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 泛型签名 / 注解 / 内部类信息：Retrofit、Gson、Kotlin 反射都要读
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# Kotlin 元数据。协程和 Compose 编译器插件都依赖它，缺了是静默出错
-keep class kotlin.Metadata { *; }

# ---------------------------------------------------------------------------
# Retrofit / OkHttp
# ---------------------------------------------------------------------------
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------------------------------------------------------------------------
# Gson
#
# 带 @SerializedName 的字段：JSON key 由注解给定，字段名可以随便混淆。
# 不带注解的字段：Gson 直接把字段名当 JSON key，混淆后就是一堆 null。
# 本项目 model 全部带注解，所以不需要整包保字段名。
# ---------------------------------------------------------------------------
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class * extends com.google.gson.TypeAdapter { *; }
-keep class * implements com.google.gson.TypeAdapterFactory { *; }
-keep class * implements com.google.gson.JsonSerializer { *; }
-keep class * implements com.google.gson.JsonDeserializer { *; }
# TypeToken 靠匿名子类的泛型签名取类型，压掉就取不到了
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-dontwarn sun.misc.**
-dontwarn com.google.gson.**

# ---------------------------------------------------------------------------
# Hilt / Dagger
#
# 注解处理器生成的元信息按类名索引，被改名就注入失败。
# ---------------------------------------------------------------------------
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.EntryPoint interface * { *; }
-dontwarn dagger.hilt.**
-dontwarn javax.inject.**
-dontwarn jakarta.inject.**

# ---------------------------------------------------------------------------
# Room
#
# Room 用反射按「<数据库类名>_Impl」找生成的实现类，所以这两个名字必须定住。
# 用 -keepnames 而不是 -keep { *; }：只禁止改名，仍然允许 R8 裁掉没用的成员，
# 比整类全保留省得多。
# ---------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keepnames class * extends androidx.room.RoomDatabase
-keepnames class * extends androidx.room.RoomDatabase_Impl
-keepclassmembers @androidx.room.Entity class * {
    <fields>;
}
-dontwarn androidx.room.paging.**

# ---------------------------------------------------------------------------
# Protobuf lite
#
# 旧规则是 -keep class com.google.protobuf.** { *; }，整个 runtime 都不压缩。
# 实际只有 GeneratedMessageLite 体系靠反射拿字段/默认实例，其余工具类正常压缩。
# ---------------------------------------------------------------------------
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
-keepclassmembers class * implements com.google.protobuf.MessageLite {
    <fields>;
}
-dontwarn com.google.protobuf.**

# ---------------------------------------------------------------------------
# WebView JS 桥：方法名就是 JS 里调的名字，不能改
# ---------------------------------------------------------------------------
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---------------------------------------------------------------------------
# 加密存储：Tink 的关键表是反射加载的
# ---------------------------------------------------------------------------
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn androidx.security.crypto.**
-dontwarn com.google.crypto.tink.**

# ---------------------------------------------------------------------------
# 按字符串名反射打开的类
#
# LiveRoomLauncher 用 Class.forName("com.yhchat.canary.ui.live.LiveRoomActivity")
# 打开直播间。它在清单里、AGP 生成的规则已经保了名，这里再显式写一遍：
# 名字一旦被改，runCatching 会把异常吞掉，表现成"点了没反应"，极难查。
# ---------------------------------------------------------------------------
-keep class com.yhchat.canary.ui.live.LiveRoomActivity { *; }
-keepnames class com.yhchat.canary.ui.live.LiveRoomActivity

# ---------------------------------------------------------------------------
# Compose Multiplatform 资源
#
# markdown-renderer / ksoup 是 KMP 库，会用到 org.jetbrains.compose.resources。
# 它靠反射读取生成的 Res 类和 Res 里的字段（字体、图片、字符串），
# 所以只保这一小块，不保整个 org.jetbrains.compose.*。
# ---------------------------------------------------------------------------
-keep class org.jetbrains.compose.resources.** { *; }
-keepclassmembers class * {
    @org.jetbrains.compose.resources.* <fields>;
}
-dontwarn org.jetbrains.compose.**

# ---------------------------------------------------------------------------
# 依赖库的可选实现（缺失是正常的，只是别报警告）
# ---------------------------------------------------------------------------
-dontwarn coil.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.slf4j.**
-dontwarn androidx.media3.**
-dontwarn org.java_websocket.**

# WebDAV (sardine) 在 Android 上走 OkHttp 分支，JAXB / 老 commons-httpclient
# 那一套类不存在也不影响运行
-dontwarn javax.xml.bind.**
-dontwarn javax.activation.**
-dontwarn org.apache.commons.httpclient.**
-dontwarn org.apache.jackrabbit.**
-dontwarn org.jdom.**
-dontwarn org.xmlpull.v1.**
-dontwarn com.thegrizzlylabs.sardineandroid.**

# ---------------------------------------------------------------------------
# Android 基础约定
# ---------------------------------------------------------------------------
# Parcelable 的 CREATOR 是反射取的
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# 枚举的 values() / valueOf() 走反射，折叠后会崩
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
