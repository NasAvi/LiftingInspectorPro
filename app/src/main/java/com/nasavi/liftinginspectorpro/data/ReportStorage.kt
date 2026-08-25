package com.nasavi.liftinginspectorpro.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * ReportStorage.kt
 *
 * קוד מלא 11:
 * 1. שומר "רשומת עבודה" אחת לכל מספר תסקיר — תמיד ניתנת לפתיחה ועריכה.
 * 2. שומר היסטוריית גרסאות PDF לאותו תסקיר:
 *    R6006, R6006.1, R6006.2 ...
 *
 * חשוב:
 * כרגע גרסת PDF נשמרת כ-HTML שממנו אפשר להפיק שוב PDF דרך מנגנון ההדפסה.
 * כלומר אנחנו לא דורסים גרסאות קודמות.
 */
object ReportStorage {

    private const val PREF_NAME = "report_storage"
    private const val KEY_WORKING_REPORTS = "working_reports"
    private const val KEY_PDF_VERSIONS = "pdf_versions"
    private const val KEY_OLD_REPORTS = "reports"

    data class StoredAccessoryRow(
        val description: String,
        val manufacturer: String,
        val model: String,
        val quantity: String,
        val serialNumbers: String,
        val testLoad: String,
        val wll: String
    )

    data class StoredDefectRow(
        val defectDescription: String,
        val fixUntil: String
    )

    data class StoredNoteRow(
        val text: String
    )

    /**
     * רשומת העבודה הראשית של התסקיר.
     * זו הרשומה שאפשר לפתוח שוב ולערוך.
     */
    data class WorkingReport(
        val reportNumber: String,
        val inspectorNumber: String,
        val inspectorFirstName: String = "",
        val inspectorLastName: String = "",
        val inspectorCertificateNumber: String = "",
        val inspectionDate: String,
        val nextInspectionDate: String,
        val owner: String,
        val address: String,
        val phone: String,
        val contactPerson: String = "",
        val vehicle: String,
        val inspectionLocation: String,
        // המקום הפיזי שבו הציוד נבדק: אתר בניה / מפעל / מחסן לוגיסטי.
        val inspectionPlaceType: String = "",
        val fixedNote: String,
        val generalNote: String,
        val accessories: List<StoredAccessoryRow>,
        val defects: List<StoredDefectRow>,
        val notes: List<StoredNoteRow>,
        val html: String,
        // קוד מלא 12:
        // true = כבר הופקה לפחות גרסת PDF אחת, ולכן לא מוסיפים אביזרים חדשים לתסקיר זה.
        val isLockedForNewAccessories: Boolean = false,
        val site: String = "",
        // מעקב שרשרת בדיקות: 5 בדיקות פנים-מפעליות רצופות, ואז בדיקה ע"י בודק מוסמך חיצוני שמאפסת את המחזור.
        val chainIndex: Int = 1,
        val previousReportNumber: String = "",
        val previousInspectionDate: String = "",
        val previousInspectorName: String = "",
        val externalReportNumber: String = "",
        val externalInspectorName: String = "",
        val externalInspectionDate: String = ""
    )

    /**
     * גרסת PDF סגורה/היסטורית.
     * למשל: R6006, R6006.1, R6006.2
     */
    data class PdfVersion(
        val baseReportNumber: String,
        val versionName: String,
        val date: String,
        val client: String,
        // עדכון 020:
        // כדי למנוע קריסות כשיש נספח תמונות, ה-HTML של גרסאות PDF נשמר בקובץ פנימי
        // ולא בתוך SharedPreferences. השדה html נשאר לתאימות לאחור בלבד.
        val html: String = "",
        val htmlFileName: String = ""
    )

    fun saveWorkingReport(context: Context, report: WorkingReport) {
        val updated = loadWorkingReports(context)
            .filterNot { it.reportNumber == report.reportNumber }
            .toMutableList()

        updated.add(report)
        saveWorkingReports(context, updated)
        InspectionReminderScheduler.scheduleReminder(context, report)
    }

