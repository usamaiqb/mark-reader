import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
const base = new URL('../', import.meta.url);
const read = path => readFileSync(new URL(path, base));
const config = await import(`data:text/javascript;base64,${read('app/src/main/assets/renderer/config.js').toString('base64')}`);
test('code preferences survive strict page configuration parsing', () => {
  const patch = config.parseConfig({codeFontFamily:'"Roboto Mono", monospace',sourceCode:true});
  assert.equal(patch.codeFontFamily, '"Roboto Mono", monospace');
  assert.equal(patch.sourceCode, true);
  assert.equal(config.parseConfig({sourceCode:'true'}).sourceCode, undefined);
  assert.notEqual(config.parseConfig({codeFontFamily:42}).codeFontFamily, 42);
});
test('all five browser fonts are byte-identical to licensed native fonts', () => {
  const names = ['merriweather_regular','merriweather_bold','merriweather_sans_regular','merriweather_sans_bold','jetbrains_mono_regular'];
  const css = read('app/src/main/assets/renderer/css/fonts.css').toString();
  for (const name of names) {
    const native = read(`app/src/main/res/font/${name}.ttf`);
    const browser = read(`app/src/main/assets/renderer/fonts/${name}.ttf`);
    assert.equal(createHash('sha256').update(browser).digest('hex'), createHash('sha256').update(native).digest('hex'));
    assert.ok(browser.length > 1000);
    assert.ok(css.includes(`../fonts/${name}.ttf`));
  }
});
