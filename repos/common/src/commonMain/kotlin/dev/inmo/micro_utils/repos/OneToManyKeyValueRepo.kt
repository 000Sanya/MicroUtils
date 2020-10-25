package dev.inmo.micro_utils.repos

import dev.inmo.micro_utils.pagination.*
import kotlinx.coroutines.flow.Flow

interface ReadOneToManyKeyValueRepo<Key, Value> : Repo {
    suspend fun get(k: Key, pagination: Pagination, reversed: Boolean = false): PaginationResult<Value>
    suspend fun keys(pagination: Pagination, reversed: Boolean = false): PaginationResult<Key>
    suspend fun contains(k: Key): Boolean
    suspend fun contains(k: Key, v: Value): Boolean
    suspend fun count(k: Key): Long
    suspend fun count(): Long

    suspend fun getAll(k: Key, reversed: Boolean = false): List<Value> = mutableListOf<Value>().also { list ->
        doWithPagination {
            get(k, it).also {
                list.addAll(it.results)
            }.nextPageIfNotEmpty()
        }
        if (reversed) {
            list.reverse()
        }
    }

    /**
     * WARNING!!! THIS METHOD PROBABLY IS NOT EFFICIENT, USE WITH CAUTION
     */
    suspend fun getAll(reverseLists: Boolean = false): Map<Key, List<Value>> = mutableMapOf<Key, List<Value>>().also { map ->
        doWithPagination {
            keys(it).also { paginationResult ->
                paginationResult.results.forEach { k ->
                    map[k] = getAll(k, reverseLists)
                }
            }.nextPageIfNotEmpty()
        }
    }
}
@Deprecated("Renamed", ReplaceWith("ReadOneToManyKeyValueRepo", "dev.inmo.micro_utils.repos.ReadOneToManyKeyValueRepo"))
typealias OneToManyReadKeyValueRepo<Key, Value> = ReadOneToManyKeyValueRepo<Key, Value>

interface WriteOneToManyKeyValueRepo<Key, Value> : Repo {
    val onNewValue: Flow<Pair<Key, Value>>
    val onValueRemoved: Flow<Pair<Key, Value>>
    val onDataCleared: Flow<Key>

    suspend fun add(toAdd: Map<Key, List<Value>>) = toAdd.forEach { (k, values) ->
        values.forEach { v ->
            add(k, v)
        }
    }
    @Deprecated("Will be extracted as extension for other add method")
    suspend fun add(k: Key, v: Value)

    suspend fun remove(toRemove: Map<Key, List<Value>>) = toRemove.forEach { (k, values) ->
        values.forEach { v ->
            remove(k, v)
        }
    }
    @Deprecated("Will be extracted as extension for other remove method")
    suspend fun remove(k: Key, v: Value)

    suspend fun clear(k: Key)
}
@Deprecated("Renamed", ReplaceWith("WriteOneToManyKeyValueRepo", "dev.inmo.micro_utils.repos.WriteOneToManyKeyValueRepo"))
typealias OneToManyWriteKeyValueRepo<Key, Value> = WriteOneToManyKeyValueRepo<Key, Value>

interface OneToManyKeyValueRepo<Key, Value> : ReadOneToManyKeyValueRepo<Key, Value>, WriteOneToManyKeyValueRepo<Key, Value>

suspend inline fun <Key, Value, REPO : WriteOneToManyKeyValueRepo<Key, Value>> REPO.add(
    k: Key,
    vararg v: Value
) = add(mapOf(k to v.toList()))

suspend inline fun <Key, Value, REPO : WriteOneToManyKeyValueRepo<Key, Value>> REPO.add(
    keysAndValues: List<Pair<Key, List<Value>>>
) = add(keysAndValues.toMap())

suspend inline fun <Key, Value, REPO : WriteOneToManyKeyValueRepo<Key, Value>> REPO.add(
    vararg keysAndValues: Pair<Key, List<Value>>
) = add(keysAndValues.toMap())

suspend inline fun <Key, Value, REPO : WriteOneToManyKeyValueRepo<Key, Value>> REPO.remove(
    k: Key,
    vararg v: Value
) = remove(mapOf(k to v.toList()))

suspend inline fun <Key, Value, REPO : WriteOneToManyKeyValueRepo<Key, Value>> REPO.remove(
    keysAndValues: List<Pair<Key, List<Value>>>
) = remove(keysAndValues.toMap())

suspend inline fun <Key, Value, REPO : WriteOneToManyKeyValueRepo<Key, Value>> REPO.remove(
    vararg keysAndValues: Pair<Key, List<Value>>
) = remove(keysAndValues.toMap())
