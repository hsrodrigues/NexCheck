import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

// ==============================================================
// MOTOR DE VERSIONAMENTO AUTOMÁTICO
// ==============================================================
val versionFile = file("version.properties")
val versionProps = Properties()

if (!versionFile.exists()) {
    versionFile.writeText("BUILD_NUMBER=0")
}

versionProps.load(versionFile.inputStream())
var currentBuild = (versionProps.getProperty("BUILD_NUMBER") ?: "0").toInt()

val taskRequests = gradle.startParameter.taskRequests.toString()
val isBuilding = taskRequests.contains("assemble") || taskRequests.contains("bundle")

if (isBuilding) {
    currentBuild += 1
    versionProps.setProperty("BUILD_NUMBER", currentBuild.toString())
    versionProps.store(versionFile.outputStream(), "Auto-incremented build number")
}
// ==============================================================

// Assinatura do release: lida de keystore.properties (fora do git).
// Sem o arquivo, o release é gerado sem assinatura.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.nexcheck.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.nexcheck.app"
        minSdk = 24
        targetSdk = 37
        versionCode = currentBuild
        versionName = "1.0.$currentBuild"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = false
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
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)

    // Firebase (versões controladas pelo BoM) e login Google
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

}
