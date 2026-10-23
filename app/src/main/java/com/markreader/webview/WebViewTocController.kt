package com.markreader.webview

import org.json.JSONObject

/** Rendered headings are DOM identities, deliberately not native text offsets. */
data class WebViewHeading(val slug: String, val text: String, val level: Int)

data class WebViewToc(
    val generation: Long = 0,
    val headings: List<WebViewHeading> = emptyList(),
    val activeIndex: Int = -1,
)

/** Main-thread state. Every payload is bounded and belongs to the currently requested document. */
class WebViewTocController(private val call: (String, JSONObject) -> Unit) {
    var onChanged: (WebViewToc) -> Unit = {}
    private var generation = 0L
    private var current = WebViewToc()

    fun invalidate(): Long {
        generation += 1
        current = WebViewToc(generation)
        onChanged(current)
        return generation
    }

    fun receive(json: String) {
        if (json.length > MAX_PAYLOAD_CHARS) return
        val parsed = runCatching { parse(JSONObject(json)) }.getOrNull() ?: return
        current = parsed
        onChanged(current)
    }

    fun reveal(heading: WebViewHeading) {
        if (current.headings.none { it.slug == heading.slug }) return
        call("revealHeading", JSONObject().put("generation", generation).put("slug", heading.slug))
    }

    private fun parse(payload: JSONObject): WebViewToc? {
        if (payload.optLong("generation", -1) != generation) return null
        val items = payload.optJSONArray("headings") ?: return null
        if (items.length() > MAX_HEADINGS) return null
        val headings = ArrayList<WebViewHeading>(items.length())
        val seen = HashSet<String>()
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            val slug = item.getString("slug")
            val text = item.getString("text")
            val level = item.getInt("level")
            if (slug.isEmpty() || slug.length > MAX_LABEL_CHARS || text.length > MAX_LABEL_CHARS) return null
            if (level !in 1..MAX_HEADING_LEVEL || !seen.add(slug)) return null
            headings.add(WebViewHeading(slug, text, level))
        }
        val active = payload.optString("activeSlug", "")
        return WebViewToc(generation, headings, headings.indexOfFirst { it.slug == active })
    }

    private companion object {
        const val MAX_HEADING_LEVEL = 6
        const val MAX_HEADINGS = 2000
        const val MAX_LABEL_CHARS = 512
        // JSON escaping can expand every label character sixfold.
        const val MAX_PAYLOAD_CHARS = 13_000_000
    }
}
