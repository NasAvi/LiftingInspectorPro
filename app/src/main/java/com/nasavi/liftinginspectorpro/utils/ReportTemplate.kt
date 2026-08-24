package com.nasavi.liftinginspectorpro.utils

import com.nasavi.liftinginspectorpro.data.InspectorSettingsStorage
import com.nasavi.liftinginspectorpro.data.InspectorSettingsStorage.ReportTextSettings
import java.util.Calendar

fun buildReportHtml(
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
    accessoryRowsHtml: String,
    defectRowsHtml: String,
    fixedNote: String,
    generalNote: String,
    serialNotesHtml: String,
    inspectorStampDataUri: String = "",
    inspectorSignatureDataUri: String = "",
    reportTextSettings: ReportTextSettings = InspectorSettingsStorage.defaultReportTextSettings(),
    logoDataUri: String = "",
    company: InspectorSettingsStorage.CompanyDetails = InspectorSettingsStorage.CompanyDetails(),
    compact: Boolean = false
): String {
    val certificateToShow = inspectorCertificateNumber.ifBlank { inspectorNumber }
    val inspectorFullName = listOf(inspectorFirstName, inspectorLastName)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { "__________" }

    val sixMonthsLaterDate = try {
        val parts = inspectionDate.split("/")
        if (parts.size == 3) {
            val cal = Calendar.getInstance()
            cal.set(parts[2].toInt(), parts[1].toInt() - 1, parts[0].toInt())
            cal.add(Calendar.MONTH, 6)
            "%02d/%02d/%04d".format(
                cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.YEAR)
            )
        } else inspectionDate
    } catch (_: Exception) { inspectionDate }

    val variables = mapOf(
        "שם הבודק" to inspectorFullName,
        "מספר הסמכה" to certificateToShow,
        "מספר בודק" to certificateToShow,
        "מספר תסקיר" to runningNumber,
        "תאריך בדיקה" to inspectionDate,
        "תאריך בדיקה הבאה" to nextInspectionDate,
        "תאריך הדפסה" to inspectionDate,
        "לקוח" to owner,
        "תאריך+6חודשים" to sixMonthsLaterDate
    )

    val titleHtml = renderReportText(reportTextSettings.title, variables)
    val legalLineHtml = renderReportText(reportTextSettings.legalLine, variables)
    val defaultNoteHtml = renderReportText(
        reportTextSettings.defaultNote.ifBlank { fixedNote },
        variables
    )
    val declarationHtml = renderReportText(reportTextSettings.declaration, variables)
    val printDateLabelHtml = renderReportText(reportTextSettings.printDateLabel, variables)
    val bottomWarningHtml = renderReportText(reportTextSettings.bottomWarning, variables)

    val logoHtml = if (logoDataUri.isNotBlank())
        """<img src="$logoDataUri" style="max-width:55mm;max-height:22mm;object-fit:contain;" />"""
    else ""
    val companyLines = buildList {
        if (company.companyName.isNotBlank()) add("<b>${safe(company.companyName)}</b>")
        if (company.companyTitle.isNotBlank()) add(safe(company.companyTitle))
        val addrParts = listOf(company.address, company.city).filter { it.isNotBlank() }
        if (addrParts.isNotEmpty()) add(addrParts.joinToString(", ") { safe(it) })
        if (company.phone.isNotBlank()) add("טל': ${safe(company.phone)}")
    }
    val companyHtml = companyLines.joinToString("<br>")

    return """
<!DOCTYPE html>
<html lang="he" dir="rtl">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=595">
<style>
body { font-family: Arial; direction: rtl; margin: 0; font-size: ${if (compact) "9px" else "11px"}; line-height: ${if (compact) "1.1" else "1.2"}; width: 100%; box-sizing: border-box; }
.page { width: 100%; margin-top: ${if (compact) "2px" else "4px"}; box-sizing: border-box; }
.title-main { text-align: center; font-size: 16px; font-weight: bold; text-decoration: underline; }
.title-sub { text-align: center; font-size: 9px; }
.report-number { text-align: center; font-size: 12px; font-weight: bold; }
.header-table { width: 100%; border-collapse: collapse; border-bottom: 2px solid black; margin-bottom: 5px; }
.header-table td { vertical-align: middle; padding: 2px 4px; }
.header-logo { width: 22%; text-align: center; }
.header-title { width: 56%; text-align: center; }
.header-company { width: 22%; text-align: right; font-size: 8.5px; line-height: 1.4; }
.info-table { width: 100%; border-collapse: collapse; border: 1.5px solid black; margin-bottom: 6px; }
.info-table td { border: 1px solid #999; padding: 3px; font-size: ${if (compact) "9px" else "11px"}; text-align: right; }
.info-label { background: #eeeeee; font-weight: bold; white-space: nowrap; }
.main-table { width: 100%; border-collapse: collapse; margin-top: 4px; }
.main-table th, .main-table td { border: 1px solid black; padding: 3px; font-size: ${if (compact) "9px" else "11px"}; white-space: pre-line; text-align: center; }
.main-table th { background: #dce8dc; }
.main-table th:nth-child(2), .main-table td:nth-child(2) { text-align: right; }
.bottom-section { width: 100%; border-collapse: collapse; margin-top: 6px; }
.bottom-section th, .bottom-section td { border: 1px solid black; padding: 3px; font-size: ${if (compact) "9px" else "11px"}; }
.bottom-hdr { background: #dce8dc; font-weight: bold; }
.decl-box { border: 1px solid black; border-top: none; padding: 5px 3px; margin-top: 0; }
.declaration-block { margin-top: 6px; font-size: ${if (compact) "9px" else "11px"}; line-height: 1.2; text-align: center; }
.signature-table { width: 100%; margin-top: 8px; border-collapse: collapse; }
.signature-table td { font-size: ${if (compact) "9px" else "11px"}; vertical-align: middle; }
.stamp-signature { width: 150px; max-height: 90px; object-fit: contain; }
.inspector-signature-image { max-width: 150px; max-height: 45px; object-fit: contain; display: block; margin: 0 auto 2px auto; }
.inspector-stamp-image { max-width: 150px; max-height: 70px; object-fit: contain; display: block; margin: 0 auto; }
.warning { color: #b00000; text-align: center; font-weight: bold; margin-top: 2px; font-size: 9px; }
</style>
</head>
<body>
<div class="page">
<table class="header-table"><tr>
<td class="header-company">$companyHtml</td>
<td class="header-title">
<div class="title-main">$titleHtml</div>
<div class="title-sub">$legalLineHtml</div>
<div class="report-number">תסקיר בדיקה מס': ${safe(runningNumber)} / ${safe(certificateToShow)}</div>
</td>
<td class="header-logo">$logoHtml</td>
</tr></table>

<table class="info-table">
<tr><td class="info-label">תאריך בדיקה:</td>
<td>${safe(inspectionDate)}</td>
<td class="info-label">תאריך בדיקה הבאה:</td>
<td>${safe(nextInspectionDate)}</td></tr>
<tr><td class="info-label">לקוח:</td>
<td>${safe(owner)}</td>
<td class="info-label">טלפון / איש קשר:</td>
<td>${safe(phone)}&nbsp;&nbsp;&nbsp;${safe(contactPerson)}</td>
</tr>
<tr><td class="info-label">כתובת:</td>
<td>${safe(address)}</td>
<td class="info-label">מס' רכב:</td>
<td>${safe(vehicle)}</td></tr>
<tr><td class="info-label">כתובת הבדיקה:</td>
<td>${safe(inspectionLocation)}</td>
<td class="info-label">הציוד נבדק ב:</td>
<td>${safe(inspectionPlaceType)}</td>
</tr>
</table>

<table class="main-table">
<colgroup>
    <col style="width: 4%;">
    <col style="width: 36%;">
    <col style="width: 8%;">
    <col style="width: 8%;">
    <col style="width: 6%;">
    <col style="width: 12%;">
    <col style="width: 13%;">
    <col style="width: 13%;">
</colgroup>
<tr><th>מס'</th><th>תיאור אביזר ההרמה</th><th>יצרן</th><th>דגם</th><th>כמות</th><th>מס' זיהוי</th><th>עומס מבחן (טון)</th><th>עומס עבודה בטוח (טון)</th></tr>
$accessoryRowsHtml
</table>

<table class="bottom-section">
<colgroup><col style="width:78%;"><col style="width:22%;"></colgroup>
<tr><th class="bottom-hdr" style="text-align:right;">ליקויים</th><th class="bottom-hdr" style="text-align:center;">לביצוע עד</th></tr>
$defectRowsHtml
<tr><th class="bottom-hdr" colspan="2">הערות נוספות</th></tr>
<tr><td colspan="2">$defaultNoteHtml</td></tr>${if (generalNote.isNotBlank()) "<tr><td colspan=\"2\">${safe(generalNote)}</td></tr>" else ""}$serialNotesHtml
</table>

<div class="decl-box">
<div class="declaration-block">
$declarationHtml
</div>

<table class="signature-table"><tr>
<td style="width: 33%; text-align: right;"></td>
<td style="width: 34%; text-align: center;">
    ${buildInspectorStampSignatureHtml(inspectorStampDataUri, inspectorSignatureDataUri)}
</td>
<td style="width: 33%; text-align: left;"><b>$printDateLabelHtml</b> ${safe(inspectionDate)}</td>
</tr></table>

<div class="warning">$bottomWarningHtml</div>
</div>
</div>
</body>
</html>
""".trimIndent()
}

