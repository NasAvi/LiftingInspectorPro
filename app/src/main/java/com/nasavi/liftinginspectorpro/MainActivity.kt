package com.nasavi.liftinginspectorpro

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import com.nasavi.liftinginspectorpro.data.AppBackupManager
import com.nasavi.liftinginspectorpro.data.AppThemeState
import com.nasavi.liftinginspectorpro.data.InspectorSettingsStorage
import com.nasavi.liftinginspectorpro.data.LicenseManager
import com.nasavi.liftinginspectorpro.data.LicenseResult
import com.nasavi.liftinginspectorpro.data.ReportStorage
import org.json.JSONObject
import com.nasavi.liftinginspectorpro.ui.screens.CustomListsManagementScreen
import com.nasavi.liftinginspectorpro.ui.screens.HomeScreen
import com.nasavi.liftinginspectorpro.ui.screens.LoginScreen
import com.nasavi.liftinginspectorpro.ui.screens.InspectionFormScreen
import com.nasavi.liftinginspectorpro.ui.screens.PaywallScreen
import com.nasavi.liftinginspectorpro.ui.screens.SavedRecordsScreen
import com.nasavi.liftinginspectorpro.ui.theme.LiftingInspectorProTheme

/**
 * כרטיס תסקיר לתצוגה במסך הרשומות.
 * הנתונים המלאים נשמרים ב-ReportStorage כ-WorkingReport.
 */
