package com.nasavi.liftinginspectorpro.utils

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.roundToInt

// WEB_W=1000 → WebKit מרנדר ב-1000px (vs 980px CSS viewport) → scale≈1.02 → רזולוציה גבוהה
// ללא LAYER_TYPE_SOFTWARE: webView.draw(canvas) ישיר ל-PDF canvas → טקסט ברור (וקטורי)
private const val PDF_W = 595
private const val PDF_H = 842
private const val MARGIN = 40
private const val WEB_W = 1000
private const val CONTENT_W = PDF_W - 2 * MARGIN   // 515
private const val CONTENT_H = PDF_H - 2 * MARGIN   // 762
private val SCALE = CONTENT_W.toFloat() / WEB_W.toFloat()   // 515/1000 = 0.515
private val WEB_H = (CONTENT_H / SCALE).roundToInt()        // ≈ 1479

private const val PAGE_BREAK_MARKER = "<div class='pageBreak'></div>"

/** פותח PDF ב-viewer חיצוני ישירות (ללא AlertDialog). */
fun openPdfFile(context: Context, file: java.io.File) = viewPdf(context, file)
/** משתף PDF. */
fun sharePdfFile(context: Context, file: java.io.File) = sharePdf(context, file)

fun saveHtmlAsPdfAndOpen(
    context: Context,
    htmlContent: String,
    fileName: String,
    onReady: ((java.io.File) -> Unit)? = null
) {
    val activity = context as? Activity ?: return
    Toast.makeText(context, "מייצר PDF...", Toast.LENGTH_SHORT).show()

    // מפצלים לעמודים בגבול pageBreak:
    //   עמוד 0 = תסקיר (טקסט) — 1000px, useWideViewPort=true, ללא SOFTWARE
    //   עמוד 1+ = נספח תמונות — WebView נפרד, תמונות ב-y=0, delay 3000ms
    val pages = splitAtPageBreak(context, htmlContent)
    if (pages.isEmpty()) {
        Toast.makeText(context, "שגיאה בכתיבת קובץ", Toast.LENGTH_LONG).show()
        return
    }
    renderPage(activity, pages, 0, PdfDocument(), fileName, onReady)
}

private fun splitAtPageBreak(context: Context, html: String): List<File> {
    val markerIdx = html.indexOf(PAGE_BREAK_MARKER)
    if (markerIdx < 0) {
        val f = writeTempHtml(context, html, 0) ?: return emptyList()
        return listOf(f)
    }

    // שומרים את ה-<head> (CSS, meta) לשימוש בעמוד הנספח
    val bodyOpenIdx = html.indexOf("<body")
    val bodyCloseIdx = if (bodyOpenIdx >= 0) html.indexOf(">", bodyOpenIdx) + 1 else 0
    val headSection = if (bodyCloseIdx > 0) html.substring(0, bodyCloseIdx)
                      else "<html dir=\"rtl\"><body>"

    val page0Html = html.substring(0, markerIdx).trimEnd() + "\n</body></html>"
    val page1Html = headSection + "\n" + html.substring(markerIdx + PAGE_BREAK_MARKER.length).trimStart()

    val files = mutableListOf<File>()
    writeTempHtml(context, page0Html, 0)?.let { files.add(it) }
    writeTempHtml(context, page1Html, 1)?.let { files.add(it) }
    return files
}

private fun writeTempHtml(context: Context, html: String, index: Int): File? = try {
    File(context.cacheDir, "pdf_page${index}_${System.currentTimeMillis()}.html")
        .also { it.outputStream().writer(Charsets.UTF_8).use { w -> w.write(html) } }
} catch (_: Exception) { null }

