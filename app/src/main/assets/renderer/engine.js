/*---------------------------------------------------------------------------------------------
 *  SPDX-License-Identifier: GPL-3.0-only
 *  SPDX-FileCopyrightText: 2026 DigiGate
 *  SPDX-FileCopyrightText: Microsoft Corporation — MIT, see licenses/vscode.txt
 *
 *  Contains code derived from Visual Studio Code. See licenses/NOTICE.md for what this file
 *  derives from and how much it changed.
 *--------------------------------------------------------------------------------------------*/
// Ported from vscode/extensions/markdown-language-features/src/markdownEngine.ts
//
// The five VS Code couplings that were cut:
//   1. `vscode.Uri`                              -> plain absolute path strings
//   2. `MarkdownPreviewConfiguration.getForResource` -> ./config getMarkdownItConfig()
//   3. `vscode.env.uriScheme` + vscode: scheme normalization -> removed entirely
//   4. `vscode.workspace.getWorkspaceFolder`     -> RenderContext.rootPath
//   5. `MarkdownContributionProvider`            -> a plain array of markdown-it plugins
//
// Additions over the original:
//   - collects a heading outline into the render env, for the table of contents
//   - intercepts ```mermaid fences into placeholder divs the renderer hydrates
import MarkdownItFactory from './vendor/markdown-it.js';
import { getHighlighter } from './lazy.js';
import { githubSlugifier } from './slugify.js';
import { extendMarkdownIt as extendMarkdownItWithFrontMatter } from './frontMatter.js';
import { getMarkdownItConfig } from './config.js';
import { asLocalResourceUri, isAbsolutePath } from './util.js';
import { dirname, resolvePath } from './paths.js';
/**
 * A table's own elements must not carry `dir="auto"`.
 *
 * `dir="auto"` resolves from an element's *own* text and ignores any descendant that carries a
 * `dir` of its own. Set it on the table and on its rows and cells, and the table has nothing
 * left to sniff — so an RTL table fell back to `ltr` and laid its columns out backwards. The
 * `<table>` keeps the attribute; everything inside it loses it, and the table then reads the
 * direction out of its cells.
 */
const TABLE_INTERNAL_TOKENS = new Set([
    'thead_open',
    'tbody_open',
    'tfoot_open',
    'tr_open',
    'th_open',
    'td_open',
]);
/**
 * Adds begin line index to the output via the 'data-line' data attribute.
 */
