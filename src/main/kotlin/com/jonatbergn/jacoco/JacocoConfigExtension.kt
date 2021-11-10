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

import com.android.build.api.variant.ComponentIdentity
import org.gradle.api.Project
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.kotlin.dsl.extra

open class JacocoConfigExtension(
    val project: Project,
) {
    val jacocoVersion = with(project) {
        if (hasProperty(PROPERTY_VERSION)) {
            requireNotNull(uncheckedCast(property(PROPERTY_VERSION)))
        } else {
            DEFAULT_VERSION
        }
    }
    private val jacocoGlobalExcludes = with(project.rootProject.extra) {
        if (has(EXTRA_EXCLUDES)) {
            requireNotNull(uncheckedCast(get(EXTRA_EXCLUDES)))
        } else {
            DEFAULT_EXCLUDES
        }
    }
    var excludes: List<String> = jacocoGlobalExcludes
    var isXmlEnabled: Boolean = !project.hasProperty(PROPERTY_XML_DISABLED)
    var isCsvEnabled: Boolean = !project.hasProperty(PROPERTY_CSV_DISABLED)
    var isHtmlEnabled: Boolean = !project.hasProperty(PROPERTY_HTML_DISABLED)

    fun rootReportVariant(block: ComponentIdentity.() -> Boolean) {
        this.rootReportVariant = block
    }

    internal var rootReportVariant: ComponentIdentity.() -> Boolean = {
        buildType == "debug" && productFlavors.isEmpty()
    }

    companion object {
        const val EXTRA_EXCLUDES = "jacocoConfig.globalExcludes"
        const val PROPERTY_VERSION = "jacocoConfig.version"
        const val PROPERTY_XML_DISABLED = "jacocoConfig.xml.disabled"
        const val PROPERTY_CSV_DISABLED = "jacocoConfig.csv.disabled"
        const val PROPERTY_HTML_DISABLED = "jacocoConfig.html.disabled"
        const val DEFAULT_VERSION = "0.8.7"
        val DEFAULT_EXCLUDES = emptyList<String>()
    }
}
