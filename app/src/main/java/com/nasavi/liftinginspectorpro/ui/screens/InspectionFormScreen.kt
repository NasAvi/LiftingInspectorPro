package com.nasavi.liftinginspectorpro.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.input.KeyboardType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.floor
import com.nasavi.liftinginspectorpro.utils.buildAccessoryRowHtml
import com.nasavi.liftinginspectorpro.utils.buildDefectRowHtml
import com.nasavi.liftinginspectorpro.utils.buildNoteRowHtml
import com.nasavi.liftinginspectorpro.utils.buildReportHtml
import com.nasavi.liftinginspectorpro.utils.generatePdfFromHtml
import com.nasavi.liftinginspectorpro.utils.saveHtmlAsPdfAndOpen
import com.nasavi.liftinginspectorpro.utils.openPdfFile
import com.nasavi.liftinginspectorpro.utils.sharePdfFile
import com.nasavi.liftinginspectorpro.data.ReportStorage
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.time.ZoneOffset
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.nasavi.liftinginspectorpro.data.ReportPhotoStorage
import com.nasavi.liftinginspectorpro.data.InspectorSettingsStorage
import com.nasavi.liftinginspectorpro.data.ClientMemoryItem
import com.nasavi.liftinginspectorpro.data.ClientMemoryStore
import com.nasavi.liftinginspectorpro.data.ManufacturerModelMemoryStore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Spacer


data class ReportAccessoryRow(
    val description: String,
    val manufacturer: String,
    val model: String,
    val quantity: String,
    val serialNumbers: String,
    val testLoad: String,
    val wll: String
)

data class ReportDefectRow(
    val defectDescription: String,
    val fixUntil: String
)

data class ReportNoteRow(
    val text: String
)

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private data class InspectorIdentity(
    val firstName: String,
    val lastName: String,
    val certificateNumber: String
)

private fun parseInspectorIdentity(raw: String): InspectorIdentity {
    val parts = raw.split("|")
    return if (parts.size >= 3) {
        InspectorIdentity(parts[0].trim(), parts[1].trim(), parts[2].trim())
    } else {
        InspectorIdentity("", "", raw.trim())
    }
}

internal enum class SmartFieldInputType(val label: String) {
    TEXT("טקסט חופשי"),
    MULTILINE_TEXT("טקסט רב שורות"),
    INTEGER("מספר שלם"),
    DECIMAL("מספר עשרוני"),
    DATE("תאריך"),
    BOOLEAN("כן / לא"),
    LIST("רשימה נפתחת"),
    TEXT_TEMPLATE("טקסט עם משתנים"),
    FORMULA("חישוב מתמטי"),
    READ_ONLY("תוצאה / לקריאה בלבד")
}

internal data class SmartAccessoryFieldDefinition(
    val name: String,
    val inputType: SmartFieldInputType,
    val options: List<String> = emptyList(),
    val isRequired: Boolean = false,
    val isMemoryKey: Boolean = false,
    val inDescription: Boolean = true,
    val isSeparateColumn: Boolean = false,
    val isForCalculation: Boolean = false,
    val isScannable: Boolean = false,
    val defaultValue: String = ""
)

internal data class SmartFormulaDefinition(
    val name: String,
    val expression: String,
    val targetFieldName: String,
    val digits: Int = 2
)

internal data class SmartAccessoryTemplate(
    val typeName: String,
    val descriptionTemplate: String,
    val fields: List<SmartAccessoryFieldDefinition>,
    val formulas: List<SmartFormulaDefinition> = emptyList()
)

internal data class SmartFieldDraft(
    val name: String = "",
    val inputType: SmartFieldInputType = SmartFieldInputType.TEXT,
    val optionsText: String = "",
    val isRequired: Boolean = false,
    val isMemoryKey: Boolean = false,
    val inDescription: Boolean = true,
    val isSeparateColumn: Boolean = false,
    val isForCalculation: Boolean = false,
    val isScannable: Boolean = false,
    val defaultValue: String = "",
    val formulaExpr: String = ""   // לשדות READ_ONLY/FORMULA: נוסחה מותאמת-משתמש (עוקפת חישוב מובנה)
)

internal data class SmartFormulaDraft(
    val name: String = "",
    val expression: String = "",
    val targetFieldName: String = "",
    val digitsText: String = "2"
)


private data class AccessoryReusableTemplate(
    val name: String,
    val accessoryType: String,
    val values: Map<String, String>
)

private const val ACCESSORY_REUSABLE_TEMPLATE_PREFS_KEY = "accessory_reusable_templates_072"
private const val ACCESSORY_TEMPLATE_PART_SEPARATOR = "§AT§"
private const val ACCESSORY_TEMPLATE_FIELD_SEPARATOR = "§AF§"
private const val ACCESSORY_TEMPLATE_VALUE_SEPARATOR = "§AV§"

private fun AccessoryReusableTemplate.encodeForPrefs(): String {
    val encodedValues = values.entries.joinToString(ACCESSORY_TEMPLATE_FIELD_SEPARATOR) { (key, value) ->
        listOf(smartBase64(key), smartBase64(value)).joinToString(ACCESSORY_TEMPLATE_VALUE_SEPARATOR)
    }
    return listOf(
        smartBase64(name),
        smartBase64(accessoryType),
        smartBase64(encodedValues)
    ).joinToString(ACCESSORY_TEMPLATE_PART_SEPARATOR)
}

private fun decodeAccessoryReusableTemplateFromPrefs(raw: String): AccessoryReusableTemplate? = try {
    val parts = raw.split(ACCESSORY_TEMPLATE_PART_SEPARATOR)
    if (parts.size < 3) null else {
        val name = smartBase64Decode(parts[0]).trim()
        val accessoryType = smartBase64Decode(parts[1]).trim()
        val valuesText = smartBase64Decode(parts[2])
        val values = valuesText
            .split(ACCESSORY_TEMPLATE_FIELD_SEPARATOR)
            .mapNotNull { item ->
                if (item.isBlank()) return@mapNotNull null
                val pair = item.split(ACCESSORY_TEMPLATE_VALUE_SEPARATOR)
                if (pair.size < 2) return@mapNotNull null
                val key = smartBase64Decode(pair[0]).trim()
                val value = smartBase64Decode(pair[1])
                if (key.isBlank()) null else key to value
            }
            .toMap()
        if (name.isBlank() || accessoryType.isBlank() || values.isEmpty()) null
        else AccessoryReusableTemplate(name, accessoryType, values)
    }
} catch (_: Exception) {
    null
}

private fun loadAccessoryReusableTemplates(prefs: android.content.SharedPreferences): List<AccessoryReusableTemplate> =
    prefs.getStringSet(ACCESSORY_REUSABLE_TEMPLATE_PREFS_KEY, emptySet())
        ?.mapNotNull { decodeAccessoryReusableTemplateFromPrefs(it) }
        ?.sortedWith(compareBy<AccessoryReusableTemplate> { it.accessoryType }.thenBy { it.name })
        ?: emptyList()

private fun saveAccessoryReusableTemplates(
    prefs: android.content.SharedPreferences,
    templates: List<AccessoryReusableTemplate>
) {
    prefs.edit()
        .putStringSet(
            ACCESSORY_REUSABLE_TEMPLATE_PREFS_KEY,
            templates.map { it.encodeForPrefs() }.toSet()
        )
        .apply()
}

private fun isUnsafeAccessoryTemplateField(fieldName: String): Boolean {
    val normalized = fieldName.replace(" ", "").replace("_", "").lowercase(Locale.getDefault())
    return listOf(
        "אורך",
        "כמות",
        "מספרזיהוי",
        "מסזיהוי",
        "זיהוי",
        "סידורי",
        "ליקוי",
        "ליקויים",
        "תמונה",
        "תמונות",
        "לקוח",
        "תאריך",
        "מיקום",
        "כתובתבדיקה"
    ).any { normalized.contains(it) }
}

internal const val SMART_TEMPLATE_PREFS_KEY = "smart_accessory_templates_v1"

// סדר תבניות המובנות בדרופדאון — אי אפשר למחוק, רק לערוך.
internal val SMART_BUILT_IN_TEMPLATE_NAMES = listOf(
    "מענבי שרשרת", "מענבי כבל", "רצועות הרמה", "אביזרי קצה", "סגיר אומגה", "התקן להרמת שוחות"
)

// ממיין: תבניות מובנות ראשונות (לפי הסדר הנ"ל), אחר כך תבניות משתמש לפי שם.
internal fun sortSmartTemplates(templates: List<SmartAccessoryTemplate>): List<SmartAccessoryTemplate> {
    val builtInOrder = SMART_BUILT_IN_TEMPLATE_NAMES.withIndex().associate { (idx, name) -> name to idx }
    return templates.sortedWith(compareBy({ builtInOrder[it.typeName] ?: Int.MAX_VALUE }, { it.typeName }))
}
internal const val SMART_PART_SEPARATOR = "§T§"
internal const val SMART_FIELD_SEPARATOR = "§F§"
internal const val SMART_VALUE_SEPARATOR = "§V§"

internal fun smartBase64(value: String): String =
    Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

internal fun smartBase64Decode(value: String): String =
    try { String(Base64.decode(value, Base64.NO_WRAP), Charsets.UTF_8) } catch (_: Exception) { "" }

internal fun SmartAccessoryTemplate.encodeForPrefs(): String {
    val encodedFields = fields.joinToString(SMART_FIELD_SEPARATOR) { field ->
        listOf(
            smartBase64(field.name),
            field.inputType.name,
            smartBase64(field.options.joinToString("\n")),
            field.isRequired.toString(),
            field.isMemoryKey.toString(),
            field.inDescription.toString(),
            field.isSeparateColumn.toString(),
            field.isForCalculation.toString(),
            field.isScannable.toString(),
            smartBase64(field.defaultValue)
        ).joinToString(SMART_VALUE_SEPARATOR)
    }
    val encodedFormulas = formulas.joinToString(SMART_FIELD_SEPARATOR) { formula ->
        listOf(
            smartBase64(formula.name),
            smartBase64(formula.expression),
            smartBase64(formula.targetFieldName),
            formula.digits.toString()
        ).joinToString(SMART_VALUE_SEPARATOR)
    }
    return listOf(
        smartBase64(typeName),
        smartBase64(descriptionTemplate),
        smartBase64(encodedFields),
        smartBase64(encodedFormulas)
    ).joinToString(SMART_PART_SEPARATOR)
}

internal fun decodeSmartTemplateFromPrefs(raw: String): SmartAccessoryTemplate? = try {
    val parts = raw.split(SMART_PART_SEPARATOR)
    if (parts.size < 3) null else {
        val typeName = smartBase64Decode(parts[0]).trim()
        val descriptionTemplate = smartBase64Decode(parts[1])
        val encodedFieldsText = smartBase64Decode(parts[2])
        val fields = encodedFieldsText.split(SMART_FIELD_SEPARATOR).mapNotNull { fieldRaw ->
            val fieldParts = fieldRaw.split(SMART_VALUE_SEPARATOR)
            if (fieldParts.size < 3) return@mapNotNull null
            val name = smartBase64Decode(fieldParts[0]).trim()
            val inputType = SmartFieldInputType.values().firstOrNull { it.name == fieldParts[1] } ?: SmartFieldInputType.TEXT
            val options = smartBase64Decode(fieldParts[2]).lines().map { it.trim() }.filter { it.isNotBlank() }.distinct()
            val isRequired = fieldParts.getOrNull(3)?.toBooleanStrictOrNull() ?: false
            val isMemoryKey = fieldParts.getOrNull(4)?.toBooleanStrictOrNull() ?: false
            val inDescription = fieldParts.getOrNull(5)?.toBooleanStrictOrNull() ?: true
            val isSeparateColumn = fieldParts.getOrNull(6)?.toBooleanStrictOrNull() ?: false
            val isForCalculation = fieldParts.getOrNull(7)?.toBooleanStrictOrNull() ?: false
            val isScannable = fieldParts.getOrNull(8)?.toBooleanStrictOrNull() ?: false
            val defaultValue = smartBase64Decode(fieldParts.getOrNull(9).orEmpty()).trim()
            if (name.isBlank()) null else SmartAccessoryFieldDefinition(name, inputType, options, isRequired, isMemoryKey, inDescription, isSeparateColumn, isForCalculation, isScannable, defaultValue)
        }
        val formulas = if (parts.size >= 4) {
            smartBase64Decode(parts[3]).split(SMART_FIELD_SEPARATOR).mapNotNull { formulaRaw ->
                if (formulaRaw.isBlank()) return@mapNotNull null
                val formulaParts = formulaRaw.split(SMART_VALUE_SEPARATOR)
                if (formulaParts.size < 3) return@mapNotNull null
                val name = smartBase64Decode(formulaParts[0]).trim()
                val expression = smartBase64Decode(formulaParts[1]).trim()
                val target = smartBase64Decode(formulaParts[2]).trim()
                val digits = formulaParts.getOrNull(3)?.toIntOrNull()?.coerceIn(0, 4) ?: 2
                if (name.isBlank() || target.isBlank()) return@mapNotNull null
                if (expression.isBlank() && !isSmartBuiltInCalculatedTarget(target.trim())) return@mapNotNull null
                SmartFormulaDefinition(name, expression, target, digits)
            }
        } else emptyList()
        if (typeName.isBlank()) null else SmartAccessoryTemplate(typeName, descriptionTemplate, fields, formulas)
    }
} catch (_: Exception) { null }

internal fun loadSmartAccessoryTemplates(prefs: android.content.SharedPreferences): List<SmartAccessoryTemplate> =
    prefs.getStringSet(SMART_TEMPLATE_PREFS_KEY, emptySet())
        ?.mapNotNull { decodeSmartTemplateFromPrefs(it) }
        ?.let { sortSmartTemplates(it) }
        ?: emptyList()

internal fun saveSmartAccessoryTemplates(prefs: android.content.SharedPreferences, templates: List<SmartAccessoryTemplate>) {
    prefs.edit().putStringSet(SMART_TEMPLATE_PREFS_KEY, templates.map { it.encodeForPrefs() }.toSet()).apply()
}

internal fun normalizeSmartPlaceholder(value: String): String =
    value.trim().replace(Regex("\\s+"), "")

internal fun smartFieldFormulaAlias(fieldName: String): String {
    val trimmed = fieldName.trim()
    return Regex("^[A-Za-z][A-Za-z0-9_]*").find(trimmed)?.value.orEmpty()
}

internal fun smartFieldMatchesName(field: SmartAccessoryFieldDefinition, requestedName: String): Boolean {
    val normalizedRequested = normalizeSmartPlaceholder(requestedName)
    val alias = smartFieldFormulaAlias(field.name)
    return field.name.trim() == requestedName.trim() ||
        normalizeSmartPlaceholder(field.name) == normalizedRequested ||
        alias.equals(requestedName.trim(), ignoreCase = true) ||
        (alias.isNotBlank() && normalizeSmartPlaceholder(alias).equals(normalizedRequested, ignoreCase = true))
}

internal fun smartFieldByPlaceholder(template: SmartAccessoryTemplate?, requestedName: String): SmartAccessoryFieldDefinition? =
    template?.fields?.firstOrNull { field -> smartFieldMatchesName(field, requestedName) }

private fun smartDisplayFieldToken(field: SmartFieldDraft): String {
    val name = field.name.trim()
    if (name.isBlank()) return ""
    val alias = smartFieldFormulaAlias(name)
    return if (alias.isNotBlank() && alias != name) "{$alias} = {$name}" else "{$name}"
}

internal fun buildSmartDescription(template: SmartAccessoryTemplate?, values: Map<String, String>): String {
    if (template == null) return ""

    val sourceTemplate = template.descriptionTemplate.ifBlank { template.typeName }
    // עדכון 023:
    // אפשר להגדיר שם שדה מלא כמו: Z (מומנט התנגדות cm^3)
    // ובתבנית/נוסחה להשתמש בקיצור {Z}. אם השדה לא מולא, המשתנה נשאר אופציונלי ונמחק מהתיאור.
    return Regex("\\{([^}]+)\\}").replace(sourceTemplate) { match ->
        val placeholderName = match.groupValues.getOrNull(1).orEmpty().trim()
        val field = smartFieldByPlaceholder(template, placeholderName)

        field?.let { f ->
            val v = values[f.name].orEmpty().trim()
            if (v == "ללא") "" else v
        }.orEmpty()
    }
        .replace(Regex("[ \t]+"), " ")
        .replace(Regex("\n[ \t]+"), "\n")
        .replace(" ,", ",")
        .replace(" .", ".")
        .replace(" ,", ",")
        // תיקון BiDi: סמן LTR (U+200E) אחרי סימן אינץ' " כך שיוצג מימין למספר גם בטקסט RTL
        .replace(Regex("([0-9/])\""), "$1\"‎")
        .trim()
}

private fun hasUnfilledSmartPlaceholders(description: String): Boolean =
    Regex("\\{[^}]+\\}").containsMatchIn(description)

private fun smartDescriptionMissingFields(
    template: SmartAccessoryTemplate?,
    values: Map<String, String>
): List<String> {
    if (template == null) return emptyList()

    val sourceTemplate = template.descriptionTemplate.ifBlank { template.typeName }
    val missing = mutableListOf<String>()

    Regex("\\{([^}]+)\\}").findAll(sourceTemplate).forEach { match ->
        val placeholderName = match.groupValues.getOrNull(1).orEmpty().trim()
        val field = smartFieldByPlaceholder(template, placeholderName)
        if (field == null) {
            missing.add("משתנה לא מוכר בתבנית התיאור: {$placeholderName}")
        } else if (values[field.name].orEmpty().trim().isBlank()) {
            missing.add(field.name)
        }
    }

    return missing.distinct()
}

private data class SmartConcreteCalculation(
    val volumeCubicMeter: Double,
    val suggestedWllTon: Double,
    val volumeText: String,
    val suggestedWllText: String
)

internal fun parseSmartDecimalValue(value: String): Double? =
    value.trim()
        .replace(",", ".")
        .replace(Regex("[^0-9.]"), "")
        .takeIf { it.isNotBlank() }
        ?.toDoubleOrNull()

internal fun formatSmartDecimal(value: Double, digits: Int = 2): String {
    val formatted = String.format(Locale.US, "%.${digits}f", value)
    return formatted.trimEnd('0').trimEnd('.')
}

internal fun findSmartFieldByKeywords(
    template: SmartAccessoryTemplate?,
    requiredKeywords: List<String>,
    alternativeKeywords: List<String> = emptyList()
): SmartAccessoryFieldDefinition? {
    if (template == null) return null

    return template.fields.firstOrNull { field ->
        val normalizedName = normalizeSmartPlaceholder(field.name)
        val hasRequired = requiredKeywords.all { normalizedName.contains(normalizeSmartPlaceholder(it)) }
        val hasAlternative = alternativeKeywords.any { normalizedName.contains(normalizeSmartPlaceholder(it)) }
        hasRequired || hasAlternative
    }
}

private fun calculateSmartConcreteVolumeAndWll(
    template: SmartAccessoryTemplate?,
    values: Map<String, String>,
    densityText: String
): SmartConcreteCalculation? {
    val topDiameterField = findSmartFieldByKeywords(
        template,
        requiredKeywords = listOf("קוטר", "עליון"),
        alternativeKeywords = listOf("פתחעליון")
    ) ?: return null

    val bottomDiameterField = findSmartFieldByKeywords(
        template,
        requiredKeywords = listOf("קוטר", "תחתון"),
        alternativeKeywords = listOf("פתחתחתון")
    ) ?: return null

    val heightField = findSmartFieldByKeywords(
        template,
        requiredKeywords = listOf("גובה"),
        alternativeKeywords = listOf("גובההדוד")
    ) ?: return null

    val topDiameterMm = parseSmartDecimalValue(values[topDiameterField.name].orEmpty()) ?: return null
    val bottomDiameterMm = parseSmartDecimalValue(values[bottomDiameterField.name].orEmpty()) ?: return null
    val heightMm = parseSmartDecimalValue(values[heightField.name].orEmpty()) ?: return null
    val concreteDensity = parseSmartDecimalValue(densityText) ?: 2.5

    if (topDiameterMm <= 0.0 || bottomDiameterMm <= 0.0 || heightMm <= 0.0 || concreteDensity <= 0.0) return null

    val topDiameterMeter = topDiameterMm / 1000.0
    val bottomDiameterMeter = bottomDiameterMm / 1000.0
    val heightMeter = heightMm / 1000.0

    // נוסחת חרוט קטום לפי קטרים במטר:
    // V = π × h / 12 × (D² + D×d + d²)
    val volume = Math.PI * heightMeter / 12.0 *
        (topDiameterMeter * topDiameterMeter +
            topDiameterMeter * bottomDiameterMeter +
            bottomDiameterMeter * bottomDiameterMeter)

    val suggestedWll = volume * concreteDensity

    return SmartConcreteCalculation(
        volumeCubicMeter = volume,
        suggestedWllTon = suggestedWll,
        volumeText = formatSmartDecimal(volume, digits = 2),
        suggestedWllText = formatSmartDecimal(suggestedWll, digits = 2)
    )
}

internal fun findSmartVolumeField(template: SmartAccessoryTemplate?): SmartAccessoryFieldDefinition? =
    findSmartFieldByKeywords(
        template,
        requiredKeywords = listOf("נפח"),
        alternativeKeywords = listOf("נפחכללי")
    )

internal data class SmartFormulaResult(
    val formula: SmartFormulaDefinition,
    val valueText: String? = null,
    val error: String? = null
)

internal fun isSmartWllTargetName(value: String): Boolean {
    val normalized = normalizeSmartPlaceholder(value).lowercase(Locale.getDefault())
    return normalized == "ע.ע.ב" ||
        normalized == "עעב" ||
        normalized == "wll" ||
        normalized.contains("עומסעבודהבטוח")
}

internal fun isSmartTestLoadTargetName(value: String): Boolean {
    val normalized = normalizeSmartPlaceholder(value)
    return normalized == "עומס מבחן" || normalized == "מבחן" || normalized == "עומסמבחן"
}

internal fun isSmartBuiltInCalculatedTarget(value: String): Boolean =
    isSmartWllTargetName(value) || isSmartTestLoadTargetName(value)

internal fun smartFormulaValueByName(template: SmartAccessoryTemplate?, values: Map<String, String>, name: String): String? {
    val exact = values[name.trim()]
    if (!exact.isNullOrBlank()) return exact
    val normalizedName = normalizeSmartPlaceholder(name)
    val byFullName = values.entries.firstOrNull { normalizeSmartPlaceholder(it.key) == normalizedName }?.value
    if (!byFullName.isNullOrBlank()) return byFullName
    val field = smartFieldByPlaceholder(template, name)
    return field?.let { values[it.name] }
}

internal data class SmartFormulaEvaluation(val value: Double? = null, val error: String? = null)

private class SmartExpressionParser(private val text: String) {
    private var index = 0

    fun parse(): SmartFormulaEvaluation = try {
        val value = parseExpression()
        skipSpaces()
        if (index < text.length) SmartFormulaEvaluation(error = "תו לא צפוי בנוסחה: ${text[index]}")
        else SmartFormulaEvaluation(value = value)
    } catch (e: Exception) {
        SmartFormulaEvaluation(error = e.message ?: "שגיאה בנוסחה")
    }

    private fun parseExpression(): Double {
        var value = parseTerm()
        while (true) {
            skipSpaces()
            value = when {
                match('+') -> value + parseTerm()
                match('-') -> value - parseTerm()
                else -> return value
            }
        }
    }

    private fun parseTerm(): Double {
        var value = parsePower()
        while (true) {
            skipSpaces()
            value = when {
                match('*') -> value * parsePower()
                match('/') -> {
                    val divisor = parsePower()
                    if (divisor == 0.0) throw IllegalArgumentException("חלוקה באפס")
                    value / divisor
                }
                else -> return value
            }
        }
    }

    private fun parsePower(): Double {
        var value = parseUnary()
        skipSpaces()
        if (match('^')) {
            value = Math.pow(value, parsePower())
        }
        return value
    }

    private fun parseUnary(): Double {
        skipSpaces()
        return when {
            match('+') -> parseUnary()
            match('-') -> -parseUnary()
            else -> parsePrimary()
        }
    }

    private fun parsePrimary(): Double {
        skipSpaces()
        if (match('(')) {
            val value = parseExpression()
            skipSpaces()
            if (!match(')')) throw IllegalArgumentException("חסר סוגר ימני")
            return value
        }
        val start = index
        while (index < text.length && (text[index].isDigit() || text[index] == '.')) index++
        if (start == index) throw IllegalArgumentException("חסר מספר בנוסחה")
        return text.substring(start, index).toDoubleOrNull()
            ?: throw IllegalArgumentException("מספר לא תקין בנוסחה")
    }

