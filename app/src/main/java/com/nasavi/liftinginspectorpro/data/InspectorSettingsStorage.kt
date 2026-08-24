package com.nasavi.liftinginspectorpro.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

object InspectorSettingsStorage {

    private const val PREF_NAME = "inspector_settings"
    private const val KEY_INSPECTOR_DETAILS = "inspector_details"
    private const val KEY_NEXT_REPORT_NUMBER = "next_report_number"
    private const val KEY_STAMP_PATH = "inspector_stamp_path"
    private const val KEY_SIGNATURE_PATH = "inspector_signature_path"

    private const val KEY_REPORT_TITLE = "report_title"
    private const val KEY_REPORT_LEGAL_LINE = "report_legal_line"
    private const val KEY_REPORT_DEFAULT_NOTE = "report_default_note"
    private const val KEY_REPORT_DECLARATION = "report_declaration"
    private const val KEY_REPORT_PRINT_DATE_LABEL = "report_print_date_label"
    private const val KEY_REPORT_BOTTOM_WARNING = "report_bottom_warning"
    private const val KEY_TEMPLATE_NOTES_LIST = "template_notes_list"

    private const val ASSETS_FOLDER = "inspector_assets"
    private const val STAMP_FILE_NAME = "stamp.png"
    private const val SIGNATURE_FILE_NAME = "signature.png"
    private const val LOGO_FILE_NAME = "logo.png"

    private const val KEY_LOGO_PATH = "business_logo_path"
    private const val KEY_NEXT_AIR_RECEIVER_NUMBER = "next_air_receiver_number"
    private const val KEY_AIR_RECEIVER_NOTES = "air_receiver_notes"
    private const val KEY_COMPANY_NAME = "company_name"
    private const val KEY_COMPANY_TITLE = "company_title"
    private const val KEY_INSPECTOR_ADDRESS = "inspector_address"
    private const val KEY_INSPECTOR_CITY = "inspector_city"
    private const val KEY_INSPECTOR_PHONE = "inspector_phone"
    private const val KEY_INSPECTOR_EMAIL = "inspector_email"
    private const val KEY_BUSINESS_ID = "business_id"

    data class InspectorDetails(
        val firstName: String,
        val lastName: String,
        val certificateNumber: String
    ) {
        val fullName: String
            get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
    }

    data class CompanyDetails(
        val companyName: String = "",
        val companyTitle: String = "",
        val address: String = "",
        val city: String = "",
        val phone: String = "",
        val email: String = "",
        val businessId: String = ""
    )

    data class ReportTextSettings(
        val title: String,
        val legalLine: String,
        val defaultNote: String,
        val declaration: String,
        val printDateLabel: String,
        val bottomWarning: String,
        val templateNotesList: List<String> = emptyList()
    )

    fun defaultReportTextSettings(): ReportTextSettings {
        return ReportTextSettings(
            title = "תסקיר בדיקה וניסוי אביזרי הרמה",
            legalLine = "לפי פקודת הבטיחות בעבודה (נוסח חדש), תש\"ל-1970, סעיפים: 75, 76",
            defaultNote = "לעבוד לפי הוראות היצרן וכללי הבטיחות המקובלים.",
            declaration = "אני {שם הבודק} שהוסמכתי ע\"י מפקח עבודה ראשי לפי סימנים ו' ו-ז' לפרק ג' לפקודת הבטיחות בעבודה (נוסח חדש) תש\"ל-1970\nתעודת הסמכה מס' {מספר הסמכה} מאשר שבדקתי את אביזרי ההרמה המתוארים בתסקיר זה בהתאם לתקנות.",
            printDateLabel = "תאריך הדפסה:",
            bottomWarning = "שים לב: תסקיר זה מחייב את המעסיק לבצע תיקון ליקויים ובדיקתם מחדש אם צוינו ליקויים."
        )
    }

