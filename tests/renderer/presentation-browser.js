import { DocumentView } from '../../app/src/main/assets/renderer/document.js';
import { setConfig } from '../../app/src/main/assets/renderer/config.js';
import { setDocumentHtml } from '../../app/src/main/assets/renderer/sanitizer.js';
import { enhanceCodeBlocks, enhanceTables } from '../../app/src/main/assets/renderer/presentation.js';
const params = new URLSearchParams(location.search);
const config = setConfig({ theme: params.get('theme') || 'light', math: false, mermaid: false, codeFontFamily: '"JetBrains Mono", monospace' });
// Mirrors Android applyTypography: body variables must beat theme.css's body defaults.
document.body.style.setProperty('--vscode-editor-font-family', config.codeFontFamily);
const root = document.querySelector('main');
window.copyCalls = [];
window.copyMode = 'success';
const host = { copyText: async text => {
    if (window.copyMode === 'failure') throw new Error('Native clipboard denied');
    window.copyCalls.push(text);
} };
const view = new DocumentView({ root, host });
window.view = view;
view.applyTheme();
view.setWordWrap(params.get('wrap') === 'true');
const source = await (await fetch('./fixtures/presentation.md')).text();
await view.setDocumentAsync({ path: '/fixture.md', text: source }, {});
window.helpers = { enhanceCodeBlocks, enhanceTables, setDocumentHtml };
await document.fonts.load('16px "JetBrains Mono"');
await document.fonts.ready;
window.ready = true;
