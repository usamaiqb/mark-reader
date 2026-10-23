// Run: node --test scripts/webview-search.test.mjs (no Android build required).
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const root = new URL('../', import.meta.url);
const read = p => readFileSync(new URL(p, root), 'utf8');
const host = read('app/src/main/java/com/markreader/webview/WebViewDocumentHost.kt');
const controller = read('app/src/main/java/com/markreader/webview/WebViewSearchController.kt');
const controllerMethod = (name, next) => controller.slice(
  controller.indexOf(`fun ${name}(`), controller.indexOf(`fun ${next}(`));
const source = read('app/src/main/assets/renderer/search.js');
const { createSearchRenderNotifier } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);
function harness() {
  let next = 0;
  const frames = new Map();
  let calls = 0;
  const notifier = createSearchRenderNotifier(() => calls++, cb => {
    frames.set(++next, cb); return next;
  }, id => frames.delete(id));
  return { notifier, get calls() { return calls; }, tick() {
    const batch = [...frames.values()]; frames.clear(); batch.forEach(cb => cb());
  } };
}
test('native re-index waits two layout frames', () => {
  const h = harness(); h.notifier.rendered();
  assert.equal(h.calls, 0); h.tick(); assert.equal(h.calls, 0);
  h.tick(); assert.equal(h.calls, 1);
});
test('consecutive renders coalesce, including render between frames', () => {
  const h = harness(); h.notifier.rendered(); h.notifier.rendered();
  h.tick(); h.notifier.rendered(); h.tick(); assert.equal(h.calls, 0);
  h.tick(); assert.equal(h.calls, 1);
});
test('dispose invalidates pending render notification', () => {
  const h = harness(); h.notifier.rendered(); h.tick(); h.notifier.dispose();
  h.tick(); assert.equal(h.calls, 0);
});
test('renderer integration never rewrites DOM for search', () => {
  assert.doesNotMatch(source, /innerHTML|createTreeWalker|createElement|replaceChild|surroundContents/);
  const host = read('app/src/main/assets/renderer/android-host.js');
  assert.match(host, /view\.on\('headings', \(\) => searchRender\.rendered\(\)\)/);
  assert.match(host, /bridge\.searchRendered\(\)/);
});
test('Android policy: native scan, serialize requests, debounce, guard stale results and disposal', () => {
  assert.match(controller, /webView\.findAllAsync\(searchQuery\)/);
  assert.match(controller, /request\.first == searchGeneration/);
  assert.match(controller, /if \(inFlightSearch != null \|\| searchQuery\.isEmpty\(\)\) return/);
  assert.match(controller, /delay\(SEARCH_DEBOUNCE_MS\)/);
  assert.match(controller, /const val SEARCH_DEBOUNCE_MS = 200L/);
  assert.match(controller, /if \(done && !destroyed\) onFindResult\(activeIndex, count\)/);
  const disposal = controllerMethod('destroy', 'onFindResult');
  assert.match(disposal, /destroyed = true/);
  assert.match(disposal, /searchDebounce\?\.cancel\(\)/);
  assert.match(disposal, /\+\+searchGeneration/);
  assert.match(disposal, /webView\.setFindListener\(null\)/);
  assert.match(disposal, /webView\.clearMatches\(\)/);
  const next = controllerMethod('findNext', 'invalidateDocument');
  assert.match(next, /webView\.findNext\(forward\)/);
  assert.doesNotMatch(next, /findAllAsync|scheduleSearch|evaluateJavascript/);
});
test('Compose exposes Markdown search without enabling inaccurate heading offsets', () => {
  const screen = read('app/src/main/java/com/markreader/ui/screens/ViewerScreen.kt');
  assert.match(screen, /isContentSearchable = isContentIndexable \|\| readerContent is ReaderContent.Markdown/);
  assert.match(screen, /hasHeadings && isContentIndexable/);
  assert.match(screen, /onSearchResult = viewModel::onWebViewSearchResult/);
  assert.match(screen, /webViewSearchNavigation\?\.invoke\(true\)/);
  assert.match(screen, /webViewSearchNavigation\?\.invoke\(false\)/);
});

test('navigation callbacks are tagged before dispatch and drain queued taps', () => {
  const next = controllerMethod('findNext', 'invalidateDocument');
  const tag = next.indexOf('inFlightSearch = searchGeneration to searchQuery');
  assert.ok(tag >= 0 && tag < next.indexOf('webView.findNext(forward)'));
  assert.match(next, /pendingSearchNavigation.addLast\(forward\)/);
  assert.match(controller, /findNext\(pendingSearchNavigation.removeFirst\(\)\)/);
});
test('document changes invalidate synchronously, scans await rendered layout', () => {
  for (const name of ['openDocument', 'showDocument']) {
    const method = host.slice(host.indexOf(`fun ${name}(`));
    const invalidate = method.indexOf('searchController.invalidateDocument()');
    assert.ok(invalidate >= 0 && invalidate < method.indexOf('scope.launch('));
  }
  assert.match(controller, /if \(destroyed \|\| !searchDocumentReady \|\| !searchReady\) return/);
  assert.match(host, /fun searchRendered\(\) = fromHostPage \{ searchController.rendered\(\) \}/);
  assert.match(controllerMethod('invalidateDocument', 'rendered'), /searchDocumentReady = false/);
  assert.match(controllerMethod('rendered', 'destroy'), /searchDocumentReady = true\s+scheduleSearch\(\)/);
  assert.match(controller, /pendingSearchNavigation.clear\(\)/);
  assert.doesNotMatch(controllerMethod('invalidateDocument', 'rendered'), /inFlightSearch = null/);
});
test('host delegates search after WebView initialization and before page load', () => {
  const view = host.indexOf('val webView: WebView =');
  const owner = host.indexOf('private val searchController = WebViewSearchController(webView, scope)');
  assert.ok(view >= 0 && owner > view && owner < host.indexOf('webView.loadUrl(PAGE_URL)'));
  assert.match(host, /fun setSearchQuery\(query: String, onResult: \(String, Int, Int\) -> Unit\) =\s+searchController.setSearchQuery\(query, onResult\)/);
  assert.match(host, /val findNext: \(Boolean\) -> Unit = searchController::findNext/);
  assert.match(host, /destroyed = true\s+searchController.destroy\(\)\s+webView.removeJavascriptInterface/);
  assert.doesNotMatch(host, /inFlightSearch|searchGeneration|findAllAsync|setFindListener/);
});
test('search extraction stays within function and condition complexity budgets', () => {
  // Four-space declarations count outer-class methods, not nested bridge/client methods.
  for (const text of [host, controller]) {
    const methods = [...text.matchAll(/^    (?:private )?fun /gm)];
    assert.ok(methods.length < 11, `outer class has ${methods.length} functions (must be below 11)`);
  }
  const start = controller.slice(controller.indexOf('private fun startSearchIfReady()'));
  const guards = [...start.matchAll(/if \((.+)\) return/g)];
  assert.equal(guards.length, 2);
  for (const [, condition] of guards) {
    assert.ok(condition.split(/\|\||&&/).length <= 4, 'search guard exceeds condition budget');
  }
});
