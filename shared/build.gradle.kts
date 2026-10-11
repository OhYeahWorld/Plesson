import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("PlessonDatabase") {
            packageName.set("rea.ohyeahworld.plesson")
            // Явно указываем каталог со схемой (.sq/.sqm) и запросами.
            // Без этого плагин не находит базу и падает с ошибкой
            // "SQLDelight Gradle plugin was applied but there are no databases set up".
            srcDirs.setFrom("src/commonMain/sqldelight")
            // Схема описана миграцией 0001.sqm — разрешаем её как начальную.
            deriveSchemaFromMigrations.set(true)
        }
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    jvm()

    // В новых версиях Kotlin Multiplatform блоки targets объявляются через builder-функции
    // `js { ... }` / `wasmJs { ... }`, а не через устаревшие свойства `target.js { }`.
    // Старый синтаксис (`js("browser")`/свойство `js`) давал ошибку
    // "Unresolved reference 'js'" при конфигурации Gradle.
    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    android {
        namespace = "rea.ohyeahworld.plesson.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.android.driver)
        }

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
            implementation(libs.sqldelight.runtime)
        }

        // Добавляем logback ТОЛЬКО для JVM
        jvmMain.dependencies {
            implementation("ch.qos.logback:logback-classic:1.5.16")
            implementation(libs.sqldelight.jvm.driver)
            implementation(libs.ktor.client.cio)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        jsMain.dependencies {
            implementation(libs.wrappers.browser)
            implementation(libs.ktor.client.cio)
        }

        wasmJsMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}