package com.nasavi.liftinginspectorpro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.nasavi.liftinginspectorpro.SavedInspection
import com.nasavi.liftinginspectorpro.data.ReportPhotoStorage
import com.nasavi.liftinginspectorpro.data.ReportStorage
import com.nasavi.liftinginspectorpro.utils.openPdfFile
import com.nasavi.liftinginspectorpro.utils.saveHtmlAsPdfAndOpen
import com.nasavi.liftinginspectorpro.utils.sharePdfFile
import com.nasavi.liftinginspectorpro.data.themedButtonBorder
import com.nasavi.liftinginspectorpro.data.themedTitleBorder

/**
 * מסך רשומות שמורות.
 *
 * מבנה המסך לפי בקשת המשתמש:
 * 1. המסך הראשון הוא תפריט משנה בלבד:
 *    למעלה שורת חיפוש קבועה, ומתחתיה כפתורי מספרי תסקיר בשני טורים.
 * 2. במסך הראשון לא מציגים פרטי תסקיר, לא כרטיסי פירוט ולא כפתור "פתח לעריכה".
 * 3. רק לאחר לחיצה על מספר תסקיר, נפתח מסך פרטים של אותו תסקיר.
 * 4. במסך הפרטים אפשר לפתוח רשומת עבודה לעריכה ולפתוח גרסאות PDF שהופקו.
 */
@Composable
fun SavedRecordsScreen(
    reports: List<SavedInspection>,
    onEditReport: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val themeState = com.nasavi.liftinginspectorpro.data.AppThemeState.current
    val bgColor = themeState.backgroundColor
    val borderColor = themeState.borderColor
    val titleColor = themeState.titleColor
    val buttonColor = themeState.buttonColor
    var pdfVersions by remember { mutableStateOf(ReportStorage.loadPdfVersions(context)) }

    // ברירת מחדל לחיפוש לפי המספור הקיים שלך.
    var searchText by remember { mutableStateOf("") }
    var activeSearchText by remember { mutableStateOf("") }

    // null = המסך הראשון, תפריט מספרי התסקירים.
    // כאשר יש ערך, מוצג מסך פרטים של התסקיר שנבחר.
    var selectedReportNumber by remember { mutableStateOf<String?>(null) }
    var pdfLoading by remember { mutableStateOf(false) }
    var pendingPdfFile by remember { mutableStateOf<java.io.File?>(null) }

    val allBaseReportNumbers = remember(reports, pdfVersions) {
        (reports.map { it.reportNumber } + pdfVersions.map { it.baseReportNumber })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(compareByDescending<String> { extractReportNumberDigits(it) }.thenByDescending { it })
    }

    val normalizedSearch = activeSearchText.filter { it.isDigit() }
    val filteredReportNumbers = allBaseReportNumbers.filter { reportNumber ->
        val reportDigits = reportNumber.filter { it.isDigit() }
        normalizedSearch.isBlank() || reportDigits.contains(normalizedSearch)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .border(10.dp, borderColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp)
                .background(titleColor)
                .themedTitleBorder()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (selectedReportNumber == null) "רשומות שמורות" else "תסקיר מס' $selectedReportNumber",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        if (selectedReportNumber == null) {
            SavedRecordsMenuContent(
                searchText = searchText,
                onSearchTextChange = { value ->
                    val cleaned = value.filter { it.isDigit() }
                    searchText = cleaned
                    activeSearchText = cleaned
                },
                onSearchClick = { activeSearchText = searchText },
                reportNumbers = filteredReportNumbers,
                onReportClick = { selectedReportNumber = it },
                onBack = onBack
            )
        } else {
            val reportNumber = selectedReportNumber ?: ""
            SavedRecordDetailsContent(
                reportNumber = reportNumber,
                report = reports.firstOrNull { it.reportNumber == reportNumber },
                pdfVersions = pdfVersions.filter { it.baseReportNumber == reportNumber },
                onEditReport = onEditReport,
                onOpenPdf = { version ->
                    val htmlContent = ReportStorage.loadPdfVersionHtml(context, version)
                    if (htmlContent.isBlank()) {
                        android.widget.Toast.makeText(context, "קובץ HTML לא נמצא — ייתכן שנמחק עם מחיקת האפליקציה", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        pdfLoading = true
                        saveHtmlAsPdfAndOpen(
                            context = context,
                            htmlContent = htmlContent,
                            fileName = "תסקיר_${version.versionName}"
                        ) { file ->
                            pdfLoading = false
                            pendingPdfFile = file
                        }
                    }
                },
                onDeleteVersion = { version ->
                    ReportStorage.deletePdfVersion(context, version)
                    pdfVersions = ReportStorage.loadPdfVersions(context)
                },
                onBackToList = { selectedReportNumber = null }
            )
        }
    }

    if (pdfLoading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("מייצר PDF...", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.padding(7.dp))
                    Text("אנא המתן, תהליך זה עשוי לארוך עד כחצי דקה.", textAlign = TextAlign.Center)
                }
            },
            confirmButton = {}
        )
    }

    pendingPdfFile?.let { pdfFile ->
        AlertDialog(
            onDismissRequest = { pendingPdfFile = null },
            title = { Text("תסקיר PDF מוכן") },
            text = { Text("בחר פעולה:") },
            confirmButton = {
                Button(onClick = {
                    openPdfFile(context, pdfFile)
                    pendingPdfFile = null
                }) { Text("הצג PDF") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        sharePdfFile(context, pdfFile)
                        pendingPdfFile = null
                    }) { Text("שתף") }
                    TextButton(onClick = { pendingPdfFile = null }) { Text("סגור") }
                }
            }
        )
    }
}

