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

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.DynamicFeatureExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.DynamicFeatureAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.gradle.LibraryExtension
import com.jonatbergn.jacoco.JacocoConfigExtension
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.hasPlugin
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.jvm

@Suppress("UnstableApiUsage")
internal class JacocoConfig(
    private val ext: JacocoConfigExtension,
) {

    private fun JacocoReport.configureReportContainer() = reports {
        val destinationDir = project.buildDir.resolve("reports/jacoco")
        html.apply {
            required.set(ext.isHtmlEnabled)
            outputLocation.set(destinationDir)
        }
        xml.apply {
            required.set(ext.isXmlEnabled)
            outputLocation.set(destinationDir.resolve("jacoco.xml"))
        }
        csv.apply {
            required.set(ext.isCsvEnabled)
            outputLocation.set(destinationDir.resolve("jacoco.csv"))
        }
    }

    private fun Project.configureJacocoPlugin() {
        if (!plugins.hasPlugin(JacocoPlugin::class)) plugins.apply(JacocoPlugin::class.java)
        extensions.configure<JacocoPluginExtension> { toolVersion = ext.jacocoVersion }
    }

    private fun Project.configureJacocoPluginAndroidApplication() {
        extensions.configure<ApplicationExtension> {
            testCoverage.jacocoVersion = ext.jacocoVersion
        }
    }

    private fun Project.configureJacocoPluginAndroidLibrary() {
        extensions.configure<LibraryExtension> {
            testCoverage.jacocoVersion = ext.jacocoVersion
        }
    }

    private fun Project.configureJacocoPluginAndroidFeature() {
        extensions.configure<DynamicFeatureExtension> {
            testCoverage.jacocoVersion = ext.jacocoVersion
        }
    }

    private fun Project.createJacocoReportTaskJvm(
        extension: KotlinMultiplatformExtension,
    ) {
        val testTaskName = "jvmTest"
        val testTask = tasks.findByName(testTaskName)
        val reportTask = tasks.create<JacocoReport>("jacocoTestReportJvm") {
            group = reportTaskGroup
            description = "Generate Jacoco coverage reports for after running jvm tests."
            configureReportContainer()
            val classDirs = fileTree(buildDir)
                .setIncludes(listOf("**/classes/**/main/**"))
                .setExcludes(ext.excludes)
            val sourceDirs = extension.sourceSets.flatMap { it.kotlin.srcDirs }
            classDirectories.setFrom(classDirs)
            sourceDirectories.setFrom(sourceDirs)
            executionData.setFrom("$buildDir/jacoco/${testTaskName}.exec")
            dependsOn(testTask)
        }
        defaultReportTask.dependsOn(reportTask)
        tasks["check"].dependsOn(reportTask)
    }

    private fun Project.configureJacocoReportTaskJvm() {
        val testTaskName = "test"
        val testTask = tasks.findByName(testTaskName)
        val reportTask = tasks.create<JacocoReport>("jacocoTestReportJvm") {
            group = reportTaskGroup
            description = "Generate Jacoco coverage reports for after running jvm tests."
            configureReportContainer()
            val classDirs = fileTree(buildDir)
                .setIncludes(listOf("**/classes/**/main/**"))
                .setExcludes(ext.excludes)
            val sourceDirs = javaSourceDirs()
            classDirectories.setFrom(classDirs)
            sourceDirectories.setFrom(sourceDirs)
            executionData.setFrom("$buildDir/jacoco/${testTaskName}.exec")
            dependsOn(testTask)
        }
        defaultReportTask.dependsOn(reportTask)
        tasks["check"].dependsOn(reportTask)
    }

    private fun Project.createJacocoReportTaskAndroid(
        identity: ComponentIdentity,
    ) {
        val buildVariant = identity.name
        val buildType = identity.buildType
        val productFlavor = identity.flavorName
        val reportTask = tasks.create<JacocoReport>("jacocoTestReport${buildVariant.capitalize()}") {
            group = reportTaskGroup
            description = "Generate Jacoco coverage reports after running $buildVariant tests."
            configureReportContainer()
            val classDirs = fileTree(buildDir) {
                setIncludes(listOf("**/tmp/kotlin-classes/$buildVariant/**"))
                setExcludes(ext.excludes)
            }
            val sourceDirs = listOfNotNull(
                androidSourceDirs("main"),
                androidSourceDirs(buildVariant),
                productFlavor?.let { androidSourceDirs(it) },
                buildType?.let { androidSourceDirs(it) }
            ).flatten().toSet()
            classDirectories.setFrom(classDirs)
            sourceDirectories.setFrom(files(sourceDirs))
            val testTaskName = "test${buildVariant.capitalize()}UnitTest"
            val executionDataFiles = fileTree("$buildDir/jacoco/")
                .matching { include("${testTaskName}.exec") }
            executionData.setFrom(executionDataFiles)
            dependsOn(testTaskName)
        }
        if (ext.defaultReportVariant(identity)) defaultReportTask.dependsOn(reportTask)
        tasks["check"].dependsOn(reportTask)
    }

    private fun Project.createJacocoReportTasksAndroidApplication() {
        extensions.configure<ApplicationAndroidComponentsExtension> {
            onVariants { createJacocoReportTaskAndroid(it) }
        }
    }

    private fun Project.createJacocoReportTasksAndroidLibrary() {
        extensions.configure<LibraryAndroidComponentsExtension> {
            onVariants { createJacocoReportTaskAndroid(it) }
        }
    }

    private fun Project.createJacocoReportTasksAndroidFeature() {
        extensions.configure<DynamicFeatureAndroidComponentsExtension> {
            onVariants { createJacocoReportTaskAndroid(it) }
        }
    }

    fun configure() = with(ext.project) {
        configureJacocoPlugin()
        when {
            extensions.findByType<ApplicationExtension>() != null -> {
                configureJacocoPluginAndroidApplication()
                createJacocoReportTasksAndroidApplication()
            }
            extensions.findByType<LibraryExtension>() != null -> {
                configureJacocoPluginAndroidLibrary()
                createJacocoReportTasksAndroidLibrary()
            }
            extensions.findByType<DynamicFeatureExtension>() != null -> {
                configureJacocoPluginAndroidFeature()
                createJacocoReportTasksAndroidFeature()
            }
            else -> afterEvaluate {
                extensions.findByType<KotlinMultiplatformExtension>()
                    ?.takeIf { it.hasPlatformType(jvm) }
                    ?.let { createJacocoReportTaskJvm(it) }
                    ?: extensions.findByType<JavaPluginExtension>()
                        ?.let { configureJacocoReportTaskJvm() }
            }
        }
    }

    private companion object {
        const val reportTaskGroup = "Reporting"
        const val defaultReportTaskName = "jacocoReportDefault"
        val Project.defaultReportTask
            get() = rootProject.tasks.run {
                findByName(defaultReportTaskName) ?: create(defaultReportTaskName) {
                    group = reportTaskGroup
                    description = "Generate Jacoco coverage reports for default variants."
                }
            }
        val languages = listOf("java", "kotlin")
        fun androidSourceDirs(variant: String) = languages.map { language -> "src/$variant/$language" }
        fun javaSourceDirs() = languages.map { language -> "src/main/$language" }
        fun KotlinMultiplatformExtension.hasPlatformType(type: KotlinPlatformType) =
            targets.any { it.platformType == type }
    }
}
