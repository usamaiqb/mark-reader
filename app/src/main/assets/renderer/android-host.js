// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 DigiGate

/*---------------------------------------------------------------------------------------------
 *  The JS half of the Android host — hand-written, not generated. See MANIFEST.md.
 *
 *  It does four things and nothing else:
 *
 *    1. installs `window.markreader` as a `DocumentHost`, on top of the `@JavascriptInterface`
 *       object Kotlin injects as `__mdrHost`;
 *    2. builds the one `DocumentView` this page holds, and forwards the events Compose has to
 *       draw from — the outline, the active heading, the match count — out through `__mdrHost`;
 *    3. exposes `window.__mdrPage` for Kotlin to drive through `evaluateJavascript`;
 *    4. builds standalone HTML files for export, off the same engine the view draws with but
 *       without touching it — see `buildExportHtml`.
 *
 *  **The two directions are deliberately separate objects.** `window.markreader` is the
 *  contract in `host.ts` — the narrow surface the shared document module is allowed to assume,
 *  which on Android is `capabilities`, `openExternal`, `openPath` and `readFile`. `__mdrPage`
 *  is this page's own control surface, which is nobody's contract and can change freely, and the
 *  event forwarding above is its inbound half and equally ours to change. The desktop bridge
 *  conflates them because there the shell *is* the page; here the shell is Compose, so keeping
 *  them apart is what stops app concerns leaking into the host contract.
 *
 *  `showInFolder` is absent, not stubbed. Android has no file manager to reveal into, and the
 *  contract says a host that cannot do something omits it — `capabilitiesOf()` then reports
 *  `revealInFolder: false` and `document.ts` makes the click inert instead of throwing.
 *
 *  Ownership of the document is Kotlin's. `openPath` does not read the file here even though
 *  `readFile` could: SAF permission, the encoding decision, recents and the resume position all
 *  live on the Kotlin side, and a document that changed underneath them would be invisible.
 *  So the flow is always page -> `openPath` -> Kotlin reads -> `__mdrPage.showDocument`.
 *--------------------------------------------------------------------------------------------*/

import { getConfig, parseConfig, setConfig, setHostDefaults } from './config.js';
import { DocumentView } from './document.js';
import { MarkdownItEngine } from './engine.js';
import { NO_CAPABILITIES } from './host.js';
import { prepareForDocument } from './lazy.js';
import { getPlugins } from './plugins.js';
import { setDocumentHtml } from './sanitizer.js';
import { asLocalResourceUri, getResourceOrigin, setResourceOrigin } from './util.js';

/**
 * Where document resources are served from, and the one string this file shares with
 * `DocumentResourceHandler.kt`. The path prefix keeps the SAF space clear of `/assets/`, which
 * is the renderer's own files.
 */
const RESOURCE_PREFIX = '/__mdr';

/**
 * Stand-in for the Kotlin bridge, so this page still loads in a plain browser — `npm run
 * serve:esm` in the desktop repo, with the page copied in — instead of throwing at module
 * scope. Not a fallback for a real device: if this is what is installed there, the
 * `addJavascriptInterface` call did not happen and nothing will work.
 */
const NO_BRIDGE = {
	ready: () => console.warn('[host] no Kotlin bridge; running detached'),
	rendered: path => console.info('[host] rendered', path),
	openPath: path => console.info('[host] openPath', path),
	openExternal: url => console.info('[host] openExternal', url),
	outline: json => console.info('[host] outline', json),
	activeHeading: slug => console.info('[host] activeHeading', slug),
	findState: (index, total) => console.info('[host] findState', index, total),
	exported: (id, html) => console.info('[host] exported', id, html?.length ?? 'failed'),
};

const bridge = window.__mdrHost ?? NO_BRIDGE;

// The host knows its own origin and says so, rather than being sniffed for. Whatever is set
// here has to be reachable under the page's CSP — it is, as `'self'`.
setResourceOrigin(location.origin + RESOURCE_PREFIX);

/**
 * Reading defaults for a phone, replacing the desktop set `config.js` ships.
 *
 * 14px over 22px in a Segoe-first stack is a mouse-and-keyboard reading size. These are the
 * baseline the reader's stored settings are then applied over, which is why they go in through
 * `setHostDefaults` and not `setConfig`.
 */
setHostDefaults({
	fontSize: 16,
	lineHeight: 26,
	fontFamily: 'system-ui, -apple-system, "Roboto", "Noto Sans", "Droid Sans", sans-serif',
});

