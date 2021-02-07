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

package com.jonatbergn.jacoco

import com.jonatbergn.jacoco.internal.JacocoConfig.configureJacoco
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create

class JacocoConfigPlugin : Plugin<Project> {
    override fun apply(target: Project) = target.run {
        val ext = extensions.create<JacocoConfigExtension>("jacocoConfig")
        subprojects {
            afterEvaluate {
                if (!ext.ignore.contains(name)) {
                    configureJacoco(ext)
                }
            }
        }
    }
}
