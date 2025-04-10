package dev.inmo.micro_utils.fsm.common.managers

import dev.inmo.micro_utils.coroutines.SmartRWLocker
import dev.inmo.micro_utils.coroutines.withReadAcquire
import dev.inmo.micro_utils.coroutines.withWriteLock
import dev.inmo.micro_utils.fsm.common.State
import dev.inmo.micro_utils.fsm.common.StatesManager
import kotlinx.coroutines.flow.*

/**
 * Implement this repo if you want to use some custom repo for [DefaultStatesManager]
 */
interface DefaultStatesManagerRepo<T : State> {
    /**
     * Must save [state] as current state of chain with [State.context] of [state]
     */
    suspend fun set(state: T)
    /**
     * Remove exactly [state]. In case if internally [State.context] is busy with different [State], that [State] should
     * NOT be removed
     */
    suspend fun removeState(state: T)

    /**
     * Semantically, calls [removeState] and then [set]
     */
    suspend fun removeAndSet(toRemove: T, toSet: T) {
        removeState(toRemove)
        set(toSet)
    }
    /**
     * @return Current list of available and saved states
     */
    suspend fun getStates(): List<T>

    /**
     * @return Current state by [context]
     */
    suspend fun getContextState(context: Any): T?

    /**
     * @return Current state by [context]
     */
    suspend fun contains(context: Any): Boolean = getContextState(context) != null
}

/**
 * @param repo This repo will be used as repository for storing states. All operations with this repo will happen BEFORE
 * any event will be sent to [onChainStateUpdated], [onStartChain] or [onEndChain]. By default, will be used
 * [InMemoryDefaultStatesManagerRepo] or you may create custom [DefaultStatesManagerRepo] and pass as [repo] parameter
 * @param onStartContextsConflictResolver Receive current [State] and the state passed with [startChain]. In case when
 * this callback will return true, currently placed on the [State.context] [State] will be replaced by new state
 * with [endChain] with current state
 * @param onUpdateContextsConflictResolver Receive old [State], new one and the state currently placed on new [State.context]
 * key. In case when this callback will returns true, the state placed on [State.context] of new will be replaced by
 * new state by using [endChain] with that state
 */
open class DefaultStatesManager<T : State>(
    protected val repo: DefaultStatesManagerRepo<T> = InMemoryDefaultStatesManagerRepo(),
    protected val onStartContextsConflictResolver: suspend (current: T, new: T) -> Boolean = { _, _ -> false },
    protected val onUpdateContextsConflictResolver: suspend (old: T, new: T, currentNew: T) -> Boolean = { _, _, _ -> false }
) : StatesManager<T> {
    protected val _onChainStateUpdated = MutableSharedFlow<Pair<T, T>>(0)
    override val onChainStateUpdated: Flow<Pair<T, T>> = _onChainStateUpdated.asSharedFlow()
    protected val _onStartChain = MutableSharedFlow<T>(0)
    override val onStartChain: Flow<T> = _onStartChain.asSharedFlow()
    protected val _onEndChain = MutableSharedFlow<T>(0)
    override val onEndChain: Flow<T> = _onEndChain.asSharedFlow()

    protected val internalLocker = SmartRWLocker()

    constructor(
        repo: DefaultStatesManagerRepo<T>,
        onContextsConflictResolver: suspend (old: T, new: T, currentNew: T) -> Boolean
    ) : this (
        repo,
        onUpdateContextsConflictResolver = onContextsConflictResolver
    )

    override suspend fun update(old: T, new: T) = internalLocker.withWriteLock {
        val stateByOldContext: T? = repo.getContextState(old.context)
        when {
            stateByOldContext != old -> return@withWriteLock
            old.context == new.context -> {
                repo.removeAndSet(old, new)
                _onChainStateUpdated.emit(old to new)
            }
            old.context != new.context -> {
                val stateOnNewOneContext = repo.getContextState(new.context)
                if (stateOnNewOneContext == null || onUpdateContextsConflictResolver(old, new, stateOnNewOneContext)) {
                    stateOnNewOneContext ?.let { endChainWithoutLock(it) }
                    repo.removeAndSet(old, new)
                    _onChainStateUpdated.emit(old to new)
                } else {
                    error(
                        "Unable to update state from $old to $new due to false answer from $onUpdateContextsConflictResolver and state on old context $stateOnNewOneContext"
                    )
                }
            }
        }
    }

    override suspend fun startChain(state: T) = internalLocker.withWriteLock {
        val stateOnContext = repo.getContextState(state.context)
        if (stateOnContext == null || onStartContextsConflictResolver(stateOnContext, state)) {
            stateOnContext ?.let {
                endChainWithoutLock(it)
            }
            repo.set(state)
            _onStartChain.emit(state)
        }
    }

    protected open suspend fun endChainWithoutLock(state: T) {
        if (repo.getContextState(state.context) == state) {
            repo.removeState(state)
            _onEndChain.emit(state)
        }
    }

    override suspend fun endChain(state: T) {
        internalLocker.withWriteLock {
            endChainWithoutLock(state)
        }
    }

    override suspend fun getActiveStates(): List<T> = internalLocker.withReadAcquire {
        repo.getStates()
    }

}
