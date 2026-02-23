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

# ✅ Keep ALL BouncyCastle classes
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keepclassmembers class org.bouncycastle.** { *; }

# ✅ Keep EdEC classes specifically (this was causing the crash)
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

# Keep all model classes (for data integrity)
-keep class com.example.takwafortress.model.** { *; }

# Keep all enums
-keepclassmembers enum com.example.takwafortress.model.enums.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep builders
-keep class com.example.takwafortress.model.builders.** { *; }

# Keep entities with identifiers
-keep class com.example.takwafortress.model.entities.** { *; }

# Keep interfaces
-keep class com.example.takwafortress.model.interfaces.** { *; }

# Keep Device Admin Receiver
-keep class com.example.takwafortress.receivers.DeviceAdminReceiver { *; }
-keep class com.example.takwafortress.receivers.** { *; }

# Keep Services
-keep class com.example.takwafortress.services.** { *; }

# Keep Repositories
-keep class com.example.takwafortress.repository.** { *; }

# Keep Mappers
-keep class com.example.takwafortress.mappers.** { *; }

# Keep Application class
-keep class com.example.takwafortress.TaqwaApplication { *; }

# Keep Activities
-keep class com.example.takwafortress.ui.activities.** { *; }

# Keep Fragments
-keep class com.example.takwafortress.ui.fragments.** { *; }

# Keep ViewModels
-keep class com.example.takwafortress.ui.viewmodels.** { *; }

# ==================== DEVICE POLICY MANAGER ====================

# Keep Device Policy Manager related classes
-keep class android.app.admin.DevicePolicyManager { *; }
-keep class android.app.admin.DeviceAdminReceiver { *; }

# ==================== REMOVE LOGS (RELEASE ONLY) ====================

# Remove all Log.d, Log.v, Log.i calls in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep Log.w and Log.e for production debugging
-keep class android.util.Log {
    public static *** w(...);
    public static *** e(...);
}

# ==================== SUPPRESS WARNINGS ====================

# Suppress warnings for known issues
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn io.github.muntashirakon.adb.**