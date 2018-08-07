/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.kotlin.dsl.integration

import org.gradle.util.GradleVersion

import org.gradle.kotlin.dsl.fixtures.containsMultiLineString

import org.hamcrest.CoreMatchers.allOf
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test

import java.io.File
import java.util.jar.JarFile


class GradleApiExtensionsIntegrationTest : AbstractPluginIntegrationTest() {

    @Test
    fun `can use Gradle API generated extensions in scripts`() {

        withFile("init.gradle.kts", """
            allprojects {
                container(String::class)
            }
        """)

        withSettings("""
            buildCache {
                local(DirectoryBuildCache::class)
            }
            sourceControl {
                vcsMappings {
                    withModule("some:thing") {
                        from(GitVersionControlSpec::class) {
                            url = uri("")
                        }
                    }
                }
            }
        """)

        withBuildScript("""
            container(String::class)
            apply(from = "plugin.gradle.kts")
        """)

        withFile("plugin.gradle.kts", """
            import org.apache.tools.ant.filters.ReplaceTokens

            // Class<T> to KClass<T>
            property(Long::class)

            // Groovy named arguments to vararg of Pair
            fileTree("dir" to "src", "excludes" to listOf("**/ignore/**", "**/.data/**"))

            // Class<T> + Action<T> to KClass<T> + T.() -> Unit
            tasks.register("foo", Copy::class) {
                from("src")
                into("dst")

                // Class<T> + Groovy named arguments to KClass<T> + vararg of Pair
                filter(ReplaceTokens::class, "foo" to "bar")
            }
        """)

        build("foo", "-I", "init.gradle.kts")
    }

    @Test
    fun `can use Gradle API generated extensions in buildSrc`() {

        withDefaultSettingsIn("buildSrc")

        withBuildScriptIn("buildSrc", """
            plugins {
                `kotlin-dsl`
                `java-gradle-plugin`
            }

            $repositoriesBlock
        """)

        withFile("buildSrc/src/main/kotlin/foo/FooTask.kt", """
            package foo

            import org.gradle.api.*
            import org.gradle.api.tasks.*

            import org.gradle.kotlin.dsl.*

            import org.apache.tools.ant.filters.ReplaceTokens

            open class FooTask : DefaultTask() {

                @TaskAction
                fun foo() {
                    project.container(Long::class)
                }
            }
        """)

        withFile("buildSrc/src/main/kotlin/foo/foo.gradle.kts", """
            package foo

            tasks.register("foo", FooTask::class)
        """)

        withBuildScript("""
            plugins {
                id("foo.foo")
            }
        """)

        build("foo")
    }

    @Test
    fun `generated jar contains Gradle API extensions sources and byte code`() {

        withBuildScript("")
        build("help")

        val gradleUserHomeDir = File(System.getProperty("org.gradle.testkit.dir"))

        val generatedJar = gradleUserHomeDir.resolve("caches")
            .listFiles { f -> f.isDirectory && f.name == GradleVersion.current().version }.single()
            .resolve("generated-gradle-jars")
            .listFiles { f -> f.isFile && f.name.startsWith("gradle-kotlin-dsl-extensions-") }.single()

        val (generatedSources, generatedClasses) = JarFile(generatedJar)
            .use { it.entries().toList().map { entry -> entry.name } }
            .filter { it.startsWith("org/gradle/kotlin/dsl/GradleApiKotlinDslExtensions") }
            .groupBy { it.substring(it.lastIndexOf('.')) }
            .let { it[".kt"]!! to it[".class"]!! }

        assertTrue(generatedSources.isNotEmpty())
        assertTrue(generatedClasses.size >= generatedSources.size)

        val generatedSourceCode = JarFile(generatedJar).use { jar ->
            generatedSources.joinToString("\n") { name ->
                jar.getInputStream(jar.getJarEntry(name)).bufferedReader().use { it.readText() }
            }
        }

        val extensions = listOf(
            "package org.gradle.kotlin.dsl",
            """
            inline fun <S : T, T : Any> org.gradle.api.DomainObjectSet<T>.`withType`(`type`: kotlin.reflect.KClass<S>): org.gradle.api.DomainObjectSet<S> =
                `withType`(`type`.java)
            """,
            """
            inline fun org.gradle.api.tasks.AbstractCopyTask.`filter`(`filterType`: kotlin.reflect.KClass<out java.io.FilterReader>, vararg `properties`: Pair<String, Any?>): org.gradle.api.tasks.AbstractCopyTask =
                `filter`(mapOf(*`properties`), `filterType`.java)
            """,
            """
            @org.gradle.api.Incubating
            inline fun <T : org.gradle.api.Task> org.gradle.api.tasks.TaskContainer.`register`(`name`: String, `type`: kotlin.reflect.KClass<T>, `configurationAction`: org.gradle.api.Action<in T>): org.gradle.api.tasks.TaskProvider<T> =
                `register`(`name`, `type`.java, `configurationAction`)
            """,
            """
            @Deprecated("Deprecated Gradle API")
            inline fun org.gradle.api.file.FileCollection.`asType`(`type`: kotlin.reflect.KClass<*>): Any =
                `asType`(`type`.java)
            """,
            """
            inline fun <T : Any> org.gradle.api.plugins.ExtensionContainer.`create`(`name`: String, `type`: kotlin.reflect.KClass<T>, vararg `constructionArguments`: Any): T =
                `create`(`name`, `type`.java, *`constructionArguments`)
            """)

        assertThat(
            generatedSourceCode,
            allOf(extensions.map { containsMultiLineString(it) }))
    }
}
