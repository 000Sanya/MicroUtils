package dev.inmo.micro_utils.repos.cache.fallback.crud

import dev.inmo.micro_utils.coroutines.runCatchingSafely
import dev.inmo.micro_utils.pagination.Pagination
import dev.inmo.micro_utils.pagination.PaginationResult
import dev.inmo.micro_utils.repos.KeyValueRepo
import dev.inmo.micro_utils.repos.MapKeyValueRepo
import dev.inmo.micro_utils.repos.ReadCRUDRepo
import dev.inmo.micro_utils.repos.annotations.OverrideRequireManualInvalidation
import dev.inmo.micro_utils.repos.cache.fallback.ActionWrapper
import dev.inmo.micro_utils.repos.cache.util.actualizeAll
import dev.inmo.micro_utils.repos.cache.FallbackCacheRepo
import dev.inmo.micro_utils.repos.cache.util.ActualizeAllClearMode
import dev.inmo.micro_utils.repos.set
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

open class AutoRecacheReadCRUDRepo<RegisteredObject, Id>(
    protected val originalRepo: ReadCRUDRepo<RegisteredObject, Id>,
    protected val scope: CoroutineScope,
    protected val kvCache: KeyValueRepo<Id, RegisteredObject> = MapKeyValueRepo(),
    protected val recacheDelay: Long = 60.seconds.inWholeMilliseconds,
    protected val actionWrapper: ActionWrapper = ActionWrapper.Direct,
    protected val idGetter: (RegisteredObject) -> Id
) : ReadCRUDRepo<RegisteredObject, Id>, FallbackCacheRepo {
    val autoUpdateJob = scope.launch {
        while (isActive) {
            actualizeAll()

            delay(recacheDelay)
        }
    }

    constructor(
        originalRepo: ReadCRUDRepo<RegisteredObject, Id>,
        scope: CoroutineScope,
        originalCallTimeoutMillis: Long,
        kvCache: KeyValueRepo<Id, RegisteredObject> = MapKeyValueRepo(),
        recacheDelay: Long = 60.seconds.inWholeMilliseconds,
        idGetter: (RegisteredObject) -> Id
    ) : this(originalRepo, scope, kvCache, recacheDelay, ActionWrapper.Timeouted(originalCallTimeoutMillis), idGetter)

    protected open suspend fun actualizeAll(): Result<Unit> {
        return runCatchingSafely {
            kvCache.actualizeAll(originalRepo)
        }
    }

    override suspend fun contains(id: Id): Boolean = actionWrapper.wrap {
        originalRepo.contains(id)
    }.getOrElse {
        kvCache.contains(id)
    }

    override suspend fun getAll(): Map<Id, RegisteredObject> = actionWrapper.wrap {
        originalRepo.getAll()
    }.onSuccess {
        kvCache.actualizeAll(clearMode = ActualizeAllClearMode.BeforeSet) { it }
    }.getOrElse {
        kvCache.getAll()
    }

    override suspend fun count(): Long = actionWrapper.wrap {
        originalRepo.count()
    }.getOrElse {
        kvCache.count()
    }

    override suspend fun getByPagination(
        pagination: Pagination
    ): PaginationResult<RegisteredObject> = actionWrapper.wrap {
        originalRepo.getByPagination(pagination)
    }.getOrNull() ?.also {
        it.results.forEach {
            kvCache.set(idGetter(it), it)
        }
    } ?: kvCache.values(pagination)

    override suspend fun getIdsByPagination(
        pagination: Pagination
    ): PaginationResult<Id> = actionWrapper.wrap {
        originalRepo.getIdsByPagination(pagination)
    }.getOrElse { kvCache.keys(pagination) }

    override suspend fun getById(id: Id): RegisteredObject? = actionWrapper.wrap {
        originalRepo.getById(id)
    }.getOrNull() ?.also {
        kvCache.set(idGetter(it), it)
    } ?: kvCache.get(id)

    @OverrideRequireManualInvalidation
    override suspend fun invalidate() {
        actualizeAll()
    }
}
