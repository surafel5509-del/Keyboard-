plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.novakeyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.novakeyboard"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ""
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so that "assembleRelease" produces an
            // installable artifact out of the box; replace with your own key.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = false
    }
    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "DebugProbesKt.bin")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    lint {
        // The keyboard is intentionally free of AndroidX/Material, so the only
        // lint gate we keep is "never break the build on a warning".
        warningsAsErrors = false
        abortOnError = true
        checkDependencies = false
        disable += setOf("UnusedResources", "ObsoleteSdkInt", "MissingTranslation")
    }
}

// The unit tests need the bundled assets (dictionary, bigrams, emoji index).
tasks.withType<Test>().configureEach {
    systemProperty("nova.assets", file("src/main/assets").absolutePath)
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
