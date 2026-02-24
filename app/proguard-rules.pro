# Taqwa Fortress ProGuard Rules

# ==================== GENERAL ====================

# Keep line numbers for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations
-keepattributes *Annotation*

# Keep generic signatures
-keepattributes Signature

# Keep inner classes
-keepattributes InnerClasses,EnclosingMethod

# ==================== KOTLIN ====================

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.** {
    volatile <fields>;
}

# ==================== ANDROIDX ====================

# Keep ViewBinding classes
-keep class com.example.takwafortress.databinding.** { *; }

# Keep ViewModel classes
-keep class * extends androidx.lifecycle.ViewModel {
    <init>();
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}

# Keep LiveData
-keep class androidx.lifecycle.LiveData { *; }
-keep class androidx.lifecycle.MutableLiveData { *; }

# ==================== SECURITY ====================

# Keep EncryptedSharedPreferences
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }

# ==================== BOUNCYCASTLE (CRITICAL) ====================

-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keepclassmembers class org.bouncycastle.** { *; }

-keep class org.bouncycastle.asn1.edec.** { *; }
-keep class org.bouncycastle.crypto.params.** { *; }
-keep class org.bouncycastle.jcajce.** { *; }
-keep class org.bouncycastle.operator.** { *; }
-keep class org.bouncycastle.cert.** { *; }

# ==================== CONSCRYPT ====================

-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**

# ==================== LIBADB ====================

-keep class io.github.muntashirakon.adb.** { *; }
-dontwarn io.github.muntashirakon.adb.**

# ==================== TAQWA FORTRESS ====================

-keep class com.example.takwafortress.model.** { *; }

-keepclassmembers enum com.example.takwafortress.model.enums.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class com.example.takwafortress.model.builders.** { *; }
-keep class com.example.takwafortress.model.entities.** { *; }
-keep class com.example.takwafortress.model.interfaces.** { *; }

-keep class com.example.takwafortress.receivers.DeviceAdminReceiver { *; }
-keep class com.example.takwafortress.receivers.** { *; }

-keep class com.example.takwafortress.services.** { *; }
-keep class com.example.takwafortress.repository.** { *; }
-keep class com.example.takwafortress.mappers.** { *; }

-keep class com.example.takwafortress.TaqwaApplication { *; }

-keep class com.example.takwafortress.ui.activities.** { *; }
-keep class com.example.takwafortress.ui.fragments.** { *; }
-keep class com.example.takwafortress.ui.viewmodels.** { *; }

# ==================== DEVICE POLICY MANAGER ====================

-keep class android.app.admin.DevicePolicyManager { *; }
-keep class android.app.admin.DeviceAdminReceiver { *; }

# ==================== FIREBASE & GOOGLE ====================

-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# ==================== REMOVE LOGS (RELEASE ONLY) ====================

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

-keep class android.util.Log {
    public static *** w(...);
    public static *** e(...);
}

# ==================== SUPPRESS WARNINGS ====================

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn io.github.muntashirakon.adb.**

# ── Fix: R8 missing classes from com.google.crypto.tink ──────────────────────
# Tink references Google HTTP Client and Joda Time as optional dependencies
# not present on Android. Safe to ignore — Tink uses Android's HTTP stack.
-dontwarn com.google.api.client.http.**
-dontwarn org.joda.time.**

# ── Suppress deprecated Google Sign-In warnings ───────────────────────────────
-dontwarn com.google.android.gms.auth.api.signin.**