private fun renderPage(
    activity: Activity,
    htmlFiles: List<File>,
    pageIndex: Int,
    pdf: PdfDocument,
    fileName: String,
    onReady: ((File) -> Unit)? = null
) {
    if (pageIndex >= htmlFiles.size) {
        finalizePdf(activity, pdf, fileName, onReady)
        return
    }

    val htmlFile = htmlFiles[pageIndex]
    val root = activity.window.decorView as FrameLayout

    // 1000×WEB_H px, ללא LAYER_TYPE_SOFTWARE → draw(canvas) ישיר → טקסט ברור
    // useWideViewPort=true → viewport CSS=980px → פריסת הטבלאות תקינה
    // translationX: מסתיר מהמשתמש ללא alpha (alpha נמוך → draw ב-PDF canvas כמעט שקוף)
    val webView = WebView(activity).apply {
        settings.javaScriptEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.allowFileAccess = true
        setBackgroundColor(android.graphics.Color.WHITE)
        layoutDirection = View.LAYOUT_DIRECTION_LTR
    }
    root.addView(webView, FrameLayout.LayoutParams(WEB_W, WEB_H))
    webView.translationX = -(WEB_W + 100).toFloat()

    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            webView.postDelayed({
                try {
                    if (pageIndex == 0) {
                        renderReportPage(activity, webView, root, pdf, pageIndex, htmlFiles, fileName, htmlFile, onReady)
                    } else {
                        webView.postDelayed({
                            try {
                                val displayHeight = (webView.contentHeight * webView.scale)
                                    .toInt().coerceAtLeast(WEB_H)
                                var numStrips = ceil(displayHeight.toFloat() / WEB_H).toInt().coerceIn(1, 50)
                                if (numStrips > 1) {
                                    val lastStripContent = displayHeight - (numStrips - 1) * WEB_H
                                    if (lastStripContent <= 20) numStrips -= 1
                                }
                                renderStrip(
                                    activity, webView, root, pdf,
                                    0, numStrips, numStrips, 1 + pageIndex,
                                    htmlFiles, pageIndex, fileName, htmlFile, onReady
                                )
                            } catch (e: Throwable) {
                                root.removeView(webView)
                                webView.destroy()
                                htmlFile.delete()
                                Toast.makeText(activity, "שגיאה בנספח תמונות: ${e.message}", Toast.LENGTH_SHORT).show()
                                renderPage(activity, htmlFiles, pageIndex + 1, pdf, fileName, onReady)
                            }
                        }, 3000)
                        return@postDelayed
                    }
                } catch (e: Throwable) {
                    root.removeView(webView)
                    webView.destroy()
                    htmlFile.delete()
                    Toast.makeText(activity, "שגיאה בעמוד ${pageIndex + 1}: ${e.message}", Toast.LENGTH_SHORT).show()
                    renderPage(activity, htmlFiles, pageIndex + 1, pdf, fileName, onReady)
                }
            }, 1500)
        }
    }

    webView.loadUrl("file://${htmlFile.absolutePath}")
}

