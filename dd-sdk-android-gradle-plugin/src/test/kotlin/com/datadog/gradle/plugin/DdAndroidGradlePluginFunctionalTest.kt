/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2020-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin

import com.datadog.gradle.plugin.utils.initializeGit
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.internal.PluginUnderTestMetadataReading
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Locale
import java.util.Properties
import kotlin.io.path.Path

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class DdAndroidGradlePluginFunctionalTest {
    @TempDir
    lateinit var testProjectDir: File
    private lateinit var appRootDir: File
    private lateinit var libModuleRootDir: File
    private lateinit var appMainSrcDir: File
    private lateinit var appKotlinSourcesDir: File
    private lateinit var libModuleMainSrcDir: File
    private lateinit var libModuleKotlinSourcesDir: File
    private lateinit var settingsFile: File
    private lateinit var appBuildGradleFile: File
    private lateinit var libModuleBuildGradleFile: File
    private lateinit var appManifestFile: File
    private lateinit var libModuleManifestFile: File
    private lateinit var gradlePropertiesFile: File
    private lateinit var sampleApplicationClassFile: File
    private lateinit var libModulePlaceholderFile: File

    @StringForgery(regex = "http[s]?://github\\.com:[1-9]{2}/[a-z]+/repository\\.git")
    lateinit var fakeRemoteUrl: String
    private val colors = listOf("Blue", "Green")
    private val versions = listOf("Demo", "Full")
    private val variants: List<String> by lazy {
        versions.flatMap { version ->
            colors.map {
                "${version.lowercase()}$it"
            }
        }
    }

    private lateinit var datadogCiFile: File

    @BeforeEach
    fun `set up`(forge: Forge) {
        appRootDir = File(testProjectDir, "samples/app").apply { mkdirs() }
        libModuleRootDir = File(testProjectDir, "samples/lib-module").apply { mkdirs() }
        settingsFile = File(testProjectDir, "settings.gradle")
        gradlePropertiesFile = File(testProjectDir, "gradle.properties")
        appMainSrcDir = File(appRootDir, "src/main").apply { mkdirs() }
        appKotlinSourcesDir = File(appMainSrcDir, "kotlin").apply { mkdirs() }
        libModuleMainSrcDir = File(libModuleRootDir, "src/main").apply { mkdirs() }
        libModuleKotlinSourcesDir = File(libModuleMainSrcDir, "kotlin").apply { mkdirs() }
        appBuildGradleFile = File(appRootDir, "build.gradle")
        libModuleBuildGradleFile = File(libModuleRootDir, "build.gradle")
        appManifestFile = File(appMainSrcDir, "AndroidManifest.xml")
        libModuleManifestFile = File(libModuleMainSrcDir, "AndroidManifest.xml")
        sampleApplicationClassFile = File(appKotlinSourcesDir, "SampleApplication.kt")
        libModulePlaceholderFile = File(libModuleKotlinSourcesDir, "Placeholder.kt")
        datadogCiFile = File(testProjectDir.parent, "datadog-ci.json")

        // we need to check that our plugin supports different AGP versions (backward and forward
        // compatible)
        val agpVersion = forge.anElementFrom(OLD_AGP_VERSION, LATEST_AGP_VERSION)
        stubFile(settingsFile, SETTINGS_FILE_CONTENT.format(Locale.US, agpVersion))
        stubFile(sampleApplicationClassFile, APPLICATION_CLASS_CONTENT)
        stubFile(appManifestFile, APP_MANIFEST_FILE_CONTENT)
        stubFile(gradlePropertiesFile, GRADLE_PROPERTIES_FILE_CONTENT)
        stubFile(libModulePlaceholderFile, LIB_MODULE_PLACEHOLDER_CLASS_CONTENT)
        stubFile(libModuleManifestFile, LIB_MODULE_MANIFEST_FILE_CONTENT)
        stubGradleBuildFromResourceFile(
            "lib_module_build.gradle",
            libModuleBuildGradleFile
        )
        initializeGit(fakeRemoteUrl, appRootDir)
    }

    @AfterEach
    fun `tear down`() {
        if (datadogCiFile.exists()) {
            datadogCiFile.delete()
        }
    }

    // region Assemble

    @Test
    fun `M success W assembleRelease`() {
        // Given
        stubGradleBuildFromResourceFile(
            "build_with_datadog_dep.gradle",
            appBuildGradleFile
        )
        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(":samples:app:assembleRelease")
            .withPluginClasspath(getTestConfigurationClasspath())
            .build()

        // Then
        assertThat(result.task(":samples:app:assembleRelease")?.outcome)
            .isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `M success W assembleRelease { project with library module }`() {
        // Given
        stubGradleBuildFromResourceFile(
            "build_with_lib_module_attached.gradle",
            appBuildGradleFile
        )
        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(":samples:app:assembleRelease")
            .withPluginClasspath(getTestConfigurationClasspath())
            .build()

        // Then
        assertThat(result.task(":samples:app:assembleRelease")?.outcome)
            .isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `M success W assembleRelease { build cache }`() {
        // Given
        stubGradleBuildFromResourceFile(
            "build_with_datadog_dep.gradle",
            appBuildGradleFile
        )

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("--build-cache", ":samples:app:assembleRelease")
            .withPluginClasspath(getTestConfigurationClasspath())
            .build()

        // Then
        assertThat(result.task(":samples:app:assembleRelease")?.outcome)
            .isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `M success W assembleDebug`() {
        // Given
        stubGradleBuildFromResourceFile(
            "build_with_datadog_dep.gradle",
            appBuildGradleFile
        )
        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(":samples:app:assembleDebug")
            .withPluginClasspath(getTestConfigurationClasspath())
            .build()

        // Then
        assertThat(result.task(":samples:app:assembleDebug")?.outcome)
            .isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `M success W assembleDebug { project with library module }`() {
        // Given
        stubGradleBuildFromResourceFile(
            "build_with_lib_module_attached.gradle",
            appBuildGradleFile
        )
        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(":samples:app:assembleDebug")
            .withPluginClasspath(getTestConfigurationClasspath())
            .build()

        // Then
        assertThat(result.task(":samples:app:assembleDebug")?.outcome)
            .isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `M success W assembleDebug { build cache }`() {
        // Given
        stubGradleBuildFromResourceFile(
            "build_with_datadog_dep.gradle",
            appBuildGradleFile
        )

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("--build-cache", ":samples:app:assembleDebug")
            .withPluginClasspath(getTestConfigurationClasspath())
            .build()

        // Then
        assertThat(result.task(":samples:app:assembleDebug")?.outcome)
            .isEqualTo(TaskOutcome.SUCCESS)
    }

    @Disabled(
        "This test is ignored for now because of these tasks: " +
            "collect[Flavour1][Flavour2]ReleaseDependencies. This is caused under the hood" +
            "by this task: PerModuleReportDependenciesTask which accesses the project object" +
            "inside the action method. " +
            "There is already an opened issue here: https://github.com/gradle/gradle/issues/12871"
    )
    @Test
    fun `M success W assembleRelease { configuration cache, checkProjectDependencies enabled }`() {
        // TODO: https://datadoghq.atlassian.net/browse/RUMM-1894

        // Given
        stubGradleBuildFromResourceFile(
            "build_with_datadog_dep.gradle",
            appBuildGradleFile
        )

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("--configuration-cache", ":samples:app:assembleRelease")
            .withPluginClasspath(getTestConfigurationClasspath())
            .build()

        // Then
        assertThat(result.task(":samples:app:assembleRelease")?.outcome)
            .isEqualTo(TaskOutcome.SUCCESS)
    }

    @Disabled(
        "This test is ignored for now as we are using the Configuration object at the " +
            "task action level in our DdCheckSdkDepsTask and this is breaking " +
            "the --configuration-cache. There is no workaround this yet and this is " +
            "also present in some internal build.gradle tasks (see the test comment above)"
    )
    @Test
    fun `M success W assembleDebug { configuration cache, checkProjectDependencies enabled }`() {
        // TODO: https://datadoghq.atlassian.net/browse/RUMM-1893

        // Given
        stubGradleBuildFromResourceFile(
            "build_with_datadog_dep.gradle",
            appBuildGradleFile
        )

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("--configuration-cache", ":samples:app:assembleDebug")
            .withPluginClasspath(getTestConfigurationClasspath())
            .build()

        // Then
        assertThat(result.task(":samples:app:assembleDebug")?.outcome)
            .isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `M success W assembleDebug { configuration cache, checkDependencies disabled }`() {
        // Given
        stubGradleBuildFromResourceFile(
            "build_with_check_deps_disabled.gradle",
            appBuildGradleFile
        )

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("--configuration-cache", ":samples:app:assembleDebug")
            .withPluginClasspath(getTestConfigurationClasspath())
            .build()

        // Then
        assertThat(result.task(":samples:app:assembleDebug")?.outcome)
            .isEqualTo(TaskOutcome.SUCCESS)
    }

    @Disabled(
        "This test is ignored for now because of these tasks: " +
            "collect[Flavour1][Flavour2]ReleaseDependencies. This is caused under the hood" +
            "by this task: PerModuleReportDependenciesTask which accesses the project object" +
            "inside the action method. " +
            "There is already an opened issue here: https://github.com/gradle/gradle/issues/12871"
    )
    @Test
    fun `M success W assembleRelease { configuration cache, checkDependencies disabled  }`() {
        // TODO: https://datadoghq.atlassian.net/browse/RUMM-1894

        // Given
        stubGradleBuildFromResourceFile(
            "build_with_check_deps_disabled.gradle",
            appBuildGradleFile
        )

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("--configuration-cache", ":samples:app:assembleRelease")
            .withPluginClasspath(getTestConfigurationClasspath())
            .build()

        // Then
        assertThat(result.task(":samples:app:assembleRelease")?.outcome)
            .isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `M success W assembleRelease { Datadog SDK not in deps, checkDependencies to warn }`() {
        // Given
        stubGradleBuildFromResourceFile(
            "build_with_check_deps_set_to_warn.gradle",
            appBuildGradleFile
        )

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(":samples:app:assembleRelease")
            .withPluginClasspath(getTestConfigurationClasspath())
            .build()

        // Then
        assertThat(result.task(":samples:app:assembleRelease")?.outcome)
            .isEqualTo(TaskOutcome.SUCCESS)
        for (it in variants) {
            assertThat(result.output).contains(
                "Following application variant doesn't have " +
                    "Datadog SDK included: ${it}Release"
            )
        }
    }

    @Test
    fun `M success W assembleDebug { Datadog SDK not in deps, checkDependencies to warn }`() {
        // Given
        stubGradleBuildFromResourceFile(
            "build_with_check_deps_set_to_warn.gradle",
            appBuildGradleFile
        )

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(":samples:app:assembleDebug")
            .withPluginClasspath(getTestConfigurationClasspath())
            .build()

        // Then
        assertThat(result.task(":samples:app:assembleDebug")?.outcome)
            .isEqualTo(TaskOutcome.SUCCESS)
        for (it in variants) {
            assertThat(result.output).contains(
                "Following application variant doesn't have " +
                    "Datadog SDK included: ${it}Debug"
            )
        }
    }

    @Disabled("RUMM-2344")
    @Test
    fun `M fail W assembleRelease { Datadog SDK not in deps }`() {
        // Given
        stubGradleBuildFromResourceFile(
            "build_without_datadog_dep.gradle",
            appBuildGradleFile
        )

        // When
        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(":samples:app:assembleRelease")
            .withPluginClasspath(getTestConfigurationClasspath())
            .buildAndFail()
    }

    @Disabled("RUMM-2344")
    @Test
    fun `M fail W assembleDebug { Datadog SDK not in deps }`() {
        // Given
        stubGradleBuildFromResourceFile(
            "build_without_datadog_dep.gradle",
            appBuildGradleFile
        )

        // When
        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(":samples:app:assembleDebug")
            .withPluginClasspath(getTestConfigurationClasspath())
            .buildAndFail()
    }

    // TODO remove once RUMM-2344 is done
    @Test
    fun `M success W assembleRelease { Datadog SDK not in deps }`() {
        // Given
        stubGradleBuildFromResourceFile(
            "build_without_datadog_dep.gradle",
            appBuildGradleFile
        )

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(":samples:app:assembleRelease")
            .withPluginClasspath(getTestConfigurationClasspath())
            .build()

        // Then
        assertThat(result.task(":samples:app:assembleRelease")?.outcome)
            .isEqualTo(TaskOutcome.SUCCESS)
    }

    // TODO remove once RUMM-2344 is done
    @Test
    fun `M success W assembleDebug { Datadog SDK not in deps }`() {
        // Given
        stubGradleBuildFromResourceFile(
            "build_without_datadog_dep.gradle",
            appBuildGradleFile
        )

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(":samples:app:assembleDebug")
            .withPluginClasspath(getTestConfigurationClasspath())
            .build()

        // Then
        assertThat(result.task(":samples:app:assembleDebug")?.outcome)
            .isEqualTo(TaskOutcome.SUCCESS)
    }

    // endregion

    // region Upload

    @Test
    fun `M try to upload the mapping file W upload { using a fake API_KEY }`(forge: Forge) {
        // Given
        stubGradleBuildFromResourceFile(
            "build_with_datadog_dep.gradle",
            appBuildGradleFile
        )
        val color = forge.anElementFrom(colors)
        val version = forge.anElementFrom(versions)
        val variantVersionName = version.lowercase()
        val variant = "${version.lowercase()}$color"
        val taskName = resolveUploadTask(variant)

        // When
        // since there is no explicit dependency between assemble and upload tasks, Gradle may
        // optimize the execution and run them in parallel, ignoring the order in the command
        // line, so we do the explicit split
        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(":samples:app:assembleRelease")
            .withPluginClasspath(getTestConfigurationClasspath())
            .build()

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(taskName, "--info", "-PDD_API_KEY=fakekey")
            .withPluginClasspath(getTestConfigurationClasspath())
            .buildAndFail()

        // Then
        assertThat(result.output).contains("Creating request with GZIP encoding.")
        assertThat(result.output).contains(
            "Uploading mapping file with tags " +
                "`service:com.example.variants.$variantVersionName`, " +
                "`version:1.0-$variantVersionName`, " +
                "`variant:$variant` (site=datadoghq.com):"
        )
    }

    @Test
    fun `M try to upload the mapping file W upload { using a fake API_KEY, gzip disabled }`(forge: Forge) {
        // Given
        stubGradleBuildFromResourceFile(
            "build_with_datadog_dep.gradle",
            appBuildGradleFile
        )
        val color = forge.anElementFrom(colors)
        val version = forge.anElementFrom(versions)
        val variantVersionName = version.lowercase()
        val variant = "${version.lowercase()}$color"
        val taskName = resolveUploadTask(variant)

        // When
        // since there is no explicit dependency between assemble and upload tasks, Gradle may
        // optimize the execution and run them in parallel, ignoring the order in the command
        // line, so we do the explicit split
        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(":samples:app:assembleRelease")
            .withPluginClasspath(getTestConfigurationClasspath())
            .build()

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(taskName, "--info", "-PDD_API_KEY=fakekey", "-Pdd-disable-gzip")
            .withPluginClasspath(getTestConfigurationClasspath())
            .buildAndFail()

        // Then
        assertThat(result.output).contains("Creating request without GZIP encoding.")
        assertThat(result.output).contains(
            "Uploading mapping file with tags " +
                "`service:com.example.variants.$variantVersionName`, " +
                "`version:1.0-$variantVersionName`, " +
                "`variant:$variant` (site=datadoghq.com):"
        )
    }

    @Test
    fun `M try to upload the mapping file W upload { datadog-ci file, parent dir }`(forge: Forge) {
        // Given
        stubGradleBuildFromResourceFile(
            "build_with_datadog_dep.gradle",
            appBuildGradleFile
        )
        val color = forge.anElementFrom(colors)
        val version = forge.anElementFrom(versions)
        val variantVersionName = version.lowercase()
        val variant = "${version.lowercase()}$color"
        val taskName = resolveUploadTask(variant)

        datadogCiFile.createNewFile()

        datadogCiFile.writeText(
            JSONObject().apply {
                put("apiKey", "someKey")
                put("datadogSite", "datadoghq.eu")
            }.toString()
        )

        // When
        // since there is no explicit dependency between assemble and upload tasks, Gradle may
        // optimize the execution and run them in parallel, ignoring the order in the command
        // line, so we do the explicit split
        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(":samples:app:assembleRelease")
            .withPluginClasspath(getTestConfigurationClasspath())
            .build()

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(taskName, "--info")
            .withPluginClasspath(getTestConfigurationClasspath())
            .buildAndFail()

        // Then
        assertThat(result.output).contains(
            "Uploading mapping file with tags " +
                "`service:com.example.variants.$variantVersionName`, " +
                "`version:1.0-$variantVersionName`, " +
                "`variant:$variant` (site=datadoghq.eu):"
        )
        assertThat(result.output).contains("API key found in Datadog CI config file, using it.")
        assertThat(result.output)
            .contains("Site property found in Datadog CI config file, using it.")
    }

    @Test
    fun `M try to upload the mapping file W upload { custom remote repos url }`(forge: Forge) {
        // Given
        stubGradleBuildFromResourceFile(
            "build_with_custom_remote_repos_url.gradle",
            appBuildGradleFile
        )
        val color = forge.anElementFrom(colors)
        val version = forge.anElementFrom(versions)
        val variantVersionName = version.lowercase()
        val variant = "${version.lowercase()}$color"
        val taskName = resolveUploadTask(variant)

        // When
        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(":samples:app:assembleRelease")
            .withPluginClasspath(getTestConfigurationClasspath())
            .build()

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(taskName, "--info", "-PDD_API_KEY=fakekey")
            .withPluginClasspath(getTestConfigurationClasspath())
            .buildAndFail()

        // Then
        assertThat(result.output).contains(
            "Uploading mapping file with tags " +
                "`service:com.example.variants.$variantVersionName`, " +
                "`version:1.0-$variantVersionName`, " +
                "`variant:$variant` (site=datadoghq.com):"
        )
        assertThat(result.output).contains(
            "http://github.com:fakeapp/repository.git"
        )
    }

    @Test
    fun `M try to upload the mapping file W upload { optimized mapping }`(forge: Forge) {
        // Given
        stubGradleBuildFromResourceFile(
            "build_with_optimized_mapping.gradle",
            appBuildGradleFile
        )
        val color = forge.anElementFrom(colors)
        val version = forge.anElementFrom(versions)
        val variantVersionName = version.lowercase()
        val variant = "${version.lowercase()}$color"
        val taskName = resolveUploadTask(variant)

        // When
        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(":samples:app:assembleRelease")
            .withPluginClasspath(getTestConfigurationClasspath())
            .build()

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(taskName, "--info", "-PDD_API_KEY=fakekey")
            .withPluginClasspath(getTestConfigurationClasspath())
            .buildAndFail()

        // Then
        assertThat(result.output).contains(
            "Uploading mapping file with tags " +
                "`service:com.example.variants.$variantVersionName`, " +
                "`version:1.0-$variantVersionName`, " +
                "`variant:$variant` (site=datadoghq.com):"
        )
        val optimizedFile = Path(
            appRootDir.path,
            "build",
            "outputs",
            "mapping",
            "${variant}Release",
            DdMappingFileUploadTask.MAPPING_OPTIMIZED_FILE_NAME
        ).toFile()
        assertThat(result.output).contains(
            "Size of optimized file is ${optimizedFile.length()} bytes"
        )
    }

    @Test
    fun `M try to upload the mapping file W upload {variant config override}`(forge: Forge) {
        // Given
        stubGradleBuildFromResourceFile(
            "build_with_variant_override.gradle",
            appBuildGradleFile
        )
        val color = forge.anElementFrom(colors)
        val variant = "pro$color"
        val taskName = resolveUploadTask(variant)

        // When
        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(":samples:app:assembleRelease")
            .withPluginClasspath(getTestConfigurationClasspath())
            .build()

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(taskName, "--info", "-PDD_API_KEY=fakekey")
            .withPluginClasspath(getTestConfigurationClasspath())
            .buildAndFail()

        // Then
        assertThat(result.output).contains("http://github.com:fakeapp-another/repository.git")
    }

    @Test
    fun `M not contain any uploadTasks W minifyNotEnabled`() {
        // Given
        stubGradleBuildFromResourceFile(
            "build_with_minify_not_enabled.gradle",
            appBuildGradleFile
        )

        // When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(":samples:app:tasks")
            .withPluginClasspath(getTestConfigurationClasspath())
            .build()

        // Then
        val uploadTask = result.output
            .split("\n")
            .firstOrNull { it.startsWith("upload") }
        assertThat(uploadTask).isNull()
    }

    // endregion

    // region Internal

    private fun resolveUploadTask(variantName: String) = "uploadMapping${variantName}Release"

    private fun stubFile(destination: File, content: String) {
        with(destination.outputStream()) {
            write(content.toByteArray())
        }
    }

    private fun stubGradleBuildFromResourceFile(resourceFilePath: String, gradleFile: File) {
        javaClass.classLoader.getResource(resourceFilePath)?.file?.let {
            File(it).copyTo(gradleFile)
        }
    }

    private fun getTestConfigurationClasspath(): List<File> {
        // we will use this classpath for the GradleRunner to make sure we have all the
        // required classes (AndroidGradle and KotlinGradle) to build the plugin. GradleRunner
        // creates its own process and by default the classpath used is the one from the `main`
        // configuration.
        val properties = Properties()
        properties["implementation-classpath"] = System.getProperty("java.class.path")
        return PluginUnderTestMetadataReading
            .readImplementationClasspath("gradle-runner-classpath", properties)
    }

    // endregion

    companion object {
        val APPLICATION_CLASS_CONTENT = """
            package com.datadog.android.sample

            import android.app.Application
            import android.util.Log

            class SampleApplication : Application() {

                override fun onCreate() {
                    super.onCreate()
                    Log.v("Application","Hello World")
                }
            }
        """.trimIndent()
        val LIB_MODULE_PLACEHOLDER_CLASS_CONTENT = """
            package com.example.lib
            
            class Placeholder {
            }
        """.trimIndent()
        val APP_MANIFEST_FILE_CONTENT = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                      package="com.example.variants">

                <application
                    android:allowBackup="true"
                    android:supportsRtl="true">
                    <activity android:name=".MainActivity" android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN"/>

                            <category android:name="android.intent.category.LAUNCHER"/>
                        </intent-filter>
                    </activity>
                </application>

            </manifest>
        """.trimIndent()
        val LIB_MODULE_MANIFEST_FILE_CONTENT = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.example.lib">

            </manifest>
        """.trimIndent()
        const val SETTINGS_FILE_CONTENT = """
            pluginManagement {
                resolutionStrategy {
                    eachPlugin {
                        if (requested.id.id == "com.android.application") {
                            useModule("com.android.tools.build:gradle:%s")
                        }
                        if (requested.id.id == "kotlin-android") {
                            useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.10")
                        }
                    }
                }
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                    google()
                }
            }
            include(":samples:app")
            include(":samples:lib-module")
        """
        val GRADLE_PROPERTIES_FILE_CONTENT = """
           org.gradle.jvmargs=-Xmx2560m
           android.useAndroidX=true
        """.trimIndent()

        const val OLD_AGP_VERSION = "7.1.2"
        const val LATEST_AGP_VERSION = "8.1.0"
    }
}
