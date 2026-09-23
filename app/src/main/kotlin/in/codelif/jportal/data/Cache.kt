package `in`.codelif.jportal.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** last good response per key, so every screen can paint before the network answers */
class Cache(context: Context) : SQLiteOpenHelper(context, "cache.db", null, 1) {

    class Entry(val json: String, val fetchedAt: Long)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE responses (key TEXT PRIMARY KEY, json TEXT NOT NULL, fetched_at INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS responses")
        onCreate(db)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    fun get(key: String): Entry? =
        readableDatabase.rawQuery("SELECT json, fetched_at FROM responses WHERE key = ?", arrayOf(key)).use { c ->
            if (c.moveToFirst()) Entry(c.getString(0), c.getLong(1)) else null
        }

    fun put(key: String, json: String, at: Long = System.currentTimeMillis()) {
        writableDatabase.insertWithOnConflict(
            "responses", null,
            ContentValues().apply {
                put("key", key)
                put("json", json)
                put("fetched_at", at)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun clear() {
        writableDatabase.delete("responses", null, null)
    }
}
