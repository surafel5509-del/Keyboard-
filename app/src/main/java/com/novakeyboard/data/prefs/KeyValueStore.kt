package com.novakeyboard.data.prefs

/**
 * Small key/value abstraction so preference-backed logic can be unit tested on
 * the JVM without Android's SharedPreferences.
 */
interface KeyValueStore {

    fun getString(key: String, fallback: String?): String?
    fun putString(key: String, value: String)
    fun getBoolean(key: String, fallback: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getInt(key: String, fallback: Int): Int
    fun putInt(key: String, value: Int)
    fun getFloat(key: String, fallback: Float): Float
    fun putFloat(key: String, value: Float)
    fun remove(key: String)

    fun addListener(listener: Listener)
    fun removeListener(listener: Listener)

    fun interface Listener {
        fun onKeyChanged(key: String)
    }
}

/** In-memory store used by unit tests. */
class MemoryKeyValueStore : KeyValueStore {

    private val values = LinkedHashMap<String, Any>()
    private val listeners = LinkedHashSet<KeyValueStore.Listener>()

    override fun getString(key: String, fallback: String?): String? = values[key] as? String ?: fallback

    override fun putString(key: String, value: String) = put(key, value)

    override fun getBoolean(key: String, fallback: Boolean): Boolean = values[key] as? Boolean ?: fallback

    override fun putBoolean(key: String, value: Boolean) = put(key, value)

    override fun getInt(key: String, fallback: Int): Int = values[key] as? Int ?: fallback

    override fun putInt(key: String, value: Int) = put(key, value)

    override fun getFloat(key: String, fallback: Float): Float = values[key] as? Float ?: fallback

    override fun putFloat(key: String, value: Float) = put(key, value)

    override fun remove(key: String) {
        if (values.remove(key) != null) notifyChanged(key)
    }

    override fun addListener(listener: KeyValueStore.Listener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: KeyValueStore.Listener) {
        listeners.remove(listener)
    }

    private fun put(key: String, value: Any) {
        values[key] = value
        notifyChanged(key)
    }

    private fun notifyChanged(key: String) {
        for (listener in listeners.toList()) listener.onKeyChanged(key)
    }

    /** Exposed for assertions in tests. */
    fun contains(key: String): Boolean = values.containsKey(key)
}
