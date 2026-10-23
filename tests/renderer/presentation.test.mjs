// Run: node --experimental-default-type=module tests/renderer/presentation.test.mjs
// Dependency-free Node parser/presentation contract regressions. Browser assertions live
// in presentation-suite.js and optional presentation.e2e.mjs, not silently skipped here.
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { MarkdownItEngine } from '../../app/src/main/assets/renderer/engine.js';
import { ensureHighlighter } from '../../app/src/main/assets/renderer/lazy.js';
import { setConfig } from '../../app/src/main/assets/renderer/config.js';
import { enhanceTables, enhanceCodeBlocks } from '../../app/src/main/assets/renderer/presentation.js';
setConfig({ math: false, mermaid: false });
await ensureHighlighter();
const engine = new MarkdownItEngine();
const source = await readFile(new URL('./fixtures/presentation.md', import.meta.url), 'utf8');
const html = engine.render(source).html;
let count = 0;
const check = (condition, message) => { assert.ok(condition, message); count++; };
check(html.includes('hljs-keyword'), 'lazy highlighter emits actual tokens');
check(/class="[^"]*hljs[^\"]*language-javascript/.test(html), 'real fence rule attaches hljs and language');
check(html.includes('language-unknown-language'), 'unknown language retained');
check(html.includes('&lt;script&gt;'), 'unknown language escapes executable source');
check(html.includes('style="text-align:right"'), 'markdown alignment preserved');
check((html.match(/<table/g) || []).length === 3, 'markdown and raw HTML tables preserved');
const exact = '\tconst x = 1;  \n\n';
const result = engine.render('```unrecognized\n' + exact + '```').html;
check(result.includes(exact), 'exact indentation/trailing spaces/blank line preserved');
const unsafe = engine.render('```"><img src=x onerror=alert(1)>\n<body>\n```').html;
check(!unsafe.includes('<img'), 'malicious info string cannot create markup');
check(unsafe.includes('&lt;body&gt;'), 'literal code escaped');
const mermaid = engine.render('```mermaid\ngraph LR; A-->B\n```').html;
check(mermaid.includes('mermaid-fallback') && !mermaid.includes('code-block-toolbar'), 'mermaid stays separate');
const css = await readFile(new URL('../../app/src/main/assets/renderer/css/document-overrides.css', import.meta.url), 'utf8');
check(css.indexOf("@import './fonts.css'") < css.indexOf('blockquote {'), 'offline fonts imported before CSS rules');
check(css.includes('--vscode-editor-font-family'), 'font preference variable supported');
check(css.includes('body.source-document'), 'whole source file avoids double gutter');
check(css.includes('overflow-wrap: anywhere'), 'long tokens can wrap');
check(typeof enhanceTables === 'function' && typeof enhanceCodeBlocks === 'function', 'enhancement module imports without browser globals');
const vendorHashes = {
    'markdown.css': '9c7fc4016a05a953820e7dd83506cecc31c610a19d137142f21b10bed7c0ff2a',
    'highlight.css': '427a82c14e421dbbfa6c595f5623ce25579424f203b6506a1dac4bcd13ad719b',
};
for (const [name, expected] of Object.entries(vendorHashes)) {
    const { createHash } = await import('node:crypto');
    const local = await readFile(new URL(`../../app/src/main/assets/renderer/css/${name}`, import.meta.url));
    check(createHash('sha256').update(local).digest('hex') === expected, `${name}: pinned upstream vendor bytes unchanged`);
}
console.log(`PASS ${count} presentation parser/contracts (Node; no DOM emulation)`);
