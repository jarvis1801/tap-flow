# ==============================================================================
# TapFlow Production R8 Rules (AGP 9.1.1)
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. Attributes, Stacktrace Readability & Log Stripping
# ------------------------------------------------------------------------------
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 徹底移除 VERBOSE / DEBUG / INFO log 及對應嘅 Log.isLoggable() 分支
-maximumremovedandroidloglevel 4

# ------------------------------------------------------------------------------
# 2. Kotlinx Serialization (1.9.0) & Navigation Compose (2.8.9)
# ------------------------------------------------------------------------------
# 注意：
# - kotlinx-serialization 1.9.0 已內建大部分 rules，唔需要鎖死成個 model/server/backup package。
# - 所有用作 Navigation Compose Type-Safe Route 參數嘅 Enum，請直接喺 Kotlin Enum 上加 @androidx.annotation.Keep
# - 如果以下某啲類有用到「純 Java 反射 / Gson」或「無 @SerialName 嘅多態序列化」，先針對該 DTO 加 @Keep 或單獨 keep；
#   否則純 Kotlinx Serialization 可以直接靠內建規則處理（同時保留混淆優化）。

# 僅保留 Navigation Route 類別名稱（若有用到非預設 serialName / Class.forName 映射時需要；如全部標準 @Serializable 其實可再收窄）
# -keepnames @kotlinx.serialization.Serializable class com.jarvis.tapflow.navigation.**

-dontwarn kotlinx.serialization.internal.ClassValueReferences

# ------------------------------------------------------------------------------
# 3. Shizuku (13.1.5) & Android 16 ART Hidden Compat Fix
# ------------------------------------------------------------------------------
# 防止 Android 16 ART 對 hidden framework stub adapter 觸發 VerifyError
-keep class rikka.hidden.compat.adapter.** { *; }
# 確保跨進程 Parcelable BinderContainer 名稱不被混淆
-keep class moe.shizuku.api.BinderContainer* { *; }
# 若有自訂 Shizuku UserService，建議收窄到該 Service 類名（或直接喺 class 上加 @Keep）
# -keep class com.tapflow.core.service.shizuku.MyUserService { public <init>(...); *; }
-keep class com.tapflow.core.service.shizuku.** { *; }

-dontwarn rikka.hidden.**
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

# ------------------------------------------------------------------------------
# 4. Ktor (3.3.3) CIO Embedded Server
# ------------------------------------------------------------------------------
# Kotlin Coroutines 已自帶完整 rules，無需手寫
# Ktor AtomicFU / FieldUpdater 需保留 volatile fields
-keepclassmembers class io.ktor.** {
    volatile <fields>;
}

# 若有用到 ApplicationEngine reflection / EngineMain 或特定 ServiceLoader 喺 Release 測試出事，先針對性補上
# 例如：-keep class io.ktor.serialization.kotlinx.json.KotlinxSerializationJsonExtensionProvider { <init>(); }

-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn java.beans.**
-dontwarn sun.misc.**
-dontwarn kotlinx.atomicfu.**

# ------------------------------------------------------------------------------
# 5. Room / Hilt / Coil / Play Billing / Firebase Remote Config
# ------------------------------------------------------------------------------
# 以上全部現代版本均已內建 AAR Consumer R8 Rules，不需額外 -keep。
# 如 R8 報錯找不到 Paging 或其他可選模組，再「按 missing_rules.txt 針對性」加 -dontwarn：
# -dontwarn androidx.room.paging.**