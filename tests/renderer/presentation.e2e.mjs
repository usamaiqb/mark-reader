// Optional remote/desktop browser runner. Dependency-free Node tests are presentation.test.mjs.
// npm install --no-save playwright-core && npx playwright-core install --with-deps chromium
// python3 -m http.server 8766 &
// node --experimental-default-type=module tests/renderer/presentation.e2e.mjs
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright-core');
const out = process.env.EVIDENCE_DIR || '/tmp/mark-reader-presentation-evidence';
await mkdir(out, { recursive: true });
const browser = await chromium.launch({ ...(process.env.CHROMIUM ? { executablePath: process.env.CHROMIUM } : {}), headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'] });
const results = [];
try {
    for (const width of [390, 1280]) for (const theme of ['light', 'dark']) {
        const page = await browser.newPage({ viewport: { width, height: 1000 } });
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        await page.goto(`http://127.0.0.1:${process.env.PORT || 8766}/tests/renderer/presentation.html?theme=${theme}`);
        await page.waitForFunction(() => window.ready);
        const result = await page.evaluate(async () => {
            const { runPresentationTests } = await import('./presentation-suite.js');
            return await runPresentationTests();
        });
        for (const check of result.checks) assert.ok(check.pass, `${width}/${theme}: ${check.message}`);
        assert.deepEqual(errors, [], 'no browser runtime exceptions');
        await page.screenshot({ path: `${out}/${width}-${theme}.png`, fullPage: true });
        results.push({ width, theme, ...result, errors });
        await page.close();
    }
    await writeFile(`${out}/dimensions.json`, JSON.stringify(results, null, 2));
    console.log(JSON.stringify(results, null, 2));
} finally { await browser.close(); }
