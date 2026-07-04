package de.diamind.ai.storage

import android.content.Context

object Preferences {
    private const val FILE_NAME = "diamind"

    fun saveText(context: Context, key: String, value: String) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key, value)
            .apply()
    }

    fun loadText(context: Context, key: String, default: String): String {
        return context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getString(key, default) ?: default
    }

    fun saveLong(context: Context, key: String, value: Long) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(key, value)
            .apply()
    }

    fun loadLong(context: Context, key: String, default: Long): Long {
        return context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getLong(key, default)
    }
}
