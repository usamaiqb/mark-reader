package com.markreader.ui.export

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import com.markreader.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Getting a rendered document out of the app: to the printer, to a file, or to another app.
 *
 * **It renders nothing.** It used to: it parsed the source with commonmark and rendered it
 * against a stylesheet written by hand here, which made export the app's *third* Markdown
 * implementation and meant the file that left never quite matched the document on screen — a gap
 * that widened the moment the reader moved into the shared renderer. The HTML now arrives already
 * built, from the same page the reader draws with, through
 * [com.markreader.webview.WebViewDocumentHost.renderExportHtml].
 *
 * What is left here is platform integration, which is the half a renderer cannot have: the print
 * framework, the cache file behind a `FileProvider`, and the share sheet.
 *
 * The WebView below keeps `javaScriptEnabled` false, and that is not vestigial now that the HTML
 * comes from a page where it is on. The two do different jobs: the host page runs a renderer over
 * untrusted document text, behind a CSP and a sanitiser; this one lays a finished, sanitised
 * document out for the printer and has nothing left to execute.
 */
class ExportManager(private val context: Context) {

    /**
     * Prints [html] through the platform print dialog, which is also where "save as PDF" lives.
     *
     * The adapter cannot be built until the page has laid out, so the print is started from
     * `onPageFinished` rather than from here.
     */
    fun exportPdf(html: String, fileName: String?) {
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = false
        webView.settings.loadsImagesAutomatically = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val appName = context.getString(R.string.app_name)
                val printAdapter = view.createPrintDocumentAdapter(fileName ?: appName)
                printManager.print(appName, printAdapter, PrintAttributes.Builder().build())
            }
        }
        // A null base URL, deliberately. Every reference the document needs is inside the file —
        // stylesheets inlined, images as `data:` URIs — so there is nothing for a base to
        // resolve, and giving it one would only make an unreachable app origin reachable again.
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    /** Writes [html] to a cache file and offers it to the share sheet. */
    fun exportHtml(html: String) {
        val file = createHtmlFile(html)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.export_share_html_chooser))
        )
    }

    /** The document's own text, unrendered, straight to another app. */
    fun shareRawMarkdown(markdown: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, markdown)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.export_share_markdown_chooser))
        )
    }

    private fun createHtmlFile(html: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(context.cacheDir, "markreader_export_$stamp.html")
        file.writeText(html)
        return file
    }
}
