package com.nasavi.liftinginspectorpro.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * ReportPhotoStorage.kt
 *
 * שמירת נתוני תמונות לפי מספר תסקיר ומספר זיהוי.
 *
 * חשוב:
 * התמונה עצמה נשמרת כקובץ בתיקיית האפליקציה.
 * כאן שומרים רק את נתיב/Uri התמונה ואת מספר הזיהוי שאליו היא שייכת.
 *
 * קוד מלא 21:
 * התמונות אינן נשמרות בתוך ReportStorage כדי לא לנפח את רשומות התסקיר.
 * כאשר המשתמש בוחר לצרף תמונות ל-PDF, הקוד ב-InspectionFormScreen.kt קורא מכאן את התמונות
 * ומכניס אותן כנספח לתוך ה-HTML של גרסת ה-PDF הספציפית שנשמרה.
 */
object ReportPhotoStorage {

    private const val PREF_NAME = "report_photo_storage"
    private const val KEY_PREFIX = "photos_"

    data class PhotoRecord(
        val reportNumber: String,
        val serialNumber: String,
        val uri: String,
        val capturedAtMillis: Long
    )

    fun loadPhotos(context: Context, reportNumber: String): List<PhotoRecord> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PREFIX + reportNumber, null) ?: return emptyList()

        val array = JSONArray(json)
        val result = mutableListOf<PhotoRecord>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result.add(
                PhotoRecord(
                    reportNumber = obj.optString("reportNumber"),
                    serialNumber = obj.optString("serialNumber"),
                    uri = obj.optString("uri"),
                    capturedAtMillis = obj.optLong("capturedAtMillis", 0L)
                )
            )
        }

        return result
    }

    fun savePhotos(
        context: Context,
        reportNumber: String,
        photos: List<PhotoRecord>
    ) {
        val array = JSONArray()

        photos.forEach { photo ->
            val obj = JSONObject()
            obj.put("reportNumber", photo.reportNumber)
            obj.put("serialNumber", photo.serialNumber)
            obj.put("uri", photo.uri)
            obj.put("capturedAtMillis", photo.capturedAtMillis)
            array.put(obj)
        }

        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PREFIX + reportNumber, array.toString())
            .apply()
    }
}


