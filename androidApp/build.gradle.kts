import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeScreenshot)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptiveNavigationSuite)
    implementation(libs.compose.ui)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serializationJson)
    implementation(libs.kotlinx.coroutinesCore)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutinesTest)

    screenshotTestImplementation(libs.screenshot.validationApi)
    screenshotTestImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.saubh.deskbuddy"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.saubh.deskbuddy"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
    buildFeatures {
        compose = true
    }
}

val copyReleaseApk = tasks.register<Copy>("copyReleaseApk") {
    val apkDir = layout.buildDirectory.dir("outputs/apk/release")
    from(apkDir)
    include("*.apk")
    into(layout.projectDirectory.dir("release"))
}

afterEvaluate {
    tasks.findByName("assembleRelease")?.let { assembleTask ->
        copyReleaseApk.configure {
            mustRunAfter(assembleTask)
        }
        assembleTask.finalizedBy(copyReleaseApk)
    }
}