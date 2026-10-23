// Real DOM suite, callable from the built-in browser or Playwright.
export async function runPresentationTests() {
    const checks = [];
    const check = (condition, message) => { checks.push({ pass: !!condition, message }); };
    const root = document.querySelector('main');
    const pre = root.querySelector('pre');
    const table = root.querySelector('.table-wrapper');
    const dims = () => ({ viewport: innerWidth, page: document.documentElement.scrollWidth, root: root.clientWidth,
        table: { client: table.clientWidth, scroll: table.scrollWidth }, code: { client: pre.clientWidth, scroll: pre.scrollWidth },
        font: getComputedStyle(pre.querySelector('code')).fontFamily });
    const before = dims();
    check(before.page === innerWidth, 'no whole-page horizontal overflow');
    check(innerWidth <= 600 ? before.table.scroll > before.table.client : before.table.scroll === before.table.client, 'table scrolls on mobile and fits naturally on wide screens');
    if (innerWidth === 390) check(before.code.scroll > before.code.client, 'long unwrapped code scroll confined to pre');
    check(root.querySelectorAll('.hljs-keyword').length > 0, 'syntax highlighting loaded');
    check(root.querySelectorAll('thead th:not([scope="col"])').length === 0, 'column headers accessible');
    check(root.querySelectorAll('.table-wrapper[tabindex="0"]').length === 3, 'all table regions keyboard accessible');
    check(getComputedStyle(root.querySelector('.code-block-copy-button')).opacity === '1', 'copy visible without hover');
    const source = pre.querySelector('code').textContent;
    const copy = root.querySelector('.code-block-copy-button');
    copy.click();
    await new Promise(resolve => setTimeout(resolve, 0));
    check(window.copyCalls[0] === source && copy.textContent === 'Copied', 'native adapter copies exact text and confirms success');
    window.copyMode = 'failure'; copy.click();
    await new Promise(resolve => setTimeout(resolve, 0));
    check(root.querySelector('[role="status"]').textContent.includes('unavailable'), 'rejected copy reports failure');
    root.querySelector('.code-block-wrap-button').click();
    check(getComputedStyle(pre).whiteSpace === 'pre-wrap' && pre.scrollWidth <= pre.clientWidth, 'wrap includes unbroken code tokens');
    window.view.setWordWrap(true);
    window.helpers.enhanceTables(root); window.helpers.enhanceCodeBlocks(root);
    check(root.querySelectorAll('.code-block').length === 4 && !root.querySelector('.table-wrapper .table-wrapper'), 'enhancement idempotent');
    check(!root.querySelector('.code-block-wrap-button[aria-pressed="false"]'), 'global wrap synchronizes controls');
    window.view.runFind('Wrap');
    check(window.view.findMatches.length === 1, 'find excludes toolbar text');
    window.view.closeFind();
    const box = document.createElement('div');
    window.helpers.setDocumentHtml(box, '<img src=x onerror="alert(1)"><script>alert(1)</script><pre><code>&lt;script&gt;literal&lt;/script&gt;</code></pre>');
    check(!box.querySelector('script,[onerror]') && box.querySelector('code').textContent === '<script>literal</script>', 'sanitizer and literal code preserved');
    const oldClipboard = Object.getOwnPropertyDescriptor(navigator, 'clipboard');
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: undefined });
    box.innerHTML = '<pre><code>exact source\n</code></pre>'; root.append(box);
    window.helpers.enhanceCodeBlocks(box); box.querySelector('.code-block-copy-button').click();
    await new Promise(resolve => setTimeout(resolve, 0));
    check(box.querySelector('[role="status"]').textContent.includes('unavailable'), 'unavailable clipboard never reports success');
    box.remove();
    if (oldClipboard) Object.defineProperty(navigator, 'clipboard', oldClipboard); else delete navigator.clipboard;
    document.body.style.setProperty('--vscode-editor-font-family', '"JetBrains Mono", monospace');
    const loaded = await document.fonts.load('16px "JetBrains Mono"');
    await document.fonts.ready;
    check(loaded.length > 0 && loaded.every(font => font.status === 'loaded'), 'offline JetBrains font loaded successfully');
    check(getComputedStyle(pre.querySelector('code')).fontFamily.includes('JetBrains Mono'), 'code uses requested font on body');
    document.body.classList.add('source-document');
    check(pre.clientWidth > before.code.client, 'source mode eliminates duplicated gutter');
    document.body.classList.remove('source-document');
    window.view.setWordWrap(false);
    return { passed: checks.filter(c => c.pass).length, total: checks.length, checks, before, after: dims() };
}