// --------------------------------------------------------------------------- the host

/** @type {import('./host.js').DocumentHost} */
const host = {
	// Stated, not probed. Android has no file manager to reveal into, no native window behind
	// the page, and no watcher on a SAF tree.
	capabilities: NO_CAPABILITIES,

	/**
	 * A same-origin fetch, not a second bridge — which is the whole reason the page is served
	 * by `WebViewAssetLoader` rather than `loadDataWithBaseURL`. Document text and document
	 * images then travel one seam, so `DocumentResourceHandler.kt` is the only thing that has
	 * to know what a virtual path means.
	 *
	 * Goes through the renderer's own `asLocalResourceUri` rather than reimplementing it, so
	 * there is exactly one place a path becomes a URL.
	 */
	async readFile(filePath) {
		try {
			const response = await fetch(asLocalResourceUri(filePath));
			return response.ok ? { path: filePath, text: await response.text() } : undefined;
		} catch (error) {
			// Reaches logcat through WebChromeClient.onConsoleMessage.
			console.error(`readFile ${filePath}: ${error}`);
			return undefined;
		}
	},

	async openPath(filePath) {
		bridge.openPath(filePath);
	},

	/**
	 * The scheme allowlist is on the Kotlin side, in `ExternalLinks.kt`. `document.ts` checks
	 * `^(https?|mailto):` before it calls and the sanitizer strips `javascript:` before that,
	 * but a link in an untrusted document must not be the thing that decides what the platform
	 * launches — so the last check is the one that cannot be routed around.
	 */
	async openExternal(url) {
		bridge.openExternal(url);
	},

	// showInFolder: deliberately absent.
};

/*
 * `defineProperty`, not assignment, and non-writable. Named access on the window object
 * exposes every element id as a global, so a document with an element id of `markreader`
 * would otherwise shadow the host. Stage 1 of the spike lost an hour to exactly that with
 * mermaid. `addJavascriptInterface` already makes `__mdrHost` a real own property, so that
 * half is safe; this half is ours to get right.
 */
Object.defineProperty(window, 'markreader', {
	value: Object.freeze(host),
	enumerable: true,
	writable: false,
	configurable: false,
});

// --------------------------------------------------------------------------- the view

const view = new DocumentView({
	root: document.getElementById('markdown-body'),
	host,
});

/*
 * Kotlin already knows which document it pushed; what it cannot see is whether the pixels
 * landed. Two frames after the render, they have. That is the signal a loading state is
 * dismissed on, and the one the spike measured first paint with.
 */
view.on('document', doc => {
	if (doc) {
		requestAnimationFrame(() => requestAnimationFrame(() => bridge.rendered(doc.path)));
	}
});

/*
 * The outline and the find state are the two things Compose has to draw that only the page
 * knows, so they leave as events rather than being asked for. `document.js` recomputes both on
 * every render — a theme switch, a live reload, a document opened by following a link — and
 * Kotlin has no way to notice that happened.
 *
 * The outline crosses as JSON because it is a list. The other two are primitives, which
 * `addJavascriptInterface` marshals on its own, so wrapping them in an object would only give
 * the Kotlin side something to parse.
 *
 * The active heading is `undefined` in a document with no headings at all; `''` carries that
 * across, because a `@JavascriptInterface` String parameter is a poor place to learn the
 * difference between a missing argument and a null one.
 *
 * The find state's query is dropped: the app is holding the query, and it is only ever told
 * the answer to something it asked. What it cannot know is the count.
 */
view.on('headings', headings => bridge.outline(JSON.stringify(headings)));
view.on('activeHeading', slug => bridge.activeHeading(slug ?? ''));
view.on('find', state => bridge.findState(state.index, state.total));

/**
 * The code font, held here rather than in `config.js`.
 *
 * The app has a code-font setting and the shared configuration has no key for one — code takes
 * `--vscode-editor-font-family` from the theme shim, which is a *palette* value. Adding a key
 * to `config.ts` is desktop work, and desktop has no bundled monospace to point it at, so this
 * host carries the setting alone: `parseConfig` would drop it, which is why it is read off the
 * raw payload in `applyConfig` and kept here instead of going through `setConfig`.
 *
 * Undefined until the app sends one, which leaves the theme's own stack in place.
 *
 * @type {string | undefined}
 */
let codeFontFamily;

