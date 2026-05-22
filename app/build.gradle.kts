plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

fun readSecretValue(label: String): String {
    val keyFile = rootProject.file("key.txt")
    if (!keyFile.exists()) return ""
    val line = keyFile.readLines()
        .firstOrNull { it.trim().startsWith(label, ignoreCase = true) }
        ?.trim()
        ?: return ""
    val withoutLabel = line.removePrefix(label).trim()
    return withoutLabel.trimStart('=', ':', '：').trim()
}

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.neurogarden.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.neurogarden.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "GUARDIAN_API_URL", buildConfigString(readSecretValue("URL")))
        buildConfigField("String", "GUARDIAN_API_KEY", buildConfigString(readSecretValue("APIkey")))
        buildConfigField("String", "GUARDIAN_MODEL", buildConfigString(readSecretValue("MODEL").ifBlank { "MiniMax-M2.7" }))
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
}
