package com.example.data.repository.stories

import com.example.data.database.StateEntity
import com.example.data.database.StatesDao
import com.example.data.repository.states.StateUrlResolver
import kotlinx.coroutines.flow.Flow

/**
 * Local data boundary for Stories / History.
 *
 * The physical Room storage remains shared for now, while this feature-owned
 * boundary prevents Story consumers from depending directly on StatesDao.
 */
class StoriesLocalDataSource(
    private val statesDao: StatesDao
) {
    fun observe(): Flow<List<StateEntity>> = statesDao.getStatesFlow(isReel = false)

    suspend fun getById(id: String): StateEntity? = statesDao.getStateById(id)

    suspend fun save(state: StateEntity) {
        require(!state.isReel) { "StoriesLocalDataSource only accepts Story states" }
        val existing = statesDao.getStateById(state.id)
        val stabilized = StateUrlResolver.stabilizeEntityForRoom(state, existing)
        statesDao.insertState(stabilized)
    }

    suspend fun saveAll(states: List<StateEntity>) {
        require(states.none { it.isReel }) { "StoriesLocalDataSource only accepts Story states" }
        val stabilized = states.map { entity ->
            val existing = statesDao.getStateById(entity.id)
            StateUrlResolver.stabilizeEntityForRoom(entity, existing)
        }
        statesDao.insertStates(stabilized)
    }

    suspend fun deleteById(id: String) = statesDao.deleteById(id)

    suspend fun updateLocalPath(id: String, path: String?) = statesDao.updateLocalPath(id, path)
}
