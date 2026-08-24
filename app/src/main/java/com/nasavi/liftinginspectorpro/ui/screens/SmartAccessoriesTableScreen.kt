package com.nasavi.liftinginspectorpro.ui.screens

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.nasavi.liftinginspectorpro.data.ReportPhotoStorage
import java.io.File
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

// ── JSON ייצוא / ייבוא תבניות אביזרים למחשב ──────────────────────────────────────
internal fun SmartAccessoryTemplate.toJsonObject(): JSONObject {
    val fieldsArr = JSONArray()
    fields.forEach { f ->
        val optArr = JSONArray(); f.options.forEach { optArr.put(it) }
        fieldsArr.put(JSONObject()
            .put("name", f.name).put("inputType", f.inputType.name).put("options", optArr)
            .put("isRequired", f.isRequired).put("isMemoryKey", f.isMemoryKey)
            .put("inDescription", f.inDescription).put("isSeparateColumn", f.isSeparateColumn)
            .put("isForCalculation", f.isForCalculation).put("isScannable", f.isScannable)
            .put("defaultValue", f.defaultValue))
    }
    val formulasArr = JSONArray()
    formulas.forEach { formula ->
        formulasArr.put(JSONObject()
            .put("name", formula.name).put("expression", formula.expression)
            .put("targetFieldName", formula.targetFieldName).put("digits", formula.digits))
    }
    return JSONObject()
        .put("version", 1).put("type", "accessory_smart_template")
        .put("typeName", typeName).put("descriptionTemplate", descriptionTemplate)
        .put("fields", fieldsArr).put("formulas", formulasArr)
}

private fun smartTemplateFromJsonText(text: String): SmartAccessoryTemplate? {
    return try {
        // תמיכה גם בקובץ ייצוא מלא (עם מפתח "templates") וגם בתבנית בודדת
        val root = JSONObject(text)
        val obj = if (root.has("templates")) root.getJSONArray("templates").optJSONObject(0) else root
            ?: return null
        val typeName = obj.optString("typeName").orEmpty().trim()
        if (typeName.isBlank()) return null
        val fieldsArr = obj.optJSONArray("fields") ?: JSONArray()
        val fields = (0 until fieldsArr.length()).mapNotNull { i ->
            val f = fieldsArr.getJSONObject(i)
            val name = f.optString("name").trim(); if (name.isBlank()) return@mapNotNull null
            val inputType = SmartFieldInputType.values().firstOrNull { it.name == f.optString("inputType") } ?: SmartFieldInputType.TEXT
            val optArr = f.optJSONArray("options")
            val options = if (optArr != null) (0 until optArr.length()).map { optArr.getString(it) } else emptyList()
            SmartAccessoryFieldDefinition(name, inputType, options,
                f.optBoolean("isRequired", false), f.optBoolean("isMemoryKey", false),
                f.optBoolean("inDescription", true), f.optBoolean("isSeparateColumn", false),
                f.optBoolean("isForCalculation", false), f.optBoolean("isScannable", false),
                f.optString("defaultValue", ""))
        }
        val formulasArr = obj.optJSONArray("formulas") ?: JSONArray()
        val formulas = (0 until formulasArr.length()).mapNotNull { i ->
            val formula = formulasArr.getJSONObject(i)
            val name = formula.optString("name").trim(); val expression = formula.optString("expression").trim()
            val target = formula.optString("targetFieldName").trim(); val digits = formula.optInt("digits", 1).coerceIn(0, 4)
            if (name.isBlank() || target.isBlank()) return@mapNotNull null
            if (expression.isBlank() && !isSmartBuiltInCalculatedTarget(target)) return@mapNotNull null
            SmartFormulaDefinition(name, expression, target, digits)
        }
        SmartAccessoryTemplate(typeName, obj.optString("descriptionTemplate"), fields, formulas)
    } catch (_: Exception) { null }
}

// ────────────────────────────────────────────────────────────────────────────────

private const val SEED_VERSION_KEY = "smart_acc_seed_v"
private const val CURRENT_SEED_VERSION = 9  // הגדל כאשר defaultAccessoryTemplates משתנה

// תת-סוגים של אביזרי קצה — מוצגים בתפריט משני בלבד, לא בדרופדאון הראשי
private val END_ACCESSORY_CHILD_NAMES = listOf("סגיר אומגה", "התקן להרמת שוחות")

// תבניות שיוחלפו בכוח בעדכון לגרסה 8 (תיקון אפשרויות מידה — הסרת ")
private val SEED_FORCE_REFRESH_V8 = setOf("סגיר אומגה")
// תבניות שיוחלפו בכוח בעדכון לגרסה 9 (מענבי כבל — מקדם מוטמע באפשרויות "טקסט,מקדם")
private val SEED_FORCE_REFRESH_V9 = setOf("מענבי כבל")

private val smartDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

// ────────────────────────────────────────────────────────
// תבניות ברירת מחדל — נזרעות ל-SharedPreferences.
// ════════════════════════════════════════════════════════
private val defaultAccessoryTemplates: List<SmartAccessoryTemplate> = listOf(
    SmartAccessoryTemplate(
        typeName = "מענבי שרשרת",
        descriptionTemplate = "מענב שרשרת {מספר ענפים} {קוטר}/{דרגת שרשרת} באורך {אורך} מטר, עם {אביזר קצה 1} {אביזר קצה 2}",
        fields = listOf(
            SmartAccessoryFieldDefinition("יצרן", SmartFieldInputType.LIST, listOf("ללא", "אחר"), isMemoryKey = true),
            SmartAccessoryFieldDefinition("דגם", SmartFieldInputType.LIST, listOf("ללא", "אחר"), isMemoryKey = true),
            SmartAccessoryFieldDefinition("קוטר", SmartFieldInputType.LIST, listOf("8", "10", "13", "16", "20", "אחר"), isRequired = true, inDescription = true),
            SmartAccessoryFieldDefinition("דרגת שרשרת", SmartFieldInputType.LIST, listOf("8", "10", "אחר"), isRequired = true, inDescription = true),
            SmartAccessoryFieldDefinition("מקדם בסיס", SmartFieldInputType.LIST, listOf("30", "32", "אחר"), isRequired = true, isForCalculation = true),
            SmartAccessoryFieldDefinition("מספר ענפים", SmartFieldInputType.LIST, listOf("חד ענפי", "דו ענפי", "תלת ענפי", "ארבע ענפי"), isRequired = true, inDescription = true, isForCalculation = true),
            SmartAccessoryFieldDefinition("שיטת זווית", SmartFieldInputType.LIST, listOf("90° / 120°", "45° / 60° ביחס לאנך"), isRequired = true, isForCalculation = true),
            SmartAccessoryFieldDefinition("אורך", SmartFieldInputType.DECIMAL, isRequired = true, inDescription = true),
            SmartAccessoryFieldDefinition("אביזר קצה 1", SmartFieldInputType.LIST, listOf("ללא", "אונקלי נעילה עצמית", "אונקלי קיצור שרשרת", "אחר"), isRequired = true, inDescription = true),
            SmartAccessoryFieldDefinition("אביזר קצה 2", SmartFieldInputType.LIST, listOf("ללא", "אונקלי נעילה עצמית", "אונקלי קיצור שרשרת", "אחר"), inDescription = true),
            SmartAccessoryFieldDefinition("ע.ע.ב", SmartFieldInputType.READ_ONLY, isRequired = true, isSeparateColumn = true),
            SmartAccessoryFieldDefinition("האם בדיקה ראשונה", SmartFieldInputType.BOOLEAN),
            SmartAccessoryFieldDefinition("עומס מבחן", SmartFieldInputType.DECIMAL, isSeparateColumn = true),
            SmartAccessoryFieldDefinition("הערה לשורת התסקיר", SmartFieldInputType.TEXT)
        )
    ),
    SmartAccessoryTemplate(
        typeName = "מענבי כבל",
        descriptionTemplate = "מענב כבל {מספר ענפים} {קוטר כבל}מ\"מ באורך {אורך} מטר, {סוג סיומת}",
        fields = listOf(
            SmartAccessoryFieldDefinition("יצרן", SmartFieldInputType.LIST, listOf("ללא", "אחר"), isMemoryKey = true),
            SmartAccessoryFieldDefinition("דגם", SmartFieldInputType.LIST, listOf("ללא", "אחר"), isMemoryKey = true),
            SmartAccessoryFieldDefinition("מספר ענפים", SmartFieldInputType.LIST, listOf("חד ענפי", "דו ענפי", "תלת ענפי", "ארבע ענפי"), isRequired = true, inDescription = true, isForCalculation = true),
            SmartAccessoryFieldDefinition("שיטת זווית", SmartFieldInputType.LIST, listOf("90° / 120°", "45° / 60° ביחס לאנך"), isRequired = true, isForCalculation = true),
            SmartAccessoryFieldDefinition("קוטר כבל", SmartFieldInputType.LIST, listOf("8", "10", "12", "13", "14", "16", "18", "20", "22", "24", "26", "28", "30", "32", "36", "40", "אחר"), isRequired = true, inDescription = true, isForCalculation = true),
            SmartAccessoryFieldDefinition("מקדם בסיס", SmartFieldInputType.LIST, listOf("8", "אחר"), isRequired = true, isForCalculation = true, defaultValue = "8"),
            SmartAccessoryFieldDefinition("אורך", SmartFieldInputType.DECIMAL, isRequired = true, inDescription = true),
            // פורמט "טקסט,מקדם" — הטקסט נראה לבודק ומוטבע בתיאור; המקדם נשלף לחישוב ע.ע.ב
            SmartAccessoryFieldDefinition("סוג סיומת", SmartFieldInputType.LIST, listOf("שרוול לחיצה,0.8", "שרוול יציקה,0.9", "מהדקי כבל,0.5", "אחר"), isRequired = true, inDescription = true, isForCalculation = true),
            SmartAccessoryFieldDefinition("אביזר קצה 1", SmartFieldInputType.LIST, listOf("ללא", "אחר"), inDescription = true),
            SmartAccessoryFieldDefinition("ע.ע.ב", SmartFieldInputType.READ_ONLY, isRequired = true, isSeparateColumn = true),
            SmartAccessoryFieldDefinition("האם בדיקה ראשונה", SmartFieldInputType.BOOLEAN),
            SmartAccessoryFieldDefinition("עומס מבחן", SmartFieldInputType.DECIMAL, isSeparateColumn = true),
            SmartAccessoryFieldDefinition("הערה לשורת התסקיר", SmartFieldInputType.TEXT)
        )
    ),
    SmartAccessoryTemplate(
        typeName = "רצועות הרמה",
        descriptionTemplate = "{סוג רצועה} {חומר גלם} באורך {אורך} מטר, {גוון / ע.ע.ב}",
        fields = listOf(
            SmartAccessoryFieldDefinition("יצרן", SmartFieldInputType.LIST, listOf("ללא", "אחר"), isMemoryKey = true),
            SmartAccessoryFieldDefinition("דגם", SmartFieldInputType.LIST, listOf("ללא", "אחר"), isMemoryKey = true),
            SmartAccessoryFieldDefinition("סוג רצועה", SmartFieldInputType.LIST, listOf("רצועת הרמה שטוחה", "רצועת הרמה עגולה", "רצועת הרמה אינסופית", "אחר"), isRequired = true, inDescription = true),
            SmartAccessoryFieldDefinition("רוחב / ע.ע.ב", SmartFieldInputType.LIST, listOf("30 - 1 טון", "60 - 2 טון", "90 - 3 טון", "120 - 4 טון", "150 - 5 טון", "180 - 6 טון", "210 - 8 טון", "240 - 10 טון", "אחר"), inDescription = true, isForCalculation = true),
            SmartAccessoryFieldDefinition("גוון / ע.ע.ב", SmartFieldInputType.LIST, listOf("סגולה - 1 טון", "ירוקה - 2 טון", "צהובה - 3 טון", "אפורה - 4 טון", "אדומה - 5 טון", "חומה - 6 טון", "כחולה - 8 טון", "כתומה - 10 טון", "אחר"), inDescription = true, isForCalculation = true),
            SmartAccessoryFieldDefinition("חומר גלם", SmartFieldInputType.LIST, listOf("מפוליאסטר", "פוליפרופילן", "ניילון", "אחר"), inDescription = true),
            SmartAccessoryFieldDefinition("אורך", SmartFieldInputType.DECIMAL, isRequired = true, inDescription = true),
            SmartAccessoryFieldDefinition("ע.ע.ב", SmartFieldInputType.READ_ONLY, isRequired = true, isSeparateColumn = true),
            SmartAccessoryFieldDefinition("האם בדיקה ראשונה", SmartFieldInputType.BOOLEAN),
            SmartAccessoryFieldDefinition("עומס מבחן", SmartFieldInputType.DECIMAL, isSeparateColumn = true),
            SmartAccessoryFieldDefinition("הערה לשורת התסקיר", SmartFieldInputType.TEXT)
        )
    ),
    SmartAccessoryTemplate(
        typeName = "אביזרי קצה",
        descriptionTemplate = "{סוג אביזר קצה} {מידה} {סוג פין / נעילה}",
        fields = listOf(
            SmartAccessoryFieldDefinition("סוג אביזר קצה", SmartFieldInputType.LIST, listOf("סגיר אומגה", "אחר"), isRequired = true, inDescription = true),
            SmartAccessoryFieldDefinition("מידה", SmartFieldInputType.LIST, listOf("1/4\"", "5/16\"", "3/8\"", "1/2\"", "5/8\"", "3/4\"", "7/8\"", "1\"", "1-1/8\"", "1-1/4\"", "אחר"), isRequired = true, inDescription = true),
            SmartAccessoryFieldDefinition("סוג פין / נעילה", SmartFieldInputType.LIST, listOf("בורג", "פין + אום + אבטחה", "פין הברגה", "פין ביטחון", "אחר"), isRequired = true, inDescription = true),
            SmartAccessoryFieldDefinition("יצרן", SmartFieldInputType.LIST, listOf("ללא", "אחר"), isMemoryKey = true),
            SmartAccessoryFieldDefinition("דגם", SmartFieldInputType.LIST, listOf("ללא", "אחר"), isMemoryKey = true),
            SmartAccessoryFieldDefinition("ע.ע.ב", SmartFieldInputType.READ_ONLY, isRequired = true, isSeparateColumn = true),
            SmartAccessoryFieldDefinition("האם בדיקה ראשונה", SmartFieldInputType.BOOLEAN),
            SmartAccessoryFieldDefinition("עומס מבחן", SmartFieldInputType.DECIMAL, isSeparateColumn = true),
            SmartAccessoryFieldDefinition("הערה לשורת התסקיר", SmartFieldInputType.TEXT)
        )
    ),
    SmartAccessoryTemplate(
        typeName = "סגיר אומגה",
        // " אינץ' כתו קבוע בתבנית — לא חלק מערך {מידה} — למיקום נכון ב-RTL
        descriptionTemplate = "סגיר אומגה {מידה}\" {סוג פין / נעילה}",
        fields = listOf(
            SmartAccessoryFieldDefinition("מידה", SmartFieldInputType.LIST, listOf("1/4", "5/16", "3/8", "1/2", "5/8", "3/4", "7/8", "1", "1-1/8", "1-1/4", "אחר"), isRequired = true, inDescription = true),
            SmartAccessoryFieldDefinition("סוג פין / נעילה", SmartFieldInputType.LIST, listOf("בורג", "פין + אום + אבטחה", "פין הברגה", "פין ביטחון", "אחר"), isRequired = true, inDescription = true),
            SmartAccessoryFieldDefinition("יצרן", SmartFieldInputType.LIST, listOf("ללא", "אחר"), isMemoryKey = true),
            SmartAccessoryFieldDefinition("דגם", SmartFieldInputType.LIST, listOf("ללא", "אחר"), isMemoryKey = true),
            SmartAccessoryFieldDefinition("ע.ע.ב", SmartFieldInputType.READ_ONLY, isRequired = true, isSeparateColumn = true),
            SmartAccessoryFieldDefinition("האם בדיקה ראשונה", SmartFieldInputType.BOOLEAN),
            SmartAccessoryFieldDefinition("עומס מבחן", SmartFieldInputType.DECIMAL, isSeparateColumn = true),
            SmartAccessoryFieldDefinition("הערה לשורת התסקיר", SmartFieldInputType.TEXT)
        )
    ),
    SmartAccessoryTemplate(
        typeName = "התקן להרמת שוחות",
        descriptionTemplate = "התקן להרמת שוחות {יצרן} {דגם}",
        fields = listOf(
            SmartAccessoryFieldDefinition("יצרן", SmartFieldInputType.LIST, listOf("ללא", "אחר"), isMemoryKey = true, inDescription = true),
            SmartAccessoryFieldDefinition("דגם", SmartFieldInputType.LIST, listOf("ללא", "אחר"), isMemoryKey = true, inDescription = true),
            SmartAccessoryFieldDefinition("ע.ע.ב", SmartFieldInputType.MULTILINE_TEXT, isRequired = false, isSeparateColumn = true),
            SmartAccessoryFieldDefinition("הערה לשורת התסקיר", SmartFieldInputType.TEXT)
        )
    )
)

