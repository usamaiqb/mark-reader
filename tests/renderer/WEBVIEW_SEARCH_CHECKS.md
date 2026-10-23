# Markdown WebView search checks

## Implementation

Rendered Markdown uses Android WebView `findAllAsync`, `setFindListener`,
`findNext`, and `clearMatches`. It does not insert search spans, wrappers, or
rewrite renderer DOM. The 200 ms debounce runs in the host's UI coroutine scope.
Only one native scan runs at a time: callbacks have no request IDs, so a generation
check plus serialization rejects obsolete counts before scanning the latest query.
Next/previous reuse the WebView match cache and wrap natively. Search close clears
matches; when a scan is still running its obsolete final result is discarded and
matches are cleared at that serialization fence. Disposal cancels debounce and
removes the listener before destroying the WebView.

Navigation requests also receive the current generation before `findNext` is
called, and rapid taps are queued and drained after each final callback. Document
replacement invalidates the generation immediately and blocks scans until the
renderer reports layout completion.

Platform boundary requiring device verification: native find has no request ID
or cancellation-completion API. If a particular WebView drops a final callback
when its DOM is replaced, the serialized request remains pending; do not add a
blind timeout that clears the request and attributes its late callback to a newer
query. Test pending-scan document/settings changes on supported WebView versions.

The renderer's `headings` event fires on every render, even with zero headings.
`search.js` coalesces that event and waits two animation frames before notifying
Kotlin to invalidate and re-query (e.g. after a front-matter/settings re-render).
The helper never accesses document text or changes DOM. Markdown search controls
are enabled independently of the source-offset table of contents, which remains
span-only.

## No-build regression check

```sh
node --test scripts/webview-search.test.mjs
git diff --check
```

These tests exercise frame coalescing, mid-frame invalidation, disposal and static
Android/Compose wiring. They do **not** emulate Chromium's native find callbacks.
No local Gradle, Kotlin or Android build is required or was performed; compilation
and JVM tests belong to GitHub Actions.

## Device acceptance checks (after CI supplies APK)

1. Open rendered Markdown with repeated words in headings, prose, links, inline
   code, a table and fenced code. Open Search: verify visible-text matches and
   `1/N` count. Search Markdown syntax absent from visible text (`**`, fence
   delimiters): verify it is not counted merely because it exists in source.
2. Rapidly type, replace, and clear queries on a large document. Final count must
   correspond only to the last query, with no old count flashing after settling.
   Include literal punctuation (`[`, `.`, `*`), Unicode and no-match queries.
3. Tap Next/Previous repeatedly, including beyond either end. Verify native scroll,
   active highlight and wrapped `index/count`; profiling/logging should show no
   additional `findAllAsync` per navigation tap.
4. Change a render-affecting setting (front matter, breaks, linkify) while Search
   remains open. Verify the same query is re-indexed after the new layout, not
   left highlighting detached DOM. Also reopen/change documents with Search open.
5. Close Search and navigate away while a large scan/debounce is pending. Verify
   highlights clear, no stale count returns, and no callback reaches a destroyed
   WebView. Repeat rendered/raw switching, rotation, and fallback on unsupported
   WebView; native/plain-text search must remain functional.
6. Verify links, selections, KaTeX and diagrams still work before/after Search;
   native find must not add `<mark>` elements or damage renderer HTML. Actual
   Chromium visibility behavior (e.g. math accessibility text) is device-dependent
   and must be checked here, not asserted by the static tests.
