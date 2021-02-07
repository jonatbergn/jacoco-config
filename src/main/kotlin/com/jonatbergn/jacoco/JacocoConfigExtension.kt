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

open class JacocoConfigExtension {
    var jacocoVersion = "0.8.6"
    var ignore = listOf("")
    var excludes = listOf(
        "**/R.class",
        "**/R2.class",
        "**/R\$*.class",
        "**/R2\$*.class",
        "**/*\$ViewInjector*.*",
        "**/*\$ViewBinder*.*",
        "**/*_ViewBinding*.*",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Dagger*.*",
        "**/*MembersInjector*.*",
        "**/*_Provide*Factory*.*",
        "**/*_Factory*.*",
        "**/*\$JsonObjectMapper.*",
        "**/*\$Icepick.*",
        "**/*\$StateSaver.*",
        "**/*AutoValue_*.*"
    )
    var isXmlEnabled = true
    var isCsvEnabled = true
    var isHtmlEnabled = true
}
