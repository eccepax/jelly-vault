package org.jellyfin.mobile.testing

import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.definition.BeanDefinition
import org.koin.core.parameter.parametersOf
import java.io.File

/**
 * Builds every definition this [Koin] was started with, and describes each one that can't be built
 * (a missing binding, a wrong type, a throwing constructor). Empty when the whole graph builds.
 *
 * Each definition is given [parameters]: those that take parameters (e.g. `viewModel { (albumId:
 * String) -> ... }`) read them, the rest ignore them. A view model that gets a `SavedStateHandle`
 * from its owner needs one passed here.
 */
fun Koin.unbuildableDefinitions(vararg parameters: Any = arrayOf("test-id")): List<String> =
    definitions().mapNotNull { definition ->
        runCatching { get<Any>(definition.primaryType, definition.qualifier) { parametersOf(*parameters) } }
            .exceptionOrNull()
            ?.let { "$definition: ${it.rootCause()}" }
    }

/**
 * The types injected in the Kotlin sources under [sourceDirs] (`by inject()`, `by viewModel()`,
 * `koinInject()`, `koinViewModel()` and the like) that no definition of this [Koin] provides.
 *
 * Those injections are resolved lazily, when a screen, service or helper first touches them, so
 * [unbuildableDefinitions] can't see them. They're matched to definitions by simple class name.
 * [notShipped] names source files (e.g. a shared component another app registers) to leave out.
 */
fun Koin.unprovidedInjections(sourceDirs: List<File>, notShipped: Set<String> = emptySet()): List<String> {
    val provided = definitions().flatMap { it.secondaryTypes + it.primaryType }.mapNotNull { it.simpleName }.toSet()
    return sourceDirs.asSequence()
        .flatMap { dir -> dir.walk().filter { it.extension == "kt" && it.name !in notShipped } }
        .flatMap { file ->
            file.readLines().withIndex().flatMap { (index, line) ->
                INJECTIONS.flatMap { it.findAll(line) }.map { match ->
                    match.groupValues[1].simpleTypeName() to "${file.name}:${index + 1}"
                }
            }
        }
        .filter { (type, _) -> type !in provided }
        .map { (type, where) -> "$type, injected at $where" }
        .distinct()
        .toList()
}

private const val TYPE = """([A-Z][\w.]*(?:<[^>]*>)?\??)"""
private const val INJECTORS = "inject|viewModel|activityViewModel|koinInject|koinViewModel|koinActivityViewModel"

private val INJECTIONS = listOf(
    // val x: T by inject()
    Regex(""":\s*$TYPE\s+by\s+(?:$INJECTORS)\b(?!\s*\.)"""),
    // x: T = koinInject()
    Regex(""":\s*$TYPE\s*=\s*(?:$INJECTORS)\("""),
    // koinInject<T>()
    Regex("""\b(?:$INJECTORS)<$TYPE>\("""),
)

private fun String.simpleTypeName() = substringBefore('<').removeSuffix("?").substringAfterLast('.')

@OptIn(KoinInternalApi::class)
private fun Koin.definitions(): List<BeanDefinition<*>> =
    instanceRegistry.instances.values.map { it.beanDefinition }.distinct()

private fun Throwable.rootCause(): Throwable = generateSequence(this) { it.cause }.last()
