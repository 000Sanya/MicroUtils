package dev.inmo.micro_utils.coroutines

import kotlinx.coroutines.*

class DeferredAction<T, O>(
    val deferred: Deferred<T>,
    val callback: suspend (T) -> O
) {
    suspend operator fun invoke() = callback(deferred.await())
}

fun <T, O> Deferred<T>.buildAction(callback: suspend (T) -> O) = DeferredAction(this, callback)

suspend fun <O> Iterable<DeferredAction<*, O>>.invokeFirstOf(
    scope: CoroutineScope,
    cancelOnResult: Boolean = true
): O {
    return map { it.deferred }.awaitFirstWithDeferred(scope, cancelOnResult).let { result ->
        first { it.deferred == result.first }.invoke()
    }
}

suspend fun <O> invokeFirstOf(
    scope: CoroutineScope,
    vararg variants: DeferredAction<*, O>,
    cancelOnResult: Boolean = true
): O = variants.toList().invokeFirstOf(scope, cancelOnResult)

suspend fun <T, O> Iterable<Deferred<T>>.invokeOnFirst(
    scope: CoroutineScope,
    cancelOnResult: Boolean = true,
    callback: suspend (T) -> O
): O = map { it.buildAction(callback) }.invokeFirstOf(scope, cancelOnResult)

suspend fun <T, O> invokeOnFirst(
    scope: CoroutineScope,
    vararg variants: Deferred<T>,
    cancelOnResult: Boolean = true,
    callback: suspend (T) -> O
): O = variants.toList().invokeOnFirst(scope, cancelOnResult, callback)
