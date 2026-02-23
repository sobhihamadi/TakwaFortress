// Top-level build file for Taqwa Fortress
// Compatible with Android Studio 2025.x and Android 16

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("com.google.gms.google-services") version "4.4.4" apply false
}


tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}