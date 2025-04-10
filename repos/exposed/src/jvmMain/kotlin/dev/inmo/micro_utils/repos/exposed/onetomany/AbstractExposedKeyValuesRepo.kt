package dev.inmo.micro_utils.repos.exposed.onetomany

import dev.inmo.micro_utils.repos.KeyValuesRepo
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction

abstract class AbstractExposedKeyValuesRepo<Key, Value>(
    override val database: Database,
    tableName: String? = null,
    flowsExtraBufferCapacity: Int = Int.MAX_VALUE,
    flowsBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND
) : KeyValuesRepo<Key, Value>, AbstractExposedReadKeyValuesRepo<Key, Value>(
    database,
    tableName
) {
    protected val _onNewValue: MutableSharedFlow<Pair<Key, Value>> = MutableSharedFlow(extraBufferCapacity = flowsExtraBufferCapacity, onBufferOverflow = flowsBufferOverflow)
    override val onNewValue: Flow<Pair<Key, Value>>
        get() = _onNewValue.asSharedFlow()
    protected val _onValueRemoved: MutableSharedFlow<Pair<Key, Value>> = MutableSharedFlow(extraBufferCapacity = flowsExtraBufferCapacity, onBufferOverflow = flowsBufferOverflow)
    override val onValueRemoved: Flow<Pair<Key, Value>>
        get() = _onValueRemoved.asSharedFlow()
    protected val _onDataCleared: MutableSharedFlow<Key> = MutableSharedFlow(extraBufferCapacity = flowsExtraBufferCapacity, onBufferOverflow = flowsBufferOverflow)
    override val onDataCleared: Flow<Key>
        get() = _onDataCleared.asSharedFlow()

    protected abstract fun insert(k: Key, v: Value, it: UpdateBuilder<Int>)

    override suspend fun add(toAdd: Map<Key, List<Value>>) {
        transaction(database) {
            toAdd.keys.flatMap { k ->
                toAdd[k] ?.mapNotNull { v ->
                    if (selectAll().where { selectById(k).and(selectByValue(v)) }.limit(1).any()) {
                        return@mapNotNull null
                    }
                    val insertResult = insert {
                        insert(k, v, it)
                    }
                    if (insertResult.insertedCount > 0) {
                        k to v
                    } else {
                        null
                    }
                } ?: emptyList()
            }
        }.forEach { _onNewValue.emit(it) }
    }

    override suspend fun set(toSet: Map<Key, List<Value>>) {
        if (toSet.isEmpty()) return
        val prepreparedData = toSet.flatMap { (k, vs) ->
            vs.map { v ->
                k to v
            }
        }

        val (oldObjects, insertedResults) = transaction(database) {
            val oldObjects = selectAll().where { selectByIds(toSet.keys.toList()) }.map { it.asKey to it.asObject }

            deleteWhere {
                selectByIds(it, toSet.keys.toList())
            }
            val inserted = batchInsert(
                prepreparedData,
            ) { (k, v) ->
                insert(k, v, this)
            }.map {
                it.asKey to it.asObject
            }
            oldObjects to inserted
        }.let {
            val mappedFirst = it
                .first
                .asSequence()
                .groupBy { it.first }
                .mapValues { it.value.map { it.second }.toSet() }
            val mappedSecond = it
                .second
                .asSequence()
                .groupBy { it.first }
                .mapValues { it.value.map { it.second }.toSet() }
            mappedFirst to mappedSecond
        }
        val deletedResults = oldObjects.mapNotNull { (k, vs) ->
            k to vs.filter { v ->
                insertedResults[k] ?.contains(v) != true
            }.ifEmpty { return@mapNotNull null }
        }
        deletedResults.forEach { (k, vs) ->
            vs.forEach { v ->
                _onValueRemoved.emit(k to v)
            }
        }
        insertedResults.forEach { (k, vs) ->
            vs.forEach { v ->
                _onNewValue.emit(k to v)
            }
        }
    }

    override suspend fun remove(toRemove: Map<Key, List<Value>>) {
        transaction(database) {
            toRemove.keys.flatMap { k ->
                toRemove[k] ?.mapNotNull { v ->
                    if (deleteWhere { selectById(it, k).and(SqlExpressionBuilder.selectByValue(v)) } > 0 ) {
                        k to v
                    } else {
                        null
                    }
                } ?: emptyList()
            }
        }.forEach {
            _onValueRemoved.emit(it)
        }
    }

    override suspend fun clear(k: Key) {
        transaction(database) {
            deleteWhere { selectById(it, k) }
        }.also { _onDataCleared.emit(k) }
    }
}