    private fun skipSpaces() {
        while (index < text.length && text[index].isWhitespace()) index++
    }

    private fun match(char: Char): Boolean {
        skipSpaces()
        return if (index < text.length && text[index] == char) {
            index++
            true
        } else false
    }
}

internal fun evaluateSmartFormulaExpression(template: SmartAccessoryTemplate?, expression: String, values: Map<String, String>): SmartFormulaEvaluation {
    var missingVariable: String? = null
    val replaced = Regex("\\{([^}]+)\\}").replace(expression) { match ->
        val variableName = match.groupValues.getOrNull(1).orEmpty().trim()
        val rawValue = smartFormulaValueByName(template, values, variableName)
        val number = parseSmartDecimalValue(rawValue.orEmpty())
        if (number == null) {
            missingVariable = variableName
            "0"
        } else {
            String.format(Locale.US, "%.10f", number).trimEnd('0').trimEnd('.')
        }
    }
    missingVariable?.let { return SmartFormulaEvaluation(error = "חסר ערך מספרי עבור {$it}") }

    val normalizedExpression = replaced
        .replace("π", Math.PI.toString())
        .replace(Regex("(?i)\\bpi\\b"), Math.PI.toString())
        .replace("×", "*")
        .replace("÷", "/")

    if (Regex("[^0-9+\\-*/^().\\s]").containsMatchIn(normalizedExpression)) {
        return SmartFormulaEvaluation(error = "הנוסחה כוללת תווים שלא נתמכים")
    }

    return SmartExpressionParser(normalizedExpression).parse()
}

internal fun calculateSmartFormulaResults(
    template: SmartAccessoryTemplate?,
    values: Map<String, String>,
    wllText: String
): Pair<Map<String, String>, List<SmartFormulaResult>> {
    if (template == null || template.formulas.isEmpty()) return emptyMap<String, String>() to emptyList()

    val workingValues = values.toMutableMap()
    if (wllText.isNotBlank()) workingValues["ע.ע.ב"] = wllText

    val calculatedTargets = mutableMapOf<String, String>()
    val results = template.formulas.map { formula ->
        val evaluation = evaluateSmartFormulaExpression(template, formula.expression, workingValues)
        val value = evaluation.value
        if (value == null) {
            SmartFormulaResult(formula = formula, error = evaluation.error ?: "לא ניתן לחשב")
        } else {
            val text = formatSmartDecimal(value, formula.digits)
            // תמיכה בשם שדה עם או בלי סוגריים (למשל {עומס מבחן} → עומס מבחן)
            val targetName = formula.targetFieldName.trim().let {
                if (it.startsWith("{") && it.endsWith("}")) it.removeSurrounding("{", "}") else it
            }
            calculatedTargets[targetName] = text
            workingValues[targetName] = text
            if (isSmartWllTargetName(targetName)) {
                workingValues["ע.ע.ב"] = text
            }
            SmartFormulaResult(formula = formula, valueText = text)
        }
    }

    return calculatedTargets to results
}


private fun smartMissingFieldsMessage(
    template: SmartAccessoryTemplate?,
    values: Map<String, String>,
    quantityText: String,
    serialNumbers: List<String>,
    wllText: String,
    description: String,
    formulaResults: List<SmartFormulaResult>
): String {
    if (template == null) return ""

    val missing = mutableListOf<String>()

    if (description.isBlank()) {
        val missingDescriptionFields = smartDescriptionMissingFields(template, values)
        if (missingDescriptionFields.isNotEmpty()) {
            missing.add("שדות חסרים בתבנית התיאור:")
            missingDescriptionFields.forEach { missing.add("• $it") }
        } else {
            missing.add("תיאור האביזר")
        }
    }

    val formulaErrors = formulaResults.filter { it.error != null }
    formulaErrors.forEach { result ->
        missing.add("${result.formula.name}: ${result.error}")
    }

    if (quantityText.trim().isBlank()) {
        missing.add("כמות")
    }

    val quantity = quantityText.toIntOrNull() ?: 0
    if (quantity > 0) {
        val missingSerialIndexes = serialNumbers.take(quantity).mapIndexedNotNull { index, value ->
            if (value.trim().isBlank()) index + 1 else null
        }
        if (missingSerialIndexes.isNotEmpty()) {
            missing.add("מספרי זיהוי: ${missingSerialIndexes.joinToString(", ")}")
        }
    }

    if (wllText.trim().isBlank()) {
        missing.add("ע.ע.ב")
    }

    return missing.distinct().joinToString("\n")
}



private data class DuplicateSerialInfo(
    val serialNumber: String,
    val firstRowNumber: Int,
    val secondRowNumber: Int
)

