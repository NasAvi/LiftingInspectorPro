package com.nasavi.liftinginspectorpro.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * גרסה 064:
 * זיכרון יצרן + דגם.
 * נשמר מקומית ב-SharedPreferences ולכן מחיקת נתוני אפליקציה תמחק אותו,
 * עד שנחבר את מנגנון הגיבוי/שחזור החיצוני למסך ההגדרות.
 */
data class ManufacturerModelMemoryItem(
    val category: String,
    val manufacturer: String,
    val model: String,
    val compositeKey: String = "",  // מפתח מורכב לתבניות עם שדות isMemoryKey מוגדרים
    val values: Map<String, String> = emptyMap(),
    val useCount: Int = 0,
    val lastUsedAt: Long = System.currentTimeMillis()
)

class ManufacturerModelMemoryStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun findExact(category: String, manufacturer: String, model: String): ManufacturerModelMemoryItem? {
        val categoryKey = normalizeKey(category)
        val makerKey = normalizeKey(manufacturer)
        val modelKey = normalizeKey(model)
        if (categoryKey.isBlank() || makerKey.isBlank() || modelKey.isBlank()) return null

        return getAll()
            .firstOrNull {
                normalizeKey(it.category) == categoryKey &&
                    normalizeKey(it.manufacturer) == makerKey &&
                    normalizeKey(it.model) == modelKey
            }
    }

    fun searchModels(category: String, manufacturer: String, modelPrefix: String, maxResults: Int = 8): List<ManufacturerModelMemoryItem> {
        val categoryKey = normalizeKey(category)
        val makerKey = normalizeKey(manufacturer)
        val prefixKey = normalizeKey(modelPrefix)
        if (categoryKey.isBlank() || makerKey.isBlank() || prefixKey.length < 1) return emptyList()

        return getAll()
            .filter {
                normalizeKey(it.category) == categoryKey &&
                    normalizeKey(it.manufacturer) == makerKey &&
                    normalizeKey(it.model).startsWith(prefixKey)
            }
            .sortedWith(
                compareByDescending<ManufacturerModelMemoryItem> { it.useCount }
                    .thenByDescending { it.lastUsedAt }
                    .thenBy { it.model }
            )
            .take(maxResults)
    }

    fun saveOrUpdate(
        category: String,
        manufacturer: String,
        model: String,
        values: Map<String, String>
    ) {
        val cleanCategory = category.trim()
        val cleanManufacturer = manufacturer.trim()
        val cleanModel = model.trim()
        if (cleanCategory.isBlank() || cleanManufacturer.isBlank() || cleanModel.isBlank()) return

        val existing = getAll().toMutableList()
        val categoryKey = normalizeKey(cleanCategory)
        val makerKey = normalizeKey(cleanManufacturer)
        val modelKey = normalizeKey(cleanModel)
        val index = existing.indexOfFirst {
            normalizeKey(it.category) == categoryKey &&
                normalizeKey(it.manufacturer) == makerKey &&
                normalizeKey(it.model) == modelKey
        }

        val cleanValues = values.mapValues { it.value.trim() }.filterValues { it.isNotBlank() }
        if (index >= 0) {
            val old = existing[index]
            existing[index] = old.copy(
                category = cleanCategory,
                manufacturer = cleanManufacturer,
                model = cleanModel,
                values = old.values + cleanValues,
                useCount = old.useCount + 1,
                lastUsedAt = System.currentTimeMillis()
            )
        } else {
            existing.add(
                ManufacturerModelMemoryItem(
                    category = cleanCategory,
                    manufacturer = cleanManufacturer,
                    model = cleanModel,
                    values = cleanValues,
                    useCount = 1,
                    lastUsedAt = System.currentTimeMillis()
                )
            )
        }

        saveAll(existing)
    }

    fun findByCompositeKey(category: String, compositeKey: String): ManufacturerModelMemoryItem? {
        val catKey = normalizeKey(category)
        val ck = normalizeKey(compositeKey)
        if (catKey.isBlank() || ck.isBlank()) return null
        val all = getAll()
        // חיפוש מדויק: רשומות חדשות עם compositeKey, או ישנות ללא compositeKey שמכילות manufacturer+model
        val exact = all.firstOrNull {
            normalizeKey(it.category) == catKey &&
                (if (it.compositeKey.isNotBlank()) normalizeKey(it.compositeKey) == ck
                 else normalizeKey(it.manufacturer + "|" + it.model) == ck)
        }
        if (exact != null) return exact
        // fallback לרשומות ישנות (בלי compositeKey): מחלץ רק את הערכים מהמפתח המורכב ומשווה ל-manufacturer|model
        val valuesOnly = normalizeKey(compositeKey.split("|").joinToString("|") { it.substringAfter(":") })
        return all.firstOrNull {
            normalizeKey(it.category) == catKey &&
                it.compositeKey.isBlank() &&
                normalizeKey(it.manufacturer + "|" + it.model) == valuesOnly
        }
    }

    fun saveOrUpdateWithCompositeKey(
        category: String,
        compositeKey: String,
        values: Map<String, String>
    ) {
        val cleanCategory = category.trim()
        val cleanKey = compositeKey.trim()
        if (cleanCategory.isBlank() || cleanKey.isBlank()) return

        val existing = getAll().toMutableList()
        val catKey = normalizeKey(cleanCategory)
        val ck = normalizeKey(cleanKey)
        // חיפוש רשומה קיימת: תמיכה ברשומות ישנות (ללא compositeKey) לפי הערכים בלבד
        val valuesOnly = normalizeKey(cleanKey.split("|").joinToString("|") { it.substringAfter(":") })
        val index = existing.indexOfFirst {
            normalizeKey(it.category) == catKey &&
                (if (it.compositeKey.isNotBlank()) normalizeKey(it.compositeKey) == ck
                 else normalizeKey(it.manufacturer + "|" + it.model) == ck ||
                      (it.compositeKey.isBlank() && normalizeKey(it.manufacturer + "|" + it.model) == valuesOnly))
        }

        val cleanValues = values.mapValues { it.value.trim() }.filterValues { it.isNotBlank() }
        if (index >= 0) {
            val old = existing[index]
            existing[index] = old.copy(
                compositeKey = cleanKey,
                values = old.values + cleanValues,
                useCount = old.useCount + 1,
                lastUsedAt = System.currentTimeMillis()
            )
        } else {
            existing.add(ManufacturerModelMemoryItem(
                category = cleanCategory,
                manufacturer = "",
                model = cleanKey,
                compositeKey = cleanKey,
                values = cleanValues,
                useCount = 1,
                lastUsedAt = System.currentTimeMillis()
            ))
        }
        saveAll(existing)
    }

    fun exportRawData(): String = prefs.getString(KEY_ITEMS, "[]").orEmpty()

    fun importRawData(rawData: String) {
        val array = JSONArray(rawData)
        val imported = mutableListOf<ManufacturerModelMemoryItem>()
        for (i in 0 until array.length()) {
            decode(array.optJSONObject(i))?.let { imported.add(it) }
        }
        saveAll(imported)
    }

    private fun getAll(): List<ManufacturerModelMemoryItem> {
        val array = runCatching { JSONArray(prefs.getString(KEY_ITEMS, "[]") ?: "[]") }.getOrElse { JSONArray() }
        val result = mutableListOf<ManufacturerModelMemoryItem>()
        for (i in 0 until array.length()) {
            decode(array.optJSONObject(i))?.let { result.add(it) }
        }
        return result
    }

    private fun saveAll(items: List<ManufacturerModelMemoryItem>) {
        val distinct = items
            .filter { it.category.isNotBlank() && (it.compositeKey.isNotBlank() || (it.manufacturer.isNotBlank() && it.model.isNotBlank())) }
            .distinctBy { item ->
                val key = if (item.compositeKey.isNotBlank()) normalizeKey(item.compositeKey)
                          else normalizeKey(item.manufacturer + "|" + item.model)
                normalizeKey(item.category) + "||" + key
            }

        val array = JSONArray()
        distinct.forEach { array.put(encode(it)) }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun encode(item: ManufacturerModelMemoryItem): JSONObject {
        val valuesObj = JSONObject()
        item.values.forEach { (key, value) -> valuesObj.put(key, value) }
        return JSONObject()
            .put("category", item.category)
            .put("manufacturer", item.manufacturer)
            .put("model", item.model)
            .put("compositeKey", item.compositeKey)
            .put("values", valuesObj)
            .put("useCount", item.useCount)
            .put("lastUsedAt", item.lastUsedAt)
    }

    private fun decode(obj: JSONObject?): ManufacturerModelMemoryItem? {
        if (obj == null) return null
        val category = obj.optString("category").trim()
        val manufacturer = obj.optString("manufacturer").trim()
        val model = obj.optString("model").trim()
        val compositeKey = obj.optString("compositeKey", "").trim()
        // פריטים שנשמרו עם compositeKey יכולים להיות עם manufacturer ריק
        if (category.isBlank()) return null
        if (compositeKey.isBlank() && (manufacturer.isBlank() || model.isBlank())) return null

        val valuesObj = obj.optJSONObject("values") ?: JSONObject()
        val values = mutableMapOf<String, String>()
        val keys = valuesObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            values[key] = valuesObj.optString(key)
        }

        return ManufacturerModelMemoryItem(
            category = category,
            manufacturer = manufacturer,
            model = model,
            compositeKey = compositeKey,
            values = values,
            useCount = obj.optInt("useCount", 0),
            lastUsedAt = obj.optLong("lastUsedAt", 0L)
        )
    }

    private fun normalizeKey(value: String): String =
        value.trim().lowercase().replace(Regex("\\s+"), "")

    companion object {
        private const val PREFS_NAME = "manufacturer_model_memory_store_064"
        private const val KEY_ITEMS = "items_json"
    }
}

