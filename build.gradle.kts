plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0" apply false
    id("com.google.devtools.ksp") version "2.3.0" apply false
}

allprojects {
    providers.gradleProperty("period.buildRoot").orNull?.let { root ->
        layout.buildDirectory.set(file("$root/$name"))
    }
}
