// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 DigiGate
/*---------------------------------------------------------------------------------------------
 *  What a document needs before it can be rendered, and loading only that.
 *
 *  KaTeX and highlight.js are the two heaviest things in the module graph, and both used to be
 *  evaluated at startup whether or not the document had any use for them — KaTeX's stylesheet
 *  was linked in the page for good measure. Most documents contain no math; many contain no
 *  code fences. On Android the spike measured a fixed ~1.15s of start-up cost that a document
 *  120 times larger barely moved, which is what identified this as the lever worth pulling:
 *  it is the only one that shrinks the fixed cost rather than hiding it, and desktop gets it
 *  for free. Mermaid already proved the dynamic import survives bundling.
 *
 *  The loads happen *before* the render rather than after it, so a document is painted once,
 *  complete. The alternative — paint, then upgrade code blocks and math in place — is a
 *  visible reflow on every document that has either.
 *
 *  **Detection errs towards loading.** A false positive costs a module nobody needed and
 *  renders identically, because the markdown-it plugins decide what is actually math or code.
 *  A false negative silently renders math as literal text. So the tests below pin the
 *  generosity, not the tightness.
 *--------------------------------------------------------------------------------------------*/
import { getConfig } from './config.js';
import { isMathPluginLoaded, loadMathPlugin } from './plugins.js';
let highlighter;
/** The highlighter, if a document has needed it yet. The engine falls back to escaped text. */
export function getHighlighter() {
    return highlighter;
}
/**
 * Any `$` at all counts as math.
 *
 * A tighter rule would have to reimplement KaTeX's own delimiter matching to stay correct, and
 * getting it wrong means a document quietly renders `$x^2$` as text. Since KaTeX being loaded
 * is exactly the state every document was rendered in before, a false positive cannot change
 * output — so a document with a price in it loads KaTeX, and one with no `$` anywhere does not.
 */
function needsMath(text) {
    return text.includes('$') || /^\s*(```|~~~)\s*math/m.test(text);
}
/**
 * A fence, or front matter — which the `codeBlock` render style highlights as YAML.
 * Indented code blocks never reach the `highlight` option, so they are not a trigger.
 */
function needsCode(text) {
    return /(^|\n)\s*(```|~~~)/.test(text) || /^---\r?\n/.test(text);
}
export function detectDocumentNeeds(text) {
    return { code: needsCode(text), math: needsMath(text) };
}
/**
 * Loads whatever this document needs and reports whether the markdown-it plugin set changed as
 * a result — if it did, the caller has to rebuild its engine before rendering, since plugins
 * are fixed at construction.
 */
export async function prepareForDocument(text) {
    const needs = detectDocumentNeeds(text);
    const wasMathLoaded = isMathPluginLoaded();
    await Promise.all([
        needs.code ? ensureHighlighter() : undefined,
        needs.math && getConfig().math ? ensureMath() : undefined,
    ]);
    return { pluginsChanged: isMathPluginLoaded() !== wasMathLoaded };
}
/** Loads the math plugin regardless of the document, for turning the setting on by hand. */
export async function ensureMath() {
    await Promise.all([loadMathPlugin(), loadKatexStylesheet()]);
}
/** Loads highlight.js regardless of the document. Exported for tests and for a manual reload. */
export async function ensureHighlighter() {
    highlighter ??= (await import('./vendor/highlight.js')).default;
}
const KATEX_STYLESHEET = 'css/katex.css';
let katexStylesheet;
/**
 * Injects KaTeX's stylesheet the first time a document needs it. Resolved before rendering so
 * math is never painted unstyled, and the promise is cached so a failed load is not retried on
 * every document — math renders unstyled rather than blocking the document.
 */
function loadKatexStylesheet() {
    katexStylesheet ??= new Promise(resolve => {
        if (typeof document === 'undefined'
            || document.querySelector(`link[href="${KATEX_STYLESHEET}"]`)) {
            resolve();
            return;
        }
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = KATEX_STYLESHEET;
        link.addEventListener('load', () => resolve());
        link.addEventListener('error', () => {
            console.error('Failed to load the KaTeX stylesheet; math will render unstyled');
            resolve();
        });
        document.head.appendChild(link);
    });
    return katexStylesheet;
}
//# sourceMappingURL=lazy.js.map