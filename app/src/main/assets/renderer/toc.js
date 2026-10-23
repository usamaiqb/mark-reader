// SPDX-License-Identifier: GPL-3.0-only
// Android TOC uses the renderer's slugs, never Markdown character offsets.
export const TOC_LIMITS = Object.freeze({ count: 2000, text: 512, slug: 512 });

export function createTocController({ root, notify, scroll = options => window.scrollTo(options),
	getScrollY = () => window.scrollY, getInset = () =>
		parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--mr-content-inset-top')) || 0 }) {
	let generation = 0;
	let headings = [];
	let targets = new Map();
	let activeSlug = null;
	const publish = () => notify(JSON.stringify({ generation, headings, activeSlug }));
	return {
		begin(value) {
			generation = value;
			headings = [];
			targets.clear();
			activeSlug = null;
		},
		rendered(items) {
			// Scope lookup to actual heading elements in this document. Duplicate raw HTML IDs
			// elsewhere in the page must not redirect a TOC tap to a non-heading element.
			const nodes = new Map();
			for (const node of root.querySelectorAll('h1[id],h2[id],h3[id],h4[id],h5[id],h6[id]')) {
				if (!nodes.has(node.id)) nodes.set(node.id, node);
			}
			targets = new Map();
			headings = [];
			for (const item of items) {
				if (headings.length >= TOC_LIMITS.count) break;
				if (typeof item.slug !== 'string' || !item.slug || item.slug.length > TOC_LIMITS.slug ||
					!Number.isInteger(item.level) || item.level < 1 || item.level > 6 ||
					!nodes.has(item.slug) || targets.has(item.slug)) continue;
				targets.set(item.slug, nodes.get(item.slug));
				headings.push({ slug: item.slug, text: String(item.text ?? '').slice(0, TOC_LIMITS.text), level: item.level });
			}
			if (!targets.has(activeSlug)) activeSlug = headings[0]?.slug ?? null;
			publish();
		},
		active(slug) {
			const next = targets.has(slug) ? slug : null;
			if (next === activeSlug) return;
			activeSlug = next;
			publish();
		},
		reveal(json) {
			const request = JSON.parse(json);
			if (request.generation !== generation) return false;
			const target = targets.get(request.slug);
			if (!target || !root.contains(target)) return false;
			// Measure at tap time, after images, math and font changes; no source-length estimate.
			const top = target.getBoundingClientRect().top + getScrollY() - Math.max(0, getInset()) - 12;
			scroll({ top: Math.max(0, top), behavior: 'auto' });
			return true;
		},
	};
}
