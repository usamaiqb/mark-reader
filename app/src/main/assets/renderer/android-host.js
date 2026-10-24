// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 DigiGate

/*---------------------------------------------------------------------------------------------
 *  The JS half of the Android host — hand-written, not generated. See MANIFEST.md.
 *
 *  It does three things and nothing else:
 *
 *    1. installs `window.markreader` as a `DocumentHost`, on top of the `@JavascriptInterface`
 *       object Kotlin injects as `__mdrHost`;
 *    2. builds the one `DocumentView` this page holds, and forwards the events Compose has to
 *       draw from — the outline, the active heading, the match count — out through `__mdrHost`;
 *    3. exposes `window.__mdrPage` for Kotlin to drive through `evaluateJavascript`.
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
import { NO_CAPABILITIES } from './host.js';
import { asLocalResourceUri, setResourceOrigin } from './util.js';

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
 * Writes the three typography custom properties `markdown.css` reads.
 *
 * Nothing in the renderer does this today — `fontSize`, `lineHeight` and `fontFamily` are in
 * `config.ts` and are read by no one, so `markdown.css` falls back to its own `14px` — which is
 * a desktop-sized default on a phone. Delete this and its call sites the moment the shared
 * renderer applies its own configuration.
 */
function applyTypography() {
	const config = getConfig();
	const style = document.documentElement.style;
	style.setProperty('--markdown-font-size', `${config.fontSize}px`);
	style.setProperty('--markdown-line-height', `${config.lineHeight}px`);
	style.setProperty('--markdown-font-family', config.fontFamily);
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
	 * @param json {string} a JSON settings object, in the shape `config.ts` persists. Validated
	 *   by `parseConfig`, which drops unknown keys and falls back per-setting — it is the same
	 *   check the desktop app applies to settings that have been on disk, and Kotlin's DataStore
	 *   is no more trustworthy than a file someone hand-edited.
	 */
	applyConfig(json) {
		const before = getConfig();
		const after = setConfig(parseConfig(JSON.parse(json)));

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