@Composable
private fun ColumnScope.SavedRecordsMenuContent(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    reportNumbers: List<String>,
    onReportClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val buttonColor = com.nasavi.liftinginspectorpro.data.AppThemeState.current.buttonColor

    // שורת החיפוש מחוץ לרשימה הנגללת כדי שתישאר תמיד למעלה.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "חפש תסקיר\nמס'",
            modifier = Modifier.weight(1.0f),
            textAlign = TextAlign.Right,
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            singleLine = true,
            modifier = Modifier.weight(1.7f),
            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center)
        )

    }

    Spacer(modifier = Modifier.padding(10.dp))

    LazyColumn(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (reportNumbers.isEmpty()) {
            item {
                Text(
                    text = "לא נמצאו תסקירים לפי החיפוש",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            reportNumbers.chunked(2).forEach { rowItems ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { reportNumber ->
                            Button(
                                onClick = { onReportClick(reportNumber) },
                                modifier = Modifier.weight(1f).themedButtonBorder(),
                                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
                            ) {
                                Text(reportNumber)
                            }
                        }

                        // בשורה האחרונה, אם יש כפתור אחד בלבד, שומרים על מבנה שני טורים.
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.padding(8.dp))

    Button(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth().themedButtonBorder(),
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
    ) {
        Text("חזרה לתפריט ראשי")
    }
}

@Composable
private fun ColumnScope.SavedRecordDetailsContent(
    reportNumber: String,
    report: SavedInspection?,
    pdfVersions: List<ReportStorage.PdfVersion>,
    onEditReport: (String) -> Unit,
    onOpenPdf: (ReportStorage.PdfVersion) -> Unit,
    onDeleteVersion: (ReportStorage.PdfVersion) -> Unit,
    onBackToList: () -> Unit
) {
    val buttonColor = com.nasavi.liftinginspectorpro.data.AppThemeState.current.buttonColor
    var versionToDelete by remember { mutableStateOf<ReportStorage.PdfVersion?>(null) }

    LazyColumn(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "רשומת עבודה",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            if (report == null) {
                Text("אין רשומת עבודה לעריכה לתסקיר זה")
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("מספר תסקיר: ${report.reportNumber}")
                        Text("תאריך בדיקה: ${report.date}")
                        Text("לקוח: ${report.client}")
                        Text(report.summary)

                        Button(
                            onClick = { onEditReport(report.reportNumber) },
                            colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                            modifier = Modifier.fillMaxWidth().themedButtonBorder()
                        ) {
                            Text("פתח לעריכה")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "גרסאות PDF שהופקו",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (pdfVersions.isEmpty()) {
            item {
                Text("עדיין אין גרסאות PDF לתסקיר זה")
            }
        } else {
            pdfVersions.forEach { version ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onOpenPdf(version) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(version.versionName)
                        }
                        TextButton(
                            onClick = { versionToDelete = version },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFC62828))
                        ) { Text("✕ מחק") }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.padding(8.dp))

    Button(
        onClick = onBackToList,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("חזרה לרשימת תסקירים")
    }

    if (versionToDelete != null) {
        val version = versionToDelete!!
        AlertDialog(
            onDismissRequest = { versionToDelete = null },
            title = { Text("מחיקת גרסה ${version.versionName}") },
            text = { Text("הגרסה ${version.versionName} תימחק לצמיתות. לא ניתן לשחזרה.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteVersion(version)
                        versionToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFC62828))
                ) { Text("אישור מחיקה") }
            },
            dismissButton = {
                TextButton(onClick = { versionToDelete = null }) { Text("ביטול") }
            }
        )
    }
}

private fun extractReportNumberDigits(value: String): Int {
    return value.filter { it.isDigit() }.toIntOrNull() ?: 0
}

/**
 * מסיר נספח תמונות קיים מה-HTML של תסקיר.
 * פורמט חדש: <div id='photoAppendix'> (PdfGenerator מוסיף spacer ב-JS)
 * פורמט ישן: <div class='pageBreak'></div> (תסקירים ישנים שנשמרו לפני העדכון)
 */
private fun stripPhotoAppendix(html: String): String {
    val newMarker = html.indexOf("<div id='photoAppendix'>")
    val markerIdx = if (newMarker != -1) newMarker else html.indexOf("<div class='pageBreak'></div>")
    if (markerIdx == -1) return html
    val bodyCloseIdx = html.lastIndexOf("</body>")
    return if (bodyCloseIdx > markerIdx) html.substring(0, markerIdx) + html.substring(bodyCloseIdx)
    else html
}


