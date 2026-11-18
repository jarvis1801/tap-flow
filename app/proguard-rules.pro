# ==============================================================================
# TapFlow Production R8 / ProGuard Rules (AGP 9.1.1)
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. Attributes, Stacktrace Readability & Log Stripping
# ------------------------------------------------------------------------------
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeInvisibleAnnotations,RuntimeInvisibleParameterAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Preserve @Keep classes and members explicitly
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
-keep,includedescriptorclasses class com.tapflow.core.billing.PromoCodeCatalog { *; }
-keep,includedescriptorclasses class com.tapflow.core.billing.RemotePromoCode { *; }
-keep,includedescriptorclasses class com.tapflow.core.billing.PromoCodeCatalog$** { *; }
-keep,includedescriptorclasses class com.tapflow.core.billing.RemotePromoCode$** { *; }
-keep,includedescriptorclasses class com.tapflow.core.server.** { *; }
-keep,includedescriptorclasses class com.tapflow.core.database.backup.** { *; }

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

-keepclasseswithmembers class *$$serializer {
    private ** descriptor;
    public static ** INSTANCE;
}

-keepclassmembers class **$$serializer {
    private ** descriptor;
    public static ** INSTANCE;
}

-keepclassmembers class * {
    *** serializer(...);
}

# Preserve Kotlinx Serialization enum entries & Parcelable/TypeSafe Nav args
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
# 3. Shizuku (13.1.5) & Android 16 ART Hidden Compat Fix
# ------------------------------------------------------------------------------
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep class rikka.hidden.compat.** { *; }
-keep class com.tapflow.core.service.shizuku.** { *; }

-dontwarn rikka.hidden.**
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

# ------------------------------------------------------------------------------
# 4. Ktor (3.3.3) CIO Embedded Server & Kotlin Coroutines
# ------------------------------------------------------------------------------
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler

-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn io.ktor.**
-dontwarn kotlinx.atomicfu.**
-dontwarn java.beans.**

# ------------------------------------------------------------------------------
# 5. Google Play Billing (9.1.0) & Firebase Remote Config (35.2.0)
# ------------------------------------------------------------------------------
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

-keep class com.google.firebase.remoteconfig.** { *; }
-keep class com.google.firebase.FirebaseApp { *; }
-keep class com.google.firebase.components.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ------------------------------------------------------------------------------
# 6. Room (2.7.0), Dagger Hilt (2.60.1) & Coil (2.7.0)
# ------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.**

-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-dontwarn dagger.hilt.**

-keep class coil.** { *; }
-dontwarn coil.**

# ------------------------------------------------------------------------------
# 7. AndroidManifest Components & TapFlow Entry Points
# ------------------------------------------------------------------------------
-keep public class com.jarvis.tapflow.TapFlowApp { *; }
-keep public class com.jarvis.tapflow.MainActivity { *; }
-keep public class com.tapflow.core.service.accessibility.TapFlowAccessibilityService { *; }
-keep public class com.tapflow.core.service.overlay.OverlayService { *; }
-keep public class com.tapflow.core.service.projection.MediaProjectionService { *; }
-keep public class com.tapflow.core.service.bridge.LocalBridgeService { *; }
-keep public class com.tapflow.core.service.external.TapFlowExternalTriggerReceiver { *; }
