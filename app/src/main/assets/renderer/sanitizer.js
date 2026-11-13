// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 DigiGate
/*---------------------------------------------------------------------------------------------
 *  The sanitizer — and the only place in the renderer that writes document HTML into the DOM.
 *
 *  `engine.ts` runs markdown-it with `html: true`, because a Markdown reader that drops raw
 *  HTML is not much of a Markdown reader. Nothing filtered the result before this file
 *  existed, which meant a document could put whatever it liked into the page.
 *
 *  Both hosts need this, for different reasons. Desktop has the more capable attacker — the
 *  page can reach the `mdr:` scheme and the IPC bridge. Android needs it because option 3
 *  introduces script execution on untrusted document content, which the Markwon-based reader
 *  it replaces could not do at all. So the fix lives here, in shared code, rather than in
 *  either host.
 *
 *  This is the second layer, not the only one: the host page's CSP is the first, and it is
 *  what actually stops an inline `onerror` from running. A sanitizer that missed something
 *  and a CSP that missed something have to line up before anything executes.
 *--------------------------------------------------------------------------------------------*/
import createDOMPurify from './vendor/purify.js';
/**
 * `mdr:` is the scheme the engine rewrites document-relative images and media to, and it is
 * not in DOMPurify's default allowlist — without this every local image in every document
 * would be stripped. The rest of the list is DOMPurify's own default, kept verbatim so it can
 * be re-diffed when the dependency moves.
 */
const ALLOWED_URI_REGEXP = 
// The inner `\-` stays escaped deliberately: unescaped, `.-:` would read as a character
// range rather than three literals.
/^(?:(?:(?:f|ht)tps?|mailto|tel|callto|sms|cid|xmpp|mdr):|[^a-z]|[a-z+.-]+(?:[^a-z+.\-:]|$))/i;
/**
 * Tags with no place in a rendered document, on top of what DOMPurify already removes.
 *
 * `style` and `link` are here because CSS is an exfiltration channel: `background: url(https:
 * //…)` in a document-supplied stylesheet reports back that the document was opened, and the
 * CSP has to allow `https:` images for documents that legitimately reference remote ones.
 * Inline `style` *attributes* stay allowed — KaTeX lays out its output with them.
 */
const FORBID_TAGS = ['style', 'link', 'base', 'meta', 'form', 'object', 'embed', 'iframe', 'frame'];
/** `target` on a link is meaningless here; every external link goes through `openExternal`. */
const FORBID_ATTR = ['target', 'ping', 'formaction'];
const purify = createDOMPurify(window);
/**
 * The attributes of raw HTML that load a document resource, by element. Markdown's `![]()`
 * is rewritten by the engine's image rule; these are the same references written as HTML —
 * a README's `<img src="docs/shot.png">`, GitHub's `<picture><source srcset>` light/dark pair.
 */
const RESOURCE_ATTRIBUTES = {
    IMG: ['src', 'srcset'],
    SOURCE: ['src', 'srcset'],
    VIDEO: ['src', 'poster'],
    AUDIO: ['src'],
    TRACK: ['src'],
};
/** The rewriter for the sanitize call in progress; DOMPurify hooks are per instance, not per call. */
let rewriteResource;
purify.addHook('afterSanitizeAttributes', node => {
    const attributes = rewriteResource && RESOURCE_ATTRIBUTES[node.nodeName];
    // `data-src` marks an image the engine already rewrote — rewriting it twice would resolve
    // an `mdr:` URL against the document's folder.
    if (!attributes || node.hasAttribute('data-src')) {
        return;
    }
    for (const name of attributes) {
        const value = node.getAttribute(name);
        if (!value) {
            continue;
        }
        const rewritten = name === 'srcset' ? rewriteSrcset(value, rewriteResource) : rewriteResource(value.trim());
        if (rewritten !== value) {
            node.setAttribute(name, rewritten);
            if (name === 'src') {
                node.setAttribute('data-src', value);
            }
        }
    }
});
/**
 * `srcset` is a comma-separated list of `url [descriptor]`. A `data:` candidate carries commas
 * of its own and needs no rewriting, so a set containing one is left whole rather than split
 * wrongly.
 */
function rewriteSrcset(srcset, rewrite) {
    if (/data:/i.test(srcset)) {
        return srcset;
    }
    return srcset
        .split(',')
        .map(candidate => {
        const [url, ...descriptors] = candidate.trim().split(/\s+/);
        return url ? [rewrite(url), ...descriptors].join(' ') : candidate;
    })
        .join(', ');
}
/**
 * Sanitizes rendered document HTML, and — given `resolveResource` — points the local images
 * and media of raw HTML at the host the way the engine already did for `![]()`.
 *
 * The engine's own attributes have to survive: `data-line` for the source map, `data-href` and
 * `data-resolved-path` for link handling, `data-src` for the pre-rewrite original, and the
 * `id`s that in-page links and the outline navigate by. DOMPurify keeps `data-*` and `id` by
 * default; the tests pin that.
 *
 * What it does *not* keep is any attribute whose value contains `-->`, an mXSS defence with no
 * opt-out. That is why the mermaid source travels as element text rather than an attribute —
 * see `engine.ts`'s `#addMermaidRenderer`.
 */
export function sanitizeDocumentHtml(html, resolveResource) {
    rewriteResource = resolveResource;
    try {
        return purify.sanitize(html, {
            ALLOWED_URI_REGEXP,
            FORBID_TAGS,
            FORBID_ATTR,
            // MathML and SVG are both legitimate here — KaTeX emits MathML, mermaid emits SVG.
            USE_PROFILES: { html: true, mathMl: true, svg: true },
        });
    }
    finally {
        rewriteResource = undefined;
    }
}
/**
 * The single seam through which document content reaches the DOM. Everything the renderer
 * renders goes through here, so there is one line to audit rather than a scattering of
 * `innerHTML` assignments.
 */
export function setDocumentHtml(root, html, resolveResource) {
    root.innerHTML = sanitizeDocumentHtml(html, resolveResource);
}
//# sourceMappingURL=sanitizer.js.map