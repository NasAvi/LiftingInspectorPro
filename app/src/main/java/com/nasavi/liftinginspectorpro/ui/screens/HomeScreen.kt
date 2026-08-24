package com.nasavi.liftinginspectorpro.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import com.nasavi.liftinginspectorpro.data.AppTheme
import com.nasavi.liftinginspectorpro.data.AppThemeState
import com.nasavi.liftinginspectorpro.data.InspectorSettingsStorage
import com.nasavi.liftinginspectorpro.data.ReportStorage
import com.nasavi.liftinginspectorpro.data.themeBackgroundColors
import com.nasavi.liftinginspectorpro.data.themeBorderColors
import com.nasavi.liftinginspectorpro.data.themeButtonColors
import com.nasavi.liftinginspectorpro.data.themedButtonBorder
import com.nasavi.liftinginspectorpro.data.themedTitleBorder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun HomeScreen(
    inspectorDetails: String = "",
    nextReportNumber: String = "",
    inspectorStampPath: String = "",
    inspectorSignaturePath: String = "",
    reportTextSettings: InspectorSettingsStorage.ReportTextSettings = InspectorSettingsStorage.defaultReportTextSettings(),
    onNewInspectionClick: () -> Unit = {},
    onSavedRecordsClick: () -> Unit = {},
    onManageListsClick: () -> Unit = {},
    onSmartAccessoriesClick: (() -> Unit)? = null,
    onSaveSettings: (String, String) -> Unit = { _, _ -> },
    onSaveReportTextSettings: (InspectorSettingsStorage.ReportTextSettings) -> Unit = {},
    onResetReportTextSettings: () -> InspectorSettingsStorage.ReportTextSettings = { InspectorSettingsStorage.defaultReportTextSettings() },
    onStampSelected: (Uri) -> Unit = {},
    onSignatureSelected: (Uri) -> Unit = {},
    onRemoveStamp: () -> Unit = {},
    onRemoveSignature: () -> Unit = {},
    onImportTemplateSelected: (android.net.Uri) -> Unit = {},
    onRenewalClick: (() -> Unit)? = null,
    onBackToMain: (() -> Unit)? = null,
    onGeneralSettingsSaved: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    val theme = com.nasavi.liftinginspectorpro.data.AppThemeState.current
    val lightGreen = theme.backgroundColor
    val borderWidth = 10.dp
    val borderColor = theme.borderColor
    val titleColor = theme.titleColor
    val buttonColor = theme.buttonColor

    var reportTextDialogOpen by remember { mutableStateOf(false) }
    var generalSettingsOpen by remember { mutableStateOf(false) }

    fun exportAccessoryTemplatesToDownloads() {
        val prefs = ctx.getSharedPreferences("lifting_inspection_prefs", Context.MODE_PRIVATE)
        val templates = loadSmartAccessoryTemplates(prefs)
        if (templates.isEmpty()) { Toast.makeText(ctx, "אין תבניות לייצוא", Toast.LENGTH_SHORT).show(); return }
        val templatesArr = JSONArray()
        templates.forEach { tpl -> templatesArr.put(tpl.toJsonObject()) }
        val bundle = JSONObject()
            .put("exportVersion", 1).put("exportType", "accessory_templates_bundle")
            .put("exportDate", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
            .put("templates", templatesArr)
        val jsonStr = bundle.toString(2)
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val fileName = "accessory_templates_$dateStr.json"
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
                handler.post { Toast.makeText(ctx, "יוצאו ${templates.size} תבניות → $fileName", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                handler.post { Toast.makeText(ctx, "שגיאה: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    var accessoryExportInProgress by remember { mutableStateOf(false) }
    var accessoryMetaExportInProgress by remember { mutableStateOf(false) }

    fun doExportAccessoryMetadataToDownloads() {
        if (accessoryMetaExportInProgress) return
        val versions = ReportStorage.loadPdfVersions(ctx)
        if (versions.isEmpty()) { Toast.makeText(ctx, "אין תסקירים לייצוא", Toast.LENGTH_SHORT).show(); return }
        accessoryMetaExportInProgress = true
        Thread {
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            try {
                val detObj = InspectorSettingsStorage.getInspectorDetailsObject(ctx)
                val inspectorName = listOf(detObj.firstName, detObj.lastName).filter { it.isNotBlank() }.joinToString(" ")
                val reportsArr = org.json.JSONArray()
                versions.forEach { v ->
                    reportsArr.put(org.json.JSONObject()
                        .put("baseReportNumber", v.baseReportNumber)
                        .put("versionName", v.versionName)
                        .put("date", v.date)
                        .put("client", v.client)
                        .put("htmlFileName", "${v.versionName}.html"))
                }
                val bundle = org.json.JSONObject()
                    .put("exportVersion", 1)
                    .put("exportType", "accessory_reports_bundle")
                    .put("exportDate", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
                    .put("inspectorName", inspectorName)
                    .put("reports", reportsArr)
                val jsonStr = bundle.toString(2)
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val fileName = "accessory_reports_export_$dateStr.json"
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
                handler.post {
                    showExportDialog(ctx, "ייצוא רשימת תסקירים הושלם",
                        "יוצאו ${versions.size} תסקירים\nקובץ: $fileName\nמיקום: תיקיית Download בנייד\nהעברה למחשב: USB ← תיקיית Download",
                        "Download/$fileName")
                    accessoryMetaExportInProgress = false
                }
            } catch (e: Throwable) {
                handler.post {
                    Toast.makeText(ctx, "שגיאה: ${e.message}", Toast.LENGTH_LONG).show()
                    accessoryMetaExportInProgress = false
                }
            }
        }.start()
    }

    fun doExportAccessoryReportsToDownloads() {
        if (accessoryExportInProgress) return
        val versions = ReportStorage.loadPdfVersions(ctx)
        if (versions.isEmpty()) { Toast.makeText(ctx, "אין תסקירים לייצוא", Toast.LENGTH_SHORT).show(); return }
        accessoryExportInProgress = true
        Toast.makeText(ctx, "בודק ${versions.size} תסקירים... אנא המתן", Toast.LENGTH_LONG).show()
        Thread {
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            try {
                val relPath = "Download/LiftingInspection/Accessories/"
                var exported = 0
                var skipped = 0
                val newVersionNames = mutableListOf<String>()
                versions.forEach { version ->
                    val fileName = "${version.versionName}.html"
                    val existsCursor = ctx.contentResolver.query(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Downloads._ID, MediaStore.MediaColumns.DATA),
                        "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                        arrayOf(fileName, relPath), null
                    )
                    val alreadyExists = existsCursor?.use { c ->
                        if (c.moveToFirst()) {
                            val dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                            val path = if (dataIdx >= 0) c.getString(dataIdx) else null
                            path != null && java.io.File(path).exists()
                        } else false
                    } ?: false
                    if (alreadyExists) { skipped++; return@forEach }
                    val html = ReportStorage.loadPdfVersionHtml(ctx, version)
                    if (html.isBlank()) return@forEach
                    val values = android.content.ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "text/html")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@forEach
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(html.toByteArray(Charsets.UTF_8)) }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    ctx.contentResolver.update(uri, values, null, null)
                    exported++
                    newVersionNames.add(version.versionName)
                }
                handler.post {
                    val skipNote = if (skipped > 0) "\n$skipped קבצים כבר קיימים ודולגו" else ""
                    val nums = newVersionNames.mapNotNull { n ->
                        n.replace(Regex("[^0-9.]"), "").split(".").firstOrNull()?.toIntOrNull()
                    }.distinct()
                    val rangeNote = when {
                        nums.size > 1 -> "\nלטעינה במחשב: R${nums.minOrNull()} עד R${nums.maxOrNull()}"
                        nums.size == 1 -> "\nלטעינה במחשב: R${nums[0]}"
                        else -> ""
                    }
                    showExportDialog(ctx, "ייצוא קבצי HTML הושלם",
                        "יוצאו $exported קבצים חדשים$skipNote$rangeNote\nתיקייה:\nDownload/LiftingInspection/Accessories/\nהעברה למחשב: USB ← אותה תיקייה",
                        "Download/LiftingInspection/Accessories/")
                    accessoryExportInProgress = false
                }
            } catch (e: Throwable) {
                handler.post {
                    Toast.makeText(ctx, "שגיאה בייצוא: ${e.message}", Toast.LENGTH_LONG).show()
                    accessoryExportInProgress = false
                }
            }
        }.start()
    }

    val templateImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportTemplateSelected(uri)
    }

    val parsedInspector = remember(inspectorDetails) { parseInspectorDetailsForHome(inspectorDetails) }

    var reportTitleInput by remember(reportTextSettings) { mutableStateOf(reportTextSettings.title) }
    var reportLegalLineInput by remember(reportTextSettings) { mutableStateOf(reportTextSettings.legalLine) }
    var reportDefaultNoteInput by remember(reportTextSettings) { mutableStateOf(reportTextSettings.defaultNote) }
    var reportDeclarationInput by remember(reportTextSettings) { mutableStateOf(reportTextSettings.declaration) }
    var reportPrintDateLabelInput by remember(reportTextSettings) { mutableStateOf(reportTextSettings.printDateLabel) }
    var reportBottomWarningInput by remember(reportTextSettings) { mutableStateOf(reportTextSettings.bottomWarning) }
    val templateNotesListInput = remember(reportTextSettings) {
        mutableStateListOf<String>().also { it.addAll(reportTextSettings.templateNotesList) }
    }

    val fullName = listOf(parsedInspector.firstName, parsedInspector.lastName)
        .filter { it.isNotBlank() }
        .joinToString(" ")

    val inspectorDisplay = if (fullName.isBlank() && parsedInspector.certificateNumber.isBlank()) {
        "לא הוגדר"
    } else {
        "$fullName | מס' בודק: ${parsedInspector.certificateNumber}".trim().trim('|').trim()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(lightGreen)
            .border(borderWidth, borderColor)
            .statusBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 30.dp)
                .background(titleColor)
                .themedTitleBorder()
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "בדיקת אביזרי הרמה",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("פרטי בודק: $inspectorDisplay")
            Text("מספר תסקיר הבא: ${nextReportNumber.ifBlank { "לא הוגדר" }}")

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { generalSettingsOpen = true },
                modifier = Modifier.fillMaxWidth().themedButtonBorder(),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
                Text("הגדרות כלליות")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { reportTextDialogOpen = true },
                modifier = Modifier.fillMaxWidth().themedButtonBorder(),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
                Text("הגדרות נוסח התסקיר")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { templateImportLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.fillMaxWidth().themedButtonBorder(),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
                Text("ייבוא תבנית מקובץ")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { exportAccessoryTemplatesToDownloads() },
                modifier = Modifier.fillMaxWidth().themedButtonBorder(),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
                Text("ייצוא תבניות למחשב")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { doExportAccessoryMetadataToDownloads() },
                modifier = Modifier.fillMaxWidth().themedButtonBorder(),
                enabled = !accessoryMetaExportInProgress,
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
                Text(if (accessoryMetaExportInProgress) "מייצא..." else "ייצוא רשימת תסקירים למחשב")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { doExportAccessoryReportsToDownloads() },
                modifier = Modifier.fillMaxWidth().themedButtonBorder(),
                enabled = !accessoryExportInProgress,
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
                Text(if (accessoryExportInProgress) "מייצא..." else "ייצוא קבצי HTML תסקירים למחשב")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onNewInspectionClick,
                modifier = Modifier.fillMaxWidth().themedButtonBorder(),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
                Text("עריכת תסקיר אביזרים")
            }

            if (onSmartAccessoriesClick != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onSmartAccessoriesClick,
                    modifier = Modifier.fillMaxWidth().themedButtonBorder(),
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
                ) {
                    Text("טופס חכם אביזרים")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onSavedRecordsClick,
                modifier = Modifier.fillMaxWidth().themedButtonBorder(),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
                Text("רשומות שמורות")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onManageListsClick,
                modifier = Modifier.fillMaxWidth().themedButtonBorder(),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
                Text("ניהול רשימות בחירה")
            }

            if (onRenewalClick != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRenewalClick,
                    modifier = Modifier.fillMaxWidth().themedButtonBorder(),
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
                ) {
                    Text("חידוש תסקירים")
                }
            }

            if (onBackToMain != null) {
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onBackToMain,
                    modifier = Modifier.fillMaxWidth().themedButtonBorder(),
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
                ) {
                    Text("חזרה למסך הראשי")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }

    if (generalSettingsOpen) {
        GeneralSettingsDialog(
            onDismiss = { generalSettingsOpen = false },
            onSaved = { onGeneralSettingsSaved?.invoke() }
        )
    }

    if (reportTextDialogOpen) {
        AlertDialog(
            onDismissRequest = { reportTextDialogOpen = false },
            title = { Text("הגדרות נוסח התסקיר") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "ניתן להשתמש במשתנים: {שם הבודק}, {מספר הסמכה}, {מספר תסקיר}, {תאריך בדיקה}, {תאריך בדיקה הבאה}, {תאריך הדפסה}, {לקוח}, {תאריך+6חודשים}",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Right
                    )

                    OutlinedTextField(
                        value = reportTitleInput,
                        onValueChange = { reportTitleInput = it },
                        label = { Text("כותרת ראשית") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = reportLegalLineInput,
                        onValueChange = { reportLegalLineInput = it },
                        label = { Text("שורת חוק / תקנות מתחת לכותרת") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    OutlinedTextField(
                        value = reportDefaultNoteInput,
                        onValueChange = { reportDefaultNoteInput = it },
                        label = { Text("הערת ברירת מחדל בהערות נוספות") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    OutlinedTextField(
                        value = reportDeclarationInput,
                        onValueChange = { reportDeclarationInput = it },
                        label = { Text("נוסח הצהרת הבודק בתחתית התסקיר") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4
                    )

                    OutlinedTextField(
                        value = reportPrintDateLabelInput,
                        onValueChange = { reportPrintDateLabelInput = it },
                        label = { Text("תווית תאריך הדפסה") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = reportBottomWarningInput,
                        onValueChange = { reportBottomWarningInput = it },
                        label = { Text("הערת אזהרה אדומה בתחתית") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    Text(
                        text = "הערות ברירת מחדל (יופיעו בכל תסקיר חדש ויסונכרנו עם התסקיר):",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Right
                    )
                    templateNotesListInput.forEachIndexed { index, note ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = note,
                                onValueChange = { templateNotesListInput[index] = it },
                                label = { Text("הערה ${index + 1}") },
                                modifier = Modifier.weight(1f),
                                minLines = 1
                            )
                            TextButton(onClick = { templateNotesListInput.removeAt(index) }) {
                                Text("הסר")
                            }
                        }
                    }
                    TextButton(
                        onClick = { templateNotesListInput.add("") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+ הוסף הערה")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveReportTextSettings(
                            InspectorSettingsStorage.ReportTextSettings(
                                title = reportTitleInput.trim(),
                                legalLine = reportLegalLineInput.trim(),
                                defaultNote = reportDefaultNoteInput.trim(),
                                declaration = reportDeclarationInput.trim(),
                                printDateLabel = reportPrintDateLabelInput.trim(),
                                bottomWarning = reportBottomWarningInput.trim(),
                                templateNotesList = templateNotesListInput.map { it.trim() }.filter { it.isNotBlank() }
                            )
                        )
                        reportTextDialogOpen = false
                    }
                ) {
                    Text("שמור נוסח")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            val defaults = onResetReportTextSettings()
                            reportTitleInput = defaults.title
                            reportLegalLineInput = defaults.legalLine
                            reportDefaultNoteInput = defaults.defaultNote
                            reportDeclarationInput = defaults.declaration
                            reportPrintDateLabelInput = defaults.printDateLabel
                            reportBottomWarningInput = defaults.bottomWarning
                            templateNotesListInput.clear()
                            templateNotesListInput.addAll(defaults.templateNotesList)
                        }
                    ) {
                        Text("איפוס לברירת מחדל")
                    }
                    TextButton(onClick = { reportTextDialogOpen = false }) {
                        Text("ביטול")
                    }
                }
            }
        )
    }
}

private data class HomeInspectorDetails(
    val firstName: String,
    val lastName: String,
    val certificateNumber: String
)

private fun parseInspectorDetailsForHome(raw: String): HomeInspectorDetails {
    val parts = raw.split("|")
    return if (parts.size >= 3) {
        HomeInspectorDetails(parts[0].trim(), parts[1].trim(), parts[2].trim())
    } else {
        HomeInspectorDetails("", "", raw.trim())
    }
}

internal fun showExportDialog(context: android.content.Context, title: String, message: String, path: String) {
    android.app.AlertDialog.Builder(context)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton("אישור") { d, _ -> d.dismiss() }
        .setNeutralButton("העתק נתיב") { _, _ ->
            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("export_path", path))
            Toast.makeText(context, "הנתיב הועתק ללוח", Toast.LENGTH_SHORT).show()
        }
        .show()
}

private fun composeInspectorDetailsForHome(
    firstName: String,
    lastName: String,
    certificateNumber: String
): String {
    return listOf(firstName.trim(), lastName.trim(), certificateNumber.trim()).joinToString("|")
}

@Composable
private fun GeneralSettingsDialog(onDismiss: () -> Unit, onSaved: () -> Unit = {}) {
    val context = LocalContext.current
    val initInspector = remember { InspectorSettingsStorage.getInspectorDetailsObject(context) }
    var firstName by remember { mutableStateOf(initInspector.firstName) }
    var lastName by remember { mutableStateOf(initInspector.lastName) }
    var certNumber by remember { mutableStateOf(initInspector.certificateNumber) }
    var stampPath by remember { mutableStateOf(InspectorSettingsStorage.getStampPath(context)) }
    var signaturePath by remember { mutableStateOf(InspectorSettingsStorage.getSignaturePath(context)) }
    var company by remember { mutableStateOf(InspectorSettingsStorage.getCompanyDetails(context)) }
    var logoPath by remember { mutableStateOf(InspectorSettingsStorage.getLogoPath(context)) }
    var nextAccessoriesNum by remember { mutableStateOf(InspectorSettingsStorage.getNextReportNumber(context)) }
    var tab by remember { mutableStateOf(0) }
    var tempTheme by remember { mutableStateOf(AppThemeState.current) }
    var pickerCategory by remember { mutableStateOf("") }
    var pickerOpen by remember { mutableStateOf(false) }
    var pickerInitialColor by remember { mutableStateOf(Color.White) }

    val stampPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) stampPath = InspectorSettingsStorage.saveStampFromUri(context, uri)
    }
    val signaturePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) signaturePath = InspectorSettingsStorage.saveSignatureFromUri(context, uri)
    }
    val logoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) logoPath = InspectorSettingsStorage.saveLogoFromUri(context, uri)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false, usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        title = { Text("הגדרות כלליות") },
        text = {
            Column {
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }) { Text("בודק פנים מפעלי", modifier = Modifier.padding(6.dp), fontSize = 11.sp) }
                    Tab(selected = tab == 1, onClick = { tab = 1 }) { Text("חתימה/חותמת מפעל", modifier = Modifier.padding(6.dp), fontSize = 11.sp) }
                    Tab(selected = tab == 2, onClick = { tab = 2 }) { Text("מספרי תסקיר", modifier = Modifier.padding(6.dp), fontSize = 11.sp) }
                    Tab(selected = tab == 3, onClick = { tab = 3 }) { Text("צבעים", modifier = Modifier.padding(6.dp), fontSize = 11.sp) }
                }
                Column(
                    modifier = Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (tab) {
                        0 -> {
                            OutlinedTextField(value = firstName, onValueChange = { firstName = it }, label = { Text("שם פרטי") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = lastName, onValueChange = { lastName = it }, label = { Text("שם משפחה") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = certNumber, onValueChange = { certNumber = it.filter { ch -> ch.isDigit() } }, label = { Text("מספר הסמכה / מספר בודק") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                        1 -> {
                            OutlinedTextField(value = company.companyName, onValueChange = { company = company.copy(companyName = it) }, label = { Text("שם המפעל") }, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("לוגו:", fontWeight = FontWeight.Bold)
                            Text(if (logoPath.isNotBlank()) "לוגו קיים" else "לא נבחר לוגו", fontSize = 12.sp)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { logoPickerLauncher.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) {
                                    Text(if (logoPath.isNotBlank()) "החלף לוגו" else "בחר לוגו PNG/JPG")
                                }
                                if (logoPath.isNotBlank()) {
                                    TextButton(onClick = { InspectorSettingsStorage.removeLogo(context); logoPath = "" }, modifier = Modifier.weight(1f)) { Text("הסר") }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("חותמת:", fontWeight = FontWeight.Bold)
                            Text(if (stampPath.isNotBlank()) "חותמת קיימת" else "לא הוגדרה חותמת", fontSize = 12.sp)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { stampPickerLauncher.launch(arrayOf("image/png", "image/jpeg")) }, modifier = Modifier.weight(1f)) { Text("בחר חותמת") }
                                if (stampPath.isNotBlank()) {
                                    TextButton(onClick = { InspectorSettingsStorage.removeStamp(context); stampPath = "" }, modifier = Modifier.weight(1f)) { Text("הסר") }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("חתימה:", fontWeight = FontWeight.Bold)
                            Text(if (signaturePath.isNotBlank()) "חתימה קיימת" else "לא הוגדרה חתימה", fontSize = 12.sp)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { signaturePickerLauncher.launch(arrayOf("image/png", "image/jpeg")) }, modifier = Modifier.weight(1f)) { Text("בחר חתימה") }
                                if (signaturePath.isNotBlank()) {
                                    TextButton(onClick = { InspectorSettingsStorage.removeSignature(context); signaturePath = "" }, modifier = Modifier.weight(1f)) { Text("הסר") }
                                }
                            }
                        }
                        2 -> {
                            Text("מספר תסקיר הבא — אביזרי הרמה", fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = nextAccessoriesNum,
                                onValueChange = { nextAccessoriesNum = it },
                                label = { Text("מספר תסקיר (לדוגמה: R6175)") },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(textDirection = TextDirection.Ltr)
                            )
                        }
                        3 -> {
                            Text("צבעי ממשק האפליקציה", fontWeight = FontWeight.Bold)
                            Text("לחץ על ריבוע הצבע או 'בחר' לפתיחת בורר הצבעים", fontSize = 11.sp, color = Color.Gray)
                            Spacer(Modifier.height(4.dp))
                            ColorPickerRow("רקע המסך", tempTheme.backgroundColor) { pickerCategory = "bg"; pickerInitialColor = tempTheme.backgroundColor; pickerOpen = true }
                            ColorPickerRow("מסגרת/גבול", tempTheme.borderColor) { pickerCategory = "border"; pickerInitialColor = tempTheme.borderColor; pickerOpen = true }
                            ColorPickerRow("כותרת מסך", tempTheme.titleColor) { pickerCategory = "title"; pickerInitialColor = tempTheme.titleColor; pickerOpen = true }
                            ColorPickerRow("כפתורים", tempTheme.buttonColor) { pickerCategory = "button"; pickerInitialColor = tempTheme.buttonColor; pickerOpen = true }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text("מסגרת כפתורים", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            ColorPickerRow("גוון", tempTheme.buttonBorderColor) { pickerCategory = "btnBorderColor"; pickerInitialColor = tempTheme.buttonBorderColor; pickerOpen = true }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("עובי: ${tempTheme.buttonBorderWidth}dp", modifier = Modifier.width(80.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Slider(value = tempTheme.buttonBorderWidth.toFloat(), onValueChange = { tempTheme = tempTheme.copy(buttonBorderWidth = it.toInt()) }, valueRange = 0f..8f, steps = 7, modifier = Modifier.weight(1f))
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text("מסגרת כותרת", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            ColorPickerRow("גוון", tempTheme.titleBorderColor) { pickerCategory = "titleBorderColor"; pickerInitialColor = tempTheme.titleBorderColor; pickerOpen = true }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("עובי: ${tempTheme.titleBorderWidth}dp", modifier = Modifier.width(80.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Slider(value = tempTheme.titleBorderWidth.toFloat(), onValueChange = { tempTheme = tempTheme.copy(titleBorderWidth = it.toInt()) }, valueRange = 0f..8f, steps = 7, modifier = Modifier.weight(1f))
                            }
                            if (pickerOpen) {
                                ColorPickerDialog(
                                    title = when (pickerCategory) {
                                        "bg" -> "בחר צבע רקע"; "border" -> "בחר צבע מסגרת"
                                        "title" -> "בחר צבע כותרת"; "button" -> "בחר צבע כפתורים"
                                        "btnBorderColor" -> "גוון מסגרת כפתורים"; "titleBorderColor" -> "גוון מסגרת כותרת"
                                        else -> "בחר צבע"
                                    },
                                    initialColor = pickerInitialColor,
                                    presetColors = when (pickerCategory) { "bg" -> themeBackgroundColors; "button" -> themeButtonColors; else -> themeBorderColors },
                                    onColorSelected = { color ->
                                        tempTheme = when (pickerCategory) {
                                            "bg" -> tempTheme.copy(backgroundColor = color); "border" -> tempTheme.copy(borderColor = color)
                                            "title" -> tempTheme.copy(titleColor = color); "button" -> tempTheme.copy(buttonColor = color)
                                            "btnBorderColor" -> tempTheme.copy(buttonBorderColor = color); "titleBorderColor" -> tempTheme.copy(titleBorderColor = color)
                                            else -> tempTheme
                                        }
                                        pickerOpen = false
                                    },
                                    onDismiss = { pickerOpen = false }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                InspectorSettingsStorage.saveSettings(context, firstName, lastName, certNumber, nextAccessoriesNum.trim())
                InspectorSettingsStorage.saveCompanyDetails(context, company)
                AppThemeState.save(context, tempTheme)
                onSaved()
                onDismiss()
            }) { Text("שמור") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ביטול") }
        }
    )
}

@Composable
private fun ColorPickerRow(label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, modifier = Modifier.width(80.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Box(modifier = Modifier.weight(1f).height(36.dp).background(color).border(1.dp, Color.Gray).clickable { onClick() })
        OutlinedButton(onClick = onClick, modifier = Modifier.width(56.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            Text("בחר", fontSize = 12.sp)
        }
    }
}

@Composable
private fun ColorPickerDialog(title: String, initialColor: Color, presetColors: List<Color>, onColorSelected: (Color) -> Unit, onDismiss: () -> Unit) {
    val initHsv = remember(initialColor.toArgb()) { FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor.toArgb(), it) } }
    var hue by remember { mutableStateOf(initHsv[0]) }
    var sat by remember { mutableStateOf(initHsv[1]) }
    var bri by remember { mutableStateOf(initHsv[2]) }
    var selectedTab by remember { mutableStateOf(0) }
    var pendingColor by remember { mutableStateOf(initialColor) }
    val customColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, bri)))
    val activeColor = if (selectedTab == 0) pendingColor else customColor

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                androidx.compose.material3.TabRow(selectedTabIndex = selectedTab) {
                    androidx.compose.material3.Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("רגיל", fontSize = 13.sp) })
                    androidx.compose.material3.Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("מותאם אישית", fontSize = 13.sp) })
                }
                if (selectedTab == 0) {
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(5), modifier = Modifier.height(160.dp)) {
                        items(presetColors.size) { i ->
                            val c = presetColors[i]
                            Box(modifier = Modifier.padding(4.dp).aspectRatio(1f).background(c).border(if (c == pendingColor) 3.dp else 1.dp, if (c == pendingColor) Color.Black else Color.Gray).clickable { pendingColor = c })
                        }
                    }
                } else {
                    Text("גוון (Hue)", fontSize = 12.sp); Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                    Text("רוויה (Saturation)", fontSize = 12.sp); Slider(value = sat, onValueChange = { sat = it }, valueRange = 0f..1f)
                    Text("בהירות (Brightness)", fontSize = 12.sp); Slider(value = bri, onValueChange = { bri = it }, valueRange = 0f..1f)
                }
                Box(modifier = Modifier.fillMaxWidth().height(40.dp).background(activeColor).border(1.dp, Color.Gray))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("ביטול") }
                    TextButton(onClick = { onColorSelected(activeColor) }) { Text("בחר") }
                }
            }
        }
    }
}

