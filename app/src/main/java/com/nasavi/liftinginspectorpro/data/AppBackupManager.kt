package com.nasavi.liftinginspectorpro.data

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * גרסה 071:
 * גיבוי קל ובטוח לזיכרון האפליקציה.
 *
 * הסיבה לשינוי מ-070:
 * ב-070 ניסינו להכניס גם קבצים פנימיים/HTML/תמונות לתוך JSON כ-Base64.
 * במכשירים עם מעט זיכרון זה גרם ל-OutOfMemory בזמן יצירת הגיבוי.
 *
 * לכן ב-071 הגיבוי שומר רק נתוני טקסט קלים מתוך SharedPreferences:
 * - זיכרון לקוחות
 * - זיכרון יצרן + דגם במכונות
 * - תבניות טופס חכם ותבניות אביזרי הרמה
 * - רשימות שהמשתמש הוסיף
 * - הגדרות בודק ומספרי תסקיר
 * - אינדקס/רשומות שמורות אם נשמרים כטקסט בהעדפות
 *
 * קבצי PDF/תמונות/HTML כבדים לא מוכנסים לקובץ הגיבוי כדי למנוע קריסה.
 */
class AppBackupManager(private val context: Context) {

    fun createBackupJson(): String {
        val root = JSONObject()
        root.put("backupVersion", 4)
        root.put("appCodeVersion", "072")
        root.put("createdAt", System.currentTimeMillis())
        root.put(
            "createdAtText",
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        )
        root.put("backupMode", "lightweight_shared_preferences_only")
        root.put("note", "קבצי PDF, תמונות וקבצי HTML כבדים אינם כלולים בגיבוי זה כדי למנוע שגיאת זיכרון.")
        root.put("sharedPreferences", exportSharedPreferences())
        root.put("skippedInternalFiles", skippedInternalFilesInfo())
        return root.toString(2)
    }

    fun restoreBackupJson(json: String) {
        val root = JSONObject(json)
        restoreSharedPreferences(root.optJSONObject("sharedPreferences") ?: JSONObject())
        // תאימות לאחור: אם קיימים בגיבוי ישן internalFiles, לא משחזרים אותם כאן בכוונה.
        // שחזור קבצים כבדים מתוך JSON עלול לגרום שוב לשגיאת זיכרון.
    }

    fun suggestedFileName(): String {
        val dateText = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
        return "LiftingInspection_Backup_$dateText.json"
    }

    fun suggestedTemplatesFileName(): String {
        val dateText = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return "LiftingTemplates_$dateText.json"
    }

    fun createTemplatesJson(): String {
        val prefs = context.getSharedPreferences("lifting_inspection_prefs", Context.MODE_PRIVATE)
        val root = JSONObject()
        root.put("templateExportVersion", 1)
        root.put("createdAt", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))
        root.put("type", "templates_only")

        // תבניות חכמות
        val tplSet = prefs.getStringSet("smart_accessory_templates_v1", emptySet()) ?: emptySet()
        val tplArr = JSONArray().also { arr -> tplSet.forEach { arr.put(it) } }
        root.put("smart_accessory_templates_v1", tplArr)

        // תיאורים ידניים — אביזרי הרמה
        val manualArr = JSONArray().also { arr ->
            (prefs.getStringSet("manual_accessory_descriptions", emptySet()) ?: emptySet()).sorted().forEach { arr.put(it) }
        }
        root.put("manual_accessory_descriptions", manualArr)

        // תיאורים ידניים — אביזרי קצה
        val endArr = JSONArray().also { arr ->
            (prefs.getStringSet("manual_end_acc_descriptions", emptySet()) ?: emptySet()).sorted().forEach { arr.put(it) }
        }
        root.put("manual_end_acc_descriptions", endArr)

        // טבלת ע.ע.ב לאביזרי קצה
        prefs.getString("end_acc_wll_table_v2", null)?.let { root.put("end_acc_wll_table_v2", it) }