    fun loadWorkingReports(context: Context): List<WorkingReport> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_WORKING_REPORTS, null)

        if (json != null) {
            val array = JSONArray(json)
            val result = mutableListOf<WorkingReport>()
            for (i in 0 until array.length()) {
                result.add(parseWorkingReport(context, array.getJSONObject(i)))
            }
            return result.sortedBy { it.reportNumber }
        }

        // תאימות לאחור לרשומות הישנות שנשמרו רק כ-HTML.
        // רשומות אלה יופיעו ברשימה, אבל לא תהיה בהן טבלת אביזרים לעריכה מלאה.
        val oldJson = prefs.getString(KEY_OLD_REPORTS, null) ?: return emptyList()
        val oldArray = JSONArray(oldJson)
        val migrated = mutableListOf<WorkingReport>()
        for (i in 0 until oldArray.length()) {
            val obj = oldArray.getJSONObject(i)
            migrated.add(
                WorkingReport(
                    reportNumber = obj.optString("reportNumber"),
                    inspectorNumber = "",
                    inspectorFirstName = "",
                    inspectorLastName = "",
                    inspectorCertificateNumber = "",
                    inspectionDate = obj.optString("date"),
                    nextInspectionDate = "",
                    owner = obj.optString("client"),
                    address = "",
                    phone = "",
                    contactPerson = "",
                    vehicle = "",
                    inspectionLocation = "",
                    inspectionPlaceType = "",
                    fixedNote = "לעבוד לפי הוראות היצרן וכללי הבטיחות המקובלים.",
                    generalNote = "",
                    accessories = emptyList(),
                    defects = emptyList(),
                    notes = emptyList(),
                    html = obj.optString("html"),
                    isLockedForNewAccessories = true
                )
            )
        }
        if (migrated.isNotEmpty()) saveWorkingReports(context, migrated)
        return migrated.sortedBy { it.reportNumber }
    }

    fun getWorkingReport(context: Context, reportNumber: String): WorkingReport? {
        return loadWorkingReports(context).firstOrNull { it.reportNumber == reportNumber }
    }

    fun getNextPdfVersionName(
        context: Context,
        baseReportNumber: String
    ): String {
        val existingForReport = loadPdfVersions(context)
            .filter { it.baseReportNumber == baseReportNumber }

        return if (existingForReport.isEmpty()) {
            baseReportNumber
        } else {
            "$baseReportNumber.${existingForReport.size}"
        }
    }


    /**
     * שמירת גרסת PDF חדשה.
     * אם אין גרסאות קודמות: R6006
     * אם כבר קיימת גרסה ראשונה: R6006.1
     * אחר כך: R6006.2 וכו'.
     */
    fun saveNewPdfVersion(context: Context, report: WorkingReport): PdfVersion {
        val versions = loadPdfVersions(context).toMutableList()
        val existingForReport = versions.filter { it.baseReportNumber == report.reportNumber }

        val versionName = if (existingForReport.isEmpty()) {
            report.reportNumber
        } else {
            "${report.reportNumber}.${existingForReport.size}"
        }

        val htmlFileName = buildPdfHtmlFileName(versionName)
        writePdfHtmlToFile(context, htmlFileName, report.html)

        val newVersion = PdfVersion(
            baseReportNumber = report.reportNumber,
            versionName = versionName,
            date = report.inspectionDate,
            client = report.owner,
            html = "",
            htmlFileName = htmlFileName
        )

        versions.add(newVersion)
        savePdfVersions(context, versions)
        return newVersion
    }

    fun deletePdfVersion(context: Context, version: PdfVersion) {
        if (version.htmlFileName.isNotBlank()) {
            runCatching { File(getPdfHtmlDir(context), version.htmlFileName).delete() }
        }
        val updated = loadPdfVersions(context).filterNot { it.versionName == version.versionName }
        savePdfVersions(context, updated)
    }

    fun loadPdfVersions(context: Context): List<PdfVersion> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PDF_VERSIONS, null) ?: return emptyList()
        val array = JSONArray(json)
        val result = mutableListOf<PdfVersion>()
        var migratedLegacyHtml = false

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val versionName = obj.optString("versionName")
            val legacyHtml = obj.optString("html")
            var htmlFileName = obj.optString("htmlFileName")

            // עדכון 020:
            // אם קיימת גרסה ישנה שבה ה-HTML נשמר עדיין בתוך SharedPreferences,
            // מעבירים אותו לקובץ פנימי חד-פעמית ומנקים את ה-JSON הכבד בשמירה הבאה.
            if (htmlFileName.isBlank() && legacyHtml.isNotBlank()) {
                htmlFileName = buildPdfHtmlFileName(versionName)
                writePdfHtmlToFile(context, htmlFileName, legacyHtml)
                migratedLegacyHtml = true
            }

            result.add(
                PdfVersion(
                    baseReportNumber = obj.optString("baseReportNumber"),
                    versionName = versionName,
                    date = obj.optString("date"),
                    client = obj.optString("client"),
                    html = if (htmlFileName.isBlank()) legacyHtml else "",
                    htmlFileName = htmlFileName
                )
            )
        }

        if (migratedLegacyHtml) {
            savePdfVersions(context, result)
        }

        return result
    }

    fun loadPdfVersionHtml(context: Context, version: PdfVersion): String {
        if (version.htmlFileName.isNotBlank()) {
            val file = File(getPdfHtmlDir(context), version.htmlFileName)
            if (file.exists()) {
                return file.readText(Charsets.UTF_8)
            }
        }
        return version.html
    }

    /**
     * קוד מלא 21:
     * עדכון HTML של גרסת PDF שכבר נשמרה.
     *
     * למה זה דרוש:
     * בעת הפקת PDF סופי אנחנו שומרים גרסת PDF, ואז שואלים את המשתמש אם לצרף תמונות.
     * אם המשתמש בוחר לצרף תמונות, צריך לעדכן את אותה גרסה שמורה עם HTML הכולל נספח תמונות.
     * כך ברשומות השמורות:
     * R6000 יכול להיפתח עם תמונות,
     * ו-R6000.1 יכול להיפתח בלי תמונות — לפי הבחירה בזמן ההפקה.
     */
    fun updatePdfVersionHtml(
        context: Context,
        versionName: String,
        html: String
    ) {
        val updated = loadPdfVersions(context).map { version ->
            if (version.versionName == versionName) {
                val htmlFileName = if (version.htmlFileName.isNotBlank()) {
                    version.htmlFileName
                } else {
                    buildPdfHtmlFileName(versionName)
                }
                writePdfHtmlToFile(context, htmlFileName, html)
                version.copy(html = "", htmlFileName = htmlFileName)
            } else {
                version
            }
        }

        savePdfVersions(context, updated)
    }

    /**
     * תאימות לאחור:
     * הקוד הישן קרא ל-loadReports כדי להציג כרטיסים.
     * עכשיו נחזיר את רשומות העבודה.
     */
    data class StoredReport(
        val reportNumber: String,
        val date: String,
        val client: String,
        val html: String
    )

    fun loadReports(context: Context): List<StoredReport> {
        return loadWorkingReports(context).map {
            StoredReport(
                reportNumber = it.reportNumber,
                date = it.inspectionDate,
                client = it.owner,
                html = it.html
            )
        }
    }

    fun saveReport(context: Context, report: StoredReport) {
        // נשאר רק כדי שקוד ישן לא יישבר.
        val working = WorkingReport(
            reportNumber = report.reportNumber,
            inspectorNumber = "",
            inspectorFirstName = "",
            inspectorLastName = "",
            inspectorCertificateNumber = "",
            inspectionDate = report.date,
            nextInspectionDate = "",
            owner = report.client,
            address = "",
            phone = "",
            contactPerson = "",
            vehicle = "",
            inspectionLocation = "",
            inspectionPlaceType = "",
            fixedNote = "לעבוד לפי הוראות היצרן וכללי הבטיחות המקובלים.",
            generalNote = "",
            accessories = emptyList(),
            defects = emptyList(),
            notes = emptyList(),
            html = report.html,
            isLockedForNewAccessories = false
        )
        saveWorkingReport(context, working)
    }

    private fun saveWorkingReports(context: Context, reports: List<WorkingReport>) {
        val array = JSONArray()
        reports.forEach { array.put(workingReportToJson(context, it)) }
        // commit() סינכרוני — מבטיח כתיבה לדיסק לפני שה-OOM יכול להרוג את הפרוצס
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_WORKING_REPORTS, array.toString())
            .commit()
    }

    private fun savePdfVersions(context: Context, versions: List<PdfVersion>) {
        val array = JSONArray()
        versions.forEach { version ->
            val htmlFileName = if (version.htmlFileName.isNotBlank()) {
                version.htmlFileName
            } else if (version.html.isNotBlank()) {
                val fileName = buildPdfHtmlFileName(version.versionName)
                writePdfHtmlToFile(context, fileName, version.html)
                fileName
            } else {
                ""
            }

            val obj = JSONObject()
            obj.put("baseReportNumber", version.baseReportNumber)
            obj.put("versionName", version.versionName)
            obj.put("date", version.date)
            obj.put("client", version.client)
            obj.put("htmlFileName", htmlFileName)
            // עדכון 020:
            // לא שומרים יותר html מלא ב-SharedPreferences.
            // במיוחד לא data:image Base64, כי זה גרם לקריסות לאחר כמה גרסאות PDF.
            array.put(obj)
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PDF_VERSIONS, array.toString())
            .apply()
    }

    private fun workingReportToJson(context: Context, report: WorkingReport): JSONObject {
        val obj = JSONObject()
        obj.put("reportNumber", report.reportNumber)
        obj.put("inspectorNumber", report.inspectorNumber)
        obj.put("inspectorFirstName", report.inspectorFirstName)
        obj.put("inspectorLastName", report.inspectorLastName)
        obj.put("inspectorCertificateNumber", report.inspectorCertificateNumber)
        obj.put("inspectionDate", report.inspectionDate)
        obj.put("nextInspectionDate", report.nextInspectionDate)
        obj.put("owner", report.owner)
        obj.put("address", report.address)
        obj.put("phone", report.phone)
        obj.put("contactPerson", report.contactPerson)
        obj.put("vehicle", report.vehicle)
        obj.put("inspectionLocation", report.inspectionLocation)
        obj.put("inspectionPlaceType", report.inspectionPlaceType)
        obj.put("fixedNote", report.fixedNote)
        obj.put("generalNote", report.generalNote)
        // עדכון 024:
        // גם HTML של רשומת העבודה נשמר בקובץ פנימי, ולא בתוך SharedPreferences.
        // זה מונע קריסות אחרי כמה הפקות תסקיר, במיוחד כאשר קיימות גרסאות PDF עם תמונות.
        val workingHtmlFileName = buildWorkingHtmlFileName(report.reportNumber)
        // כותבים לקובץ רק כאשר יש תוכן — כך לא מחקים HTML קיים בעת שמירת עדכוני מטה-דאטה
        if (report.html.isNotBlank()) {
            writeWorkingHtmlToFile(context, workingHtmlFileName, report.html)
        }
        obj.put("html", "")
        obj.put("htmlFileName", workingHtmlFileName)
        obj.put("isLockedForNewAccessories", report.isLockedForNewAccessories)
        obj.put("site", report.site)
        obj.put("chainIndex", report.chainIndex)
        obj.put("previousReportNumber", report.previousReportNumber)
        obj.put("previousInspectionDate", report.previousInspectionDate)
        obj.put("previousInspectorName", report.previousInspectorName)
        obj.put("externalReportNumber", report.externalReportNumber)
        obj.put("externalInspectorName", report.externalInspectorName)
        obj.put("externalInspectionDate", report.externalInspectionDate)

        val accessoriesArray = JSONArray()
        report.accessories.forEach {
            val row = JSONObject()
            row.put("description", it.description)
            row.put("manufacturer", it.manufacturer)
            row.put("model", it.model)
            row.put("quantity", it.quantity)
            row.put("serialNumbers", it.serialNumbers)
            row.put("testLoad", it.testLoad)
            row.put("wll", it.wll)
            accessoriesArray.put(row)
        }
        obj.put("accessories", accessoriesArray)

        val defectsArray = JSONArray()
        report.defects.forEach {
            val row = JSONObject()
            row.put("defectDescription", it.defectDescription)
            row.put("fixUntil", it.fixUntil)
            defectsArray.put(row)
        }
        obj.put("defects", defectsArray)

        val notesArray = JSONArray()
        report.notes.forEach {
            val row = JSONObject()
            row.put("text", it.text)
            notesArray.put(row)
        }
        obj.put("notes", notesArray)

        return obj
    }

    private fun parseWorkingReport(context: Context, obj: JSONObject): WorkingReport {
        val accessories = mutableListOf<StoredAccessoryRow>()
        val accessoriesArray = obj.optJSONArray("accessories") ?: JSONArray()
        for (i in 0 until accessoriesArray.length()) {
            val row = accessoriesArray.getJSONObject(i)
            accessories.add(
                StoredAccessoryRow(
                    description = row.optString("description"),
                    manufacturer = row.optString("manufacturer"),
                    model = row.optString("model"),
                    quantity = row.optString("quantity"),
                    serialNumbers = row.optString("serialNumbers"),
                    testLoad = row.optString("testLoad"),
                    wll = row.optString("wll")
                )
            )
        }

        val defects = mutableListOf<StoredDefectRow>()
        val defectsArray = obj.optJSONArray("defects") ?: JSONArray()
        for (i in 0 until defectsArray.length()) {
            val row = defectsArray.getJSONObject(i)
            defects.add(
                StoredDefectRow(
                    defectDescription = row.optString("defectDescription"),
                    fixUntil = row.optString("fixUntil")
                )
            )
        }

        val notes = mutableListOf<StoredNoteRow>()
        val notesArray = obj.optJSONArray("notes") ?: JSONArray()
        for (i in 0 until notesArray.length()) {
            val row = notesArray.getJSONObject(i)
            notes.add(StoredNoteRow(text = row.optString("text")))
        }

        return WorkingReport(
            reportNumber = obj.optString("reportNumber"),
            inspectorNumber = obj.optString("inspectorNumber"),
            inspectorFirstName = obj.optString("inspectorFirstName"),
            inspectorLastName = obj.optString("inspectorLastName"),
            inspectorCertificateNumber = obj.optString("inspectorCertificateNumber"),
            inspectionDate = obj.optString("inspectionDate"),
            nextInspectionDate = obj.optString("nextInspectionDate"),
            owner = obj.optString("owner"),
            address = obj.optString("address"),
            phone = obj.optString("phone"),
            contactPerson = obj.optString("contactPerson"),
            vehicle = obj.optString("vehicle"),
            inspectionLocation = obj.optString("inspectionLocation"),
            inspectionPlaceType = obj.optString("inspectionPlaceType"),
            fixedNote = obj.optString("fixedNote", "לעבוד לפי הוראות היצרן וכללי הבטיחות המקובלים."),
            generalNote = obj.optString("generalNote"),
            accessories = accessories,
            defects = defects,
            notes = notes,
            html = "",  // לא טוענים HTML מקובץ בעת loadWorkingReports — מונע OOM מ-25+ קבצי 2–3MB
            isLockedForNewAccessories = obj.optBoolean("isLockedForNewAccessories", false),
            site = obj.optString("site", ""),
            chainIndex = obj.optInt("chainIndex", 1),
            previousReportNumber = obj.optString("previousReportNumber", ""),
            previousInspectionDate = obj.optString("previousInspectionDate", ""),
            previousInspectorName = obj.optString("previousInspectorName", ""),
            externalReportNumber = obj.optString("externalReportNumber", ""),
            externalInspectorName = obj.optString("externalInspectorName", ""),
            externalInspectionDate = obj.optString("externalInspectionDate", "")
        )
    }


    private fun getWorkingHtmlDir(context: Context): File {
        return File(context.filesDir, "working_report_html").apply {
            if (!exists()) mkdirs()
        }
    }

    private fun buildWorkingHtmlFileName(reportNumber: String): String {
        val safeName = reportNumber
            .replace(Regex("[^A-Za-z0-9_.-]"), "_")
            .ifBlank { "report" }
        return "working_$safeName.html"
    }

    private fun writeWorkingHtmlToFile(context: Context, fileName: String, html: String) {
        if (fileName.isBlank()) return
        File(getWorkingHtmlDir(context), fileName).writeText(html, Charsets.UTF_8)
    }

    private fun getPdfHtmlDir(context: Context): File {
        return File(context.filesDir, "saved_pdf_html").apply {
            if (!exists()) mkdirs()
        }
    }

    private fun buildPdfHtmlFileName(versionName: String): String {
        val safeName = versionName
            .replace(Regex("[^A-Za-z0-9_.-]"), "_")
            .ifBlank { "report" }
        return "pdf_$safeName.html"
    }

    private fun writePdfHtmlToFile(context: Context, fileName: String, html: String) {
        if (fileName.isBlank()) return
        File(getPdfHtmlDir(context), fileName).writeText(html, Charsets.UTF_8)
    }

    /**
     * עדכון 011:
     * נשארה כפונקציית עזר להגנה בלבד אם נרצה לנקות HTML של רשומת עבודה ישנה מאוד.
     * עדכון 012: לא משתמשים בה לגרסאות PDF, כי אחרת חותמת/חתימה ונספח תמונות
     * נעלמים בעת פתיחה מרשומות שמורות.
     */
    private fun sanitizeHtmlForStorage(html: String): String {
        if (!html.contains("data:image/", ignoreCase = true)) return html

        return html.replace(
            Regex("data:image/[^;]+;base64,[^\"']+"),
            ""
        )
    }

}


