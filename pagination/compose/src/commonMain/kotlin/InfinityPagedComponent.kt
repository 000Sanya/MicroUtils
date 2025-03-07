package dev.inmo.micro_utils.pagination.compose

import androidx.compose.runtime.*
import dev.inmo.micro_utils.coroutines.SpecialMutableStateFlow
import dev.inmo.micro_utils.coroutines.launchLoggingDropExceptions
import dev.inmo.micro_utils.coroutines.runCatchingLogging
import dev.inmo.micro_utils.pagination.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Context for managing infinite pagination in a Compose UI.
 *
 * @param T The type of the data being paginated.
 * @property iterationState Holds the current pagination state and iteration count.
 * @property dataState Stores the loaded data, initially null.
 * @constructor Internal constructor to initialize pagination.
 * @param page Initial page number.
 * @param size Number of items per page.
 */
class InfinityPagedComponentContext<T> internal constructor(
    page: Int,
    size: Int,
    private val scope: CoroutineScope,
    private val loader: suspend InfinityPagedComponentContext<T>.(Pagination) -> PaginationResult<T>
) {
    internal val startPage = SimplePagination(page, size)
    internal val latestLoadedPage = SpecialMutableStateFlow<PaginationResult<T>?>(null)
    internal val dataState = SpecialMutableStateFlow<List<T>?>(null)
    internal var loadingJob: Job? = null
    internal val loadingMutex = Mutex()

    /**
     * Loads the next page of data. If the current page is the last one, the function returns early.
     */
    fun loadNext(): Job {
        return scope.launchLoggingDropExceptions {
            loadingMutex.withLock {
                if (latestLoadedPage.value ?.isLastPage == true) return@launchLoggingDropExceptions
                loadingJob = loadingJob ?: scope.launchLoggingDropExceptions {
                    runCatching {
                        loader(latestLoadedPage.value ?.nextPage() ?: startPage)
                    }.onSuccess {
                        latestLoadedPage.value = it
                        dataState.value = (dataState.value ?: emptyList()) + it.results
                    }
                    loadingMutex.withLock {
                        loadingJob = null
                    }
                }
                loadingJob
            } ?.join()
        }
    }

    /**
     * Reloads the pagination from the first page, clearing previously loaded data.
     */
    fun reload(): Job {
        latestLoadedPage.value = null
        dataState.value = null
        return loadNext()
    }
}

/**
 * Composable function for managing an infinitely paged component.
 *
 * @param T The type of the paginated data.
 * @param page Initial page number.
 * @param size Number of items per page.
 * @param loader Suspended function that loads paginated data.
 * @param block Composable function that renders the UI with the loaded data. When data is in loading state, block will
 * receive null as `it` parameter
 */
@Composable
internal fun <T> InfinityPagedComponent(
    page: Int,
    size: Int,
    loader: suspend InfinityPagedComponentContext<T>.(Pagination) -> PaginationResult<T>,
    predefinedScope: CoroutineScope? = null,
    block: @Composable InfinityPagedComponentContext<T>.(List<T>?) -> Unit
) {
    val scope = predefinedScope ?: rememberCoroutineScope()
    val context = remember { InfinityPagedComponentContext<T>(page, size, scope, loader) }

    val dataState = context.dataState.collectAsState()
    context.block(dataState.value)
}

/**
 * Overloaded composable function for an infinitely paged component.
 *
 * @param T The type of the paginated data.
 * @param pageInfo Initial pagination information.
 * @param loader Suspended function that loads paginated data.
 * @param block Composable function that renders the UI with the loaded data. When data is in loading state, block will
 * receive null as `it` parameter
 */
@Composable
fun <T> InfinityPagedComponent(
    pageInfo: Pagination,
    loader: suspend InfinityPagedComponentContext<T>.(Pagination) -> PaginationResult<T>,
    predefinedScope: CoroutineScope? = null,
    block: @Composable InfinityPagedComponentContext<T>.(List<T>?) -> Unit
) {
    InfinityPagedComponent(
        pageInfo.page,
        pageInfo.size,
        loader,
        predefinedScope,
        block
    )
}

/**
 * Overloaded composable function for an infinitely paged component.
 *
 * @param T The type of the paginated data.
 * @param size Number of items per page.
 * @param loader Suspended function that loads paginated data.
 * @param block Composable function that renders the UI with the loaded data. When data is in loading state, block will
 * receive null as `it` parameter
 */
@Composable
fun <T> InfinityPagedComponent(
    size: Int,
    loader: suspend InfinityPagedComponentContext<T>.(Pagination) -> PaginationResult<T>,
    predefinedScope: CoroutineScope? = null,
    block: @Composable InfinityPagedComponentContext<T>.(List<T>?) -> Unit
) {
    InfinityPagedComponent(0, size, loader, predefinedScope, block)
}
