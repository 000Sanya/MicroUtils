package dev.inmo.micro_utils.fsm.common.managers

import dev.inmo.micro_utils.fsm.common.State
import dev.inmo.micro_utils.fsm.common.StatesManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * @param onContextsConflictResolver Receive old [State], new one and the state currently placed on new [State.context]
 * key. In case when this callback will returns true, the state placed on [State.context] of new will be replaced by
 * new state by using [endChain] with that state
 */
class InMemoryStatesManager(
    private val onContextsConflictResolver: suspend (old: State, new: State, currentNew: State) -> Boolean = { _, _, _ -> true }
) : StatesManager {
    private val _onChainStateUpdated = MutableSharedFlow<Pair<State, State>>(0)
    override val onChainStateUpdated: Flow<Pair<State, State>> = _onChainStateUpdated.asSharedFlow()
    private val _onStartChain = MutableSharedFlow<State>(0)
    override val onStartChain: Flow<State> = _onStartChain.asSharedFlow()
    private val _onEndChain = MutableSharedFlow<State>(0)
    override val onEndChain: Flow<State> = _onEndChain.asSharedFlow()

    private val contextsToStates = mutableMapOf<Any, State>()
    private val mapMutex = Mutex()

    override suspend fun update(old: State, new: State) = mapMutex.withLock {
        when {
            contextsToStates[old.context] != old -> return@withLock
            old.context == new.context || !contextsToStates.containsKey(new.context) -> {
                contextsToStates[old.context] = new
                _onChainStateUpdated.emit(old to new)
            }
            else -> {
                val stateOnNewOneContext = contextsToStates.getValue(new.context)
                if (onContextsConflictResolver(old, new, stateOnNewOneContext)) {
                    endChainWithoutLock(stateOnNewOneContext)
                    contextsToStates.remove(old.context)
                    contextsToStates[new.context] = new
                    _onChainStateUpdated.emit(old to new)
                }
            }
        }
    }

    override suspend fun startChain(state: State) = mapMutex.withLock {
        if (!contextsToStates.containsKey(state.context)) {
            contextsToStates[state.context] = state
            _onStartChain.emit(state)
        }
    }

    private suspend fun endChainWithoutLock(state: State) {
        if (contextsToStates[state.context] == state) {
            contextsToStates.remove(state.context)
            _onEndChain.emit(state)
        }
    }

    override suspend fun endChain(state: State) {
        mapMutex.withLock {
            endChainWithoutLock(state)
        }
    }

    override suspend fun getActiveStates(): List<State> = contextsToStates.values.toList()

}