data class SavedInspection(
    val reportNumber: String,
    val date: String,
    val client: String,
    val summary: String,
    val nextInspectionDate: String = "",
    val site: String = ""
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // מצב מסך מלא — כמו אצלך בקובץ הקיים.
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.let { controller ->
            controller.hide(WindowInsets.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            LiftingInspectorProTheme {
                val context = LocalContext.current

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }

                LaunchedEffect(Unit) {
                    AppThemeState.load(context)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        AppBackupManager(applicationContext).loadDefaultConfigIfFirstLaunch()
                        AppBackupManager.runMigrationsIfNeeded(applicationContext)
                        com.nasavi.liftinginspectorpro.data.InspectionReminderScheduler.rescheduleAll(applicationContext)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                // ---- מערכת אימות ורישיון ----
                // "auth_loading" | "login" | "license_loading" | "active" | "expired" | "no_internet" | "saved_only"
                var authState by remember { mutableStateOf("auth_loading") }
                var licenseCheckTrigger by remember { mutableIntStateOf(0) }
                var savedOnlyNavScreen by remember { mutableStateOf("main") }
                var trialDaysLeft by remember { mutableStateOf<Int?>(null) }
                var showTrialWarning by remember { mutableStateOf(false) }
                var showConsentDialog by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(licenseCheckTrigger) {
                    authState = if (LicenseManager.currentUser == null) {
                        "login"
                    } else {
                        authState = "license_loading"
                        when (LicenseManager.checkLicense(context)) {
                            LicenseResult.Active -> {
                                if (!LicenseManager.isConsentGiven(context)) {
                                    showConsentDialog = true
                                }
                                val expiryMs = LicenseManager.getExpiryMs(context)
                                if (expiryMs > 0) {
                                    val daysLeft = ((expiryMs - System.currentTimeMillis()) / (1000L * 60 * 60 * 24)).toInt()
                                    if (daysLeft <= 30) {
                                        val wp = context.getSharedPreferences("trial_warning", android.content.Context.MODE_PRIVATE)
                                        val lastDay = wp.getLong("last_day", 0L)
                                        val today = System.currentTimeMillis() / (1000L * 60 * 60 * 24)
                                        if (daysLeft <= 7 || lastDay < today) {
                                            trialDaysLeft = daysLeft
                                            showTrialWarning = true
                                            wp.edit().putLong("last_day", today).apply()
                                        }
                                    }
                                }
                                "active"
                            }
                            LicenseResult.Expired -> "expired"
                            LicenseResult.RequiresInternet -> "no_internet"
                            LicenseResult.NotLoggedIn -> "login"
                        }
                    }
                }

                when (authState) {
                    "auth_loading", "license_loading" -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        return@LiftingInspectorProTheme
                    }
                    "login" -> {
                        LoginScreen(onLoggedIn = { licenseCheckTrigger++ })
                        return@LiftingInspectorProTheme
                    }
                    "expired" -> {
                        PaywallScreen(onViewSavedOnly = { savedOnlyNavScreen = "accessories"; authState = "saved_only" })
                        return@LiftingInspectorProTheme
                    }
                    "no_internet" -> {
                        PaywallScreen(requiresInternet = true, onViewSavedOnly = { savedOnlyNavScreen = "accessories"; authState = "saved_only" })
                        return@LiftingInspectorProTheme
                    }
                    "saved_only" -> {
                        SavedRecordsScreen(
                            reports = loadSavedReportsForCards(context),
                            onEditReport = { },
                            onBack = { savedOnlyNavScreen = "accessories" }
                        )
                        return@LiftingInspectorProTheme
                    }
                }
                // authState == "active" — ממשיך לניווט הרגיל למטה
                // ---- סוף מערכת אימות ----

                // דיאלוג הסכמה לתנאי שימוש — מוצג פעם אחת בלבד
                if (showConsentDialog) {
                    AlertDialog(
                        onDismissRequest = { },
                        title = { androidx.compose.material3.Text("הצהרת אחריות — תנאי שימוש") },
                        text = {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                androidx.compose.material3.Text(
                                    "האפליקציה משמשת ככלי עזר טכני בלבד להפקת תסקירי בדיקה. " +
                                    "המשתמש הוא הבודק המוסמך והאחראי הבלעדי לכל תוכן, ניסוח, מסקנה והמלצה המופיעים בתסקיר שהופק על ידו.\n\n" +
                                    "האפליקציה מאפשרת עריכה חופשית של כל שדות התסקיר. משתמש שבחר לשנות, לתקן או להתאים את הנוסח — " +
                                    "עושה זאת על דעתו ובאחריותו המקצועית הבלעדית.\n\n" +
                                    "מפתח האפליקציה הינו בודק מוסמך למכונות ואביזרי הרמה. יחד עם זאת, למרות הסמכתו, " +
                                    "אינו צד לבדיקות שבוצעו על ידי משתמשי האפליקציה ואינו נושא בכל אחריות מקצועית, חוקית, נזיקית או חוזית " +
                                    "הנובעת מתוכן התסקירים, מהחלטות שהתקבלו על בסיסם או מכל שימוש שנעשה בהם. " +
                                    "מודול בדיקת קולטי האוויר והקומפרסורים מיועד לשימוש בודקים מוסמכים בתחום זה בלבד — " +
                                    "מפתח האפליקציה אינו מוסמך לביצוע בדיקות קולטי אוויר ואינו נושא כל אחריות מקצועית, חוקית, נזיקית או חוזית הנובעת משימוש במודול זה.\n\n" +
                                    "בכל מקרה, האחריות המקצועית על תוכן התסקיר, נכונותו ומסקנותיו מוטלת על הבודק שחתום עליו בלבד."
                                )
                            }
                        },
                        confirmButton = {
                            androidx.compose.material3.Button(
                                onClick = {
                                    showConsentDialog = false
                                    scope.launch { LicenseManager.saveConsent(context) }
                                }
                            ) {
                                androidx.compose.material3.Text("קראתי ומסכים לתנאים")
                            }
                        }
                    )
                }

                // התראת תום ניסיון
                if (showTrialWarning && trialDaysLeft != null) {
                    AlertDialog(
                        onDismissRequest = { showTrialWarning = false },
                        title = { androidx.compose.material3.Text("תזכורת — תום תקופת הניסיון") },
                        text = {
                            androidx.compose.material3.Text(
                                "נותרים ${trialDaysLeft} ימים לתקופת הניסיון החינמית.\n" +
                                "לאחר מכן יש לרכוש מנוי (200 ₪ לחודש) להמשך שימוש מלא."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { showTrialWarning = false }) {
                                androidx.compose.material3.Text("הבנתי")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showTrialWarning = false
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/972507202846"))
                                )
                            }) {
                                androidx.compose.material3.Text("צור קשר לתשלום")
                            }
                        }
                    )
                }

                var currentScreen by remember { mutableStateOf("home") }

                // אם null — טופס חדש. אם יש מספר — פתיחת תסקיר קיים לעריכה.
                var editingReportNumber by remember { mutableStateOf<String?>(null) }

                // נתונים לטופס חכם אביזרים
                var smartFormInspectionDate by remember { mutableStateOf("") }
                var smartFormPendingRows by remember { mutableStateOf<List<com.nasavi.liftinginspectorpro.ui.screens.SmartFormTableRow>>(emptyList()) }
                var smartFormPendingDefects by remember { mutableStateOf<List<com.nasavi.liftinginspectorpro.ui.screens.SmartFormDefect>>(emptyList()) }
                var smartFormReturnedRows by remember { mutableStateOf<List<com.nasavi.liftinginspectorpro.ui.screens.SmartFormTableRow>>(emptyList()) }
                var smartFormReturnedDefects by remember { mutableStateOf<List<com.nasavi.liftinginspectorpro.ui.screens.SmartFormDefect>>(emptyList()) }

                var inspectorDetails by remember {
                    mutableStateOf(InspectorSettingsStorage.getInspectorDetails(context))
                }

                var nextReportNumber by remember {
                    mutableStateOf(InspectorSettingsStorage.getNextReportNumber(context))
                }

                var inspectorStampPath by remember {
                    mutableStateOf(InspectorSettingsStorage.getStampPath(context))
                }

                var inspectorSignaturePath by remember {
                    mutableStateOf(InspectorSettingsStorage.getSignaturePath(context))
                }

                var reportTextSettings by remember {
                    mutableStateOf(InspectorSettingsStorage.getReportTextSettings(context))
                }

                var savedReports by remember {
                    mutableStateOf(loadSavedReportsForCards(context))
                }

                // מעקב חידוש תסקירים — תסקירים שחודשו בסשן הנוכחי (לגריעה מהרשימה)
                var accessoryRenewalExcluded by remember { mutableStateOf(setOf<String>()) }
                // שדות סינון לפי אתר — מורמים כאן כדי לשרוד ניווט לטופס ובחזרה
                var renewalSelectedSiteId by remember { mutableStateOf("") }
                var renewalMonthsText by remember { mutableStateOf("") }
                // האם לחזור למסך חידוש אחרי שמירת הטופס
                var returnToRenewalAfterForm by remember { mutableStateOf(false) }
                val renewalScope = rememberCoroutineScope()

                fun reloadSavedReports() {
                    savedReports = loadSavedReportsForCards(context)

                    // תיקון הגנה: בכל רענון רשומות מחשבים את המספר הבא לפי התסקירים הקיימים בפועל.
                    // כך גם אם מסיבה כלשהי מספר רץ לא התקדם בזמן הפקת PDF,
                    // התפריט הראשי יתיישר לפי המספר האחרון שקיים ברשומות.
                    val maxUsedNumber = savedReports
                        .map { it.reportNumber }
                        .mapNotNull { reportNumber ->
                            Regex("""^(.*?)(\d+)$""")
                                .find(reportNumber.trim())
                                ?.groupValues
                                ?.getOrNull(2)
                                ?.toLongOrNull()
                        }
                        .maxOrNull()

                    if (maxUsedNumber != null) {
                        val currentNext = InspectorSettingsStorage.getNextReportNumber(context)
                        val currentMatch = Regex("""^(.*?)(\d+)$""").find(currentNext.trim())

                        val prefix = currentMatch?.groupValues?.getOrNull(1) ?: "R"
                        val digitLength = currentMatch?.groupValues?.getOrNull(2)?.length ?: 4
                        val correctedNext = prefix + (maxUsedNumber + 1).toString().padStart(digitLength, '0')

                        if (correctedNext != currentNext) {
                            InspectorSettingsStorage.saveSettings(
                                context = context,
                                inspectorDetails = inspectorDetails,
                                nextReportNumber = correctedNext
                            )
                        }

                        nextReportNumber = InspectorSettingsStorage.getNextReportNumber(context)
                    }
                }

                when (currentScreen) {
                    "home" -> HomeScreen(
                        inspectorDetails = inspectorDetails,
                        nextReportNumber = nextReportNumber,
                        inspectorStampPath = inspectorStampPath,
                        inspectorSignaturePath = inspectorSignaturePath,
                        reportTextSettings = reportTextSettings,
                        onNewInspectionClick = {
                            editingReportNumber = null
                            smartFormReturnedRows = emptyList()
                            smartFormReturnedDefects = emptyList()
                            currentScreen = "form"
                        },
                        onSmartAccessoriesClick = {
                            smartFormInspectionDate = ""
                            smartFormPendingRows = emptyList()
                            smartFormPendingDefects = emptyList()
                            currentScreen = "smart_accessories"
                        },
                        onSavedRecordsClick = {
                            reloadSavedReports()
                            currentScreen = "saved"
                        },
                        onManageListsClick = {
                            currentScreen = "lists"
                        },
                        onSaveSettings = { newInspectorDetails, newNextReportNumber ->
                            InspectorSettingsStorage.saveSettings(
                                context = context,
                                inspectorDetails = newInspectorDetails,
                                nextReportNumber = newNextReportNumber
                            )
                            inspectorDetails = newInspectorDetails
                            nextReportNumber = newNextReportNumber
                        },
                        onSaveReportTextSettings = { newReportTextSettings ->
                            InspectorSettingsStorage.saveReportTextSettings(context, newReportTextSettings)
                            reportTextSettings = InspectorSettingsStorage.getReportTextSettings(context)
                        },
                        onResetReportTextSettings = {
                            InspectorSettingsStorage.resetReportTextSettings(context)
                            val defaults = InspectorSettingsStorage.getReportTextSettings(context)
                            reportTextSettings = defaults
                            defaults
                        },
                        onStampSelected = { uri ->
                            inspectorStampPath = InspectorSettingsStorage.saveStampFromUri(context, uri)
                        },
                        onSignatureSelected = { uri ->
                            inspectorSignaturePath = InspectorSettingsStorage.saveSignatureFromUri(context, uri)
                        },
                        onRemoveStamp = {
                            InspectorSettingsStorage.removeStamp(context)
                            inspectorStampPath = ""
                        },
                        onRemoveSignature = {
                            InspectorSettingsStorage.removeSignature(context)
                            inspectorSignaturePath = ""
                        },
                        onImportTemplateSelected = { uri ->
                            importReportTemplateFromUri(context, uri)?.let { imported ->
                                InspectorSettingsStorage.saveReportTextSettings(context, imported)
                                reportTextSettings = InspectorSettingsStorage.getReportTextSettings(context)
                            }
                        },
                        onRenewalClick = { currentScreen = "renewal" },
                        onBackToMain = null,
                        onGeneralSettingsSaved = {
                            inspectorDetails = InspectorSettingsStorage.getInspectorDetails(context)
                            nextReportNumber = InspectorSettingsStorage.getNextReportNumber(context)
                            inspectorStampPath = InspectorSettingsStorage.getStampPath(context)
                            inspectorSignaturePath = InspectorSettingsStorage.getSignaturePath(context)
                        }
                    )

                    "renewal" -> com.nasavi.liftinginspectorpro.ui.screens.RenewalScreen(
                        allReports = savedReports,
                        excludedReportNumbers = accessoryRenewalExcluded,
                        sites = com.nasavi.liftinginspectorpro.data.InspectorSettingsStorage.getSites(context),
                        selectedSiteId = renewalSelectedSiteId,
                        onSelectedSiteIdChange = { renewalSelectedSiteId = it },
                        monthsText = renewalMonthsText,
                        onMonthsTextChange = { renewalMonthsText = it },
                        onRenewReport = { sourceNumber ->
                            renewalScope.launch {
                                val source = withContext(Dispatchers.IO) {
                                    ReportStorage.getWorkingReport(context, sourceNumber)
                                }
                                if (source != null) {
                                    val newNumber = InspectorSettingsStorage.getNextReportNumber(context)
                                    val renewed = source.copy(
                                        reportNumber = newNumber,
                                        inspectionDate = "",
                                        nextInspectionDate = "",
                                        inspectionLocation = "",
                                        inspectionPlaceType = "",
                                        defects = emptyList(),
                                        html = "",
                                        isLockedForNewAccessories = false,
                                        chainIndex = if (source.chainIndex >= 5) 1 else source.chainIndex + 1,
                                        previousReportNumber = source.reportNumber,
                                        previousInspectionDate = source.inspectionDate,
                                        previousInspectorName = listOf(source.inspectorFirstName, source.inspectorLastName)
                                            .filter { it.isNotBlank() }.joinToString(" "),
                                        externalReportNumber = "",
                                        externalInspectorName = "",
                                        externalInspectionDate = ""
                                    )
                                    withContext(Dispatchers.IO) {
                                        ReportStorage.saveWorkingReport(context, renewed)
                                        InspectorSettingsStorage.advanceToNextReportNumber(context)
                                    }
                                    nextReportNumber = InspectorSettingsStorage.getNextReportNumber(context)
                                    accessoryRenewalExcluded = accessoryRenewalExcluded + sourceNumber
                                    returnToRenewalAfterForm = true
                                    editingReportNumber = newNumber
                                    // reloadSavedReports() נקרא ב-onSaveReport / onBackToHome לאחר חזרה מהטופס
                                    currentScreen = "form"
                                }
                            }
                        },
                        onBack = {
                            accessoryRenewalExcluded = emptySet()
                            renewalSelectedSiteId = ""
                            renewalMonthsText = ""
                            currentScreen = "home"
                        },
                        onStartNewRenewalSession = {
                            accessoryRenewalExcluded = emptySet()
                            renewalSelectedSiteId = ""
                            renewalMonthsText = ""
                        }
                    )

                    "form" -> {
                        val editingReport = editingReportNumber?.let {
                            ReportStorage.getWorkingReport(context, it)
                        }

                        val inRows = smartFormReturnedRows
                        val inDefects = smartFormReturnedDefects
                        SideEffect {
                            if (smartFormReturnedRows.isNotEmpty()) smartFormReturnedRows = emptyList()
                            if (smartFormReturnedDefects.isNotEmpty()) smartFormReturnedDefects = emptyList()
                        }

                        InspectionFormScreen(
                            initialInspectorNumber = inspectorDetails,
                            initialRunningNumber = nextReportNumber,
                            editingReport = editingReport,
                            onBackToHome = {
                                editingReportNumber = null
                                reloadSavedReports()
                                if (returnToRenewalAfterForm) {
                                    returnToRenewalAfterForm = false
                                    currentScreen = "renewal"
                                } else {
                                    currentScreen = "home"
                                }
                            },
                            onSaveReport = { savedInspection, shouldAdvanceReportNumber ->
                                val currentNextNumber = InspectorSettingsStorage.getNextReportNumber(context)
                                if (
                                    shouldAdvanceReportNumber ||
                                    savedInspection.reportNumber == currentNextNumber
                                ) {
                                    InspectorSettingsStorage.advanceToNextReportNumber(context)
                                }
                                nextReportNumber = InspectorSettingsStorage.getNextReportNumber(context)
                                editingReportNumber = null
                                reloadSavedReports()
                                if (returnToRenewalAfterForm) {
                                    returnToRenewalAfterForm = false
                                    currentScreen = "renewal"
                                } else {
                                    currentScreen = "home"
                                }
                            },
                            onNavigateToSmartAccessories = { date, rows, defects, draftRunningNumber ->
                                // שמור את מספר התסקיר כדי שהטופס יטען את הטיוטה השמורה בחזרה
                                editingReportNumber = draftRunningNumber
                                smartFormInspectionDate = date
                                smartFormPendingRows = rows
                                smartFormPendingDefects = defects
                                currentScreen = "smart_accessories"
                            },
                            incomingSmartRows = inRows,
                            incomingSmartDefects = inDefects
                        )
                    }

                    "smart_accessories" -> com.nasavi.liftinginspectorpro.ui.screens.SmartAccessoriesTableScreen(
                        inspectionDate = smartFormInspectionDate,
                        initialRows = smartFormPendingRows,
                        initialDefects = smartFormPendingDefects,
                        draftRunningNumber = editingReportNumber ?: "",
                        onTransferToReport = { rows, defects ->
                            smartFormReturnedRows = rows
                            smartFormReturnedDefects = defects
                            // editingReportNumber נשמר — הטופס יטען את הטיוטה השמורה
                            currentScreen = "form"
                        },
                        onBack = {
                            // חזרה לטופס אם באנו ממנו (editingReportNumber מוגדר), אחרת לדף הבית
                            if (editingReportNumber != null) currentScreen = "form"
                            else currentScreen = "home"
                        }
                    )

                    "saved" -> SavedRecordsScreen(
                        reports = savedReports,
                        onEditReport = { reportNumber ->
                            editingReportNumber = reportNumber
                            currentScreen = "form"
                        },
                        onBack = {
                            reloadSavedReports()
                            currentScreen = "home"
                        }
                    )

                    "lists" -> CustomListsManagementScreen(
                        onBack = {
                            currentScreen = "home"
                        }
                    )
                }
            }
        }
    }
}

