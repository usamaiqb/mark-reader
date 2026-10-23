// SPDX-License-Identifier: GPL-3.0-only
import { mathBoundaries } from './fixtures/math-boundaries.js';
import { runMathTests } from './math-suite.js';
import { setDocumentHtml } from '../../app/src/main/assets/renderer/sanitizer.js';
import { MarkdownItEngine } from '../../app/src/main/assets/renderer/engine.js';
import { getPlugins } from '../../app/src/main/assets/renderer/plugins.js';
import { ensureMath } from '../../app/src/main/assets/renderer/lazy.js';
const result = document.querySelector('#result');
try {
    const assert = (value, message) => { if (!value) throw new Error(message); };
    const fixture = await (await fetch('./fixtures/eight-questions.md')).text();
    const original = await (await fetch('./fixtures/user-eight-answers.md')).text();
    const { count } = await runMathTests(assert, fixture, original);
    // Exercise the real engine and unchanged production sanitizer, not a parallel DOM path.
    const engine = new MarkdownItEngine(getPlugins());
    const root = document.querySelector('#document');
    const rendered = engine.render(fixture);
    setDocumentHtml(root, rendered.html);
    assert(root.querySelectorAll('.katex').length === 13, 'sanitizer retains thirteen formulas');
    assert(root.querySelectorAll('math').length === 13, 'sanitizer retains MathML');
    assert(!root.querySelector('.katex-error'), 'production engine has no formula errors');
    const originalRoot = document.createElement('section');
    root.after(originalRoot);
    setDocumentHtml(originalRoot, engine.render(original).html);
    assert(!originalRoot.querySelector('.katex-error'), 'original answers render through production sanitizer');
    const originalVisible = originalRoot.cloneNode(true);
    originalVisible.querySelectorAll('pre,code,.katex').forEach(node => node.remove());
    assert(!/\\(?:[A-Za-z]+|%)/.test(originalVisible.textContent), 'original prose has no leftover TeX');
    const boundaryRoot = document.createElement('section');
    root.after(boundaryRoot);
    for (const [name, source, expected, prose] of mathBoundaries) {
        const renderedBoundary = engine.render(source).html;
        const beforeSanitizing = document.createElement('template');
        beforeSanitizing.innerHTML = renderedBoundary;
        assert(JSON.stringify([...beforeSanitizing.content.querySelectorAll('annotation')].map(node => node.textContent)) === JSON.stringify(expected), `${name}: engine preserves exact annotations`);
        setDocumentHtml(boundaryRoot, renderedBoundary);
        assert(boundaryRoot.querySelectorAll('.katex').length === expected.length, `${name}: sanitizer retains all formulas`);
        assert(!boundaryRoot.querySelector('.katex-error'), `${name}: production rendering valid`);
        const visible = boundaryRoot.cloneNode(true);
        visible.querySelectorAll('.katex,pre,code').forEach(node => node.remove());
        // Cell layout supplies whitespace absent from token content.
        assert(visible.textContent.replace(/\s/g, '') === prose.replace(/\s/g, ''), `${name}: production DOM preserves prose`);
    }
    boundaryRoot.remove();
    const unsafe = document.createElement('div');
    setDocumentHtml(unsafe, '<script>alert(1)</script><img src=x onerror="alert(1)"><iframe src="https://example.org"></iframe><a href="javascript:alert(1)">bad</a><style>body{display:none}</style>');
    assert(!unsafe.querySelector('script,iframe,style,[onerror],[href^="javascript:"]'), 'sanitizer still blocks executable HTML');
    // The harness is outside renderer/: give the lazy loader its production base before testing.
    const base = document.createElement('base');
    base.href = new URL('../../app/src/main/assets/renderer/', location.href).href;
    document.head.append(base);
    await ensureMath();
    await document.fonts.ready;
    const loaded = [...document.fonts].filter(font => font.status === 'loaded');
    assert(loaded.some(font => font.family === 'KaTeX_Main'), 'offline Main font actually loaded');
    assert(loaded.some(font => font.family === 'KaTeX_Math'), 'offline Math font actually loaded');
    assert(root.querySelector('.katex').getBoundingClientRect().height > 10, 'formula has real layout');
    window.mathTestResult = { passed: true, assertions: count + 11 + mathBoundaries.length * 4, formulas: 13, originalFormulas: originalRoot.querySelectorAll('.katex').length, preservedCodeBlocks: originalRoot.querySelectorAll('pre').length, loadedFonts: loaded.map(f => f.family) };
    result.textContent = JSON.stringify(window.mathTestResult, null, 2);
} catch (error) {
    window.mathTestResult = { passed: false, error: error.stack };
    result.textContent = error.stack;
    console.error(error);
}
