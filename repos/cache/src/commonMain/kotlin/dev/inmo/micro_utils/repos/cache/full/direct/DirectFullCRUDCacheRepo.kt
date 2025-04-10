package dev.inmo.micro_utils.repos.cache.full.direct

import dev.inmo.micro_utils.common.Warning
import dev.inmo.micro_utils.coroutines.SmartRWLocker
import dev.inmo.micro_utils.coroutines.launchLoggingDropExceptions
import dev.inmo.micro_utils.coroutines.withReadAcquire
import dev.inmo.micro_utils.pagination.Pagination
import dev.inmo.micro_utils.pagination.PaginationResult
import dev.inmo.micro_utils.repos.*
import dev.inmo.micro_utils.repos.annotations.OverrideRequireManualInvalidation
import dev.inmo.micro_utils.repos.cache.*
import dev.inmo.micro_utils.repos.cache.util.actualizeAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

open class DirectFullReadCRUDCacheRepo<ObjectType, IdType>(
    protected val parentRepo: ReadCRUDRepo<ObjectType, IdType>,
    protected val kvCache: KeyValueRepo<IdType, ObjectType>,
    protected val locker: SmartRWLocker = SmartRWLocker(),
    protected val idGetter: (ObjectType) -> IdType
) : ReadCRUDRepo<ObjectType, IdType>, DirectFullCacheRepo {
    protected open suspend fun actualizeAll() {
        kvCache.actualizeAll(parentRepo, locker = locker)
    }

    override suspend fun getByPagination(pagination: Pagination): PaginationResult<ObjectType> = locker.withReadAcquire {
        kvCache.values(pagination)
    }

    override suspend fun getIdsByPagination(pagination: Pagination): PaginationResult<IdType> = locker.withReadAcquire {
        kvCache.keys(pagination)
    }

    override suspend fun count(): Long = locker.withReadAcquire {
        kvCache.count()
    }

    override suspend fun contains(id: IdType): Boolean = locker.withReadAcquire {
        kvCache.contains(id)
    }

    override suspend fun getAll(): Map<IdType, ObjectType> = locker.withReadAcquire {
        kvCache.getAll()
    }

    override suspend fun getById(id: IdType): ObjectType? = locker.withReadAcquire {
        kvCache.get(id)
    }

    override suspend fun invalidate() {
        actualizeAll()
    }
}

fun <ObjectType, IdType> ReadCRUDRepo<ObjectType, IdType>.directlyCached(
    kvCache: KeyValueRepo<IdType, ObjectType>,
    locker: SmartRWLocker = SmartRWLocker(),
    idGetter: (ObjectType) -> IdType
) = DirectFullReadCRUDCacheRepo(this, kvCache, locker, idGetter)

open class DirectFullCRUDCacheRepo<ObjectType, IdType, InputValueType>(
    protected val crudRepo: CRUDRepo<ObjectType, IdType, InputValueType>,
    kvCache: KeyValueRepo<IdType, ObjectType>,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    skipStartInvalidate: Boolean = false,
    locker: SmartRWLocker = SmartRWLocker(writeIsLocked = !skipStartInvalidate),
    idGetter: (ObjectType) -> IdType
) : DirectFullReadCRUDCacheRepo<ObjectType, IdType>(
    crudRepo,
    kvCache,
    locker,
    idGetter
),
    WriteCRUDRepo<ObjectType, IdType, InputValueType> by WriteCRUDCacheRepo(
        crudRepo,
        kvCache,
        scope,
        locker,
        idGetter
    ),
    CRUDRepo<ObjectType, IdType, InputValueType> {
    init {
        if (!skipStartInvalidate) {
            scope.launchLoggingDropExceptions {
                if (locker.writeMutex.isLocked) {
                    initialInvalidate()
                } else {
                    invalidate()
                }
            }
        }
    }

    protected open suspend fun initialInvalidate() {
        try {
            kvCache.actualizeAll(parentRepo, locker = null)
        } finally {
            locker.unlockWrite()
        }
    }

    @OverrideRequireManualInvalidation
    override suspend fun invalidate() {
        actualizeAll()
    }
}

fun <ObjectType, IdType, InputType> CRUDRepo<ObjectType, IdType, InputType>.directFullyCached(
    kvCache: KeyValueRepo<IdType, ObjectType> = MapKeyValueRepo(),
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    skipStartInvalidate: Boolean = false,
    locker: SmartRWLocker = SmartRWLocker(),
    idGetter: (ObjectType) -> IdType
) = DirectFullCRUDCacheRepo(this, kvCache, scope, skipStartInvalidate, locker, idGetter)
