package com.pocketrf.ble.library

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

private const val DB_NAME = "pocketrf_signals.db"
private const val DB_VERSION = 3
private const val TABLE = "signals"

private object Col {
    const val ID = "id"
    const val LABEL = "label"
    const val PROTOCOL = "protocol"
    const val BITS = "bits"
    const val KEY_HEX = "key_hex"
    const val FREQ_MHZ = "freq_mhz"
    const val TE_US = "te_us"
    const val RSSI = "rssi"
    const val MF_NAME = "mf_name"
    const val BTN = "btn"
    const val SERIAL = "serial"
    const val CNT = "cnt"
    const val FAVORITE = "favorite"
    const val SOURCE = "source"
    const val CREATED_AT = "created_at"
    const val RAW_DURATIONS = "raw_durations_csv"
    const val PIN_ORDER = "pin_order"
}

/**
 * Plain SQLiteOpenHelper rather than Room: one small table, no migrations
 * expected soon, and it avoids pulling in KSP/annotation-processor wiring
 * for a schema this simple.
 */
class SignalLibraryDb(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                ${Col.ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${Col.LABEL} TEXT NOT NULL,
                ${Col.PROTOCOL} TEXT NOT NULL,
                ${Col.BITS} INTEGER NOT NULL,
                ${Col.KEY_HEX} TEXT NOT NULL,
                ${Col.FREQ_MHZ} REAL,
                ${Col.TE_US} INTEGER,
                ${Col.RSSI} INTEGER,
                ${Col.MF_NAME} TEXT,
                ${Col.BTN} INTEGER,
                ${Col.SERIAL} TEXT,
                ${Col.CNT} INTEGER,
                ${Col.FAVORITE} INTEGER NOT NULL DEFAULT 0,
                ${Col.SOURCE} TEXT NOT NULL,
                ${Col.CREATED_AT} INTEGER NOT NULL,
                ${Col.RAW_DURATIONS} TEXT,
                ${Col.PIN_ORDER} INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN ${Col.RAW_DURATIONS} TEXT")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN ${Col.PIN_ORDER} INTEGER NOT NULL DEFAULT 0")
        }
    }

    fun insert(signal: SavedSignal): Long = writableDatabase.insert(TABLE, null, signal.toContentValues())

    fun update(signal: SavedSignal) {
        writableDatabase.update(TABLE, signal.toContentValues(), "${Col.ID} = ?", arrayOf(signal.id.toString()))
    }

    fun delete(id: Long) {
        writableDatabase.delete(TABLE, "${Col.ID} = ?", arrayOf(id.toString()))
    }

    /** The highest `pin_order` currently in use — a newly-pinned key goes one past it, to land at the end of the grid. */
    fun maxPinOrder(): Int {
        readableDatabase.rawQuery("SELECT MAX(${Col.PIN_ORDER}) FROM $TABLE WHERE ${Col.FAVORITE} = 1", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    fun queryAll(): List<SavedSignal> {
        readableDatabase.query(
            TABLE, null, null, null, null, null, "${Col.FAVORITE} DESC, ${Col.PIN_ORDER} ASC, ${Col.CREATED_AT} DESC",
        ).use { cursor ->
            return buildList { while (cursor.moveToNext()) add(cursor.toSavedSignal()) }
        }
    }

    private fun SavedSignal.toContentValues() = ContentValues().apply {
        put(Col.LABEL, label)
        put(Col.PROTOCOL, protocol)
        put(Col.BITS, bits)
        put(Col.KEY_HEX, keyHex)
        put(Col.FREQ_MHZ, freqMhz)
        put(Col.TE_US, teUs)
        put(Col.RSSI, rssi)
        put(Col.MF_NAME, mfName)
        put(Col.BTN, btn)
        put(Col.SERIAL, serial)
        put(Col.CNT, cnt)
        put(Col.FAVORITE, if (favorite) 1 else 0)
        put(Col.SOURCE, source)
        put(Col.CREATED_AT, createdAtMs)
        put(Col.RAW_DURATIONS, rawDurations?.joinToString(","))
        put(Col.PIN_ORDER, pinOrder)
    }

    private fun Cursor.toSavedSignal() = SavedSignal(
        id = getLong(getColumnIndexOrThrow(Col.ID)),
        label = getString(getColumnIndexOrThrow(Col.LABEL)),
        protocol = getString(getColumnIndexOrThrow(Col.PROTOCOL)),
        bits = getInt(getColumnIndexOrThrow(Col.BITS)),
        keyHex = getString(getColumnIndexOrThrow(Col.KEY_HEX)),
        freqMhz = getColumnIndexOrThrow(Col.FREQ_MHZ).let { if (isNull(it)) null else getDouble(it) },
        teUs = getColumnIndexOrThrow(Col.TE_US).let { if (isNull(it)) null else getInt(it) },
        rssi = getColumnIndexOrThrow(Col.RSSI).let { if (isNull(it)) null else getInt(it) },
        mfName = getColumnIndexOrThrow(Col.MF_NAME).let { if (isNull(it)) null else getString(it) },
        btn = getColumnIndexOrThrow(Col.BTN).let { if (isNull(it)) null else getInt(it) },
        serial = getColumnIndexOrThrow(Col.SERIAL).let { if (isNull(it)) null else getString(it) },
        cnt = getColumnIndexOrThrow(Col.CNT).let { if (isNull(it)) null else getInt(it) },
        favorite = getInt(getColumnIndexOrThrow(Col.FAVORITE)) != 0,
        source = getString(getColumnIndexOrThrow(Col.SOURCE)),
        createdAtMs = getLong(getColumnIndexOrThrow(Col.CREATED_AT)),
        rawDurations = getColumnIndexOrThrow(Col.RAW_DURATIONS).let { col ->
            if (isNull(col)) null else getString(col).split(",").mapNotNull { it.toIntOrNull() }
        },
        pinOrder = getInt(getColumnIndexOrThrow(Col.PIN_ORDER)),
    )
}
