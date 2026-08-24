package com.nasavi.liftinginspectorpro.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class AirPhotoEntry(val path: String, val note: String = "")

data class AirReceiverFormData(
    val inspectionDate: String = "",
    val prevReportNumber: String = "",
    val prevInspectionDate: String = "",
    val nextInspectionDate: String = "",
    val clientName: String = "",
    val clientAddress: String = "",
    val clientPhone: String = "",
    val inspectionLocation: String = "",
    val receiverType: String = "קולט אוויר",
    val receiverDescription: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val serialNumber: String = "",
    val buildStandard: String = "",
    val standardsApproval: String = "",
    val material: String = "פלדה",
    val volumeLiters: String = "",
    val manufactureYear: String = "",
    val diameterCm: String = "",
    val lengthCm: String = "",
    val designPressureBar: String = "",
    val testPressureBar: String = "",
    val designTempC: String = "",
    val hasPressureGauge: Boolean = true,
    val hasDrainValve: Boolean = true,
    val numInspectionPorts: String = "",
    val safetyValveDiameter: String = "",
    val safetyValveType: String = "קפיצי",
    val inspectionType: String = "תקופתית",
    val hasPrevSpecialInspection: Boolean = false,
    val prevSpecType: String = "",
    val prevSpecDate: String = "",
    val prevSpecBy: String = "",
    val prevSpecReportNum: String = "",
    val prevSpecInspectorNum: String = "",
    val prevSpecTestPressure: String = "",
    val prevSpecSafetyValveDiam: String = "",
    val prevSpecDrainValve: String = "",
    val prevSpecMinCapWall: String = "",
    val actualWorkPressure: String = "",
    val minEnvelopeWall: String = "",
    val minCapWall: String = "",
    val accessoriesCondition: String = "תקין",
    val safetyValveOpenPressure: String = "",
    val externalCondition: String = "תקין",
    val externalConditionNote: String = "",
    val defects: List<String> = listOf("אין"),
    val defectsDueDate: String = "",
    val allowedWorkPressure: String = "",
    val photos: List<AirPhotoEntry> = emptyList(),
    val customFields: Map<String, String> = emptyMap()
) {
    fun toJson(): String {
        val defectsArr = JSONArray().also { arr -> defects.forEach { arr.put(it) } }
        val photosArr = JSONArray().also { arr ->
            photos.forEach { arr.put(JSONObject().put("path", it.path).put("note", it.note)) }
        }
        val customFieldsObj = JSONObject().also { obj -> customFields.forEach { (k, v) -> obj.put(k, v) } }
        return JSONObject()
            .put("inspectionDate", inspectionDate)
            .put("prevReportNumber", prevReportNumber)
            .put("prevInspectionDate", prevInspectionDate)
            .put("nextInspectionDate", nextInspectionDate)
            .put("clientName", clientName)
            .put("clientAddress", clientAddress)
            .put("clientPhone", clientPhone)
            .put("inspectionLocation", inspectionLocation)
            .put("receiverType", receiverType)
            .put("receiverDescription", receiverDescription)
            .put("manufacturer", manufacturer)
            .put("model", model)
            .put("serialNumber", serialNumber)
            .put("buildStandard", buildStandard)
            .put("standardsApproval", standardsApproval)
            .put("material", material)
            .put("volumeLiters", volumeLiters)
            .put("manufactureYear", manufactureYear)
            .put("diameterCm", diameterCm)
            .put("lengthCm", lengthCm)
            .put("designPressureBar", designPressureBar)
            .put("testPressureBar", testPressureBar)
            .put("designTempC", designTempC)
            .put("hasPressureGauge", hasPressureGauge)
            .put("hasDrainValve", hasDrainValve)
            .put("numInspectionPorts", numInspectionPorts)
            .put("safetyValveDiameter", safetyValveDiameter)
            .put("safetyValveType", safetyValveType)
            .put("inspectionType", inspectionType)
            .put("hasPrevSpecialInspection", hasPrevSpecialInspection)
            .put("prevSpecType", prevSpecType)
            .put("prevSpecDate", prevSpecDate)
            .put("prevSpecBy", prevSpecBy)
            .put("prevSpecReportNum", prevSpecReportNum)
            .put("prevSpecInspectorNum", prevSpecInspectorNum)
            .put("prevSpecTestPressure", prevSpecTestPressure)
            .put("prevSpecSafetyValveDiam", prevSpecSafetyValveDiam)
            .put("prevSpecDrainValve", prevSpecDrainValve)
            .put("prevSpecMinCapWall", prevSpecMinCapWall)
            .put("actualWorkPressure", actualWorkPressure)
            .put("minEnvelopeWall", minEnvelopeWall)
            .put("minCapWall", minCapWall)
            .put("accessoriesCondition", accessoriesCondition)
            .put("safetyValveOpenPressure", safetyValveOpenPressure)
            .put("externalCondition", externalCondition)
            .put("externalConditionNote", externalConditionNote)
            .put("defects", defectsArr)
            .put("defectsDueDate", defectsDueDate)
            .put("allowedWorkPressure", allowedWorkPressure)
            .put("photos", photosArr)
            .put("customFields", customFieldsObj)
            .toString()
    }

    companion object {
        fun fromJson(json: String): AirReceiverFormData {
            return try {
                val obj = JSONObject(json)
                val defectsArr = obj.optJSONArray("defects")
                val defectsList = if (defectsArr != null)
                    (0 until defectsArr.length()).map { defectsArr.getString(it) }
                else listOf("אין")
                AirReceiverFormData(
                    inspectionDate = obj.optString("inspectionDate"),
                    prevReportNumber = obj.optString("prevReportNumber"),
                    prevInspectionDate = obj.optString("prevInspectionDate"),
                    nextInspectionDate = obj.optString("nextInspectionDate"),
                    clientName = obj.optString("clientName"),
                    clientAddress = obj.optString("clientAddress"),
                    clientPhone = obj.optString("clientPhone"),
                    inspectionLocation = obj.optString("inspectionLocation"),
                    receiverType = obj.optString("receiverType", "קולט אוויר"),
                    receiverDescription = obj.optString("receiverDescription"),
                    manufacturer = obj.optString("manufacturer"),
                    model = obj.optString("model"),
                    serialNumber = obj.optString("serialNumber"),
                    buildStandard = obj.optString("buildStandard"),
                    standardsApproval = obj.optString("standardsApproval"),
                    material = obj.optString("material", "פלדה"),
                    volumeLiters = obj.optString("volumeLiters"),
                    manufactureYear = obj.optString("manufactureYear"),
                    diameterCm = obj.optString("diameterCm"),
                    lengthCm = obj.optString("lengthCm"),
                    designPressureBar = obj.optString("designPressureBar"),
                    testPressureBar = obj.optString("testPressureBar"),
                    designTempC = obj.optString("designTempC"),
                    hasPressureGauge = obj.optBoolean("hasPressureGauge", true),
                    hasDrainValve = obj.optBoolean("hasDrainValve", true),
                    numInspectionPorts = obj.optString("numInspectionPorts"),
                    safetyValveDiameter = obj.optString("safetyValveDiameter"),
                    safetyValveType = obj.optString("safetyValveType", "קפיצי"),
                    inspectionType = obj.optString("inspectionType", "תקופתית"),
                    hasPrevSpecialInspection = obj.optBoolean("hasPrevSpecialInspection", false),
                    prevSpecType = obj.optString("prevSpecType"),
                    prevSpecDate = obj.optString("prevSpecDate"),
                    prevSpecBy = obj.optString("prevSpecBy"),
                    prevSpecReportNum = obj.optString("prevSpecReportNum"),
                    prevSpecInspectorNum = obj.optString("prevSpecInspectorNum"),
                    prevSpecTestPressure = obj.optString("prevSpecTestPressure"),
                    prevSpecSafetyValveDiam = obj.optString("prevSpecSafetyValveDiam"),
                    prevSpecDrainValve = obj.optString("prevSpecDrainValve"),
                    prevSpecMinCapWall = obj.optString("prevSpecMinCapWall"),
                    actualWorkPressure = obj.optString("actualWorkPressure"),
                    minEnvelopeWall = obj.optString("minEnvelopeWall"),
                    minCapWall = obj.optString("minCapWall"),
                    accessoriesCondition = obj.optString("accessoriesCondition", "תקין"),
                    safetyValveOpenPressure = obj.optString("safetyValveOpenPressure"),
                    externalCondition = obj.optString("externalCondition", "תקין"),
                    externalConditionNote = obj.optString("externalConditionNote"),
                    defects = defectsList,
                    defectsDueDate = obj.optString("defectsDueDate"),
                    allowedWorkPressure = obj.optString("allowedWorkPressure"),
                    photos = try {
                        val arr = obj.optJSONArray("photos") ?: JSONArray()
                        (0 until arr.length()).mapNotNull { i ->
                            val item = arr.optJSONObject(i)
                            if (item != null) {
                                val path = item.optString("path").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                                AirPhotoEntry(path = path, note = item.optString("note"))
                            } else {
                                val path = arr.optString(i).takeIf { it.isNotBlank() } ?: return@mapNotNull null
                                AirPhotoEntry(path = path)
                            }
                        }
                    } catch (_: Exception) { emptyList() },
                    customFields = try {
                        val cfObj = obj.optJSONObject("customFields") ?: JSONObject()
                        cfObj.keys().asSequence().associateWith { cfObj.optString(it) }
                    } catch (_: Exception) { emptyMap() }
                )
            } catch (_: Exception) { AirReceiverFormData() }
        }
    }
}

