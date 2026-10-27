buildscript {
    dependencies {
        // AGP 9 ships built-in Kotlin and pins KGP to 2.2.10 by default.
        // Declaring a newer KGP here is the documented way to upgrade it.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
}