private fun splitSerialNumbersForValidation(serialNumbersText: String): List<String> {
    return serialNumbersText
        .split(Regex("[\n,;]+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

private fun duplicateSerialMessage(info: DuplicateSerialInfo): String {
    return if (info.firstRowNumber == info.secondRowNumber) {
        "מספר הזיהוי ${info.serialNumber} כבר מופיע באותה שורה. לא ניתן להזין מספר זיהוי כפול בתסקיר."
    } else {
        "מספר הזיהוי ${info.serialNumber} כבר מופיע בשורה ${info.firstRowNumber}. לא ניתן להזין אותו שוב בשורה ${info.secondRowNumber}."
    }
}

private fun findDuplicateSerialsInRows(rows: List<ReportAccessoryRow>): DuplicateSerialInfo? {
    val seen = linkedMapOf<String, Int>()

    rows.forEachIndexed { rowIndex, row ->
        val rowNumber = rowIndex + 1
        splitSerialNumbersForValidation(row.serialNumbers).forEach { serial ->
            val key = serial.trim().lowercase()
            val existingRowNumber = seen[key]
            if (existingRowNumber != null) {
                return DuplicateSerialInfo(
                    serialNumber = serial,
                    firstRowNumber = existingRowNumber,
                    secondRowNumber = rowNumber
                )
            }
            seen[key] = rowNumber
        }
    }

    return null
}

private fun findDuplicateSerialForNewAccessory(
    existingRows: List<ReportAccessoryRow>,
    newSerialNumbersText: String
): DuplicateSerialInfo? {
    val candidateRows = existingRows + ReportAccessoryRow(
        description = "",
        manufacturer = "",
        model = "",
        quantity = "",
        serialNumbers = newSerialNumbersText,
        testLoad = "",
        wll = ""
    )
    return findDuplicateSerialsInRows(candidateRows)
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionFormScreen(
    initialInspectorNumber: String,
    initialRunningNumber: String,
    editingReport: ReportStorage.WorkingReport? = null,
    onBackToHome: () -> Unit,
    onSaveReport: (com.nasavi.liftinginspectorpro.SavedInspection, Boolean) -> Unit,
    onNavigateToSmartAccessories: ((date: String, rows: List<SmartFormTableRow>, defects: List<SmartFormDefect>, draftRunningNumber: String) -> Unit)? = null,
    incomingSmartRows: List<SmartFormTableRow> = emptyList(),
    incomingSmartDefects: List<SmartFormDefect> = emptyList()
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("lifting_inspection_prefs", Context.MODE_PRIVATE)
    }

    val reportTextSettings = remember { InspectorSettingsStorage.getReportTextSettings(context) }
    val fixedNote = reportTextSettings.defaultNote
    val clientMemoryStore = remember { ClientMemoryStore(context) }
    val manufacturerModelMemoryStore = remember { ManufacturerModelMemoryStore(context) }

    // אם editingReport אינו null — אנחנו פותחים תסקיר קיים לעריכה.
    // אם null — זה תסקיר חדש עם מספר רץ חדש.
    val isEditingExistingReport = editingReport != null
    val screenKey = editingReport?.reportNumber ?: initialRunningNumber

    // קוד מלא 12:
    // לאחר הפקת PDF מאושרת, התסקיר ננעל להוספת אביזרים חדשים.
    // עדיין ניתן לפתוח אותו לעריכה, לשנות פרטים כלליים/ליקויים/הערות ולהפיק גרסאות PDF נוספות.
    var isLockedForNewAccessories by remember(screenKey) {
        mutableStateOf(editingReport?.isLockedForNewAccessories ?: false)
    }

    // חלון אישור לפני הפקת PDF ונעילת הוספת אביזרים.
    var confirmPdfDialogOpen by remember { mutableStateOf(false) }
    var pdfGenerationInProgress by remember { mutableStateOf(false) }
    var pdfPrintLoading by remember { mutableStateOf(false) }
    var pendingReadyPdfFile by remember { mutableStateOf<java.io.File?>(null) }
    var compactPdf by remember { mutableStateOf(false) }

    var showFullReport by remember { mutableStateOf(false) }

    val initialInspectorIdentity = remember(screenKey, initialInspectorNumber) { parseInspectorIdentity(initialInspectorNumber) }

    var inspectorFirstName by remember(screenKey) { mutableStateOf(editingReport?.inspectorFirstName ?: initialInspectorIdentity.firstName) }
    var inspectorLastName by remember(screenKey) { mutableStateOf(editingReport?.inspectorLastName ?: initialInspectorIdentity.lastName) }
    var inspectorNumber by remember(screenKey) {
        mutableStateOf(
            editingReport?.inspectorCertificateNumber
                ?: editingReport?.inspectorNumber
                ?: initialInspectorIdentity.certificateNumber
        )
    }
    var runningNumber by remember(screenKey) { mutableStateOf(editingReport?.reportNumber ?: initialRunningNumber) }
    var inspectionDate by remember(screenKey) { mutableStateOf(editingReport?.inspectionDate ?: "") }
    var nextInspectionDate by remember(screenKey) { mutableStateOf(editingReport?.nextInspectionDate ?: "") }
    // בבודק פנים מפעלי הלקוח הוא תמיד החברה עצמה — ממלאים אוטומטית מפרטי החברה בהגדרות בתסקיר חדש
    val companyDetailsForFill = remember { InspectorSettingsStorage.getCompanyDetails(context) }
    var owner by remember(screenKey) { mutableStateOf(editingReport?.owner ?: companyDetailsForFill.companyName) }
    var address by remember(screenKey) { mutableStateOf(editingReport?.address ?: companyDetailsForFill.address) }
    var phone by remember(screenKey) { mutableStateOf(editingReport?.phone ?: companyDetailsForFill.phone) }
    var contactPerson by remember(screenKey) { mutableStateOf(editingReport?.contactPerson ?: "") }
    var saveClientToMemory by remember(screenKey) { mutableStateOf(true) }
    var saveClientAddressToMemory by remember(screenKey) { mutableStateOf(true) }
    var saveClientPhoneToMemory by remember(screenKey) { mutableStateOf(true) }
    var saveClientContactToMemory by remember(screenKey) { mutableStateOf(true) }
    var vehicle by remember(screenKey) { mutableStateOf(editingReport?.vehicle ?: "") }
    var inspectionLocation by remember(screenKey) { mutableStateOf(editingReport?.inspectionLocation ?: "") }
    var inspectionPlaceType by remember(screenKey) { mutableStateOf(editingReport?.inspectionPlaceType ?: "") }

    val inspectionPlaceOptions = listOf("באתר בניה", "במפעל", "במחסן לוגיסטי")
    var inspectionPlaceDialogOpen by remember { mutableStateOf(false) }

    val availableSites = remember { InspectorSettingsStorage.getSites(context) }
    var selectedSiteId by remember(screenKey) { mutableStateOf(editingReport?.site ?: "") }

    var inspectionDateDialogOpen by remember { mutableStateOf(false) }
    var defectFixUntilDialogOpen by remember { mutableStateOf(false) }

    var hasGeneralNote by remember(screenKey) { mutableStateOf(!editingReport?.generalNote.isNullOrBlank()) }
    var generalNoteText by remember(screenKey) { mutableStateOf(editingReport?.generalNote ?: "") }

    val baseAccessoryTypes = listOf(
        "מענב שרשרת",
        "רצועות הרמה",
        "מענב כבל פלדה",
        "אביזרי קצה",
        "קורת הרמה",
        "ונטוזה",
        "מאזניים למלגזה"
    )

    var customAccessoryTypes by remember {
        mutableStateOf(
            prefs.getStringSet("custom_accessory_types", emptySet())
                ?.toList()
                ?.sorted()
                ?: emptyList()
        )
    }

    var smartAccessoryTemplates by remember { mutableStateOf(loadSmartAccessoryTemplates(prefs)) }
    var reusableAccessoryTemplates by remember { mutableStateOf(loadAccessoryReusableTemplates(prefs)) }
    var saveAccessoryTemplateDialogOpen by remember { mutableStateOf(false) }
    var loadAccessoryTemplateDialogOpen by remember { mutableStateOf(false) }
    var editingAccessoryTemplateOriginal by remember { mutableStateOf<AccessoryReusableTemplate?>(null) }
    var deleteAccessoryTemplateCandidate by remember { mutableStateOf<AccessoryReusableTemplate?>(null) }
    var accessoryTemplateNameDraft by remember { mutableStateOf("") }
    var accessoryTemplateMessage by remember { mutableStateOf("") }

    val accessoryTypes =
        (baseAccessoryTypes + customAccessoryTypes + smartAccessoryTemplates.map { it.typeName })
            .distinct()
            .filter { it != "אחר" } + listOf("אחר")

    var accessoryDialogOpen by remember { mutableStateOf(false) }
    var addAccessoryDialogOpen by remember { mutableStateOf(false) }
    var selectedAccessoryType by remember { mutableStateOf("") }
    var showAccessoryTemplateTools by remember(selectedAccessoryType) { mutableStateOf(false) }
    var addAccessoryValidationMessage by remember { mutableStateOf("") }
    var showCapacityWarning by remember { mutableStateOf(false) }

    var smartBuilderTypeName by remember { mutableStateOf("") }
    var smartBuilderDescriptionTemplate by remember { mutableStateOf("") }
    val smartBuilderFields = remember { mutableStateListOf(SmartFieldDraft()) }
    val smartBuilderFormulas = remember { mutableStateListOf(SmartFormulaDraft()) }
    var smartBuilderMessage by remember { mutableStateOf("") }
    var smartBuilderEditingOriginalTypeName by remember { mutableStateOf<String?>(null) }

    var smartSelectedListFieldName by remember { mutableStateOf<String?>(null) }
    var smartSelectedDateFieldName by remember { mutableStateOf<String?>(null) }
    val smartOtherListFieldNames = remember { mutableStateListOf<String>() }
    val smartFieldValues = remember { mutableStateMapOf<String, String>() }
    var smartManufacturer by remember { mutableStateOf("") }
    var smartModel by remember { mutableStateOf("") }
    var smartQuantity by remember { mutableStateOf("") }
    var smartTestLoad by remember { mutableStateOf("") }
    var smartWll by remember { mutableStateOf("") }
    var smartConcreteDensity by remember { mutableStateOf(prefs.getString("smart_concrete_density", "2.5") ?: "2.5") }
    val smartSerialNumbers = remember { mutableStateListOf<String>() }

    val selectedSmartTemplate = smartAccessoryTemplates.firstOrNull { it.typeName == selectedAccessoryType }
    val smartQuantityInt = smartQuantity.toIntOrNull()?.coerceAtLeast(0) ?: 0
    while (smartSerialNumbers.size < smartQuantityInt) { smartSerialNumbers.add("") }
    while (smartSerialNumbers.size > smartQuantityInt) { smartSerialNumbers.removeAt(smartSerialNumbers.lastIndex) }
    val smartDescription = buildSmartDescription(selectedSmartTemplate, smartFieldValues)
    val smartConcreteCalculation = calculateSmartConcreteVolumeAndWll(selectedSmartTemplate, smartFieldValues, smartConcreteDensity)
    val smartFormulaCalculation = calculateSmartFormulaResults(selectedSmartTemplate, smartFieldValues, smartWll)
    val smartFormulaResults = smartFormulaCalculation.second
    val smartSerialNumbersText = smartSerialNumbers.map { it.trim() }.filter { it.isNotBlank() }.joinToString("\n")

    // -----------------------------
    // מענב שרשרת
    // -----------------------------
    val chainBranchesOptions = listOf("חד", "דו", "תלת", "ארבע")
    val chainSizeOptions = listOf("8/8", "10/8", "13/8", "16/8", "20/8")
    val baseChainEndOptions = listOf(
        "אונקלי נעילה עצמית",
        "אונקלי קיצור שרשרת",
        "אחר"
    )

    var customChainEndOptions by remember {
        mutableStateOf(
            prefs.getStringSet("custom_chain_end_options", emptySet())
                ?.toList()
                ?.sorted()
                ?: emptyList()
        )
    }

    val chainEndOptions =
        (baseChainEndOptions.filter { it != "אחר" } + customChainEndOptions)
            .distinct() + listOf("אחר")

    val chainEnd2Options = listOf("ללא") + chainEndOptions

    // יצרני מענבי שרשרת — רשימה קבועה + ערכים שהמשתמש הוסיף דרך "אחר".
    // כך שדה היצרן אחיד כמו בשאר האביזרים וחוסך הקלדה חוזרת.
    val baseChainManufacturerOptions = listOf("Gunnebo", "Crosby", "Yoke", "אחר")

    var customChainManufacturers by remember {
        mutableStateOf(
            prefs.getStringSet("custom_chain_manufacturers", emptySet())
                ?.toList()
                ?.sorted()
                ?: emptyList()
        )
    }

    val chainManufacturerOptions =
        (baseChainManufacturerOptions.filter { it != "אחר" } + customChainManufacturers)
            .distinct()
            .sorted() + listOf("אחר")

    var chainBranches by remember { mutableStateOf("") }
    var chainSize by remember { mutableStateOf("") }
    var chainLength by remember { mutableStateOf("") }
    var chainEnd1 by remember { mutableStateOf("") }
    var chainEnd1Other by remember { mutableStateOf("") }
    var chainEnd2 by remember { mutableStateOf("") }
    var chainEnd2Other by remember { mutableStateOf("") }
    var chainManufacturerChoice by remember { mutableStateOf("") }
    var chainManufacturerOther by remember { mutableStateOf("") }
    var chainModel by remember { mutableStateOf("") }
    var chainQuantity by remember { mutableStateOf("") }
    var chainTestLoad by remember { mutableStateOf("") }

    val chainSerialNumbers = remember { mutableStateListOf<String>() }

    var chainBranchesDialogOpen by remember { mutableStateOf(false) }
    var chainSizeDialogOpen by remember { mutableStateOf(false) }
    var chainEnd1DialogOpen by remember { mutableStateOf(false) }
    var chainEnd2DialogOpen by remember { mutableStateOf(false) }
    var chainManufacturerDialogOpen by remember { mutableStateOf(false) }

    val chainQuantityInt = chainQuantity.toIntOrNull()?.coerceAtLeast(0) ?: 0
    while (chainSerialNumbers.size < chainQuantityInt) {
        chainSerialNumbers.add("")
    }
    while (chainSerialNumbers.size > chainQuantityInt) {
        chainSerialNumbers.removeAt(chainSerialNumbers.lastIndex)
    }

    val chainWll = calculateChainWll(
        branches = chainBranches,
        sizeText = chainSize
    )

    // יצרן סופי למענב שרשרת:
    // אם המשתמש בחר "אחר" — לוקחים את הערך שהוקלד.
    // אחרת לוקחים את היצרן שנבחר מהרשימה הנפתחת.
    val chainManufacturerFinal =
        if (chainManufacturerChoice == "אחר") chainManufacturerOther.trim() else chainManufacturerChoice.trim()

    val chainEnd1Final =
        if (chainEnd1 == "אחר") chainEnd1Other.trim() else chainEnd1.trim()

    val chainEnd2Final =
        when (chainEnd2) {
            "אחר" -> chainEnd2Other.trim()
            "ללא" -> ""
            else -> chainEnd2.trim()
        }

    val chainDescription =
        if (
            selectedAccessoryType == "מענב שרשרת" &&
            chainBranches.isNotBlank() &&
            chainSize.isNotBlank() &&
            chainLength.isNotBlank() &&
            chainEnd1Final.isNotBlank()
        ) {
            val end2Text = if (chainEnd2Final.isNotBlank()) " ו-$chainEnd2Final" else ""
            "מענב שרשרת ${chainBranches} ענפי $chainSize באורך $chainLength מטר, עם $chainEnd1Final$end2Text"
        } else {
            ""
        }

    val chainSerialNumbersText =
        chainSerialNumbers
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")


    // -----------------------------
    // מענב כבל פלדה
    // -----------------------------
    val wireBranchesOptions = listOf("חד", "דו", "תלת", "ארבע")
    val wireEndTypeOptions = listOf("לחוצים", "יצוקים", "מהדקים", "אחר")

    val baseWireExtraEndOptions = listOf(
        "אחר"
    )

    var customWireExtraEnds by remember {
        mutableStateOf(
            prefs.getStringSet("custom_wire_extra_ends", emptySet())
                ?.toList()
                ?.sorted()
                ?: emptyList()
        )
    }

    val wireExtraEndOptions =
        (baseWireExtraEndOptions.filter { it != "אחר" } + customWireExtraEnds)
            .distinct()
            .sorted() + listOf("אחר")

    // יצרני מענבי כבל פלדה — רשימה קבועה + ערכים שהמשתמש הוסיף דרך "אחר".
    val baseWireManufacturerOptions = listOf("Crosby", "Gunnebo", "Yoke", "אחר")

    var customWireManufacturers by remember {
        mutableStateOf(
            prefs.getStringSet("custom_wire_manufacturers", emptySet())
                ?.toList()
                ?.sorted()
                ?: emptyList()
        )
    }

    val wireManufacturerOptions =
        (baseWireManufacturerOptions.filter { it != "אחר" } + customWireManufacturers)
            .distinct()
            .sorted() + listOf("אחר")

    var wireBranches by remember { mutableStateOf("") }
    var wireDiameterMm by remember { mutableStateOf("") }
    var wireLength by remember { mutableStateOf("") }
    var wireEndType by remember { mutableStateOf("") }
    var wireHasExtraEnd by remember { mutableStateOf(false) }
    var wireExtraEndChoice by remember { mutableStateOf("") }
    var wireExtraEndOther by remember { mutableStateOf("") }
    var wireManufacturerChoice by remember { mutableStateOf("") }
    var wireManufacturerOther by remember { mutableStateOf("") }
    var wireModel by remember { mutableStateOf("") }
    var wireQuantity by remember { mutableStateOf("") }
    var wireTestLoad by remember { mutableStateOf("") }

    val wireSerialNumbers = remember { mutableStateListOf<String>() }

    var wireBranchesDialogOpen by remember { mutableStateOf(false) }
    var wireEndTypeDialogOpen by remember { mutableStateOf(false) }
    var wireExtraEndDialogOpen by remember { mutableStateOf(false) }
    var wireManufacturerDialogOpen by remember { mutableStateOf(false) }

    val wireQuantityInt = wireQuantity.toIntOrNull()?.coerceAtLeast(0) ?: 0
    while (wireSerialNumbers.size < wireQuantityInt) {
        wireSerialNumbers.add("")
    }
    while (wireSerialNumbers.size > wireQuantityInt) {
        wireSerialNumbers.removeAt(wireSerialNumbers.lastIndex)
    }

    val wireExtraEndFinal =
        when {
            !wireHasExtraEnd -> ""
            wireExtraEndChoice == "אחר" -> wireExtraEndOther.trim()
            else -> wireExtraEndChoice.trim()
        }

    val wireManufacturerFinal =
        if (wireManufacturerChoice == "אחר") wireManufacturerOther.trim() else wireManufacturerChoice.trim()

    val wireWll = calculateWireRopeSlingWll(
        branches = wireBranches,
        diameterText = wireDiameterMm,
        endType = wireEndType
    )

    val wireDescription =
        if (
            selectedAccessoryType == "מענב כבל פלדה" &&
            wireBranches.isNotBlank() &&
            wireDiameterMm.isNotBlank() &&
            wireLength.isNotBlank() &&
            wireEndType.isNotBlank()
        ) {
            val extraEndText = if (wireExtraEndFinal.isNotBlank()) ", עם $wireExtraEndFinal" else ""
            "מענב כבל פלדה ${wireBranches} ענפי בקוטר $wireDiameterMm מ\"מ באורך $wireLength מטר, קצוות $wireEndType$extraEndText"
        } else {
            ""
        }

    val wireSerialNumbersText =
        wireSerialNumbers
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")

    // -----------------------------
    // אביזרי קצה עצמאיים
    // -----------------------------
    val baseEndAccessoryOptions = listOf(
        "סגיר אומגה",
        "אונקל פתוח עם עין 5.4 טון",
        "התקן להרמת שוחות 6-10 טון",
        "התקן להרמת שוחות 12-20 טון",
        "אוזן חזיר",
        "אחר"
    )

    var customEndAccessoryOptions by remember {
        mutableStateOf(
            prefs.getStringSet("custom_end_accessory_options", emptySet())
                ?.toList()
                ?.sorted()
                ?: emptyList()
        )
    }

    val endAccessoryOptions =
        (baseEndAccessoryOptions.filter { it != "אחר" } + customEndAccessoryOptions)
            .distinct() + listOf("אחר")

    var endAccessoryChoice by remember { mutableStateOf("") }
    var endAccessoryOther by remember { mutableStateOf("") }
    var endAccessoryDialogOpen by remember { mutableStateOf(false) }

    val omegaSizeOptions = listOf(
        "3/16",
        "1/4",
        "5/16",
        "3/8",
        "7/16",
        "1/2",
        "5/8",
        "3/4",
        "7/8",
        "1",
        "1-1/8",
        "1-1/4",
        "1-3/8",
        "1-1/2",
        "1-3/4",
        "2",
        "2-1/2"
    )

    val baseOmegaManufacturerOptions = listOf("Crosby", "NPL", "Yoke", "אחר")

    var customOmegaManufacturers by remember {
        mutableStateOf(
            prefs.getStringSet("custom_omega_manufacturers", emptySet())
                ?.toList()
                ?.sorted()
                ?: emptyList()
        )
    }

    val omegaManufacturerOptions =
        (baseOmegaManufacturerOptions.filter { it != "אחר" } + customOmegaManufacturers)
            .distinct()
            .sorted() + listOf("אחר")

    var omegaSize by remember { mutableStateOf("") }
    var omegaManufacturerChoice by remember { mutableStateOf("") }
    var omegaManufacturerOther by remember { mutableStateOf("") }
    var omegaModel by remember { mutableStateOf("") }
    var omegaQuantity by remember { mutableStateOf("") }
    var omegaTestLoad by remember { mutableStateOf("") }

    val omegaSerialNumbers = remember { mutableStateListOf<String>() }

    var omegaSizeDialogOpen by remember { mutableStateOf(false) }
    var omegaManufacturerDialogOpen by remember { mutableStateOf(false) }

    val omegaQuantityInt = omegaQuantity.toIntOrNull()?.coerceAtLeast(0) ?: 0
    while (omegaSerialNumbers.size < omegaQuantityInt) {
        omegaSerialNumbers.add("")
    }
    while (omegaSerialNumbers.size > omegaQuantityInt) {
        omegaSerialNumbers.removeAt(omegaSerialNumbers.lastIndex)
    }

    val omegaManufacturerFinal =
        when (omegaManufacturerChoice) {
            "אחר" -> omegaManufacturerOther
            else -> omegaManufacturerChoice
        }

    val omegaWll = if (endAccessoryChoice == "סגיר אומגה") calculateOmegaWll(omegaSize) else ""

    val endAccessoryFinal =
        if (endAccessoryChoice == "אחר") endAccessoryOther.trim() else endAccessoryChoice.trim()

    val endAccessoryDescription =
        if (
            selectedAccessoryType == "אביזרי קצה" &&
            endAccessoryFinal.isNotBlank() &&
            (endAccessoryChoice != "סגיר אומגה" || omegaSize.isNotBlank())
        ) {
            if (endAccessoryChoice == "סגיר אומגה") {
                "סגיר ${omegaSize}\""
            } else {
                endAccessoryFinal
            }
        } else {
            ""
        }

    val omegaSerialNumbersText =
        omegaSerialNumbers
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")

    // -----------------------------
    // רצועות הרמה
    // -----------------------------
    val textileTypeOptions = listOf("שטוחה", "אינסופית")
    val textileColorOptions = listOf("סגולה", "ירוקה", "צהובה", "אפורה", "אדומה", "חומה", "כחולה", "כתומה")

    // יצרני רצועות הרמה — רשימה קבועה לפי ההגדרה שלך + ערכים שהמשתמש הוסיף דרך "אחר".
    val baseTextileManufacturerOptions = listOf("NPL", "Zeta", "ROHER TOOLS", "אחר")

    var customTextileManufacturers by remember {
        mutableStateOf(
            prefs.getStringSet("custom_textile_manufacturers", emptySet())
                ?.toList()
                ?.sorted()
                ?: emptyList()
        )
    }

    val textileManufacturerOptions =
        (baseTextileManufacturerOptions.filter { it != "אחר" } + customTextileManufacturers)
            .distinct()
            .sorted() + listOf("אחר")

    var textileKind by remember { mutableStateOf("") }
    var textileColorInput by remember { mutableStateOf("") }
    var textileLength by remember { mutableStateOf("") }
    var textileManufacturerChoice by remember { mutableStateOf("") }
    var textileManufacturerOther by remember { mutableStateOf("") }
    var textileModel by remember { mutableStateOf("") }
    var textileQuantity by remember { mutableStateOf("") }
    var textileTestLoad by remember { mutableStateOf("") }

    val textileSerialNumbers = remember { mutableStateListOf<String>() }

    var textileKindDialogOpen by remember { mutableStateOf(false) }
    var textileColorDialogOpen by remember { mutableStateOf(false) }
    var textileManufacturerDialogOpen by remember { mutableStateOf(false) }

    val textileQuantityInt = textileQuantity.toIntOrNull()?.coerceAtLeast(0) ?: 0
    while (textileSerialNumbers.size < textileQuantityInt) {
        textileSerialNumbers.add("")
    }
    while (textileSerialNumbers.size > textileQuantityInt) {
        textileSerialNumbers.removeAt(textileSerialNumbers.lastIndex)
    }

    val normalizedTextileColor = normalizeTextileColor(textileColorInput)
    val textileWll = calculateTextileWll(normalizedTextileColor)

    val textileManufacturerFinal =
        when (textileManufacturerChoice) {
            "אחר" -> textileManufacturerOther.trim()
            else -> textileManufacturerChoice.trim()
        }

    val textileDescription =
        if (
            selectedAccessoryType == "רצועות הרמה" &&
            textileKind.isNotBlank() &&
            normalizedTextileColor.isNotBlank() &&
            textileLength.isNotBlank()
        ) {
            "רצועת הרמה $textileKind $normalizedTextileColor באורך $textileLength מטר"
        } else {
            ""
        }

    val textileSerialNumbersText =
        textileSerialNumbers
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")

    // -----------------------------
    // ליקויים והערות
    // -----------------------------
    var hasDefect by remember { mutableStateOf(false) }
    var defectDescription by remember { mutableStateOf("") }
    var defectFixUntil by remember { mutableStateOf("") }

    var hasSerialNote by remember { mutableStateOf(false) }
    var serialNoteText by remember { mutableStateOf("") }
    var selectedSerialForNote by remember { mutableStateOf("") }
    var serialNoteDialogOpen by remember { mutableStateOf(false) }

    var selectedSerialForDefect by remember { mutableStateOf("") }
    var defectSerialDialogOpen by remember { mutableStateOf(false) }

    val availableSerialsForNote =
        when (selectedAccessoryType) {
            "מענב שרשרת" -> chainSerialNumbers.map { it.trim() }.filter { it.isNotBlank() }
            "מענב כבל פלדה" -> wireSerialNumbers.map { it.trim() }.filter { it.isNotBlank() }
            "רצועות הרמה" -> textileSerialNumbers.map { it.trim() }.filter { it.isNotBlank() }
            "אביזרי קצה" -> omegaSerialNumbers.map { it.trim() }.filter { it.isNotBlank() }
            else -> if (selectedSmartTemplate != null) {
                smartSerialNumbers.map { it.trim() }.filter { it.isNotBlank() }
            } else {
                emptyList()
            }
        }

    if (selectedSerialForNote.isNotBlank() && !availableSerialsForNote.contains(selectedSerialForNote)) {
        selectedSerialForNote = ""
    }
    if (selectedSerialForDefect.isNotBlank() && !availableSerialsForNote.contains(selectedSerialForDefect)) {
        selectedSerialForDefect = ""
    }


    fun applyAccessoryManufacturerModelMemory(category: String, manufacturer: String, model: String): Boolean {
        // עדכון 065:
        // באביזרי הרמה בוטל זיכרון יצרן + דגם.
        // באביזרים יצרן/דגם אינם מזהים בצורה אמינה את מידה, אורך, דרגה ואביזרי קצה,
        // ולכן טעינה אוטומטית עלולה להכניס נתונים שגויים לתסקיר.
        return false
    }

    fun saveAccessoryManufacturerModelMemory(category: String, manufacturer: String, model: String) {
        // עדכון 065:
        // לא שומרים ולא טוענים זיכרון יצרן + דגם באביזרי הרמה.
        // זיכרון לקוחות נשאר פעיל כרגיל.
        return
    }


    fun collectCurrentAccessoryReusableTemplate(templateName: String): AccessoryReusableTemplate? {
        val cleanName = templateName.trim()
        if (cleanName.isBlank() || selectedAccessoryType.isBlank()) return null

        val values = mutableMapOf<String, String>()
        fun putIfNotBlank(key: String, value: String) {
            val cleanValue = value.trim()
            if (cleanValue.isNotBlank()) values[key] = cleanValue
        }

        when (selectedAccessoryType) {
            "מענב שרשרת" -> {
                putIfNotBlank("chainBranches", chainBranches)
                putIfNotBlank("chainSize", chainSize)
                putIfNotBlank("chainEnd1", chainEnd1)
                putIfNotBlank("chainEnd1Other", chainEnd1Other)
                putIfNotBlank("chainEnd2", chainEnd2)
                putIfNotBlank("chainEnd2Other", chainEnd2Other)
            }
            "מענב כבל פלדה" -> {
                putIfNotBlank("wireBranches", wireBranches)
                putIfNotBlank("wireDiameterMm", wireDiameterMm)
                putIfNotBlank("wireEndType", wireEndType)
                values["wireHasExtraEnd"] = wireHasExtraEnd.toString()
                putIfNotBlank("wireExtraEndChoice", wireExtraEndChoice)
                putIfNotBlank("wireExtraEndOther", wireExtraEndOther)
            }
            "רצועות הרמה" -> {
                putIfNotBlank("textileKind", textileKind)
                putIfNotBlank("textileColorInput", textileColorInput)
            }
            "אביזרי קצה" -> {
                putIfNotBlank("endAccessoryChoice", endAccessoryChoice)
                putIfNotBlank("endAccessoryOther", endAccessoryOther)
                putIfNotBlank("omegaSize", omegaSize)
            }
            else -> {
                val template = selectedSmartTemplate ?: return null
                template.fields
                    .filterNot { isUnsafeAccessoryTemplateField(it.name) }
                    .forEach { field ->
                        putIfNotBlank("smartField::${field.name}", smartFieldValues[field.name].orEmpty())
                    }
            }
        }

        return if (values.isEmpty()) null else AccessoryReusableTemplate(cleanName, selectedAccessoryType, values)
    }

    fun clearUnsafeFieldsAfterLoadingAccessoryTemplate(accessoryType: String) {
        when (accessoryType) {
            "מענב שרשרת" -> {
                chainLength = ""
                chainQuantity = ""
                chainTestLoad = ""
                chainSerialNumbers.clear()
                chainManufacturerChoice = ""
                chainManufacturerOther = ""
                chainModel = ""
            }
            "מענב כבל פלדה" -> {
                wireLength = ""
                wireQuantity = ""
                wireTestLoad = ""
                wireSerialNumbers.clear()
                wireManufacturerChoice = ""
                wireManufacturerOther = ""
                wireModel = ""
            }
            "רצועות הרמה" -> {
                textileLength = ""
                textileQuantity = ""
                textileTestLoad = ""
                textileSerialNumbers.clear()
                textileManufacturerChoice = ""
                textileManufacturerOther = ""
                textileModel = ""
            }
            "אביזרי קצה" -> {
                omegaQuantity = ""
                omegaTestLoad = ""
                omegaSerialNumbers.clear()
                omegaManufacturerChoice = ""
                omegaManufacturerOther = ""
                omegaModel = ""
            }
            else -> {
                smartQuantity = ""
                smartTestLoad = ""
                smartWll = ""
                smartSerialNumbers.clear()
                smartManufacturer = ""
                smartModel = ""
                selectedSmartTemplate?.fields
                    ?.filter { isUnsafeAccessoryTemplateField(it.name) }
                    ?.forEach { field -> smartFieldValues.remove(field.name) }
            }
        }
    }

    fun applyAccessoryReusableTemplate(template: AccessoryReusableTemplate) {
        selectedAccessoryType = template.accessoryType
        val values = template.values

        when (template.accessoryType) {
            "מענב שרשרת" -> {
                chainBranches = values["chainBranches"].orEmpty()
                chainSize = values["chainSize"].orEmpty()
                chainEnd1 = values["chainEnd1"].orEmpty()
                chainEnd1Other = values["chainEnd1Other"].orEmpty()
                chainEnd2 = values["chainEnd2"].orEmpty()
                chainEnd2Other = values["chainEnd2Other"].orEmpty()
            }
            "מענב כבל פלדה" -> {
                wireBranches = values["wireBranches"].orEmpty()
                wireDiameterMm = values["wireDiameterMm"].orEmpty()
                wireEndType = values["wireEndType"].orEmpty()
                wireHasExtraEnd = values["wireHasExtraEnd"] == "true"
                wireExtraEndChoice = values["wireExtraEndChoice"].orEmpty()
                wireExtraEndOther = values["wireExtraEndOther"].orEmpty()
            }
            "רצועות הרמה" -> {
                textileKind = values["textileKind"].orEmpty()
                textileColorInput = values["textileColorInput"].orEmpty()
            }
            "אביזרי קצה" -> {
                endAccessoryChoice = values["endAccessoryChoice"].orEmpty()
                endAccessoryOther = values["endAccessoryOther"].orEmpty()
                omegaSize = values["omegaSize"].orEmpty()
            }
            else -> {
                smartFieldValues.clear()
                values.forEach { (key, value) ->
                    if (key.startsWith("smartField::")) {
                        val fieldName = key.removePrefix("smartField::")
                        if (!isUnsafeAccessoryTemplateField(fieldName)) {
                            smartFieldValues[fieldName] = value
                        }
                    }
                }
            }
        }

        clearUnsafeFieldsAfterLoadingAccessoryTemplate(template.accessoryType)
        accessoryTemplateMessage = "התבנית '${template.name}' נטענה. נא להשלים אורך, כמות ומספרי זיהוי."
    }

    fun saveCurrentAccessoryReusableTemplate() {
        val template = collectCurrentAccessoryReusableTemplate(accessoryTemplateNameDraft)
        if (template == null) {
            accessoryTemplateMessage = "לא נשמרה תבנית. יש להזין שם תבנית ולמלא לפחות נתון מבנה אחד."
            return
        }

        val original = editingAccessoryTemplateOriginal
        val updated = (reusableAccessoryTemplates
            .filterNot { existing ->
                val sameNewName = existing.accessoryType == template.accessoryType &&
                    existing.name.equals(template.name, ignoreCase = true)
                val sameOriginal = original != null &&
                    existing.accessoryType == original.accessoryType &&
                    existing.name.equals(original.name, ignoreCase = true)
                sameNewName || sameOriginal
            } + template)
            .sortedWith(compareBy<AccessoryReusableTemplate> { it.accessoryType }.thenBy { it.name })

        reusableAccessoryTemplates = updated
        saveAccessoryReusableTemplates(prefs, updated)
        accessoryTemplateNameDraft = ""
        editingAccessoryTemplateOriginal = null
        saveAccessoryTemplateDialogOpen = false
        accessoryTemplateMessage = "התבנית '${template.name}' נשמרה. אורך, כמות ומספרי זיהוי לא נשמרו בכוונה."
    }

    fun startEditingAccessoryReusableTemplate(template: AccessoryReusableTemplate) {
        applyAccessoryReusableTemplate(template)
        accessoryTemplateNameDraft = template.name
        editingAccessoryTemplateOriginal = template
        loadAccessoryTemplateDialogOpen = false
        saveAccessoryTemplateDialogOpen = true
        accessoryTemplateMessage = "התבנית '${template.name}' נטענה לעריכה. שנה את השדות ולחץ עדכן תבנית."
    }

    fun deleteAccessoryReusableTemplate(template: AccessoryReusableTemplate) {
        val updated = reusableAccessoryTemplates
            .filterNot { existing ->
                existing.accessoryType == template.accessoryType &&
                    existing.name.equals(template.name, ignoreCase = true)
            }
            .sortedWith(compareBy<AccessoryReusableTemplate> { it.accessoryType }.thenBy { it.name })

        reusableAccessoryTemplates = updated
        saveAccessoryReusableTemplates(prefs, updated)
        if (editingAccessoryTemplateOriginal?.accessoryType == template.accessoryType &&
            editingAccessoryTemplateOriginal?.name?.equals(template.name, ignoreCase = true) == true
        ) {
            editingAccessoryTemplateOriginal = null
            accessoryTemplateNameDraft = ""
        }
        deleteAccessoryTemplateCandidate = null
        accessoryTemplateMessage = "התבנית '${template.name}' נמחקה. תסקירים קיימים לא נמחקו."
    }

    val reportAccessories = remember(screenKey) {
        mutableStateListOf<ReportAccessoryRow>().apply {
            if (incomingSmartRows.isNotEmpty()) {
                // שורות שחזרו מטופס חכם אביזרים — מחליפות את הטבלה הקיימת
                incomingSmartRows.forEach { row ->
                    add(ReportAccessoryRow(row.description, row.manufacturer, row.model, row.quantity, row.serialNumbers, row.testLoad, row.wll))
                }
            } else {
                editingReport?.accessories?.forEach { row ->
                    add(
                        ReportAccessoryRow(
                            description = row.description,
                            manufacturer = row.manufacturer,
                            model = row.model,
                            quantity = row.quantity,
                            serialNumbers = row.serialNumbers,
                            testLoad = row.testLoad,
                            wll = row.wll
                        )
                    )
                }
            }
        }
    }

    val reportDefects = remember(screenKey) {
        mutableStateListOf<ReportDefectRow>().apply {
            if (incomingSmartDefects.isNotEmpty()) {
                incomingSmartDefects.forEach { d -> add(ReportDefectRow(d.description, d.fixUntil)) }
            } else {
                editingReport?.defects?.forEach { row ->
                    add(ReportDefectRow(row.defectDescription, row.fixUntil))
                }
            }
        }
    }

    val reportNotes = remember(screenKey) {
        mutableStateListOf<ReportNoteRow>().apply {
            if (editingReport != null) {
                editingReport.notes.forEach { row ->
                    add(ReportNoteRow(row.text))
                }
            } else {
                reportTextSettings.templateNotesList.forEach { text ->
                    add(ReportNoteRow(text))
                }
            }
        }
    }


    // -----------------------------
    // תמונות לפי מספר זיהוי
    // -----------------------------
    // כל תמונה משויכת למספר זיהוי מסוים מתוך שורת האביזר.
    // כך שורה אחת עם כמות 2/4/6 יכולה להכיל תמונות נפרדות לכל אביזר פיזי.
    val reportPhotos = remember(screenKey) {
        mutableStateListOf<ReportPhotoStorage.PhotoRecord>().apply {
            addAll(ReportPhotoStorage.loadPhotos(context, runningNumber))
        }
    }

    var photoDialogOpen by remember { mutableStateOf(false) }
    var photoDialogAccessoryIndex by remember { mutableStateOf(-1) }
    var photoDialogSerials by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingPhotoSerial by remember { mutableStateOf("") }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }

    var printPhotoQuestionOpen by remember { mutableStateOf(false) }
    var pendingPrintHtml by remember { mutableStateOf("") }
    var pendingPrintFileName by remember { mutableStateOf("") }

    // קוד מלא 21:
    // כאשר מפיקים PDF סופי ושומרים גרסת PDF, נזכור כאן את שם הגרסה שנשמרה.
    // אם המשתמש בחר לצרף תמונות, נעדכן את אותה גרסת PDF שמורה עם HTML הכולל נספח תמונות.
    // אם זה רק "הצג תסקיר PDF" לבדיקה, הערך יישאר ריק ולא נשנה רשומות שמורות.
    var pendingSavedPdfVersionName by remember { mutableStateOf("") }

    // קוד מלא 22:
    // בהפקת PDF סופי אסור לחזור למסך הראשי לפני שהמשתמש בחר
    // האם לצרף תמונות או לא.
    // לכן שומרים כאן את פעולת הסיום, ומבצעים אותה רק אחרי פתיחת ההדפסה.
    var pendingAfterPrintSavedInspection by remember {
        mutableStateOf<com.nasavi.liftinginspectorpro.SavedInspection?>(null)
    }
    var pendingAfterPrintShouldAdvance by remember { mutableStateOf(false) }

    fun finishAfterPrintIfNeeded() {
        val savedInspection = pendingAfterPrintSavedInspection

        if (savedInspection != null) {
            val shouldAdvance = pendingAfterPrintShouldAdvance

            pendingAfterPrintSavedInspection = null
            pendingAfterPrintShouldAdvance = false

            InspectorSettingsStorage.saveTemplateNotesList(context, reportNotes.map { it.text })
            onSaveReport(savedInspection, shouldAdvance)
        }
    }

    fun finishAfterPrintIfNeededDelayed() {
        // עדכון 011:
        // בהפקת PDF סופי Android פותח חלון הדפסה בצורה אסינכרונית דרך WebView.
        // אם מחזירים את המסך הראשי מיד באותה לחיצה, בחלק מהמכשירים זה עלול לגרום לקריסה.
        // לכן מסיימים את שמירת הרשומה וחזרה למסך הראשי בהשהיה קצרה אחרי פתיחת ההדפסה.
        val activity = context as? android.app.Activity
        val rootView = activity?.window?.decorView
        if (rootView != null) {
            rootView.postDelayed({ finishAfterPrintIfNeeded() }, 1500)
        } else {
            finishAfterPrintIfNeeded()
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingPhotoUri
        val serial = pendingPhotoSerial

        if (success && uri != null && serial.isNotBlank()) {
            val photo = ReportPhotoStorage.PhotoRecord(
                reportNumber = runningNumber,
                serialNumber = serial,
                uri = uri.toString(),
                capturedAtMillis = System.currentTimeMillis()
            )

            reportPhotos.add(photo)
            ReportPhotoStorage.savePhotos(context, runningNumber, reportPhotos)

            android.widget.Toast.makeText(
                context,
                "התמונה נשמרה למספר זיהוי $serial",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }

        pendingPhotoUri = null
        pendingPhotoSerial = ""
    }

    fun requestPrintHtml(
        html: String,
        fileName: String,
        savedPdfVersionName: String = "",
        afterPrintSavedInspection: com.nasavi.liftinginspectorpro.SavedInspection? = null,
        afterPrintShouldAdvance: Boolean = false
    ) {
        // קוד מלא 22:
        // פונקציה זו משמשת גם לתצוגה לבדיקה וגם להפקת PDF סופי.
        // אם זו הפקת PDF סופית, לא חוזרים למסך הראשי מיד.
        // קודם פותחים את ההדפסה, ואם יש תמונות נותנים לבחור כן/לא לצירוף תמונות.
        // רק אחרי הבחירה והפתיחה להדפסה מפעילים onSaveReport וחוזרים למסך הראשי.
        if (reportPhotos.isEmpty()) {
            pdfPrintLoading = true
            saveHtmlAsPdfAndOpen(context = context, htmlContent = html, fileName = fileName, onReady = { file ->
                pdfPrintLoading = false
                pendingReadyPdfFile = file
            })

            if (afterPrintSavedInspection != null) {
                pendingAfterPrintSavedInspection = afterPrintSavedInspection
                pendingAfterPrintShouldAdvance = afterPrintShouldAdvance
            }
        } else {
            pendingPrintHtml = html
            pendingPrintFileName = fileName
            pendingSavedPdfVersionName = savedPdfVersionName
            pendingAfterPrintSavedInspection = afterPrintSavedInspection
            pendingAfterPrintShouldAdvance = afterPrintShouldAdvance
            printPhotoQuestionOpen = true
        }
    }

    val todayMillis = remember {
        LocalDate.now()
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    }

    if (inspectionDateDialogOpen) {
        val initialMillis = parseDateToMillisOrToday(inspectionDate)
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis <= todayMillis
                }
            }
        )

        DatePickerDialog(
            onDismissRequest = { inspectionDateDialogOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            val selectedDate = millisToLocalDate(selectedMillis)
                            inspectionDate = formatLocalDate(selectedDate)
                            nextInspectionDate = formatLocalDate(selectedDate.plusMonths(6))
                        }
                        inspectionDateDialogOpen = false
                    }
                ) {
                    Text("אישור")
                }
            },
            dismissButton = {
                TextButton(onClick = { inspectionDateDialogOpen = false }) {
                    Text("ביטול")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (defectFixUntilDialogOpen) {
        val minMillis = parseDateToMillisOrToday(inspectionDate)
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = minMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis >= minMillis
                }
            }
        )

        DatePickerDialog(
            onDismissRequest = { defectFixUntilDialogOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            val selectedDate = millisToLocalDate(selectedMillis)
                            defectFixUntil = formatLocalDate(selectedDate)
                        }
                        defectFixUntilDialogOpen = false
                    }
                ) {
                    Text("אישור")
                }
            },
            dismissButton = {
                TextButton(onClick = { defectFixUntilDialogOpen = false }) {
                    Text("ביטול")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    val formScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(formScrollState)
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("פרטי התסקיר")

        OutlinedTextField(
            value = inspectorFirstName,
            onValueChange = { inspectorFirstName = it },
            label = { Text("שם פרטי בודק") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = inspectorLastName,
            onValueChange = { inspectorLastName = it },
            label = { Text("שם משפחה בודק") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = inspectorNumber,
            onValueChange = { inspectorNumber = it.filter { ch -> ch.isDigit() } },
            label = { Text("מספר הסמכה / מספר בודק") },
            modifier = Modifier.fillMaxWidth()
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isLockedForNewAccessories) { inspectionDateDialogOpen = true }
        ) {
            OutlinedTextField(
                value = inspectionDate,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text("תאריך בדיקה") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(
            value = nextInspectionDate,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text("תאריך בדיקה הבאה") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = runningNumber,
            onValueChange = { runningNumber = it },
            label = { Text("מספר רץ") },
            modifier = Modifier.fillMaxWidth()
        )

        SiteSelector(
            sites = availableSites,
            selectedSiteId = selectedSiteId,
            onSiteSelected = { selectedSiteId = it }
        )

        AccessoryClientMemoryField(
            value = owner,
            clientMemoryStore = clientMemoryStore,
            onValueChange = { owner = it },
            onClientSelected = { selectedClient ->
                owner = selectedClient.name
                address = selectedClient.address
                phone = selectedClient.phone
                contactPerson = selectedClient.contactPerson
            }
        )

        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("כתובת משרד") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("טלפון") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = contactPerson,
            onValueChange = { contactPerson = it },
            label = { Text("איש קשר") },
            modifier = Modifier.fillMaxWidth()
        )

        ClientMemorySaveOptions(
            saveClientToMemory = saveClientToMemory,
            onSaveClientToMemoryChange = { saveClientToMemory = it },
            saveAddress = saveClientAddressToMemory,
            onSaveAddressChange = { saveClientAddressToMemory = it },
            savePhone = saveClientPhoneToMemory,
            onSavePhoneChange = { saveClientPhoneToMemory = it },
            saveContact = saveClientContactToMemory,
            onSaveContactChange = { saveClientContactToMemory = it }
        )

        OutlinedTextField(
            value = vehicle,
            onValueChange = { vehicle = it },
            label = { Text("מס' רכב") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = inspectionLocation,
            onValueChange = { inspectionLocation = it },
            label = { Text("כתובת בדיקה") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { inspectionPlaceDialogOpen = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (inspectionPlaceType.isBlank()) {
                    "בחר היכן הציוד נבדק"
                } else {
                    "הציוד נבדק ב: $inspectionPlaceType"
                }
            )
        }

        Text("הערה כללית לתסקיר")

        Button(
            onClick = { hasGeneralNote = !hasGeneralNote },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (hasGeneralNote) "יש הערה כללית: כן" else "יש הערה כללית: לא")
        }

        if (hasGeneralNote) {
            OutlinedTextField(
                value = generalNoteText,
                onValueChange = { generalNoteText = it },
                label = { Text("הערה כללית") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (isLockedForNewAccessories) {
            Text("התסקיר כבר הופק ל-PDF ולכן נעול להוספת אביזרים חדשים. ניתן לעדכן פרטים כלליים, ליקויים והערות ולהפיק גרסת PDF נוספת.")
        }

        Text("סוג אביזר")

        if (onNavigateToSmartAccessories != null && !isLockedForNewAccessories) {
            Button(
                onClick = {
                    // שמור טיוטה לפני המעבר לטופס החכם כדי לשמר פרטי לקוח ופרטים כלליים
                    val draft = buildWorkingReportForStorage(
                        inspectorNumber = inspectorNumber,
                        inspectorFirstName = inspectorFirstName,
                        inspectorLastName = inspectorLastName,
                        inspectorCertificateNumber = inspectorNumber,
                        runningNumber = runningNumber,
                        inspectionDate = inspectionDate,
                        nextInspectionDate = nextInspectionDate,
                        owner = owner,
                        address = address,
                        phone = phone,
                        contactPerson = contactPerson,
                        vehicle = vehicle,
                        inspectionLocation = inspectionLocation,
                        inspectionPlaceType = inspectionPlaceType,
                        fixedNote = fixedNote,
                        generalNote = if (hasGeneralNote) generalNoteText else "",
                        reportAccessories = reportAccessories.toList(),
                        reportDefects = reportDefects.toList(),
                        reportNotes = reportNotes.toList(),
                        site = selectedSiteId,
                        html = "",
                        isLockedForNewAccessories = isLockedForNewAccessories
                    )
                    scope.launch {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            ReportStorage.saveWorkingReport(context, draft)
                        }
                        onNavigateToSmartAccessories(
                            inspectionDate,
                            reportAccessories.mapIndexed { idx, row ->
                                SmartFormTableRow(idx + 1, "", row.description, row.manufacturer, row.model, row.quantity, row.serialNumbers, row.testLoad, row.wll)
                            },
                            reportDefects.map { SmartFormDefect(0, it.defectDescription, it.fixUntil) },
                            runningNumber
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("פתח טופס חכם אביזרים")
            }
        }

        // כפתור "בחר סוג אביזר (ידני)" הוסר — השימוש עובר לטופס החכם אביזרים

        if (selectedAccessoryType.isNotBlank() && !isLockedForNewAccessories) {
            val templatesForSelectedAccessory = reusableAccessoryTemplates
                .filter { it.accessoryType == selectedAccessoryType }
                .sortedBy { it.name }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAccessoryTemplateTools = !showAccessoryTemplateTools },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = showAccessoryTemplateTools,
                    onCheckedChange = { showAccessoryTemplateTools = it }
                )
                Text("הצג אפשרויות תבנית אביזר")
            }

            if (showAccessoryTemplateTools) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.Gray)
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("תבניות אביזר")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                accessoryTemplateNameDraft = ""
                                editingAccessoryTemplateOriginal = null
                                saveAccessoryTemplateDialogOpen = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text("שמור כתבנית")
                        }
                        Button(
                            onClick = { loadAccessoryTemplateDialogOpen = true },
                            enabled = templatesForSelectedAccessory.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text(
                                if (templatesForSelectedAccessory.isEmpty()) {
                                    "אין תבניות"
                                } else {
                                    "טען תבנית"
                                }
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    if (selectedSmartTemplate != null) {
                        smartBuilderEditingOriginalTypeName = selectedSmartTemplate.typeName
                        smartBuilderTypeName = selectedSmartTemplate.typeName
                        smartBuilderDescriptionTemplate = selectedSmartTemplate.descriptionTemplate
                        smartBuilderFields.clear()
                        selectedSmartTemplate.fields.forEach { field ->
                            smartBuilderFields.add(
                                SmartFieldDraft(
                                    name = field.name,
                                    inputType = field.inputType,
                                    optionsText = field.options.joinToString("\n")
                                )
                            )
                        }
                        if (smartBuilderFields.isEmpty()) smartBuilderFields.add(SmartFieldDraft())
                        smartBuilderFormulas.clear()
                        selectedSmartTemplate.formulas.forEach { formula ->
                            smartBuilderFormulas.add(
                                SmartFormulaDraft(
                                    name = formula.name,
                                    expression = formula.expression,
                                    targetFieldName = formula.targetFieldName,
                                    digitsText = formula.digits.toString()
                                )
                            )
                        }
                    } else {
                        smartBuilderEditingOriginalTypeName = null
                        smartBuilderTypeName = ""
                        smartBuilderDescriptionTemplate = ""
                        smartBuilderFields.clear()
                        smartBuilderFields.add(SmartFieldDraft())
                        smartBuilderFormulas.clear()
                    }
                    smartBuilderMessage = ""
                    addAccessoryDialogOpen = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (selectedSmartTemplate != null) "ערוך טופס חכם לאביזר" else "הוסף סוג אביזר חכם")
            }

            if (accessoryTemplateMessage.isNotBlank()) {
                Text(accessoryTemplateMessage)
            }
        }

        if (selectedAccessoryType == "מענב שרשרת") {
            Text("טופס הזנה - מענב שרשרת")

            Button(
                onClick = { chainBranchesDialogOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (chainBranches.isBlank()) "בחר מספר ענפים" else chainBranches)
            }

            Button(
                onClick = { chainSizeDialogOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (chainSize.isBlank()) "בחר מידה" else chainSize)
            }

            OutlinedTextField(
                value = chainLength,
                onValueChange = { value ->
                    val filtered = value.filter { it.isDigit() || it == '.' }
                    val parts = filtered.split(".")
                    chainLength =
                        if (parts.size <= 2) {
                            if (parts.size == 2) parts[0] + "." + parts[1].take(1) else filtered
                        } else {
                            chainLength
                        }
                },
                label = { Text("אורך במטר") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { chainEnd1DialogOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (chainEnd1.isBlank()) "בחר אביזר קצה 1" else chainEnd1)
            }

            if (chainEnd1 == "אחר") {
                OutlinedTextField(
                    value = chainEnd1Other,
                    onValueChange = { chainEnd1Other = it },
                    label = { Text("אביזר קצה 1 אחר") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = { chainEnd2DialogOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (chainEnd2.isBlank() || chainEnd2 == "ללא") "אביזר קצה 2 - אופציונלי" else chainEnd2)
            }

            if (chainEnd2 == "אחר") {
                OutlinedTextField(
                    value = chainEnd2Other,
                    onValueChange = { chainEnd2Other = it },
                    label = { Text("אביזר קצה 2 אחר") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = { chainManufacturerDialogOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (chainManufacturerChoice.isBlank()) "בחר יצרן" else chainManufacturerChoice)
            }

            if (chainManufacturerChoice == "אחר") {
                OutlinedTextField(
                    value = chainManufacturerOther,
                    onValueChange = { chainManufacturerOther = it },
                    label = { Text("יצרן אחר") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                value = chainModel,
                onValueChange = { value ->
                    chainModel = value
                    applyAccessoryManufacturerModelMemory("מענב שרשרת", chainManufacturerFinal, value.trim())
                },
                label = { Text("דגם") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = chainQuantity,
                onValueChange = { value ->
                    chainQuantity = value.filter { it.isDigit() }.take(2)
                },
                label = { Text("כמות") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            if (chainQuantityInt > 0) {
                repeat(chainQuantityInt) { index ->
                    OutlinedTextField(
                        value = chainSerialNumbers[index],
                        onValueChange = { value ->
                            val filtered = value.filter { ch -> ch.isDigit() }
                            val candidateSerials = chainSerialNumbers.toMutableList().also { it[index] = filtered }
                            val duplicate = findDuplicateSerialForNewAccessory(
                                existingRows = reportAccessories,
                                newSerialNumbersText = candidateSerials.joinToString("\n")
                            )
                            if (duplicate != null) {
                                android.widget.Toast.makeText(context, duplicateSerialMessage(duplicate), android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                chainSerialNumbers[index] = filtered
                            }
                        },
                        label = { Text("מס' זיהוי ${index + 1}") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            OutlinedTextField(
                value = chainTestLoad,
                onValueChange = { chainTestLoad = it },
                label = { Text("עומס מבחן") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = chainWll,
                onValueChange = {},
                readOnly = true,
                label = { Text("ע.ע.ב") },
                modifier = Modifier.fillMaxWidth()
            )

            if (chainDescription.isNotBlank()) {
                OutlinedTextField(
                    value = chainDescription,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("תיאור האביזר") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }


        if (selectedAccessoryType == "מענב כבל פלדה") {
            Text("טופס הזנה - מענב כבל פלדה")

            Button(
                onClick = { wireBranchesDialogOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (wireBranches.isBlank()) "בחר מספר ענפים" else wireBranches)
            }

            OutlinedTextField(
                value = wireDiameterMm,
                onValueChange = { value ->
                    // קוטר הכבל הוא שדה חופשי במ\"מ, לכן מאפשרים מספרים ונקודה עשרונית אחת.
                    val filtered = value.filter { it.isDigit() || it == '.' }
                    val parts = filtered.split(".")
                    wireDiameterMm =
                        if (parts.size <= 2) {
                            if (parts.size == 2) parts[0] + "." + parts[1].take(1) else filtered
                        } else {
                            wireDiameterMm
                        }
                },
                label = { Text("קוטר הכבל במ\"מ") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            OutlinedTextField(
                value = wireLength,
                onValueChange = { value ->
                    val filtered = value.filter { it.isDigit() || it == '.' }
                    val parts = filtered.split(".")
                    wireLength =
                        if (parts.size <= 2) {
                            if (parts.size == 2) parts[0] + "." + parts[1].take(1) else filtered
                        } else {
                            wireLength
                        }
                },
                label = { Text("אורך במטר") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            Button(
                onClick = { wireEndTypeDialogOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (wireEndType.isBlank()) "בחר סוג סיומת קצוות" else wireEndType)
            }

            if (wireEndType == "מהדקים") {
                Text("אזהרה: בקצוות עם מהדקים יש לוודא התקנה תקינה, מספר מהדקים מתאים, כיוון נכון וחיזוק לפי הוראות יצרן. החישוב משתמש במקדם 0.7.")
            }

            Button(
                onClick = {
                    wireHasExtraEnd = !wireHasExtraEnd
                    if (!wireHasExtraEnd) {
                        wireExtraEndChoice = ""
                        wireExtraEndOther = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (wireHasExtraEnd) "אביזר קצה נוסף: כן" else "אביזר קצה נוסף: לא")
            }

            if (wireHasExtraEnd) {
                Button(
                    onClick = { wireExtraEndDialogOpen = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (wireExtraEndChoice.isBlank()) "בחר אביזר קצה נוסף" else wireExtraEndChoice)
                }

                if (wireExtraEndChoice == "אחר") {
                    OutlinedTextField(
                        value = wireExtraEndOther,
                        onValueChange = { wireExtraEndOther = it },
                        label = { Text("אביזר קצה נוסף אחר") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Button(
                onClick = { wireManufacturerDialogOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (wireManufacturerChoice.isBlank()) "בחר יצרן" else wireManufacturerChoice)
            }

            if (wireManufacturerChoice == "אחר") {
                OutlinedTextField(
                    value = wireManufacturerOther,
                    onValueChange = { wireManufacturerOther = it },
                    label = { Text("יצרן אחר") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = wireModel,
                onValueChange = { value ->
                    wireModel = value
                    applyAccessoryManufacturerModelMemory("מענב כבל פלדה", wireManufacturerFinal, value.trim())
                },
                label = { Text("דגם") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = wireQuantity,
                onValueChange = { value ->
                    wireQuantity = value.filter { it.isDigit() }.take(2)
                },
                label = { Text("כמות") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            if (wireQuantityInt > 0) {
                repeat(wireQuantityInt) { index ->
                    OutlinedTextField(
                        value = wireSerialNumbers[index],
                        onValueChange = { value ->
                            val filtered = value.filter { ch -> ch.isDigit() }
                            val candidateSerials = wireSerialNumbers.toMutableList().also { it[index] = filtered }
                            val duplicate = findDuplicateSerialForNewAccessory(
                                existingRows = reportAccessories,
                                newSerialNumbersText = candidateSerials.joinToString("\n")
                            )
                            if (duplicate != null) {
                                android.widget.Toast.makeText(context, duplicateSerialMessage(duplicate), android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                wireSerialNumbers[index] = filtered
                            }
                        },
                        label = { Text("מס' זיהוי ${index + 1}") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            OutlinedTextField(
                value = wireTestLoad,
                onValueChange = { wireTestLoad = it },
                label = { Text("עומס מבחן בטון") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = wireWll,
                onValueChange = {},
                readOnly = true,
                label = { Text("ע.ע.ב") },
                modifier = Modifier.fillMaxWidth()
            )

            if (wireDescription.isNotBlank()) {
                OutlinedTextField(
                    value = wireDescription,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("תיאור האביזר") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (selectedAccessoryType == "אביזרי קצה") {
            Text("טופס הזנה - אביזרי קצה")

            Button(
                onClick = { endAccessoryDialogOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (endAccessoryChoice.isBlank()) "בחר אביזר קצה" else endAccessoryChoice)
            }

            if (endAccessoryChoice == "אחר") {
                OutlinedTextField(
                    value = endAccessoryOther,
                    onValueChange = { endAccessoryOther = it },
                    label = { Text("אביזר קצה אחר") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (endAccessoryChoice == "סגיר אומגה") {

                Button(
                    onClick = { omegaSizeDialogOpen = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (omegaSize.isBlank()) "בחר מידה באינץ'" else "${omegaSize}\"")
                }

            }

            Button(
                onClick = { omegaManufacturerDialogOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (omegaManufacturerChoice.isBlank()) "בחר יצרן" else omegaManufacturerChoice)
            }

            if (omegaManufacturerChoice == "אחר") {
                OutlinedTextField(
                    value = omegaManufacturerOther,
                    onValueChange = { omegaManufacturerOther = it },
                    label = { Text("יצרן אחר") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = omegaModel,
                onValueChange = { value ->
                    omegaModel = value
                    applyAccessoryManufacturerModelMemory("אביזרי קצה", omegaManufacturerFinal, value.trim())
                },
                label = { Text("דגם") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = omegaQuantity,
                onValueChange = { value ->
                    omegaQuantity = value.filter { it.isDigit() }.take(2)
                },
                label = { Text("כמות") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            if (omegaQuantityInt > 0) {
                repeat(omegaQuantityInt) { index ->
                    OutlinedTextField(
                        value = omegaSerialNumbers[index],
                        onValueChange = { value ->
                            val filtered = value.filter { ch -> ch.isDigit() }
                            val candidateSerials = omegaSerialNumbers.toMutableList().also { it[index] = filtered }
                            val duplicate = findDuplicateSerialForNewAccessory(
                                existingRows = reportAccessories,
                                newSerialNumbersText = candidateSerials.joinToString("\n")
                            )
                            if (duplicate != null) {
                                android.widget.Toast.makeText(context, duplicateSerialMessage(duplicate), android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                omegaSerialNumbers[index] = filtered
                            }
                        },
                        label = { Text("מס' זיהוי ${index + 1}") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            OutlinedTextField(
                value = omegaTestLoad,
                onValueChange = { omegaTestLoad = it },
                label = { Text("עומס מבחן") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = omegaWll,
                onValueChange = {},
                readOnly = true,
                label = { Text("עומס עבודה בטוח") },
                modifier = Modifier.fillMaxWidth()
            )

            if (endAccessoryDescription.isNotBlank()) {
                OutlinedTextField(
                    value = endAccessoryDescription,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("תיאור האביזר") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (selectedAccessoryType == "רצועות הרמה") {
            Text("טופס הזנה - רצועות הרמה")

            Button(
                onClick = { textileKindDialogOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (textileKind.isBlank()) "בחר סוג רצועה" else textileKind)
            }

            Button(
                onClick = { textileColorDialogOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (textileColorInput.isBlank()) "בחר גוון" else textileColorInput)
            }

            OutlinedTextField(
                value = textileLength,
                onValueChange = { textileLength = it },
                label = { Text("אורך") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { textileManufacturerDialogOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (textileManufacturerChoice.isBlank()) "בחר יצרן" else textileManufacturerChoice)
            }

            if (textileManufacturerChoice == "אחר") {
                OutlinedTextField(
                    value = textileManufacturerOther,
                    onValueChange = { textileManufacturerOther = it },
                    label = { Text("יצרן אחר") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = textileModel,
                onValueChange = { value ->
                    textileModel = value
                    applyAccessoryManufacturerModelMemory("רצועות הרמה", textileManufacturerFinal, value.trim())
                },
                label = { Text("דגם") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = textileQuantity,
                onValueChange = { value ->
                    textileQuantity = value.filter { it.isDigit() }.take(2)
                },
                label = { Text("כמות") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            if (textileQuantityInt > 0) {
                repeat(textileQuantityInt) { index ->
                    OutlinedTextField(
                        value = textileSerialNumbers[index],
                        onValueChange = { value ->
                            val filtered = value.filter { ch -> ch.isDigit() }
                            val candidateSerials = textileSerialNumbers.toMutableList().also { it[index] = filtered }
                            val duplicate = findDuplicateSerialForNewAccessory(
                                existingRows = reportAccessories,
                                newSerialNumbersText = candidateSerials.joinToString("\n")
                            )
                            if (duplicate != null) {
                                android.widget.Toast.makeText(context, duplicateSerialMessage(duplicate), android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                textileSerialNumbers[index] = filtered
                            }
                        },
                        label = { Text("מס' זיהוי ${index + 1}") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            OutlinedTextField(
                value = textileTestLoad,
                onValueChange = { textileTestLoad = it },
                label = { Text("עומס מבחן") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = textileWll,
                onValueChange = {},
                readOnly = true,
                label = { Text("ע.ע.ב") },
                modifier = Modifier.fillMaxWidth()
            )

            if (textileDescription.isNotBlank()) {
                OutlinedTextField(
                    value = textileDescription,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("תיאור האביזר") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (selectedSmartTemplate != null) {
            Text("טופס חכם - ${selectedSmartTemplate.typeName}")
            Button(
                onClick = {
                    smartBuilderEditingOriginalTypeName = selectedSmartTemplate.typeName
                    smartBuilderTypeName = selectedSmartTemplate.typeName
                    smartBuilderDescriptionTemplate = selectedSmartTemplate.descriptionTemplate
                    smartBuilderFields.clear()
                    selectedSmartTemplate.fields.forEach { field ->
                        smartBuilderFields.add(
                            SmartFieldDraft(
                                name = field.name,
                                inputType = field.inputType,
                                optionsText = field.options.joinToString("\n"),
                                isRequired = field.isRequired,
                                isMemoryKey = field.isMemoryKey,
                                inDescription = field.inDescription
                            )
                        )
                    }
                    if (smartBuilderFields.isEmpty()) smartBuilderFields.add(SmartFieldDraft())
                    smartBuilderFormulas.clear()
                    selectedSmartTemplate.formulas.forEach { formula ->
                        smartBuilderFormulas.add(
                            SmartFormulaDraft(
                                name = formula.name,
                                expression = formula.expression,
                                targetFieldName = formula.targetFieldName,
                                digitsText = formula.digits.toString()
                            )
                        )
                    }
                    smartBuilderMessage = ""
                    addAccessoryDialogOpen = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ערוך מבנה טופס חכם / תבנית תיאור")
            }

            selectedSmartTemplate.fields.forEach { field ->
                val currentValue = smartFieldValues[field.name].orEmpty()
                when (field.inputType) {
                    SmartFieldInputType.LIST -> {
                        val isOtherListValue = smartOtherListFieldNames.contains(field.name) ||
                                (currentValue.isNotBlank() && currentValue !in field.options)

                        Button(onClick = { smartSelectedListFieldName = field.name }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                when {
                                    currentValue.isNotBlank() -> currentValue
                                    isOtherListValue -> "הקלד ${field.name} אחר"
                                    else -> "בחר ${field.name}"
                                }
                            )
                        }

                        if (isOtherListValue) {
                            OutlinedTextField(
                                value = currentValue,
                                onValueChange = { value ->
                                    if (!smartOtherListFieldNames.contains(field.name)) {
                                        smartOtherListFieldNames.add(field.name)
                                    }
                                    smartFieldValues[field.name] = value
                                },
                                label = { Text("הקלד ${field.name} אחר") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    SmartFieldInputType.INTEGER -> {
                        OutlinedTextField(
                            value = currentValue,
                            onValueChange = { value -> smartFieldValues[field.name] = value.filter { it.isDigit() } },
                            label = { Text(field.name) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    SmartFieldInputType.DECIMAL -> {
                        OutlinedTextField(
                            value = currentValue,
                            onValueChange = { value ->
                                val filtered = value.filter { it.isDigit() || it == '.' || it == ',' }
                                val dotNormalized = filtered.replace(',', '.')
                                val parts = dotNormalized.split(".")
                                smartFieldValues[field.name] = if (parts.size <= 2) {
                                    if (parts.size == 2) parts[0] + "." + parts[1].take(3) else dotNormalized
                                } else currentValue
                            },
                            label = { Text(field.name) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                    SmartFieldInputType.DATE -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = currentValue,
                                onValueChange = { value -> smartFieldValues[field.name] = value },
                                label = { Text(field.name) },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Button(onClick = { smartSelectedDateFieldName = field.name }) {
                                Text("בחר")
                            }
                        }
                    }
                    SmartFieldInputType.BOOLEAN -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = currentValue == "כן" || currentValue.equals("true", ignoreCase = true),
                                onCheckedChange = { checked -> smartFieldValues[field.name] = if (checked) "כן" else "לא" }
                            )
                            Text(field.name)
                        }
                    }
                    SmartFieldInputType.READ_ONLY -> {
                        OutlinedTextField(
                            value = currentValue,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(field.name) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    SmartFieldInputType.MULTILINE_TEXT -> {
                        OutlinedTextField(
                            value = currentValue,
                            onValueChange = { value -> smartFieldValues[field.name] = value },
                            label = { Text(field.name) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                    }
                    SmartFieldInputType.TEXT_TEMPLATE -> {
                        OutlinedTextField(
                            value = currentValue,
                            onValueChange = { value -> smartFieldValues[field.name] = value },
                            label = { Text(field.name) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                    }
                    SmartFieldInputType.FORMULA -> {
                        OutlinedTextField(
                            value = currentValue,
                            onValueChange = { value -> smartFieldValues[field.name] = value },
                            label = { Text(field.name) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            textStyle = TextStyle(
                                textDirection = TextDirection.Ltr,
                                textAlign = TextAlign.Start
                            )
                        )
                    }
                    SmartFieldInputType.TEXT -> {
                        OutlinedTextField(
                            value = currentValue,
                            onValueChange = { value -> smartFieldValues[field.name] = value },
                            label = { Text(field.name) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (selectedSmartTemplate.formulas.isNotEmpty()) {
                Text("חישובים חכמים")
                smartFormulaResults.forEach { result ->
                    val targetText = result.formula.targetFieldName
                    if (result.valueText != null) {
                        Text("${result.formula.name}: ${result.valueText}  ←  $targetText")
                    } else {
                        Text("${result.formula.name}: ${result.error ?: "לא ניתן לחשב"}")
                    }
                }
                Button(
                    onClick = {
                        val calculatedTargets = calculateSmartFormulaResults(selectedSmartTemplate, smartFieldValues, smartWll).first
                        if (calculatedTargets.isEmpty()) {
                            android.widget.Toast.makeText(context, "לא חושב ערך. בדוק שהשדות בנוסחה מולאו במספרים.", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            calculatedTargets.forEach { (target, value) ->
                                val field = smartFieldByPlaceholder(selectedSmartTemplate, target)
                                if (field != null) {
                                    smartFieldValues[field.name] = value
                                }
                                if (isSmartWllTargetName(target)) {
                                    smartWll = value
                                }
                            }
                            android.widget.Toast.makeText(context, "החישובים הועתקו לשדות היעד. אפשר עדיין לשנות ידנית.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("חשב והעתק תוצאות")
                }
            }

            if (selectedSmartTemplate != null &&
                findSmartFieldByKeywords(selectedSmartTemplate, listOf("קוטר", "עליון"), listOf("פתחעליון")) != null &&
                findSmartFieldByKeywords(selectedSmartTemplate, listOf("קוטר", "תחתון"), listOf("פתחתחתון")) != null &&
                findSmartFieldByKeywords(selectedSmartTemplate, listOf("גובה"), listOf("גובההדוד")) != null
            ) {
                Text("חישוב נפח וע.ע.ב לדוד")
                OutlinedTextField(
                    value = smartConcreteDensity,
                    onValueChange = { value ->
                        val filtered = value.filter { it.isDigit() || it == '.' || it == ',' }
                        val dotNormalized = filtered.replace(',', '.')
                        val parts = dotNormalized.split(".")
                        smartConcreteDensity = if (parts.size <= 2) {
                            if (parts.size == 2) parts[0] + "." + parts[1].take(2) else dotNormalized
                        } else smartConcreteDensity
                    },
                    label = { Text("משקל סגולי בטון נוזלי טון/מ״ק") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                if (smartConcreteCalculation != null) {
                    Text("נפח מחושב: ${smartConcreteCalculation.volumeText} מ״ק")
                    Text("ע.ע.ב מחושב מוצע: ${smartConcreteCalculation.suggestedWllText} טון")
                } else {
                    Text("הזן קוטר עליון, קוטר תחתון וגובה במ״מ כדי לחשב נפח וע.ע.ב מוצע.")
                }

                Button(
                    onClick = {
                        val calculation = calculateSmartConcreteVolumeAndWll(selectedSmartTemplate, smartFieldValues, smartConcreteDensity)
                        if (calculation == null) {
                            android.widget.Toast.makeText(
                                context,
                                "לא ניתן לחשב. ודא שקוטר עליון, קוטר תחתון וגובה הדוד הוזנו במ״מ.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        } else {
                            findSmartVolumeField(selectedSmartTemplate)?.let { volumeField ->
                                smartFieldValues[volumeField.name] = calculation.volumeText
                            }
                            smartWll = calculation.suggestedWllText
                            prefs.edit().putString("smart_concrete_density", smartConcreteDensity.ifBlank { "2.5" }).apply()
                            android.widget.Toast.makeText(
                                context,
                                "החישוב הועתק לנפח ולע.ע.ב. ניתן עדיין לשנות ידנית את ע.ע.ב.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("חשב והעתק לנפח ולע.ע.ב")
                }
            }

            OutlinedTextField(value = smartManufacturer, onValueChange = { smartManufacturer = it }, label = { Text("יצרן") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = smartModel,
                onValueChange = { value ->
                    smartModel = value
                    selectedSmartTemplate?.let { template ->
                        applyAccessoryManufacturerModelMemory(template.typeName, smartManufacturer.trim(), value.trim())
                    }
                },
                label = { Text("דגם") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = smartQuantity,
                onValueChange = { value -> smartQuantity = value.filter { it.isDigit() }.take(2) },
                label = { Text("כמות") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            if (smartQuantityInt > 0) {
                repeat(smartQuantityInt) { index ->
                    OutlinedTextField(
                        value = smartSerialNumbers[index],
                        onValueChange = { value ->
                            val filtered = value.filter { ch -> ch.isDigit() }
                            val candidateSerials = smartSerialNumbers.toMutableList().also { it[index] = filtered }
                            val duplicate = findDuplicateSerialForNewAccessory(
                                existingRows = reportAccessories,
                                newSerialNumbersText = candidateSerials.joinToString("\n")
                            )
                            if (duplicate != null) {
                                android.widget.Toast.makeText(context, duplicateSerialMessage(duplicate), android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                smartSerialNumbers[index] = filtered
                            }
                        },
                        label = { Text("מס' זיהוי ${index + 1}") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            OutlinedTextField(value = smartTestLoad, onValueChange = { smartTestLoad = it }, label = { Text("עומס מבחן") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = smartWll, onValueChange = { smartWll = it }, label = { Text("ע.ע.ב") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = smartDescription,
                onValueChange = {},
                readOnly = true,
                label = { Text("תיאור האביזר") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            if (smartDescription.isBlank()) {
                Text("שים לב: עדיין אין תבנית תיאור תקינה לסוג האביזר החכם.")
            } else if (hasUnfilledSmartPlaceholders(smartDescription)) {
                Text("שים לב: בתיאור עדיין נשארו משתנים בתוך { }. יש למלא את השדות החסרים או לבדוק ששמות השדות בתבנית זהים לשמות שהגדרת.")
            }
        }

        if (
            selectedAccessoryType == "מענב שרשרת" ||
            selectedAccessoryType == "מענב כבל פלדה" ||
            selectedAccessoryType == "רצועות הרמה" ||
            selectedAccessoryType == "אביזרי קצה" ||
            selectedSmartTemplate != null
        ) {
            Text("ליקוי")

            Button(
                onClick = { hasDefect = !hasDefect },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (hasDefect) "יש ליקוי: כן" else "יש ליקוי: לא")
            }

            if (hasDefect) {
                if (availableSerialsForNote.isNotEmpty()) {
                    Button(
                        onClick = { defectSerialDialogOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (selectedSerialForDefect.isBlank()) "שייך ליקוי למספר זיהוי (אופציונלי)"
                            else "מס׳ זיהוי לליקוי: $selectedSerialForDefect"
                        )
                    }
                }

                OutlinedTextField(
                    value = defectDescription,
                    onValueChange = { defectDescription = it },
                    label = { Text("ליקוי") },
                    modifier = Modifier.fillMaxWidth()
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (inspectionDate.isNotBlank()) {
                                defectFixUntilDialogOpen = true
                            }
                        }
                ) {
                    OutlinedTextField(
                        value = defectFixUntil,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("לביצוע עד") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Text("הערה לפי מספר זיהוי")

            Button(
                onClick = { hasSerialNote = !hasSerialNote },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (hasSerialNote) "יש הערה לפי מספר זיהוי: כן" else "יש הערה לפי מספר זיהוי: לא")
            }

            if (hasSerialNote) {
                Button(
                    onClick = { serialNoteDialogOpen = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (selectedSerialForNote.isBlank()) "בחר מספר זיהוי להערה"
                        else selectedSerialForNote
                    )
                }

                OutlinedTextField(
                    value = serialNoteText,
                    onValueChange = { serialNoteText = it },
                    label = { Text("טקסט הערה") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = addAccessoryButton@{
                    // קוד מלא 12:
                    // אם התסקיר כבר הופק ל-PDF, לא מאפשרים להוסיף אביזרים חדשים.
                    if (isLockedForNewAccessories) {
                        android.widget.Toast.makeText(
                            context,
                            "התסקיר נעול להוספת אביזרים לאחר הפקת PDF. ניתן להפיק גרסת PDF נוספת לאחר שינוי פרטים מותרים.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        return@addAccessoryButton
                    }

                    // 🛑 בדיקת מקום לפני הוספה.
                    // אם כבר יש 7 אביזרים בתסקיר, מציגים הודעה בלבד ולא מוחקים שום נתון.
                    // חשוב: הבדיקה נמצאת בתוך onClick של כפתור ההוספה, לפני reportAccessories.add.
                    if (reportAccessories.size >= 12) {
                        android.widget.Toast.makeText(
                            context,
                            "אין מקום נוסף בתסקיר זה. מקסימום 12 אביזרים. יש לשמור/להפיק את התסקיר ולעבור לתסקיר הבא.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        return@addAccessoryButton
                    }

                    val currentDescription =
                        when (selectedAccessoryType) {
                            "מענב שרשרת" -> chainDescription
                            "מענב כבל פלדה" -> wireDescription
                            "רצועות הרמה" -> textileDescription
                            "אביזרי קצה" -> endAccessoryDescription
                            else -> if (selectedSmartTemplate != null) smartDescription else ""
                        }

                    val currentManufacturer =
                        when (selectedAccessoryType) {
                            "מענב שרשרת" -> chainManufacturerFinal
                            "מענב כבל פלדה" -> wireManufacturerFinal
                            "רצועות הרמה" -> textileManufacturerFinal
                            "אביזרי קצה" -> omegaManufacturerFinal
                            else -> if (selectedSmartTemplate != null) smartManufacturer.trim() else ""
                        }

                    val currentModel =
                        when (selectedAccessoryType) {
                            "מענב שרשרת" -> chainModel
                            "מענב כבל פלדה" -> wireModel
                            "רצועות הרמה" -> textileModel
                            "אביזרי קצה" -> omegaModel
                            else -> if (selectedSmartTemplate != null) smartModel.trim() else ""
                        }

                    val currentQuantity =
                        when (selectedAccessoryType) {
                            "מענב שרשרת" -> chainQuantity
                            "מענב כבל פלדה" -> wireQuantity
                            "רצועות הרמה" -> textileQuantity
                            "אביזרי קצה" -> omegaQuantity
                            else -> if (selectedSmartTemplate != null) smartQuantity.trim() else ""
                        }

                    val currentSerials =
                        when (selectedAccessoryType) {
                            "מענב שרשרת" -> chainSerialNumbersText
                            "מענב כבל פלדה" -> wireSerialNumbersText
                            "רצועות הרמה" -> textileSerialNumbersText
                            "אביזרי קצה" -> omegaSerialNumbersText
                            else -> if (selectedSmartTemplate != null) smartSerialNumbersText else ""
                        }

                    val currentTestLoad =
                        when (selectedAccessoryType) {
                            "מענב שרשרת" -> chainTestLoad
                            "מענב כבל פלדה" -> wireTestLoad
                            "רצועות הרמה" -> textileTestLoad
                            "אביזרי קצה" -> omegaTestLoad
                            else -> if (selectedSmartTemplate != null) smartTestLoad.trim() else ""
                        }
                    val currentWll =
                        when (selectedAccessoryType) {
                            "מענב שרשרת" -> chainWll
                            "מענב כבל פלדה" -> wireWll
                            "רצועות הרמה" -> textileWll
                            "אביזרי קצה" -> omegaWll
                            else -> if (selectedSmartTemplate != null) smartWll.trim() else ""
                        }

                    // גרסה 062:
                    // לפני הוספת אביזר לטבלה בודקים שאין כפילות מספרי זיהוי
                    // בתוך האביזר החדש וגם מול האביזרים שכבר הוזנו בתסקיר הנוכחי.
                    findDuplicateSerialForNewAccessory(reportAccessories, currentSerials)?.let { duplicate ->
                        android.widget.Toast.makeText(
                            context,
                            duplicateSerialMessage(duplicate),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        return@addAccessoryButton
                    }

                    if (selectedSmartTemplate != null) {
                        val missingMessage = smartMissingFieldsMessage(
                            template = selectedSmartTemplate,
                            values = smartFieldValues,
                            quantityText = smartQuantity,
                            serialNumbers = smartSerialNumbers,
                            wllText = smartWll,
                            description = smartDescription,
                            formulaResults = smartFormulaResults
                        )
                        if (missingMessage.isNotBlank()) {
                            addAccessoryValidationMessage = missingMessage
                            return@addAccessoryButton
                        }
                    }

                    if (currentDescription.isBlank()) {
                        android.widget.Toast.makeText(
                            context,
                            "לא ניתן להוסיף אביזר לפני שנוצר תיאור. בדוק שתבנית התיאור מכילה שמות שדות בתוך { } ושמילאת את הערכים.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        return@addAccessoryButton
                    }

                    if (currentDescription.isNotBlank()) {
                        // אם הוזן יצרן חדש לאביזרי קצה דרך "אחר", שומרים אותו לרשימה לפעם הבאה.
                        if (selectedAccessoryType == "אביזרי קצה" && omegaManufacturerChoice == "אחר") {
                            val newMaker = omegaManufacturerOther.trim()
                            val allMakers = baseOmegaManufacturerOptions + customOmegaManufacturers
                            if (newMaker.isNotBlank() && !allMakers.contains(newMaker)) {
                                val updated = (customOmegaManufacturers + newMaker).distinct().sorted()
                                customOmegaManufacturers = updated
                                prefs.edit().putStringSet("custom_omega_manufacturers", updated.toSet()).apply()
                            }
                        }

                        // אם הוזן יצרן חדש למענב שרשרת דרך "אחר",
                        // שומרים אותו לרשימת היצרנים לפעם הבאה.
                        if (selectedAccessoryType == "מענב שרשרת" && chainManufacturerChoice == "אחר") {
                            val newMaker = chainManufacturerOther.trim()
                            val allMakers = baseChainManufacturerOptions + customChainManufacturers
                            if (newMaker.isNotBlank() && !allMakers.contains(newMaker)) {
                                val updated = (customChainManufacturers + newMaker).distinct().sorted()
                                customChainManufacturers = updated
                                prefs.edit().putStringSet("custom_chain_manufacturers", updated.toSet()).apply()
                            }
                        }

                        // אם הוזן יצרן חדש למענב כבל פלדה דרך "אחר",
                        // שומרים אותו לרשימת היצרנים לפעם הבאה.
                        if (selectedAccessoryType == "מענב כבל פלדה" && wireManufacturerChoice == "אחר") {
                            val newMaker = wireManufacturerOther.trim()
                            val allMakers = baseWireManufacturerOptions + customWireManufacturers
                            if (newMaker.isNotBlank() && !allMakers.contains(newMaker)) {
                                val updated = (customWireManufacturers + newMaker).distinct().sorted()
                                customWireManufacturers = updated
                                prefs.edit().putStringSet("custom_wire_manufacturers", updated.toSet()).apply()
                            }
                        }

                        // אם הוזן יצרן חדש לרצועות הרמה דרך "אחר",
                        // שומרים אותו לרשימת היצרנים לפעם הבאה.
                        if (selectedAccessoryType == "רצועות הרמה" && textileManufacturerChoice == "אחר") {
                            val newMaker = textileManufacturerOther.trim()
                            val allMakers = baseTextileManufacturerOptions + customTextileManufacturers
                            if (newMaker.isNotBlank() && !allMakers.contains(newMaker)) {
                                val updated = (customTextileManufacturers + newMaker).distinct().sorted()
                                customTextileManufacturers = updated
                                prefs.edit().putStringSet("custom_textile_manufacturers", updated.toSet()).apply()
                            }
                        }

                        // אם במענב כבל פלדה נבחר אביזר קצה נוסף מסוג "אחר",
                        // שומרים אותו לרשימה כדי שיופיע בפעמים הבאות.
                        if (selectedAccessoryType == "מענב כבל פלדה" && wireExtraEndChoice == "אחר") {
                            val newExtraEnd = wireExtraEndOther.trim()
                            val allExtraEnds = baseWireExtraEndOptions + customWireExtraEnds
                            if (newExtraEnd.isNotBlank() && !allExtraEnds.contains(newExtraEnd)) {
                                val updated = (customWireExtraEnds + newExtraEnd).distinct().sorted()
                                customWireExtraEnds = updated
                                prefs.edit().putStringSet("custom_wire_extra_ends", updated.toSet()).apply()
                            }
                        }

                        // אם במענב שרשרת נבחר אביזר קצה מסוג "אחר",
                        // שומרים אותו לרשימת אביזרי הקצה לפעם הבאה.
                        if (selectedAccessoryType == "מענב שרשרת") {
                            val newChainEnds = listOf(chainEnd1Other.trim(), chainEnd2Other.trim())
                                .filter { it.isNotBlank() }
                            if (newChainEnds.isNotEmpty()) {
                                val allChainEnds = baseChainEndOptions + customChainEndOptions
                                val valuesToAdd = newChainEnds.filter { !allChainEnds.contains(it) }
                                if (valuesToAdd.isNotEmpty()) {
                                    val updated = (customChainEndOptions + valuesToAdd).distinct().sorted()
                                    customChainEndOptions = updated
                                    prefs.edit().putStringSet("custom_chain_end_options", updated.toSet()).apply()
                                }
                            }
                        }

                        // אם באביזרי קצה נבחר "אחר",
                        // שומרים אותו לרשימת אביזרי הקצה העצמאיים לפעם הבאה.
                        if (selectedAccessoryType == "אביזרי קצה" && endAccessoryChoice == "אחר") {
                            val newEndAccessory = endAccessoryOther.trim()
                            val allEndAccessories = baseEndAccessoryOptions + customEndAccessoryOptions
                            if (newEndAccessory.isNotBlank() && !allEndAccessories.contains(newEndAccessory)) {
                                val updated = (customEndAccessoryOptions + newEndAccessory).distinct().sorted()
                                customEndAccessoryOptions = updated
                                prefs.edit().putStringSet("custom_end_accessory_options", updated.toSet()).apply()
                            }
                        }

                        saveAccessoryManufacturerModelMemory(
                            category = selectedSmartTemplate?.typeName ?: selectedAccessoryType,
                            manufacturer = currentManufacturer,
                            model = currentModel
                        )

                        reportAccessories.add(
                            ReportAccessoryRow(
                                description = currentDescription,
                                manufacturer = currentManufacturer,
                                model = currentModel,
                                quantity = currentQuantity,
                                serialNumbers = currentSerials,
                                testLoad = currentTestLoad,
                                wll = currentWll
                            )
                        )

                        if (hasDefect && defectDescription.isNotBlank()) {
                            val defectPrefix = if (selectedSerialForDefect.isNotBlank()) "באביזר $selectedSerialForDefect " else ""
                            reportDefects.add(
                                ReportDefectRow(
                                    defectDescription = "$defectPrefix$defectDescription",
                                    fixUntil = defectFixUntil
                                )
                            )
                        }

                        // אזהרה דינמית: כל שורת אביזר = 1 יחידה, כל ליקוי = 0.5 יחידה
                        // סף 9 יחידות → הדף עלול להיות מלא
                        val contentScore = reportAccessories.size.toFloat() + reportDefects.size.toFloat() * 0.5f
                        if (contentScore >= 9f) {
                            showCapacityWarning = true
                        }

                        if (
                            hasSerialNote &&
                            selectedSerialForNote.isNotBlank() &&
                            serialNoteText.isNotBlank()
                        ) {
                            reportNotes.add(
                                ReportNoteRow(
                                    text = "מס' זיהוי $selectedSerialForNote: $serialNoteText"
                                )
                            )
                        }

                        hasDefect = false
                        defectDescription = ""
                        defectFixUntil = ""
                        selectedSerialForDefect = ""
                        hasSerialNote = false
                        serialNoteText = ""
                        selectedSerialForNote = ""

                        chainBranches = ""
                        chainSize = ""
                        chainLength = ""
                        chainEnd1 = ""
                        chainEnd1Other = ""
                        chainEnd2 = ""
                        chainEnd2Other = ""
                        chainManufacturerChoice = ""
                        chainManufacturerOther = ""
                        chainModel = ""
                        chainQuantity = ""
                        chainTestLoad = ""
                        chainSerialNumbers.clear()

                        wireBranches = ""
                        wireDiameterMm = ""
                        wireLength = ""
                        wireEndType = ""
                        wireHasExtraEnd = false
                        wireExtraEndChoice = ""
                        wireExtraEndOther = ""
                        wireManufacturerChoice = ""
                        wireManufacturerOther = ""
                        wireModel = ""
                        wireQuantity = ""
                        wireTestLoad = ""
                        wireSerialNumbers.clear()

                        textileKind = ""
                        textileColorInput = ""
                        textileManufacturerChoice = ""
                        textileManufacturerOther = ""
                        textileModel = ""
                        textileQuantity = ""
                        textileTestLoad = ""
                        textileLength = ""
                        textileSerialNumbers.clear()

                        endAccessoryChoice = ""
                        endAccessoryOther = ""
                        omegaSize = ""
                        omegaManufacturerChoice = ""
                        omegaManufacturerOther = ""
                        omegaModel = ""
                        omegaQuantity = ""
                        omegaTestLoad = ""
                        omegaSerialNumbers.clear()

                        smartFieldValues.clear()
                        smartOtherListFieldNames.clear()
                        smartManufacturer = ""
                        smartModel = ""
                        smartQuantity = ""
                        smartTestLoad = ""
                        smartWll = ""
                        smartSerialNumbers.clear()

                        selectedAccessoryType = ""

                        scope.launch {
                            formScrollState.animateScrollTo(2000)
                        }
                    }
                },
                enabled = !isLockedForNewAccessories,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isLockedForNewAccessories) "הוספת אביזרים נעולה" else "הוסף אביזר לתסקיר")
            }
        }

        Text("טבלת אביזרים לעריכה")

        // קוד מלא 14:
        // במקום טבלת תצוגה בלבד, כל שורת אביזר מוצגת ככרטיס עריכה.
        // לפני הפקת PDF ניתן לערוך הכל, כולל כמות ומספרי זיהוי.
        // אחרי הפקת PDF ניתן לערוך שדות קיימים, אך אי אפשר להוסיף אביזרים,
        // לשנות כמות, או להוסיף/למחוק שורות של מספרי זיהוי.
        if (reportAccessories.isEmpty()) {
            Text("טרם נוספו אביזרים")
        } else {
            EditableAccessoryRows(
                rows = reportAccessories,
                isLockedForNewAccessories = isLockedForNewAccessories,
                photoCountForSerials = { serialsText ->
                    val serials = serialsText.lines().map { it.trim() }.filter { it.isNotBlank() }
                    reportPhotos.count { photo -> serials.contains(photo.serialNumber) }
                },
                onPhotoButtonClick = { accessoryIndex, row ->
                    val serials = row.serialNumbers.lines().map { it.trim() }.filter { it.isNotBlank() }
                    if (serials.isEmpty()) {
                        android.widget.Toast.makeText(
                            context,
                            "יש להזין מספר זיהוי לפני צילום תמונה.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } else {
                        photoDialogAccessoryIndex = accessoryIndex
                        photoDialogSerials = serials
                        photoDialogOpen = true
                    }
                }
            )
        }

        Text("טבלת ליקויים")

// כפתור זה זמין תמיד, גם אחרי הפקת PDF,
// כדי שאפשר יהיה לתעד ליקוי חדש או לרשום למשל:
// "הליקויים תוקנו" / "נוסף ליקוי לאחר בדיקה חוזרת".
        Button(
            onClick = {
                reportDefects.add(
                    ReportDefectRow(
                        defectDescription = "",
                        fixUntil = ""
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("הוסף שורת ליקוי / הערת תיקון")
        }

        if (reportDefects.isEmpty()) {
            Text("לא נוספו ליקויים")
        } else {
            DefectReportTable(rows = reportDefects)
            EditableDefectRows(rows = reportDefects)
        }

        // אזהרה — שדות חובה חסרים
        if (owner.isBlank() || inspectionDate.isBlank()) {
            val missingStr = buildList {
                if (owner.isBlank()) add("שם לקוח")
                if (inspectionDate.isBlank()) add("תאריך בדיקה")
            }.joinToString(", ")
            Text(
                text = "⚠ חסרים שדות חובה: $missingStr",
                color = Color(0xFFB71C1C),
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(
            onClick = {
                if (owner.isBlank()) {
                    android.widget.Toast.makeText(
                        context,
                        "שים לב: שם הלקוח לא הוזן — התסקיר יוצג ללא שם לקוח",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                val html = buildHtmlForCurrentReport(
                    context = context,
                    inspectorNumber = inspectorNumber,
                    inspectorFirstName = inspectorFirstName,
                    inspectorLastName = inspectorLastName,
                    inspectorCertificateNumber = inspectorNumber,
                    runningNumber = runningNumber,
                    inspectionDate = inspectionDate,
                    nextInspectionDate = nextInspectionDate,
                    owner = owner,
                    address = address,
                    phone = phone,
                    contactPerson = contactPerson,
                    vehicle = vehicle,
                    inspectionLocation = inspectionLocation,
                    inspectionPlaceType = inspectionPlaceType,
                    fixedNote = fixedNote,
                    generalNote = if (hasGeneralNote) generalNoteText else "",
                    reportAccessories = reportAccessories,
                    reportDefects = reportDefects,
                    reportNotes = reportNotes,
                    compact = compactPdf
                )

                // הצגת PDF לבדיקה בלבד: לא נועל את התסקיר ולא יוצר גרסת R6000.1.
                requestPrintHtml(
                    html = html,
                    fileName = "תצוגה_${runningNumber.ifBlank { "בדיקה" }}"
                )
            },
            enabled = reportAccessories.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("הצג תסקיר PDF")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Checkbox(checked = compactPdf, onCheckedChange = { compactPdf = it })
            Text("כווץ לעמוד אחד (גופן קטן)")
        }

        Button(
            onClick = {
                val missing = buildList {
                    if (owner.isBlank()) add("שם לקוח")
                    if (inspectionDate.isBlank()) add("תאריך בדיקה")
                }
                if (missing.isNotEmpty()) {
                    android.widget.Toast.makeText(
                        context,
                        "לא ניתן להפיק תסקיר — חסרים: ${missing.joinToString(", ")}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@Button
                }
                confirmPdfDialogOpen = true
            },
            enabled = reportAccessories.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLockedForNewAccessories) "הפקת גרסת PDF נוספת" else "הפקת תסקיר PDF סופי")
        }

        Button(
            onClick = {
                val duplicateSerial = findDuplicateSerialsInRows(reportAccessories)
                if (duplicateSerial != null) {
                    android.widget.Toast.makeText(
                        context,
                        duplicateSerialMessage(duplicateSerial),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@Button
                }
                val html = buildHtmlForCurrentReport(
                    context = context,
                    inspectorNumber = inspectorNumber,
                    inspectorFirstName = inspectorFirstName,
                    inspectorLastName = inspectorLastName,
                    inspectorCertificateNumber = inspectorNumber,
                    runningNumber = runningNumber,
                    inspectionDate = inspectionDate,
                    nextInspectionDate = nextInspectionDate,
                    owner = owner,
                    address = address,
                    phone = phone,
                    contactPerson = contactPerson,
                    vehicle = vehicle,
                    inspectionLocation = inspectionLocation,
                    inspectionPlaceType = inspectionPlaceType,
                    fixedNote = fixedNote,
                    generalNote = if (hasGeneralNote) generalNoteText else "",
                    reportAccessories = reportAccessories,
                    reportDefects = reportDefects,
                    reportNotes = reportNotes,
                    compact = compactPdf
                )

                val workingReport = buildWorkingReportForStorage(
                    inspectorNumber = inspectorNumber,
                    runningNumber = runningNumber,
                    inspectorFirstName = inspectorFirstName,
                    inspectorLastName = inspectorLastName,
                    inspectorCertificateNumber = inspectorNumber,
                    inspectionDate = inspectionDate,
                    nextInspectionDate = nextInspectionDate,
                    owner = owner,
                    address = address,
                    phone = phone,
                    contactPerson = contactPerson,
                    vehicle = vehicle,
                    inspectionLocation = inspectionLocation,
                    inspectionPlaceType = inspectionPlaceType,
                    fixedNote = fixedNote,
                    generalNote = if (hasGeneralNote) generalNoteText else "",
                    reportAccessories = reportAccessories,
                    reportDefects = reportDefects,
                    reportNotes = reportNotes,
                    html = html,
                    isLockedForNewAccessories = isLockedForNewAccessories,
                    site = selectedSiteId
                )

                val noteTexts = reportNotes.map { it.text }
                scope.launch {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        saveAccessoryClientIfNeeded(
                            clientMemoryStore = clientMemoryStore,
                            owner = owner,
                            address = address,
                            phone = phone,
                            contactPerson = contactPerson,
                            saveClientToMemory = saveClientToMemory,
                            saveAddress = saveClientAddressToMemory,
                            savePhone = saveClientPhoneToMemory,
                            saveContact = saveClientContactToMemory
                        )
                        ReportStorage.saveWorkingReport(context, workingReport)
                        InspectorSettingsStorage.saveTemplateNotesList(context, noteTexts)
                    }
                    onSaveReport(
                        com.nasavi.liftinginspectorpro.SavedInspection(
                            reportNumber = runningNumber,
                            date = inspectionDate,
                            client = owner,
                            summary = "רשומת עבודה לעריכה | אביזרים: ${reportAccessories.size}"
                        ),
                        !isEditingExistingReport
                    )
                }
            },
            enabled = reportAccessories.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isEditingExistingReport) "שמור שינויים" else "שמור תסקיר")
        }

        Button(
            onClick = {
                onBackToHome()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("יציאה ללא שמירה")
        }

        Text("הערות")

        OutlinedTextField(
            value = fixedNote,
            onValueChange = {},
            readOnly = true,
            label = { Text("הערה קבועה") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )

        if (hasGeneralNote && generalNoteText.isNotBlank()) {
            OutlinedTextField(
                value = "הערה כללית: $generalNoteText",
                onValueChange = {},
                readOnly = true,
                label = { Text("הערה כללית") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        }

        if (reportNotes.isEmpty()) {
            Text("לא נוספו הערות לפי מספר זיהוי")
        } else {
            reportNotes.forEachIndexed { index, note ->
                OutlinedTextField(
                    value = "הערה ${index + 1}: ${note.text}",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        }
    }


    if (confirmPdfDialogOpen) {
        AlertDialog(
            onDismissRequest = { if (!pdfGenerationInProgress) confirmPdfDialogOpen = false },
            title = { Text("אישור הפקת PDF") },
            text = {
                Text(
                    if (isLockedForNewAccessories) {
                        "התסקיר כבר נעול להוספת אביזרים. האם להפיק גרסת PDF נוספת עם השינויים האחרונים?"
                    } else {
                        "האם אתה בטוח שברצונך להפיק תסקיר PDF? לאחר ההפקה לא ניתן יהיה להוסיף אביזרים חדשים לטבלת האביזרים בתסקיר זה. ניתן יהיה לפתוח את התסקיר שוב, לעדכן פרטים מותרים ולהפיק גרסאות PDF נוספות."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (pdfGenerationInProgress) return@TextButton

                        // גרסה 062:
                        // שכבת הגנה אחרונה לפני הפקת PDF. גם אם כפילות נכנסה בדרך כלשהי,
                        // התסקיר לא יופק לפני תיקון מספרי הזיהוי.
                        findDuplicateSerialsInRows(reportAccessories)?.let { duplicate ->
                            android.widget.Toast.makeText(
                                context,
                                duplicateSerialMessage(duplicate),
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            return@TextButton
                        }

                        pdfGenerationInProgress = true
                        // העבודה הכבדה (בניית HTML, שמירה) עוברת ל-IO thread כדי למנוע ANR
                        val snapshotInspectorNumber = inspectorNumber
                        val snapshotRunningNumber = runningNumber
                        val snapshotFirstName = inspectorFirstName
                        val snapshotLastName = inspectorLastName
                        val snapshotInspectionDate = inspectionDate
                        val snapshotNextInspectionDate = nextInspectionDate
                        val snapshotOwner = owner
                        val snapshotAddress = address
                        val snapshotPhone = phone
                        val snapshotContactPerson = contactPerson
                        val snapshotVehicle = vehicle
                        val snapshotInspectionLocation = inspectionLocation
                        val snapshotInspectionPlaceType = inspectionPlaceType
                        val snapshotFixedNote = fixedNote
                        val snapshotGeneralNote = if (hasGeneralNote) generalNoteText else ""
                        val snapshotAccessories = reportAccessories.toList()
                        val snapshotDefects = reportDefects.toList()
                        val snapshotNotes = reportNotes.toList()
                        val snapshotSiteId = selectedSiteId
                        val isEditing = editingReport != null
                        scope.launch {
                        try {
                        data class PdfResult(val htmlForVersion: String, val versionName: String)
                        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            // HTML ייבנה פעם אחת בלבד לגרסת ה-PDF — מונע החזקת שתי מחרוזות ~8MB בו-זמנית
                            val workingReport = buildWorkingReportForStorage(
                                inspectorNumber = snapshotInspectorNumber,
                                runningNumber = snapshotRunningNumber,
                                inspectorFirstName = snapshotFirstName,
                                inspectorLastName = snapshotLastName,
                                inspectorCertificateNumber = snapshotInspectorNumber,
                                inspectionDate = snapshotInspectionDate,
                                nextInspectionDate = snapshotNextInspectionDate,
                                owner = snapshotOwner,
                                address = snapshotAddress,
                                phone = snapshotPhone,
                                contactPerson = snapshotContactPerson,
                                vehicle = snapshotVehicle,
                                inspectionLocation = snapshotInspectionLocation,
                                inspectionPlaceType = snapshotInspectionPlaceType,
                                fixedNote = snapshotFixedNote,
                                generalNote = snapshotGeneralNote,
                                reportAccessories = snapshotAccessories,
                                reportDefects = snapshotDefects,
                                reportNotes = snapshotNotes,
                                html = "",
                                isLockedForNewAccessories = true,
                                site = snapshotSiteId
                            )
                            saveAccessoryClientIfNeeded(
                                clientMemoryStore = clientMemoryStore,
                                owner = snapshotOwner,
                                address = snapshotAddress,
                                phone = snapshotPhone,
                                contactPerson = snapshotContactPerson,
                                saveClientToMemory = saveClientToMemory,
                                saveAddress = saveClientAddressToMemory,
                                savePhone = saveClientPhoneToMemory,
                                saveContact = saveClientContactToMemory
                            )
                            ReportStorage.saveWorkingReport(context, workingReport)
                            val pdfVersionName = ReportStorage.getNextPdfVersionName(
                                context = context,
                                baseReportNumber = workingReport.reportNumber
                            )
                            val htmlForPdfVersion = buildHtmlForCurrentReport(
                                context = context,
                                inspectorNumber = snapshotInspectorNumber,
                                runningNumber = pdfVersionName,
                                inspectorFirstName = snapshotFirstName,
                                inspectorLastName = snapshotLastName,
                                inspectorCertificateNumber = snapshotInspectorNumber,
                                inspectionDate = snapshotInspectionDate,
                                nextInspectionDate = snapshotNextInspectionDate,
                                owner = snapshotOwner,
                                address = snapshotAddress,
                                phone = snapshotPhone,
                                contactPerson = snapshotContactPerson,
                                vehicle = snapshotVehicle,
                                inspectionLocation = snapshotInspectionLocation,
                                inspectionPlaceType = snapshotInspectionPlaceType,
                                fixedNote = snapshotFixedNote,
                                generalNote = snapshotGeneralNote,
                                reportAccessories = snapshotAccessories,
                                reportDefects = snapshotDefects,
                                reportNotes = snapshotNotes,
                                compact = compactPdf
                            )
                            val workingReportForPdfVersion = workingReport.copy(html = htmlForPdfVersion)
                            val pdfVersion = ReportStorage.saveNewPdfVersion(
                                context = context,
                                report = workingReportForPdfVersion
                            )
                            PdfResult(htmlForPdfVersion, pdfVersion.versionName)
                        }
                        // חזרנו ל-main thread — WebView חייב לרוץ על main thread
                        isLockedForNewAccessories = true
                        confirmPdfDialogOpen = false
                        requestPrintHtml(
                            html = result.htmlForVersion,
                            fileName = "תסקיר_${result.versionName}",
                            savedPdfVersionName = result.versionName,
                            afterPrintSavedInspection = com.nasavi.liftinginspectorpro.SavedInspection(
                                reportNumber = snapshotRunningNumber,
                                date = snapshotInspectionDate,
                                client = snapshotOwner,
                                summary = "תסקיר הופק ל-PDF | נעול להוספת אביזרים | אביזרים: ${snapshotAccessories.size}"
                            ),
                            afterPrintShouldAdvance = !isEditing
                        )
                        pdfGenerationInProgress = false
                        } catch (e: Throwable) {
                            pdfGenerationInProgress = false
                            confirmPdfDialogOpen = false
                            // PDF לא נוצר — אין נעילה
                            isLockedForNewAccessories = false
                            android.widget.Toast.makeText(
                                context,
                                "שגיאה בהפקת התסקיר: ${e.message ?: "לא ידועה"}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        } // end scope.launch

                    }
                ) {
                    Text(if (pdfGenerationInProgress) "מפיק..." else "כן, הפק PDF סופי")
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!pdfGenerationInProgress) confirmPdfDialogOpen = false }) {
                    Text("ביטול")
                }
            }
        )
    }

    if (photoDialogOpen) {
        AlertDialog(
            onDismissRequest = { photoDialogOpen = false },
            title = { Text("תמונות לאביזר ${photoDialogAccessoryIndex + 1}") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("בחר מספר זיהוי שאליו תשויך התמונה:")

                    photoDialogSerials.forEach { serial ->
                        Button(
                            onClick = {
                                val uri = createInspectionPhotoUri(
                                    context = context,
                                    reportNumber = runningNumber,
                                    serialNumber = serial
                                )
                                pendingPhotoSerial = serial
                                pendingPhotoUri = uri
                                photoDialogOpen = false
                                takePictureLauncher.launch(uri)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("צלם תמונה למספר זיהוי $serial")
                        }
                    }

                    val existingPhotosForAccessory = reportPhotos.filter { photo ->
                        photoDialogSerials.contains(photo.serialNumber)
                    }

                    if (existingPhotosForAccessory.isNotEmpty()) {
                        Text("תמונות קיימות:")
                        existingPhotosForAccessory.forEachIndexed { index, photo ->
                            TextButton(
                                onClick = { openPhotoInExternalViewer(context, photo.uri) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("הצג תמונה ${index + 1} — מס' זיהוי ${photo.serialNumber}")
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { photoDialogOpen = false }) { Text("סגור") }
            }
        )
    }

    if (printPhotoQuestionOpen) {
        AlertDialog(
            onDismissRequest = { printPhotoQuestionOpen = false },
            title = { Text("צירוף תמונות לתסקיר") },
            text = {
                Text("קיימות ${reportPhotos.size} תמונות המשויכות למספרי הזיהוי בתסקיר. האם לצרף אותן כנספח אחרי התסקיר? שתי תמונות יודפסו בכל עמוד.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val htmlWithPhotos = appendPhotosToReportHtml(
                            html = pendingPrintHtml,
                            context = context,
                            photos = reportPhotos
                        )

                        // עדכון 013:
                        // אם זו הפקת PDF סופית, כבר שמרנו לפני כן גרסת PDF ללא תמונות.
                        // עכשיו, אחרי שהמשתמש אישר לצרף תמונות, מעדכנים את אותה גרסה שמורה
                        // עם ה-HTML המלא שכולל גם את נספח התמונות.
                        // כך פתיחה מרשומות שמורות תציג בדיוק את מה שהודפס בפועל.
                        val versionNameToUpdate = pendingSavedPdfVersionName
                        if (versionNameToUpdate.isNotBlank()) {
                            ReportStorage.updatePdfVersionHtml(
                                context = context,
                                versionName = versionNameToUpdate,
                                html = htmlWithPhotos
                            )
                        }

                        printPhotoQuestionOpen = false
                        pendingSavedPdfVersionName = ""

                        pdfPrintLoading = true
                        saveHtmlAsPdfAndOpen(context = context, htmlContent = htmlWithPhotos, fileName = pendingPrintFileName, onReady = { file ->
                            pdfPrintLoading = false
                            pendingReadyPdfFile = file
                        })
                    }
                ) {
                    Text("כן, צרף תמונות")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        // קוד מלא 21:
                        // אם המשתמש בחר לא לצרף תמונות, משאירים את גרסת ה-PDF השמורה כפי שהיא:
                        // HTML של התסקיר בלבד, ללא נספח תמונות.
                        printPhotoQuestionOpen = false
                        pendingSavedPdfVersionName = ""

                        pdfPrintLoading = true
                        saveHtmlAsPdfAndOpen(context = context, htmlContent = pendingPrintHtml, fileName = pendingPrintFileName, onReady = { file ->
                            pdfPrintLoading = false
                            pendingReadyPdfFile = file
                        })
                    }
                ) {
                    Text("לא, הדפס בלי תמונות")
                }
            }
        )
    }

    if (addAccessoryValidationMessage.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { addAccessoryValidationMessage = "" },
            title = { Text("חסרים נתונים להוספת אביזר") },
            text = {
                Text("יש להשלים את השדות הבאים:\n\n$addAccessoryValidationMessage")
            },
            confirmButton = {
                TextButton(onClick = { addAccessoryValidationMessage = "" }) { Text("הבנתי") }
            }
        )
    }


    if (saveAccessoryTemplateDialogOpen) {
        val isEditingAccessoryTemplate = editingAccessoryTemplateOriginal != null
        AlertDialog(
            onDismissRequest = {
                saveAccessoryTemplateDialogOpen = false
                editingAccessoryTemplateOriginal = null
            },
            title = {
                Text(if (isEditingAccessoryTemplate) "עריכת תבנית אביזר" else "שמירת תבנית אביזר")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("התבנית תשמור רק נתוני מבנה קבועים. אורך, כמות, מספרי זיהוי, ליקויים ותמונות לא יישמרו.")
                    if (isEditingAccessoryTemplate) {
                        Text("עדכון התבנית לא ישנה תסקירים שכבר הופקו.")
                    }
                    OutlinedTextField(
                        value = accessoryTemplateNameDraft,
                        onValueChange = { accessoryTemplateNameDraft = it },
                        label = { Text("שם תבנית") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { saveCurrentAccessoryReusableTemplate() }) {
                    Text(if (isEditingAccessoryTemplate) "עדכן תבנית" else "שמור")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        saveAccessoryTemplateDialogOpen = false
                        editingAccessoryTemplateOriginal = null
                    }
                ) {
                    Text("ביטול")
                }
            }
        )
    }

    if (loadAccessoryTemplateDialogOpen) {
        val templatesForSelectedAccessory = reusableAccessoryTemplates
            .filter { it.accessoryType == selectedAccessoryType }
            .sortedBy { it.name }

        AlertDialog(
            onDismissRequest = { loadAccessoryTemplateDialogOpen = false },
            title = { Text("ניהול תבניות אביזר") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("בחר תבנית. לאחר טעינה יש להשלים ידנית אורך, כמות ומספרי זיהוי.")
                    templatesForSelectedAccessory.forEach { template ->
                        Text(template.name)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    applyAccessoryReusableTemplate(template)
                                    loadAccessoryTemplateDialogOpen = false
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("טען")
                            }
                            Button(
                                onClick = { startEditingAccessoryReusableTemplate(template) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("ערוך")
                            }
                            Button(
                                onClick = { deleteAccessoryTemplateCandidate = template },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("מחק")
                            }
                        }
                    }
                    if (templatesForSelectedAccessory.isEmpty()) {
                        Text("אין תבניות שמורות לסוג האביזר הנוכחי.")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { loadAccessoryTemplateDialogOpen = false }) {
                    Text("סגור")
                }
            }
        )
    }

    deleteAccessoryTemplateCandidate?.let { templateToDelete ->
        AlertDialog(
            onDismissRequest = { deleteAccessoryTemplateCandidate = null },
            title = { Text("מחיקת תבנית אביזר") },
            text = {
                Text("האם למחוק את התבנית '${templateToDelete.name}'?\n\nפעולה זו מוחקת רק את התבנית ואינה מוחקת תסקירים קיימים.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteAccessoryReusableTemplate(templateToDelete)
                        loadAccessoryTemplateDialogOpen = false
                    }
                ) {
                    Text("מחק")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteAccessoryTemplateCandidate = null }) {
                    Text("ביטול")
                }
            }
        )
    }


    smartSelectedDateFieldName?.let { dateFieldName ->
        val initialMillis = parseDateToMillisOrToday(smartFieldValues[dateFieldName].orEmpty())
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { smartSelectedDateFieldName = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedMillis ->
                            smartFieldValues[dateFieldName] = formatLocalDate(millisToLocalDate(selectedMillis))
                        }
                        smartSelectedDateFieldName = null
                    }
                ) { Text("אישור") }
            },
            dismissButton = {
                TextButton(onClick = { smartSelectedDateFieldName = null }) { Text("ביטול") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (inspectionPlaceDialogOpen) {
        AlertDialog(
            onDismissRequest = { inspectionPlaceDialogOpen = false },
            title = { Text("הציוד נבדק ב") },
            text = {
                SelectionDialogColumn(items = inspectionPlaceOptions) { item ->
                    inspectionPlaceType = item
                    inspectionPlaceDialogOpen = false
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { inspectionPlaceDialogOpen = false }) { Text("סגור") }
            }
        )
    }


    if (showCapacityWarning) {
        AlertDialog(
            onDismissRequest = { showCapacityWarning = false },
            title = { Text("⚠️ הדף עשוי להיות מלא") },
            text = {
                Text(
                    "התסקיר מכיל ${reportAccessories.size} שורות אביזרים" +
                    (if (reportDefects.isNotEmpty()) " ו-${reportDefects.size} ליקויים" else "") +
                    ".\n\nמומלץ להפיק תסקיר זה ולהמשיך את הבדיקות הנוספות בתסקיר חדש.\n\nאו: הסר את השורה האחרונה שהוספת ואז הפק.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Right
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showCapacityWarning = false }) {
                    Text("הבנתי, הוסף בכל זאת")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    reportAccessories.removeLastOrNull()
                    showCapacityWarning = false
                }) {
                    Text("הסר שורה אחרונה")
                }
            }
        )
    }

    if (accessoryDialogOpen) {
        AlertDialog(
            onDismissRequest = { accessoryDialogOpen = false },
            title = { Text("בחר סוג אביזר") },
            text = {
                SelectionDialogColumn(items = accessoryTypes) { item ->
                    if (item == "אחר") {
                        accessoryDialogOpen = false
                        smartBuilderEditingOriginalTypeName = null
                        smartBuilderTypeName = ""
                        smartBuilderDescriptionTemplate = ""
                        smartBuilderFields.clear()
                        smartBuilderFields.add(SmartFieldDraft())
                        smartBuilderFormulas.clear()
                        smartBuilderMessage = ""
                        addAccessoryDialogOpen = true
                    } else {
                        selectedAccessoryType = item
                        smartFieldValues.clear()
                        smartOtherListFieldNames.clear()
                        smartManufacturer = ""
                        smartModel = ""
                        smartQuantity = ""
                        smartTestLoad = ""
                        smartWll = ""
                        smartSerialNumbers.clear()
                        accessoryDialogOpen = false
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { accessoryDialogOpen = false }) { Text("סגור") }
            }
        )
    }

    if (addAccessoryDialogOpen) {
        AlertDialog(
            onDismissRequest = { addAccessoryDialogOpen = false },
            title = { Text(if (smartBuilderEditingOriginalTypeName == null) "הוספה חכמה של סוג אביזר" else "עריכת טופס חכם") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("שלב 1: שם סוג האביזר")
                    OutlinedTextField(
                        value = smartBuilderTypeName,
                        onValueChange = { smartBuilderTypeName = it },
                        label = { Text("שם סוג אביזר חדש") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("שלב 2: שדות משתנים")
                    Text("עדכון 076: רשימת סוגי השדות אוחדה עם מכונות. סוג 'טקסט באנגלית / אותיות גדולות' הוסר, כי טקסט חופשי מספיק.")
                    smartBuilderFields.forEachIndexed { index, field ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color.LightGray)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("שדה ${index + 1}")
                            OutlinedTextField(
                                value = field.name,
                                onValueChange = { value -> smartBuilderFields[index] = field.copy(name = value) },
                                label = { Text("שם השדה") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("טיפ: אפשר לרשום קיצור באנגלית בתחילת השם, למשל: Z (מומנט התנגדות cm^3). בתיאור/נוסחה אפשר להשתמש ב-{Z}.")
                            Button(
                                onClick = {
                                    val types = SmartFieldInputType.values()
                                    val nextIndex = (types.indexOf(field.inputType) + 1) % types.size
                                    smartBuilderFields[index] = field.copy(inputType = types[nextIndex])
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("סוג קלט: ${field.inputType.label}") }

                            if (field.inputType == SmartFieldInputType.LIST) {
                                OutlinedTextField(
                                    value = field.optionsText,
                                    onValueChange = { value -> smartBuilderFields[index] = field.copy(optionsText = value) },
                                    label = { Text("אפשרויות לרשימה - כל אפשרות בשורה נפרדת") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 3
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Checkbox(
                                    checked = field.isRequired,
                                    onCheckedChange = { smartBuilderFields[index] = field.copy(isRequired = it) }
                                )
                                Text("שדה חובה", modifier = Modifier.weight(1f))
                                Checkbox(
                                    checked = field.isMemoryKey,
                                    onCheckedChange = { smartBuilderFields[index] = field.copy(isMemoryKey = it) }
                                )
                                Text("מפתח זיכרון", modifier = Modifier.weight(1f))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = field.inDescription,
                                    onCheckedChange = { smartBuilderFields[index] = field.copy(inDescription = it) }
                                )
                                Text("הכנס לתיאור")
                            }
                            if (smartBuilderFields.size > 1) {
                                TextButton(onClick = { smartBuilderFields.removeAt(index) }) { Text("מחק שדה") }
                            }
                        }
                    }
                    Button(onClick = { smartBuilderFields.add(SmartFieldDraft()) }, modifier = Modifier.fillMaxWidth()) {
                        Text("הוסף שדה")
                    }

                    Text("שלב 3: תבנית תיאור")
                    Text("כתוב טקסט קבוע ושלב שמות שדות בתוך { }, לדוגמה: {אורך הקורה}")
                    OutlinedTextField(
                        value = smartBuilderDescriptionTemplate,
                        onValueChange = { smartBuilderDescriptionTemplate = it },
                        label = { Text("תבנית תיאור האביזר") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4
                    )
                    val fieldNames = smartBuilderFields.map { smartDisplayFieldToken(it) }.filter { it.isNotBlank() }
                    if (fieldNames.isNotEmpty()) {
                        Text("שדות זמינים: ${fieldNames.joinToString("  ")}")
                    }

                    Text("שלב 4: חישובים חכמים - אופציונלי")
                    Text("אפשר להשתמש ב- + - * / ^ וסוגריים. משתנים כותבים בתוך { }, לדוגמה: {קוטר}/1000")
                    smartBuilderFormulas.forEachIndexed { index, formula ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color.LightGray)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("חישוב ${index + 1}")
                            OutlinedTextField(
                                value = formula.name,
                                onValueChange = { value -> smartBuilderFormulas[index] = formula.copy(name = value) },
                                label = { Text("שם החישוב") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = formula.expression,
                                onValueChange = { value -> smartBuilderFormulas[index] = formula.copy(expression = value) },
                                label = { Text("נוסחה") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                textStyle = TextStyle(
                                    textDirection = TextDirection.Ltr,
                                    textAlign = TextAlign.Start
                                )
                            )
                            OutlinedTextField(
                                value = formula.targetFieldName,
                                onValueChange = { value -> smartBuilderFormulas[index] = formula.copy(targetFieldName = value) },
                                label = { Text("שדה יעד לתוצאה, למשל: נפח כללי, שדה תוצאה או ע.ע.ב") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = formula.digitsText,
                                onValueChange = { value -> smartBuilderFormulas[index] = formula.copy(digitsText = value.filter { it.isDigit() }.take(1)) },
                                label = { Text("מספר ספרות אחרי הנקודה") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            TextButton(onClick = { smartBuilderFormulas.removeAt(index) }) { Text("מחק חישוב") }
                        }
                    }
                    Button(onClick = { smartBuilderFormulas.add(SmartFormulaDraft()) }, modifier = Modifier.fillMaxWidth()) {
                        Text("הוסף חישוב")
                    }

                    if (smartBuilderMessage.isNotBlank()) Text(smartBuilderMessage)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val typeName = smartBuilderTypeName.trim()
                        val fields = smartBuilderFields.mapNotNull { draft ->
                            val name = draft.name.trim()
                            if (name.isBlank()) return@mapNotNull null
                            val options = draft.optionsText.lines().map { it.trim() }.filter { it.isNotBlank() }.distinct()
                            SmartAccessoryFieldDefinition(name, draft.inputType, options, draft.isRequired, draft.isMemoryKey, draft.inDescription, draft.isSeparateColumn, draft.isForCalculation, draft.isScannable, draft.defaultValue)
                        }
                        val formulas = smartBuilderFormulas.mapNotNull { draft ->
                            val name = draft.name.trim()
                            val expression = draft.expression.trim()
                            val target = draft.targetFieldName.trim()
                            val digits = draft.digitsText.toIntOrNull()?.coerceIn(0, 4) ?: 2
                            if (name.isBlank() && expression.isBlank() && target.isBlank()) return@mapNotNull null
                            if (name.isBlank() || expression.isBlank() || target.isBlank()) {
                                return@mapNotNull SmartFormulaDefinition("", "", "", digits)
                            }
                            SmartFormulaDefinition(name, expression, target, digits)
                        }
                        val editingOriginal = smartBuilderEditingOriginalTypeName
                        val allExisting = (baseAccessoryTypes + customAccessoryTypes + smartAccessoryTemplates.map { it.typeName })
                            .filter { it != editingOriginal }
                        when {
                            typeName.isBlank() -> smartBuilderMessage = "יש להזין שם סוג אביזר."
                            allExisting.contains(typeName) -> smartBuilderMessage = "סוג אביזר בשם זה כבר קיים."
                            fields.isEmpty() -> smartBuilderMessage = "יש להגדיר לפחות שדה אחד."
                            smartBuilderDescriptionTemplate.isBlank() -> smartBuilderMessage = "יש להגדיר תבנית תיאור."
                            formulas.any { it.name.isBlank() || it.expression.isBlank() || it.targetFieldName.isBlank() } ->
                                smartBuilderMessage = "בחישוב חכם יש למלא שם, נוסחה ושדה יעד, או למחוק את החישוב הריק."
                            else -> {
                                val newTemplate = SmartAccessoryTemplate(typeName, smartBuilderDescriptionTemplate.trim(), fields, formulas)
                                val templatesWithoutOld = smartAccessoryTemplates.filter { it.typeName != editingOriginal && it.typeName != typeName }
                                val updatedTemplates = sortSmartTemplates(templatesWithoutOld + newTemplate)
                                smartAccessoryTemplates = updatedTemplates
                                saveSmartAccessoryTemplates(prefs, updatedTemplates)

                                val customWithoutOld = customAccessoryTypes.filter { it != editingOriginal && it != typeName }
                                val updatedCustomTypes = (customWithoutOld + typeName).distinct().sorted()
                                customAccessoryTypes = updatedCustomTypes
                                prefs.edit().putStringSet("custom_accessory_types", updatedCustomTypes.toSet()).apply()

                                selectedAccessoryType = typeName

                                // אם מדובר בעריכת טופס חכם קיים — לא מאפסים את נתוני האביזר שכבר הוזנו.
                                // כך אפשר לתקן שגיאת כתיב בתבנית/בנוסחה בלי לבנות מחדש ובלי לאבד יצרן, דגם, כמות, זיהויים וערכים שכבר מולאו.
                                if (editingOriginal == null) {
                                    smartFieldValues.clear()
                                    smartOtherListFieldNames.clear()
                                    smartManufacturer = ""
                                    smartModel = ""
                                    smartQuantity = ""
                                    smartTestLoad = ""
                                    smartWll = ""
                                    smartSerialNumbers.clear()
                                } else {
                                    val validFieldNames = fields.map { it.name }.toSet()
                                    val keysToRemove = smartFieldValues.keys.filter { it !in validFieldNames }
                                    keysToRemove.forEach { smartFieldValues.remove(it) }
                                    smartOtherListFieldNames.removeAll { it !in validFieldNames }
                                }

                                smartBuilderEditingOriginalTypeName = null
                                smartBuilderTypeName = ""
                                smartBuilderDescriptionTemplate = ""
                                smartBuilderFields.clear()
                                smartBuilderFields.add(SmartFieldDraft())
                                smartBuilderFormulas.clear()
                                smartBuilderMessage = ""
                                addAccessoryDialogOpen = false
                            }
                        }
                    }
                ) { Text(if (smartBuilderEditingOriginalTypeName == null) "שמור סוג אביזר" else "שמור תיקון טופס") }
            },
            dismissButton = { TextButton(onClick = { addAccessoryDialogOpen = false; smartBuilderEditingOriginalTypeName = null }) { Text("ביטול") } }
        )
    }

    val selectedListFieldName = smartSelectedListFieldName
    if (selectedListFieldName != null) {
        val field = selectedSmartTemplate?.fields?.firstOrNull { it.name == selectedListFieldName }
        if (field != null) {
            AlertDialog(
                onDismissRequest = { smartSelectedListFieldName = null },
                title = { Text("בחר ${field.name}") },
                text = {
                    SelectionDialogColumn(items = field.options + listOf("אחר")) { item ->
                        if (item == "אחר") {
                            smartFieldValues[field.name] = ""
                            if (!smartOtherListFieldNames.contains(field.name)) {
                                smartOtherListFieldNames.add(field.name)
                            }
                        } else {
                            smartFieldValues[field.name] = item
                            smartOtherListFieldNames.remove(field.name)
                        }
                        smartSelectedListFieldName = null
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { smartSelectedListFieldName = null }) { Text("סגור") } }
            )
        }
    }

    if (chainBranchesDialogOpen) {
        AlertDialog(
            onDismissRequest = { chainBranchesDialogOpen = false },
            title = { Text("בחר מספר ענפים") },
            text = {
                SelectionDialogColumn(items = chainBranchesOptions) { item ->
                    chainBranches = item
                    chainBranchesDialogOpen = false
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { chainBranchesDialogOpen = false }) { Text("סגור") }
            }
        )
    }

    if (chainSizeDialogOpen) {
        AlertDialog(
            onDismissRequest = { chainSizeDialogOpen = false },
            title = { Text("בחר מידה") },
            text = {
                SelectionDialogColumn(items = chainSizeOptions) { item ->
                    chainSize = item
                    chainSizeDialogOpen = false
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { chainSizeDialogOpen = false }) { Text("סגור") }
            }
        )
    }

    if (chainEnd1DialogOpen) {
        AlertDialog(
            onDismissRequest = { chainEnd1DialogOpen = false },
            title = { Text("בחר אביזר קצה 1") },
            text = {
                SelectionDialogColumn(items = chainEndOptions) { item ->
                    chainEnd1 = item
                    if (item != "אחר") {
                        chainEnd1Other = ""
                    }
                    chainEnd1DialogOpen = false
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { chainEnd1DialogOpen = false }) { Text("סגור") }
            }
        )
    }

    if (chainEnd2DialogOpen) {
        AlertDialog(
            onDismissRequest = { chainEnd2DialogOpen = false },
            title = { Text("בחר אביזר קצה 2") },
            text = {
                SelectionDialogColumn(items = chainEnd2Options) { item ->
                    chainEnd2 = item
                    if (item != "אחר") {
                        chainEnd2Other = ""
                    }
                    chainEnd2DialogOpen = false
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { chainEnd2DialogOpen = false }) { Text("סגור") }
            }
        )
    }


    if (chainManufacturerDialogOpen) {
        AlertDialog(
            onDismissRequest = { chainManufacturerDialogOpen = false },
            title = { Text("בחר יצרן") },
            text = {
                SelectionDialogColumn(items = chainManufacturerOptions) { item ->
                    chainManufacturerChoice = item
                    if (item != "אחר") {
                        chainManufacturerOther = ""
                    }
                    chainManufacturerDialogOpen = false
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { chainManufacturerDialogOpen = false }) { Text("סגור") }
            }
        )
    }


    if (wireBranchesDialogOpen) {
        AlertDialog(
            onDismissRequest = { wireBranchesDialogOpen = false },
            title = { Text("בחר מספר ענפים") },
            text = {
                SelectionDialogColumn(items = wireBranchesOptions) { item ->
                    wireBranches = item
                    wireBranchesDialogOpen = false
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { wireBranchesDialogOpen = false }) { Text("סגור") }
            }
        )
    }

    if (wireEndTypeDialogOpen) {
        AlertDialog(
            onDismissRequest = { wireEndTypeDialogOpen = false },
            title = { Text("בחר סוג סיומת קצוות") },
            text = {
                SelectionDialogColumn(items = wireEndTypeOptions) { item ->
                    wireEndType = item
                    wireEndTypeDialogOpen = false
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { wireEndTypeDialogOpen = false }) { Text("סגור") }
            }
        )
    }

    if (wireExtraEndDialogOpen) {
        AlertDialog(
            onDismissRequest = { wireExtraEndDialogOpen = false },
            title = { Text("בחר אביזר קצה נוסף") },
            text = {
                SelectionDialogColumn(items = wireExtraEndOptions) { item ->
                    wireExtraEndChoice = item
                    if (item != "אחר") {
                        wireExtraEndOther = ""
                    }
                    wireExtraEndDialogOpen = false
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { wireExtraEndDialogOpen = false }) { Text("סגור") }
            }
        )
    }

    if (wireManufacturerDialogOpen) {
        AlertDialog(
            onDismissRequest = { wireManufacturerDialogOpen = false },
            title = { Text("בחר יצרן") },
            text = {
                SelectionDialogColumn(items = wireManufacturerOptions) { item ->
                    wireManufacturerChoice = item
                    if (item != "אחר") {
                        wireManufacturerOther = ""
                    }
                    wireManufacturerDialogOpen = false
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { wireManufacturerDialogOpen = false }) { Text("סגור") }
            }
        )
    }

    if (endAccessoryDialogOpen) {
        AlertDialog(
            onDismissRequest = { endAccessoryDialogOpen = false },
            title = { Text("בחר אביזר קצה") },
            text = {
                SelectionDialogColumn(items = endAccessoryOptions) { item ->
                    endAccessoryChoice = item
                    if (item != "אחר") {
                        endAccessoryOther = ""
                    }
                    if (item != "סגיר אומגה") {
                        omegaSize = ""
                    }
                    endAccessoryDialogOpen = false
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { endAccessoryDialogOpen = false }) { Text("סגור") }
            }
        )
    }

    if (omegaSizeDialogOpen) {
        AlertDialog(
            onDismissRequest = { omegaSizeDialogOpen = false },
            title = { Text("בחר מידה") },
            text = {
                SelectionDialogColumn(items = omegaSizeOptions) { item ->
                    omegaSize = item
                    omegaSizeDialogOpen = false
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { omegaSizeDialogOpen = false }) { Text("סגור") }
            }
        )
    }

    if (omegaManufacturerDialogOpen) {
        AlertDialog(
            onDismissRequest = { omegaManufacturerDialogOpen = false },
            title = { Text("בחר יצרן") },
            text = {
                SelectionDialogColumn(items = omegaManufacturerOptions) { item ->
                    omegaManufacturerChoice = item
                    if (item != "אחר") {
                        omegaManufacturerOther = ""
                    }
                    omegaManufacturerDialogOpen = false
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { omegaManufacturerDialogOpen = false }) { Text("סגור") }
            }
        )
    }

    if (textileKindDialogOpen) {
        AlertDialog(
            onDismissRequest = { textileKindDialogOpen = false },
            title = { Text("בחר סוג רצועה") },
            text = {
                SelectionDialogColumn(items = textileTypeOptions) { item ->
                    textileKind = item
                    textileKindDialogOpen = false
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { textileKindDialogOpen = false }) { Text("סגור") }
            }
        )
    }

    if (textileColorDialogOpen) {
        AlertDialog(
            onDismissRequest = { textileColorDialogOpen = false },
            title = { Text("בחר גוון") },
            text = {
                SelectionDialogColumn(items = textileColorOptions) { item ->
                    textileColorInput = item
                    textileColorDialogOpen = false
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { textileColorDialogOpen = false }) { Text("סגור") }
            }
        )
    }

    if (textileManufacturerDialogOpen) {
        AlertDialog(
            onDismissRequest = { textileManufacturerDialogOpen = false },
            title = { Text("בחר יצרן") },
            text = {
                SelectionDialogColumn(items = textileManufacturerOptions) { item ->
                    textileManufacturerChoice = item
                    if (item != "אחר") {
                        textileManufacturerOther = ""
                    }
                    textileManufacturerDialogOpen = false
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { textileManufacturerDialogOpen = false }) { Text("סגור") }
            }
        )
    }

    if (serialNoteDialogOpen) {
        AlertDialog(
            onDismissRequest = { serialNoteDialogOpen = false },
            title = { Text("בחר מספר זיהוי להערה") },
            text = {
                if (availableSerialsForNote.isEmpty()) {
                    Text("יש להזין תחילה מספרי זיהוי")
                } else {
                    SelectionDialogColumn(items = availableSerialsForNote) { item ->
                        selectedSerialForNote = item
                        serialNoteDialogOpen = false
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { serialNoteDialogOpen = false }) { Text("סגור") }
            }
        )
    }

    if (defectSerialDialogOpen) {
        AlertDialog(
            onDismissRequest = { defectSerialDialogOpen = false },
            title = { Text("שייך ליקוי למספר זיהוי") },
            text = {
                if (availableSerialsForNote.isEmpty()) {
                    Text("יש להזין תחילה מספרי זיהוי")
                } else {
                    SelectionDialogColumn(items = availableSerialsForNote) { item ->
                        selectedSerialForDefect = item
                        defectSerialDialogOpen = false
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { defectSerialDialogOpen = false }) { Text("סגור") }
            }
        )
    }

    pendingReadyPdfFile?.let { pdfFile ->
        AlertDialog(
            onDismissRequest = { pendingReadyPdfFile = null; finishAfterPrintIfNeeded() },
            title = { Text("תסקיר PDF מוכן") },
            text = { Text("בחר פעולה:") },
            confirmButton = {
                Button(onClick = {
                    openPdfFile(context, pdfFile)
                    pendingReadyPdfFile = null
                    finishAfterPrintIfNeeded()
                }) { Text("הצג PDF") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        sharePdfFile(context, pdfFile)
                        pendingReadyPdfFile = null
                        finishAfterPrintIfNeeded()
                    }) { Text("שתף") }
                    TextButton(onClick = {
                        pendingReadyPdfFile = null
                        finishAfterPrintIfNeeded()
                    }) { Text("סגור") }
                }
            }
        )
    }

    if (pdfPrintLoading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("מייצר PDF...", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("אנא המתן, תהליך זה עשוי לארוך עד כחצי דקה.", textAlign = TextAlign.Center)
                }
            },
            confirmButton = {}
        )
    }
}


/**
 * בונה HTML עדכני מהנתונים שמופיעים כרגע במסך.
 * משתמש בדיוק באותה תבנית ReportTemplate.kt שלך, כדי לא לפגוע בעיצוב הדוח.
 */
private fun buildHtmlForCurrentReport(
    context: Context,
    inspectorNumber: String,
    inspectorFirstName: String,
    inspectorLastName: String,
    inspectorCertificateNumber: String,
    runningNumber: String,
    inspectionDate: String,
    nextInspectionDate: String,
    owner: String,
    address: String,
    phone: String,
    contactPerson: String,
    vehicle: String,
    inspectionLocation: String,
    inspectionPlaceType: String,
    fixedNote: String,
    generalNote: String,
    reportAccessories: List<ReportAccessoryRow>,
    reportDefects: List<ReportDefectRow>,
    reportNotes: List<ReportNoteRow>,
    compact: Boolean = false
): String {
    val accessoryRowsHtml = reportAccessories.mapIndexed { index, row ->
        buildAccessoryRowHtml(
            index = index + 1,
            quantity = row.quantity,
            description = row.description,
            manufacturer = row.manufacturer,
            model = row.model,
            serialNumbers = row.serialNumbers,
            testLoad = row.testLoad,
            wll = row.wll
        )
    }.joinToString("\n")

    val defectRowsHtml = if (reportDefects.isEmpty()) {
        buildDefectRowHtml("לא נמצאו ליקויים", "")
    } else {
        reportDefects.joinToString("\n") { row ->
            buildDefectRowHtml(row.defectDescription, row.fixUntil)
        }
    }

    val sixMonthsLaterDate = try {
        val parts = inspectionDate.split("/")
        if (parts.size == 3) {
            val cal = java.util.Calendar.getInstance()
            cal.set(parts[2].toInt(), parts[1].toInt() - 1, parts[0].toInt())
            cal.add(java.util.Calendar.MONTH, 6)
            "%02d/%02d/%04d".format(
                cal.get(java.util.Calendar.DAY_OF_MONTH),
                cal.get(java.util.Calendar.MONTH) + 1,
                cal.get(java.util.Calendar.YEAR)
            )
        } else inspectionDate
    } catch (_: Exception) { inspectionDate }

    val notesRowsHtml = reportNotes.joinToString("\n") { row ->
        buildNoteRowHtml(row.text.replace("{תאריך+6חודשים}", sixMonthsLaterDate))
    }

    return buildReportHtml(
        inspectorNumber = inspectorNumber,
        inspectorFirstName = inspectorFirstName,
        inspectorLastName = inspectorLastName,
        inspectorCertificateNumber = inspectorCertificateNumber,
        runningNumber = runningNumber,
        inspectionDate = inspectionDate,
        nextInspectionDate = nextInspectionDate,
        owner = owner,
        address = address,
        phone = phone,
        contactPerson = contactPerson,
        vehicle = vehicle,
        inspectionLocation = inspectionLocation,
        inspectionPlaceType = inspectionPlaceType,
        accessoryRowsHtml = accessoryRowsHtml,
        defectRowsHtml = defectRowsHtml,
        fixedNote = fixedNote,
        generalNote = generalNote,
        serialNotesHtml = notesRowsHtml,
        inspectorStampDataUri = InspectorSettingsStorage.getStampDataUri(context),
        inspectorSignatureDataUri = InspectorSettingsStorage.getSignatureDataUri(context),
        reportTextSettings = InspectorSettingsStorage.getReportTextSettings(context),
        logoDataUri = InspectorSettingsStorage.getLogoDataUri(context),
        company = InspectorSettingsStorage.getCompanyDetails(context),
        compact = compact
    )
}

/**
 * בונה רשומת עבודה מלאה לשמירה ב-ReportStorage.
 * זו הרשומה שאפשר לפתוח שוב לעריכה.
 */
private fun buildWorkingReportForStorage(
    inspectorNumber: String,
    inspectorFirstName: String,
    inspectorLastName: String,
    inspectorCertificateNumber: String,
    runningNumber: String,
    inspectionDate: String,
    nextInspectionDate: String,
    owner: String,
    address: String,
    phone: String,
    contactPerson: String,
    vehicle: String,
    inspectionLocation: String,
    inspectionPlaceType: String,
    fixedNote: String,
    generalNote: String,
    reportAccessories: List<ReportAccessoryRow>,
    reportDefects: List<ReportDefectRow>,
    reportNotes: List<ReportNoteRow>,
    html: String,
    isLockedForNewAccessories: Boolean,
    site: String = ""
): ReportStorage.WorkingReport {
    return ReportStorage.WorkingReport(
        reportNumber = runningNumber,
        inspectorNumber = inspectorNumber,
        inspectorFirstName = inspectorFirstName,
        inspectorLastName = inspectorLastName,
        inspectorCertificateNumber = inspectorCertificateNumber,
        inspectionDate = inspectionDate,
        nextInspectionDate = nextInspectionDate,
        owner = owner,
        address = address,
        phone = phone,
        contactPerson = contactPerson,
        vehicle = vehicle,
        inspectionLocation = inspectionLocation,
        inspectionPlaceType = inspectionPlaceType,
        fixedNote = fixedNote,
        generalNote = generalNote,
        site = site,
        accessories = reportAccessories.map {
            ReportStorage.StoredAccessoryRow(
                description = it.description,
                manufacturer = it.manufacturer,
                model = it.model,
                quantity = it.quantity,
                serialNumbers = it.serialNumbers,
                testLoad = it.testLoad,
                wll = it.wll
            )
        },
        defects = reportDefects.map {
            ReportStorage.StoredDefectRow(
                defectDescription = it.defectDescription,
                fixUntil = it.fixUntil
            )
        },
        notes = reportNotes.map {
            ReportStorage.StoredNoteRow(text = it.text)
        },
        html = html,
        isLockedForNewAccessories = isLockedForNewAccessories
    )
}


private fun saveAccessoryClientIfNeeded(
    clientMemoryStore: ClientMemoryStore,
    owner: String,
    address: String,
    phone: String,
    contactPerson: String,
    saveClientToMemory: Boolean,
    saveAddress: Boolean,
    savePhone: Boolean,
    saveContact: Boolean
) {
    if (!saveClientToMemory) return
    clientMemoryStore.saveOrUpdateClient(
        name = owner,
        address = if (saveAddress) address else "",
        phone = if (savePhone) phone else "",
        contactPerson = if (saveContact) contactPerson else ""
    )
}

@Composable
private fun AccessoryClientMemoryField(
    value: String,
    clientMemoryStore: ClientMemoryStore,
    onValueChange: (String) -> Unit,
    onClientSelected: (ClientMemoryItem) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions = remember(value) { clientMemoryStore.searchClients(value) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("תופש / לקוח", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = value,
                onValueChange = { newValue ->
                    onValueChange(newValue)
                    expanded = newValue.trim().length >= 2
                },
                label = { Text("תופש / לקוח") },
                placeholder = { Text("הקלד 2-3 אותיות לסינון לקוחות שמורים") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            DropdownMenu(
                expanded = expanded && suggestions.isNotEmpty(),
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                suggestions.forEach { client ->
                    val subText = listOf(client.address, client.phone, client.contactPerson)
                        .filter { it.isNotBlank() }
                        .joinToString(" | ")

                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(client.name)
                                if (subText.isNotBlank()) {
                                    Text(subText)
                                }
                            }
                        },
                        onClick = {
                            onClientSelected(client)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ClientMemorySaveOptions(
    saveClientToMemory: Boolean,
    onSaveClientToMemoryChange: (Boolean) -> Unit,
    saveAddress: Boolean,
    onSaveAddressChange: (Boolean) -> Unit,
    savePhone: Boolean,
    onSavePhoneChange: (Boolean) -> Unit,
    saveContact: Boolean,
    onSaveContactChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFBDBDBD))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text("זיכרון לקוח לפעם הבאה", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        ClientMemoryCheckRow(
            text = "שמור לקוח/תופש בזיכרון",
            checked = saveClientToMemory,
            onCheckedChange = onSaveClientToMemoryChange,
            enabled = true
        )
        ClientMemoryCheckRow(
            text = "שמור כתובת משרד",
            checked = saveAddress,
            onCheckedChange = onSaveAddressChange,
            enabled = saveClientToMemory
        )
        ClientMemoryCheckRow(
            text = "שמור טלפון",
            checked = savePhone,
            onCheckedChange = onSavePhoneChange,
            enabled = saveClientToMemory
        )
        ClientMemoryCheckRow(
            text = "שמור איש קשר",
            checked = saveContact,
            onCheckedChange = onSaveContactChange,
            enabled = saveClientToMemory
        )
        Text("כתובת/מיקום בדיקה לא נשמרים אוטומטית, כי הם משתנים מבדיקה לבדיקה.")
    }
}

@Composable
private fun ClientMemoryCheckRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
        Text(text)
    }
}

@Composable
private fun FullReportScreen(
    inspectorNumber: String,
    runningNumber: String,
    inspectionDate: String,
    nextInspectionDate: String,
    owner: String,
    address: String,
    phone: String,
    contactPerson: String,
    vehicle: String,
    inspectionLocation: String,
    accessoryRows: List<ReportAccessoryRow>,
    defectRows: List<ReportDefectRow>,
    fixedNote: String,
    generalNote: String,
    noteRows: List<ReportNoteRow>,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "סגור")
            }
        }

        Text("תסקיר מלא")

        OutlinedTextField(
            value = inspectorNumber,
            onValueChange = {},
            readOnly = true,
            label = { Text("מספר בודק") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = runningNumber,
            onValueChange = {},
            readOnly = true,
            label = { Text("מספר רץ") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = inspectionDate,
            onValueChange = {},
            readOnly = true,
            label = { Text("תאריך בדיקה") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = nextInspectionDate,
            onValueChange = {},
            readOnly = true,
            label = { Text("תאריך בדיקה הבאה") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = owner,
            onValueChange = {},
            readOnly = true,
            label = { Text("תופש") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = address,
            onValueChange = {},
            readOnly = true,
            label = { Text("כתובת") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = phone,
            onValueChange = {},
            readOnly = true,
            label = { Text("טלפון") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = vehicle,
            onValueChange = {},
            readOnly = true,
            label = { Text("מס' רכב") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = inspectionLocation,
            onValueChange = {},
            readOnly = true,
            label = { Text("מיקום הבדיקה") },
            modifier = Modifier.fillMaxWidth()
        )

        Text("טבלת אביזרים")
        AccessoryReportTable(rows = accessoryRows)

        Text("טבלת ליקויים")
        if (defectRows.isEmpty()) {
            Text("לא נוספו ליקויים")
        } else {
            DefectReportTable(rows = defectRows)
        }

        Text("הערות")

        OutlinedTextField(
            value = fixedNote,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )

        if (generalNote.isNotBlank()) {
            OutlinedTextField(
                value = "הערה כללית: $generalNote",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        }

        noteRows.forEachIndexed { index, row ->
            OutlinedTextField(
                value = "הערה ${index + 1}: ${row.text}",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        }
    }
}

@Composable
private fun SiteSelector(
    sites: List<InspectorSettingsStorage.Site>,
    selectedSiteId: String,
    onSiteSelected: (String) -> Unit
) {
    if (sites.isEmpty()) return
    var dialogOpen by remember { mutableStateOf(false) }

    Button(
        onClick = { dialogOpen = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(sites.firstOrNull { it.id == selectedSiteId }?.name ?: "בחר אתר בדיקה")
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text("בחר אתר בדיקה") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    sites.forEach { site ->
                        TextButton(
                            onClick = {
                                onSiteSelected(site.id)
                                dialogOpen = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(site.name, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                if (site.address.isNotBlank()) Text(site.address, fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { dialogOpen = false }) { Text("סגור") }
            }
        )
    }
}

@Composable
private fun SelectionDialogColumn(
    items: List<String>,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { item ->
            TextButton(
                onClick = { onSelect(item) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(item)
            }
        }
    }
}

@Composable
private fun AccessoryReportTable(rows: List<ReportAccessoryRow>) {
    if (rows.isEmpty()) {
        Text("טרם נוספו אביזרים")
        return
    }

    val tableScroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(tableScroll)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            TableCell("תיאור אביזר ההרמה", 312.dp, true, TextAlign.Right)
            TableCell("יצרן/ספק", 120.dp, true, TextAlign.Center)
            TableCell("דגם", 120.dp, true, TextAlign.Center)
            TableCell("כמות", 80.dp, true, TextAlign.Center)
            TableCell("מס' זיהוי", 108.dp, true, TextAlign.Center)
            TableCell("עומס מבחן", 120.dp, true, TextAlign.Center)
            TableCell("ע.ע.ב", 120.dp, true, TextAlign.Center)
        }

        rows.forEach { row ->
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                TableCell(row.description, 312.dp, false, TextAlign.Right)
                TableCell(row.manufacturer, 120.dp, false, TextAlign.Center)
                TableCell(row.model, 120.dp, false, TextAlign.Center)
                TableCell(row.quantity, 80.dp, false, TextAlign.Center)
                TableCell(row.serialNumbers, 108.dp, false, TextAlign.Center)
                TableCell(row.testLoad, 120.dp, false, TextAlign.Center)
                TableCell(row.wll, 120.dp, false, TextAlign.Center)
            }
        }
    }
}


@Composable
private fun EditableAccessoryRows(
    rows: androidx.compose.runtime.snapshots.SnapshotStateList<ReportAccessoryRow>,
    isLockedForNewAccessories: Boolean,
    photoCountForSerials: (String) -> Int = { 0 },
    onPhotoButtonClick: (Int, ReportAccessoryRow) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current

    Text(
        if (isLockedForNewAccessories) {
            "עריכת שורות אביזרים קיימות — לאחר PDF"
        } else {
            "עריכת שורות אביזרים קיימות — לפני PDF"
        }
    )

    Text(
        if (isLockedForNewAccessories) {
            "לאחר הפקת PDF: אסור להוסיף אביזר, לשנות כמות, או להוסיף/למחוק מספרי זיהוי. מותר לערוך את הערכים הקיימים."
        } else {
            "לפני הפקת PDF: ניתן לערוך את כל השדות. שינוי כמות יעדכן את מספר שורות הזיהוי בהתאם."
        }
    )

    rows.forEachIndexed { index, row ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.Gray)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "אביזר ${index + 1}",
                    modifier = Modifier.weight(1f)
                )

                val photoCount = photoCountForSerials(row.serialNumbers)
                Button(
                    onClick = { onPhotoButtonClick(index, row) }
                ) {
                    Text(
                        if (photoCount > 0) {
                            "📷 תמונות ($photoCount)"
                        } else {
                            "📷 צילום"
                        }
                    )
                }
            }

            OutlinedTextField(
                value = row.description,
                onValueChange = { rows[index] = row.copy(description = it) },
                label = { Text("תיאור אביזר ההרמה") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            OutlinedTextField(
                value = row.manufacturer,
                onValueChange = { rows[index] = row.copy(manufacturer = it) },
                label = { Text("יצרן") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = row.model,
                onValueChange = { rows[index] = row.copy(model = it) },
                label = { Text("דגם") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = row.quantity,
                onValueChange = { newValue ->
                    if (!isLockedForNewAccessories) {
                        val newQuantity = newValue.filter { ch -> ch.isDigit() }.take(2)
                        val quantityInt = newQuantity.toIntOrNull() ?: 0

                        rows[index] = row.copy(
                            quantity = newQuantity,
                            serialNumbers = adjustSerialLinesToQuantity(
                                currentSerials = row.serialNumbers,
                                quantity = quantityInt
                            )
                        )
                    }
                },
                readOnly = isLockedForNewAccessories,
                label = {
                    Text(
                        if (isLockedForNewAccessories) {
                            "כמות — נעולה לאחר הפקת PDF"
                        } else {
                            "כמות"
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            OutlinedTextField(
                value = row.serialNumbers,
                onValueChange = { newValue ->
                    val candidateRows = rows.mapIndexed { rowIndex, existingRow ->
                        if (rowIndex == index) existingRow.copy(serialNumbers = newValue) else existingRow
                    }
                    val duplicate = findDuplicateSerialsInRows(candidateRows)

                    if (duplicate != null) {
                        android.widget.Toast.makeText(
                            context,
                            duplicateSerialMessage(duplicate),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } else if (!isLockedForNewAccessories) {
                        rows[index] = row.copy(serialNumbers = newValue)
                    } else {
                        val oldCount = countSerialLines(row.serialNumbers)
                        val newCount = countSerialLines(newValue)

                        if (oldCount == newCount) {
                            rows[index] = row.copy(serialNumbers = newValue)
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                "לא ניתן להוסיף או למחוק מספרי זיהוי לאחר הפקת PDF. ניתן לערוך רק את הערכים הקיימים.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                label = {
                    Text(
                        if (isLockedForNewAccessories) {
                            "מספרי זיהוי — עריכת ערכים קיימים בלבד"
                        } else {
                            "מספרי זיהוי — שורה לכל מספר זיהוי"
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            OutlinedTextField(
                value = row.testLoad,
                onValueChange = { rows[index] = row.copy(testLoad = it) },
                label = { Text("עומס מבחן") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = row.wll,
                onValueChange = { rows[index] = row.copy(wll = it) },
                label = { Text("ע.ע.ב") },
                modifier = Modifier.fillMaxWidth()
            )

            if (!isLockedForNewAccessories) {
                Button(
                    onClick = {
                        rows.removeAt(index)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("מחק אביזר זה")
                }
            }
        }
    }
}

@Composable
private fun EditableDefectRows(
    rows: androidx.compose.runtime.snapshots.SnapshotStateList<ReportDefectRow>
) {
    Text("עריכת ליקויים קיימים")

    rows.forEachIndexed { index, row ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.Gray)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("ליקוי ${index + 1}")

            OutlinedTextField(
                value = row.defectDescription,
                onValueChange = { rows[index] = row.copy(defectDescription = it) },
                label = { Text("תיאור ליקוי") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            OutlinedTextField(
                value = row.fixUntil,
                onValueChange = { rows[index] = row.copy(fixUntil = it) },
                label = { Text("לביצוע עד / הערת תיקון") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun countSerialLines(value: String): Int {
    return value
        .lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .size
}

/**
 * מתאים את מספר שורות הזיהוי לכמות.
 * לפני הפקת PDF מותר לשנות כמות, ולכן כאשר הכמות משתנה
 * אנו מוסיפים שורות ריקות או מסירים שורות עודפות כדי שהכמות ומספרי הזיהוי יתאימו.
 */
private fun adjustSerialLinesToQuantity(
    currentSerials: String,
    quantity: Int
): String {
    if (quantity <= 0) return ""

    val currentLines = currentSerials
        .lines()
        .map { it.trim() }
        .toMutableList()

    while (currentLines.size < quantity) {
        currentLines.add("")
    }

    while (currentLines.size > quantity) {
        currentLines.removeAt(currentLines.lastIndex)
    }

    return currentLines.joinToString("\n")
}

@Composable
private fun DefectReportTable(rows: List<ReportDefectRow>) {
    val tableScroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(tableScroll)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            TableCell("ליקוי", 360.dp, true, TextAlign.Right)
            TableCell("לביצוע עד", 160.dp, true, TextAlign.Center)
        }

        rows.forEach { row ->
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                TableCell(row.defectDescription, 360.dp, false, TextAlign.Right)
                TableCell(row.fixUntil, 160.dp, false, TextAlign.Center)
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    width: Dp,
    header: Boolean,
    align: TextAlign
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .border(1.dp, Color.Gray)
            .padding(8.dp),
        contentAlignment = when (align) {
            TextAlign.Center -> Alignment.Center
            TextAlign.Right -> Alignment.CenterEnd
            else -> Alignment.CenterStart
        }
    ) {
        Text(
            text = if (text.isBlank()) "-" else text,
            color = if (header) Color.Black else Color.DarkGray,
            textAlign = align,
            modifier = Modifier.fillMaxWidth()
        )
    }
}


private fun calculateWireRopeSlingWll(
    branches: String,
    diameterText: String,
    endType: String
): String {
    val diameter = diameterText.toDoubleOrNull() ?: return ""

    // נוסחת הבסיס לחד ענפי לפי החלטת הבודק:
    // לחוצים:  WLL = 8 * d^2 * 0.8 / 1000 [t]
    // יצוקים:  WLL = 8 * d^2 * 0.9 / 1000 [t]
    // מהדקים: WLL = 8 * d^2 * 0.7 / 1000 [t]
    // עבור "אחר" לא מחשבים אוטומטית כדי שהבודק יבדוק ויערוך ידנית אם צריך.
    val endCoefficient = when (endType) {
        "לחוצים" -> 0.8
        "יצוקים" -> 0.9
        "מהדקים" -> 0.7
        else -> return ""
    }

    fun oneDecimalFloor(value: Double): String {
        val result = floor(value * 10.0) / 10.0
        return String.format("%.1f", result)
    }

    val singleLegWll = 8.0 * diameter * diameter * endCoefficient / 1000.0

    return when (branches) {
        "חד" -> oneDecimalFloor(singleLegWll)
        "דו" -> {
            val v90 = oneDecimalFloor(singleLegWll * 1.4)
            val v120 = oneDecimalFloor(singleLegWll * 1.0)
            "$v90/90° , $v120/120°"
        }
        "תלת", "ארבע" -> {
            val v90 = oneDecimalFloor(singleLegWll * 2.1)
            val v120 = oneDecimalFloor(singleLegWll * 1.5)
            "$v90/90° , $v120/120°"
        }
        else -> ""
    }
}

private fun calculateChainWll(branches: String, sizeText: String): String {
    val size = sizeText.substringBefore("/").toDoubleOrNull() ?: return ""

    fun oneDecimalFloor(value: Double): String {
        val result = floor(value * 10.0) / 10.0
        return String.format("%.1f", result)
    }
    return when (branches) {
        "חד" -> {
            val v = oneDecimalFloor(size * size * 30.0 / 1000.0)
            "$v"
        }
        "דו" -> {
            val v90 = oneDecimalFloor(size * size * 30.0 * 1.4 / 1000.0)
            val v120 = oneDecimalFloor(size * size * 30.0 * 1.0 / 1000.0)
            "$v90/90° , $v120/120°"
        }
        "תלת", "ארבע" -> {
            val v90 = oneDecimalFloor(size * size * 30.0 * 2.1 / 1000.0)
            val v120 = oneDecimalFloor(size * size * 30.0 * 1.5 / 1000.0)
            "$v90/90° , $v120/120°"
        }
        else -> ""
    }
}

private fun normalizeTextileColor(input: String): String {
    val colors = listOf("סגולה", "ירוקה", "צהובה", "אפורה", "אדומה", "חומה", "כחולה", "כתומה")
    val text = input.trim()
    if (text.isBlank()) return ""
    return colors.firstOrNull { it.startsWith(text) } ?: ""
}

private fun calculateOmegaWll(size: String): String {
    return when (size) {
        "3/16" -> "0.33"
        "1/4" -> "0.5"
        "5/16" -> "0.75"
        "3/8" -> "1"
        "7/16" -> "1.5"
        "1/2" -> "2"
        "5/8" -> "3.25"
        "3/4" -> "4.75"
        "7/8" -> "6.5"
        "1" -> "8.5"
        "1-1/8" -> "9.5"
        "1-1/4" -> "12"
        "1-3/8" -> "13.5"
        "1-1/2" -> "17"
        "1-3/4" -> "25"
        "2" -> "35"
        "2-1/2" -> "55"
        else -> ""
    }
}

private fun calculateTextileWll(color: String): String {
    return when (color) {
        "סגולה" -> "1"
        "ירוקה" -> "2"
        "צהובה" -> "3"
        "אפורה" -> "4"
        "אדומה" -> "5"
        "חומה" -> "6"
        "כחולה" -> "8"
        "כתומה" -> "10"
        else -> ""
    }
}


private fun createInspectionPhotoUri(
    context: Context,
    reportNumber: String,
    serialNumber: String
): Uri {
    val cleanReport = reportNumber.ifBlank { "report" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val cleanSerial = serialNumber.ifBlank { "serial" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    val photosDir = File(context.getExternalFilesDir("inspection_photos"), cleanReport).apply {
        mkdirs()
    }

    val photoFile = File(photosDir, "${cleanSerial}_${timeStamp}.jpg")

    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        photoFile
    )
}

private fun openPhotoInExternalViewer(context: Context, uriText: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uriText), "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        android.widget.Toast.makeText(
            context,
            "לא ניתן לפתוח את התמונה במכשיר זה.",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
}

internal fun appendPhotosToReportHtml(
    html: String,
    context: Context,
    photos: List<ReportPhotoStorage.PhotoRecord>
): String {
    if (photos.isEmpty()) return html

    val photoItemsHtml = photos.mapNotNull { photo ->
        val encoded = encodeImageUriToCompressedBase64(context, photo.uri, maxSidePx = 300, jpegQuality = 78) ?: return@mapNotNull null
        """
        <div class="photo-item">
            <div class="photo-title">תמונה מאביזר מספר זיהוי: ${safeForLocalHtml(photo.serialNumber)}</div>
            <img src="data:image/jpeg;base64,$encoded"/>
        </div>
        """.trimIndent()
    }

    if (photoItemsHtml.isEmpty()) return html

    // שורות flex של 2 תמונות — עד 8 תמונות על דף אחד
    // 261 = 297 - 16mm כותרת - 20mm overhead נסתר, gap בין שורות בלבד = (rows-1)*2
    val n = photoItemsHtml.size
    val rows = (n + 1) / 2
    val rowHeightMm = ((261 - (rows - 1) * 2) / rows).coerceIn(25, 50)

    val pages = buildString {
        append("<div class='pageBreak'></div>\n")
        append("<div class=\"photo-page-title\">נספח תמונות אביזרי הרמה</div>\n")
        append("<div class=\"photos-wrapper\">\n")
        photoItemsHtml.chunked(2).forEach { pair ->
            append("<div class=\"photo-row\">\n${pair.joinToString("\n")}\n</div>\n")
        }
        append("</div>\n")
    }

    val style = """
    <style>
    @page { size: A4; margin: 0; }
    html, body { margin: 0 !important; padding: 0 !important; }
    .pageBreak { page-break-before: always; height: 0; overflow: hidden; }
    .photo-page-title {
        height: 16mm; box-sizing: border-box; overflow: hidden; margin: 0;
        display: flex; align-items: center; justify-content: center;
        font-size: 16px; font-weight: bold; text-decoration: underline;
    }
    .photos-wrapper { display: flex; flex-direction: column; gap: 2mm; }
    .photo-row { display: flex; flex-direction: row; gap: 2mm; }
    .photo-item {
        flex: 1; height: ${rowHeightMm}mm; box-sizing: border-box;
        overflow: hidden; border: 1px solid #444; padding: 4px; text-align: center;
    }
    .photo-title { font-size: 10px; font-weight: bold; margin-bottom: 2px; }
    .photo-item img {
        display: block; margin: 0 auto; max-width: 100%;
        max-height: calc(${rowHeightMm}mm - 14mm); height: auto; width: auto;
    }
    </style>
    """.trimIndent()

    return html
        .replace("</head>", "$style\n</head>")
        .replace("</body>", "$pages\n</body>")
}

private fun encodeImageUriToBase64(context: Context, uriText: String): String? {
    // עדכון 010:
    // בעבר התמונות צורפו ל-HTML בגודל המקורי שלהן. תמונות מטלפון יכולות להיות גדולות מאוד,
    // וזה עלול לגרום להמתנה ארוכה, הדפסה בלי תמונות, או קריסה של WebView/Print.
    // לכן מקטינים את התמונה לפני Base64 ושומרים איכות מספקת לנספח PDF.
    return encodeImageUriToCompressedBase64(
        context = context,
        uriText = uriText,
        maxSidePx = 1400,
        jpegQuality = 78
    )
}

private fun encodeImageUriToCompressedBase64(
    context: Context,
    uriText: String,
    maxSidePx: Int,
    jpegQuality: Int
): String? {
    return try {
        val uri = Uri.parse(uriText)

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (
            bounds.outWidth / sampleSize > maxSidePx ||
            bounds.outHeight / sampleSize > maxSidePx
        ) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }

        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: return null

        val scaledBitmap = scaleBitmapToMaxSide(bitmap, maxSidePx)
        if (scaledBitmap !== bitmap) {
            bitmap.recycle()
        }

        val output = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(40, 95), output)
        scaledBitmap.recycle()

        Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    } catch (_: Exception) {
        null
    }
}

private fun scaleBitmapToMaxSide(bitmap: Bitmap, maxSidePx: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val maxSide = maxOf(width, height)

    if (maxSide <= maxSidePx) return bitmap

    val ratio = maxSidePx.toFloat() / maxSide.toFloat()
    val targetWidth = (width * ratio).toInt().coerceAtLeast(1)
    val targetHeight = (height * ratio).toInt().coerceAtLeast(1)

    return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
}

private fun safeForLocalHtml(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}

private fun parseDateToMillisOrToday(text: String): Long {
    return try {
        val date = LocalDate.parse(text, dateFormatter)

        // DatePicker של Compose עובד טוב יותר עם UTC.
        // כך נמנעת קפיצה ליום קודם בגלל אזור הזמן של ישראל.
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    } catch (_: Exception) {
        LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
}

private fun millisToLocalDate(millis: Long): LocalDate {
    // DatePicker מחזיר millis לפי UTC.
    // לכן גם הקריאה חזרה צריכה להיות לפי UTC ולא לפי ZoneId.systemDefault().
    return Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}

private fun formatLocalDate(date: LocalDate): String {
    return date.format(dateFormatter)
}



