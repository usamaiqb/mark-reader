// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 DigiGate
/** The window when `element` is absent, which is what a host without a scroll container gets. */
export function scrollAreaOf(element, win = window) {
    const target = element ?? win;
    // The padding box is what scrolls, so the border is not part of the visible area.
    const edge = element ? () => element.getBoundingClientRect().top + element.clientTop : () => 0;
    const extent = element ? () => element.clientHeight : () => win.innerHeight;
    let covered = 0;
    return {
        get scrollTop() { return element ? element.scrollTop : win.scrollY; },
        get height() { return Math.max(0, extent() - covered); },
        fromTop: clientY => clientY - edge() - covered,
        scrollTo: (top, behavior) => target.scrollTo(behavior ? { top, behavior } : { top }),
        onScroll: listener => target.addEventListener('scroll', listener, { passive: true }),
        get coveredTop() { return covered; },
        set coveredTop(px) { covered = Number.isFinite(px) && px > 0 ? px : 0; },
    };
}
//# sourceMappingURL=scroll.js.map