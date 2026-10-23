/*---------------------------------------------------------------------------------------------
 *  SPDX-License-Identifier: GPL-3.0-only
 *  SPDX-FileCopyrightText: 2026 DigiGate
 *  SPDX-FileCopyrightText: Microsoft Corporation — MIT, see licenses/vscode.txt
 *
 *  Contains code derived from Visual Studio Code. See licenses/NOTICE.md for what this file
 *  derives from and how much it changed.
 *--------------------------------------------------------------------------------------------*/
/*---------------------------------------------------------------------------------------------
 *  The document half of the renderer — the part both hosts share.
 *
 *  It owns everything inside the reading surface: rendering markdown into it, hydrating code
 *  blocks and diagrams, resolving link clicks, finding text, tracking which heading is in
 *  view, and applying the theme the stylesheets key off. It draws no chrome. Anything a host
 *  needs to draw its own — the outline, a title, a match counter — leaves through `on()`.
 *
 *  The rule that keeps this shareable: **it touches no DOM outside its root**, apart from the
 *  theme classes below, and it reaches the app only through `DocumentHost`. A `document.
 *  getElementById('some-pane')` in here is a bug, not a shortcut.
 *--------------------------------------------------------------------------------------------*/
import { getConfig } from './config.js';
import { MarkdownItEngine } from './engine.js';
import { capabilitiesOf } from './host.js';
import { ensureMath, prepareForDocument } from './lazy.js';
import { isMarkdownPath, samePath } from './paths.js';
import { getPlugins } from './plugins.js';
import { setDocumentHtml } from './sanitizer.js';
import { enhanceCodeBlocks, enhanceTables, syncCodeWrapButtons } from './presentation.js';
/** Distance from the top of the viewport at which a heading counts as "current". */
const READING_LINE = 90;
/** Headroom left above a revealed target, so it isn't flush against the top edge. */
const REVEAL_OFFSET = 56;
const NO_MATCHES = { query: '', index: -1, total: 0 };
export class DocumentView {
    root;
    host;
    /**
     * The element carrying `vscode-dark` / `vscode-light` and `wordWrap`. This is the one
     * piece of DOM outside the root that the document owns, and it is not a choice:
     * `markdown.css` selects `body.wordWrap pre`, and `highlight.css` selects
     * `.vscode-light .hljs-*`. The stylesheets are copied verbatim from VS Code, so the
     * classes go where they expect them.
     */
    themeRoot;
    engine = new MarkdownItEngine(getPlugins());
    doc;
    root_;
    headings = [];
    activeSlug;
    /** Scroll offsets are restored per document, so revisiting a file lands where you left. */
    scrollMemory = new Map();
    /** Set when a link carried a fragment, consumed once that document has rendered. */
    pendingFragment;
    findMatches = [];
    findIndex = 0;
    findQuery = '';
    findActive = false;
    mermaidPromise;
    mermaidRenderSeq = 0;
    /** Guards the await in `setDocumentAsync` against a second document overtaking the first. */
    documentSeq = 0;
    scrollRaf = 0;
    listeners = new Map();
    systemDark = window.matchMedia('(prefers-color-scheme: dark)');
    constructor(options) {
        this.root = options.root;
        this.host = options.host;
        this.themeRoot = options.root.ownerDocument.body;
        this.root.addEventListener('click', event => this.onLinkClick(event));
        window.addEventListener('scroll', () => this.onScroll(), { passive: true });
        this.systemDark.addEventListener('change', () => {
            if (getConfig().theme === 'system') {
                this.applyTheme();
            }
        });
    }
    // ------------------------------------------------------------------------- events
    on(event, listener) {
        let set = this.listeners.get(event);
        if (!set) {
            set = new Set();
            this.listeners.set(event, set);
        }
        set.add(listener);
        return () => set.delete(listener);
    }
    emit(event, payload) {
        for (const listener of this.listeners.get(event) ?? []) {
            listener(payload);
        }
    }
    // -------------------------------------------------------------------------- state
    get currentDocument() {
        return this.doc;
    }
    get currentHeadings() {
        return this.headings;
    }
    /** The folder a document was opened from, used to resolve root-relative links. */
    setRootPath(rootPath) {
        this.root_ = rootPath;
    }
    // ---------------------------------------------------------------- document flow
    setDocument(doc, options) {
        void this.setDocumentAsync(doc, options);
    }
    /**
     * The document's modules are loaded before it is painted, so it appears once and complete
     * rather than reflowing as KaTeX or highlight.js arrive. See `lazy.ts`.
     *
     * `setDocument` is kept synchronous for callers, which only ever fire and forget. The
     * sequence guard is what makes that safe: two documents opened in quick succession would
     * otherwise race through the await below and paint in whichever order their modules
     * happened to resolve.
     */
    async setDocumentAsync(doc, options) {
        const seq = ++this.documentSeq;
        const prepared = await prepareForDocument(doc.text);
        if (seq !== this.documentSeq) {
            return;
        }
        if (prepared.pluginsChanged) {
            this.engine = new MarkdownItEngine(getPlugins());
        }
        const previousScroll = window.scrollY;
        const isSameDoc = this.doc !== undefined && samePath(this.doc.path, doc.path);
        if (this.doc && !isSameDoc) {
            this.scrollMemory.set(this.doc.path, previousScroll);
        }
        this.doc = doc;
        this.emit('document', doc);
        this.render();
        if (this.pendingFragment) {
            const fragment = this.pendingFragment;
            this.pendingFragment = undefined;
            requestAnimationFrame(() => this.revealSlug(fragment));
            return;
        }
        if (options.preserveScroll && isSameDoc) {
            // Live reload: hold position so saving doesn't yank the reader to the top.
            requestAnimationFrame(() => window.scrollTo({ top: previousScroll }));
        }
        else {
            const remembered = this.scrollMemory.get(doc.path) ?? 0;
            requestAnimationFrame(() => window.scrollTo({ top: remembered }));
        }
    }
    /** Re-read the showing document from the host and re-render it in place. */
    async reload() {
        if (!this.doc) {
            return;
        }
        const reloaded = await this.host.readFile(this.doc.path);
        if (reloaded) {
            this.setDocument(reloaded, { preserveScroll: true });
        }
    }
    /**
     * Rebuild the engine after a setting that changes the plugin set or parser options.
     *
     * Turning math on by hand has to load KaTeX whether or not the open document looked like
     * it needed it — the setting is not about this document.
     */
    rebuildEngine() {
        void (async () => {
            if (getConfig().math) {
                await ensureMath();
            }
            this.engine = new MarkdownItEngine(getPlugins());
            this.render();
        })();
    }
    // ---------------------------------------------------------------------- rendering
    render() {
        if (!this.doc) {
            this.root.innerHTML = '';
            this.setHeadings([]);
            return;
        }
        const output = this.engine.render(this.doc.text, {
            documentPath: this.doc.path,
            rootPath: this.root_,
        });
        setDocumentHtml(this.root, output.html);
        this.addHeadingAnchors();
        enhanceCodeBlocks(this.root, {
            copyText: typeof this.host.copyText === 'function' ? text => this.host.copyText(text) : undefined,
        });
        enhanceTables(this.root);
        void this.renderMermaidBlocks();
        this.setHeadings(output.headings);
        this.refreshFind();
    }
    setHeadings(headings) {
        this.headings = headings;
        this.emit('headings', headings);
        this.updateActiveHeading();
    }
    addHeadingAnchors() {
        const selector = 'h1[id], h2[id], h3[id], h4[id], h5[id], h6[id]';
        for (const heading of this.root.querySelectorAll(selector)) {
            if (heading.querySelector('.heading-anchor')) {
                continue;
            }
            const anchor = document.createElement('a');
            anchor.className = 'heading-anchor';
            anchor.href = `#${heading.id}`;
            anchor.textContent = '#';
            anchor.title = 'Link to this section';
            anchor.setAttribute('aria-hidden', 'true');
            heading.insertBefore(anchor, heading.firstChild);
        }
    }
    // ------------------------------------------------------------------------ mermaid
    /** Mermaid is ~2.5MB, so it's only pulled in once a document actually contains a diagram. */
    async loadMermaid() {
        this.mermaidPromise ??= import('./vendor/mermaid.js').then(mod => mod.default);
        return this.mermaidPromise;
    }
    async renderMermaidBlocks() {
        const blocks = Array.from(this.root.querySelectorAll('.mermaid-block'));
        if (!blocks.length || !getConfig().mermaid) {
            return;
        }
        let mermaid;
        try {
            mermaid = await this.loadMermaid();
        }
        catch (e) {
            console.error('Failed to load mermaid', e);
            return;
        }
        mermaid.initialize({
            startOnLoad: false,
            theme: this.themeRoot.classList.contains('vscode-dark') ? 'dark' : 'default',
            securityLevel: 'strict',
            fontFamily: getComputedStyle(this.themeRoot).fontFamily,
        });
        // Guards against a second render (theme switch, live reload) interleaving with this one.
        const seq = ++this.mermaidRenderSeq;
        for (const [index, block] of blocks.entries()) {
            // The fallback `<pre>` is the diagram's source of truth; see `#addMermaidRenderer`.
            // It survives hydration below, so a re-render (theme switch) still finds it.
            const source = block.querySelector('.mermaid-fallback')?.textContent;
            if (!source) {
                continue;
            }
            try {
                const { svg } = await mermaid.render(`mr-mermaid-${seq}-${index}`, source);
                if (seq !== this.mermaidRenderSeq || !block.isConnected) {
                    return;
                }
                const fallback = block.querySelector('.mermaid-fallback');
                // Not routed through `setDocumentHtml`: mermaid's `securityLevel: 'strict'`
                // above already runs its own DOMPurify pass over this SVG, and our document
                // profile drops `foreignObject` — which is how mermaid draws HTML labels.
                block.innerHTML = svg;
                if (fallback) {
                    block.appendChild(fallback);
                }
                block.classList.add('rendered');
                block.classList.remove('failed');
            }
            catch (e) {
                if (seq !== this.mermaidRenderSeq) {
                    return;
                }
                // Leave the source visible and say why it didn't draw.
                block.classList.add('failed');
                block.classList.remove('rendered');
                if (!block.querySelector('.mermaid-error')) {
                    const message = document.createElement('div');
                    message.className = 'mermaid-error';
                    message.textContent = `Mermaid diagram failed to render: ${e.message ?? e}`;
                    block.insertBefore(message, block.firstChild);
                }
                // Mermaid appends its own error graphic to the body on failure.
                document.querySelectorAll('body > svg[id^="mr-mermaid-"]').forEach(n => n.remove());
            }
        }
    }
    // ------------------------------------------------------------------------ outline
    onScroll() {
        if (this.scrollRaf) {
            return;
        }
        this.scrollRaf = requestAnimationFrame(() => {
            this.scrollRaf = 0;
            this.updateActiveHeading();
        });
    }
    updateActiveHeading() {
        // The active heading is the last one whose top edge is above the reading line.
        let active;
        for (const heading of this.headings) {
            const node = document.getElementById(heading.slug);
            if (!node) {
                continue;
            }
            if (node.getBoundingClientRect().top <= READING_LINE) {
                active = heading.slug;
            }
            else {
                break;
            }
        }
        active ??= this.headings[0]?.slug;
        if (active !== this.activeSlug) {
            this.activeSlug = active;
            this.emit('activeHeading', active);
        }
    }
    revealSlug(slug) {
        const target = document.getElementById(slug)
            // Fall back to a fragment-style match, as VS Code's link handling does.
            ?? document.getElementById(decodeURIComponent(slug));
        if (!target) {
            return;
        }
        const top = target.getBoundingClientRect().top + window.scrollY - REVEAL_OFFSET;
        window.scrollTo({ top: Math.max(0, top), behavior: 'smooth' });
        target.classList.remove('mr-target-flash');
        // Force a reflow so the animation replays when the same target is picked twice.
        void target.offsetWidth;
        target.classList.add('mr-target-flash');
    }
    // ------------------------------------------------------------------------ theming
    applyTheme() {
        const mode = getConfig().theme;
        const dark = mode === 'dark' || (mode === 'system' && this.systemDark.matches);
        this.themeRoot.classList.toggle('vscode-dark', dark);
        this.themeRoot.classList.toggle('vscode-light', !dark);
        const background = rgbToHex(getComputedStyle(this.themeRoot).backgroundColor);
        this.emit('theme', { dark, background });
        // Mermaid bakes theme colors into the SVG, so diagrams must be redrawn.
        if (this.root.querySelector('.mermaid-block')) {
            void this.renderMermaidBlocks();
        }
    }
    setWordWrap(wrap) {
        this.themeRoot.classList.toggle('wordWrap', wrap);
        for (const block of this.root.querySelectorAll('.code-block')) delete block.dataset.wrap;
        syncCodeWrapButtons(this.root);
    }
    // --------------------------------------------------------------------------- find
    /** Called when the host's find affordance opens, so a stale query repaints. */
    openFind() {
        this.findActive = true;
        this.runFind(this.findQuery);
    }
    closeFind() {
        this.findActive = false;
        this.clearFindHighlights();
        this.findMatches = [];
        this.findQuery = '';
        this.emit('find', NO_MATCHES);
    }
    refreshFind() {
        if (this.findActive) {
            this.runFind(this.findQuery);
        }
    }
    /**
     * Uses the CSS Custom Highlight API, so matches are painted without mutating the DOM —
     * the same approach VS Code's preview uses for inner diff ranges.
     */
    runFind(query) {
        this.findActive = true;
        this.findQuery = query;
        this.clearFindHighlights();
        this.findMatches = [];
        this.findIndex = 0;
        if (query.length < 1) {
            this.emitFindState();
            return;
        }
        const needle = query.toLowerCase();
        const walker = document.createTreeWalker(this.root, NodeFilter.SHOW_TEXT, {
            acceptNode: node => {
                const parent = node.parentElement;
                if (!parent || parent.closest('.heading-anchor, .code-block-toolbar, .code-block-status, script, style')) {
                    return NodeFilter.FILTER_REJECT;
                }
                return node.nodeValue && node.nodeValue.trim().length > 0
                    ? NodeFilter.FILTER_ACCEPT
                    : NodeFilter.FILTER_REJECT;
            },
        });
        for (let node = walker.nextNode(); node; node = walker.nextNode()) {
            const text = (node.nodeValue ?? '').toLowerCase();
            let from = text.indexOf(needle);
            while (from !== -1) {
                const range = document.createRange();
                range.setStart(node, from);
                range.setEnd(node, from + needle.length);
                this.findMatches.push(range);
                from = text.indexOf(needle, from + needle.length);
            }
        }
        this.paintFindHighlights();
        this.emitFindState();
        if (this.findMatches.length) {
            this.scrollToFindMatch(0);
        }
    }
    stepFind(delta) {
        if (!this.findMatches.length) {
            return;
        }
        this.findIndex = (this.findIndex + delta + this.findMatches.length) % this.findMatches.length;
        this.paintFindHighlights();
        this.emitFindState();
        this.scrollToFindMatch(this.findIndex);
    }
    emitFindState() {
        this.emit('find', {
            query: this.findQuery,
            index: this.findMatches.length ? this.findIndex : -1,
            total: this.findMatches.length,
        });
    }
    paintFindHighlights() {
        if (!('highlights' in CSS)) {
            return;
        }
        const all = this.findMatches.filter((_, i) => i !== this.findIndex);
        const current = this.findMatches[this.findIndex];
        CSS.highlights.set('mr-find-match', new Highlight(...all));
        CSS.highlights.set('mr-find-current', new Highlight(...(current ? [current] : [])));
    }
    clearFindHighlights() {
        if ('highlights' in CSS) {
            CSS.highlights.delete('mr-find-match');
            CSS.highlights.delete('mr-find-current');
        }
    }
    scrollToFindMatch(index) {
        const range = this.findMatches[index];
        if (!range) {
            return;
        }
        const rect = range.getBoundingClientRect();
        const top = rect.top + window.scrollY - window.innerHeight / 2;
        window.scrollTo({ top: Math.max(0, top), behavior: 'smooth' });
    }
    // ------------------------------------------------------------------- link handling
    onLinkClick(event) {
        const anchor = event.target.closest('a');
        if (!anchor) {
            return;
        }
        event.preventDefault();
        const rawHref = anchor.getAttribute('data-href') ?? anchor.getAttribute('href') ?? '';
        if (!rawHref) {
            return;
        }
        // In-page anchor
        if (rawHref.startsWith('#')) {
            this.revealSlug(rawHref.slice(1));
            return;
        }
        // External
        if (/^(https?|mailto):/i.test(rawHref)) {
            void this.host.openExternal(rawHref);
            return;
        }
        // Local: the engine already resolved this to an absolute path at render time.
        const resolved = anchor.getAttribute('data-resolved-path');
        if (!resolved) {
            return;
        }
        if (isMarkdownPath(resolved)) {
            this.pendingFragment = rawHref.includes('#') ? rawHref.slice(rawHref.indexOf('#') + 1) : undefined;
            void this.host.openPath(resolved);
        }
        else if (capabilitiesOf(this.host).revealInFolder) {
            // A link to a non-markdown file goes to the file manager, where there is one.
            // Android has none, and the click is simply inert there.
            void this.host.showInFolder?.(resolved);
        }
    }
}
function rgbToHex(color) {
    const match = /^rgba?\((\d+),\s*(\d+),\s*(\d+)/.exec(color);
    if (!match) {
        return undefined;
    }
    const [, r, g, b] = match;
    return '#' + [r, g, b].map(v => Number(v).toString(16).padStart(2, '0')).join('');
}
//# sourceMappingURL=document.js.map