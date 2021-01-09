/*
 *    Copyright 2021 Jonathan Bergen
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.jonatbergn.jacoco.internal

import com.android.build.api.extension.ApplicationAndroidComponentsExtension
import com.android.build.api.extension.LibraryAndroidComponentsExtension
import com.android.build.api.variant.Variant
import com.jonatbergn.jacoco.JacocoConfigPluginExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

internal object JacocoConfig {

    private val mainSourceDirs by lazy { sourceDirs("main") }
    private fun sourceDirs(name: String) = LanguageName.values().map { "src/$name/${it.value}" }
    private fun Project.executionDataFile(fileName: String) = files("$buildDir/jacoco/${fileName}.exec")

    private fun Project.applyJacocoPlugin(ext: JacocoConfigPluginExtension) {
        plugins.apply(JacocoPlugin::class.java)
        afterEvaluate {
            extensions.configure<JacocoPluginExtension> {
                toolVersion = ext.jacocoVersion
            }
        }
    }

    private fun Project.configureJacocoReportTask(
        ext: JacocoConfigPluginExtension,
        testTaskName: String,
        reportTask: JacocoReport
    ) = reportTask.run {
        group = "Reporting"
        description = "Generate Jacoco coverage reports."
        dependsOn(testTaskName)
        tasks["check"].dependsOn(this)
        reports {
            xml.isEnabled = ext.isXmlEnabled
            csv.isEnabled = ext.isCsvEnabled
            html.isEnabled = ext.isHtmlEnabled
        }
        val classPaths = listOf("**/classes/**/main/**")
        val classDirs = fileTree(buildDir) { setIncludes(classPaths); setExcludes(ext.excludes) }
        classDirectories.setFrom(classDirs)
        additionalSourceDirs.setFrom(files(mainSourceDirs))
        sourceDirectories.setFrom(files(mainSourceDirs))
        executionData.setFrom(executionDataFile(testTaskName))
    }

    private fun Project.configureJacocoReportTaskJvm(ext: JacocoConfigPluginExtension) {
        tasks.withType<JacocoReport> { configureJacocoReportTask(ext, "test", this) }
    }

    private fun Project.createJacocoReportTaskJvm(ext: JacocoConfigPluginExtension) {
        tasks.create<JacocoReport>("jacocoTestReport") {
            configureJacocoReportTask(ext, "jvmTest", this)
        }
    }

    private fun Project.createJacocoReportTaskAndroid(
        ext: JacocoConfigPluginExtension,
        variant: Variant
    ) {
        val productFlavor = variant.flavorName
        val buildType = variant.buildType.orEmpty()
        val productName = variant.name
        val (sourceName, sourcePath) = if (productFlavor.isEmpty()) {
            buildType to buildType
        } else {
            productName to "$productFlavor/$buildType"
        }
        val taskName = "jacocoTestReport${sourceName.capitalize()}"
        val testTaskName = "test${sourceName.capitalize()}UnitTest"
        tasks.create<JacocoReport>(taskName) {
            group = "Reporting"
            description = "Generate Jacoco coverage reports after running $sourceName tests."
            dependsOn(testTaskName)
            tasks["check"].dependsOn(this)
            reports {
                val destinationDir = "$buildDir/reports/jacoco/${sourceName}"
                html.run {
                    isEnabled = ext.isHtmlEnabled
                    destination = file(destinationDir)
                }
                xml.run {
                    isEnabled = ext.isXmlEnabled
                    destination = file("$destinationDir/jacoco.xml")
                }
                csv.run {
                    isEnabled = ext.isCsvEnabled
                    destination = file("$destinationDir/jacoco.csv")
                }
            }
            val classPaths = listOfNotNull(
                "**/intermediates/classes/${sourcePath}/**",
                "**/tmp/kotlin-classes/${sourcePath}/**"
                    .takeIf { hasKotlinPlugin() },
                "**/tmp/kotlin-classes/$productName/**"
                    .takeIf { hasKotlinPlugin() }
                    .takeUnless { productFlavor.isEmpty() }
            )
            val classDirs = fileTree(buildDir) { setIncludes(classPaths); setExcludes(ext.excludes) }
            val flavorSourceDirs = sourceDirs(productFlavor)
            val buildTypeSourceDirs = sourceDirs(buildType).takeUnless { productFlavor.isEmpty() }.orEmpty()
            val sourceDirs = mainSourceDirs + flavorSourceDirs + buildTypeSourceDirs
            classDirectories.setFrom(classDirs)
            additionalSourceDirs.setFrom(files(sourceDirs))
            sourceDirectories.setFrom(files(sourceDirs))
            executionData.setFrom(executionDataFile(testTaskName))
        }
    }

    private fun Project.createJacocoReportTasksAndroidApplication(ext: JacocoConfigPluginExtension) {
        extensions.configure<ApplicationAndroidComponentsExtension> {
            onVariants { createJacocoReportTaskAndroid(ext, it) }
        }
    }

    private fun Project.createJacocoReportTasksAndroidLibrary(ext: JacocoConfigPluginExtension) {
        extensions.configure<LibraryAndroidComponentsExtension> {
            onVariants { createJacocoReportTaskAndroid(ext, it) }
        }
    }

    private fun Project.hasPlugin(plugin: PluginId) = plugins.hasPlugin(plugin.value)

    private fun Project.hasKotlinPlugin(): Boolean {
        if (hasPlugin(PluginId.KotlinAndroid)) return true
        if (hasPlugin(PluginId.KotlinMultiplatform)) return true
        return false
    }

    private fun Project.hasAndroidPlugin(): Boolean {
        if (hasPlugin(PluginId.AndroidApplication)) return true
        if (hasPlugin(PluginId.AndroidLibrary)) return true
        if (hasPlugin(PluginId.AndroidTest)) return true
        if (hasPlugin(PluginId.AndroidFeature)) return true
        if (hasPlugin(PluginId.AndroidDynamicFeature)) return true
        if (hasPlugin(PluginId.AndroidInstantApp)) return true
        return false
    }

    private fun Project.hasJavaPlugin(): Boolean {
        if (hasPlugin(PluginId.Java)) return true
        if (hasPlugin(PluginId.JavaLibrary)) return true
        if (hasPlugin(PluginId.JavaGradlePlugin)) return true
        return false
    }

    private fun Project.hasJvmTarget() = extensions.findByType<KotlinMultiplatformExtension>()
        ?.targets
        .orEmpty()
        .any { it.platformType == KotlinPlatformType.jvm }

    fun Project.applicable() = PluginId.values().run {
        if (none { hasPlugin(it) }) {
            logger.warn(
                "The jacoco-config plugin cannot be applied prior to applying at least one of the following plugins:\n${
                    map { it.value }.sorted().joinToString("\n") { "- '$it'" }
                }"
            )
            false
        } else {
            true
        }
    }

    fun Project.configureJacoco(ext: JacocoConfigPluginExtension) {
        applyJacocoPlugin(ext)
        when {
            hasPlugin(PluginId.AndroidApplication) -> createJacocoReportTasksAndroidApplication(ext)
            hasAndroidPlugin() -> createJacocoReportTasksAndroidLibrary(ext)
        }
        when {
            hasPlugin(PluginId.KotlinMultiplatform) -> afterEvaluate {
                if (hasJvmTarget()) createJacocoReportTaskJvm(ext)
            }
            hasJavaPlugin() -> configureJacocoReportTaskJvm(ext)
        }
    }
}