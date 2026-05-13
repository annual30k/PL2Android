plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

fun runtimeString(name: String): String =
    providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orElse("").get()

fun runtimeBoolean(name: String): String =
    providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orElse("false").get()
        .toBooleanStrictOrNull()
        ?.toString()
        ?: "false"

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.patrollink"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.patrollink"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "REST_BASE_URL", runtimeString("PATROL_REST_BASE_URL").asBuildConfigString())
        buildConfigField("String", "WEBSOCKET_URL", runtimeString("PATROL_WEBSOCKET_URL").asBuildConfigString())
        buildConfigField("String", "WIFI_FILE_BASE_URL", runtimeString("PATROL_WIFI_FILE_BASE_URL").asBuildConfigString())
        buildConfigField("String", "BLE_SERVICE_UUID", runtimeString("PATROL_BLE_SERVICE_UUID").asBuildConfigString())
        buildConfigField("String", "BLE_COMMAND_UUID", runtimeString("PATROL_BLE_COMMAND_UUID").asBuildConfigString())
        buildConfigField("String", "BLE_STATUS_UUID", runtimeString("PATROL_BLE_STATUS_UUID").asBuildConfigString())
        buildConfigField("String", "STREAM_RELAY_URL", runtimeString("PATROL_STREAM_RELAY_URL").asBuildConfigString())
        buildConfigField("boolean", "USE_REAL_BLE", runtimeBoolean("PATROL_USE_REAL_BLE"))
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
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
