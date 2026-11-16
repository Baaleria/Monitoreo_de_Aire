package com.example.monitoreo_de_aire

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Almacenamiento temporal de lecturas en la APLICACIÓN (rúbrica 3.1.2.7).
 * Guarda en SQLite las lecturas que llegan del buffer offline del ESP32 y
 * todavía no pudieron subirse a Firebase. Sincroniza hacia la base de datos
 * cuando se restablece la conexión y luego las borra.
 * Es la contraparte móvil del buffer en flash (LittleFS) del ESP32.
 */
class BufferLecturas(context: Context) :
    SQLiteOpenHelper(context, "buffer_lecturas.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE lecturas(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "valor INTEGER NOT NULL, " +
                    "ms_esp32 INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, vieja: Int, nueva: Int) {
        db.execSQL("DROP TABLE IF EXISTS lecturas")
        onCreate(db)
    }

    fun agregar(valor: Int, msEsp32: Long) {
        val fila = ContentValues().apply {
            put("valor", valor)
            put("ms_esp32", msEsp32)
        }
        writableDatabase.insert("lecturas", null, fila)
    }

    fun contar(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM lecturas", null).use { c ->
            c.moveToFirst(); c.getInt(0)
        }

    /** Devuelve hasta [n] lecturas en orden de captura (las más antiguas primero). */
    fun primeras(n: Int): List<Pair<Int, Long>> {
        val lista = mutableListOf<Pair<Int, Long>>()
        readableDatabase.rawQuery(
            "SELECT valor, ms_esp32 FROM lecturas ORDER BY id ASC LIMIT ?",
            arrayOf(n.toString())
        ).use { c ->
            while (c.moveToNext()) lista.add(c.getInt(0) to c.getLong(1))
        }
        return lista
    }

    /** Borra las primeras [n] lecturas (tras subirlas a Firebase). */
    fun borrarPrimeras(n: Int) {
        writableDatabase.execSQL(
            "DELETE FROM lecturas WHERE id IN " +
                    "(SELECT id FROM lecturas ORDER BY id ASC LIMIT ?)",
            arrayOf(n.toString())
        )
    }
}
