package com.nasavi.liftinginspectorpro.data

import android.content.Context
import android.util.Base64

/**
 * זיכרון לקוחות כללי לאביזרי הרמה.
 * נשמר מקומית ב-SharedPreferences, ולכן ניקוי "נתוני אפליקציה" באנדרואיד ימחק אותו.
 */
data class ClientMemoryItem(
    val name: String,
    val address: String = "",
    val phone: String = "",
    val contactPerson: String = "",
    val useCount: Int = 0,
    val lastUsedAt: Long = System.currentTimeMillis()
)

class ClientMemoryStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAllClients(): List<ClientMemoryItem> {
        return prefs.getString(KEY_CLIENTS, "")
            .orEmpty()
            .split(CLIENT_SEPARATOR)
            .mapNotNull { decodeClient(it) }
            .distinctBy { normalizeKey(it.name) }
            .sortedWith(
                compareByDescending<ClientMemoryItem> { it.useCount }
                    .thenByDescending { it.lastUsedAt }
                    .thenBy { it.name }
            )
    }

    fun searchClients(query: String, maxResults: Int = 8): List<ClientMemoryItem> {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) return emptyList()

        val normalizedQuery = normalizeKey(cleanQuery)

        // גרסה 062:
        // סינון הלקוח מתבצע לפי תחילת שם הלקוח בלבד.
        // בעבר השתמשנו גם ב-contains ולכן לקוחות קפצו לרשימה גם כאשר התווים הופיעו באמצע השם.
        // בשטח זה פחות נוח, כי הבודק מצפה שהרשימה תצטמצם לפי מה שהוא מתחיל להקליד.
        return getAllClients()
            .filter { client ->
                normalizeKey(client.name).startsWith(normalizedQuery)
            }
            .sortedWith(
                compareByDescending<ClientMemoryItem> { it.useCount }
                    .thenByDescending { it.lastUsedAt }
                    .thenBy { it.name }
            )
            .take(maxResults)
    }

    /**
     * שמירה חכמה:
     * שם הלקוח נשמר תמיד כאשר אינו ריק.
     * כתובת/טלפון/איש קשר מתעדכנים רק אם המשתמש אישר לשמור אותם וגם הערך אינו ריק.
     */
    fun saveOrUpdateClient(
        name: String,
        address: String = "",
        phone: String = "",
        contactPerson: String = ""
    ) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return

        val currentClients = getAllClients().toMutableList()
        val key = normalizeKey(cleanName)
        val existingIndex = currentClients.indexOfFirst { normalizeKey(it.name) == key }

        if (existingIndex >= 0) {
            val old = currentClients[existingIndex]
            currentClients[existingIndex] = old.copy(
                name = cleanName,
                address = address.trim().ifBlank { old.address },
                phone = phone.trim().ifBlank { old.phone },
                contactPerson = contactPerson.trim().ifBlank { old.contactPerson },
                useCount = old.useCount + 1,
                lastUsedAt = System.currentTimeMillis()
            )
        } else {
            currentClients.add(
                ClientMemoryItem(
                    name = cleanName,
                    address = address.trim(),
                    phone = phone.trim(),
                    contactPerson = contactPerson.trim(),
                    useCount = 1,
                    lastUsedAt = System.currentTimeMillis()
                )
            )
        }

        saveAllClients(currentClients)
    }

    fun exportRawData(): String = prefs.getString(KEY_CLIENTS, "").orEmpty()

    fun importRawData(rawData: String) {
        // בדיקת תקינות בסיסית: מפענחים ושומרים מחדש כדי למנוע רשומות שבורות.
        val validClients = rawData.split(CLIENT_SEPARATOR).mapNotNull { decodeClient(it) }
        saveAllClients(validClients)
    }

    private fun saveAllClients(clients: List<ClientMemoryItem>) {
        val encoded = clients
            .filter { it.name.trim().isNotBlank() }
            .distinctBy { normalizeKey(it.name) }
            .sortedWith(
                compareByDescending<ClientMemoryItem> { it.useCount }
                    .thenByDescending { it.lastUsedAt }
                    .thenBy { it.name }
            )
            .joinToString(CLIENT_SEPARATOR) { encodeClient(it) }

        prefs.edit().putString(KEY_CLIENTS, encoded).apply()
    }

    private fun encodeClient(client: ClientMemoryItem): String {
        return listOf(
            b64(client.name),
            b64(client.address),
            b64(client.phone),
            b64(client.contactPerson),
            client.useCount.toString(),
            client.lastUsedAt.toString()
        ).joinToString(VALUE_SEPARATOR)
    }

    private fun decodeClient(raw: String): ClientMemoryItem? {
        if (raw.isBlank()) return null
        return try {
            val parts = raw.split(VALUE_SEPARATOR)
            val name = parts.getOrNull(0)?.let { fromB64(it).trim() }.orEmpty()
            if (name.isBlank()) return null
            ClientMemoryItem(
                name = name,
                address = parts.getOrNull(1)?.let { fromB64(it).trim() }.orEmpty(),
                phone = parts.getOrNull(2)?.let { fromB64(it).trim() }.orEmpty(),
                contactPerson = parts.getOrNull(3)?.let { fromB64(it).trim() }.orEmpty(),
                useCount = parts.getOrNull(4)?.toIntOrNull() ?: 0,
                lastUsedAt = parts.getOrNull(5)?.toLongOrNull() ?: 0L
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun b64(value: String): String =
        Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun fromB64(value: String): String =
        try { String(Base64.decode(value, Base64.NO_WRAP), Charsets.UTF_8) } catch (_: Exception) { "" }

    private fun normalizeKey(value: String): String =
        value.trim().lowercase().replace(Regex("\\s+"), "")

    companion object {
        private const val PREFS_NAME = "client_memory_store_061"
        private const val KEY_CLIENTS = "clients"
        private const val CLIENT_SEPARATOR = "§CLIENT§"
        private const val VALUE_SEPARATOR = "§VALUE§"
    }
}

