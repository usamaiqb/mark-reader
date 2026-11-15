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
import { placeSubgraphTitlesIn, subgraphTitleMargin } from './mermaid/subgraphTitles.js';
import { VsCodeMermaidThemeTracker, vsCodeMermaidTheme } from './mermaid/vsCodeTheme.js';
import { isMarkdownPath, samePath } from './paths.js';
import { getPlugins } from './plugins.js';
import { setDocumentHtml } from './sanitizer.js';
import { scrollAreaOf } from './scroll.js';
/**
 * Distance from the top of the scroll area at which a heading counts as "current". Both of these
 * are measured from the visible top of the document: they were 90 and 56 when the window scrolled
 * and the desktop's 38px toolbar overlapped the page, and are restated net of it. A host that
 * overlays the top of the scroll area says so with `setCoveredTop`, and they are measured from
 * below that instead.
 */
const READING_LINE = 52;
/** Headroom left above a revealed target, so it isn't flush against the top edge. */
const REVEAL_OFFSET = 18;
const NO_MATCHES = { query: '', index: -1, total: 0 };
/**
 * VS Code's diagram theme for both light and dark, its default. `resolveMermaidTheme` reads
 * only these two fields; the rest of the type configures diagram controls this app lacks.
 */
const MERMAID_THEME_CONFIG = {
    darkModeTheme: vsCodeMermaidTheme,
    lightModeTheme: vsCodeMermaidTheme,
};
export class DocumentView {
    root;
    host;
    scroll;
    /**
     * The element carrying `vscode-dark` / `vscode-light` and `wordWrap`. This is the one
     * piece of DOM outside the root that the document owns, and it is not a choice:
     * `markdown.css` selects `body.wordWrap pre`, and `highlight.css` selects
     * `.vscode-light .hljs-*`. The stylesheets are copied verbatim from VS Code, so the
     * classes go where they expect them. The theme classes are mirrored onto `<html>`; see
     * `applyTheme()`.
     */
    themeRoot;
    engine = new MarkdownItEngine(getPlugins());
    doc;
    root_;
    headings = [];
    activeSlug;
    /** Scroll offsets are restored per document, so revisiting a file lands where you left. */
    scrollMemory = new Map();
    /**
     * Set when a link carried a fragment, consumed once *that* document renders. Tied to the
     * link's target, because the next document to render is not necessarily it: a host with
     * several documents may render another first (a reload, a switch), or never open the target.
     */
    pendingFragment;
    findMatches = [];
    /**
     * The two registered highlights, kept so a step moves one match between them instead of
     * rebuilding either: every match but the current one, and the current one.
     */
    findPaint;
    findIndex = 0;
    /**
     * Whether the reader has moved to a match since the query last changed. Until they have, the
     * current match is only the nearest one marked, and the first step goes *to* it rather than
     * past it.
     */
    findStepped = false;
    findQuery = '';
    findActive = false;
    mermaidPromise;
    mermaidRenderSeq = 0;
    mermaidTheme = new VsCodeMermaidThemeTracker();
    /** Guards the await in `setDocumentAsync` against a second document overtaking the first. */
    documentSeq = 0;
    scrollRaf = 0;
    /**
     * A scroll position set for the next frame and not yet applied. A document is positioned a
     * frame after it renders, once it has laid out; until then the scroll area still reads where
     * the last document left it, and anything asking where the reader is must get this instead.
     * See `readingPosition()`.
     */
    pendingScroll;
    listeners = new Map();
    systemDark = window.matchMedia('(prefers-color-scheme: dark)');
    constructor(options) {
        this.root = options.root;
        this.host = options.host;
        this.scroll = scrollAreaOf(options.scroller);
        this.themeRoot = options.root.ownerDocument.body;
        this.root.addEventListener('click', event => this.onLinkClick(event));
        // A middle click arrives as `auxclick`, and left alone the webview would open the link
        // in a new window of its own.
        this.root.addEventListener('auxclick', event => {
            if (event.button === 1) {
                this.onLinkClick(event);
            }
        });
        this.scroll.onScroll(() => this.onScroll());
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
    /** Where the reader is in the showing document; see `SetDocumentOptions.viewState`. */
    getViewState() {
        return { scrollTop: this.readingPosition() };
    }
    /**
     * How many CSS px at the top of the scroll area the host draws over, so the reading line,
     * reveal headroom and find centring are measured from below it. A host whose chrome sits
     * above the scroll area, as desktop's toolbar does, never calls this. One that overlays it,
     * as Android's bars do, calls it whenever that chrome shows or hides; nothing scrolls when
     * it does, so the current heading is re-evaluated here.
     */
    setCoveredTop(px) {
        this.scroll.coveredTop = px;
        this.updateActiveHeading();
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
        const previousScroll = this.readingPosition();
        const isSameDoc = this.doc !== undefined && samePath(this.doc.path, doc.path);
        if (this.doc && !isSameDoc) {
            this.scrollMemory.set(this.doc.path, previousScroll);
        }
        this.doc = doc;
        this.emit('document', doc);
        this.render();
        if (this.pendingFragment && samePath(this.pendingFragment.path, doc.path)) {
            const { slug } = this.pendingFragment;
            this.pendingFragment = undefined;
            // The fragment decides where this document lands; an older restore must not undo it.
            this.pendingScroll = undefined;
            requestAnimationFrame(() => this.revealSlug(slug));
            return;
        }
        let top;
        if (options.viewState) {
            top = options.viewState.scrollTop;
        }
        else if (options.preserveScroll && isSameDoc) {
            // Live reload: hold position so saving doesn't yank the reader to the top.
            top = previousScroll;
        }
        else {
            top = this.scrollMemory.get(doc.path) ?? 0;
        }
        this.scrollNextFrame(top);
    }
    /**
     * Show nothing — the host closed the last document it had. Anything still loading for a
     * previous `setDocument` is abandoned rather than painted afterwards.
     */
    clearDocument() {
        ++this.documentSeq;
        if (this.doc) {
            this.scrollMemory.set(this.doc.path, this.readingPosition());
        }
        this.pendingScroll = undefined;
        this.doc = undefined;
        this.pendingFragment = undefined;
        this.emit('document', undefined);
        this.render();
        // `render()` skips this with nothing to render; an open find bar would keep its old count.
        this.refreshFind();
    }
    /**
     * Where the reader is: the position about to be applied, if there is one, else the scroll
     * area's own. Without this, a live reload landing between a restore and its frame read the
     * old position (often 0) as the one to keep, and undid the restore.
     */
    readingPosition() {
        return this.pendingScroll?.top ?? this.scroll.scrollTop;
    }
    /** Only the latest request applies: a newer one supersedes any still waiting. */
    scrollNextFrame(top) {
        const pending = { top };
        this.pendingScroll = pending;
        requestAnimationFrame(() => {
            if (this.pendingScroll !== pending) {
                return;
            }
            this.pendingScroll = undefined;
            this.scroll.scrollTo(top);
        });
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
        setDocumentHtml(this.root, output.html, output.resolveResource);
        this.addCodeBlockCopyButtons();
        this.wrapWideTables();
        void this.renderMermaidBlocks();
        this.setHeadings(output.headings);
        this.refreshFind();
    }
    setHeadings(headings) {
        this.headings = headings;
        this.emit('headings', headings);
        this.updateActiveHeading();
    }
    /**
     * Ported in spirit from preview-src/index.ts, which added the same affordance for
     * copying fenced code. The button markup relies on `.code-block-copy-button` from
     * the copied markdown.css.
     */
    /**
     * Upstream's `addCodeBlockCopyButtons`, scoped to the root. `pre > code` also leaves out the
     * mermaid fallback, which is a bare `<pre>`.
     */
    addCodeBlockCopyButtons() {
        for (const code of this.root.querySelectorAll('pre > code')) {
            const pre = code.parentElement;
            // Inject copy button if not already present
            if (!pre.querySelector('.code-block-copy-button')) {
                const button = document.createElement('button');
                button.className = 'code-block-copy-button';
                button.setAttribute('aria-label', 'Copy code block');
                button.appendChild(createCopyIcon());
                button.addEventListener('click', async (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    const text = code.textContent ?? '';
                    try {
                        await navigator.clipboard.writeText(text);
                        button.replaceChildren(createCheckIcon());
                        button.classList.add('copied');
                        setTimeout(() => {
                            button.replaceChildren(createCopyIcon());
                            button.classList.remove('copied');
                        }, 2000);
                    }
                    catch {
                        const selection = window.getSelection();
                        if (selection) {
                            selection.removeAllRanges();
                            const range = document.createRange();
                            range.selectNodeContents(code);
                            selection.addRange(range);
                            document.execCommand('copy');
                            selection.removeAllRanges();
                        }
                    }
                });
                pre.appendChild(button);
            }
        }
    }
    /** Wrap tables so an over-wide table scrolls itself instead of the page. */
    wrapWideTables() {
        for (const table of this.root.querySelectorAll('table')) {
            if (table.parentElement?.classList.contains('table-wrapper') || table.classList.contains('frontmatter')) {
                continue;
            }
            const wrapper = document.createElement('div');
            wrapper.className = 'table-wrapper';
            table.parentNode?.insertBefore(wrapper, table);
            wrapper.appendChild(table);
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
        // As upstream's preview does: re-resolve the colors before every render, since this
        // runs again on each theme switch.
        this.mermaidTheme.refresh();
        mermaid.initialize({
            startOnLoad: false,
            // Upstream's `buildMermaidConfig`: room above subgraph nodes for the title band that
            // `placeSubgraphTitlesIn` below moves titles within.
            flowchart: { subGraphTitleMargin: { ...subgraphTitleMargin } },
            ...this.mermaidTheme.resolveMermaidTheme(MERMAID_THEME_CONFIG),
            securityLevel: 'strict',
            // Without this, a diagram that fails to parse leaves mermaid's "Syntax error in text"
            // graphic behind in a `div#d<id>` on the body: it throws before removing its own
            // scratch element. Suppressed, it removes it and just throws, and the catch below
            // shows the source and the reason instead.
            suppressErrorRendering: true,
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
                // As upstream's preview does, on the diagram just written: moves subgraph titles
                // clear of the edges that enter them.
                placeSubgraphTitlesIn(block);
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
            const node = this.elementById(heading.slug);
            if (!node) {
                continue;
            }
            if (this.scroll.fromTop(node.getBoundingClientRect().top) <= READING_LINE) {
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
        const target = this.elementById(slug)
            // Fall back to a fragment-style match, as VS Code's link handling does.
            ?? this.elementById(decodeURIComponent(slug));
        if (!target) {
            return;
        }
        const top = this.scroll.scrollTop + this.scroll.fromTop(target.getBoundingClientRect().top) - REVEAL_OFFSET;
        this.scroll.scrollTo(Math.max(0, top), 'smooth');
        target.classList.remove('mr-target-flash');
        // Force a reflow so the animation replays when the same target is picked twice.
        void target.offsetWidth;
        target.classList.add('mr-target-flash');
    }
    /**
     * `document.getElementById`, scoped to the root: an id elsewhere on the page — the shell's,
     * or another view's — is not this document's. Escaped, because slugs are routinely not
     * valid selectors as written (`1-introduction`).
     */
    elementById(id) {
        return id ? this.root.querySelector(`#${CSS.escape(id)}`) : null;
    }
    // ------------------------------------------------------------------------ theming
    applyTheme() {
        const mode = getConfig().theme;
        const dark = mode === 'dark' || (mode === 'system' && this.systemDark.matches);
        // On `<html>` too: theme.css defines the `--vscode-*` properties there, where VS Code
        // puts them and mermaid/vsCodeTheme.ts reads them, without needing `:has()`.
        for (const element of [this.themeRoot, this.themeRoot.ownerDocument.documentElement]) {
            element.classList.toggle('vscode-dark', dark);
            element.classList.toggle('vscode-light', !dark);
        }
        const background = rgbToHex(getComputedStyle(this.themeRoot).backgroundColor);
        this.emit('theme', { dark, background });
        // Mermaid bakes theme colors into the SVG, so diagrams must be redrawn.
        if (this.root.querySelector('.mermaid-block')) {
            void this.renderMermaidBlocks();
        }
    }
    setWordWrap(wrap) {
        this.themeRoot.classList.toggle('wordWrap', wrap);
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
     *
     * **It does not scroll.** It paints every match, counts them and marks the nearest one, the
     * first at or below the top of what is visible, as current — and leaves the reader where
     * they are. Moving is `stepFind`'s, which the host calls for Enter and for next / previous.
     * This is VS Code's editor find with `editor.find.cursorMoveOnType` off, the reading position
     * standing in for the cursor: a query typed, or re-run by a live reload, never costs the
     * reader their place.
     */
    runFind(query) {
        this.findActive = true;
        this.findQuery = query;
        this.clearFindHighlights();
        this.findMatches = [];
        this.findIndex = 0;
        this.findStepped = false;
        if (query.length < 1) {
            this.emitFindState();
            return;
        }
        const needle = query.toLowerCase();
        const walker = document.createTreeWalker(this.root, NodeFilter.SHOW_TEXT, {
            acceptNode: node => {
                const parent = node.parentElement;
                if (!parent || parent.closest('.code-block-copy-button, script, style')) {
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
        this.findIndex = this.nearestFindMatch();
        this.paintFindHighlights();
        this.emitFindState();
    }
    /**
     * Moves to a match, scrolling only if it is not already in view.
     *
     * The first step after a query goes from where the reader is *now* — they may have scrolled
     * since typing — and lands on the nearest match going forward, or the one before it going
     * back. Later steps go `delta` matches on from the current one, wrapping at either end.
     */
    stepFind(delta) {
        const count = this.findMatches.length;
        if (!count || !delta) {
            return;
        }
        const previous = this.findIndex;
        const from = this.findStepped ? this.findIndex + delta : this.nearestFindMatch() + (delta > 0 ? delta - 1 : delta);
        this.findIndex = ((from % count) + count) % count;
        this.findStepped = true;
        this.moveCurrentHighlight(previous, this.findIndex);
        this.emitFindState();
        this.revealFindMatch(this.findIndex);
    }
    /**
     * The first match whose top is at or below the visible top, or the first match when the
     * reader is past them all.
     *
     * Matches are in document order, which is top-to-bottom order for everything but cells side
     * by side in a row, so this is a binary search: a handful of measurements rather than one per
     * match, which matters for a one-letter query.
     */
    nearestFindMatch() {
        let low = 0;
        let high = this.findMatches.length;
        while (low < high) {
            const mid = (low + high) >>> 1;
            if (this.scroll.fromTop(this.findMatches[mid].getBoundingClientRect().top) < 0) {
                low = mid + 1;
            }
            else {
                high = mid;
            }
        }
        return low < this.findMatches.length ? low : 0;
    }
    emitFindState() {
        this.emit('find', {
            query: this.findQuery,
            index: this.findMatches.length ? this.findIndex : -1,
            total: this.findMatches.length,
        });
    }
    /**
     * Added one at a time, never spread into `new Highlight(...)`: a spread passes every match as
     * an argument, and a one-letter query in a large document has enough of them to overflow the
     * stack, which threw out of `runFind` before it reported a count or painted anything.
     */
    paintFindHighlights() {
        if (!('highlights' in CSS)) {
            return;
        }
        const others = new Highlight();
        const current = new Highlight();
        for (const [index, match] of this.findMatches.entries()) {
            (index === this.findIndex ? current : others).add(match);
        }
        CSS.highlights.set('mr-find-match', others);
        CSS.highlights.set('mr-find-current', current);
        this.findPaint = { others, current };
    }
    /** A step changes two matches' paint, whatever the total. */
    moveCurrentHighlight(from, to) {
        const paint = this.findPaint;
        const previous = this.findMatches[from];
        const next = this.findMatches[to];
        if (!paint || !previous || !next) {
            return;
        }
        paint.current.delete(previous);
        paint.others.add(previous);
        paint.others.delete(next);
        paint.current.add(next);
    }
    clearFindHighlights() {
        this.findPaint = undefined;
        if ('highlights' in CSS) {
            CSS.highlights.delete('mr-find-match');
            CSS.highlights.delete('mr-find-current');
        }
    }
    /** Centres a match that is out of view, and leaves one in view where it is. */
    revealFindMatch(index) {
        const range = this.findMatches[index];
        if (!range) {
            return;
        }
        const rect = range.getBoundingClientRect();
        const top = this.scroll.fromTop(rect.top);
        if (top >= 0 && top + rect.height <= this.scroll.height) {
            return;
        }
        this.scroll.scrollTo(Math.max(0, this.scroll.scrollTop + top - this.scroll.height / 2), 'smooth');
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
            // Ctrl/Cmd or the middle button asks for it behind the current document, as a browser
            // opens a background tab. The fragment is for when the document shows, so a
            // background open leaves any pending one alone rather than setting its own.
            const background = event.ctrlKey || event.metaKey || event.button === 1;
            if (!background) {
                this.pendingFragment = rawHref.includes('#')
                    ? { path: resolved, slug: rawHref.slice(rawHref.indexOf('#') + 1) }
                    : undefined;
            }
            void this.host.openPath(resolved, { background });
        }
        else if (capabilitiesOf(this.host).revealInFolder) {
            // A link to a non-markdown file goes to the file manager, where there is one.
            // Android has none, and the click is simply inert there.
            void this.host.showInFolder?.(resolved);
        }
    }
}
function createCopyIcon() {
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('aria-hidden', 'true');
    svg.setAttribute('focusable', 'false');
    svg.setAttribute('width', '16');
    svg.setAttribute('height', '16');
    svg.setAttribute('viewBox', '0 0 16 16');
    svg.setAttribute('fill', 'none');
    const path1 = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path1.setAttribute('d', 'M4 4H2V14H11V12H4V4Z');
    path1.setAttribute('fill', 'currentColor');
    const path2 = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path2.setAttribute('fill-rule', 'evenodd');
    path2.setAttribute('clip-rule', 'evenodd');
    path2.setAttribute('d', 'M5 2H14V11H5V2ZM6 3H13V10H6V3Z');
    path2.setAttribute('fill', 'currentColor');
    svg.appendChild(path1);
    svg.appendChild(path2);
    return svg;
}
function createCheckIcon() {
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('aria-hidden', 'true');
    svg.setAttribute('focusable', 'false');
    svg.setAttribute('width', '16');
    svg.setAttribute('height', '16');
    svg.setAttribute('viewBox', '0 0 16 16');
    svg.setAttribute('fill', 'none');
    const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path.setAttribute('d', 'M6.27 10.87L3.63 8.23L2.56 9.3L6.27 13.01L14.07 5.21L13 4.14L6.27 10.87Z');
    path.setAttribute('fill', 'currentColor');
    svg.appendChild(path);
    return svg;
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