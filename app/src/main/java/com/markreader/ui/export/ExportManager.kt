package com.markreader.ui.export

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import com.markreader.data.ReaderThemePreference
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportManager(private val context: Context) {
    private val parser = Parser.builder().build()
    private val renderer = HtmlRenderer.builder().build()

    fun exportPdf(markdown: String, theme: ReaderThemePreference, fileName: String?) {
        val html = buildHtml(markdown, theme)
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = false
        webView.settings.loadsImagesAutomatically = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val printAdapter = view.createPrintDocumentAdapter(fileName ?: "MarkReader")
                printManager.print("MarkReader", printAdapter, PrintAttributes.Builder().build())
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    fun exportHtml(markdown: String, theme: ReaderThemePreference) {
        val html = buildHtml(markdown, theme)
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
        context.startActivity(Intent.createChooser(intent, "Share HTML"))
    }

    fun shareRawMarkdown(markdown: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, markdown)
        }
        context.startActivity(Intent.createChooser(intent, "Share Markdown"))
    }

    private fun createHtmlFile(html: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(context.cacheDir, "markreader_export_$stamp.html")
        file.writeText(html)
        return file
    }

    private fun buildHtml(markdown: String, theme: ReaderThemePreference): String {
        val document = parser.parse(markdown)
        val content = renderer.render(document)

        val (background, text, codeBg, codeText, link) = when (theme) {
            ReaderThemePreference.Light ->
                arrayOf("#F9F9FF", "#1A1B1F", "#EEF0FF", "#1A1B1F", "#2A5BD7")
            ReaderThemePreference.Dark ->
                arrayOf("#121318", "#E3E2E8", "#1E1F26", "#E3E2E8", "#B5C4FF")
            ReaderThemePreference.Amoled ->
                arrayOf("#000000", "#E6E6EB", "#101014", "#E6E6EB", "#B5C4FF")
            ReaderThemePreference.Sepia ->
                arrayOf("#F6EDE3", "#201A17", "#E8D7CA", "#201A17", "#7A4E2B")
        }

        val css = """
            :root {
              color-scheme: ${if (theme == ReaderThemePreference.Light || theme == ReaderThemePreference.Sepia) "light" else "dark"};
            }
            body {
              margin: 0;
              padding: 32px 20px 48px;
              background: $background;
              color: $text;
              font-family: 'Merriweather', 'Georgia', 'Times New Roman', serif;
              font-size: 18px;
              line-height: 1.6;
            }
            main {
              max-width: 900px;
              margin: 0 auto;
            }
            h1, h2, h3, h4, h5, h6 {
              font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
              margin-top: 1.6em;
              margin-bottom: 0.6em;
            }
            p { margin: 0 0 1em; }
            a { color: $link; }
            code, pre {
              font-family: 'JetBrains Mono', 'Courier New', monospace;
              background: $codeBg;
              color: $codeText;
            }
            code { padding: 0.1em 0.3em; border-radius: 4px; }
            pre {
              padding: 16px;
              border-radius: 8px;
              overflow-x: auto;
            }
            blockquote {
              border-left: 3px solid $link;
              margin: 1em 0;
              padding: 0.2em 1em;
              background: $codeBg;
            }
            img { max-width: 100%; height: auto; }
            table { width: 100%; border-collapse: collapse; margin: 1em 0; }
            th, td { border: 1px solid ${link}55; padding: 8px; text-align: left; }
        """.trimIndent()

        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>MarkReader Export</title>
              <style>
              $css
              </style>
            </head>
            <body>
              <main>
                $content
              </main>
            </body>
            </html>
        """.trimIndent()
    }
}