private fun renderReportText(value: String, variables: Map<String, String>): String {
    var result = value
    variables.forEach { (key, variableValue) ->
        result = result.replace("{$key}", variableValue)
    }
    return safe(result).replace("\n", "<br>")
}

private fun buildInspectorStampSignatureHtml(
    stampDataUri: String,
    signatureDataUri: String
): String {
    val signatureHtml = if (signatureDataUri.isNotBlank()) {
        """<img class="inspector-signature-image" src="$signatureDataUri" />"""
    } else {
        ""
    }

    val stampHtml = if (stampDataUri.isNotBlank()) {
        """<img class="inspector-stamp-image" src="$stampDataUri" />"""
    } else {
        ""
    }

    return signatureHtml + stampHtml
}

fun buildAccessoryRowHtml(index: Int, description: String, manufacturer: String, model: String, quantity: String, serialNumbers: String, testLoad: String, wll: String): String {
    return """<tr><td>$index</td><td>${safe(description)}</td><td>${safe(manufacturer)}</td><td>${safe(model)}</td><td>$quantity</td><td>${safe(serialNumbers)}</td><td>${safe(testLoad)}</td><td>${formatWll(wll)}</td></tr>""".trimIndent()
}

fun buildDefectRowHtml(desc: String, date: String): String {
    return """<tr><td>${safe(desc)}</td><td>${safe(date)}</td></tr>""".trimIndent()
}

fun buildNoteRowHtml(text: String): String { return "<tr><td colspan=\"2\">${safe(text)}</td></tr>" }
fun formatWll(value: String): String { return safe(value).replace(",", "<br>") }
fun safe(value: String): String { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") }