/**
 * בונה כרטיסים למסך רשומות.
 */
private fun importReportTemplateFromUri(
    context: android.content.Context,
    uri: Uri
): InspectorSettingsStorage.ReportTextSettings? {
    return try {
        val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            ?: return null
        val obj = JSONObject(json)
        val defaults = InspectorSettingsStorage.defaultReportTextSettings()
        val importedNotes = obj.optJSONArray("templateNotesList")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList()
        InspectorSettingsStorage.ReportTextSettings(
            title             = obj.optString("title",          defaults.title),
            legalLine         = obj.optString("legalLine",      defaults.legalLine),
            defaultNote       = obj.optString("defaultNote",    defaults.defaultNote),
            declaration       = obj.optString("declaration",    defaults.declaration),
            printDateLabel    = obj.optString("printDateLabel", defaults.printDateLabel),
            bottomWarning     = obj.optString("bottomWarning",  defaults.bottomWarning),
            templateNotesList = importedNotes
        )
    } catch (e: Exception) {
        null
    }
}

private fun loadSavedReportsForCards(context: android.content.Context): List<SavedInspection> {
    return ReportStorage.loadWorkingReports(context).map { stored ->
        SavedInspection(
            reportNumber = stored.reportNumber,
            date = stored.inspectionDate,
            client = stored.owner,
            nextInspectionDate = stored.nextInspectionDate,
            site = stored.site,
            summary = if (stored.isLockedForNewAccessories) {
                "תסקיר הופק ל-PDF | נעול להוספת אביזרים | אביזרים: ${stored.accessories.size}"
            } else {
                "רשומת עבודה לעריכה | אביזרים: ${stored.accessories.size}"
            }
        )
    }
}



