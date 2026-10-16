/*---------------------------------------------------------------------------------------------
 *  SPDX-License-Identifier: GPL-3.0-only
 *  SPDX-FileCopyrightText: 2026 DigiGate
 *  SPDX-FileCopyrightText: Microsoft Corporation — MIT, see licenses/vscode.txt
 *
 *  Contains code derived from Visual Studio Code. See licenses/NOTICE.md for what this file
 *  derives from and how much it changed.
 *--------------------------------------------------------------------------------------------*/
// Ported from vscode/extensions/markdown-language-features/src/extensions/yamlPreamble/yamlPreamble.ts
//
// Changes from the original:
//   - `vscode.workspace.getConfiguration('markdown').get('preview.frontMatter')` -> getConfig()
//   - `vscode.l10n.t(...)` -> plain strings
//   - dropped the `data-vscode-context` attribute (webview context menus only)
import * as yaml from './vendor/yaml.js';
import { escapeHtml } from './util.js';
import { getConfig } from './config.js';
const FRONT_MATTER_TOKEN = 'front_matter';
const MARKER = '---';
/**
 * Extends a `markdown-it` instance with parsing and rendering support for YAML
 * frontmatter at the start of a Markdown document.
 */
export function extendMarkdownIt(md) {
    md.block.ruler.before('fence', FRONT_MATTER_TOKEN, frontMatterRule, {
        alt: ['paragraph', 'reference', 'blockquote', 'list']
    });
    md.renderer.rules[FRONT_MATTER_TOKEN] = renderFrontMatter;
    return md;
}
const frontMatterRule = (state, startLine, endLine, silent) => {
    if (startLine !== 0 || state.tShift[startLine] !== 0) {
        return false;
    }
    const firstLineStart = state.bMarks[startLine];
    const firstLineEnd = state.eMarks[startLine];
    const firstLine = state.src.slice(firstLineStart, firstLineEnd).replace(/\s+$/, '');
    if (firstLine !== MARKER) {
        return false;
    }
    let nextLine = startLine + 1;
    let foundEnd = false;
    for (; nextLine < endLine; nextLine++) {
        if (state.tShift[nextLine] !== 0) {
            continue;
        }
        const lineStart = state.bMarks[nextLine];
        const lineEnd = state.eMarks[nextLine];
        const line = state.src.slice(lineStart, lineEnd).replace(/\s+$/, '');
        if (line === MARKER) {
            foundEnd = true;
            break;
        }
    }
    if (!foundEnd) {
        return false;
    }
    if (silent) {
        return true;
    }
    const contentStart = state.bMarks[startLine + 1];
    const contentEnd = state.bMarks[nextLine];
    const rawContent = state.src.slice(contentStart, contentEnd).replace(/\n$/, '');
    const token = state.push(FRONT_MATTER_TOKEN, '', 0);
    token.block = true;
    token.hidden = false;
    token.markup = MARKER;
    token.map = [startLine, nextLine + 1];
    const meta = { content: rawContent };
    token.meta = meta;
    state.line = nextLine + 1;
    return true;
};
function renderFrontMatter(tokens, idx, options) {
    const meta = tokens[idx].meta;
    if (!meta) {
        return '';
    }
    const style = getConfig().frontMatter;
    switch (style) {
        case 'codeBlock':
            return renderAsCodeBlock(meta, options);
        case 'table':
            return renderAsTable(meta);
        case 'hide':
        default:
            return '';
    }
}
function renderAsCodeBlock(meta, options) {
    let highlighted;
    if (typeof options.highlight === 'function') {
        try {
            highlighted = options.highlight(meta.content, 'yaml', '') || undefined;
        }
        catch {
            highlighted = undefined;
        }
    }
    if (highlighted?.startsWith('<pre')) {
        return highlighted.replace(/^<pre\b/, `<pre ${frontMatterAttributes()}`) + '\n';
    }
    const body = highlighted ?? escapeHtml(meta.content);
    return `<pre class="frontmatter hljs" ${frontMatterAttributes()}><code class="language-yaml">${body}</code></pre>\n`;
}
function renderAsTable(meta) {
    const result = parseEntries(meta);
    if (result.error !== undefined) {
        return renderError(result.error);
    }
    if (!result.entries.length) {
        return '';
    }
    const rows = result.entries.map(([key, value]) => `<tr><th>${escapeHtml(key)}</th><td>${formatValueHtml(value)}</td></tr>`).join('');
    return `<table class="frontmatter" ${frontMatterAttributes()}><tbody>${rows}</tbody></table>\n`;
}
function renderError(message) {
    return `<div class="frontmatter-error" role="alert" ${frontMatterAttributes()}><strong>Failed to parse frontmatter</strong><pre>${escapeHtml(message)}</pre></div>\n`;
}
function frontMatterAttributes() {
    return `title="Frontmatter"`;
}
function parseEntries(meta) {
    try {
        const parsed = yaml.parse(meta.content);
        if (parsed === null || parsed === undefined) {
            return { entries: [] };
        }
        if (typeof parsed !== 'object' || Array.isArray(parsed)) {
            return { entries: [['', parsed]] };
        }
        return { entries: Object.entries(parsed) };
    }
    catch (e) {
        return { entries: [], error: e instanceof Error ? e.message : String(e) };
    }
}
function formatValueHtml(value) {
    if (value === null || value === undefined) {
        return '';
    }
    if (Array.isArray(value)) {
        if (!value.length) {
            return '';
        }
        return `<ul>${value.map(v => `<li>${formatValueHtml(v)}</li>`).join('')}</ul>`;
    }
    if (typeof value === 'object') {
        return `<code>${escapeHtml(yaml.stringify(value).trimEnd())}</code>`;
    }
    return escapeHtml(formatScalar(value));
}
function formatScalar(value) {
    if (value instanceof Date) {
        return value.toISOString();
    }
    return String(value);
}
//# sourceMappingURL=frontMatter.js.map