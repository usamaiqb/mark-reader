package com.markreader.webview

/**
 * One entry in the outline the page reports.
 *
 * **Located by [slug], not by an offset**, and that is why this lives beside the WebView host
 * rather than in `format/`. A heading's position is a fact about the rendering, and the rendering
 * is inside the page — so the page names each heading with the id it gave that heading's element,
 * and the only thing that can act on the name is the page. Nothing on this side can turn a slug
 * into a scroll position, and nothing needs to: it hands the slug back through `revealSlug`.
 *
 * It used to carry a character offset into rendered text, which is what a `TextView` scrolls.
 * That died with the native rendered path.
 */
data class HeadingItem(
    val text: String,
    val level: Int,
    val slug: String
)
