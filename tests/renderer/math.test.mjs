// SPDX-License-Identifier: GPL-3.0-only
// Run from repo root: node --experimental-default-type=module tests/renderer/math.test.mjs
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { runMathTests } from './math-suite.js';
import MarkdownIt from '../../app/src/main/assets/renderer/vendor/markdown-it.js';
import { getPlugins } from '../../app/src/main/assets/renderer/plugins.js';
const fixture = await readFile(new URL('./fixtures/eight-questions.md', import.meta.url), 'utf8');
const original = await readFile(new URL('./fixtures/user-eight-answers.md', import.meta.url), 'utf8');
const { count } = await runMathTests((value, message) => assert.ok(value, message), fixture, original);
const md = MarkdownIt();
getPlugins().forEach(plugin => md.use(plugin));
for (const source of ['ordinary plain English prose '.repeat(40000), 'x + '.repeat(200000), 'x + '.repeat(50000) + '\\alpha', 'a'.repeat(1000000), '\\[\nx=1\n'.repeat(10000), '\\( '.repeat(20000), '\\frac{'.repeat(10000), 'x^'.repeat(20000)]) {
    const start = performance.now();
    md.render(source);
    const ms = performance.now() - start;
    const budget = source.length <= 70000 ? 500 : 2000;
    assert.ok(ms < budget, `bounded scanner completes within ${budget}ms: ${ms}ms`);
    const rss = process.memoryUsage().rss;
    assert.ok(rss < 2 * 1024 ** 3, `RSS below 2GiB: ${rss}`);
    console.log(`PERF ${source.length} characters: ${Math.round(ms)}ms; RSS ${Math.round(rss / 1024 ** 2)}MiB`);
}
const css = await readFile(new URL('../../app/src/main/assets/renderer/css/katex.css', import.meta.url), 'utf8');
const fonts = [...css.matchAll(/url\(([^)]+)\)/g)].map(match => match[1]);
assert.equal(fonts.length, 60, 'all twenty faces include three offline font formats');
for (const font of fonts) {
    assert.match(font, /^data:font\/(woff2?|ttf);base64,/);
    const bytes = Buffer.from(font.split(',')[1], 'base64');
    assert.ok(bytes.length > 1000, 'embedded font is nonempty');
    assert.ok(['wOF2', 'wOFF', '\u0000\u0001\u0000\u0000'].includes(bytes.subarray(0, 4).toString('latin1')), 'font signature valid');
}
console.log(`PASS ${count} math assertions; ${fonts.length} valid embedded offline fonts`);