object AirReceiverStorage {
    private const val PREF_NAME = "air_receiver_reports"
    private const val KEY_REPORTS = "reports"
    private const val DIR_NAME = "air_receiver_reports"

    data class AirReceiverSavedReport(
        val baseReportNumber: String,
        val versionName: String,
        val date: String,
        val client: String,
        val htmlFileName: String,
        val formDataJson: String = ""
    )

    fun getDir(context: Context): File = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    fun loadReports(context: Context): List<AirReceiverSavedReport> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_REPORTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                AirReceiverSavedReport(
                    baseReportNumber = obj.optString("baseReportNumber"),
                    versionName = obj.optString("versionName"),
                    date = obj.optString("date"),
                    client = obj.optString("client"),
                    htmlFileName = obj.optString("htmlFileName"),
                    formDataJson = obj.optString("formDataJson")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveReports(context: Context, reports: List<AirReceiverSavedReport>) {
        val arr = JSONArray()
        reports.forEach { r ->
            arr.put(JSONObject()
                .put("baseReportNumber", r.baseReportNumber)
                .put("versionName", r.versionName)
                .put("date", r.date)
                .put("client", r.client)
                .put("htmlFileName", r.htmlFileName)
                .put("formDataJson", r.formDataJson))
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_REPORTS, arr.toString()).apply()
    }

    fun saveVersion(context: Context, report: AirReceiverSavedReport, htmlContent: String) {
        val file = File(getDir(context), report.htmlFileName)
        file.writeText(htmlContent, Charsets.UTF_8)
        val current = loadReports(context).toMutableList()
        val idx = current.indexOfFirst {
            it.baseReportNumber == report.baseReportNumber && it.versionName == report.versionName
        }
        if (idx >= 0) current[idx] = report else current.add(report)
        saveReports(context, current)
    }

    fun loadHtml(context: Context, report: AirReceiverSavedReport): String {
        if (report.htmlFileName.isBlank()) return ""
        return try { File(getDir(context), report.htmlFileName).readText(Charsets.UTF_8) } catch (_: Exception) { "" }
    }

    fun deleteVersion(context: Context, report: AirReceiverSavedReport) {
        if (report.htmlFileName.isNotBlank()) runCatching { File(getDir(context), report.htmlFileName).delete() }
        val updated = loadReports(context).filterNot {
            it.baseReportNumber == report.baseReportNumber && it.versionName == report.versionName
        }
        saveReports(context, updated)
    }

    fun nextVersionName(context: Context, baseReportNumber: String): String {
        val existing = loadReports(context).filter { it.baseReportNumber == baseReportNumber }
        if (existing.isEmpty()) return baseReportNumber
        val maxSuffix = existing.mapNotNull { r ->
            val dotIdx = r.versionName.indexOf('.')
            if (dotIdx == -1) 0 else r.versionName.substring(dotIdx + 1).toIntOrNull()
        }.maxOrNull() ?: 0
        return "$baseReportNumber.${maxSuffix + 1}"
    }
}