// זורע/מעדכן תבניות מובנות ל-SharedPreferences.
// MERGE + force-refresh לתבניות ספציפיות:
// מוסיף תבניות חדשות שחסרות, ומחליף בכוח תבניות ב-SEED_FORCE_REFRESH_V8 (תיקון v8).
private fun seedDefaultTemplatesIfNeeded(prefs: android.content.SharedPreferences) {
    val storedVersion = prefs.getInt(SEED_VERSION_KEY, 0)
    if (storedVersion >= CURRENT_SEED_VERSION) return
    val saved = loadSmartAccessoryTemplates(prefs)
    val savedNames = saved.map { it.typeName }.toSet()
    val forceRefresh = when {
        storedVersion < 8 -> SEED_FORCE_REFRESH_V8 + SEED_FORCE_REFRESH_V9
        storedVersion < 9 -> SEED_FORCE_REFRESH_V9
        else -> emptySet()
    }
    val toReplace = defaultAccessoryTemplates.filter { it.typeName !in savedNames || it.typeName in forceRefresh }
    val toKeep = saved.filter { it.typeName !in toReplace.map { t -> t.typeName }.toSet() }
    val merged = sortSmartTemplates(toKeep + toReplace)
    saveSmartAccessoryTemplates(prefs, merged)
    prefs.edit().putInt(SEED_VERSION_KEY, CURRENT_SEED_VERSION).apply()
}

// ────────────────────────────────────────────────────────
// חישוב ע.ע.ב — שרשרת, כבל, רצועות הרמה, אביזרי קצה
// ════════════════════════════════════════════════════════

// עדיפות: נוסחת-משתמש עם ביטוי → חישוב מובנה. נוסחה עם ביטוי ריק = הגדרת ספרות בלבד.
private fun resolveWllText(
    template: SmartAccessoryTemplate,
    fieldValues: Map<String, String>,
    endWllTable: List<Pair<String, String>>
): String {
    val userWllFormula = template.formulas.firstOrNull { isSmartWllTargetName(it.targetFieldName) }
    // מחשב מקדמים מאפשרויות LIST ("טקסט,מקדם") ומזריק אותם כ-__coeff__שם_שדה
    val enriched = injectListOptionCoeffs(template, fieldValues)
    return when {
        userWllFormula != null && userWllFormula.expression.isNotBlank() -> {
            calculateSmartFormulaResults(template, enriched, "").first
                .entries.firstOrNull { isSmartWllTargetName(it.key) }?.value.orEmpty()
        }
        else -> {
            val digits = userWllFormula?.digits ?: 1
            builtInWllValue(template.typeName, enriched, endWllTable, digits).orEmpty()
        }
    }
}

// ע.ע.ב סגיר אומגה לפי מידה — Grade S / BS3551 / EN13889 (ערכי ברירת מחדל)
private val shackleWllBySize = mapOf(
    "1/4\""   to 0.5,
    "5/16\""  to 0.75,
    "3/8\""   to 1.0,
    "1/2\""   to 2.0,
    "5/8\""   to 3.25,
    "3/4\""   to 4.75,
    "7/8\""   to 6.5,
    "1\""     to 8.5,
    "1-1/8\"" to 10.5,
    "1-1/4\"" to 13.5
)

// ── טבלת ע.ע.ב לאביזרי קצה — ניתנת לעריכה ע"י הבודק ──
// v2: מפתחות ללא " (האינץ' כתו קבוע בתבנית התיאור בלבד)
private const val KEY_END_ACCESSORY_WLL_TABLE = "end_acc_wll_table_v2"

private fun defaultEndAccessoryWllTable(): List<Pair<String, String>> = listOf(
    "1/4" to "0.5", "5/16" to "0.75", "3/8" to "1", "1/2" to "2",
    "5/8" to "3.25", "3/4" to "4.75", "7/8" to "6.5", "1" to "8.5",
    "1-1/8" to "10.5", "1-1/4" to "13.5"
)