private fun renderReportPage(
    activity: Activity,
    webView: WebView,
    root: FrameLayout,
    pdf: PdfDocument,
    pageIndex: Int,
    htmlFiles: List<File>,
    fileName: String,
    htmlFile: File,
    onReady: ((File) -> Unit)? = null
) {
    val scale = webView.scale.takeIf { it > 0f } ?: 1f
    val webHCss = (WEB_H / scale).roundToInt()
    val pageBreakJs = """
        (function(){
            var pb = document.querySelector('.pageBreak');
            if (!pb) return;
            var top = pb.offsetTop;
            var rem = top % $webHCss;
            if (rem > 0) pb.style.paddingBottom = ($webHCss - rem) + 'px';
        })();
    """.trimIndent()

    webView.evaluateJavascript(pageBreakJs) { _ ->
        webView.postDelayed({
            try {
                val displayHeight = (webView.contentHeight * webView.scale)
                    .toInt().coerceAtLeast(WEB_H)
                var numStrips = ceil(displayHeight.toFloat() / WEB_H).toInt().coerceIn(1, 50)
                if (numStrips > 1) {
                    val lastStripContent = displayHeight - (numStrips - 1) * WEB_H
                    if (lastStripContent <= 20) numStrips -= 1
                }

                renderStrip(
                    activity, webView, root, pdf,
                    0, numStrips, numStrips, /* pdfStartPage */ 1,
                    htmlFiles, pageIndex, fileName, htmlFile, onReady
                )
            } catch (e: Throwable) {
                root.removeView(webView)
                webView.destroy()
                htmlFile.delete()
                Toast.makeText(activity, "שגיאה ביצירת PDF: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, 400)
    }
}

private fun renderStrip(
    activity: Activity,
    webView: WebView,
    root: FrameLayout,
    pdf: PdfDocument,
    stripIndex: Int,
    totalStrips: Int,
    totalReportStrips: Int,
    pdfStartPage: Int,
    htmlFiles: List<File>,
    pageFileIndex: Int,
    fileName: String,
    htmlFile: File,
    onReady: ((File) -> Unit)? = null
) {
    webView.scrollTo(0, stripIndex * WEB_H)
    webView.postDelayed({
        try {
            drawStrip(webView, pdf, pdfStartPage + stripIndex)
        } catch (e: Throwable) {
            Toast.makeText(activity, "שגיאה בעמוד ${pdfStartPage + stripIndex}: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        val nextStrip = stripIndex + 1
        if (nextStrip < totalStrips) {
            renderStrip(
                activity, webView, root, pdf,
                nextStrip, totalStrips, totalReportStrips, pdfStartPage,
                htmlFiles, pageFileIndex, fileName, htmlFile, onReady
            )
        } else {
            root.removeView(webView)
            webView.destroy()
            htmlFile.delete()
            renderPage(activity, htmlFiles, pageFileIndex + 1, pdf, fileName, onReady)
        }
    }, 400)
}

private fun drawStrip(webView: WebView, pdf: PdfDocument, pdfPageNumber: Int) {
    val pageInfo = PdfDocument.PageInfo.Builder(PDF_W, PDF_H, pdfPageNumber).create()
    val page = pdf.startPage(pageInfo)
    val canvas: Canvas = page.canvas
    canvas.drawColor(android.graphics.Color.WHITE)
    canvas.save()
    canvas.translate(MARGIN.toFloat(), MARGIN.toFloat())
    canvas.scale(SCALE, SCALE)
    webView.draw(canvas)
    canvas.restore()
    pdf.finishPage(page)
}

private fun finalizePdf(context: Context, pdf: PdfDocument, fileName: String, onReady: ((File) -> Unit)? = null) {
    val folder = File(context.getExternalFilesDir(null), "reports")
    if (!folder.exists()) folder.mkdirs()

    val safeName = fileName
        .replace("/", "_").replace("\\", "_")
        .replace(":", "_").replace(" ", "_")

    val file = File(folder, "$safeName.pdf")
    FileOutputStream(file).use { pdf.writeTo(it) }
    pdf.close()

    Toast.makeText(context, "PDF נשמר ✔", Toast.LENGTH_SHORT).show()
    if (onReady != null) {
        onReady(file)
    } else {
        showPdfOptionsDialog(context, file)
    }
}

private fun showPdfOptionsDialog(context: Context, file: File) {
    val activity = context as? Activity ?: return
    AlertDialog.Builder(activity)
        .setTitle("תסקיר PDF מוכן")
        .setMessage("בחר פעולה:")
        .setPositiveButton("הצג PDF בלבד") { _, _ -> viewPdf(context, file) }
        .setNeutralButton("שתף (WhatsApp / מייל)") { _, _ -> sharePdf(context, file) }
        .setNegativeButton("ביטול") { dialog, _ -> dialog.dismiss() }
        .show()
}

private fun viewPdf(context: Context, file: File) {
    val uri = getUri(context, file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "פתח תסקיר PDF")
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    } catch (_: Exception) {
        Toast.makeText(context, "לא נמצאה אפליקציה לפתיחת PDF. הקובץ נשמר.", Toast.LENGTH_LONG).show()
    }
}

private fun sharePdf(context: Context, file: File) {
    val uri = getUri(context, file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "שתף תסקיר PDF")
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    } catch (_: Exception) {
        Toast.makeText(context, "לא נמצאה אפליקציה לשיתוף PDF. הקובץ נשמר.", Toast.LENGTH_LONG).show()
    }
}

private fun getUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

