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
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.jonatbergn.jacoco.JacocoConfigExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoMerge
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.jvm

internal class JacocoConfig(private val rootProject: Project) {

    internal companion object {
        const val taskGroup = "Reporting"
        const val taskNameMergeExec = "jacocoMerged"
        const val taskNameMergeReport = "mergedReport"
    }

    private val ext by lazy { rootProject.extensions.create<JacocoConfigExtension>("jacocoConfig") }
    private val mainSourceDirs by lazy { sourceDirs("main") }
    private fun sourceDirs(name: String) = LanguageName.values().map { "src/$name/${it.value}" }

    private fun JacocoReport.configureReportContainers() = reports {
        val destinationDir = ext.reportDir(project.buildDir)
        html.apply {
            isEnabled = ext.isHtmlEnabled
            destination = destinationDir
        }
        xml.apply {
            isEnabled = ext.isXmlEnabled
            destination = destinationDir.resolve("jacoco.xml")
        }
        csv.apply {
            isEnabled = ext.isCsvEnabled
            destination = destinationDir.resolve("jacoco.csv")
        }
    }

    private fun Project.configureJacocoPlugin() {
        if (!plugins.hasPlugin(JacocoPlugin::class)) plugins.apply(JacocoPlugin::class.java)
        extensions.configure<JacocoPluginExtension> { toolVersion = ext.jacocoVersion }
    }

    private fun Project.configureJacocoPluginAndroidApplication() {
        extensions.configure<BaseAppModuleExtension> { jacoco { version = ext.jacocoVersion } }
    }

    private fun Project.configureJacocoPluginAndroidLibrary() {
        extensions.configure<LibraryExtension> { jacoco { version = ext.jacocoVersion } }
    }

    private fun JacocoReport.merge() = let {
        rootProject.tasks.named<JacocoMerge>(taskNameMergeExec) {
            executionData += it.executionData
        }
        rootProject.tasks.named<JacocoReport>(taskNameMergeReport) {
            classDirectories.from(it.classDirectories.from)
            additionalSourceDirs.from(it.additionalClassDirs.from)
            sourceDirectories.from(it.sourceDirectories.from)
        }
    }

    private fun Project.configureJacocoReportTask(
        testTaskName: String,
        reportTask: JacocoReport
    ) = reportTask.apply {
        group = taskGroup
        description = "Generate Jacoco coverage reports."
        configureReportContainers()
        val classPaths = listOf("**/classes/**/main/**")
        val classDirs = fileTree(buildDir) { setIncludes(classPaths); setExcludes(ext.excludes) }
        classDirectories.setFrom(classDirs)
        additionalSourceDirs.setFrom(files(mainSourceDirs))
        sourceDirectories.setFrom(files(mainSourceDirs))
        executionData.setFrom("$buildDir/jacoco/${testTaskName}.exec")
        dependsOn(testTaskName)
    }.merge()

    private fun Project.configureJacocoReportTaskJvm() {
        tasks.withType<JacocoReport> { configureJacocoReportTask("test", this) }
    }

    private fun Project.createJacocoReportTaskJvm() {
        tasks.create<JacocoReport>("jacocoTestReport") { configureJacocoReportTask("jvmTest", this) }
    }

    private fun Project.createJacocoReportTaskAndroid(
        buildType: String,
        productFlavor: String,
        buildVariant: String
    ) {
        if (buildVariant in ext.androidReportBuildVariants) {
            logger.warn("Adding report task for '$buildVariant'.")
        } else {
            logger.warn("Not adding report task for'$buildVariant'.")
            return
        }
        val (sourceName, sourcePath) = if (productFlavor.isEmpty()) {
            buildType to buildType
        } else {
            buildVariant to "$productFlavor/$buildType"
        }
        val reportTask = tasks.create<JacocoReport>("jacocoTestReport${sourceName.capitalize()}") {
            group = "Reporting"
            description = "Generate Jacoco coverage reports after running $sourceName tests."
            configureReportContainers()
            val classPaths = listOfNotNull(
                //todo this seems to be outdated or java specific
                //"**/intermediates/classes/${sourcePath}/**",
                "**/tmp/kotlin-classes/${sourcePath}/**"
                    .takeIf { hasKotlinPlugin() },
                "**/tmp/kotlin-classes/$buildVariant/**"
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
            val testTaskName = "test${sourceName.capitalize()}UnitTest"
            val instrumented = false //variant.buildType.isTestCoverageEnabled
            val executionDataFiles = listOfNotNull(
                fileTree("$buildDir/jacoco/")
                    .matching { include("${testTaskName}.exec") },
                fileTree("$buildDir/outputs/code_coverage/${sourceName}AndroidTest/connected/")
                    .matching { include("*.ec") }
                    .takeIf { instrumented }
            )
            executionData.setFrom(executionDataFiles)
            val dependencies = listOfNotNull(
                testTaskName,
                tasks.findByName("connected${sourceName.capitalize()}AndroidTest")
                    ?.takeIf { instrumented }
                    ?.name
            )
            dependsOn(dependencies)
        }.merge()
        //todo
        tasks["check"].dependsOn(reportTask)
    }

    private fun Project.createJacocoReportTasksAndroidApplication() {
        extensions.configure<ApplicationAndroidComponentsExtension> {
            onVariants {
                createJacocoReportTaskAndroid(it.buildType.orEmpty(), it.flavorName.orEmpty(), it.name)
            }
        }
    }

    private fun Project.createJacocoReportTasksAndroidLibrary() {
        extensions.configure<LibraryAndroidComponentsExtension> {
            onVariants {
                createJacocoReportTaskAndroid(it.buildType.orEmpty(), it.flavorName.orEmpty(), it.name)
            }
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
        .any { it.platformType == jvm }

    fun configure() = rootProject.run {
        configureJacocoPlugin()
        subprojects {
            afterEvaluate {
                if (!ext.ignore(name)) {
                    configureJacocoPlugin()
                    when {
                        hasPlugin(PluginId.AndroidApplication) -> {
                            configureJacocoPluginAndroidApplication()
                            createJacocoReportTasksAndroidApplication()
                        }
                        hasAndroidPlugin() -> {
                            configureJacocoPluginAndroidLibrary()
                            createJacocoReportTasksAndroidLibrary()
                        }
                    }
                    when {
                        hasPlugin(PluginId.KotlinMultiplatform) -> {
                            if (hasJvmTarget()) createJacocoReportTaskJvm()
                        }
                        hasJavaPlugin() -> {
                            configureJacocoReportTaskJvm()
                        }
                    }
                }
            }
        }
    }

    init {
        rootProject.run {
            val mergedDestination = file("$buildDir/jacoco/merged.exec")
            tasks.register<JacocoMerge>(taskNameMergeExec) {
                group = taskGroup
                description = "Merges jacoco exec files."
                executionData = files()
                doFirst { executionData = executionData.filter { it.exists() } }
            }
            tasks.register<JacocoReport>(taskNameMergeReport) {
                group = taskGroup
                description = "Generate Jacoco coverage report."
                configureReportContainers()
                executionData.setFrom(file("$buildDir/jacoco/$taskNameMergeExec.exec"))
            }
        }
    }
}
