// SPDX-License-Identifier: GPL-3.0-only
// Progressive enhancement after sanitization. No source HTML is trusted here.
export function enhanceTables(root) {
    for (const table of root.querySelectorAll('table')) {
        let wrapper = table.parentElement;
        if (!wrapper?.classList.contains('table-wrapper')) {
            wrapper = table.ownerDocument.createElement('div');
            wrapper.className = 'table-wrapper';
            table.before(wrapper);
            wrapper.append(table);
        }
        wrapper.tabIndex = 0;
        wrapper.setAttribute('role', 'region');
        wrapper.setAttribute('aria-label', table.caption?.textContent?.trim() || 'Table; scroll horizontally for more columns');
        for (const th of table.querySelectorAll('thead th')) {
            if (!th.hasAttribute('scope')) th.setAttribute('scope', 'col');
        }
    }
}

/** Host adapter must resolve only after a successful native clipboard write. */
export function enhanceCodeBlocks(root, { copyText } = {}) {
    for (const pre of root.querySelectorAll('pre')) {
        // Mermaid and front-matter error output are not ordinary code blocks.
        if (pre.closest('.mermaid-block, .frontmatter-error') || pre.classList.contains('frontmatter') || pre.parentElement?.classList.contains('code-block')) continue;
        const doc = pre.ownerDocument;
        const code = pre.querySelector('code');
        if (!code) continue;
        const source = code.textContent ?? '';
        const block = doc.createElement('div');
        block.className = 'code-block';
        const toolbar = doc.createElement('div');
        toolbar.className = 'code-block-toolbar';
        const label = doc.createElement('span');
        label.className = 'code-block-language';
        const language = [...code.classList].find(name => name.startsWith('language-'))?.slice(9);
        label.textContent = language || 'Plain text';
        const status = doc.createElement('span');
        status.className = 'code-block-status';
        status.setAttribute('role', 'status');
        status.setAttribute('aria-live', 'polite');
        const wrap = doc.createElement('button');
        wrap.type = 'button';
        wrap.className = 'code-block-wrap-button';
        wrap.textContent = 'Wrap';
        wrap.setAttribute('aria-label', 'Wrap code lines');
        const initialWrap = doc.body.classList.contains('wordWrap');
        wrap.setAttribute('aria-pressed', String(initialWrap));
        wrap.addEventListener('click', () => {
            const enabled = getComputedStyle(pre).whiteSpace !== 'pre-wrap';
            block.dataset.wrap = String(enabled);
            wrap.setAttribute('aria-pressed', String(enabled));
        });
        const copy = doc.createElement('button');
        copy.type = 'button';
        copy.className = 'code-block-copy-button';
        copy.textContent = 'Copy';
        copy.setAttribute('aria-label', 'Copy code block');
        let resetTimer;
        copy.addEventListener('click', async () => {
            if (copy.disabled) return;
            clearTimeout(resetTimer);
            copy.disabled = true;
            try {
                const write = copyText ?? (globalThis.navigator?.clipboard?.writeText
                    ? text => navigator.clipboard.writeText(text) : undefined);
                if (!write) throw new Error('Clipboard unavailable');
                await write(source);
                copy.textContent = 'Copied';
                status.textContent = 'Code copied';
            } catch {
                copy.textContent = 'Copy';
                status.textContent = 'Copy unavailable — select code to copy';
            } finally {
                copy.disabled = false;
                resetTimer = setTimeout(() => {
                    copy.textContent = 'Copy';
                    status.textContent = '';
                }, 3000);
            }
        });
        pre.before(block);
        toolbar.append(label, wrap, copy);
        block.append(toolbar, pre, status);
        pre.tabIndex = 0;
        pre.setAttribute('aria-label', language ? `${language} code` : 'Code');
    }
}

export function syncCodeWrapButtons(root) {
    for (const block of root.querySelectorAll('.code-block')) {
        const pre = block.querySelector('pre');
        block.querySelector('.code-block-wrap-button')?.setAttribute('aria-pressed', String(getComputedStyle(pre).whiteSpace === 'pre-wrap'));
    }
}