    fun getInspectorDetails(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_INSPECTOR_DETAILS, "") ?: ""
    }

    fun getInspectorDetailsObject(context: Context): InspectorDetails {
        return parseInspectorDetails(getInspectorDetails(context))
    }

    fun getNextReportNumber(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_NEXT_REPORT_NUMBER, "R6000001") ?: "R6000001"
    }

    fun getStampPath(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_STAMP_PATH, "") ?: ""
    }

    fun getSignaturePath(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SIGNATURE_PATH, "") ?: ""
    }

    fun hasStamp(context: Context): Boolean = getStampPath(context).let { it.isNotBlank() && File(it).exists() }

    fun hasSignature(context: Context): Boolean = getSignaturePath(context).let { it.isNotBlank() && File(it).exists() }

    fun saveStampFromUri(context: Context, sourceUri: Uri): String {
        val savedPath = copySelectedPngToInternalStorage(
            context = context,
            sourceUri = sourceUri,
            targetFileName = STAMP_FILE_NAME
        )
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STAMP_PATH, savedPath)
            .apply()
        return savedPath
    }

    fun saveSignatureFromUri(context: Context, sourceUri: Uri): String {
        val savedPath = copySelectedPngToInternalStorage(
            context = context,
            sourceUri = sourceUri,
            targetFileName = SIGNATURE_FILE_NAME
        )
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SIGNATURE_PATH, savedPath)
            .apply()
        return savedPath
    }

    fun removeStamp(context: Context) {
        getStampPath(context).takeIf { it.isNotBlank() }?.let { path ->
            runCatching { File(path).delete() }
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_STAMP_PATH)
            .apply()
    }

    fun removeSignature(context: Context) {
        getSignaturePath(context).takeIf { it.isNotBlank() }?.let { path ->
            runCatching { File(path).delete() }
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SIGNATURE_PATH)
            .apply()
    }

    fun getStampDataUri(context: Context): String =
        imageFileToCompressedDataUri(getStampPath(context), maxSidePx = 500, jpegQuality = 85)

    fun getSignatureDataUri(context: Context): String =
        imageFileToCompressedDataUri(getSignaturePath(context), maxSidePx = 400, jpegQuality = 85)

    fun getLogoPath(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LOGO_PATH, "") ?: ""
    }

    fun hasLogo(context: Context): Boolean = getLogoPath(context).let { it.isNotBlank() && File(it).exists() }

    fun saveLogoFromUri(context: Context, sourceUri: Uri): String {
        val savedPath = copySelectedPngToInternalStorage(context, sourceUri, LOGO_FILE_NAME)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LOGO_PATH, savedPath).apply()
        return savedPath
    }

    fun removeLogo(context: Context) {
        getLogoPath(context).takeIf { it.isNotBlank() }?.let { runCatching { File(it).delete() } }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().remove(KEY_LOGO_PATH).apply()
    }

    fun getLogoDataUri(context: Context): String =
        imageFileToCompressedDataUri(getLogoPath(context), maxSidePx = 400, jpegQuality = 85)

    fun getNextAirReceiverNumber(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_NEXT_AIR_RECEIVER_NUMBER, "Q6001") ?: "Q6001"
    }

    fun saveNextAirReceiverNumber(context: Context, value: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_NEXT_AIR_RECEIVER_NUMBER, value.trim()).apply()
    }

    fun advanceToNextAirReceiverNumber(context: Context): String {
        val current = getNextAirReceiverNumber(context)
        val next = incrementReportNumber(current)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_NEXT_AIR_RECEIVER_NUMBER, next).apply()
        return next
    }

    fun defaultAirReceiverNotes(): List<String> = listOf(
        "העבודה עם הקולט בהתאם להוראות היצרן",
        "יש לנקז את המים מהקולט בסוף כל יום עבודה",
        "השימוש בקולט מותר בכפוף לתיקון הליקויים (במידה והתגלו) האחריות לשימוש עם ליקויים חלה על המחזיק",
        "בקולט אוויר יש לבצע בדיקות תקופתיות כל 26 חודשים",
        "בקולט אוויר יש לבצע בדיקה הידרוסטטית/חלופית בהגיע הקולט לגיל 10, 20, 26 ואח\"כ כל 6 שנים"
    )

    fun getAirReceiverNotes(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_AIR_RECEIVER_NOTES, null)
        if (json.isNullOrBlank()) return defaultAirReceiverNotes()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { defaultAirReceiverNotes() }
    }

    fun saveAirReceiverNotes(context: Context, notes: List<String>) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_AIR_RECEIVER_NOTES, JSONArray(notes).toString()).apply()
    }

    // ── פריסת סעיף 5 (בדיקה מיוחדת קודמת) בתסקיר ──────────────────
    private const val KEY_AIR_S5_LAYOUT = "air_s5_layout"

    val SECTION5_FIELD_LABELS = mapOf(
        "prevSpecType"        to "סוג הבדיקה המיוחדת",
        "prevSpecDate"        to "תאריך הבדיקה",
        "prevSpecBy"          to "בוצעה ע\"י",
        "prevSpecReportNum"   to "מספר תסקיר",
        "prevSpecInspectorNum" to "מספר בודק",
        "prevSpecTestPressure" to "לחץ ניסוי (בר)",
        "prevSpecSafetyValveDiam" to "קוטר שסתום בטיחות",
        "prevSpecDrainValve"  to "שסתום ניקוז",
        "prevSpecMinCapWall"  to "עובי דופן כיפה מינ' (מ\"מ)"
    )

    fun defaultAirS5Layout(): List<List<String>> = listOf(
        listOf("prevSpecType",           "prevSpecDate",         "prevSpecBy"),
        listOf("prevSpecReportNum",      "prevSpecInspectorNum", "prevSpecTestPressure"),
        listOf("prevSpecSafetyValveDiam","prevSpecDrainValve",   "prevSpecMinCapWall")
    )

    fun getAirS5Layout(context: Context): List<List<String>> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_AIR_S5_LAYOUT, null) ?: return defaultAirS5Layout()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val row = arr.getJSONArray(i)
                (0 until row.length()).map { j -> row.optString(j, "") }
            }
        } catch (_: Exception) { defaultAirS5Layout() }
    }

    fun saveAirS5Layout(context: Context, layout: List<List<String>>) {
        val arr = JSONArray()
        layout.forEach { row ->
            val rowArr = JSONArray()
            row.forEach { rowArr.put(it) }
            arr.put(rowArr)
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_AIR_S5_LAYOUT, arr.toString()).apply()
    }

    fun getCompanyDetails(context: Context): CompanyDetails {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return CompanyDetails(
            companyName = prefs.getString(KEY_COMPANY_NAME, "") ?: "",
            companyTitle = prefs.getString(KEY_COMPANY_TITLE, "") ?: "",
            address = prefs.getString(KEY_INSPECTOR_ADDRESS, "") ?: "",
            city = prefs.getString(KEY_INSPECTOR_CITY, "") ?: "",
            phone = prefs.getString(KEY_INSPECTOR_PHONE, "") ?: "",
            email = prefs.getString(KEY_INSPECTOR_EMAIL, "") ?: "",
            businessId = prefs.getString(KEY_BUSINESS_ID, "") ?: ""
        )
    }

    fun saveCompanyDetails(context: Context, details: CompanyDetails) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_COMPANY_NAME, details.companyName)
            .putString(KEY_COMPANY_TITLE, details.companyTitle)
            .putString(KEY_INSPECTOR_ADDRESS, details.address)
            .putString(KEY_INSPECTOR_CITY, details.city)
            .putString(KEY_INSPECTOR_PHONE, details.phone)
            .putString(KEY_INSPECTOR_EMAIL, details.email)
            .putString(KEY_BUSINESS_ID, details.businessId)
            .apply()
    }

    fun getReportTextSettings(context: Context): ReportTextSettings {
        val defaults = defaultReportTextSettings()
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return ReportTextSettings(
            title = prefs.getString(KEY_REPORT_TITLE, defaults.title) ?: defaults.title,
            legalLine = prefs.getString(KEY_REPORT_LEGAL_LINE, defaults.legalLine) ?: defaults.legalLine,
            defaultNote = prefs.getString(KEY_REPORT_DEFAULT_NOTE, defaults.defaultNote) ?: defaults.defaultNote,
            declaration = prefs.getString(KEY_REPORT_DECLARATION, defaults.declaration) ?: defaults.declaration,
            printDateLabel = prefs.getString(KEY_REPORT_PRINT_DATE_LABEL, defaults.printDateLabel) ?: defaults.printDateLabel,
            bottomWarning = prefs.getString(KEY_REPORT_BOTTOM_WARNING, defaults.bottomWarning) ?: defaults.bottomWarning,
            templateNotesList = run {
                val json = prefs.getString(KEY_TEMPLATE_NOTES_LIST, null)
                if (json.isNullOrBlank()) emptyList()
                else try {
                    val arr = JSONArray(json)
                    (0 until arr.length()).map { arr.getString(it) }
                } catch (_: Exception) { emptyList() }
            }
        )
    }

    fun saveReportTextSettings(context: Context, settings: ReportTextSettings) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_REPORT_TITLE, settings.title)
            .putString(KEY_REPORT_LEGAL_LINE, settings.legalLine)
            .putString(KEY_REPORT_DEFAULT_NOTE, settings.defaultNote)
            .putString(KEY_REPORT_DECLARATION, settings.declaration)
            .putString(KEY_REPORT_PRINT_DATE_LABEL, settings.printDateLabel)
            .putString(KEY_REPORT_BOTTOM_WARNING, settings.bottomWarning)
            .putString(KEY_TEMPLATE_NOTES_LIST, JSONArray(settings.templateNotesList).toString())
            .apply()
    }

    fun resetReportTextSettings(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_REPORT_TITLE)
            .remove(KEY_REPORT_LEGAL_LINE)
            .remove(KEY_REPORT_DEFAULT_NOTE)
            .remove(KEY_REPORT_DECLARATION)
            .remove(KEY_REPORT_PRINT_DATE_LABEL)
            .remove(KEY_REPORT_BOTTOM_WARNING)
            .remove(KEY_TEMPLATE_NOTES_LIST)
            .apply()
    }

    fun saveTemplateNotesList(context: Context, notes: List<String>) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TEMPLATE_NOTES_LIST, JSONArray(notes).toString())
            .apply()
    }

    fun saveSettings(
        context: Context,
        inspectorDetails: String,
        nextReportNumber: String
    ) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_INSPECTOR_DETAILS, inspectorDetails)
            .putString(KEY_NEXT_REPORT_NUMBER, nextReportNumber)
            .apply()
    }

    fun saveSettings(
        context: Context,
        firstName: String,
        lastName: String,
        certificateNumber: String,
        nextReportNumber: String
    ) {
        saveSettings(
            context = context,
            inspectorDetails = composeInspectorDetails(firstName, lastName, certificateNumber),
            nextReportNumber = nextReportNumber
        )
    }

    fun advanceToNextReportNumber(context: Context): String {
        val current = getNextReportNumber(context)
        val next = incrementReportNumber(current)
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_NEXT_REPORT_NUMBER, next).apply()
        return next
    }

    fun parseInspectorDetails(raw: String): InspectorDetails {
        val parts = raw.split("|")
        return if (parts.size >= 3) {
            InspectorDetails(parts[0].trim(), parts[1].trim(), parts[2].trim())
        } else {
            InspectorDetails("", "", raw.trim())
        }
    }

    fun composeInspectorDetails(firstName: String, lastName: String, certificateNumber: String): String {
        return listOf(firstName.trim(), lastName.trim(), certificateNumber.trim()).joinToString("|")
    }

    private fun copySelectedPngToInternalStorage(
        context: Context,
        sourceUri: Uri,
        targetFileName: String
    ): String {
        val folder = File(context.filesDir, ASSETS_FOLDER)
        if (!folder.exists()) folder.mkdirs()

        val targetFile = File(folder, targetFileName)

        context.contentResolver.openInputStream(sourceUri).use { input ->
            requireNotNull(input) { "לא ניתן לקרוא את קובץ ה-PNG שנבחר" }
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return targetFile.absolutePath
    }

    // חותמת/חתימה: JPEG דחוס לגודל מקסימלי לפני base64 — מונע OOM ממחרוזות גדולות ב-buildReportHtml
    private fun imageFileToCompressedDataUri(path: String, maxSidePx: Int, jpegQuality: Int): String {
        if (path.isBlank()) return ""
        val file = File(path)
        if (!file.exists()) return ""
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return ""
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > maxSidePx || bounds.outHeight / sampleSize > maxSidePx) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeFile(path, opts) ?: return ""
            val maxSide = maxOf(bitmap.width, bitmap.height)
            val scaled = if (maxSide <= maxSidePx) bitmap else {
                val ratio = maxSidePx.toFloat() / maxSide
                val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
                val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
                val s = Bitmap.createScaledBitmap(bitmap, w, h, true)
                bitmap.recycle()
                s
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
            if (scaled !== bitmap) scaled.recycle()
            val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64"
        } catch (_: Exception) { "" }
    }

    private fun incrementReportNumber(value: String): String {
        val clean = value.trim()
        val match = Regex("^(.*?)(\\d+)$").find(clean) ?: return clean
        val prefix = match.groupValues[1]
        val digits = match.groupValues[2]
        val nextNumber = digits.toLongOrNull()?.plus(1) ?: return clean
        val padded = nextNumber.toString().padStart(digits.length, '0')
        return prefix + padded
    }

    // ── פריסת סעיף 3 (פרטי זיהוי הקולט) ────────────────────
    private const val KEY_AIR_S3_LAYOUT = "air_s3_layout"

    val SECTION3_FIELD_LABELS = mapOf(
        "manufacturer"        to "יצרן",
        "model"               to "דגם",
        "serialNumber"        to "מספר סידורי",
        "manufactureYear"     to "שנת ייצור",
        "buildStandard"       to "תקן בנייה",
        "standardsApproval"   to "אישור תקנים",
        "material"            to "חומר",
        "volumeLiters"        to "נפח (ליטר)",
        "designPressureBar"   to "לחץ תכנון (בר)",
        "testPressureBar"     to "לחץ ניסוי (בר)",
        "diameterCm"          to "קוטר (ס\"מ)",
        "lengthCm"            to "אורך (ס\"מ)",
        "hasPressureGauge"    to "מד לחץ",
        "hasDrainValve"       to "שסתום ניקוז",
        "designTempC"         to "טמפ' תכנון (°C)",
        "safetyValveDiameter" to "קוטר שסתום בטיחות",
        "safetyValveType"     to "סוג שסתום בטיחות",
        "numInspectionPorts"  to "מספר פתחי בדיקה"
    )

    fun defaultAirS3Layout(): List<List<String>> = listOf(
        listOf("manufacturer",     "model",             "serialNumber"),
        listOf("manufactureYear",  "buildStandard",     "standardsApproval"),
        listOf("material",         "volumeLiters",      "designPressureBar"),
        listOf("testPressureBar",  "diameterCm",        "lengthCm"),
        listOf("hasPressureGauge", "hasDrainValve",     "designTempC"),
        listOf("safetyValveDiameter", "safetyValveType", "numInspectionPorts")
    )

    fun getAirS3Layout(context: Context): List<List<String>> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_AIR_S3_LAYOUT, null) ?: return defaultAirS3Layout()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val row = arr.getJSONArray(i)
                (0 until row.length()).map { j -> row.optString(j, "") }
            }
        } catch (_: Exception) { defaultAirS3Layout() }
    }

    fun saveAirS3Layout(context: Context, layout: List<List<String>>) {
        val arr = JSONArray()
        layout.forEach { row -> val r = JSONArray(); row.forEach { r.put(it) }; arr.put(r) }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_AIR_S3_LAYOUT, arr.toString()).apply()
    }

    // ── פריסת סעיף 6 (ממצאי הבדיקה הנוכחית) ──────────────────
    private const val KEY_AIR_S6_LAYOUT = "air_s6_layout"

    val SECTION6_FIELD_LABELS = mapOf(
        "actualWorkPressure"      to "לחץ עבודה בפועל (בר)",
        "minEnvelopeWall"         to "עובי דופן גלילי מינ' (מ\"מ)",
        "minCapWall"              to "עובי דופן כיפה מינ' (מ\"מ)",
        "accessoriesCondition"    to "מצב אבזרים",
        "safetyValveOpenPressure" to "לחץ פתיחת שסתום בטיחות (בר)",
        "externalCondition"       to "מצב חיצוני"
    )

    fun defaultAirS6Layout(): List<List<String>> = listOf(
        listOf("actualWorkPressure", "minEnvelopeWall",         "minCapWall"),
        listOf("accessoriesCondition", "safetyValveOpenPressure", "externalCondition")
    )

    fun getAirS6Layout(context: Context): List<List<String>> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_AIR_S6_LAYOUT, null) ?: return defaultAirS6Layout()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val row = arr.getJSONArray(i)
                (0 until row.length()).map { j -> row.optString(j, "") }
            }
        } catch (_: Exception) { defaultAirS6Layout() }
    }

    fun saveAirS6Layout(context: Context, layout: List<List<String>>) {
        val arr = JSONArray()
        layout.forEach { row -> val r = JSONArray(); row.forEach { r.put(it) }; arr.put(r) }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_AIR_S6_LAYOUT, arr.toString()).apply()
    }

    // ── סוגי שדות ואפשרויות רשימה (מצב עריכת טופס) ───────────
    private const val KEY_AIR_FIELD_SETTINGS = "air_field_settings"
    private const val KEY_AIR_FIELD_ORDER = "air_field_order"

    enum class AirFieldType(val label: String) {
        TEXT("טקסט חופשי"),
        MULTILINE_TEXT("טקסט רב שורות"),
        INTEGER("מספר שלם"),
        DECIMAL("מספר עשרוני"),
        DATE("תאריך"),
        BOOLEAN("כן / לא"),
        DROPDOWN("רשימה נפתחת"),
        READ_ONLY("קריאה בלבד")
    }

    data class AirFieldSettings(
        val type: AirFieldType = AirFieldType.TEXT,
        val options: List<String> = emptyList(),
        val isRequired: Boolean = false,
        val isMemoryKey: Boolean = false,
        val isHidden: Boolean = false
    )

    val AIR_FIELD_LABELS = linkedMapOf(
        // ── Section 1 ──────────────────────────────────────────
        "clientName"            to "שם לקוח (סעיף 1)",
        "clientAddress"         to "כתובת לקוח (סעיף 1)",
        "clientPhone"           to "טלפון / איש קשר (סעיף 1)",
        "inspectionLocation"    to "מקום הבדיקה (סעיף 1)",
        "prevReportNumber"      to "מספר תסקיר קודם (סעיף 1)",
        // ── Section 2 ──────────────────────────────────────────
        "receiverType"          to "סוג הקולט (סעיף 2)",
        "receiverDescription"   to "תיאור (סעיף 2)",
        // ── Section 3 ──────────────────────────────────────────
        "manufacturer"          to "יצרן (סעיף 3)",
        "model"                 to "דגם (סעיף 3)",
        "serialNumber"          to "מספר סידורי (סעיף 3)",
        "material"              to "חומר (סעיף 3)",
        "buildStandard"         to "תקן בנייה (סעיף 3)",
        "standardsApproval"     to "אישור תקנים (סעיף 3)",
        "volumeLiters"          to "נפח ליטר (סעיף 3)",
        "manufactureYear"       to "שנת ייצור (סעיף 3)",
        "diameterCm"            to "קוטר ס\"מ (סעיף 3)",
        "lengthCm"              to "אורך ס\"מ (סעיף 3)",
        "designPressureBar"     to "לחץ תכנון בר (סעיף 3)",
        "testPressureBar"       to "לחץ ניסוי בר (סעיף 3)",
        "designTempC"           to "טמפ' תכנון °C (סעיף 3)",
        "numInspectionPorts"    to "מספר פתחי בדיקה (סעיף 3)",
        "safetyValveDiameter"   to "קוטר שסתום בטיחות (סעיף 3)",
        "safetyValveType"       to "סוג שסתום בטיחות (סעיף 3)",
        // ── Section 4 ──────────────────────────────────────────
        "inspectionType"        to "סוג הבדיקה (סעיף 4)",
        // ── Section 5 ──────────────────────────────────────────
        "prevSpecType"          to "סוג בדיקה מיוחדת (סעיף 5)",
        "prevSpecBy"            to "בוצעה ע\"י (סעיף 5)",
        "prevSpecReportNum"     to "מספר תסקיר ב\"מ (סעיף 5)",
        "prevSpecInspectorNum"  to "מספר בודק (סעיף 5)",
        "prevSpecTestPressure"  to "לחץ ניסוי ב\"מ בר (סעיף 5)",
        "prevSpecSafetyValveDiam" to "קוטר שסתום ב\"מ (סעיף 5)",
        "prevSpecDrainValve"    to "שסתום ניקוז ב\"מ (סעיף 5)",
        "prevSpecMinCapWall"    to "עובי כיפה ב\"מ (סעיף 5)",
        // ── Section 6 ──────────────────────────────────────────
        "actualWorkPressure"    to "לחץ עבודה בפועל בר (סעיף 6)",
        "minEnvelopeWall"       to "עובי דופן גלילי מינ' (סעיף 6)",
        "minCapWall"            to "עובי כיפה מינ' (סעיף 6)",
        "accessoriesCondition"  to "מצב אבזרים (סעיף 6)",
        "safetyValveOpenPressure" to "לחץ פתיחת שסתום (סעיף 6)",
        "externalCondition"     to "מצב חיצוני (סעיף 6)",
        "externalConditionNote" to "הערה מצב חיצוני (סעיף 6)",
        // ── Section 8 ──────────────────────────────────────────
        "allowedWorkPressure"   to "לחץ עבודה מותר בר (סעיף 8)"
    )

    fun defaultAirFieldSettings(): Map<String, AirFieldSettings> = mapOf(
        // Section 1
        "clientName"            to AirFieldSettings(AirFieldType.TEXT),
        "clientAddress"         to AirFieldSettings(AirFieldType.TEXT),
        "clientPhone"           to AirFieldSettings(AirFieldType.TEXT),
        "inspectionLocation"    to AirFieldSettings(AirFieldType.TEXT),
        "prevReportNumber"      to AirFieldSettings(AirFieldType.TEXT),
        // Section 2
        "receiverType"          to AirFieldSettings(AirFieldType.DROPDOWN, listOf("קולט אוויר", "מיכל לחץ אוויר", "מיכל לחץ חנקן", "קומפרסור")),
        "receiverDescription"   to AirFieldSettings(AirFieldType.DROPDOWN, listOf("קולט אוויר אופקי עם מדחס בוכנתי (KW) 2.5")),
        // Section 3
        "manufacturer"          to AirFieldSettings(AirFieldType.DROPDOWN, listOf("fima")),
        "model"                 to AirFieldSettings(AirFieldType.DROPDOWN, listOf("60L")),
        "serialNumber"          to AirFieldSettings(AirFieldType.TEXT),
        "material"              to AirFieldSettings(AirFieldType.DROPDOWN, listOf("פלדה", "פלדה מרתכת", "נירוסטה 304", "נירוסטה 316", "אלומיניום")),
        "buildStandard"         to AirFieldSettings(AirFieldType.TEXT),
        "standardsApproval"     to AirFieldSettings(AirFieldType.DROPDOWN, listOf("ASME Section VIII", "EN 13445", "CS 2006", "PED 2014/68/EU")),
        "volumeLiters"          to AirFieldSettings(AirFieldType.DECIMAL),
        "manufactureYear"       to AirFieldSettings(AirFieldType.INTEGER),
        "diameterCm"            to AirFieldSettings(AirFieldType.DECIMAL),
        "lengthCm"              to AirFieldSettings(AirFieldType.DECIMAL),
        "designPressureBar"     to AirFieldSettings(AirFieldType.DECIMAL),
        "testPressureBar"       to AirFieldSettings(AirFieldType.DECIMAL),
        "designTempC"           to AirFieldSettings(AirFieldType.DECIMAL),
        "numInspectionPorts"    to AirFieldSettings(AirFieldType.DROPDOWN, listOf("0", "1", "2", "3", "4")),
        "safetyValveDiameter"   to AirFieldSettings(AirFieldType.TEXT),
        "safetyValveType"       to AirFieldSettings(AirFieldType.DROPDOWN, listOf("קפיצי", "קפיצי בלאנס-בלואן", "פתילה נמסה")),
        // Section 4
        "inspectionType"        to AirFieldSettings(AirFieldType.DROPDOWN, listOf("תקופתית", "ראשונה", "בדיקה מיוחדת — הידרוסטטית", "בדיקה מיוחדת — פנאומטית", "חידוש רישיון")),
        // Section 5
        "prevSpecType"          to AirFieldSettings(AirFieldType.DROPDOWN, listOf("בדיקה הידרוסטטית", "בדיקה פנאומטית", "בדיקת עובי דפנות")),
        "prevSpecBy"            to AirFieldSettings(AirFieldType.TEXT),
        "prevSpecReportNum"     to AirFieldSettings(AirFieldType.TEXT),
        "prevSpecInspectorNum"  to AirFieldSettings(AirFieldType.TEXT),
        "prevSpecTestPressure"  to AirFieldSettings(AirFieldType.DECIMAL),
        "prevSpecSafetyValveDiam" to AirFieldSettings(AirFieldType.TEXT),
        "prevSpecDrainValve"    to AirFieldSettings(AirFieldType.TEXT),
        "prevSpecMinCapWall"    to AirFieldSettings(AirFieldType.TEXT),
        // Section 6
        "actualWorkPressure"    to AirFieldSettings(AirFieldType.DECIMAL),
        "minEnvelopeWall"       to AirFieldSettings(AirFieldType.TEXT),
        "minCapWall"            to AirFieldSettings(AirFieldType.TEXT),
        "accessoriesCondition"  to AirFieldSettings(AirFieldType.DROPDOWN, listOf("תקין", "ליקויים מינוריים", "לא תקין", "לא נבדק")),
        "safetyValveOpenPressure" to AirFieldSettings(AirFieldType.DECIMAL),
        "externalCondition"     to AirFieldSettings(AirFieldType.DROPDOWN, listOf("תקין", "חלודה קלה", "חלודה בינונית", "חלודה חמורה", "לא תקין")),
        "externalConditionNote" to AirFieldSettings(AirFieldType.TEXT),
        // Section 8
        "allowedWorkPressure"   to AirFieldSettings(AirFieldType.DECIMAL)
    )

    fun getAirFieldSettings(context: Context): Map<String, AirFieldSettings> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_AIR_FIELD_SETTINGS, null) ?: return defaultAirFieldSettings()
        return try {
            val obj = JSONObject(json)
            val defaults = defaultAirFieldSettings()
            defaults.mapValues { (key, default) ->
                if (!obj.has(key)) default
                else {
                    val f = obj.getJSONObject(key)
                    val type = AirFieldType.entries.firstOrNull { it.name == f.optString("type") } ?: AirFieldType.TEXT
                    val optsArr = f.optJSONArray("options")
                    val opts = if (optsArr != null) (0 until optsArr.length()).map { optsArr.getString(it) }.distinct() else emptyList()
                    AirFieldSettings(type, opts, f.optBoolean("required", default.isRequired), f.optBoolean("memoryKey", default.isMemoryKey), f.optBoolean("hidden", false))
                }
            }
        } catch (_: Exception) { defaultAirFieldSettings() }
    }

    fun saveAirFieldSettings(context: Context, settings: Map<String, AirFieldSettings>) {
        val obj = JSONObject()
        settings.forEach { (key, s) ->
            val f = JSONObject()
            f.put("type", s.type.name)
            val optsArr = JSONArray(); s.options.forEach { optsArr.put(it) }
            f.put("options", optsArr)
            f.put("required", s.isRequired)
            f.put("memoryKey", s.isMemoryKey)
            f.put("hidden", s.isHidden)
            obj.put(key, f)
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_AIR_FIELD_SETTINGS, obj.toString()).apply()
    }

    fun getAirFieldOrder(context: Context): List<String> {
        val json = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_AIR_FIELD_ORDER, null) ?: return AIR_FIELD_LABELS.keys.toList()
        return try {
            val arr = JSONArray(json)
            val saved = (0 until arr.length()).map { arr.getString(it) }
            // Merge: saved order first, then any keys not in saved (new fields added in updates)
            val extra = AIR_FIELD_LABELS.keys.filter { it !in saved }
            saved + extra
        } catch (_: Exception) { AIR_FIELD_LABELS.keys.toList() }
    }

    fun saveAirFieldOrder(context: Context, order: List<String>) {
        val arr = JSONArray(); order.forEach { arr.put(it) }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_AIR_FIELD_ORDER, arr.toString()).apply()
    }

    // ── שדות מותאמים אישית לקולטי אוויר ──────────────────────────
    private const val KEY_AIR_CUSTOM_FIELDS = "air_custom_fields"

    data class AirCustomFieldDef(
        val id: String,
        val label: String,
        val section: Int,
        val type: AirFieldType = AirFieldType.TEXT,
        val options: List<String> = emptyList()
    )

    fun getAirCustomFieldDefs(context: Context): List<AirCustomFieldDef> {
        val json = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_AIR_CUSTOM_FIELDS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val type = AirFieldType.entries.firstOrNull { it.name == o.optString("type") } ?: AirFieldType.TEXT
                val optsArr = o.optJSONArray("options")
                val opts = if (optsArr != null) (0 until optsArr.length()).map { optsArr.getString(it) } else emptyList()
                AirCustomFieldDef(o.getString("id"), o.getString("label"), o.getInt("section"), type, opts)
            }
        } catch (_: Exception) { emptyList() }
    }

    fun saveAirCustomFieldDefs(context: Context, defs: List<AirCustomFieldDef>) {
        val arr = JSONArray()
        defs.forEach { d ->
            val o = JSONObject()
            o.put("id", d.id)
            o.put("label", d.label)
            o.put("section", d.section)
            o.put("type", d.type.name)
            val optsArr = JSONArray(); d.options.forEach { optsArr.put(it) }
            o.put("options", optsArr)
            arr.put(o)
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_AIR_CUSTOM_FIELDS, arr.toString()).apply()
    }

    // ── רשימות זיכרון שדות קולטי אוויר (תיאור / יצרן / דגם) ────
    private const val PREF_AIR_LISTS = "air_field_lists"
    const val AIR_LIST_DESCRIPTIONS = "descriptions"
    const val AIR_LIST_MANUFACTURERS = "manufacturers"
    const val AIR_LIST_MODELS = "models"

    fun getAirStringList(context: Context, listKey: String): List<String> {
        val json = context.getSharedPreferences(PREF_AIR_LISTS, Context.MODE_PRIVATE)
            .getString(listKey, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }

    fun addToAirStringList(context: Context, listKey: String, value: String) {
        val clean = value.trim()
        if (clean.isBlank()) return
        val current = getAirStringList(context, listKey).toMutableList()
        if (current.none { it.trim().equals(clean, ignoreCase = true) }) current.add(clean)
        val arr = JSONArray(); current.forEach { arr.put(it) }
        context.getSharedPreferences(PREF_AIR_LISTS, Context.MODE_PRIVATE)
            .edit().putString(listKey, arr.toString()).apply()
    }
}