/**
 * The one setting `parseConfig` does not validate, validated the same way it would be.
 *
 * Optional chaining because callers pass a payload that may not be an object at all, and a
 * `null` from `JSON.parse` would otherwise throw here rather than being ignored.
 *
 * @returns {string | undefined} the family, or undefined for anything that is not one.
 */
function validCodeFontFamily(raw) {
	const family = raw?.codeFontFamily;
	return typeof family === 'string' && family.trim().length > 0 ? family : undefined;
}

/**
 * Writes the three typography custom properties `markdown.css` reads, and the one it reads for
 * code.
 *
 * Nothing in the renderer does this today — `fontSize`, `lineHeight` and `fontFamily` are in
 * `config.ts` and are read by no one, so `markdown.css` falls back to its own `14px` — which is
 * a desktop-sized default on a phone. Delete this and its call sites the moment the shared
 * renderer applies its own configuration.
 *
 * The code family goes on the **body**, not on `<html>` with the other three. `theme.css`
 * declares `--vscode-editor-font-family` on `body.vscode-dark` and `body.vscode-light`, and a
 * custom property declared on a nearer ancestor wins — so a value set on `<html>` would be
 * shadowed by the theme and silently do nothing. An inline style on the body beats a class rule.
 */
function applyTypography() {
	const config = getConfig();
	const style = document.documentElement.style;
	style.setProperty('--markdown-font-size', `${config.fontSize}px`);
	style.setProperty('--markdown-line-height', `${config.lineHeight}px`);
	style.setProperty('--markdown-font-family', config.fontFamily);
	if (codeFontFamily) {
		document.body.style.setProperty('--vscode-editor-font-family', codeFontFamily);
	}
}

// -------------------------------------------------------------------- export rendering

/**
 * KaTeX's stylesheet, which `lazy.js` links into this page the first time *any* document has
 * needed math — and never unlinks. 1.4 MB with its fonts inlined, so an export decides for
 * itself rather than copying whatever the page happens to be carrying.
 */
const KATEX_STYLESHEET = 'css/katex.css';

/**
 * How large a document image may be before an export leaves it out.
 *
 * The exported file has to carry its images as data, and a reader would rather have a document
 * with one picture missing than one the share sheet refuses to hand on.
 */
const MAX_INLINE_BYTES = 4 * 1024 * 1024;

/**
 * A standalone HTML file, rendered by the engine the reader draws with.
 *
 * Export used to run a Markdown implementation of its own — commonmark, parsing the source a
 * second time and rendering it against a hand-written stylesheet — so "export what you see" was
 * only ever approximately true, and became less true as the reader moved into this page. What
 * leaves the app now is the same markdown-it configuration, the same plugin set, the same
 * sanitiser and the same stylesheets.
 *
 * **It renders into a detached container with an engine of its own, and never touches the
 * `DocumentView` above.** Export is not a view of the reader: it has its own text (the format's
 * rendered form, which in raw mode is not what is on screen), its own settings, and it must not
 * move the reading position, drop a live find or repaint anything.
 *
 * It is here rather than in the shared renderer because the *file format* — inlined stylesheets,
 * inlined images, no script — is a host's answer to what a document looks like once it has left
 * the app, and desktop has no export to share the answer with. If it grows one, this moves
 * across, the way `codeFontFamily` will when `config.ts` gains a key for it.
 *
 * **Three things on screen are deliberately not in the file.** Mermaid diagrams export as their
 * source: hydrating one is `DocumentView`'s, and repeating that loop here would be a second
 * mermaid pipeline — the fallback `<pre>` the renderer emits beside every diagram is exactly
 * what it is for. The bundled reading fonts are not embedded, because they are this APK's and
 * every stack the app sends ends in a generic. And the copy buttons and heading anchors the
 * reader hydrates are affordances for a page with script, which this file does not have.
 *
 * @param request {{ path?: string, title?: string, text?: string, settings?: unknown }}
 * @returns {Promise<string>} a complete HTML document.
 */
