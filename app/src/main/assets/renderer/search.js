// SPDX-License-Identifier: GPL-3.0-only
// Search highlighting belongs to Android WebView, never to renderer DOM wrappers.
// Coalesce synchronous renders and wait for layout before asking native find to re-index.
export function createSearchRenderNotifier(notify, frame = requestAnimationFrame, cancel = cancelAnimationFrame) {
	let pending;
	let generation = 0;
	return {
		rendered() {
			const current = ++generation;
			if (pending !== undefined) cancel(pending);
			pending = frame(() => {
				pending = frame(() => {
					pending = undefined;
					if (current === generation) notify();
				});
			});
		},
		dispose() {
			++generation;
			if (pending !== undefined) cancel(pending);
			pending = undefined;
		},
	};
}
