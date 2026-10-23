// Remote browser regression runner; uses the same pinned Playwright module as presentation.e2e.
import assert from 'node:assert/strict';
import { writeFile, mkdir } from 'node:fs/promises';
const {chromium} = await import(process.env.PLAYWRIGHT_MODULE || 'playwright-core');
const browser = await chromium.launch({...(process.env.CHROMIUM ? {executablePath:process.env.CHROMIUM}:{}),headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
const out=process.env.EVIDENCE_DIR || '/tmp/mark-reader-presentation-evidence';
await mkdir(out,{recursive:true});
const report=[];
try {
 for(const width of [390,1280]) {
  for(const [path,key,flag] of [['tests/renderer/math.html','mathTestResult','passed'],['scripts/webview-toc-browser.html','tocTestResult','pass']]) {
   const page=await browser.newPage({viewport:{width,height:900}});
   const errors=[]; page.on('pageerror',e=>errors.push(e.message));
   await page.goto(`http://127.0.0.1:${process.env.PORT || 8766}/${path}`);
   await page.waitForFunction(name=>Boolean(window[name]),key);
   const result=await page.evaluate(name=>window[name],key);
   assert.equal(result[flag],true,JSON.stringify(result));
   assert.deepEqual(errors,[]);
   await page.screenshot({path:`${out}/${key}-${width}.png`,fullPage:true});
   report.push({width,key,result}); await page.close();
  }
 }
 await writeFile(`${out}/math-toc-results.json`,JSON.stringify(report,null,2));
 console.log(JSON.stringify(report,null,2));
}finally{await browser.close();}