async function buildExportHtml(request) {
	const text = String(request.text ?? '');
	const path = String(request.path ?? '');
	// The app's settings, validated exactly as `applyConfig` validates them, over this page's
	// own baseline — so an export is correct even when the reader that would have applied them
	// is not on screen, which is what raw mode looks like from in here.
	const settings = { ...getConfig(), ...parseConfig(request.settings) };
	const codeFont = validCodeFontFamily(request.settings) ?? codeFontFamily;

	// Loads highlight.js and KaTeX if this document needs them, exactly as a render would. It is
	// also what makes the stylesheet decision in `exportStylesheets` correct.
	await prepareForDocument(text);

	const output = new MarkdownItEngine(getPlugins()).render(text, { documentPath: path });

	const container = document.createElement('div');
	container.className = 'markdown-body';
	container.setAttribute('dir', 'auto');
	// The same seam every rendering goes through; see `sanitizer.js`.
	setDocumentHtml(container, output.html);
	await prepareContentForExport(container);

	const dark = settings.theme === 'dark'
		|| (settings.theme !== 'light' && window.matchMedia('(prefers-color-scheme: dark)').matches);
	const bodyClasses = [dark ? 'vscode-dark' : 'vscode-light'];
	if (settings.wordWrap) {
		bodyClasses.push('wordWrap');
	}

	return [
		'<!doctype html>',
		`<html lang="en" style="${escapeAttribute(typographyProperties(settings))}">`,
		'<head>',
		'<meta charset="utf-8">',
		'<meta name="viewport" content="width=device-width, initial-scale=1">',
		`<title>${escapeText(String(request.title ?? ''))}</title>`,
		`<style>\n${await exportStylesheets(container)}\n${EXPORT_PAGE_CSS}</style>`,
		'</head>',
		`<body class="${escapeAttribute(bodyClasses.join(' '))}"`
			+ `${codeFont ? ` style="${escapeAttribute(`--vscode-editor-font-family: ${codeFont}`)}"` : ''}>`,
		container.outerHTML,
		'</body>',
		'</html>',
		'',
	].join('\n');
}

/**
 * What the host page's own `<style>` block contributes that a shared file still needs.
 *
 * Almost none of it: the rest of that block is the bundled `@font-face` rules, the content inset
 * Compose draws over, and the tap-highlight suppression — all of them statements about this app
 * rather than about the document. `markdown.css` owns the body's padding and needs the margin
 * gone from under it.
 *
 * The measure is ours and is not "what you see": a phone is narrow enough that `markdown.css`'s
 * inline padding is the whole layout, and a browser window is not. Without it an exported
 * document is one line across a desktop display.
 */
const EXPORT_PAGE_CSS = `
html { margin: 0; }
body { margin: 0 auto; max-width: 55rem; }
`;

/** The three custom properties `markdown.css` reads, as an inline style for the exported root. */
function typographyProperties(settings) {
	return [
		`--markdown-font-size: ${settings.fontSize}px`,
		`--markdown-line-height: ${settings.lineHeight}px`,
		`--markdown-font-family: ${settings.fontFamily}`,
	].join('; ');
}

/**
 * The document stylesheets, as text, in the order the page loads them.
 *
 * Read off the page's own `<link>` elements rather than from a list kept here, because a list
 * here would be a second copy of `android-host.html`'s `<head>` that nothing would notice had
 * drifted — the export would simply render unstyled in one place. The page's inline `<style>`
 * is skipped for the reason [EXPORT_PAGE_CSS] gives.
 *
 * KaTeX is the one entry decided rather than copied, and it is decided **from the rendering
 * rather than from the source**. `lazy.js` links that stylesheet the first time any document
 * needs math and never unlinks it, so copying the live set would put 1.4 MB into the export of a
 * document with no math in it; and `detectDocumentNeeds` counts any `$` at all as math, which is
 * the right generosity for *loading* KaTeX and much too generous for embedding its stylesheet.
 * A rendered `.katex` element is the exact question, and by here there is a rendering to ask.
 */
async function exportStylesheets(container) {
	const hrefs = Array.from(document.querySelectorAll('link[rel="stylesheet"]'))
		.map(link => link.getAttribute('href'))
		.filter(href => href && !href.endsWith(KATEX_STYLESHEET));
	if (container.querySelector('.katex')) {
		hrefs.push(KATEX_STYLESHEET);
	}
	const sheets = await Promise.all(hrefs.map(async href => {
		try {
			const response = await fetch(new URL(href, location.href));
			return response.ok ? await response.text() : '';
		} catch (error) {
			// Reaches logcat through WebChromeClient.onConsoleMessage.
			console.error(`export stylesheet ${href}: ${error}`);
			return '';
		}
	}));
	return sheets.filter(Boolean).join('\n');
}

