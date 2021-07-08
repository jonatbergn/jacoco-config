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
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import com.jonatbergn.jacoco.JacocoConfigExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

internal class JacocoConfig(private val rootProject: Project) {

    private val ext by lazy { rootProject.extensions.create<JacocoConfigExtension>("jacocoConfig") }
    private val mainSourceDir = sourceDir("main")
    private fun sourceDir(name: String) = "src/$name/kotlin"

    private fun JacocoReport.configureReportContainer() = reports {
        val destinationDir = ext.reportDir(project.buildDir)
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

    private fun Project.createJacocoReportTaskJvm() {
        val reportTask = tasks.create<JacocoReport>("jacocoTestReport") {
            group = "Reporting"
            description = "Generate Jacoco coverage reports."
            configureReportContainer()
            val testTaskName = "jvmTest"
            val classPaths = listOf("**/classes/**/main/**")
            val classDirs = fileTree(buildDir)
                .setIncludes(classPaths)
                .setExcludes(ext.excludes)
            classDirectories.setFrom(classDirs)
            additionalSourceDirs.setFrom(files(mainSourceDir))
            sourceDirectories.setFrom(files(mainSourceDir))
            executionData.setFrom("$buildDir/jacoco/${testTaskName}.exec")
            dependsOn(tasks.findByName(testTaskName))
        }
        tasks["check"].dependsOn(reportTask)
    }

    private fun Project.createJacocoReportTaskAndroid(
        buildType: String,
        productFlavor: String,
        buildVariant: String,
    ) {
        val (sourceName, sourcePath) = if (productFlavor.isEmpty()) {
            buildType to buildType
        } else {
            buildVariant to "$productFlavor/$buildType"
        }
        val reportTask = tasks.create<JacocoReport>("jacocoTestReport${sourceName.capitalize()}") {
            group = "Reporting"
            description = "Generate Jacoco coverage reports after running $sourceName tests."
            configureReportContainer()
            val classPaths = listOfNotNull(
                "**/tmp/kotlin-classes/${sourcePath}/**",
                "**/tmp/kotlin-classes/$buildVariant/**".takeUnless { productFlavor.isEmpty() }
            )
            val classDirs = fileTree(buildDir) {
                setIncludes(classPaths)
                setExcludes(ext.excludes)
            }
            val flavorSourceDirs = sourceDir(productFlavor)
            val buildTypeSourceDirs = sourceDir(buildType).takeUnless { productFlavor.isEmpty() }.orEmpty()
            val sourceDirs = flavorSourceDirs + buildTypeSourceDirs + mainSourceDir
            classDirectories.setFrom(classDirs)
            additionalSourceDirs.setFrom(files(sourceDirs))
            sourceDirectories.setFrom(files(sourceDirs))
            val testTaskName = "test${sourceName.capitalize()}UnitTest"
            val executionDataFiles = fileTree("$buildDir/jacoco/")
                    .matching { include("${testTaskName}.exec") }
            executionData.setFrom(executionDataFiles)
            dependsOn(testTaskName)
        }
        tasks["check"].dependsOn(reportTask)
    }

    private fun Project.createJacocoReportTasksAndroidApplication() {
        extensions.configure<ApplicationAndroidComponentsExtension> {
            onVariants {
                createJacocoReportTaskAndroid(
                    it.buildType.orEmpty(),
                    it.flavorName.orEmpty(),
                    it.name
                )
            }
        }
    }

    private fun Project.createJacocoReportTasksAndroidLibrary() {
        extensions.configure<LibraryAndroidComponentsExtension> {
            onVariants {
                createJacocoReportTaskAndroid(
                    it.buildType.orEmpty(),
                    it.flavorName.orEmpty(),
                    it.name
                )
            }
        }
    }

    fun configure() {
        rootProject.configureJacocoPlugin()
        rootProject.subprojects {
            afterEvaluate {
                if (!ext.ignore(name)) {
                    configureJacocoPlugin()
                    when {
                        extensions.findByType<AppExtension>() != null -> {
                            configureJacocoPluginAndroidApplication()
                            createJacocoReportTasksAndroidApplication()
                        }
                        extensions.findByType<LibraryExtension>() != null -> {
                            configureJacocoPluginAndroidLibrary()
                            createJacocoReportTasksAndroidLibrary()
                        }
                    }
                    if (
                        extensions.findByType<KotlinMultiplatformExtension>()
                            ?.targets
                            .orEmpty()
                            .any { it is KotlinJvmTarget }
                    ) {
                        createJacocoReportTaskJvm()
                    }
                }
            }
        }
    }
}
