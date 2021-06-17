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
import com.android.build.gradle.*
import com.jonatbergn.jacoco.JacocoConfigExtension
import org.gradle.api.Project
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.kotlin.dsl.*
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformPluginBase
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinMultiplatformPlugin
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

internal class JacocoConfig(private val rootProject: Project) {

    internal companion object {
        const val taskGroup = "Reporting"
        const val taskNameMergeExec = "jacocoMerged"

        //        const val taskNameMergeReport = "mergedReport"
        val javaPlugins = listOf<Class<*>>(
            JavaPlugin::class.java,
            JavaLibraryPlugin::class.java,
        )
        val androidPlugins = listOf<Class<*>>(
            AppPlugin::class.java,
            LibraryPlugin::class.java,
            DynamicFeaturePlugin::class.java,
        )
        val kotlinPlugins = listOf<Class<*>>(
            KotlinBasePluginWrapper::class.java,
            KotlinPlatformPluginBase::class.java,
            KotlinMultiplatformPlugin::class.java,
        )
    }

    private val ext by lazy { rootProject.extensions.create<JacocoConfigExtension>("jacocoConfig") }
    private val mainSourceDirs by lazy { sourceDirs("main") }
    private fun sourceDirs(name: String) = LanguageName.values().map { "src/$name/${it.value}" }

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

    private fun Project.configureJacocoReportTask(
        testTaskName: String,
        reportTask: JacocoReport,
    ) = reportTask.apply {
        group = taskGroup
        description = "Generate Jacoco coverage reports."
        configureReportContainer()
        val classPaths = listOf("**/classes/**/main/**")
        val classDirs = fileTree(buildDir) { setIncludes(classPaths); setExcludes(ext.excludes) }
        classDirectories.setFrom(classDirs)
        additionalSourceDirs.setFrom(files(mainSourceDirs))
        sourceDirectories.setFrom(files(mainSourceDirs))
        executionData.setFrom("$buildDir/jacoco/${testTaskName}.exec")
        dependsOn(testTaskName)
    }

    private fun Project.configureJacocoReportTaskJvm() {
        tasks.withType<JacocoReport> { configureJacocoReportTask("test", this) }
    }

    private fun Project.createJacocoReportTaskJvm() {
        tasks.create<JacocoReport>("jacocoTestReport") {
            configureJacocoReportTask("jvmTest", this)
        }
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
                //todo this seems to be outdated or java specific
                //"**/intermediates/classes/${sourcePath}/**",
                "**/tmp/kotlin-classes/${sourcePath}/**"
                    .takeIf { hasKotlinPlugin() },
                "**/tmp/kotlin-classes/$buildVariant/**"
                    .takeIf { hasKotlinPlugin() }
                    .takeUnless { productFlavor.isEmpty() }
            )
            val classDirs =
                fileTree(buildDir) { setIncludes(classPaths); setExcludes(ext.excludes) }
            val flavorSourceDirs = sourceDirs(productFlavor)
            val buildTypeSourceDirs =
                sourceDirs(buildType).takeUnless { productFlavor.isEmpty() }.orEmpty()
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

    private fun Project.hasKotlinPlugin() = plugins.any { it.javaClass in kotlinPlugins }

    fun configure() = rootProject.run {
        configureJacocoPlugin()
        subprojects {
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

//    init {
//        rootProject.run {
//            tasks.register<JacocoReport>(taskNameMergeReport) {
//                group = taskGroup
//                description = "Generate Jacoco coverage report."
//                configureReportContainer()
//                executionData.setFrom(file("$buildDir/jacoco/$taskNameMergeExec.exec"))
//            }
//        }
//    }
}
