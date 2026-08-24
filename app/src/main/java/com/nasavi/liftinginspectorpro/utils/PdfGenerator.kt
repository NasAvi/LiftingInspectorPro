package com.nasavi.liftinginspectorpro.utils

import android.app.Activity
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast

/**
 * PdfGenerator.kt — קוד מלא 13
 *
 * WebView: 1000×8000px, alpha=0.01f, ללא translationX (על-המסך לטעינת tiles מלאה).
 * גובה 8000px מבטיח rasterization של כל תמונות הנספח כולל 3-4 (לא מוגבל ל-1600px).
 *
 * רצף onPageFinished:
 *   1. גלילה לתחתית — כופה טעינת tiles לכל תמונות הנספח
 *   2. 1.2s — המתן לרינדור tiles
 *   3. img.decode() — כופה פענוח תמונות גם מחוץ ל-viewport
 *   4. גלילה לראש + polling + 400ms → print
 */
fun generatePdfFromHtml(
    context: Context,
    htmlContent: String,
    fileName: String = "lifting_inspection_report"
) {
    val activity = context as? Activity ?: run {
        Toast.makeText(context, "שגיאה: לא ניתן לפתוח PDF ללא Activity", Toast.LENGTH_LONG).show()
        return
    }

    Toast.makeText(context, "פותח חלון הפקת PDF...", Toast.LENGTH_SHORT).show()

    val root = activity.window.decorView as FrameLayout

    val webView = WebView(activity).apply {
        alpha = 0.01f
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        settings.javaScriptEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        layoutDirection = View.LAYOUT_DIRECTION_LTR
    }

    val params = FrameLayout.LayoutParams(1000, 8000).apply {
        gravity = Gravity.TOP or Gravity.START
    }
    root.addView(webView, params)
    // ללא translationX — WebView על-המסך כדי ש-Chromium ירנדר tiles לכל 8000px

    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            // שלב 1: גלול לתחתית — כופה על ה-WebView לרנדר tiles עבור כל התמונות
            webView.scrollTo(0, 1_000_000)

            webView.postDelayed({
                // שלב 2: img.decode() — כופה פענוח גם לתמונות שמחוץ ל-viewport
                webView.evaluateJavascript(
                    """
                    (function() {
                        var imgs = Array.from(document.querySelectorAll('img'));
                        if (!imgs.length) { window.__imagesReady = true; return; }
                        Promise.all(imgs.map(function(img) {
                            if (img.complete && img.naturalHeight > 0) return Promise.resolve();
                            return img.decode ? img.decode().catch(function(){}) :
                                new Promise(function(r) { img.onload = img.onerror = r; });
                        })).then(function()  { window.__imagesReady = true; })
                           .catch(function() { window.__imagesReady = true; });
                    })();
                    """.trimIndent()
                ) { _ ->
                    // שלב 3: גלול חזרה לראש
                    webView.scrollTo(0, 0)

                    // שלב 4: בדוק כל 400ms אם כל התמונות מוכנות (עד 10 שניות)
                    fun checkAndPrint(attemptsLeft: Int) {
                        webView.evaluateJavascript("!!window.__imagesReady") { result ->
                            if (result.trim() == "true" || attemptsLeft <= 0) {
                                // שלב 5: 400ms נוספות להתייצבות, ואחר-כך הדפס
                                webView.postDelayed({
                                    webView.settings.javaScriptEnabled = false
                                    try {
                                        val printManager =
                                            activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
                                        val printAdapter =
                                            webView.createPrintDocumentAdapter(fileName)
                                        printManager.print(
                                            fileName,
                                            printAdapter,
                                            PrintAttributes.Builder()
                                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                                .build()
                                        )
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            activity,
                                            "שגיאה בפתיחת PDF: ${e.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } finally {
                                        webView.postDelayed({
                                            try {
                                                root.removeView(webView)
                                                webView.destroy()
                                            } catch (_: Exception) {
                                            }
                                        }, 60000)
                                    }
                                }, 400)
                            } else {
                                webView.postDelayed({ checkAndPrint(attemptsLeft - 1) }, 400)
                            }
                        }
                    }
                    // המתן 200ms לאחר גלילה חזרה לראש, ואז בדוק
                    webView.postDelayed({ checkAndPrint(25) }, 200)
                }
            }, 1200) // 1.2 שניות בתחתית — מספיק לרינדור ה-tiles
        }
    }

    webView.loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
}

