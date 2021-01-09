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

internal enum class PluginId(val value: String) {
    AndroidApplication("com.android.application"),
    AndroidLibrary("com.android.library"),
    AndroidFeature("com.android.feature"),
    AndroidDynamicFeature("com.android.dynamic-feature"),
    AndroidInstantApp("com.android.instantapp"),
    AndroidTest("com.android.test"),
    Java("java"),
    JavaLibrary("java-library"),
    JavaGradlePlugin("java-gradle-plugin"),
    KotlinAndroid("org.jetbrains.kotlin.android"),
    KotlinMultiplatform("org.jetbrains.kotlin.multiplatform"),
}