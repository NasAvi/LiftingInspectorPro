# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android app (Hebrew, RTL) for generating lifting-equipment inspection reports (תסקירי בדיקה לאביזרי הרמה). An inspector fills a form, the app builds an HTML report and produces a PDF via Android's print framework. Single module (`:app`), Kotlin + Jetpack Compose, minSdk 26, compileSdk/targetSdk 34, JDK 17, Kotlin 1.9.24, AGP 8.5.2.

All UI strings, code comments, and report content are in Hebrew — keep new user-facing text and comments in Hebrew to match.

## Commands

```powershell
.\gradlew.bat assembleDebug        # build debug APK
.\gradlew.bat installDebug         # build + install on connected device/emulator
.\gradlew.bat compileDebugKotlin   # fast compile check without packaging
```

There are no unit/instrumented tests and no lint configuration beyond AGP defaults. Verification is done by building and running on a device.

## Git Workflow — MANDATORY

**After every significant change (feature, fix, refactor), commit and push to GitHub.** This is part of the standard workflow for this project, not optional:

1. `git add -A`
2. `git commit -m "<תיאור קצר של השינוי>"`
3. `git push`

If the repository has no remote, git is not initialized, or push fails due to missing credentials — **stop and ask the user to help set up GitHub access** (create the remote repo, authenticate `gh auth login` or configure credentials). Do not silently skip the push.

## Architecture

### Navigation & State

`MainActivity.kt` is the single Activity and the only navigation point. Navigation is a manual `when (currentScreen)` over string keys (`"main_menu"`, `"home"`, `"form"`, `"saved"`, `"lists"`, `"machines_home"`) — there is no Navigation library and no ViewModels. All cross-screen state (inspector details, next report number, saved report cards) lives as `remember { mutableStateOf(...) }` in `MainActivity` and is passed down via parameters/callbacks.

The app has two branches from the main menu: **lifting accessories** (the complete flow: home → form → saved records → custom lists) and **lifting machines** (`LiftingMachinesHomeScreen`, defined inside `MainSelectionScreen.kt`, still under construction).

### Data Layer (`data/`)

No database. Everything is persisted as JSON inside SharedPreferences or app-private files, through singleton `object`s:

- **`ReportStorage`** — the core. Stores one editable `WorkingReport` per report number, plus an immutable PDF-version history (`R6006`, `R6006.1`, `R6006.2`...). Once a PDF was generated, the report is locked for adding new accessories (`isLockedForNewAccessories`).
- **`InspectorSettingsStorage`** — inspector details, the next running report number, stamp/signature image paths, and editable report-text templates.
- **`ClientMemoryStore` / `ManufacturerModelMemoryStore`** — autocomplete memory for previously used clients and manufacturer/model pairs.
- **`ReportPhotoStorage`** — accessory photos; shared via `FileProvider` (`${applicationId}.fileprovider`).
- **`AppBackupManager`** — export/import of all app data.

### Report Numbering

The running report number (e.g. `R6006`) is managed in `InspectorSettingsStorage` and advanced when a new report is saved/exported. `MainActivity.reloadSavedReports()` contains a self-healing pass that recomputes the next number from the highest existing report number — keep this invariant intact when touching numbering logic.

### PDF Pipeline (`utils/`)

`ReportTemplate.buildReportHtml()` builds the full report HTML using a Hebrew variable map (e.g. `"שם הבודק"`, `"מספר תסקיר"`) substituted into editable text templates → `PdfGenerator.generatePdfFromHtml()` renders it in an off-screen but attached `WebView` (must stay attached for the print framework; sized full but moved off-screen and non-clickable) → Android `PrintManager`. PDF "versions" are stored as the HTML snapshot, so re-printing old versions is possible.

### Core Logic (`core/`)

Pure-Kotlin helpers: `WllParser` (parses WLL values like `"5/45°"` into ton + angle), `InspectionNumberGenerator`, `AccessoryDescriptionBuilder`.

### ML Kit

`com.google.mlkit:text-recognition` is used for OCR of accessory plate data within the form.

## Important Notes

- **`app_CURRENT_BROKEN_BACKUP/`** is a frozen backup of an older broken state plus `UPDATE_0XX_NOTES.txt` change-history files. It is NOT part of the build (`settings.gradle.kts` only includes `:app`). Never edit it; consult the notes files only for historical context.
- `InspectionFormScreen.kt` (~5300 lines) and `MainSelectionScreen.kt` (~3900 lines) are very large single files containing many composables each. Prefer targeted reads (Grep for the composable name first) over reading them whole.
- Comments throughout reference "קוד מלא N" markers — these tag behaviors that past updates depend on; read the surrounding comment before changing such code.
- The app forces full-screen (hides system bars) in `MainActivity.onCreate` — intentional.
