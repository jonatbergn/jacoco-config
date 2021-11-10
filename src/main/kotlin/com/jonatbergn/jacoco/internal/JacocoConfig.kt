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
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.plugins.JavaPluginExtension
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

    private fun Project.configureReportContainer(
        task: JacocoReport,
    ) = task.reports {
        val destinationDir = buildDir.resolve("reports/jacoco")
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

    private fun JacocoReport.addTestDataAndDirs(
        execData: FileTree,
        classDirs: PatternFilterable,
        sourceDirs: List<File>,
        dependsOn: Any,
        classPath: FileCollection? = null,
    ) {
        classDirectories.setFrom(classDirectories + classDirs)
        sourceDirectories.setFrom(sourceDirectories + sourceDirs)
        executionData.setFrom(executionData + execData)
        dependsOn(dependsOn)
        if(classPath != null) jacocoClasspath = classPath
    }

    private fun Project.configureReport(
        task: JacocoReport,
        addToRootTask: Boolean,
        classDirs: PatternFilterable,
        sourceDirs: List<File>,
        testTaskName: String,
    ) {
        val execData = fileTree("$buildDir/jacoco/").matching { include("${testTaskName}.exec") }
        task.addTestDataAndDirs(
            execData = execData,
            classDirs = classDirs,
            sourceDirs = sourceDirs,
            dependsOn = testTaskName
        )
        if (!addToRootTask) return
        rootReportTask.addTestDataAndDirs(
            execData = execData,
            classDirs = classDirs,
            sourceDirs = sourceDirs,
            dependsOn = task,
            classPath = task.jacocoClasspath,
        )
    }

    private fun Project.createJacocoReportTaskJvm(
        extension: KotlinMultiplatformExtension,
    ) {
        val testTaskName = "jvmTest"
        val reportTask = tasks.create<JacocoReport>("jacocoTestReportJvm") {
            group = reportTaskGroup
            description = "Generate Jacoco coverage reports for after running jvm tests."
            configureReportContainer(this)
            configureReport(
                task = this,
                addToRootTask = true,
                classDirs = fileTree(buildDir)
                    .setIncludes(listOf("**/classes/**/main/**"))
                    .setExcludes(ext.excludes),
                sourceDirs = extension.sourceSets.flatMap { it.kotlin.srcDirs },
                testTaskName = testTaskName
            )
        }
        tasks["check"].dependsOn(reportTask)
    }

    private fun Project.configureJacocoReportTaskJvm() {
        val testTaskName = "test"
        val reportTask = tasks.create<JacocoReport>("jacocoTestReportJvm") {
            group = reportTaskGroup
            description = "Generate Jacoco coverage reports for after running jvm tests."
            configureReportContainer(this)
            configureReport(
                task = this,
                addToRootTask = true,
                classDirs = fileTree(buildDir)
                    .setIncludes(listOf("**/classes/**/main/**"))
                    .setExcludes(ext.excludes),
                sourceDirs = javaSourceDirs(),
                testTaskName = testTaskName,
            )
        }
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
            configureReportContainer(this)
            val testTaskName = "test${buildVariant.capitalize()}UnitTest"
            configureReport(
                task = this,
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
                testTaskName = testTaskName,
            )
        }
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

    private val rootReportTask = with(ext.project.rootProject) {
        tasks.findByName(rootReportTaskName)?.let { it as JacocoReport }
            ?: tasks.create<JacocoReport>(rootReportTaskName) {
                group = reportTaskGroup
                description = "Generate Jacoco coverage reports for default variants."
                configureReportContainer(this)
            }
    }

    private companion object {
        const val reportTaskGroup = "Reporting"
        const val rootReportTaskName = "jacocoRootReport"
        val languages = listOf("java", "kotlin")
        fun Project.androidSourceDirs(variant: String) = languages.map { file("src/$variant/$it") }
        fun Project.javaSourceDirs() = languages.map { file("src/main/$it") }
        fun KotlinMultiplatformExtension.hasPlatformType(type: KotlinPlatformType) =
            targets.any { it.platformType == type }
    }
}
