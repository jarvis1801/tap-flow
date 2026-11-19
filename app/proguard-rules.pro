# ==============================================================================
# TapFlow Lean Production R8 / ProGuard Rules (AGP 9.1.1)
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. Attributes, Stacktrace Readability & Log Stripping
# ------------------------------------------------------------------------------
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Strip Log.v/d/i calls cleanly under AGP 9.1.1 R8
-maximumremovedandroidloglevel 4

# Preserve @Keep annotated classes and members (Navigation route enums, backup/promo DTOs, Shizuku UserService)
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# ------------------------------------------------------------------------------
# 2. Kotlinx Serialization (1.9.0) & Navigation Compose (2.8.9)
# ------------------------------------------------------------------------------
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

-keep,includedescriptorclasses class com.jarvis.tapflow.navigation.** { *; }
-keep,includedescriptorclasses class com.tapflow.core.model.** { *; }
-keep,includedescriptorclasses class com.tapflow.core.database.backup.TapFlowBackupEnvelope { *; }
-keep,includedescriptorclasses class com.tapflow.core.database.backup.TapFlowBackupEnvelope$** { *; }
-keep,includedescriptorclasses class com.tapflow.core.billing.PromoCodeCatalog { *; }
-keep,includedescriptorclasses class com.tapflow.core.billing.RemotePromoCode { *; }

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class **
-keep,includedescriptorclasses class <1>$$serializer {
    private kotlinx.serialization.descriptors.SerialDescriptor descriptor;
    public <1>$$serializer INSTANCE;
    public *** deserialize(...);
    public void serialize(...);
}

-keepclassmembers enum com.jarvis.tapflow.navigation.** {
    **[] $VALUES;
    public static ** valueOf(java.lang.String);
    public static **[] values();
}
-keepclassmembers enum com.tapflow.core.model.** {
    **[] $VALUES;
    public static ** valueOf(java.lang.String);
    public static **[] values();
}

-dontwarn kotlinx.serialization.internal.ClassValueReferences

# ------------------------------------------------------------------------------
# 3. Shizuku (13.1.5) & UserService AIDL
# ------------------------------------------------------------------------------
-keep class rikka.hidden.compat.adapter.** { *; }
-keep class moe.shizuku.api.BinderContainer* { *; }
-keep class com.tapflow.core.service.shizuku.IShizukuShellService** { *; }

# ------------------------------------------------------------------------------
# 4. Ktor (3.3.3) CIO Engine & Coroutines Atomics
# ------------------------------------------------------------------------------
-keepclassmembers class io.ktor.** {
    volatile <fields>;
}
-keepclassmembers class kotlinx.atomicfu.** {
    volatile <fields>;
}