/**
 * Makes the rendered markup stand on its own, away from the host that served it.
 *
 * Both halves are about the same thing: the renderer resolves a document's own references
 * against an origin only this app answers on, and that origin is meaningless — and misleading,
 * in an app with no `INTERNET` permission — in a file someone has shared or printed.
 *
 *  - **Images become data URIs.** They have never survived an export: the commonmark path left
 *    them as document-relative paths against a `null` base URL, so every one of them was a
 *    broken image in the PDF too. Anything too large, or that fails to read, loses its `src`
 *    and keeps its alt text.
 *  - **`data-resolved-path` is dropped.** It names a path in the virtual space this session
 *    minted, which no other reader of the file can resolve and which nothing without script
 *    would read anyway.
 */
async function prepareContentForExport(root) {
	for (const element of root.querySelectorAll('[data-resolved-path]')) {
		element.removeAttribute('data-resolved-path');
	}

	const prefix = getResourceOrigin() + '/';
	for (const image of root.querySelectorAll('img')) {
		const source = image.getAttribute('src') ?? '';
		if (!source.startsWith(prefix)) {
			continue;
		}
		const inlined = await asDataUri(source);
		if (inlined) {
			image.setAttribute('src', inlined);
		} else {
			image.removeAttribute('src');
		}
	}
}

/** A document resource as a `data:` URI, or undefined if it is unreadable or too large. */
async function asDataUri(url) {
	try {
		const response = await fetch(url);
		if (!response.ok) {
			return undefined;
		}
		const blob = await response.blob();
		if (blob.size > MAX_INLINE_BYTES) {
			console.warn(`export: ${url} is ${blob.size} bytes and was left out`);
			return undefined;
		}
		return await new Promise((resolve, reject) => {
			const reader = new FileReader();
			reader.addEventListener('load', () => resolve(String(reader.result)));
			reader.addEventListener('error', () => reject(reader.error));
			reader.readAsDataURL(blob);
		});
	} catch (error) {
		console.error(`export image ${url}: ${error}`);
		return undefined;
	}
}

/**
 * Escapes text for element content.
 *
 * The document's own markup is sanitised HTML and is inserted as such; this is for the values
 * *this* file writes around it — a title taken from a file name, and the styles and classes
 * built above. Nothing here is attacker-controlled by design, which is exactly why it is escaped
 * rather than trusted: a file name is provider-supplied, and the one place a document could
 * reach this is through its own name.
 */
