package com.example.alarmaproyecto.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest

class UserDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "alarma_users.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_USERS = "users"
        private const val COL_ID = "id"
        private const val COL_EMAIL = "email"
        private const val COL_PASSWORD = "password"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createTable = """
            CREATE TABLE $TABLE_USERS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_EMAIL TEXT UNIQUE NOT NULL,
                $COL_PASSWORD TEXT NOT NULL
            )
        """.trimIndent()
        db?.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        onCreate(db)
    }

    fun addUser(email: String, password: String): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_EMAIL, email.trim().lowercase())
            put(COL_PASSWORD, hashPassword(password))
        }
        return try {
            db.insert(TABLE_USERS, null, values) != -1L
        } catch (e: Exception) {
            false
        } finally {
            db.close()
        }
    }

    fun checkUser(email: String, password: String): Boolean {
        val db = readableDatabase
        val selection = "$COL_EMAIL = ? AND $COL_PASSWORD = ?"
        val selectionArgs = arrayOf(email.trim().lowercase(), hashPassword(password))
        val cursor = db.query(TABLE_USERS, null, selection, selectionArgs, null, null, null)
        val exists = cursor.count > 0
        cursor.close()
        db.close()
        return exists
    }

    fun userExists(email: String): Boolean {
        val db = readableDatabase
        val selection = "$COL_EMAIL = ?"
        val selectionArgs = arrayOf(email.trim().lowercase())
        val cursor = db.query(TABLE_USERS, null, selection, selectionArgs, null, null, null)
        val exists = cursor.count > 0
        cursor.close()
        db.close()
        return exists
    }

    private fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
