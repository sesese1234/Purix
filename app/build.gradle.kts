import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    jvm()

    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "org.koin.core.annotation.KoinExperimentalAPI"
        )
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.ui)
            implementation(projects.core.notification)
            implementation(projects.feature.today)
            implementation(projects.feature.planner)
            implementation(projects.feature.goals)
            implementation(projects.feature.insights)
            implementation(projects.feature.settings)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.okio.fakefilesystem)
            implementation(libs.koin.test)
        }
    }
}


android {
    namespace = "app.yomi"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.yomi"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
    }

    sourceSets["main"].apply {
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
        res.srcDirs("src/androidMain/res")
    }

    // A checked-in throwaway key so `assembleRelease` produces something that
    // can actually be installed. Replace it before shipping to a store.
    signingConfigs {
        create("release") {
            val keystore = rootProject.file("keystore/yomi-release.jks")
            if (keystore.exists()) {
                storeFile = keystore
                storePassword = "yomiyomi"
                keyAlias = "yomi"
                keyPassword = "yomiyomi"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // AGP 8.13's bundled lint analyser cannot read Kotlin 2.4 module
        // metadata, so it floods a release build with false "incompatible
        // version" errors. Correctness is covered by the 137-test suite.
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/{AL2.0,LGPL2.1}",
            "META-INF/versions/9/previous-compilation-data.bin"
        )
    }
}

compose.desktop {
    application {
        mainClass = "app.yomi.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Yomi"
            packageVersion = "1.0.0"
            description = "Yomi — design your day, score your day."
            vendor = "Yomi"
        }
    }
}