function escapeText(value) {
	return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

/** As {@link escapeText}, plus the quote that would close the attribute. */
function escapeAttribute(value) {
	return escapeText(value).replace(/"/g, '&quot;');
}

/** Settings the markdown-it engine is constructed with, so a change has to rebuild it. */
const ENGINE_SETTINGS = ['breaks', 'linkify', 'typographer'];

/** Settings that only change the output of a render. */
const RENDER_SETTINGS = ['frontMatter'];

// -------------------------------------------------------------- the page's own surface

const page = {
	/**
	 * @param json {string} a JSON `{ path, text, rootPath?, preserveScroll? }`. Kotlin hands it
	 *   across as a string rather than an expression, so nothing is injected into the page.
	 */
	showDocument(json) {
		const payload = JSON.parse(json);
		// Root-relative links (`/README.md`) resolve against the folder, when there is one.
		view.setRootPath(payload.rootPath ?? undefined);
		view.setDocument(
			{ path: payload.path, text: payload.text },
			{ preserveScroll: payload.preserveScroll === true },
		);
	},

	/**
	 * Room to leave at the top of the document for the chrome Compose draws over it.
	 *
	 * Android-only, and not part of the shared host contract: the desktop shell sizes its own
	 * toolbar offset in `app.css`. Applied as document padding so it scrolls away with the
	 * content when the chrome collapses.
	 *
	 * @param json {string} a JSON `{ top }`, in CSS pixels.
	 */
	setContentInset(json) {
		const top = Number(JSON.parse(json).top) || 0;
		document.documentElement.style.setProperty('--mr-content-inset-top', `${Math.max(0, top)}px`);
	},

	/**
	 * @param json {string} a JSON settings object, in the shape `config.ts` persists, plus this
	 *   host's own `codeFontFamily`. The shared keys are validated by `parseConfig`, which drops
	 *   unknown ones and falls back per-setting — the same check the desktop app applies to
	 *   settings that have been on disk, and Kotlin's DataStore is no more trustworthy than a file
	 *   someone hand-edited. `codeFontFamily` is one `parseConfig` would drop, so it is taken off
	 *   the raw payload and checked here.
	 */
	applyConfig(json) {
		const raw = JSON.parse(json);
		const before = getConfig();
		const after = setConfig(parseConfig(raw));

		codeFontFamily = validCodeFontFamily(raw) ?? codeFontFamily;

		applyTypography();
		view.applyTheme();
		view.setWordWrap(after.wordWrap);

		if (ENGINE_SETTINGS.some(key => before[key] !== after[key])) {
			view.rebuildEngine();
		} else if (RENDER_SETTINGS.some(key => before[key] !== after[key])) {
			view.render();
		}
	},

	/**
	 * Finds `query` in the rendering, paints every match and reveals the first.
	 *
	 * **The match set is the page's, not Kotlin's.** Matches are DOM ranges over the rendered
	 * document, and this is the only side that has the rendering — so what goes back out is a
	 * count and an index, through the `find` event above, and the search bar is drawn from that.
	 *
	 * Highlighting needs the CSS Custom Highlight API, which `document.js` feature-detects. Below
	 * that the count and the navigation still work and nothing is painted, which is a poor search
	 * but not a broken one.
	 *
	 * @param json {string} a JSON `{ query }`.
	 */
	runFind(json) {
		view.runFind(String(JSON.parse(json).query ?? ''));
	},

	/**
	 * Moves to another match, wrapping at either end.
	 *
	 * @param json {string} a JSON `{ delta }` — `1` for the next match, `-1` for the previous.
	 */
	stepFind(json) {
		view.stepFind(Number(JSON.parse(json).delta) || 0);
	},

	/**
	 * Drops the query and its highlights, because the search affordance closed.
	 *
	 * `openFind` has no counterpart here on purpose: it re-runs whatever query the *page* was
	 * last given, and the app is the one holding the query — the search bar's text survives the
	 * bar closing, so opening it sends `runFind` and there is nothing for a second door to do.
	 */
	closeFind() {
		view.closeFind();
	},

	/**
	 * Scrolls a heading into view: the table of contents today, the resume position when that
	 * moves onto an anchor.
	 *
	 * The headroom left above the target is the renderer's (`REVEAL_OFFSET`), which knows nothing
	 * about the chrome Compose draws over the page — so a reveal that does not also collapse the
	 * chrome can land the heading behind it. Fixing that properly means teaching the shared
	 * reveal about the content inset, which is desktop work.
	 *
	 * @param json {string} a JSON `{ slug }`, one of the slugs the outline reported.
	 */
	revealSlug(json) {
		view.revealSlug(String(JSON.parse(json).slug ?? ''));
	},

	/**
	 * Renders a document as a standalone HTML file and hands it back through `__mdrHost`.
	 *
	 * **The only method here that answers.** Everything else this page is asked to do either
	 * changes what is on screen or is reported through an event that fires again on its own;
	 * an export is a question with one answer, asked once. It cannot come back through
	 * `evaluateJavascript`'s return value because the render awaits the modules the document
	 * needs, and a promise crosses that boundary as `{}` — so the reply carries the id it was
	 * asked with, and Kotlin matches them up.
	 *
	 * A failure replies with `null` rather than not replying: a caller waiting on a rendering
	 * that threw would otherwise sit there until its timeout with nothing in the log but a
	 * console error.
	 *
	 * @param json {string} a JSON `{ id, path, text, title, settings }`. `text` is the format's
	 *   rendered form, which is not always what is on screen — exporting from raw mode still
	 *   exports the rendered document.
	 */
	exportDocument(json) {
		const request = JSON.parse(json);
		const id = String(request.id ?? '');
		void (async () => {
			let html = null;
			try {
				html = await buildExportHtml(request);
			} catch (error) {
				console.error(`export ${request.path}: ${error}`);
			}
			bridge.exported(id, html);
		})();
	},
};

Object.defineProperty(window, '__mdrPage', {
	value: Object.freeze(page),
	enumerable: false,
	writable: false,
	configurable: false,
});

// ------------------------------------------------------------------------------ start

applyTypography();
view.applyTheme();
view.setWordWrap(getConfig().wordWrap);

/*
 * Last. Kotlin holds any document it has been asked to open until this fires — one emitted
 * before the view exists would be dropped silently, which is a nasty thing to debug on a
 * device with no console.
 */
bridge.ready();
