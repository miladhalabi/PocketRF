package com.pocketrf.ble.library

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SignalLibraryRepository(context: Context) {
    private val db = SignalLibraryDb(context.applicationContext)

    suspend fun loadAll(): List<SavedSignal> = withContext(Dispatchers.IO) { db.queryAll() }

    suspend fun save(signal: SavedSignal): Long = withContext(Dispatchers.IO) { db.insert(signal) }

    suspend fun rename(signal: SavedSignal, newLabel: String) = withContext(Dispatchers.IO) {
        db.update(signal.copy(label = newLabel))
    }

    /** Pinning puts a key at the end of the grid (past whatever's already pinned); unpinning doesn't need to touch pinOrder — it's ignored once favorite is false. */
    suspend fun setFavorite(signal: SavedSignal, favorite: Boolean) = withContext(Dispatchers.IO) {
        val pinOrder = if (favorite) db.maxPinOrder() + 1 else signal.pinOrder
        db.update(signal.copy(favorite = favorite, pinOrder = pinOrder))
    }

    /** Persists a drag-reorder: `orderedIds` is the pinned grid's new left-to-right, top-to-bottom order. */
    suspend fun reorderPinned(orderedIds: List<Long>, allSignals: List<SavedSignal>) = withContext(Dispatchers.IO) {
        val byId = allSignals.associateBy { it.id }
        orderedIds.forEachIndexed { index, id ->
            byId[id]?.let { db.update(it.copy(pinOrder = index)) }
        }
    }

    suspend fun delete(signal: SavedSignal) = withContext(Dispatchers.IO) { db.delete(signal.id) }
}
