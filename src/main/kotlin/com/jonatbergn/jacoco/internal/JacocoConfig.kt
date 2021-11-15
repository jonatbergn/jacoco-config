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
import java.io.File
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.JvmTestSuitePlugin
import org.gradle.api.tasks.util.PatternFilterable
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
        if (!plugins.hasPlugin(JvmTestSuitePlugin::class)) plugins.apply(
            JvmTestSuitePlugin::class.java)
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

    private fun JacocoReport.addTestDataAndDirs(
        classDirs: PatternFilterable,
        sourceDirs: List<File>,
        execFile: File?,
        testTask: Task,
    ) {
        classDirectories.from(classDirs)
        sourceDirectories.from(sourceDirs)
        executionData.from(execFile ?: testTask)
        dependsOn(testTask)
    }

    private fun JacocoReport.configureReport(
        classDirs: PatternFilterable,
        sourceDirs: List<File>,
        execFile: File?,
        testTask: Task,
        addToRootTask: Boolean,
    ) {
        addTestDataAndDirs(
            classDirs = classDirs,
            sourceDirs = sourceDirs,
            execFile = execFile,
            testTask = testTask
        )
        if (addToRootTask) {
            rootReportTask.addTestDataAndDirs(
                classDirs = classDirs,
                sourceDirs = sourceDirs,
                execFile = execFile,
                testTask = testTask,
            )
            rootReportTask.dependsOn(this)
        }
    }

    private fun Project.createJacocoReportTaskJvm(
        extension: KotlinMultiplatformExtension,
    ) {
        tasks.create<JacocoReport>("jacocoTestReportJvm") {
            group = reportTaskGroup
            description = "Generate Jacoco coverage reports for after running jvm tests."
            configureReportContainer()
            configureReport(
                addToRootTask = true,
                classDirs = fileTree(buildDir)
                    .setIncludes(listOf("**/classes/**/main/**"))
                    .setExcludes(ext.excludes),
                sourceDirs = extension.sourceSets.flatMap { it.kotlin.srcDirs },
                execFile = buildDir.resolve("jacoco/jvmTest.exec"),
                testTask = tasks["jvmTest"]
            )
            tasks["check"].dependsOn(this)
        }
    }

    private fun Project.configureJacocoReportTaskJvm() {
        tasks.create<JacocoReport>("jacocoTestReportJvm") {
            group = reportTaskGroup
            description = "Generate Jacoco coverage reports for after running jvm tests."
            configureReportContainer()
            configureReport(
                addToRootTask = true,
                classDirs = fileTree(buildDir)
                    .setIncludes(listOf("**/classes/**/main/**"))
                    .setExcludes(ext.excludes),
                sourceDirs = javaSourceDirs(),
                execFile = buildDir.resolve("jacoco/test.exec"),
                testTask = tasks["test"]
            )
            tasks["check"].dependsOn(this)
        }
    }

    private fun Project.createJacocoReportTaskAndroid(
        identity: ComponentIdentity,
    ) = afterEvaluate {
        val buildVariant = identity.name
        val buildType = identity.buildType
        val productFlavor = identity.flavorName
        val testTaskName = "test${buildVariant.capitalize()}UnitTest"
        tasks.create<JacocoReport>("jacocoTestReport${buildVariant.capitalize()}") {
            group = reportTaskGroup
            description = "Generate Jacoco coverage reports after running $buildVariant tests."
            configureReportContainer()
            configureReport(
                addToRootTask = ext.rootReportVariant(identity),
                classDirs = fileTree(buildDir)
                    .setIncludes(listOf("**/tmp/kotlin-classes/$buildVariant/**"))
                    .setExcludes(ext.excludes),
                sourceDirs = listOfNotNull(
                    androidSourceDirs("main"),
                    androidSourceDirs(buildVariant),
                    productFlavor?.let { androidSourceDirs(it) },
                    buildType?.let { androidSourceDirs(it) }
                ).flatten(),
                execFile = buildDir.resolve("jacoco/${testTaskName}.exec"),
                testTask = tasks[testTaskName],
            )
            tasks["check"].dependsOn(this)
        }
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
        when {
            this == rootProject -> configureJacocoPlugin()
            extensions.findByType<ApplicationExtension>() != null -> {
                configureJacocoPlugin()
                configureJacocoPluginAndroidApplication()
                createJacocoReportTasksAndroidApplication()
            }
            extensions.findByType<LibraryExtension>() != null -> {
                configureJacocoPlugin()
                configureJacocoPluginAndroidLibrary()
                createJacocoReportTasksAndroidLibrary()
            }
            extensions.findByType<DynamicFeatureExtension>() != null -> {
                configureJacocoPlugin()
                configureJacocoPluginAndroidFeature()
                createJacocoReportTasksAndroidFeature()
            }
            else -> afterEvaluate {
                extensions.findByType<KotlinMultiplatformExtension>()
                    ?.takeIf { it.hasPlatformType(jvm) }
                    ?.let {
                        configureJacocoPlugin()
                        createJacocoReportTaskJvm(it)
                    } ?: extensions.findByType<JavaPluginExtension>()
                    ?.let {
                        configureJacocoPlugin()
                        configureJacocoReportTaskJvm()
                    }
            }
        }
    }

    private val rootReportTask = with(ext.project.rootProject) {
        tasks.findByName(rootReportTaskName)?.let { it as JacocoReport }
            ?: tasks.create<JacocoReport>(rootReportTaskName) {
                group = reportTaskGroup
                description = "Generate Jacoco coverage reports for default variants."
                configureReportContainer()
                doFirst {
                    executionData.forEach {
                        if (!it.exists()) {
                            logger.info(
                                "Execution data is registered for aggregated report, but missing: ${it.path}"
                            )
                        }
                    }
                }
            }
    }

    private companion object {
        const val reportTaskGroup = "Reporting"
        const val rootReportTaskName = "jacocoAggregatedReport"
        val languages = listOf("java", "kotlin")
        fun Project.androidSourceDirs(variant: String) = languages.map { file("src/$variant/$it") }
        fun Project.javaSourceDirs() = languages.map { file("src/main/$it") }
        fun KotlinMultiplatformExtension.hasPlatformType(type: KotlinPlatformType) =
            targets.any { it.platformType == type }
    }
}
