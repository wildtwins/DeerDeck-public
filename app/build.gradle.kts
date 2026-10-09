plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}

val uploadStoreFile = providers.gradleProperty("deerdeck.uploadStoreFile")
    .orElse(providers.environmentVariable("DEERDECK_UPLOAD_STORE_FILE"))
    .orNull
val uploadStorePassword = providers.gradleProperty("deerdeck.uploadStorePassword")
    .orElse(providers.environmentVariable("DEERDECK_UPLOAD_STORE_PASSWORD"))
    .orNull
val uploadKeyAlias = providers.gradleProperty("deerdeck.uploadKeyAlias")
    .orElse(providers.environmentVariable("DEERDECK_UPLOAD_KEY_ALIAS"))
    .orNull
val uploadKeyPassword = providers.gradleProperty("deerdeck.uploadKeyPassword")
    .orElse(providers.environmentVariable("DEERDECK_UPLOAD_KEY_PASSWORD"))
    .orNull
val hasUploadSigning = listOf(
    uploadStoreFile,
    uploadStorePassword,
    uploadKeyAlias,
    uploadKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.wildtwins.deerdeck"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wildtwins.deerdeck"
        minSdk = 26
        targetSdk = 36
        versionCode = 32
        versionName = "2.4.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasUploadSigning) {
            create("release") {
                storeFile = file(uploadStoreFile!!)
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        checkTestSources = false
    }
}

kapt {
    arguments {
        val roomSchemaLocation = providers.gradleProperty("deerdeck.roomSchemaLocation")
            .map { rootProject.layout.projectDirectory.dir(it).asFile.absolutePath }
            .getOrElse("$projectDir/schemas")

        arg("room.schemaLocation", roomSchemaLocation)
        arg("room.incremental", "true")
    }
}

dependencies {
    val cameraXVersion = "1.6.1"
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    val lifecycleVersion = "2.10.0"
    val roomVersion = "2.8.4"
    val workVersion = "2.11.2"

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-video:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.work:work-runtime-ktx:$workVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    kapt("androidx.room:room-compiler:$roomVersion")

    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("androidx.room:room-testing:$roomVersion")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.robolectric:robolectric:4.14.1")

    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")
}
