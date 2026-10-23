import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const source = readFileSync(new URL('../app/src/main/assets/renderer/clipboard.js', import.meta.url));
const {createClipboardBridge} = await import(`data:text/javascript;base64,${source.toString('base64')}`);
test('copy waits for matching successful native acknowledgement and preserves bytes', async () => {
  let sent; let done=false;
  const bridge=createClipboardBridge((id,text)=>sent={id,text});
  const promise=bridge.copyText('\tcode  \n').then(()=>done=true);
  await Promise.resolve(); assert.equal(done,false); assert.equal(sent.text,'\tcode  \n');
  bridge.complete({id:'wrong',ok:true}); await Promise.resolve(); assert.equal(done,false);
  bridge.complete(JSON.stringify({id:sent.id,ok:true})); await promise; assert.equal(done,true);
  bridge.dispose();
});
test('rejected writes and timeout never resolve as success', async () => {
  let id; const bridge=createClipboardBridge(value=>id=value,{timeoutMs:10,maxLength:10});
  const failed=assert.rejects(bridge.copyText('text'),/failed/);
  bridge.complete({id,ok:false}); await failed;
  await assert.rejects(bridge.copyText('timeout'),/timeout/);
  await assert.rejects(bridge.copyText('x'.repeat(11)),/large/);
  bridge.dispose();
});
test('disposal rejects pending writes and malformed acknowledgements are ignored', async () => {
  const bridge=createClipboardBridge(()=>{});
  const pending=assert.rejects(bridge.copyText('text'),/closed/);
  bridge.complete('{bad'); bridge.complete(null); bridge.dispose(); await pending;
});
