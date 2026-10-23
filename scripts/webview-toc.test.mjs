import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const source = readFileSync(new URL('../app/src/main/assets/renderer/toc.js', import.meta.url), 'utf8');
const { createTocController } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);
function harness() {
  let sent, scroll, y = 9000;
  const nodes = [{ id: 'formatted', top: 100 }, { id: 'formatted-1', top: 800 }, { id: 'late', top: 1200 }];
  nodes.forEach(n => n.getBoundingClientRect = () => ({ top: n.top }));
  const root = { querySelectorAll: () => nodes, contains: n => nodes.includes(n) };
  const toc = createTocController({ root, notify: s => sent = JSON.parse(s), scroll: s => scroll = s,
    getScrollY: () => y, getInset: () => 96 });
  toc.begin(1);
  return { toc, nodes, get sent() { return sent; }, get scroll() { return scroll; } };
}
const items = [
  { slug: 'formatted', text: 'Formatted code', level: 1 },
  { slug: 'formatted-1', text: 'Formatted code', level: 2 },
  { slug: 'late', text: 'Late heading', level: 3 },
];
test('formatted repeated headings preserve renderer identity and distinct levels', () => {
  const h = harness(); h.toc.rendered(items);
  assert.deepEqual(h.sent.headings, items);
  h.toc.active('formatted-1'); assert.equal(h.sent.activeSlug, 'formatted-1');
});
test('late heading is measured at tap time after layout changes', () => {
  const h = harness(); h.toc.rendered(items); h.nodes[2].top = 6789;
  assert.equal(h.toc.reveal(JSON.stringify({ generation: 1, slug: 'late' })), true);
  assert.equal(h.scroll.top, 9000 + 6789 - 96 - 12);
});
test('stale document requests, missing or detached targets never scroll', () => {
  const h = harness(); h.toc.rendered(items);
  assert.equal(h.toc.reveal('{"generation":0,"slug":"late"}'), false);
  assert.equal(h.toc.reveal('{"generation":1,"slug":"missing"}'), false);
  h.nodes.pop(); assert.equal(h.toc.reveal('{"generation":1,"slug":"late"}'), false);
  h.toc.begin(2); assert.equal(h.toc.reveal('{"generation":1,"slug":"formatted"}'), false);
});
test('payload excludes missing DOM, duplicate, invalid and excessively long headings', () => {
  const h = harness(); h.toc.rendered([...items, ...items, { slug: 'missing', text: 'X', level: 1 },
    { slug: 'x'.repeat(513), text: 'X', level: 1 }]);
  assert.equal(h.sent.headings.length, 3);
});
test('native host keeps security gate, bounded parser and function budget', () => {
  const host = readFileSync(new URL('../app/src/main/java/com/markreader/webview/WebViewDocumentHost.kt', import.meta.url), 'utf8');
  assert.ok([...host.matchAll(/^    (?:private )?fun /gm)].length < 11);
  assert.match(host, /fun tocChanged\(json: String\) = fromHostPage \{ toc.receive\(json\) \}/);
  const controller = readFileSync(new URL('../app/src/main/java/com/markreader/webview/WebViewTocController.kt', import.meta.url), 'utf8');
  assert.match(controller, /json.length > MAX_PAYLOAD_CHARS/);
  assert.match(controller, /payload.optLong\("generation", -1\) != generation/);
});
