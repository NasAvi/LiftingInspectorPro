package com.nasavi.liftinginspectorpro.ui.screens

import androidx.compose.material3.ExperimentalMaterial3Api
import com.nasavi.liftinginspectorpro.data.themedButtonBorder
import com.nasavi.liftinginspectorpro.data.themedTitleBorder
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nasavi.liftinginspectorpro.SavedInspection
import com.nasavi.liftinginspectorpro.data.ClientMemoryStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private fun parseDateOrNull(text: String): LocalDate? =
    try { LocalDate.parse(text.trim(), DATE_FMT) } catch (_: Exception) { null }

private val renewalLight = Color(0xFFFFF3E0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenewalScreen(
    allReports: List<SavedInspection>,
    onRenewReport: (sourceReportNumber: String) -> Unit,
    onBack: () -> Unit,
    excludedReportNumbers: Set<String> = emptySet(),
    onStartNewRenewalSession: () -> Unit = {},
    customerQuery: String = "",
    onCustomerQueryChange: (String) -> Unit = {},
    monthsText: String = "",
    onMonthsTextChange: (String) -> Unit = {}
) {
    val ctx = LocalContext.current
    val clientStore = remember { ClientMemoryStore(ctx) }
    val today = remember { LocalDate.now() }
    val renewalColor = com.nasavi.liftinginspectorpro.data.AppThemeState.current.buttonColor
    val titleColor = com.nasavi.liftinginspectorpro.data.AppThemeState.current.titleColor
    val borderColor = com.nasavi.liftinginspectorpro.data.AppThemeState.current.borderColor
    val bgColor = com.nasavi.liftinginspectorpro.data.AppThemeState.current.backgroundColor

    // מספר התסקיר שנמצא כרגע בתהליך חידוש (לאינדיקטור טעינה)
    var renewingNumber by remember { mutableStateOf<String?>(null) }

    // ── מקטע א: לפי מספר תסקיר ──
    var selectedReportNumber by remember { mutableStateOf("") }
    var reportDropdownOpen by remember { mutableStateOf(false) }
    // מסנן תסקירים שכבר חודשו
    val sortedReports = remember(allReports, excludedReportNumbers) {
        allReports.filter { it.reportNumber !in excludedReportNumbers }.sortedByDescending { it.reportNumber }
    }
    val selectedReport = remember(selectedReportNumber, allReports, excludedReportNumbers) {
        if (selectedReportNumber in excludedReportNumbers) null
        else allReports.firstOrNull { it.reportNumber == selectedReportNumber }
    }

    // ── מקטע ב: לפי לקוח ──
    var clientDropdownOpen by remember { mutableStateOf(false) }
    val clientSuggestions by remember(customerQuery) {
        derivedStateOf { clientStore.searchClients(customerQuery, 8) }
    }

    // רשימה מלאה (לפני גריעת מחודשים בסשן הנוכחי)
    val allFilteredCustomerReports = remember(customerQuery, monthsText, allReports) {
        val months = monthsText.trim().toIntOrNull() ?: return@remember emptyList<SavedInspection>()
        val maxDate = today.plusMonths(months.toLong())
        val normQuery = customerQuery.trim().lowercase()
        if (normQuery.length < 2) return@remember emptyList()
        allReports
            .filter { r -> r.client.trim().lowercase().startsWith(normQuery) && r.nextInspectionDate.isNotBlank() }
            .mapNotNull { r ->
                val nd = parseDateOrNull(r.nextInspectionDate) ?: return@mapNotNull null
                if (!nd.isAfter(maxDate)) r to nd else null
            }
            .sortedWith(compareBy({ it.second }, { it.first.reportNumber }))
            .map { it.first }
    }

    // רשימה גלויה — ללא מחודשים
    val visibleCustomerReports = remember(allFilteredCustomerReports, excludedReportNumbers) {
        allFilteredCustomerReports.filter { it.reportNumber !in excludedReportNumbers }
    }

    val allRenewedForFilter = allFilteredCustomerReports.isNotEmpty() &&
            visibleCustomerReports.isEmpty() &&
            excludedReportNumbers.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .border(10.dp, borderColor)
    ) {
        // ── כותרת ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(titleColor)
                .themedTitleBorder()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("חידוש תסקירי אביזרים", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ════════════════════════════════
            // מקטע א — חידוש לפי מספר תסקיר
            // ════════════════════════════════
            SectionCard(title = "חידוש לפי מספר תסקיר", color = titleColor) {

                if (sortedReports.isEmpty() && allReports.isEmpty()) {
                    Text("אין תסקירים שמורים", color = Color.Gray, fontSize = 13.sp)
                } else if (sortedReports.isEmpty()) {
                    Text("כל התסקירים כבר חודשו בסשן זה", color = Color.Gray, fontSize = 13.sp)
                } else {
                    ExposedDropdownMenuBox(
                        expanded = reportDropdownOpen,
                        onExpandedChange = { reportDropdownOpen = it }
                    ) {
                        OutlinedTextField(
                            value = selectedReportNumber.ifBlank { "בחר מספר תסקיר לחידוש" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("מספר תסקיר") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reportDropdownOpen) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = reportDropdownOpen,
                            onDismissRequest = { reportDropdownOpen = false }
                        ) {
                            sortedReports.forEach { r ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(r.reportNumber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text("${r.client}  |  ${r.date}", fontSize = 12.sp, color = Color.DarkGray)
                                        }
                                    },
                                    onClick = {
                                        selectedReportNumber = r.reportNumber
                                        reportDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }

                    if (selectedReport != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, renewalColor, RoundedCornerShape(8.dp))
                                .background(renewalLight, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("פרטי התסקיר לחידוש", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = renewalColor)
                            InfoRow("מספר תסקיר", selectedReport.reportNumber)
                            InfoRow("לקוח / מפעל", selectedReport.client)
                            InfoRow("תאריך בדיקה אחרון", selectedReport.date)
                            if (selectedReport.nextInspectionDate.isNotBlank()) {
                                val nd = parseDateOrNull(selectedReport.nextInspectionDate)
                                val expired = nd != null && nd.isBefore(today)
                                InfoRow(
                                    "תאריך בדיקה הבאה",
                                    selectedReport.nextInspectionDate,
                                    valueColor = if (expired) Color(0xFFC62828) else Color(0xFF1B5E20)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("שדות שיועברו: לקוח, כתובת, טלפון, רכב, אביזרים, הערות", fontSize = 11.sp, color = Color.Gray)
                            Text("שדות שיתאפסו: תאריך, מיקום, ליקויים", fontSize = 11.sp, color = Color(0xFFBF360C))
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                if (renewingNumber == null) {
                                    renewingNumber = selectedReportNumber
                                    onRenewReport(selectedReportNumber)
                                }
                            },
                            enabled = renewingNumber == null,
                            modifier = Modifier.fillMaxWidth().themedButtonBorder(),
                            colors = ButtonDefaults.buttonColors(containerColor = renewalColor)
                        ) {
                            if (renewingNumber == selectedReportNumber) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("מחדש...", fontWeight = FontWeight.Bold)
                            } else {
                                Text("חדש תסקיר $selectedReportNumber", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = renewalColor),
                modifier = Modifier.fillMaxWidth().themedButtonBorder()
            ) {
                Text("חזרה לתפריט אביזרים", fontWeight = FontWeight.Bold)
            }

            // ════════════════════════════════
            // מקטע ב — חידוש ללקוח / מפעל
            // ════════════════════════════════
            SectionCard(title = "חידוש לפי לקוח / מפעל", color = titleColor) {

                Box {
                    OutlinedTextField(
                        value = customerQuery,
                        onValueChange = {
                            onCustomerQueryChange(it)
                            clientDropdownOpen = it.length >= 2
                        },
                        label = { Text("שם לקוח / מפעל") },
                        placeholder = { Text("הקלד לפחות 2 אותיות") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    DropdownMenu(
                        expanded = clientDropdownOpen && clientSuggestions.isNotEmpty(),
                        onDismissRequest = { clientDropdownOpen = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        clientSuggestions.forEach { client ->
                            DropdownMenuItem(
                                text = { Text(client.name) },
                                onClick = {
                                    onCustomerQueryChange(client.name)
                                    clientDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = monthsText,
                    onValueChange = { onMonthsTextChange(it.filter { ch -> ch.isDigit() }.take(2)) },
                    label = { Text("הצג תסקירים שתוקפם פג בעוד כמה חודשים") },
                    placeholder = { Text("לדוגמה: 2") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                val months = monthsText.trim().toIntOrNull()
                when {
                    customerQuery.trim().length < 2 ->
                        Text("הקלד לפחות 2 אותיות לחיפוש לקוח", fontSize = 12.sp, color = Color.Gray)
                    months == null ->
                        Text("הכנס מספר חודשים", fontSize = 12.sp, color = Color.Gray)
                    allRenewedForFilter -> {
                        // ── הודעת סיום ──
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(2.dp, Color(0xFF2E7D32), RoundedCornerShape(10.dp))
                                .background(Color(0xFFE8F5E9), RoundedCornerShape(10.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "✓ משימת חידוש התסקירים הושלמה!",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF1B5E20)
                            )
                            Text(
                                "כל ${allFilteredCustomerReports.size} תסקירי ${customerQuery.trim()} חודשו בהצלחה",
                                fontSize = 13.sp,
                                color = Color(0xFF2E7D32)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = onBack,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = renewalColor)
                            ) { Text("חזור לתפריט אביזרים", fontWeight = FontWeight.Bold) }
                            OutlinedButton(
                                onClick = {
                                    onCustomerQueryChange("")
                                    onMonthsTextChange("")
                                    onStartNewRenewalSession()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("חידוש תסקירים נוסף") }
                        }
                    }
                    visibleCustomerReports.isEmpty() ->
                        Text("אין תסקירים שתוקפם פג בעוד $months חודשים ללקוח זה", fontSize = 13.sp, color = Color.Gray)
                    else -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("נמצאו ${visibleCustomerReports.size} תסקירים לחידוש:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        visibleCustomerReports.forEach { r ->
                            val nd = parseDateOrNull(r.nextInspectionDate)
                            val daysLeft = nd?.let { today.until(it).days }
                            val expired = nd != null && nd.isBefore(today)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .border(1.dp, if (expired) Color(0xFFC62828) else Color(0xFF1565C0), RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(r.reportNumber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(r.client, fontSize = 12.sp, color = Color.DarkGray)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("בדיקה: ${r.date}", fontSize = 11.sp, color = Color.Gray)
                                        if (r.nextInspectionDate.isNotBlank()) {
                                            Text(
                                                "תוקף: ${r.nextInspectionDate}${if (expired) " ✗" else ""}",
                                                fontSize = 11.sp,
                                                color = if (expired) Color(0xFFC62828) else Color(0xFF2E7D32)
                                            )
                                        }
                                    }
                                    if (expired) Text("פג תוקף", fontSize = 11.sp, color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                                    else if (daysLeft != null) Text("פג בעוד $daysLeft ימים", fontSize = 11.sp, color = Color(0xFFE65100))
                                }
                                Button(
                                    onClick = {
                                        if (renewingNumber == null) {
                                            renewingNumber = r.reportNumber
                                            onRenewReport(r.reportNumber)
                                        }
                                    },
                                    enabled = renewingNumber == null,
                                    modifier = Modifier.themedButtonBorder(),
                                    colors = ButtonDefaults.buttonColors(containerColor = renewalColor),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    if (renewingNumber == r.reportNumber) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text("חדש")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, color: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, color, RoundedCornerShape(12.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(color, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$label:", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.width(120.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

