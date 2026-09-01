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
 * opinion about today, plus [codeFontFamily], which is this host's alone. The wire format is the
 * object `config.ts` persists, and the page validates every value again through `parseConfig` on
 * the way in: an out-of-range font size falls back to the host default for that one setting
 * rather than rendering something unreadable.
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
    /**
     * The monospace stack for fenced code.
     *
     * The one field here that is **not** part of the shared configuration. Code takes its font
     * from `--vscode-editor-font-family`, which the vendored theme shim declares as a palette
     * value, so there is no `config.ts` key to fill — `parseConfig` drops this one and
     * `android-host.js` reads it off the raw payload instead. It is here rather than in a second
     * call because it is a reader setting like the other four, and it changes with them.
     */
    val codeFontFamily: String? = null,
) {
    internal fun toJson(): JSONObject = JSONObject().apply {
        put("theme", theme.wire)
        put("wordWrap", wordWrap)
        fontSize?.let { put("fontSize", it) }
        lineHeight?.let { put("lineHeight", it) }
        fontFamily?.let { put("fontFamily", it) }
        codeFontFamily?.let { put("codeFontFamily", it) }
    }
}