        return root.toString(2)
    }

    fun importTemplatesJson(json: String): Int {
        val root = JSONObject(json)
        val prefs = context.getSharedPreferences("lifting_inspection_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        var count = 0

        // מיזוג תבניות חכמות (מוסיף, לא מחליף)
        val importedArr = root.optJSONArray("smart_accessory_templates_v1")
        if (importedArr != null) {
            val existing = prefs.getStringSet("smart_accessory_templates_v1", emptySet()) ?: emptySet()
            val imported = (0 until importedArr.length()).map { importedArr.getString(it) }.toSet()
            editor.putStringSet("smart_accessory_templates_v1", existing + imported)
            count += importedArr.length()
        }

        // מיזוג תיאורים ידניים — אביזרי הרמה
        val manualArr = root.optJSONArray("manual_accessory_descriptions")
        if (manualArr != null) {
            val existing = prefs.getStringSet("manual_accessory_descriptions", emptySet()) ?: emptySet()
            val imported = (0 until manualArr.length()).map { manualArr.getString(it) }.toSet()
            editor.putStringSet("manual_accessory_descriptions", existing + imported)
        }

        // מיזוג תיאורים ידניים — אביזרי קצה
        val endArr = root.optJSONArray("manual_end_acc_descriptions")
        if (endArr != null) {
            val existing = prefs.getStringSet("manual_end_acc_descriptions", emptySet()) ?: emptySet()
            val imported = (0 until endArr.length()).map { endArr.getString(it) }.toSet()
            editor.putStringSet("manual_end_acc_descriptions", existing + imported)
        }

        // טבלת ע.ע.ב — מוסיף רק אם לא קיימת
        val wllTable = root.optString("end_acc_wll_table_v2", null)
        if (!wllTable.isNullOrBlank() && prefs.getString("end_acc_wll_table_v2", null).isNullOrBlank()) {
            editor.putString("end_acc_wll_table_v2", wllTable)
        }

        editor.apply()
        return count
    }

    private fun exportSharedPreferences(): JSONObject {
        val root = JSONObject()
        SHARED_PREFS_TO_BACKUP.forEach { prefName ->
            val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
            root.put(prefName, sharedPrefsToJson(prefs))
        }
        return root
    }

    private fun restoreSharedPreferences(root: JSONObject) {
        SHARED_PREFS_TO_BACKUP.forEach { prefName ->
            val prefsJson = root.optJSONObject(prefName) ?: return@forEach
            restoreSharedPrefsFromJson(
                prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE),
                obj = prefsJson
            )
        }
    }

    private fun sharedPrefsToJson(prefs: SharedPreferences): JSONObject {
        val obj = JSONObject()
        prefs.all.forEach { (key, value) ->
            val item = JSONObject()
            when (value) {
                is String -> {
                    item.put("type", "string")
                    item.put("value", value)
                }
                is Int -> {
                    item.put("type", "int")
                    item.put("value", value)
                }
                is Long -> {
                    item.put("type", "long")
                    item.put("value", value)
                }
                is Float -> {
                    item.put("type", "float")
                    item.put("value", value.toDouble())
                }
                is Boolean -> {
                    item.put("type", "boolean")
                    item.put("value", value)
                }
                is Set<*> -> {
                    item.put("type", "stringSet")
                    val array = JSONArray()
                    value.filterIsInstance<String>().sorted().forEach { array.put(it) }
                    item.put("value", array)
                }
                else -> return@forEach
            }
            obj.put(key, item)
        }
        return obj
    }

    private fun restoreSharedPrefsFromJson(prefs: SharedPreferences, obj: JSONObject) {
        val editor = prefs.edit().clear()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val item = obj.optJSONObject(key) ?: continue
            when (item.optString("type")) {
                "string" -> editor.putString(key, item.optString("value", ""))
                "int" -> editor.putInt(key, item.optInt("value", 0))
                "long" -> editor.putLong(key, item.optLong("value", 0L))
                "float" -> editor.putFloat(key, item.optDouble("value", 0.0).toFloat())
                "boolean" -> editor.putBoolean(key, item.optBoolean("value", false))
                "stringSet" -> {
                    val array = item.optJSONArray("value") ?: JSONArray()
                    val values = mutableSetOf<String>()
                    for (i in 0 until array.length()) {
                        val value = array.optString(i).trim()
                        if (value.isNotBlank()) values.add(value)
                    }
                    editor.putStringSet(key, values)
                }
            }
        }
        editor.apply()
    }

    private fun skippedInternalFilesInfo(): JSONArray {
        val array = JSONArray()
        INTERNAL_DIRS_NOT_INCLUDED_IN_LIGHT_BACKUP.forEach { dirName ->
            val obj = JSONObject()
            obj.put("dir", dirName)
            obj.put("reason", "לא נכלל בגיבוי קל כדי למנוע שגיאת זיכרון")
            array.put(obj)
        }
        return array
    }

    fun loadDefaultConfigIfFirstLaunch() {
        val flagPrefs = context.getSharedPreferences("app_init_flags", Context.MODE_PRIVATE)
        if (flagPrefs.getBoolean("default_config_loaded", false)) return
        try {
            val json = context.assets.open("default_config.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            restoreBackupJson(json)
        } catch (_: Exception) { }
        flagPrefs.edit().putBoolean("default_config_loaded", true).apply()
    }

    // ──────────────────────────────────────────────────────────
    // ייצוא ושחזור קבצי HTML (תסקירי אביזרים, מכונות וקולטי אוויר)
    // ──────────────────────────────────────────────────────────

    fun exportHtmlToZip(outputStream: OutputStream): Int {
        var count = 0
        ZipOutputStream(outputStream.buffered()).use { zip ->
            HTML_DIRS_TO_BACKUP.forEach { dirName ->
                val dir = File(context.filesDir, dirName)
                if (!dir.exists()) return@forEach
                dir.listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".html") }
                    ?.sortedBy { it.name }
                    ?.forEach { file ->
                        zip.putNextEntry(ZipEntry("$dirName/${file.name}"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        count++
                    }
            }
        }
        return count
    }

    fun importHtmlFromZip(inputStream: InputStream): Int {
        var count = 0
        val allowedDirs = HTML_DIRS_TO_BACKUP.toSet()
        ZipInputStream(inputStream.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val rawName = entry.name.replace("\\", "/").trimStart('/')
                    val parts = rawName.split("/")
                    val rawFileName = parts.last()
                    if (!rawFileName.contains("..") && isHtmlEntry(rawFileName)) {
                        val parentDir = if (parts.size >= 2) parts[parts.size - 2] else ""
                        val (targetDir, targetFileName) = resolveTargetDirAndName(parentDir, rawFileName)
                        if (targetDir != null && targetFileName != null) {
                            val targetDirFile = File(context.filesDir, targetDir).apply { mkdirs() }
                            File(targetDirFile, targetFileName).outputStream().buffered().use { zip.copyTo(it) }
                            count++
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return count
    }

    // מטפל בשלושה פורמטי ZIP:
    // 1. ייצוא שלנו:    saved_pdf_html/pdf_R6006.html   → saved_pdf_html/pdf_R6006.html
    // 2. תיקיית אפליקציה: Accessories/R6001.1.html      → saved_pdf_html/pdf_R6001.1.html
    //                     Machines/6013.html             → machine_reports/machine_6013.html
    // 3. ללא תיקייה:    pdf_R6006.html / R6001.html     → לפי קידומת שם
    private fun resolveTargetDirAndName(parentDir: String, rawFileName: String): Pair<String?, String?> {
        val baseName = rawFileName.removeSuffix(".html")
        val safe = baseName.replace(Regex("[^A-Za-z0-9_.-]"), "_").ifBlank { return null to null }
        return when {
            parentDir == "saved_pdf_html" || parentDir in HTML_DIRS_TO_BACKUP ->
                parentDir to rawFileName
            parentDir == "Accessories" || parentDir == "accessories" ->
                "saved_pdf_html" to "pdf_$safe.html"
            parentDir == "Machines" || parentDir == "machines" ->
                "machine_reports" to "machine_$safe.html"
            rawFileName.startsWith("pdf_") -> "saved_pdf_html" to rawFileName
            rawFileName.startsWith("machine_") -> "machine_reports" to rawFileName
            rawFileName.startsWith("working_") -> "working_report_html" to rawFileName
            else -> null to null
        }
    }

    private fun isHtmlEntry(fileName: String): Boolean =
        fileName.endsWith(".html")

    // ייבוא ישיר מתיקייה שנבחרה דרך OpenDocumentTree (ללא ZIP).
    // המשתמש בוחר את תיקיית LiftingInspection — האפליקציה קוראת Accessories/* ו-Machines/*
    fun importHtmlFromFolder(contentResolver: ContentResolver, treeUri: Uri): Int {
        var count = 0
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)

        fun listChildren(parentDocId: String): List<Pair<String, String>> {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            val result = mutableListOf<Pair<String, String>>()
            contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    result.add(cursor.getString(0) to cursor.getString(1))
                }
            }
            return result
        }

        val folderMappings = mapOf(
            "Accessories" to Pair("saved_pdf_html", "pdf"),
            "Machines"    to Pair("machine_reports", "machine")
        )

        listChildren(treeDocId).forEach { (childDocId, folderName) ->
            val (targetDir, prefix) = folderMappings[folderName] ?: return@forEach
            val targetDirFile = File(context.filesDir, targetDir).apply { mkdirs() }
            listChildren(childDocId).forEach { (fileDocId, fileName) ->
                val baseName = fileName.removeSuffix(".html")
                val safe = baseName.replace(Regex("[^A-Za-z0-9_.-]"), "_").ifBlank { return@forEach }
                val targetFileName = "${prefix}_$safe.html"
                val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, fileDocId)
                try {
                    contentResolver.openInputStream(fileUri)?.buffered()?.use { input ->
                        File(targetDirFile, targetFileName).outputStream().buffered().use { input.copyTo(it) }
                        count++
                    }
                } catch (_: Exception) {}
            }
        }
        return count
    }

    fun suggestedHtmlZipFileName(): String {
        val dateText = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
        return "LiftingInspection_HTML_$dateText.zip"
    }

    companion object {
        private val SHARED_PREFS_TO_BACKUP = listOf(
            "client_memory_store_061",
            "manufacturer_model_memory_store_064",
            "machine_smart_templates_prefs",
            "machine_clients_prefs",
            "machine_report_storage",
            "lifting_inspection_prefs",
            "inspector_settings",
            "report_storage",
            "report_photo_storage",
            "air_receiver_reports"
        )

        private val INTERNAL_DIRS_NOT_INCLUDED_IN_LIGHT_BACKUP = listOf(
            "working_report_html",
            "saved_pdf_html",
            "machine_reports",
            "machine_report_photos",
            "inspector_assets"
        )

        // מנגנון migration: מנקה נתוני זיכרון ישנים בעת עדכון גרסה
        // להגדלת MIGRATION_VERSION בכל פעם שרוצים לנקות נתוני זיכרון בעדכון חדש
        private const val MIGRATION_VERSION = 5

        fun runMigrationsIfNeeded(context: Context) {
            val prefs = context.getSharedPreferences("app_migrations", Context.MODE_PRIVATE)
            val stored = prefs.getInt("migration_version", 0)
            if (stored >= MIGRATION_VERSION) return

            if (stored < 1) {
                listOf(
                    "client_memory_store_061",
                    "manufacturer_model_memory_store_064",
                    "machine_clients_prefs"
                ).forEach { name ->
                    context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
                }
            }

            if (stored < 3) {
                // תיקון: receiverDescription יהיה DROPDOWN — מחיקת הגדרה ישנה כדי שברירת המחדל בקוד תיטען
                val inspectorPrefs = context.getSharedPreferences("inspector_settings", Context.MODE_PRIVATE)
                val settingsJson = inspectorPrefs.getString("air_field_settings", null)
                if (settingsJson != null) {
                    try {
                        val obj = org.json.JSONObject(settingsJson)
                        val desc = obj.optJSONObject("receiverDescription")
                        if (desc != null && desc.optString("type") != "DROPDOWN") {
                            desc.put("type", "DROPDOWN")
                            val opts = org.json.JSONArray()
                            opts.put("קולט אוויר אופקי עם מדחס בוכנתי (KW) 2.5")
                            opts.put("אחר – הזן ידנית")
                            desc.put("options", opts)
                            obj.put("receiverDescription", desc)
                            inspectorPrefs.edit().putString("air_field_settings", obj.toString()).apply()
                        }
                    } catch (_: Exception) { }
                }
            }

            if (stored < 4) {
                // תיקון: manufacturer ו-model יהיו DROPDOWN עם ערכי ברירת מחדל
                val inspectorPrefs = context.getSharedPreferences("inspector_settings", Context.MODE_PRIVATE)
                val settingsJson = inspectorPrefs.getString("air_field_settings", null)
                if (settingsJson != null) {
                    try {
                        val obj = org.json.JSONObject(settingsJson)
                        listOf(
                            "manufacturer" to listOf("fima", "אחר – הזן ידנית"),
                            "model"        to listOf("60L",  "אחר – הזן ידנית")
                        ).forEach { (fieldId, defaultOptions) ->
                            val field = obj.optJSONObject(fieldId)
                            if (field != null && field.optString("type") != "DROPDOWN") {
                                field.put("type", "DROPDOWN")
                                val opts = org.json.JSONArray()
                                defaultOptions.forEach { opts.put(it) }
                                field.put("options", opts)
                                obj.put(fieldId, field)
                            }
                        }
                        inspectorPrefs.edit().putString("air_field_settings", obj.toString()).apply()
                    } catch (_: Exception) { }
                }
            }

            if (stored < 5) {
                // הסרת "אחר – הזן ידנית" מרשימות DROPDOWN — הקוד מוסיף אותו אוטומטית
                val inspectorPrefs = context.getSharedPreferences("inspector_settings", Context.MODE_PRIVATE)
                val settingsJson = inspectorPrefs.getString("air_field_settings", null)
                if (settingsJson != null) {
                    try {
                        val obj = org.json.JSONObject(settingsJson)
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val field = obj.optJSONObject(key) ?: continue
                            val optsArr = field.optJSONArray("options") ?: continue
                            val cleaned = org.json.JSONArray()
                            for (i in 0 until optsArr.length()) {
                                val v = optsArr.optString(i)
                                if (!v.startsWith("אחר") || !v.contains("הזן ידנית")) cleaned.put(v)
                            }
                            field.put("options", cleaned)
                        }
                        inspectorPrefs.edit().putString("air_field_settings", obj.toString()).apply()
                    } catch (_: Exception) { }
                }
            }

            prefs.edit().putInt("migration_version", MIGRATION_VERSION).apply()
        }

        val HTML_DIRS_TO_BACKUP = listOf(
            "saved_pdf_html",
            "machine_reports",
            "working_report_html"
        )
    }
}

