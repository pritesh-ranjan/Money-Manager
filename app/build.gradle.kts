import java.io.FileInputStream
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
  if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use { load(it) }
  }
}

android {
  namespace = "com.moneymanager.app"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.moneymanager.app"
    minSdk = 26
    targetSdk = 36
    versionCode = 23
    versionName = "2.0.14"
  }

  signingConfigs {
    create("release") {
      val ksPath = keystoreProperties.getProperty("storeFile")
        ?: System.getenv("KEYSTORE_FILE")
        ?: "../keystore/release.jks"
      storeFile = file(ksPath)
      storePassword = keystoreProperties.getProperty("storePassword")
        ?: System.getenv("KEYSTORE_PASSWORD")
      keyAlias = keystoreProperties.getProperty("keyAlias")
        ?: System.getenv("KEY_ALIAS")
      keyPassword = keystoreProperties.getProperty("keyPassword")
        ?: System.getenv("KEY_PASSWORD")
      enableV1Signing = true
      enableV2Signing = true
      enableV3Signing = true
      enableV4Signing = true
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      isDebuggable = false
      signingConfig = signingConfigs.getByName("release")
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      ndk {
        debugSymbolLevel = "FULL"
      }
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
    aidl = false
    buildConfig = false
    shaders = false
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
      excludes += "/META-INF/*.kotlin_module"
      excludes += "/META-INF/version-control-info.textproto"
      excludes += "org/bouncycastle/pqc/**"
      excludes += "/org/bouncycastle/pqc/**"
    }
  }

  testOptions {
    unitTests {
      isReturnDefaultValues = true
    }
  }
}

kotlin {
  jvmToolchain(17)
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Room SQLite
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation("org.json:json:20240303")

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // WorkManager (Background Reconciliation)
  implementation(libs.androidx.work.runtime)

  // PDFBox for Android (Bank Statement Parsing Engine)
  implementation(libs.pdfbox.android)

  // OkHttp (Direct official Google OAuth 2.0 & Gmail v1 REST API)
  implementation(libs.okhttp)

  // Chrome Custom Tabs (Secure OAuth flow with explicit consent)
  implementation(libs.androidx.browser)
}
