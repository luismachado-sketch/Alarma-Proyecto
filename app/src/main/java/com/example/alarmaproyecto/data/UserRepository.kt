package com.example.alarmaproyecto.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserRepository(context: Context) {
    private val dbHelper = UserDatabaseHelper(context)

    suspend fun register(email: String, password: String): Boolean = withContext(Dispatchers.IO) {
        if (dbHelper.userExists(email)) {
            false
        } else {
            dbHelper.addUser(email, password)
        }
    }

    suspend fun login(email: String, password: String): Boolean = withContext(Dispatchers.IO) {
        dbHelper.checkUser(email, password)
    }

    suspend fun userExists(email: String): Boolean = withContext(Dispatchers.IO) {
        dbHelper.userExists(email)
    }
}