private fun loadEndAccessoryWllTable(prefs: android.content.SharedPreferences): MutableList<Pair<String, String>> {
    val raw = prefs.getString(KEY_END_ACCESSORY_WLL_TABLE, null)
    if (raw.isNullOrBlank()) return defaultEndAccessoryWllTable().toMutableList()
    return try {
        raw.split("§ROW§").mapNotNull { entry ->
            val parts = entry.split("§SEP§", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMutableList()
    } catch (_: Exception) { defaultEndAccessoryWllTable().toMutableList() }
}

private fun saveEndAccessoryWllTable(prefs: android.content.SharedPreferences, table: List<Pair<String, String>>) {
    prefs.edit()
        .putString(KEY_END_ACCESSORY_WLL_TABLE, table.joinToString("§ROW§") { "${it.first}§SEP§${it.second}" })
        .apply()
}

private fun chainAngleFactor(branches: String, isAt90: Boolean): Double = when {
    branches.contains("חד") -> 1.0
    branches.contains("דו") -> if (isAt90) 1.4 else 1.0
    else -> if (isAt90) 2.1 else 1.5   // תלת / ארבע ענפי
}

private fun formatWllTon(value: Double, digits: Int = 1): String {
    if (value % 1 == 0.0) return value.toLong().toString()
    return String.format(Locale.US, "%.${digits}f", value)
}

// פירוק אפשרות רשימה עם מקדם מוטמע: "שרוול לחיצה,0.8" → "שרוול לחיצה"
// אפשרות ללא מקדם: "חד ענפי" → "חד ענפי" (ללא שינוי)
private fun listOptionDisplayText(option: String): String {
    val comma = option.lastIndexOf(',')
    if (comma < 0) return option.trim()
    return if (option.substring(comma + 1).trim().toDoubleOrNull() != null)
        option.substring(0, comma).trim()
    else option.trim()
}

// מחלץ מקדם מאפשרות רשימה: "שרוול לחיצה,0.8" → 0.8  |  "חד ענפי" → null
private fun listOptionCoeff(option: String): Double? {
    val comma = option.lastIndexOf(',')
    return if (comma >= 0) option.substring(comma + 1).trim().toDoubleOrNull() else null
}

// מזריק מפתחות "__coeff__שם_שדה" לפי בחירות ברשימות עם מקדם מוטמע ("טקסט,0.8")
private fun injectListOptionCoeffs(
    template: SmartAccessoryTemplate,
    fieldValues: Map<String, String>
): Map<String, String> {
    val enriched = fieldValues.toMutableMap()
    template.fields.forEach { field ->
        if (field.inputType == SmartFieldInputType.LIST) {
            val selected = fieldValues[field.name].orEmpty()
            val coeff = field.options.firstOrNull { listOptionDisplayText(it) == selected }
                ?.let { listOptionCoeff(it) }
            if (coeff != null) enriched["__coeff__${field.name}"] = coeff.toString()
        }
    }
    return enriched
}

private fun chainWllText(diameter: Double, baseCoeff: Double, branches: String, angleMethod: String, digits: Int = 1): String {
    val isSingle = branches.contains("חד")
    val wll90 = baseCoeff * diameter * diameter * chainAngleFactor(branches, true) / 1000
    return if (!isSingle && angleMethod.contains("90") && angleMethod.contains("120")) {
        val wll120 = baseCoeff * diameter * diameter * chainAngleFactor(branches, false) / 1000
        "${formatWllTon(wll90, digits)}/90°\n${formatWllTon(wll120, digits)}/120°"
    } else {
        formatWllTon(wll90, digits)   // ללא " טון" — כותרת עמודת הדוח כבר מציינת את היחידה
    }
}

private fun cableWllText(diameter: Double, baseCoeff: Double, endFactor: Double, branches: String, angleMethod: String, digits: Int = 1): String {
    val isSingle = branches.contains("חד")
    val wll90 = baseCoeff * diameter * diameter * endFactor * chainAngleFactor(branches, true) / 1000
    return if (!isSingle && angleMethod.contains("90") && angleMethod.contains("120")) {
        val wll120 = baseCoeff * diameter * diameter * endFactor * chainAngleFactor(branches, false) / 1000
        "${formatWllTon(wll90, digits)}/90°\n${formatWllTon(wll120, digits)}/120°"
    } else {
        formatWllTon(wll90, digits)   // ללא " טון"
    }
}

// חישוב ע.ע.ב לרצועות הרמה לפי גוון או רוחב (מחלץ "X טון" מהאפשרות הנבחרת)
private fun strapWllText(colorField: String, widthField: String): String? {
    val source = colorField.ifBlank { widthField }
    if (source.isBlank() || source == "ללא" || source == "אחר") return null
    return Regex("(\\d+(?:[.,]\\d+)?\\s*טון)").find(source)?.value
}

// מחזיר ע.ע.ב מחושב עבור תבניות מובנות, או null אם אין חישוב מובנה.
// endWllTable: טבלת מידה→ע.ע.ב עריכה לאביזרי קצה (ריק = השתמש בברירת מחדל)
private fun builtInWllValue(
    templateName: String,
    values: Map<String, String>,
    endWllTable: List<Pair<String, String>> = emptyList(),
    digits: Int = 1
): String? {
    return when (templateName) {
        "מענבי שרשרת" -> {
            val diameter = parseSmartDecimalValue(values["קוטר"].orEmpty()) ?: return null
            val baseCoeff = parseSmartDecimalValue(values["מקדם בסיס"].orEmpty()) ?: return null
            val branches = values["מספר ענפים"].orEmpty().ifBlank { return null }
            val angle = values["שיטת זווית"].orEmpty()
            chainWllText(diameter, baseCoeff, branches, angle, digits)
        }
        "מענבי כבל" -> {
            val diameter = parseSmartDecimalValue(values["קוטר כבל"].orEmpty()) ?: return null
            val branches = values["מספר ענפים"].orEmpty().ifBlank { return null }
            val angle = values["שיטת זווית"].orEmpty()
            val termType = values["סוג סיומת"].orEmpty()
            val baseCoeff = parseSmartDecimalValue(values["מקדם בסיס"].orEmpty()) ?: 8.0
            // עדיפות: מקדם מוטמע באפשרות הרשימה ("שרוול לחיצה,0.8") ← שדה "מקדם סיומת" ← מיפוי מילות מפתח
            val endFactor = parseSmartDecimalValue(values["__coeff__סוג סיומת"].orEmpty())
                ?: parseSmartDecimalValue(values["מקדם סיומת"].orEmpty())
                ?: when {
                    termType.contains("מהדקי") -> 0.5
                    termType.contains("לחיצה") -> 0.8
                    termType.contains("יציקה") -> 0.9
                    else -> 1.0
                }
            cableWllText(diameter, baseCoeff, endFactor, branches, angle, digits)
        }
        "רצועות הרמה" -> strapWllText(
            values["גוון / ע.ע.ב"].orEmpty(),
            values["רוחב / ע.ע.ב"].orEmpty()
        )
        "אביזרי קצה", "סגיר אומגה" -> {
            val rawSize = values["מידה"].orEmpty()
            val normSize = rawSize.trimEnd('"')   // תמיכה בפורמטים עם " ובלי " ישן/חדש
            val fromTable = endWllTable.firstOrNull { it.first == rawSize || it.first == normSize }
                ?.second?.toDoubleOrNull()
            val wll = fromTable
                ?: shackleWllBySize[rawSize]
                ?: shackleWllBySize["$normSize\""]  // fallback: מידה ישנה עם "
            wll?.let { "${formatWllTon(it, digits)} טון" }
        }
        else -> null
    }
}

// חולץ מכפיל מנוסחה פשוטה: {ע.ע.ב}*X  or  X*{ע.ע.ב}
private fun extractWllMultiplierFromFormula(expression: String): Double? {
    val e = expression.trim().replace(" ", "")
    val m = Regex("""^\{[^}]+\}\*([0-9.]+)$|^([0-9.]+)\*\{[^}]+\}$""").find(e) ?: return null
    return (m.groupValues[1].ifBlank { m.groupValues[2] }).toDoubleOrNull()
}

// מחזיר מכפיל עומס מבחן מנוסחת תבנית (ברירת מחדל: 2.0) — מתעלם מנוסחות ספרות-בלבד (ביטוי ריק)
private fun testLoadMultiplierFromTemplate(template: SmartAccessoryTemplate): Double {
    val testLoadFormulaExpr = template.formulas.firstOrNull { formula ->
        if (formula.expression.isBlank()) return@firstOrNull false
        val target = formula.targetFieldName.trim().let {
            if (it.startsWith("{") && it.endsWith("}")) it.removeSurrounding("{", "}") else it
        }
        target.contains("עומס") || formula.name.contains("עומס")
    }?.expression ?: return 2.0
    return extractWllMultiplierFromFormula(testLoadFormulaExpr) ?: 2.0
}

// מחזיר מספר ספרות לעומס מבחן — נוסחת ספרות-בלבד מנצחת על נוסחה עם ביטוי
private fun testLoadDigitsFromTemplate(template: SmartAccessoryTemplate): Int =
    template.formulas.firstOrNull { formula ->
        formula.expression.isBlank() && isSmartTestLoadTargetName(formula.targetFieldName)
    }?.digits ?: template.formulas.firstOrNull { formula ->
        val target = formula.targetFieldName.trim().let {
            if (it.startsWith("{") && it.endsWith("}")) it.removeSurrounding("{", "}") else it
        }
        target.contains("עומס") || formula.name.contains("עומס")
    }?.digits ?: 1

// מחשב עומס מבחן = WLL × מכפיל, כולל פורמט מולטי-ליין (4.2/90°\n3/120°)
private fun builtInTestLoadValue(wllText: String, multiplier: Double = 2.0, digits: Int = 1): String? {
    if (wllText.isBlank()) return null
    val lines = wllText.lines().filter { it.isNotBlank() }
    // פורמט עם זווית (X/Aangle°) — כל שורה בנפרד
    if (lines.any { it.contains("/") && it.contains("°") }) {
        val result = lines.mapNotNull { line ->
            val slashIdx = line.indexOf('/')
            if (slashIdx < 0) null
            else {
                val num = parseSmartDecimalValue(line.substring(0, slashIdx)) ?: return@mapNotNull null
                "${formatWllTon(num * multiplier, digits)}${line.substring(slashIdx)}"
            }
        }
        if (result.size == lines.size) return result.joinToString("\n")
        return null
    }
    // שורה בודדת
    val num = parseSmartDecimalValue(wllText) ?: return null
    return formatWllTon(num * multiplier, digits)   // ללא " טון"
}

// ────────────────────────────────────────────────────────
// כיול סריקת מספר זיהוי
// ════════════════════════════════════════════════════════

private data class SmartScanCalibration(
    val preferLargeFont: Boolean = true,
    val formatType: String = "ALPHANUMERIC",
    val extraCondition: String = "MIN_4_CHARS"
)

private const val SCAN_CALIB_KEY_PREFIX = "smart_scan_calib_"
private val VALID_FORMAT_TYPES = listOf("NUMBERS_ONLY", "ALPHANUMERIC_EN", "ALPHANUMERIC", "ANY")
private val VALID_EXTRA_CONDITIONS = listOf("NONE", "MIN_4_CHARS", "MIN_1_DIGIT", "IGNORE_KEYWORDS", "OTHER")

private fun loadScanCalibration(prefs: android.content.SharedPreferences, templateName: String): SmartScanCalibration {
    val key = SCAN_CALIB_KEY_PREFIX + Base64.encodeToString(templateName.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    val raw = prefs.getString(key, null) ?: return SmartScanCalibration()
    return try {
        val parts = raw.split("|")
        SmartScanCalibration(
            preferLargeFont = parts.getOrNull(0)?.toBooleanStrictOrNull() ?: true,
            formatType = parts.getOrNull(1)?.takeIf { it in VALID_FORMAT_TYPES } ?: "ALPHANUMERIC",
            extraCondition = parts.getOrNull(2)?.takeIf { it in VALID_EXTRA_CONDITIONS } ?: "MIN_4_CHARS"
        )
    } catch (_: Exception) { SmartScanCalibration() }
}

private fun saveScanCalibration(prefs: android.content.SharedPreferences, templateName: String, calib: SmartScanCalibration) {
    val key = SCAN_CALIB_KEY_PREFIX + Base64.encodeToString(templateName.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    prefs.edit().putString(key, "${calib.preferLargeFont}|${calib.formatType}|${calib.extraCondition}").apply()
}

private fun extractSerialFromOcr(rawText: String, calib: SmartScanCalibration): String {
    if (rawText.isBlank()) return ""
    val IGNORED_WORDS = setOf("WLL", "CE", "EN", "SWL", "MAX", "MIN", "SN", "NO", "S/N")
    val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
    val afterExtra = when (calib.extraCondition) {
        "MIN_4_CHARS" -> lines.filter { it.length >= 4 }
        "MIN_1_DIGIT" -> lines.filter { it.any { c -> c.isDigit() } }
        "IGNORE_KEYWORDS" -> lines.filter { it.uppercase() !in IGNORED_WORDS }
        else -> lines
    }
    return when (calib.formatType) {
        "NUMBERS_ONLY" -> afterExtra.firstOrNull { it.all { c -> c.isDigit() } }
        "ALPHANUMERIC_EN" -> afterExtra.firstOrNull { it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' } }
        "ALPHANUMERIC" -> afterExtra.firstOrNull { it.all { c -> c.isLetterOrDigit() || c in "-_/." } }
        else -> afterExtra.firstOrNull()
    } ?: afterExtra.firstOrNull() ?: lines.firstOrNull().orEmpty()
}

// ────────────────────────────────────────────────────────
// הקומפוזבל הראשי
// ════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartAccessoriesTableScreen(
    inspectionDate: String = "",
    initialRows: List<SmartFormTableRow> = emptyList(),
    initialDefects: List<SmartFormDefect> = emptyList(),
    draftRunningNumber: String = "",
    onTransferToReport: (rows: List<SmartFormTableRow>, defects: List<SmartFormDefect>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("lifting_inspection_prefs", Context.MODE_PRIVATE) }
    var templates by remember {
        seedDefaultTemplatesIfNeeded(prefs)
        mutableStateOf(loadSmartAccessoryTemplates(prefs))
    }

    // טבלת ע.ע.ב לאביזרי קצה — עריכה בעורך תבנית, משמשת לחישוב מובנה
    val endAccessoryWllTable: SnapshotStateList<Pair<String, String>> = remember {
        mutableStateListOf<Pair<String, String>>().also { it.addAll(loadEndAccessoryWllTable(prefs)) }
    }

    val tableRows: SnapshotStateList<SmartFormTableRow> = remember {
        mutableStateListOf<SmartFormTableRow>().also { it.addAll(initialRows) }
    }
    val tableDefects: SnapshotStateList<SmartFormDefect> = remember {
        mutableStateListOf<SmartFormDefect>().also { it.addAll(initialDefects) }
    }

    // בחירת תבנית
    var selectedTemplate by remember { mutableStateOf(templates.firstOrNull()) }
    var templateDropdownExpanded by remember { mutableStateOf(false) }
    // תווית הדרופדאון (עבור מצבים מיוחדים)
    var dropdownLabel by remember { mutableStateOf(templates.firstOrNull()?.typeName ?: "בחר סוג אביזר") }

    // מצב: שימוש / עריכה / הזנה ידנית
    var showEditor by remember { mutableStateOf(false) }
    var manualEntryMode by remember { mutableStateOf(false) }

    // ערכי שדות של הטופס הנוכחי
    val fieldValues = remember { mutableStateMapOf<String, String>() }
    val otherListFieldNames = remember { mutableStateListOf<String>() }

    // שדות ידניים קבועים לכל אביזר
    var quantity by remember { mutableStateOf("") }
    val serialNumbersList = remember { mutableStateListOf<String>() }

    // תיאורים ידניים שנשמרו בעבר
    var savedManualDescriptions by remember {
        mutableStateOf(
            prefs.getStringSet("manual_accessory_descriptions", emptySet())
                ?.toList()?.sorted() ?: emptyList()
        )
    }
    var savedManualEndAccDescriptions by remember {
        mutableStateOf(
            prefs.getStringSet("manual_end_acc_descriptions", emptySet())
                ?.toList()?.sorted() ?: emptyList()
        )
    }
    var manualEntryIsEndAcc by remember { mutableStateOf(false) }

    // שדות הזנה ידנית
    var manualDescription by remember { mutableStateOf("") }
    var manualManufacturer by remember { mutableStateOf("") }
    var manualModel by remember { mutableStateOf("") }
    var manualWll by remember { mutableStateOf("") }
    var manualTestLoad by remember { mutableStateOf("") }

    // תפריט משני לאביזרי קצה
    var showEndAccSubPicker by remember { mutableStateOf(false) }
    var endAccSubDropExpanded by remember { mutableStateOf(false) }

    // דיאלוג מחיקת תבנית
    var deleteDialogOpen by remember { mutableStateOf(false) }
    var deleteDialogCandidate by remember { mutableStateOf("") }

    // סריקת מספרי זיהוי
    var pendingScanSlotIndex by remember { mutableStateOf(-1) }
    var scanCalib by remember(selectedTemplate) {
        mutableStateOf(loadScanCalibration(prefs, selectedTemplate?.typeName.orEmpty()))
    }

    // אתחל ערכי ברירת מחדל כשמשתנה התבנית
    LaunchedEffect(selectedTemplate) {
        selectedTemplate?.fields?.forEach { field ->
            if (field.defaultValue.isNotBlank() && !fieldValues.containsKey(field.name)) {
                fieldValues[field.name] = field.defaultValue
            }
        }
    }

    // מחושב מחוץ ל-LaunchedEffect כדי לשמש כמפתח — כך כל שינוי בע.ע.ב מפעיל מחדש את האפקט
    val firstInspectionValue = fieldValues["האם בדיקה ראשונה"]
    val currentWllForEffect = selectedTemplate?.let {
        resolveWllText(it, fieldValues.toMap(), endAccessoryWllTable)
    } ?: ""

    // עומס מבחן אוטומטי: מופעל כשמשתנה הצ'קבוקס, המידה, או ערך ע.ע.ב
    LaunchedEffect(selectedTemplate?.typeName, firstInspectionValue, currentWllForEffect) {
        if (firstInspectionValue != "כן") return@LaunchedEffect
        val tpl = selectedTemplate ?: return@LaunchedEffect
        val wllText = currentWllForEffect
        val (formulaResults, _) = calculateSmartFormulaResults(tpl, fieldValues.toMap(), wllText)
        // עדכן שדות עריכים עם תוצאות נוסחאות
        formulaResults.forEach { (targetName, computed) ->
            val field = tpl.fields.firstOrNull { it.name == targetName }
            if (field != null && field.inputType !in listOf(SmartFieldInputType.FORMULA, SmartFieldInputType.READ_ONLY, SmartFieldInputType.BOOLEAN) && computed.isNotBlank()) {
                fieldValues[targetName] = computed
            }
        }
        // חישוב עומס מבחן מובנה (כולל שרשרת/כבל עם פורמט זווית)
        if (wllText.isNotBlank()) {
            val testLoadField = tpl.fields.firstOrNull { it.name.contains("עומס") }
            if (testLoadField != null) {
                val multiplier = testLoadMultiplierFromTemplate(tpl)
                val testLoadDigits = testLoadDigitsFromTemplate(tpl)
                val isMultiLineWll = wllText.contains("°")
                if (isMultiLineWll) {
                    // מולטי-ליין (שרשרת/כבל עם זוויות): תמיד חשב מחדש — ע.ע.ב עשוי להשתנות
                    builtInTestLoadValue(wllText, multiplier, testLoadDigits)?.let { fieldValues[testLoadField.name] = it }
                } else if (formulaResults[testLoadField.name].isNullOrBlank() && fieldValues[testLoadField.name].isNullOrBlank()) {
                    // שורה בודדת: חשב רק אם אין ערך קיים
                    builtInTestLoadValue(wllText, multiplier, testLoadDigits)?.let { fieldValues[testLoadField.name] = it }
                }
            }
        }
    }
    var scanCalibExpanded by remember { mutableStateOf(false) }

    val serialScanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        val idx = pendingScanSlotIndex
        pendingScanSlotIndex = -1
        if (bitmap == null || idx < 0 || idx >= serialNumbersList.size) return@rememberLauncherForActivityResult
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val serial = extractSerialFromOcr(result.text, scanCalib)
                if (serial.isNotBlank()) serialNumbersList[idx] = serial
                else Toast.makeText(context, "לא זוהה מספר זיהוי — הקלד ידנית", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "שגיאה בסריקה, נסה שוב", Toast.LENGTH_SHORT).show()
            }
    }

    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingPhotoSerial by remember { mutableStateOf("") }

    val smartPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingPhotoUri
        val serial = pendingPhotoSerial
        if (success && uri != null && serial.isNotBlank() && draftRunningNumber.isNotBlank()) {
            val existing = ReportPhotoStorage.loadPhotos(context, draftRunningNumber).toMutableList()
            existing.add(
                ReportPhotoStorage.PhotoRecord(
                    reportNumber = draftRunningNumber,
                    serialNumber = serial,
                    uri = uri.toString(),
                    capturedAtMillis = System.currentTimeMillis()
                )
            )
            ReportPhotoStorage.savePhotos(context, draftRunningNumber, existing)
            Toast.makeText(context, "תמונה נשמרה למספר זיהוי $serial", Toast.LENGTH_SHORT).show()
        }
        pendingPhotoUri = null
        pendingPhotoSerial = ""
    }

    var defectDescription by remember { mutableStateOf("") }
    var defectFixUntil by remember { mutableStateOf("") }
    var defectRowDialogOpen by remember { mutableStateOf(false) }
    var defectDialogRowNumber by remember { mutableStateOf(0) }
    var defectSelectedSerial by remember { mutableStateOf("") }

    // עורך תבנית
    var editorTypeName by remember { mutableStateOf("") }
    var editorDescriptionTemplate by remember { mutableStateOf("") }
    val editorFields = remember { mutableStateListOf(SmartFieldDraft()) }
    val editorFormulas = remember { mutableStateListOf<SmartFormulaDraft>() }
    var editorEditingOriginal by remember { mutableStateOf<String?>(null) }
    var editorMessage by remember { mutableStateOf("") }

    // ולידציה הוספת אביזר
    var addRowMessage by remember { mutableStateOf("") }

    fun resetForm() {
        fieldValues.clear()
        otherListFieldNames.clear()
        quantity = ""
        serialNumbersList.clear()
        addRowMessage = ""
        manualDescription = ""
        manualManufacturer = ""
        manualModel = ""
        manualWll = ""
        manualTestLoad = ""
    }

    // שומר ערכי "אחר" שהוזנו בשימוש בטופס בחזרה לאפשרויות התבנית
    fun saveOtherValuesToTemplate(template: SmartAccessoryTemplate) {
        val updatedFields = template.fields.map { field ->
            if (field.inputType != SmartFieldInputType.LIST) return@map field
            if (field.name !in otherListFieldNames) return@map field
            val customValue = fieldValues[field.name].orEmpty().trim()
            if (customValue.isNotBlank() && customValue !in field.options) {
                field.copy(options = field.options + customValue)
            } else field
        }
        if (updatedFields != template.fields) {
            val updatedTemplate = template.copy(fields = updatedFields)
            val updatedTemplates = templates.map { if (it.typeName == template.typeName) updatedTemplate else it }
            saveSmartAccessoryTemplates(prefs, updatedTemplates)
            templates = updatedTemplates
            selectedTemplate = updatedTemplate
        }
    }

    fun buildRowFromCurrentForm(template: SmartAccessoryTemplate): SmartFormTableRow? {
        val allValues = fieldValues.toMutableMap()
        // ע.ע.ב: נוסחת-משתמש קודמת לחישוב מובנה
        val resolvedWll = resolveWllText(template, allValues, endAccessoryWllTable)
        if (resolvedWll.isNotBlank()) allValues["ע.ע.ב"] = resolvedWll
        val (updatedValues, _) = calculateSmartFormulaResults(template, allValues, allValues["ע.ע.ב"].orEmpty())
        allValues.putAll(updatedValues)

        // עומס מבחן = ע.ע.ב × מכפיל מהתבנית (כולל שרשרת/כבל עם פורמט זווית)
        if (allValues["האם בדיקה ראשונה"] == "כן") {
            val wllVal = allValues["ע.ע.ב"]
            if (!wllVal.isNullOrBlank()) {
                val testLoadField = template.fields.firstOrNull { it.name.contains("עומס") }
                if (testLoadField != null) {
                    val multiplier = testLoadMultiplierFromTemplate(template)
                    val testLoadDigits = testLoadDigitsFromTemplate(template)
                    val isMultiLineWll = wllVal.contains("°")
                    if (isMultiLineWll || allValues[testLoadField.name].isNullOrBlank()) {
                        builtInTestLoadValue(wllVal, multiplier, testLoadDigits)?.let { allValues[testLoadField.name] = it }
                    }
                }
            }
        }

        // ולידציה שדות חובה — "ללא" נחשב כערך תקין
        val missingRequired = template.fields.filter { field ->
            field.isRequired && allValues[field.name].orEmpty().isBlank()
        }.map { it.name }
        if (missingRequired.isNotEmpty()) {
            addRowMessage = "שדות חובה חסרים: ${missingRequired.joinToString(", ")}"
            return null
        }
        if (quantity.isBlank()) {
            addRowMessage = "יש להזין כמות"
            return null
        }

        // ממפה "ללא" → "" בשביל תיאור ויצרן/דגם
        val displayValues = allValues.mapValues { (_, v) -> if (v == "ללא") "" else v }

        val description = buildSmartDescription(template, displayValues)
        val manufacturer = findSmartFieldByKeywords(template, listOf("יצרן"))?.let { displayValues[it.name].orEmpty() } ?: ""
        val model = findSmartFieldByKeywords(template, listOf("דגם"))?.let { displayValues[it.name].orEmpty() } ?: ""
        val testLoad = findSmartFieldByKeywords(template, listOf("עומס", "מבחן"), listOf("עומסמבחן"))?.let { displayValues[it.name].orEmpty() } ?: ""
        // מסיר "טון" מהערך — כותרת הטור בדוח כבר מציינת את היחידה
        val wll = (allValues.entries.firstOrNull { isSmartWllTargetName(it.key) }?.value ?: "")
            .removeSuffix(" טון").trim()

        // בדיקת כפילות מספרי זיהוי — בתוך השורה הנוכחית ומול שורות קיימות
        val newSerials = serialNumbersList.filter { it.isNotBlank() }
        val existingAllSerials = tableRows.flatMap { r -> r.serialNumbers.lines().filter { it.isNotBlank() } }
        val crossDuplicates = newSerials.filter { it in existingAllSerials }
        val innerDuplicates = newSerials.groupBy { it }.filter { it.value.size > 1 }.keys
        val allDuplicates = (crossDuplicates + innerDuplicates).distinct()
        if (allDuplicates.isNotEmpty()) {
            addRowMessage = "מספרי זיהוי כפולים: ${allDuplicates.joinToString(", ")}"
            return null
        }

        val rowNumber = tableRows.size + 1
        val row = SmartFormTableRow(
            rowNumber = rowNumber,
            templateName = template.typeName,
            description = description,
            manufacturer = manufacturer,
            model = model,
            quantity = quantity,
            serialNumbers = newSerials.joinToString("\n"),
            testLoad = testLoad,
            wll = wll
        )

        // שמור ערכי "אחר" חדשים בחזרה לתבנית
        saveOtherValuesToTemplate(template)

        return row
    }

    fun renumberRows() {
        val renumbered = tableRows.mapIndexed { idx, row -> row.copy(rowNumber = idx + 1) }
        tableRows.clear()
        tableRows.addAll(renumbered)
    }

    fun openEditorForTemplate(tpl: SmartAccessoryTemplate?) {
        if (tpl != null) {
            editorEditingOriginal = tpl.typeName
            editorTypeName = tpl.typeName
            editorDescriptionTemplate = tpl.descriptionTemplate
            editorFields.clear()
            // שמות שדות READ_ONLY/FORMULA שיש להם נוסחה ישירה — מוחלצים לתוך formulaExpr של השדה
            val fieldFormulaTargets = tpl.fields
                .filter { it.inputType == SmartFieldInputType.READ_ONLY || it.inputType == SmartFieldInputType.FORMULA }
                .map { it.name.trim() }.toSet()
            tpl.fields.forEach { f ->
                // נוסחת שדה: רק אם יש ביטוי לא-ריק (נוסחה ריקה עם יעד ע.ע.ב = "ספרות בלבד" — עוברת לחלק החישובים)
                val fieldFml = if (f.name.trim() in fieldFormulaTargets)
                    tpl.formulas.firstOrNull { it.targetFieldName.trim() == f.name.trim() && it.expression.isNotBlank() }?.expression ?: ""
                else ""
                editorFields.add(SmartFieldDraft(f.name, f.inputType, f.options.joinToString("\n"), f.isRequired, f.isMemoryKey, f.inDescription, f.isSeparateColumn, f.isForCalculation, f.isScannable, f.defaultValue, fieldFml))
            }
            if (editorFields.isEmpty()) editorFields.add(SmartFieldDraft())
            editorFormulas.clear()
            // נוסחאות לחלק החישובים: כולל נוסחות ריקות של ע.ע.ב (הגדרת ספרות בלבד)
            tpl.formulas
                .filter { formula ->
                    val inFieldTargets = formula.targetFieldName.trim() in fieldFormulaTargets
                    val isDigitsOnly = formula.expression.isBlank() && isSmartBuiltInCalculatedTarget(formula.targetFieldName)
                    !inFieldTargets || isDigitsOnly
                }
                .forEach { f -> editorFormulas.add(SmartFormulaDraft(f.name, f.expression, f.targetFieldName, f.digits.toString())) }
        } else {
            editorEditingOriginal = null
            editorTypeName = ""
            editorDescriptionTemplate = ""
            editorFields.clear()
            editorFields.add(SmartFieldDraft())
            editorFormulas.clear()
        }
        editorMessage = ""
        showEditor = true
        manualEntryMode = false
    }

    // ────────────── UI ──────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .displayCutoutPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "טופס חכם אביזרים",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (inspectionDate.isNotBlank()) {
            Text("תאריך בדיקה: $inspectionDate", fontSize = 13.sp, color = Color.Gray)
        }

        Divider()

        // ── בחירת סוג אביזר ──
        Text("בחירת סוג אביזר", fontWeight = FontWeight.Bold)
        Box {
            OutlinedButton(onClick = { templateDropdownExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(dropdownLabel)
            }
            DropdownMenu(expanded = templateDropdownExpanded, onDismissRequest = { templateDropdownExpanded = false }) {
                // תבניות מובנות — מסתיר תת-סוגי אביזרי קצה (מוצגים בתפריט משני)
                SMART_BUILT_IN_TEMPLATE_NAMES.filter { it !in END_ACCESSORY_CHILD_NAMES }.forEach { name ->
                    // fallback: אם התבנית חסרה מה-storage, השתמש בברירת מחדל מהקוד
                    val tpl = templates.find { it.typeName == name }
                        ?: defaultAccessoryTemplates.find { it.typeName == name }
                    if (tpl != null) {
                        val isEndAccParent = (name == "אביזרי קצה")
                        DropdownMenuItem(
                            text = { Text(if (isEndAccParent) "אביזרי קצה ▶" else name) },
                            onClick = {
                                selectedTemplate = tpl
                                dropdownLabel = name
                                manualEntryMode = false
                                showEditor = false
                                templateDropdownExpanded = false
                                showEndAccSubPicker = isEndAccParent
                                if (!isEndAccParent) resetForm()
                            }
                        )
                    }
                }

                // תבניות משתמש (ניתן למחוק)
                val userTemplates = templates.filter { it.typeName !in SMART_BUILT_IN_TEMPLATE_NAMES }
                if (userTemplates.isNotEmpty()) {
                    Divider()
                    userTemplates.forEach { tpl ->
                        DropdownMenuItem(
                            text = { Text(tpl.typeName) },
                            trailingIcon = {
                                TextButton(
                                    onClick = {
                                        deleteDialogCandidate = tpl.typeName
                                        deleteDialogOpen = true
                                        templateDropdownExpanded = false
                                    },
                                    contentPadding = PaddingValues(4.dp)
                                ) { Text("🗑", fontSize = 15.sp) }
                            },
                            onClick = {
                                selectedTemplate = tpl
                                dropdownLabel = tpl.typeName
                                manualEntryMode = false
                                showEditor = false
                                showEndAccSubPicker = false
                                templateDropdownExpanded = false
                                resetForm()
                            }
                        )
                    }
                }

                // תיאורים ידניים שנשמרו
                if (savedManualDescriptions.isNotEmpty()) {
                    Divider()
                    savedManualDescriptions.forEach { desc ->
                        DropdownMenuItem(
                            text = { Text(desc, fontSize = 13.sp) },
                            trailingIcon = {
                                TextButton(
                                    onClick = {
                                        val updated = savedManualDescriptions.filter { it != desc }
                                        savedManualDescriptions = updated
                                        prefs.edit().putStringSet("manual_accessory_descriptions", updated.toSet()).apply()
                                        templateDropdownExpanded = false
                                    },
                                    contentPadding = PaddingValues(4.dp)
                                ) { Text("🗑", fontSize = 15.sp) }
                            },
                            onClick = {
                                selectedTemplate = null
                                dropdownLabel = desc
                                manualDescription = desc
                                manualEntryMode = true
                                showEditor = false
                                showEndAccSubPicker = false
                                templateDropdownExpanded = false
                            }
                        )
                    }
                }

                // רשומות מיוחדות
                Divider()
                DropdownMenuItem(
                    text = { Text("אביזר חדש / הזנה ידנית", color = Color(0xFF1565C0)) },
                    onClick = {
                        selectedTemplate = null
                        dropdownLabel = "אביזר חדש / הזנה ידנית"
                        manualEntryMode = true
                        showEditor = false
                        showEndAccSubPicker = false
                        templateDropdownExpanded = false
                        resetForm()
                    }
                )
                DropdownMenuItem(
                    text = { Text("הכנת תבנית אביזר חדש ע\"י טופס חכם", color = Color(0xFF1565C0)) },
                    onClick = {
                        dropdownLabel = "הכנת תבנית אביזר חדש"
                        showEndAccSubPicker = false
                        templateDropdownExpanded = false
                        openEditorForTemplate(null)
                    }
                )
            }
        }

        // ── תפריט משני לאביזרי קצה ──
        if (showEndAccSubPicker) {
            Box {
                OutlinedButton(
                    onClick = { endAccSubDropExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("▼  בחר סוג אביזר קצה")
                }
                DropdownMenu(
                    expanded = endAccSubDropExpanded,
                    onDismissRequest = { endAccSubDropExpanded = false }
                ) {
                    END_ACCESSORY_CHILD_NAMES.forEach { childName ->
                        val childTpl = templates.find { it.typeName == childName }
                            ?: defaultAccessoryTemplates.find { it.typeName == childName }
                        if (childTpl != null) {
                            DropdownMenuItem(text = { Text(childName) }, onClick = {
                                openEditorForTemplate(childTpl)
                                dropdownLabel = "אביזרי קצה / $childName"
                                showEndAccSubPicker = false
                                endAccSubDropExpanded = false
                            })
                        }
                    }
                    DropdownMenuItem(text = { Text("אחר") }, onClick = {
                        val genericTpl = templates.find { it.typeName == "אביזרי קצה" }
                        selectedTemplate = genericTpl
                        dropdownLabel = "אביזרי קצה / אחר"
                        showEndAccSubPicker = false
                        endAccSubDropExpanded = false
                        resetForm()
                    })
                    // תיאורים ידניים שנשמרו — אביזרי קצה
                    if (savedManualEndAccDescriptions.isNotEmpty()) {
                        Divider()
                        savedManualEndAccDescriptions.forEach { desc ->
                            DropdownMenuItem(
                                text = { Text(desc, fontSize = 13.sp) },
                                trailingIcon = {
                                    TextButton(
                                        onClick = {
                                            val updated = savedManualEndAccDescriptions.filter { it != desc }
                                            savedManualEndAccDescriptions = updated
                                            prefs.edit().putStringSet("manual_end_acc_descriptions", updated.toSet()).apply()
                                            endAccSubDropExpanded = false
                                        },
                                        contentPadding = PaddingValues(4.dp)
                                    ) { Text("🗑", fontSize = 15.sp) }
                                },
                                onClick = {
                                    selectedTemplate = null
                                    dropdownLabel = desc
                                    manualDescription = desc
                                    manualEntryMode = true
                                    manualEntryIsEndAcc = true
                                    showEndAccSubPicker = false
                                    endAccSubDropExpanded = false
                                }
                            )
                        }
                    }
                    Divider()
                    DropdownMenuItem(
                        text = { Text("אביזר קצה חדש — הזנה ידנית", color = Color(0xFF1565C0)) },
                        onClick = {
                            resetForm()
                            selectedTemplate = null
                            dropdownLabel = "אביזר קצה / הזנה ידנית"
                            manualEntryMode = true
                            manualEntryIsEndAcc = true
                            showEndAccSubPicker = false
                            endAccSubDropExpanded = false
                        }
                    )
                }
            }
        }

        // כפתורי שימוש / עריכה
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    showEditor = false
                    manualEntryMode = false
                    dropdownLabel = selectedTemplate?.typeName ?: "בחר סוג אביזר"
                    resetForm()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (!showEditor && !manualEntryMode) Color(0xFF1B5E20) else Color(0xFF64748B))
            ) { Text("שימוש בטופס") }

            Button(
                onClick = { openEditorForTemplate(selectedTemplate) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (showEditor) Color(0xFF1B5E20) else Color(0xFF64748B))
            ) { Text("עריכת טופס חכם") }
        }

        // כפתור מחק תבנית (רק לתבניות משתמש שנבחרו)
        val isUserTemplate = selectedTemplate != null && selectedTemplate!!.typeName !in SMART_BUILT_IN_TEMPLATE_NAMES
        if (isUserTemplate) {
            OutlinedButton(
                onClick = {
                    deleteDialogCandidate = selectedTemplate!!.typeName
                    deleteDialogOpen = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB91C1C))
            ) {
                Text("מחק תבנית '${selectedTemplate!!.typeName}'")
            }
        }

        Divider()

        // ── תוכן מרכזי ──
        when {
            showEndAccSubPicker -> {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "נא לבחור סוג אביזר קצה מהתפריט למעלה",
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            showEditor -> {
                SmartTemplateEditorSection(
                    typeName = editorTypeName,
                    onTypeNameChange = { editorTypeName = it },
                    descriptionTemplate = editorDescriptionTemplate,
                    onDescriptionTemplateChange = { editorDescriptionTemplate = it },
                    fields = editorFields,
                    formulas = editorFormulas,
                    message = editorMessage,
                    editingOriginal = editorEditingOriginal,
                    existingTemplateNames = templates.map { it.typeName },
                    onSave = { newTemplate ->
                        val existing = loadSmartAccessoryTemplates(prefs)
                            .filter { it.typeName != editorEditingOriginal && it.typeName != newTemplate.typeName }
                        val updated = sortSmartTemplates(existing + newTemplate)
                        saveSmartAccessoryTemplates(prefs, updated)
                        templates = loadSmartAccessoryTemplates(prefs)
                        selectedTemplate = newTemplate
                        dropdownLabel = newTemplate.typeName
                        editorEditingOriginal = newTemplate.typeName
                        editorMessage = "התבנית '${newTemplate.typeName}' נשמרה."
                        showEditor = false
                        resetForm()
                    },
                    onMessageChange = { editorMessage = it },
                    endAccessoryWllTable = endAccessoryWllTable,
                    onSaveEndWllTable = { table -> saveEndAccessoryWllTable(prefs, table) }
                )
            }

            manualEntryMode -> {
                // ── הזנה ידנית ──
                Text(if (manualEntryIsEndAcc) "הזנת אביזר קצה ידנית" else "הזנת אביזר ידנית", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = manualDescription,
                    onValueChange = { manualDescription = it },
                    label = { Text("תיאור האביזר *") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                OutlinedTextField(
                    value = manualManufacturer,
                    onValueChange = { manualManufacturer = it },
                    label = { Text("יצרן") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = manualModel,
                    onValueChange = { manualModel = it },
                    label = { Text("דגם") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = manualWll,
                    onValueChange = { manualWll = it },
                    label = { Text("ע.ע.ב") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = manualTestLoad,
                    onValueChange = { manualTestLoad = it },
                    label = { Text("עומס מבחן") },
                    modifier = Modifier.fillMaxWidth()
                )
                // כמות ומספרי זיהוי
                val qIntM = quantity.toIntOrNull()?.coerceIn(0, 99) ?: 0
                LaunchedEffect(qIntM) {
                    while (serialNumbersList.size < qIntM) serialNumbersList.add("")
                    while (serialNumbersList.size > qIntM && serialNumbersList.isNotEmpty()) serialNumbersList.removeLast()
                }
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it.filter { ch -> ch.isDigit() } },
                    label = { Text("כמות *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (qIntM > 0) {
                    SmartSerialNumbersSection(
                        serialNumbersList = serialNumbersList,
                        scanCalib = scanCalib,
                        scanCalibExpanded = scanCalibExpanded,
                        onScanCalibExpandedChange = { scanCalibExpanded = it },
                        calibTemplateName = "הזנה ידנית",
                        prefs = prefs,
                        onCalibChange = { scanCalib = it },
                        onScanRequest = { idx ->
                            pendingScanSlotIndex = idx
                            serialScanLauncher.launch(null)
                        }
                    )
                }
                if (addRowMessage.isNotBlank()) {
                    Text(addRowMessage, color = Color(0xFFB91C1C))
                }
                Button(
                    onClick = {
                        if (manualDescription.isBlank()) { addRowMessage = "יש להזין תיאור"; return@Button }
                        if (quantity.isBlank()) { addRowMessage = "יש להזין כמות"; return@Button }
                        val row = SmartFormTableRow(
                            rowNumber = tableRows.size + 1,
                            templateName = "הזנה ידנית",
                            description = manualDescription.trim(),
                            manufacturer = manualManufacturer.trim(),
                            model = manualModel.trim(),
                            quantity = quantity,
                            serialNumbers = serialNumbersList.filter { it.isNotBlank() }.joinToString("\n"),
                            testLoad = manualTestLoad.trim(),
                            wll = manualWll.trim()
                        )
                        tableRows.add(row)
                        // שמירת התיאור לזיכרון לשימוש עתידי
                        val desc = manualDescription.trim()
                        if (desc.isNotBlank()) {
                            if (manualEntryIsEndAcc) {
                                val updated = (savedManualEndAccDescriptions + desc).distinct().sorted()
                                savedManualEndAccDescriptions = updated
                                prefs.edit().putStringSet("manual_end_acc_descriptions", updated.toSet()).apply()
                            } else {
                                val updated = (savedManualDescriptions + desc).distinct().sorted()
                                savedManualDescriptions = updated
                                prefs.edit().putStringSet("manual_accessory_descriptions", updated.toSet()).apply()
                            }
                        }
                        resetForm()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
                ) { Text("הוסף אביזר לטבלה") }
            }

            else -> {
                // ── טופס שימוש בתבנית ──
                val tpl = selectedTemplate
                if (tpl != null) {
                    Text("הוספת אביזר — ${tpl.typeName}", fontWeight = FontWeight.Bold)

                    tpl.fields.forEach { field ->
                        when (field.inputType) {
                            SmartFieldInputType.FORMULA, SmartFieldInputType.READ_ONLY -> {
                                val rawComputed = if (isSmartWllTargetName(field.name)) {
                                    resolveWllText(tpl, fieldValues.toMap(), endAccessoryWllTable)
                                        .ifBlank { calculateSmartFormulaResults(tpl, fieldValues.toMap(), "").first[field.name].orEmpty() }
                                } else {
                                    calculateSmartFormulaResults(tpl, fieldValues.toMap(), fieldValues["ע.ע.ב"].orEmpty()).first[field.name].orEmpty()
                                }
                                // עומס מבחן בתבנית עם "האם בדיקה ראשונה": מוצג רק כשמסומן (כמו בתבניות מובנות)
                                val firstInspFieldName = tpl.fields.firstOrNull {
                                    it.name.trimEnd('.', ' ') == "האם בדיקה ראשונה" &&
                                    it.inputType == SmartFieldInputType.BOOLEAN
                                }?.name
                                val firstInspVal = if (firstInspFieldName != null) fieldValues[firstInspFieldName] else firstInspectionValue
                                val computedValue = if (
                                    isSmartTestLoadTargetName(field.name) &&
                                    firstInspFieldName != null &&
                                    firstInspVal != "כן"
                                ) "" else rawComputed
                                // אם ע.ע.ב לא מחושב (מידה לא ידועה / "אחר") — מאפשר הזנה ידנית
                                val canEditManually = computedValue.isBlank() && isSmartWllTargetName(field.name)
                                OutlinedTextField(
                                    value = if (canEditManually) fieldValues[field.name].orEmpty() else computedValue,
                                    onValueChange = if (canEditManually) { { fieldValues[field.name] = it } } else { {} },
                                    readOnly = !canEditManually,
                                    label = {
                                        Text(
                                            if (canEditManually) "${field.name} (הזן ידנית)${if (field.isRequired) " *" else ""}"
                                            else field.name + if (field.isRequired && computedValue.isBlank()) " *" else ""
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = if (computedValue.contains('\n')) 2 else 1
                                )
                            }
                            SmartFieldInputType.LIST -> {
                                SmartListFieldRow(
                                    field = field,
                                    currentValue = fieldValues[field.name].orEmpty(),
                                    isOther = otherListFieldNames.contains(field.name),
                                    onValueChange = { fieldValues[field.name] = it },
                                    onOtherToggle = { isOther ->
                                        if (isOther) otherListFieldNames.add(field.name)
                                        else otherListFieldNames.remove(field.name)
                                    }
                                )
                            }
                            SmartFieldInputType.BOOLEAN -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = fieldValues[field.name] == "כן",
                                        onCheckedChange = { fieldValues[field.name] = if (it) "כן" else "לא" }
                                    )
                                    Text(field.name + if (field.isRequired) " *" else "")
                                }
                            }
                            SmartFieldInputType.DATE -> {
                                SmartDateField(
                                    label = field.name + if (field.isRequired) " *" else "",
                                    value = fieldValues[field.name].orEmpty(),
                                    onValueChange = { fieldValues[field.name] = it }
                                )
                            }
                            SmartFieldInputType.INTEGER -> {
                                OutlinedTextField(
                                    value = fieldValues[field.name].orEmpty(),
                                    onValueChange = { fieldValues[field.name] = it.filter { ch -> ch.isDigit() || ch == '-' } },
                                    label = { Text(field.name + if (field.isRequired) " *" else "") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            SmartFieldInputType.DECIMAL -> {
                                OutlinedTextField(
                                    value = fieldValues[field.name].orEmpty(),
                                    onValueChange = { fieldValues[field.name] = it.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' || ch == ',' } },
                                    label = { Text(field.name + if (field.isRequired) " *" else "") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            SmartFieldInputType.MULTILINE_TEXT -> {
                                OutlinedTextField(
                                    value = fieldValues[field.name].orEmpty(),
                                    onValueChange = { fieldValues[field.name] = it },
                                    label = { Text(field.name + if (field.isRequired) " *" else "") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 3
                                )
                            }
                            else -> {
                                OutlinedTextField(
                                    value = fieldValues[field.name].orEmpty(),
                                    onValueChange = { fieldValues[field.name] = it },
                                    label = { Text(field.name + if (field.isRequired) " *" else "") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // כמות + מספרי זיהוי
                    val qInt = quantity.toIntOrNull()?.coerceIn(0, 99) ?: 0
                    LaunchedEffect(qInt) {
                        while (serialNumbersList.size < qInt) serialNumbersList.add("")
                        while (serialNumbersList.size > qInt && serialNumbersList.isNotEmpty()) serialNumbersList.removeLast()
                    }
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it.filter { ch -> ch.isDigit() } },
                        label = { Text("כמות *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (qInt > 0) {
                        SmartSerialNumbersSection(
                            serialNumbersList = serialNumbersList,
                            scanCalib = scanCalib,
                            scanCalibExpanded = scanCalibExpanded,
                            onScanCalibExpandedChange = { scanCalibExpanded = it },
                            calibTemplateName = tpl.typeName,
                            prefs = prefs,
                            onCalibChange = { scanCalib = it },
                            onScanRequest = { idx ->
                                pendingScanSlotIndex = idx
                                serialScanLauncher.launch(null)
                            }
                        )
                    }

                    if (addRowMessage.isNotBlank()) {
                        Text(addRowMessage, color = Color(0xFFB91C1C))
                    }

                    Button(
                        onClick = {
                            val row = buildRowFromCurrentForm(tpl)
                            if (row != null) {
                                tableRows.add(row)
                                resetForm()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
                    ) { Text("הוסף אביזר לטבלה") }
                } else if (!manualEntryMode) {
                    Text("בחר סוג אביזר מהרשימה למעלה.", color = Color(0xFFB45309))
                }
            }
        }

        // ── טבלת אביזרים ──
        if (tableRows.isNotEmpty()) {
            Divider()
            Text("טבלת אביזרים (${tableRows.size} שורות)", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            tableRows.forEachIndexed { idx, row ->
                SmartTableRowCard(
                    row = row,
                    onDelete = {
                        tableRows.removeAt(idx)
                        tableDefects.removeAll { it.accessoryRowNumber == row.rowNumber }
                        renumberRows()
                    }
                )
            }

            // ── ליקויים לשורות ──
            Divider()
            Text("ליקויים לשורות בטבלה", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("אם נמצא ליקוי באביזר, רשום אותו כאן לפני העברת הטבלה לתסקיר הראשי.", fontSize = 13.sp, color = Color.Gray)

            tableRows.forEach { row ->
                val defectsForRow = tableDefects.filter { it.accessoryRowNumber == row.rowNumber }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.LightGray)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("שורה ${row.rowNumber}: ${row.description.take(60)}${if (row.description.length > 60) "..." else ""}", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    if (defectsForRow.isEmpty()) {
                        Text("לא נוספו ליקויים", fontSize = 12.sp, color = Color.Gray)
                    } else {
                        defectsForRow.forEach { defect ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("• ${defect.description}${if (defect.fixUntil.isNotBlank()) " (תיקון עד: ${defect.fixUntil})" else ""}", modifier = Modifier.weight(1f), fontSize = 12.sp)
                                TextButton(onClick = { tableDefects.remove(defect) }) {
                                    Text("מחק", color = Color(0xFFB91C1C), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            defectDialogRowNumber = row.rowNumber
                            defectDescription = ""
                            defectFixUntil = ""
                            defectSelectedSerial = ""
                            defectRowDialogOpen = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("הוסף ליקוי") }
                }
            }

            Divider()

            Button(
                onClick = { onTransferToReport(tableRows.toList(), tableDefects.toList()) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
            ) { Text("העבר טבלה לתסקיר הראשי", fontSize = 16.sp) }
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("חזרה ללא העברה")
        }
    }

    // ── דיאלוג מחיקת תבנית ──
    if (deleteDialogOpen) {
        AlertDialog(
            onDismissRequest = { deleteDialogOpen = false },
            title = { Text("מחיקת תבנית") },
            text = { Text("האם למחוק את התבנית '$deleteDialogCandidate'?\nלא ניתן לשחזר את התבנית לאחר המחיקה.") },
            confirmButton = {
                TextButton(onClick = {
                    val updated = templates.filter { it.typeName != deleteDialogCandidate }
                    saveSmartAccessoryTemplates(prefs, updated)
                    templates = updated
                    if (selectedTemplate?.typeName == deleteDialogCandidate) {
                        selectedTemplate = templates.firstOrNull()
                        dropdownLabel = selectedTemplate?.typeName ?: "בחר סוג אביזר"
                    }
                    deleteDialogOpen = false
                }) { Text("מחק", color = Color(0xFFB91C1C)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogOpen = false }) { Text("ביטול") }
            }
        )
    }

    // ── דיאלוג הוספת ליקוי ──
    var defectDatePickerOpen by remember { mutableStateOf(false) }
    if (defectRowDialogOpen) {
        // תאריך הבדיקה ממיר לזמן Epoch למינימום בתאריכון
        val inspectionDateMillis = remember(inspectionDate) {
            runCatching {
                val fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
                java.time.LocalDate.parse(inspectionDate, fmt)
                    .atStartOfDay(java.time.ZoneId.of("UTC"))
                    .toInstant().toEpochMilli()
            }.getOrNull()
        }
        AlertDialog(
            onDismissRequest = { defectRowDialogOpen = false },
            title = { Text("הוספת ליקוי לשורה $defectDialogRowNumber") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val currentRow = tableRows.find { it.rowNumber == defectDialogRowNumber }
                    val rowSerials = currentRow?.serialNumbers?.lines()?.filter { it.isNotBlank() } ?: emptyList()
                    if (rowSerials.isNotEmpty()) {
                        Text("שייך ליקוי למספר זיהוי:", fontSize = 13.sp)
                        rowSerials.forEach { serial ->
                            val isSelected = defectSelectedSerial == serial
                            OutlinedButton(
                                onClick = { defectSelectedSerial = if (isSelected) "" else serial },
                                modifier = Modifier.fillMaxWidth(),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) androidx.compose.ui.graphics.Color(0xFFE3F2FD) else androidx.compose.ui.graphics.Color.Transparent
                                )
                            ) {
                                Text(serial, fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal)
                            }
                        }
                    }
                    Button(
                        onClick = {
                            if (defectSelectedSerial.isBlank()) {
                                Toast.makeText(context, "יש לבחור מספר זיהוי לפני הצילום", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (draftRunningNumber.isBlank()) {
                                Toast.makeText(context, "לא זוהה מספר תסקיר — פתח טופס תסקיר תחילה ומשם עבור לטופס החכם", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            val uri = createSmartPhotoUri(context, draftRunningNumber, defectSelectedSerial)
                            pendingPhotoSerial = defectSelectedSerial
                            pendingPhotoUri = uri
                            smartPhotoLauncher.launch(uri)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (defectSelectedSerial.isBlank()) "צלם תמונת ליקוי (בחר מס׳ זיהוי תחילה)"
                            else "צלם תמונת ליקוי למס׳ זיהוי $defectSelectedSerial"
                        )
                    }

                    OutlinedTextField(
                        value = defectDescription,
                        onValueChange = { defectDescription = it },
                        label = { Text("תיאור הליקוי") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    // תאריכון — ברירת מחדל: תאריך הבדיקה; ניתן לבחור מהיום קדימה
                    OutlinedButton(
                        onClick = { defectDatePickerOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (defectFixUntil.isNotBlank()) "תיקון עד: $defectFixUntil" else "בחר תאריך תיקון עד")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (defectDescription.isNotBlank()) {
                        val prefix = if (defectSelectedSerial.isNotBlank()) "באביזר $defectSelectedSerial " else ""
                        tableDefects.add(SmartFormDefect(defectDialogRowNumber, "$prefix${defectDescription.trim()}", defectFixUntil.trim()))
                        defectRowDialogOpen = false
                    }
                }) { Text("הוסף") }
            },
            dismissButton = {
                TextButton(onClick = { defectRowDialogOpen = false }) { Text("ביטול") }
            }
        )
        if (defectDatePickerOpen) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = inspectionDateMillis
                    ?: System.currentTimeMillis(),
                selectableDates = object : androidx.compose.material3.SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                        inspectionDateMillis == null || utcTimeMillis >= inspectionDateMillis
                }
            )
            DatePickerDialog(
                onDismissRequest = { defectDatePickerOpen = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val localDate = java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneId.of("UTC"))
                                .toLocalDate()
                            defectFixUntil = localDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        }
                        defectDatePickerOpen = false
                    }) { Text("אישור") }
                },
                dismissButton = {
                    TextButton(onClick = { defectDatePickerOpen = false }) { Text("ביטול") }
                }
            ) { DatePicker(state = datePickerState) }
        }
    }
}

// ── שורת טבלה ──
@Composable
private fun SmartTableRowCard(row: SmartFormTableRow, onDelete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1B5E20))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("שורה ${row.rowNumber} — ${row.templateName}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            TextButton(onClick = onDelete) { Text("מחק", color = Color(0xFFB91C1C), fontSize = 12.sp) }
        }
        if (row.description.isNotBlank()) Text(
            "תיאור: ${row.description.replace("\"", "\"‎")}",
            fontSize = 12.sp
        )
        if (row.manufacturer.isNotBlank()) Text("יצרן: ${row.manufacturer}${if (row.model.isNotBlank()) " | דגם: ${row.model}" else ""}", fontSize = 12.sp)
        Text("כמות: ${row.quantity}", fontSize = 12.sp)
        if (row.wll.isNotBlank()) Text("ע.ע.ב: ${row.wll}", fontSize = 12.sp)
        if (row.testLoad.isNotBlank()) Text("עומס מבחן: ${row.testLoad}", fontSize = 12.sp)
        if (row.serialNumbers.isNotBlank()) {
            Text("מספרי זיהוי:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            row.serialNumbers.lines().filter { it.isNotBlank() }.forEach {
                Text("  • $it", fontSize = 12.sp)
            }
        }
    }
}

// ── שדה רשימה נפתחת ──
// "ללא" מאוחסן כ-"ללא" (ערך תקין) אך מוצג כ-"— (ריק)".
// "אחר" — תמיד בסוף; ערך שהוזן יישמר לתבנית אחרי הוספת השורה.
@Composable
private fun SmartListFieldRow(
    field: SmartAccessoryFieldDefinition,
    currentValue: String,
    isOther: Boolean,
    onValueChange: (String) -> Unit,
    onOtherToggle: (Boolean) -> Unit
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    val label = field.name + if (field.isRequired) " *" else ""
    // ערך מוצג: "ללא" → "(ריק)", אפשרות עם מקדם מוטמע → טקסט בלבד, ריק → ""
    val cleanCurrentValue = listOptionDisplayText(currentValue)
    val displayValue = when {
        currentValue == "ללא" -> "— (ריק)"
        cleanCurrentValue.isNotBlank() -> cleanCurrentValue
        else -> ""
    }
    val selectedIsAscii = cleanCurrentValue.isNotBlank() && currentValue != "ללא" && cleanCurrentValue.all { it.code < 128 }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box {
            // OutlinedTextField ריק-לקריאה עם label על המסגרת — overlay של clickable פותח dropdown
            OutlinedTextField(
                value = displayValue,
                onValueChange = {},
                label = { Text(label) },
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = when {
                    currentValue == "ללא" -> LocalTextStyle.current.copy(color = Color.Gray, fontStyle = FontStyle.Italic)
                    selectedIsAscii -> TextStyle(textDirection = TextDirection.Ltr)
                    else -> LocalTextStyle.current
                },
                placeholder = { Text("בחר...", color = Color.Gray) }
            )
            Box(modifier = Modifier.matchParentSize().clickable { dropdownExpanded = true })
            DropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                field.options.filter { it != "אחר" }.forEach { opt ->
                    if (opt == "ללא") {
                        DropdownMenuItem(
                            text = { Text("— (ריק)", color = Color.Gray, fontStyle = FontStyle.Italic) },
                            onClick = {
                                onValueChange("ללא")
                                onOtherToggle(false)
                                dropdownExpanded = false
                            }
                        )
                    } else {
                        val optDisplay = listOptionDisplayText(opt)
                        val optCoeff = listOptionCoeff(opt)
                        val optIsAscii = optDisplay.all { it.code < 128 }
                        DropdownMenuItem(
                            text = {
                                if (optCoeff != null) {
                                    // הצג שני חלקים: "שרוול לחיצה" + "(0.8)" בצבע אפור
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text(optDisplay, style = if (optIsAscii) TextStyle(textDirection = TextDirection.Ltr) else TextStyle.Default)
                                        Text(" (${optCoeff})", color = Color.Gray, fontSize = 11.sp)
                                    }
                                } else {
                                    Text(optDisplay, style = if (optIsAscii) TextStyle(textDirection = TextDirection.Ltr) else TextStyle.Default)
                                }
                            },
                            onClick = {
                                onValueChange(optDisplay)   // שמור רק את הטקסט — מקדם נשלף מהתבנית
                                onOtherToggle(false)
                                dropdownExpanded = false
                            }
                        )
                    }
                }
                // "אחר" תמיד אחרון
                DropdownMenuItem(
                    text = { Text("אחר...") },
                    onClick = {
                        onValueChange("")
                        onOtherToggle(true)
                        dropdownExpanded = false
                    }
                )
            }
        }
        if (isOther) {
            OutlinedTextField(
                value = currentValue,
                onValueChange = onValueChange,
                label = { Text("$label (הקלד)") },
                placeholder = { Text("ערך חדש יישמר לרשימה לשימוש הבא") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── בחירת תאריך ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartDateField(label: String, value: String, onValueChange: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
        Text(if (value.isNotBlank()) "$label: $value" else "בחר $label")
    }
    if (showPicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val localDate = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        onValueChange(localDate.format(smartDateFmt))
                    }
                    showPicker = false
                }) { Text("אישור") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("ביטול") } }
        ) { DatePicker(state = state) }
    }
}

// ── מספרי זיהוי + כיול סריקה (רכיב משותף להזנה ידנית וטופס תבנית) ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartSerialNumbersSection(
    serialNumbersList: SnapshotStateList<String>,
    scanCalib: SmartScanCalibration,
    scanCalibExpanded: Boolean,
    onScanCalibExpandedChange: (Boolean) -> Unit,
    calibTemplateName: String,
    prefs: android.content.SharedPreferences,
    onCalibChange: (SmartScanCalibration) -> Unit,
    onScanRequest: (Int) -> Unit
) {
    Text("מספרי זיהוי", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    serialNumbersList.forEachIndexed { idx, sn ->
        val isDuplicate = sn.isNotBlank() &&
            serialNumbersList.indices.any { i -> i != idx && serialNumbersList[i] == sn }
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = sn,
                    onValueChange = { serialNumbersList[idx] = it },
                    label = { Text("מספר זיהוי ${idx + 1}") },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(textDirection = TextDirection.Ltr),
                    singleLine = true,
                    isError = isDuplicate
                )
                OutlinedButton(
                    onClick = { onScanRequest(idx) },
                    modifier = Modifier.defaultMinSize(minWidth = 56.dp, minHeight = 56.dp),
                    contentPadding = PaddingValues(4.dp)
                ) { Text("📷", fontSize = 22.sp) }
            }
            if (isDuplicate) {
                Text("מספר זיהוי כפול באותה שורת תסקיר", color = Color(0xFFB91C1C), fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }

    // כיול סריקה
    Column(
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFCBD5E1)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("כיול סריקת מספר זיהוי", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { onScanCalibExpandedChange(!scanCalibExpanded) }) {
                Text(if (scanCalibExpanded) "סגור ▲" else "הגדרות ▼", fontSize = 12.sp)
            }
        }
        if (scanCalibExpanded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = scanCalib.preferLargeFont,
                    onCheckedChange = { v ->
                        val u = scanCalib.copy(preferLargeFont = v)
                        onCalibChange(u); saveScanCalibration(prefs, calibTemplateName, u)
                    }
                )
                Text("תנאי I: העדף תווים בגופן גדול", fontSize = 13.sp)
            }

            var fmt2Expanded by remember { mutableStateOf(false) }
            val formatOptions = listOf(
                "NUMBERS_ONLY" to "מספרים בלבד",
                "ALPHANUMERIC_EN" to "מספרים + אותיות באנגלית",
                "ALPHANUMERIC" to "מספרים + אותיות + תווים מיוחדים",
                "ANY" to "אחר"
            )
            ExposedDropdownMenuBox(expanded = fmt2Expanded, onExpandedChange = { fmt2Expanded = it }) {
                OutlinedTextField(
                    value = formatOptions.find { it.first == scanCalib.formatType }?.second ?: "מספרים + אותיות + תווים מיוחדים",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("תנאי II: מבנה מספר הזיהוי") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fmt2Expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = fmt2Expanded, onDismissRequest = { fmt2Expanded = false }) {
                    formatOptions.forEach { (type, lbl) ->
                        DropdownMenuItem(text = { Text(lbl) }, onClick = {
                            val u = scanCalib.copy(formatType = type)
                            onCalibChange(u); saveScanCalibration(prefs, calibTemplateName, u)
                            fmt2Expanded = false
                        })
                    }
                }
            }

            var extraExpanded by remember { mutableStateOf(false) }
            val extraOptions = listOf(
                "NONE" to "ללא",
                "MIN_4_CHARS" to "חייב להכיל לפחות 4 תווים",
                "MIN_1_DIGIT" to "חייב להכיל לפחות ספרה אחת",
                "IGNORE_KEYWORDS" to "התעלם ממילים כמו WLL / CE / EN",
                "OTHER" to "אחר"
            )
            ExposedDropdownMenuBox(expanded = extraExpanded, onExpandedChange = { extraExpanded = it }) {
                OutlinedTextField(
                    value = extraOptions.find { it.first == scanCalib.extraCondition }?.second ?: "ללא",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("תנאי נוסף לכיוונון") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = extraExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = extraExpanded, onDismissRequest = { extraExpanded = false }) {
                    extraOptions.forEach { (type, lbl) ->
                        DropdownMenuItem(text = { Text(lbl) }, onClick = {
                            val u = scanCalib.copy(extraCondition = type)
                            onCalibChange(u); saveScanCalibration(prefs, calibTemplateName, u)
                            extraExpanded = false
                        })
                    }
                }
            }
        }
    }
}

// ── עורך תבנית ──
@Composable
private fun SmartTemplateEditorSection(
    typeName: String,
    onTypeNameChange: (String) -> Unit,
    descriptionTemplate: String,
    onDescriptionTemplateChange: (String) -> Unit,
    fields: SnapshotStateList<SmartFieldDraft>,
    formulas: SnapshotStateList<SmartFormulaDraft>,
    message: String,
    editingOriginal: String?,
    existingTemplateNames: List<String>,
    onSave: (SmartAccessoryTemplate) -> Unit,
    onMessageChange: (String) -> Unit,
    endAccessoryWllTable: SnapshotStateList<Pair<String, String>> = mutableStateListOf(),
    onSaveEndWllTable: (List<Pair<String, String>>) -> Unit = {}
) {
    val ctx = LocalContext.current

    // ייבוא תבנית מקובץ JSON
    val importJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        try {
            val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return@rememberLauncherForActivityResult
            val tpl = smartTemplateFromJsonText(text)
            if (tpl != null) {
                onSave(tpl)
                Toast.makeText(ctx, "תבנית '${tpl.typeName}' יובאה ונשמרה ✓", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(ctx, "שגיאה: פורמט JSON לא מזוהה", Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) { Toast.makeText(ctx, "שגיאה בקריאת הקובץ", Toast.LENGTH_SHORT).show() }
    }

    // פונקציית עזר — בניית תבנית ממצב הטיוטה הנוכחי
    fun buildTemplateFromDraft(): SmartAccessoryTemplate {
        val allFields = fields.mapNotNull { draft ->
            val fieldName = draft.name.trim(); if (fieldName.isBlank()) return@mapNotNull null
            val opts = draft.optionsText.lines().map { it.trim() }.filter { it.isNotBlank() }.distinct()
            SmartAccessoryFieldDefinition(fieldName, draft.inputType, opts, draft.isRequired, draft.isMemoryKey,
                draft.inDescription, draft.isSeparateColumn, draft.isForCalculation, draft.isScannable, draft.defaultValue)
        }
        val fieldFormulas = fields.mapNotNull { draft ->
            val fn = draft.name.trim(); val expr = draft.formulaExpr.trim()
            if (fn.isBlank() || expr.isBlank()) return@mapNotNull null
            if (draft.inputType != SmartFieldInputType.READ_ONLY && draft.inputType != SmartFieldInputType.FORMULA) return@mapNotNull null
            SmartFormulaDefinition("חישוב $fn", expr, fn, 2)
        }
        val explicitFormulas = formulas.mapNotNull { draft ->
            val n = draft.name.trim(); val e = draft.expression.trim(); val t = draft.targetFieldName.trim()
            val digits = draft.digitsText.toIntOrNull()?.coerceIn(0, 4) ?: 2
            if (n.isBlank() && e.isBlank() && t.isBlank()) return@mapNotNull null
            val isDigitsOnly = e.isBlank() && isSmartBuiltInCalculatedTarget(t)
            if (!isDigitsOnly && (n.isBlank() || t.isBlank())) return@mapNotNull null
            SmartFormulaDefinition(n.ifBlank { "ספרות $t" }, e, t, digits)
        }
        val allFormulas = (fieldFormulas + explicitFormulas).filter { it.name.isNotBlank() && it.targetFieldName.isNotBlank() }
        return SmartAccessoryTemplate(typeName.trim(), descriptionTemplate.trim(), allFields, allFormulas)
    }

    // ייצוא תבנית נוכחית לקובץ JSON בתיקיית ההורדות
    fun exportCurrentTemplate() {
        val name = typeName.trim()
        if (name.isBlank()) { Toast.makeText(ctx, "יש להזין שם תבנית לפני ייצוא", Toast.LENGTH_SHORT).show(); return }
        val tpl = buildTemplateFromDraft()
        val jsonStr = tpl.toJsonObject().toString(2)
        val safeName = name.replace(Regex("[^א-תa-zA-Z0-9_-]"), "_")
        val fileName = "accessory_template_${safeName}.json"
        Thread {
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            try {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw Exception("לא ניתן ליצור קובץ")
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(jsonStr.toByteArray(Charsets.UTF_8)) }
                values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
                ctx.contentResolver.update(uri, values, null, null)
                handler.post { Toast.makeText(ctx, "נשמר: $fileName", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                handler.post { Toast.makeText(ctx, "שגיאה בייצוא: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    var editorTab by remember { mutableStateOf(0) }
    val editorTabTitles = listOf("הכל", "שדות", "תיאור", "חישובים")

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (editingOriginal != null) "עריכת תבנית: $editingOriginal" else "תבנית חדשה", fontWeight = FontWeight.Bold, fontSize = 16.sp)

        OutlinedTextField(
            value = typeName,
            onValueChange = onTypeNameChange,
            label = { Text("שם סוג האביזר") },
            modifier = Modifier.fillMaxWidth()
        )

        ScrollableTabRow(
            selectedTabIndex = editorTab,
            containerColor = Color(0xFF2E7D32),
            contentColor = Color.White
        ) {
            editorTabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = editorTab == index,
                    onClick = { editorTab = index },
                    text = { Text(title, color = Color.White, fontSize = 13.sp) }
                )
            }
        }

        val showAll = editorTab == 0

        // ── לשונית שדות ──
        if (showAll || editorTab == 1) {
            Text("שדות משתנים", fontWeight = FontWeight.SemiBold)
            fields.forEachIndexed { index, field ->
                Column(
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color.LightGray).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("שדה ${index + 1}", fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = field.name,
                        onValueChange = { fields[index] = field.copy(name = it) },
                        label = { Text("שם השדה") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val types = SmartFieldInputType.values()
                            val nextIndex = (types.indexOf(field.inputType) + 1) % types.size
                            fields[index] = field.copy(inputType = types[nextIndex])
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                    ) { Text("סוג קלט: ${field.inputType.label}") }

                    if (field.inputType == SmartFieldInputType.LIST) {
                        OutlinedTextField(
                            value = field.optionsText,
                            onValueChange = { fields[index] = field.copy(optionsText = it) },
                            label = { Text("אפשרויות — כל אפשרות בשורה") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            textStyle = TextStyle(textDirection = TextDirection.Ltr)
                        )
                    }

                    // שורה 1: שדה חובה + מופיע בתיאור האביזר
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = field.isRequired, onCheckedChange = { fields[index] = field.copy(isRequired = it) })
                        Text("שדה חובה", modifier = Modifier.weight(1f))
                        Checkbox(checked = field.inDescription, onCheckedChange = { fields[index] = field.copy(inDescription = it) })
                        Text("מופיע בתיאור האביזר", modifier = Modifier.weight(1f))
                    }
                    // שורה 2: מפתח זיכרון + עמודה נפרדת בתסקיר
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = field.isMemoryKey, onCheckedChange = { fields[index] = field.copy(isMemoryKey = it) })
                        Text("מפתח זיכרון", modifier = Modifier.weight(1f))
                        Checkbox(checked = field.isSeparateColumn, onCheckedChange = { fields[index] = field.copy(isSeparateColumn = it) })
                        Text("עמודה נפרדת בתסקיר", modifier = Modifier.weight(1f))
                    }
                    // שורה 3: משמש לחישוב + ניתן לסריקה
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = field.isForCalculation, onCheckedChange = { fields[index] = field.copy(isForCalculation = it) })
                        Text("משמש לחישוב", modifier = Modifier.weight(1f))
                        Checkbox(checked = field.isScannable, onCheckedChange = { fields[index] = field.copy(isScannable = it) })
                        Text("ניתן לסריקה", modifier = Modifier.weight(1f))
                    }

                    // ערך ברירת מחדל
                    val optionsList = field.optionsText.lines().map { it.trim() }.filter { it.isNotBlank() }
                    if (field.inputType == SmartFieldInputType.LIST && optionsList.isNotEmpty()) {
                        var defaultDropdown by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { defaultDropdown = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (field.defaultValue.isNotBlank()) "ברירת מחדל: ${field.defaultValue}" else "ברירת מחדל: (ללא)")
                            }
                            DropdownMenu(expanded = defaultDropdown, onDismissRequest = { defaultDropdown = false }) {
                                DropdownMenuItem(text = { Text("(ללא)") }, onClick = { fields[index] = field.copy(defaultValue = ""); defaultDropdown = false })
                                optionsList.filter { it != "אחר" }.forEach { opt ->
                                    DropdownMenuItem(text = { Text(if (opt == "ללא") "— (ריק)" else opt) }, onClick = { fields[index] = field.copy(defaultValue = opt); defaultDropdown = false })
                                }
                            }
                        }
                    }
                    // שדות READ_ONLY ו-FORMULA: ממשק נוסחה ישיר (עוקף חישוב מובנה)
                    if (field.inputType == SmartFieldInputType.READ_ONLY || field.inputType == SmartFieldInputType.FORMULA) {
                        OutlinedTextField(
                            value = field.formulaExpr,
                            onValueChange = { fields[index] = field.copy(formulaExpr = it) },
                            label = { Text("נוסחת חישוב — השתמש ב-{שם שדה}") },
                            placeholder = { Text("{ע.ע.ב} * 2.5", style = TextStyle(textDirection = TextDirection.Ltr)) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(textDirection = TextDirection.Ltr),
                            minLines = 2
                        )
                        Text(
                            if (field.formulaExpr.isBlank()) "ריק = חישוב אוטומטי מובנה (שרשרת/כבל/סגיר...)"
                            else "נוסחה זו תעקוף את החישוב המובנה",
                            fontSize = 11.sp,
                            color = if (field.formulaExpr.isBlank()) Color.Gray else Color(0xFF1B5E20)
                        )
                    } else if (field.inputType !in listOf(SmartFieldInputType.BOOLEAN, SmartFieldInputType.LIST)) {
                        OutlinedTextField(
                            value = field.defaultValue,
                            onValueChange = { fields[index] = field.copy(defaultValue = it) },
                            label = { Text("ערך ברירת מחדל (אופציונלי)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // כפתורי סדר + מחיקה
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (index > 0) {
                            OutlinedButton(
                                onClick = {
                                    val tmp = fields[index - 1]; fields[index - 1] = fields[index]; fields[index] = tmp
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                            ) { Text("↑") }
                        }
                        if (index < fields.size - 1) {
                            OutlinedButton(
                                onClick = {
                                    val tmp = fields[index + 1]; fields[index + 1] = fields[index]; fields[index] = tmp
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                            ) { Text("↓") }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = { fields.removeAt(index) }
                        ) { Text("מחק שדה", color = Color(0xFFB91C1C)) }
                    }
                }
            }
            Button(onClick = { fields.add(SmartFieldDraft()) }, modifier = Modifier.fillMaxWidth()) {
                Text("הוסף שדה")
            }
        }

        // ── לשונית תיאור ──
        if (showAll || editorTab == 2) {
            Text("תבנית תיאור", fontWeight = FontWeight.SemiBold)
            Text("כתוב טקסט קבוע ושלב שמות שדות בתוך { }, לדוגמה: {אורך הרצועה}", fontSize = 12.sp, color = Color.Gray)
            OutlinedTextField(
                value = descriptionTemplate,
                onValueChange = onDescriptionTemplateChange,
                label = { Text("תבנית תיאור האביזר") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )

            // ── טבלת ע.ע.ב לאביזרי קצה / סגיר אומגה ──
            if (typeName == "אביזרי קצה" || typeName == "סגיר אומגה") {
                Divider()
                Spacer(modifier = Modifier.height(4.dp))
                Text("טבלת ע.ע.ב לפי מידה", fontWeight = FontWeight.Bold)
                Text("כאן תגדיר לאיזה מידה מתאים כמה טון. הערך שתכתוב בעמודת 'מידה' חייב לתאם בדיוק לאפשרויות שדה 'מידה' בטופס.", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("מידה", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 13.sp)
                    Text("ע.ע.ב (טון)", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(44.dp))
                }
                endAccessoryWllTable.forEachIndexed { idx, entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = entry.first,
                            onValueChange = { newSize -> endAccessoryWllTable[idx] = newSize to entry.second },
                            label = { Text("מידה") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = TextStyle(textDirection = TextDirection.Ltr)
                        )
                        OutlinedTextField(
                            value = entry.second,
                            onValueChange = { newWll -> endAccessoryWllTable[idx] = entry.first to newWll },
                            label = { Text("טון") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        IconButton(onClick = { endAccessoryWllTable.removeAt(idx) }) {
                            Text("✕", color = Color(0xFFB91C1C), fontSize = 16.sp)
                        }
                    }
                }
                OutlinedButton(
                    onClick = { endAccessoryWllTable.add("" to "") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("+ הוסף מידה") }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // ── לשונית חישובים ──
        if (showAll || editorTab == 3) {
            Text("חישובים (אופציונלי)", fontWeight = FontWeight.SemiBold)
            Text(
                "טיפ: לשליטה בספרות ע.ע.ב — הוסף חישוב עם שדה יעד ע.ע.ב, השאר נוסחה ריקה, הגדר ספרות.",
                fontSize = 11.sp, color = Color.Gray
            )
            formulas.forEachIndexed { index, formula ->
                Column(
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color.LightGray).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("חישוב ${index + 1}", fontWeight = FontWeight.Medium)
                    OutlinedTextField(value = formula.name, onValueChange = { formulas[index] = formula.copy(name = it) }, label = { Text("שם החישוב") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = formula.expression,
                        onValueChange = { formulas[index] = formula.copy(expression = it) },
                        label = { Text("נוסחה") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        textStyle = TextStyle(textDirection = TextDirection.Ltr, textAlign = TextAlign.Start)
                    )
                    OutlinedTextField(value = formula.targetFieldName, onValueChange = { formulas[index] = formula.copy(targetFieldName = it) }, label = { Text("שדה יעד (למשל: ע.ע.ב)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = formula.digitsText,
                        onValueChange = { formulas[index] = formula.copy(digitsText = it.filter { ch -> ch.isDigit() }.take(1)) },
                        label = { Text("ספרות אחרי הנקודה") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { formulas.removeAt(index) }) { Text("מחק חישוב", color = Color(0xFFB91C1C)) }
                }
            }
            Button(onClick = { formulas.add(SmartFormulaDraft()) }, modifier = Modifier.fillMaxWidth()) {
                Text("הוסף חישוב")
            }
        }

        if (message.isNotBlank()) {
            Text(
                message,
                color = if (message.contains("נשמר")) Color(0xFF166534) else Color(0xFFB91C1C),
                fontWeight = FontWeight.Bold
            )
        }

        Button(
            onClick = {
                val name = typeName.trim()
                val allFields = fields.mapNotNull { draft ->
                    val fieldName = draft.name.trim()
                    if (fieldName.isBlank()) return@mapNotNull null
                    val options = draft.optionsText.lines().map { it.trim() }.filter { it.isNotBlank() }.distinct()
                    SmartAccessoryFieldDefinition(
                        fieldName, draft.inputType, options,
                        draft.isRequired, draft.isMemoryKey, draft.inDescription,
                        draft.isSeparateColumn, draft.isForCalculation, draft.isScannable,
                        draft.defaultValue
                    )
                }
                // נוסחאות ישירות של שדות READ_ONLY/FORMULA
                val fieldFormulas = fields.mapNotNull { draft ->
                    val fn = draft.name.trim(); val expr = draft.formulaExpr.trim()
                    if (fn.isBlank() || expr.isBlank()) return@mapNotNull null
                    if (draft.inputType != SmartFieldInputType.READ_ONLY && draft.inputType != SmartFieldInputType.FORMULA) return@mapNotNull null
                    SmartFormulaDefinition("חישוב $fn", expr, fn, 2)
                }
                // נוסחאות מפורשות מחלק ה"חישובים חכמים"
                // נוסחה עם ביטוי ריק ושדה יעד ע.ע.ב מותרת — משמשת להגדרת ספרות בלבד
                val explicitFormulas = formulas.mapNotNull { draft ->
                    val n = draft.name.trim(); val e = draft.expression.trim(); val t = draft.targetFieldName.trim()
                    val digits = draft.digitsText.toIntOrNull()?.coerceIn(0, 4) ?: 2
                    if (n.isBlank() && e.isBlank() && t.isBlank()) return@mapNotNull null
                    val isDigitsOnly = e.isBlank() && isSmartBuiltInCalculatedTarget(t)
                    if (!isDigitsOnly && (n.isBlank() || e.isBlank() || t.isBlank())) return@mapNotNull SmartFormulaDefinition("", "", "", digits)
                    SmartFormulaDefinition(n.ifBlank { "ספרות $t" }, e, t, digits)
                }
                val allFormulas = fieldFormulas + explicitFormulas
                val existingWithoutSelf = existingTemplateNames.filter { it != editingOriginal }
                when {
                    name.isBlank() -> onMessageChange("יש להזין שם סוג אביזר.")
                    existingWithoutSelf.contains(name) -> onMessageChange("שם זה כבר קיים.")
                    allFields.isEmpty() -> onMessageChange("יש להגדיר לפחות שדה אחד.")
                    descriptionTemplate.isBlank() -> onMessageChange("יש להגדיר תבנית תיאור.")
                    allFormulas.any { f ->
                        val isDigitsOnly = f.expression.isBlank() && isSmartBuiltInCalculatedTarget(f.targetFieldName)
                        f.name.isBlank() || f.targetFieldName.isBlank() || (!isDigitsOnly && f.expression.isBlank())
                    } ->
                        onMessageChange("בחישוב חכם יש למלא שם, נוסחה ושדה יעד — או למחוק את החישוב הריק.")
                    else -> {
                        if (name == "אביזרי קצה") onSaveEndWllTable(endAccessoryWllTable.toList())
                        onSave(SmartAccessoryTemplate(name, descriptionTemplate.trim(), allFields, allFormulas))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
        ) { Text("שמור תבנית") }

        // ייצוא/ייבוא תבנית למחשב (כלי PC ב-tools/)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { exportCurrentTemplate() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1B5E20))
            ) { Text("ייצוא JSON למחשב", fontSize = 12.sp) }
            OutlinedButton(
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(android.content.Intent.CATEGORY_OPENABLE)
                        type = "application/json"
                    }
                    importJsonLauncher.launch(intent)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1B5E20))
            ) { Text("ייבוא JSON מהמחשב", fontSize = 12.sp) }
        }
    }
}


private fun createSmartPhotoUri(context: android.content.Context, reportNumber: String, serialNumber: String): android.net.Uri {
    val cleanReport = reportNumber.ifBlank { "smart" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val cleanSerial = serialNumber.ifBlank { "serial" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
    val photosDir = java.io.File(context.getExternalFilesDir("inspection_photos"), cleanReport).apply { mkdirs() }
    val photoFile = java.io.File(photosDir, "${cleanSerial}_${timeStamp}.jpg")
    return androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        photoFile
    )
}

