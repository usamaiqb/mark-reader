package com.markreader.ui.export

import android.net.Uri
import com.markreader.webview.RendererSettings
import com.markreader.webview.WebViewDocumentHost

/**
 * Renders a document as a standalone HTML file, through the page the reader draws with.
 *
 * The adoption is the whole of what this adds. A document's own images are served out of the
 * host's virtual path space, and the page resolves them against the path it was given — so an
 * export has to name the document there for its pictures to reach the file. It adopts a path of
 * its own rather than borrowing the reader's: the reader may not be on screen at all (raw mode
 * exports the rendered document too), and a token dropped here must not be one something else is
 * still reading through.
 *
 * @param host the Activity's page, or null where nothing is holding one — a preview or a test,
 *   where there is nothing to render with and export is simply unavailable.
 * @param documentUri the document's own URI. Blank for a document with no file behind it, which
 *   still needs a path to render *at* even though nothing under it resolves.
 * @return the file, or null if there was no page, the render failed, or it did not answer.
 */
suspend fun renderExportDocument(
    host: WebViewDocumentHost?,
    documentUri: String?,
    fileName: String,
    source: String,
    settings: RendererSettings,
): String? {
    if (host == null) {
        return null
    }
    val uri = documentUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
    val virtualPath = host.paths.adoptDocument(uri, fileName)
    return try {
        host.renderExportHtml(virtualPath, source, fileName, settings)
    } finally {
        host.paths.forget(virtualPath)
    }
}