const pluginSourceMap = (md) => {
    // Set the attribute on every possible token.
    md.core.ruler.push('source_map_data_attribute', (state) => {
        for (const token of state.tokens) {
            if (token.map && token.type !== 'inline') {
                token.attrSet('data-line', String(token.map[0]));
                token.attrJoin('class', 'code-line');
                if (!TABLE_INTERNAL_TOKENS.has(token.type)) {
                    token.attrJoin('dir', 'auto');
                }
            }
        }
    });
    // The 'html_block' renderer doesn't respect `attrs`. We need to insert a marker.
    const originalHtmlBlockRenderer = md.renderer.rules['html_block'];
    if (originalHtmlBlockRenderer) {
        md.renderer.rules['html_block'] = (tokens, idx, options, env, self) => (`<div ${self.renderAttrs(tokens[idx])} ></div>\n` +
            originalHtmlBlockRenderer(tokens, idx, options, env, self));
    }
};
/** The `env` a renderer rule is handed: markdown-it makes no promise that it was supplied. */
function renderEnvOf(env) {
    return env;
}
export class MarkdownItEngine {
    #md;
    slugifier = githubSlugifier;
    #plugins;
    constructor(plugins = []) {
        this.#plugins = plugins;
    }
    #getEngine(config) {
        if (!this.#md) {
            let md = MarkdownItFactory(getMarkdownOptions(() => md));
            md.linkify.set({ fuzzyLink: false });
            for (const plugin of this.#plugins) {
                try {
                    md = plugin(md) ?? md;
                }
                catch (e) {
                    console.error('Could not load markdown-it plugin', e);
                }
            }
            md = extendMarkdownItWithFrontMatter(md);
            this.#addImageRenderer(md);
            this.#addFencedRenderer(md);
            this.#addMermaidRenderer(md);
            this.#addLinkValidator(md);
            this.#addNamedHeaders(md);
            this.#addLinkRenderer(md);
            md.use(pluginSourceMap);
            this.#md = md;
        }
        const md = this.#md;
        md.set(config);
        return md;
    }
    /** Force the markdown-it instance to be rebuilt (e.g. after toggling math). */
    reloadPlugins() {
        this.#md = undefined;
    }
    render(text, context = {}) {
        const config = getMarkdownItConfig();
        const engine = this.#getEngine(config);
        const env = {
            containingImages: new Set(),
            headings: [],
            slugifier: this.slugifier.createBuilder(),
            documentPath: context.documentPath,
            rootPath: context.rootPath,
        };
        const tokens = engine.parse(text, env);
        const html = engine.renderer.render(tokens, { ...engine.options, ...config }, env);
        return {
            html,
            containingImages: env.containingImages,
            headings: env.headings,
        };
    }
    tokenize(text) {
        const engine = this.#getEngine(getMarkdownItConfig());
        return engine.parse(text, {
            containingImages: new Set(),
            headings: [],
            slugifier: this.slugifier.createBuilder(),
        });
    }
    #addImageRenderer(md) {
        const original = md.renderer.rules.image;
        md.renderer.rules.image = (tokens, idx, options, env, self) => {
            const token = tokens[idx];
            const renderEnv = renderEnvOf(env);
            // markdown-it 15 widened `attrGet` to `string | number | null`.
            const src = token.attrGet('src');
            if (src) {
                const value = String(src);
                renderEnv?.containingImages?.add(value);
                if (!token.attrGet('data-src')) {
                    token.attrSet('src', this.#toResourceUri(value, renderEnv));
                    token.attrSet('data-src', value);
                }
            }
            if (original) {
                return original(tokens, idx, options, env, self);
            }
            else {
                return self.renderToken(tokens, idx, options);
            }
        };
    }
    /** markdown-it calls this rule `fence`, not `fenced`. Preserve its escaping/highlighting. */
    #addFencedRenderer(md) {
        const original = md.renderer.rules.fence;
        md.renderer.rules.fence = (tokens, idx, options, env, self) => {
            tokens[idx].attrJoin('class', 'hljs');
            return original(tokens, idx, options, env, self);
        };
    }
    /**
     * Emits a placeholder for ```mermaid blocks. Mermaid itself renders in the
     * renderer process after the HTML is in the DOM, since it needs layout.
     *
     * The diagram source lives in the `.mermaid-fallback` element's text and nowhere else.
     * It used to be duplicated into a `data-mermaid-src` attribute, which the sanitizer then
     * removed from every flowchart in existence: DOMPurify drops any attribute whose value
     * contains `-->` as an mXSS defence, and `-->` is mermaid's arrow. Element text is not
     * subject to that rule, and the fallback had to carry the source anyway.
     */
    #addMermaidRenderer(md) {
        const original = md.renderer.rules.fence;
        md.renderer.rules.fence = (tokens, idx, options, env, self) => {
            const token = tokens[idx];
            const lang = token.info.trim().split(/\s+/)[0].toLowerCase();
            if (lang === 'mermaid') {
                const line = token.map ? token.map[0] : 0;
                return `<div class="mermaid-block code-line" data-line="${line}"><pre class="mermaid-fallback">${md.utils.escapeHtml(token.content)}</pre></div>\n`;
            }
            return original
                ? original(tokens, idx, options, env, self)
                : self.renderToken(tokens, idx, options);
        };
    }
    #addLinkValidator(md) {
        const validateLink = md.validateLink;
        md.validateLink = (link) => {
            return validateLink(link)
                || /^data:image\/.*?;/.test(link)
                || link.startsWith('mdr://');
        };
    }
    #addNamedHeaders(md) {
        const original = md.renderer.rules.heading_open;
        md.renderer.rules.heading_open = (tokens, idx, options, env, self) => {
            const title = this.#tokenToPlainText(tokens[idx + 1]);
            const renderEnv = renderEnvOf(env);
            const slug = renderEnv?.slugifier ? renderEnv.slugifier.add(title) : this.slugifier.fromHeading(title);
            tokens[idx].attrSet('id', slug.value);
            renderEnv?.headings?.push({
                level: Number(tokens[idx].tag.slice(1)) || 1,
                text: title,
                slug: slug.value,
                line: tokens[idx].map ? tokens[idx].map[0] : 0,
            });
            if (original) {
                return original(tokens, idx, options, env, self);
            }
            else {
                return self.renderToken(tokens, idx, options);
            }
        };
    }
    #tokenToPlainText(token) {
        if (token.children) {
            return token.children.map(x => this.#tokenToPlainText(x)).join('');
        }
        switch (token.type) {
            case 'text':
            case 'emoji':
            case 'code_inline':
                return token.content;
            default:
                return '';
        }
    }
    #addLinkRenderer(md) {
        const original = md.renderer.rules.link_open;
        md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
            const token = tokens[idx];
            const renderEnv = renderEnvOf(env);
            const href = token.attrGet('href');
            // A string, including empty string, may be `href`.
            if (typeof href === 'string') {
                token.attrSet('data-href', href);
                // Resolve relative markdown links to absolute paths so the renderer can
                // navigate to them without having to know the current document's folder.
                if (href && !/^[a-z][a-z0-9+.-]*:/i.test(href) && !href.startsWith('#')) {
                    const resolved = this.#resolveLocalPath(href.split('#')[0], renderEnv);
                    if (resolved) {
                        token.attrSet('data-resolved-path', resolved);
                    }
                }
            }
            if (original) {
                return original(tokens, idx, options, env, self);
            }
            else {
                return self.renderToken(tokens, idx, options);
            }
        };
    }
    /**
     * Replaces the original `#toResourceUri`, which produced webview URIs.
     * Here local references become `mdr://` URLs served by the host.
     */
    #toResourceUri(href, env) {
        try {
            if (href.startsWith('data:') || href.startsWith('mdr:')) {
                return href;
            }
            // Support file:// links
            if (/^file:/i.test(href)) {
                const p = decodeURIComponent(new URL(href).pathname);
                return asLocalResourceUri(/^\/[a-z]:/i.test(p) ? p.slice(1) : p);
            }
            // If the original link doesn't look like a url with a scheme, assume it must
            // be a link to a file next to the document (or under the opened folder).
            if (!/^[a-z][a-z0-9+.-]*:/i.test(href)) {
                const resolved = this.#resolveLocalPath(href, env);
                return resolved ? asLocalResourceUri(resolved) : href;
            }
            return href;
        }
        catch {
            return href;
        }
    }
    /** Resolve a document-relative or root-absolute reference to an absolute fs path. */
    #resolveLocalPath(href, env) {
        // Without an env there is no document and no root to resolve against, so a relative
        // reference has no answer — leave it as the document wrote it.
        if (!href || !env) {
            return undefined;
        }
        const bare = href.split(/[?#]/)[0];
        if (!bare) {
            return undefined;
        }
        if (isAbsolutePath(bare)) {
            // Root-absolute paths resolve against the opened folder, matching how VS Code
            // resolves them against the workspace root.
            if (bare.startsWith('/') && env.rootPath) {
                return resolvePath(env.rootPath, bare.replace(/^\/+/, ''));
            }
            return bare;
        }
        if (env.documentPath) {
            return resolvePath(dirname(env.documentPath), bare);
        }
        if (env.rootPath) {
            return resolvePath(env.rootPath, bare);
        }
        return undefined;
    }
}
function getMarkdownOptions(md) {
    // `highlight` is called by markdown-it's own fence renderer.
    return {
        html: true,
        highlight: (str, lang) => {
            // markdown-it's `highlight` is synchronous, so highlight.js cannot be awaited from
            // in here — `lazy.ts` loads it before the render when the document has fences. If
            // it is not loaded, this is the same path an unknown language already took.
            const hljs = getHighlighter();
            lang = normalizeHighlightLang(lang);
            if (hljs && lang && hljs.getLanguage(lang)) {
                try {
                    return hljs.highlight(str, {
                        language: lang,
                        ignoreIllegals: true,
                    }).value;
                }
                catch {
                    // Unsupported/illegal language — fall through to the escaped body below.
                }
            }
            return md().utils.escapeHtml(str);
        }
    };
}
function normalizeHighlightLang(lang) {
    switch (lang?.toLowerCase()) {
        case 'shell':
            return 'sh';
        case 'py3':
            return 'python';
        case 'tsx':
        case 'typescriptreact':
            // Workaround for highlight not supporting tsx: https://github.com/isagalaev/highlight.js/issues/1155
            return 'jsx';
        case 'json5':
        case 'jsonc':
            return 'json';
        case 'c#':
        case 'csharp':
            return 'cs';
        default:
            return lang;
    }
}
//# sourceMappingURL=engine.js.map