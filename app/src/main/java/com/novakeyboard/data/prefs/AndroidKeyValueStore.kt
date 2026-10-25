package com.novakeyboard.data.prefs

import android.content.SharedPreferences

/** SharedPreferences-backed [KeyValueStore]. */
class AndroidKeyValueStore(private val prefs: SharedPreferences) : KeyValueStore {

    private val adapters = HashMap<KeyValueStore.Listener, SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getString(key: String, fallback: String?): String? = prefs.getString(key, fallback)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getBoolean(key: String, fallback: Boolean): Boolean = prefs.getBoolean(key, fallback)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun getInt(key: String, fallback: Int): Int = prefs.getInt(key, fallback)

    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun getFloat(key: String, fallback: Float): Float = prefs.getFloat(key, fallback)

    override fun putFloat(key: String, value: Float) {
        prefs.edit().putFloat(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun addListener(listener: KeyValueStore.Listener) {
        if (adapters.containsKey(listener)) return
        val adapter = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null) listener.onKeyChanged(key)
        }
        adapters[listener] = adapter
        prefs.registerOnSharedPreferenceChangeListener(adapter)
    }

    override fun removeListener(listener: KeyValueStore.Listener) {
        val adapter = adapters.remove(listener) ?: return
        prefs.unregisterOnSharedPreferenceChangeListener(adapter)
    }
}

/** Compatibility alias used by the keyboard service. */
class SharedPrefsStore(prefs: SharedPreferences) : KeyValueStore by AndroidKeyValueStore(prefs)
