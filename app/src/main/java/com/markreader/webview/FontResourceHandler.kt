package com.markreader.webview

import android.content.res.Resources
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import com.markreader.R
import java.io.ByteArrayInputStream

/**
 * Serves the app's bundled reading fonts to the page.
 *
 * The fonts live in `res/font/`, which is where the platform wants them: Compose's typography
 * and the raw-text reader both reach them through `R.font`, and a `TextView` cannot load a
 * typeface out of `assets/` without being handed one. But the page can only fetch what a
 * [WebViewAssetLoader] handler serves, so without this the reading-font setting had no effect on
 * rendered Markdown at all — the CSS named `Merriweather`, nothing supplied it, and the stack
 * fell straight through to `Georgia`. Merriweather and "System serif" were the same font.
 *
 * ### Why a handler of our own
 *
 * Two routes suggested themselves and neither is this one.
 *
 * [WebViewAssetLoader.ResourcesPathHandler] over `res/` is the obvious answer, and it resolves
 * names with `Resources.getIdentifier` — a lookup by string, in a release build that has
 * `shrinkResources` on. That build is not hypothetical: in the release APK these files are
 * `res/8g.ttf` and `res/X5.ttf`, so a name is already the *only* way to address them and it is
 * the thing a shrinker is entitled to take. It also opens the door to every resource the app has,
 * by name, for the sake of five files. A fixed table of ids costs nothing and is the whole door.
 *
 * Moving the TTFs to `assets/` would need no handler at all, since `AssetsPathHandler` is already
 * registered — but it drags the Kotlin side along: [com.markreader.ui.theme.Typography] builds
 * its families at module scope with no `Context` to get an `AssetManager` from, so the theme
 * would have to become context-aware to fix a font in the WebView. The alternative, keeping both
 * copies, ships every glyph twice in an APK that F-Droid rebuilds byte-for-byte.
 *
 * So: `R.font` ids, named as constants the compiler checks, which is also what keeps the shrinker
 * from touching them.
 *
 * ### The other half is in the page
 *
 * Serving bytes is not enough — CSS has to declare the families. Those `@font-face` rules are in
 * `android-host.html`, not in any vendored stylesheet, because **which fonts a host bundles is
 * host business**: desktop ships none of these and would have to serve them from somewhere else
 * if it did. [BUNDLED_FONTS] and that block are one fact in two files, and
 * `BundledFontsTest` is what stops them drifting apart — a missing rule or a mistyped URL
 * does not fail here, it silently renders in the fallback font, which is the bug this fixes.
 */
internal class FontResourceHandler(
    private val resources: Resources,
    private val onMiss: (String) -> Unit = {},
) : WebViewAssetLoader.PathHandler {

    override fun handle(path: String): WebResourceResponse {
        val id = BUNDLED_FONTS[path.trimStart('/')]
        if (id == null) {
            onMiss(path)
            return notFound()
        }

        // A resource that is present at compile time and missing at run time means the shrinker
        // took it, which is a build problem rather than a device one. It still cannot be allowed
        // to reach the WebView's thread as an exception.
        val body = runCatching { resources.openRawResource(id) }.getOrNull()
        if (body == null) {
            onMiss("$path — no such resource")
            return notFound()
        }

        // No cache headers, matching `AssetsPathHandler`, which serves the renderer's own modules
        // and stylesheets bare. These bytes are immutable within a build but the URL does not
        // change between builds, so a long `max-age` would be a font that survives an app update.
        return WebResourceResponse(MIME_TYPE, null, HTTP_OK, "OK", HEADERS, body)
    }

    private fun notFound(): WebResourceResponse =
        WebResourceResponse(
            "text/plain", "utf-8", HTTP_NOT_FOUND, "Not Found",
            HEADERS, ByteArrayInputStream(ByteArray(0)),
        )

    internal companion object {

        /**
         * The served set, as URL name -> resource. Nothing else is reachable.
         *
         * Names match the resource names so a grep for either finds both. Only the weights the
         * app actually bundles are here: there is no italic of any of them, so emphasis is
         * synthesised by the WebView — the same skew the `TextView` reader has always drawn, and
         * not a regression.
         */
        val BUNDLED_FONTS = mapOf(
            "merriweather_regular.ttf" to R.font.merriweather_regular,
            "merriweather_bold.ttf" to R.font.merriweather_bold,
            "merriweather_sans_regular.ttf" to R.font.merriweather_sans_regular,
            "merriweather_sans_bold.ttf" to R.font.merriweather_sans_bold,
            "jetbrains_mono_regular.ttf" to R.font.jetbrains_mono_regular,
        )

        private const val MIME_TYPE = "font/ttf"

        const val HTTP_OK = 200
        const val HTTP_NOT_FOUND = 404

        /**
         * `nosniff` for the same reason [DocumentResourceHandler] states it: the type here is
         * asserted, and sniffing is how an asserted type becomes a different one.
         */
        val HEADERS = mapOf("X-Content-Type-Options" to "nosniff")
    }
}

/**
 * The [WebViewAssetLoader] prefix the bundled fonts are served under.
 *
 * Deliberately not inside [RESOURCE_PREFIX]. Every path there names something the user adopted
 * through SAF, and an app resource is not that — sharing the prefix would mean
 * [SafPathSpace] having a branch for paths that are not virtual paths at all.
 *
 * Shared with the `@font-face` rules in `android-host.html`, which name it as an absolute path.
 */
internal const val FONT_PREFIX = "/__mdr_fonts/"
