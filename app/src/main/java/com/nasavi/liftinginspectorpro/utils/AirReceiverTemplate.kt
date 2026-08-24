package com.nasavi.liftinginspectorpro.utils

import com.nasavi.liftinginspectorpro.data.AirReceiverFormData
import com.nasavi.liftinginspectorpro.data.InspectorSettingsStorage

fun buildAirReceiverHtml(
    reportNumber: String,
    inspectorName: String,
    inspectorCertNumber: String,
    logoDataUri: String,
    company: InspectorSettingsStorage.CompanyDetails,
    stampDataUri: String,
    signatureDataUri: String,
    form: AirReceiverFormData,
    notesList: List<String>,
    s3Layout: List<List<String>> = InspectorSettingsStorage.defaultAirS3Layout(),
    s5Layout: List<List<String>> = InspectorSettingsStorage.defaultAirS5Layout(),
    s6Layout: List<List<String>> = InspectorSettingsStorage.defaultAirS6Layout(),
    compact: Boolean = false,
    customFieldDefs: List<InspectorSettingsStorage.AirCustomFieldDef> = emptyList(),
    customFieldValues: Map<String, String> = emptyMap()
): String {
    val logoHtml = if (logoDataUri.isNotBlank())
        """<img src="$logoDataUri" style="max-width:55mm;max-height:22mm;object-fit:contain;" />"""
    else ""

    val companyLines = buildList {
        if (company.companyName.isNotBlank()) add("<b>${safe(company.companyName)}</b>")
        if (company.companyTitle.isNotBlank()) add(safe(company.companyTitle))
        val addrParts = listOf(company.address, company.city).filter { it.isNotBlank() }
        if (addrParts.isNotEmpty()) add(addrParts.joinToString(", ") { safe(it) })
        if (company.phone.isNotBlank()) add("טל': ${safe(company.phone)}")
        if (company.email.isNotBlank()) add(safe(company.email))
        if (company.businessId.isNotBlank()) add("מס' עסק: ${safe(company.businessId)}")
    }.joinToString("<br>")

    val boolYesNo: (Boolean) -> String = { if (it) "כן" else "לא" }
    val condStr: (String) -> String = { if (it.isBlank()) "—" else safe(it) }

    val dueDateCell = if (form.defectsDueDate.isNotBlank()) safe(form.defectsDueDate) else "—"
    val defectsHtml = if (form.defects.isEmpty() || (form.defects.size == 1 && form.defects[0].isBlank())) {
        "<tr><td>אין</td><td>—</td></tr>"
    } else {
        form.defects.filter { it.isNotBlank() }
            .joinToString("") { "<tr><td style='width:78%;'>${safe(it)}</td><td style='width:22%;text-align:center;white-space:nowrap;'>$dueDateCell</td></tr>" }
    }

    val notesHtml = notesList.filter { it.isNotBlank() }.joinToString("") { n ->
        "<li>${safe(n)}</li>"
    }

    val s3Values = mapOf(
        "manufacturer"        to form.manufacturer,
        "model"               to form.model,
        "serialNumber"        to form.serialNumber,
        "manufactureYear"     to form.manufactureYear,
        "buildStandard"       to form.buildStandard,
        "standardsApproval"   to form.standardsApproval,
        "material"            to form.material,
        "volumeLiters"        to form.volumeLiters,
        "designPressureBar"   to form.designPressureBar,
        "testPressureBar"     to form.testPressureBar,
        "diameterCm"          to form.diameterCm,
        "lengthCm"            to form.lengthCm,
        "hasPressureGauge"    to boolYesNo(form.hasPressureGauge),
        "hasDrainValve"       to boolYesNo(form.hasDrainValve),
        "designTempC"         to form.designTempC,
        "safetyValveDiameter" to form.safetyValveDiameter,
        "safetyValveType"     to form.safetyValveType,
        "numInspectionPorts"  to form.numInspectionPorts
    )
    val s3Labels = InspectorSettingsStorage.SECTION3_FIELD_LABELS

    val s3Html = buildString {
        append("<table class=\"sec-table\">\n<tr><th colspan=\"6\" class=\"sec-hdr\">סעיף 3 — פרטי זיהוי הקולט</th></tr>\n")
        s3Layout.forEach { row ->
            val cells = (row + listOf("", "", "")).take(3)
            append("<tr>")
            cells.forEach { fieldId ->
                if (fieldId.isBlank()) append("<td class=\"lbl3\"></td><td class=\"v3\"></td>")
                else {
                    val lbl = s3Labels[fieldId] ?: fieldId
                    val v = s3Values[fieldId] ?: ""
                    append("<td class=\"lbl3\">${safe(lbl)}</td><td class=\"v3\">${condStr(v)}</td>")
                }
            }
            append("</tr>\n")
        }
        customFieldDefs.filter { it.section == 3 }.forEach { def ->
            append("<tr><td class=\"lbl3\" colspan=\"3\">${safe(def.label)}</td><td colspan=\"3\">${condStr(customFieldValues[def.id] ?: "")}</td></tr>\n")
        }
        append("</table>\n")
    }

    val s5Values = mapOf(
        "prevSpecType"          to form.prevSpecType,
        "prevSpecDate"          to form.prevSpecDate,
        "prevSpecBy"            to form.prevSpecBy,
        "prevSpecReportNum"     to form.prevSpecReportNum,
        "prevSpecInspectorNum"  to form.prevSpecInspectorNum,
        "prevSpecTestPressure"  to form.prevSpecTestPressure,
        "prevSpecSafetyValveDiam" to form.prevSpecSafetyValveDiam,
        "prevSpecDrainValve"    to form.prevSpecDrainValve,
        "prevSpecMinCapWall"    to form.prevSpecMinCapWall
    )
    val s5Labels = InspectorSettingsStorage.SECTION5_FIELD_LABELS

    val s6Values = mapOf(
        "actualWorkPressure"      to form.actualWorkPressure,
        "minEnvelopeWall"         to form.minEnvelopeWall,
        "minCapWall"              to form.minCapWall,
        "accessoriesCondition"    to form.accessoriesCondition,
        "safetyValveOpenPressure" to form.safetyValveOpenPressure,
        "externalCondition"       to form.externalCondition
    )
    val s6Labels = InspectorSettingsStorage.SECTION6_FIELD_LABELS

    val s6Html = buildString {
        append("<table class=\"sec-table\">\n<tr><th colspan=\"6\" class=\"sec-hdr\">סעיף 6 — ממצאי הבדיקה הנוכחית</th></tr>\n")
        s6Layout.forEach { row ->
            val cells = (row + listOf("", "", "")).take(3)
            append("<tr>")
            cells.forEach { fieldId ->
                if (fieldId.isBlank()) append("<td class=\"lbl3\"></td><td class=\"v3\"></td>")
                else {
                    val lbl = s6Labels[fieldId] ?: fieldId
                    val v = s6Values[fieldId] ?: ""
                    append("<td class=\"lbl3\">${safe(lbl)}</td><td class=\"v3\">${condStr(v)}</td>")
                }
            }
            append("</tr>\n")
        }
        if (form.externalConditionNote.isNotBlank()) {
            append("<tr><td class=\"lbl3\" colspan=\"3\">הערה מצב חיצוני</td><td colspan=\"3\">${safe(form.externalConditionNote)}</td></tr>\n")
        }
        customFieldDefs.filter { it.section == 6 }.forEach { def ->
            append("<tr><td class=\"lbl3\" colspan=\"3\">${safe(def.label)}</td><td colspan=\"3\">${condStr(customFieldValues[def.id] ?: "")}</td></tr>\n")
        }
        append("</table>\n")
    }

    val customRows5 = customFieldDefs.filter { it.section == 5 }.joinToString("") { def ->
        "<tr><td class=\"lbl3\" colspan=\"3\">${safe(def.label)}</td><td colspan=\"3\">${condStr(customFieldValues[def.id] ?: "")}</td></tr>"
    }
    val prevSpecHtml = if (form.hasPrevSpecialInspection) {
        val rowsHtml = s5Layout.joinToString("") { row ->
            val cells = (row + listOf("", "", "")).take(3)
            "<tr>" + cells.joinToString("") { fieldId ->
                if (fieldId.isBlank()) "<td class=\"lbl3\"></td><td class=\"v3\"></td>"
                else {
                    val lbl = s5Labels[fieldId] ?: fieldId
                    val v = s5Values[fieldId] ?: ""
                    "<td class=\"lbl3\">${safe(lbl)}</td><td class=\"v3\">${condStr(v)}</td>"
                }
            } + "</tr>"
        }
        """
<table class="sec-table">
<tr><th colspan="6" class="sec-hdr">סעיף 5 — בדיקה מיוחדת קודמת</th></tr>
$rowsHtml
$customRows5
</table>
"""
    } else ""

    val stampSignatureHtml = buildString {
        if (signatureDataUri.isNotBlank()) append("""<img src="$signatureDataUri" style="max-width:110px;max-height:32px;object-fit:contain;display:block;margin:0 auto 1px auto;" />""")
        if (stampDataUri.isNotBlank()) append("""<img src="$stampDataUri" style="max-width:110px;max-height:48px;object-fit:contain;display:block;margin:0 auto;" />""")
    }

    val customRows1 = customFieldDefs.filter { it.section == 1 }.joinToString("") { def ->
        "<tr><td class=\"lbl2\">${safe(def.label)}</td><td colspan=\"3\">${condStr(customFieldValues[def.id] ?: "")}</td></tr>"
    }
    val customRows2 = customFieldDefs.filter { it.section == 2 }.joinToString("") { def ->
        "<tr><td class=\"lbl\">${safe(def.label)}</td><td>${condStr(customFieldValues[def.id] ?: "")}</td></tr>"
    }
    val customRows4 = customFieldDefs.filter { it.section == 4 }.joinToString("") { def ->
        "<tr><td class=\"lbl\">${safe(def.label)}</td><td>${condStr(customFieldValues[def.id] ?: "")}</td></tr>"
    }
    val customRows8 = customFieldDefs.filter { it.section == 8 }.joinToString("") { def ->
        "<tr><td class=\"lbl\">${safe(def.label)}</td><td>${condStr(customFieldValues[def.id] ?: "")}</td></tr>"
    }

    return """
<!DOCTYPE html>
<html lang="he" dir="rtl">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=595">
<style>
body { font-family: Arial, sans-serif; direction: rtl; margin: 0; font-size: ${if (compact) "8px" else "9.5px"}; line-height: 1.2; width: 100%; box-sizing: border-box; }
.page { width: 100%; padding: 2px 0 0 0; box-sizing: border-box; }
.header-table { width: 100%; border-collapse: collapse; margin-bottom: 2px; }
.header-table td { vertical-align: middle; padding: 1px 3px; }
.header-logo { width: 22%; text-align: center; }
.header-title { width: 56%; text-align: center; }
.header-company { width: 22%; text-align: right; font-size: 8.5px; line-height: 1.4; }
.report-title { font-size: ${if (compact) "11px" else "13px"}; font-weight: bold; }
.report-subtitle { font-size: 8px; }
.report-num { font-size: 10px; font-weight: bold; margin-top: 1px; }
.sec-table { width: 100%; border-collapse: collapse; margin-bottom: 2px; }
.sec-table td, .sec-table th { border: 1px solid #555; padding: 2px 4px; font-size: ${if (compact) "8px" else "9.5px"}; text-align: right; }
.sec-hdr { background: #dce8dc; font-weight: bold; text-align: right; font-size: ${if (compact) "8.5px" else "10px"}; }
.lbl { background: #f0f0f0; font-weight: bold; width: 38%; white-space: nowrap; }
.lbl2 { background: #f0f0f0; font-weight: bold; width: 25%; white-space: nowrap; }
.lbl3 { background: #f0f0f0; font-weight: bold; width: 16%; white-space: nowrap; font-size:9px; }
.v3 { width: 17%; font-size:9px; }
.defects-table { width: 100%; border-collapse: collapse; margin-bottom: 2px; }
.defects-table td, .defects-table th { border: 1px solid #555; padding: 2px 4px; font-size: ${if (compact) "8px" else "9.5px"}; text-align: right; }
.notes-list { margin: 1px 14px 2px 0; padding-right: 14px; font-size: ${if (compact) "8px" else "9.5px"}; }
.notes-list li { margin-bottom: 1px; }
.decl-box { border: 1px solid #555; padding: 3px 5px 2px 5px; margin-top: 3px; }
.decl-text { text-align: center; font-size: 9px; font-weight: bold; margin-bottom: 2px; }
.insp-tbl { width:100%; border-collapse:collapse; margin:2px 0; }
.insp-tbl td { border:1px solid #555; padding:2px 3px; font-size:9px; text-align:right; }
.insp-lbl { background:#f0f0f0; font-weight:bold; width:14%; white-space:nowrap; }
.sig-table { width: 100%; border-collapse: collapse; margin-top: 3px; }
.sig-table td { font-size: ${if (compact) "8px" else "9.5px"}; vertical-align: middle; text-align:center; border:1px solid #555; padding:2px; }
</style>
</head>
<body>
<div class="page">

<table class="header-table">
<tr>
<td class="header-company">$companyLines</td>
<td class="header-title">
  <div class="report-title">תסקיר בדיקת ${safe(form.receiverType)}</div>
  <div class="report-subtitle">לפי תקנות הבטיחות בעבודה (בקים, מיכלי לחץ ומיכלי גפ"מ), תשנ"א-1990</div>
  <div class="report-num">מספר תסקיר: ${safe(reportNumber)} / ${safe(inspectorCertNumber)}</div>
</td>
<td class="header-logo">$logoHtml</td>
</tr>
</table>

<table class="sec-table">
<tr><th colspan="4" class="sec-hdr">סעיף 1 — פרטי לקוח ובדיקה</th></tr>
<tr>
  <td class="lbl2">תאריך בדיקה</td><td>${condStr(form.inspectionDate)}</td>
  <td class="lbl2">תאריך בדיקה קודמת</td><td>${condStr(form.prevInspectionDate)}</td>
</tr>
<tr>
  <td class="lbl2">מספר תסקיר קודם</td><td>${condStr(form.prevReportNumber)}</td>
  <td class="lbl2">תאריך בדיקה הבאה</td><td>${condStr(form.nextInspectionDate)}</td>
</tr>
<tr>
  <td class="lbl2">שם הלקוח</td><td colspan="3">${condStr(form.clientName)}</td>
</tr>
<tr>
  <td class="lbl2">כתובת</td><td>${condStr(form.clientAddress)}</td>
  <td class="lbl2">טלפון</td><td>${condStr(form.clientPhone)}</td>
</tr>
<tr>
  <td class="lbl2">מקום הבדיקה</td><td colspan="3">${condStr(form.inspectionLocation)}</td>
</tr>
$customRows1
</table>

<table class="sec-table">
<tr><th colspan="2" class="sec-hdr">סעיף 2 — סוג הציוד</th></tr>
<tr><td class="lbl">סוג</td><td>${safe(form.receiverType)}</td></tr>
${if (form.receiverDescription.isNotBlank()) "<tr><td class=\"lbl\">תיאור</td><td>${safe(form.receiverDescription)}</td></tr>" else ""}
$customRows2
</table>

$s3Html

<table class="sec-table">
<tr><th colspan="2" class="sec-hdr">סעיף 4 — סוג הבדיקה</th></tr>
<tr><td class="lbl">סוג הבדיקה</td><td>${safe(form.inspectionType)}</td></tr>
$customRows4
</table>

$prevSpecHtml

$s6Html

<table class="defects-table">
<tr><th class="sec-hdr" style="width:78%;">סעיף 7 — ליקויים</th><th class="sec-hdr" style="width:22%;text-align:center;">לביצוע עד</th></tr>
$defectsHtml
</table>

<table class="sec-table">
<tr><th colspan="2" class="sec-hdr">סעיף 8 — לחץ עבודה מותר</th></tr>
<tr><td class="lbl">לחץ עבודה מותר (בר)</td><td>${condStr(form.allowedWorkPressure)}</td></tr>
$customRows8
</table>

<div style="border:1px solid #555;padding:4px 6px;margin-bottom:4px;">
<div style="font-weight:bold;font-size:11px;margin-bottom:2px;">סעיף 9 — הערות</div>
<ol class="notes-list">$notesHtml</ol>
</div>

<div class="decl-box">
<div class="decl-text">הצהרה: אני החתום מטה שהוסמכתי ע"י מפקח עבודה ראשי לפי תקנות הבטיחות בעבודה (בקים, מיכלי לחץ ומיכלי גפ"מ), תשנ"א-1990, תעודת הסמכה מס' ${safe(inspectorCertNumber)}, מאשר בדיקת הציוד הנ"ל</div>
<table class="insp-tbl">
<tr>
  <td class="insp-lbl">מס' בודק</td><td>${safe(inspectorCertNumber)}</td>
  <td class="insp-lbl">שם הבודק</td><td>${safe(inspectorName)}${if (company.companyTitle.isNotBlank()) " — ${safe(company.companyTitle)}" else ""}</td>
  <td class="insp-lbl">טלפון</td><td>${safe(company.phone)}</td>
</tr>
<tr>
  <td class="insp-lbl">כתובת</td><td>${safe(company.address)}</td>
  <td class="insp-lbl">יישוב</td><td>${safe(company.city)}</td>
  <td class="insp-lbl">דוא"ל</td><td>${safe(company.email)}</td>
</tr>
</table>
<table class="sig-table"><tr>
<td style="width:34%;">שם הבודק: ${safe(inspectorName)}</td>
<td style="width:32%;">חתימת הבודק:<br>$stampSignatureHtml</td>
<td style="width:34%;font-size:11px;font-weight:bold;">תאריך: ${safe(form.inspectionDate)}</td>
</tr></table>
</div>

</div>
</body>
</html>
""".trimIndent()
}


