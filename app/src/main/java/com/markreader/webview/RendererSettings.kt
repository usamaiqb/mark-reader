package com.markreader.webview

import org.json.JSONObject

/** How the document decides between the light and dark VS Code palettes. */
enum class RendererTheme(internal val wire: String) {
    LIGHT("light"),
    DARK("dark"),

    /** Follows the WebView's `prefers-color-scheme`, which follows the system. */
    SYSTEM("system"),
}

/**
 * The renderer settings the Android host drives.
 *
 * A deliberately small slice of the shared renderer's configuration — the fields the app has an
 * opinion about today. The wire format is the object `config.ts` persists, and the page validates
 * every value again through `parseConfig` on the way in: an out-of-range font size falls back to
 * the host default for that one setting rather than rendering something unreadable.
 *
 * A null means "leave the host default alone". `android-host.js` sets phone-appropriate
 * typography through `setHostDefaults`, and an absent key is not overwritten by `parseConfig` —
 * so the app only has to state what it actually wants to change.
 */
data class RendererSettings(
    val theme: RendererTheme = RendererTheme.SYSTEM,
    val wordWrap: Boolean = true,
    val fontSize: Int? = null,
    val lineHeight: Int? = null,
    val fontFamily: String? = null,
    val codeFontFamily: String? = null,
    val sourceCode: Boolean = false,
) {
    internal fun toJson(): JSONObject = JSONObject().apply {
        put("theme", theme.wire)
        put("wordWrap", wordWrap)
        fontSize?.let { put("fontSize", it) }
        lineHeight?.let { put("lineHeight", it) }
        fontFamily?.let { put("fontFamily", it) }
        codeFontFamily?.let { put("codeFontFamily", it) }
        put("sourceCode", sourceCode)
    }
}
