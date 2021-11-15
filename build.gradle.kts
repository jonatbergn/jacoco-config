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

buildscript {
    repositories {
        google()
    }
}
plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "0.15.0"
    id("org.jetbrains.dokka") version "1.4.30"
    id("com.vanniktech.maven.publish.base") version "0.15.1"
}
repositories {
    mavenCentral()
    google()
}
pluginBundle {
    website = "${property("POM_URL")}"
    vcsUrl = "${property("POM_SCM_URL")}"
    tags = setOf("jacoco", "android", "kotlin", "junit")
}
gradlePlugin {
    plugins {
        create("JacocoConfig") {
            id = "com.jonatbergn.jacoco.jacoco-config"
            implementationClass = "com.jonatbergn.jacoco.JacocoConfigPlugin"
            displayName = "${property("POM_NAME")}"
            description = "${property("POM_DESCRIPTION")}"
        }
    }
}
dependencies {
    gradleApi()
    implementation("com.android.tools.build:gradle:7.2.2")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.10")
}
group = "${property("GROUP")}"
version = "${property("VERSION_NAME")}"
