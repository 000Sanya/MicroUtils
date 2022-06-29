package dev.inmo.micro_utils.repos.cache

import dev.inmo.micro_utils.pagination.Pagination
import dev.inmo.micro_utils.pagination.PaginationResult
import dev.inmo.micro_utils.repos.*
import dev.inmo.micro_utils.repos.cache.cache.KVCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

open class ReadKeyValueCacheRepo<Key,Value>(
    protected open val parentRepo: ReadKeyValueRepo<Key, Value>,
    protected open val kvCache: KVCache<Key, Value>,
) : ReadKeyValueRepo<Key,Value> by parentRepo {
    override suspend fun get(k: Key): Value? = kvCache.get(k) ?: parentRepo.get(k) ?.also { kvCache.set(k, it) }
    override suspend fun contains(key: Key): Boolean = kvCache.contains(key) || parentRepo.contains(key)
}

fun <Key, Value> ReadKeyValueRepo<Key, Value>.cached(
    kvCache: KVCache<Key, Value>
) = ReadKeyValueCacheRepo(this, kvCache)

open class KeyValueCacheRepo<Key,Value>(
    parentRepo: KeyValueRepo<Key, Value>,
    kvCache: KVCache<Key, Value>,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : ReadKeyValueCacheRepo<Key,Value>(parentRepo, kvCache), KeyValueRepo<Key,Value>, WriteKeyValueRepo<Key, Value> by parentRepo {
    protected val onNewJob = parentRepo.onNewValue.onEach { kvCache.set(it.first, it.second) }.launchIn(scope)
    protected val onRemoveJob = parentRepo.onValueRemoved.onEach { kvCache.unset(it) }.launchIn(scope)
}

fun <Key, Value> KeyValueRepo<Key, Value>.cached(
    kvCache: KVCache<Key, Value>,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) = KeyValueCacheRepo(this, kvCache, scope)
