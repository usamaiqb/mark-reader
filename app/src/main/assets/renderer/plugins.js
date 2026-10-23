/*---------------------------------------------------------------------------------------------
 *  SPDX-License-Identifier: GPL-3.0-only
 *  SPDX-FileCopyrightText: 2026 DigiGate
 *  SPDX-FileCopyrightText: Microsoft Corporation — MIT, see licenses/vscode.txt
 *
 *  Contains code derived from Visual Studio Code. See licenses/NOTICE.md for what this file
 *  derives from and how much it changed.
 *--------------------------------------------------------------------------------------------*/
import { getConfig } from './config.js';
import { extendedMathPlugin } from './math.js';
/**
 * KaTeX, loaded only once a document turns out to contain math — it is one of the two heaviest
 * things in the graph. See `lazy.ts` for why that decision is made before rendering rather
 * than after. Until it has loaded, `getPlugins()` leaves math out and `$x$` renders as the
 * text it is.
 */
let katexPlugin;
export function isMathPluginLoaded() {
    return katexPlugin !== undefined;
}
export async function loadMathPlugin() {
    if (katexPlugin) {
        return;
    }
    // The package is CommonJS, and a dynamic import of it does not interop the way the static
    // one did: depending on the bundler, `default` is either the plugin or the module object
    // that holds it. Unwrapping one level covers both, and the alternative is a
    // `plugin.apply is not a function` that only shows up in the bundled build.
    const imported = (await import('./vendor/markdown-it-katex.js')).default;
    const plugin = typeof imported === 'function'
        ? imported
        : imported?.default;
    if (typeof plugin !== 'function') {
        throw new TypeError('@vscode/markdown-it-katex did not export a plugin function');
    }
    katexPlugin = plugin;
}
/**
 * Ported from vscode/extensions/markdown-math/src/extension.ts.
 * The macro table is reset per render so a `\newcommand` in one document can't
 * leak into the next.
 */
function mathPlugin(md) {
    if (!katexPlugin) {
        return md;
    }
    const options = {
        enableFencedBlocks: true,
        globalGroup: true,
        trust: false,
        maxExpand: 1000,
        maxSize: 100,
        throwOnError: false,
        macros: { ...getConfig().mathMacros },
    };
    md.core.ruler.push('reset-katex-macros', () => {
        options.macros = { ...getConfig().mathMacros };
        return true;
    });
    return md.use(katexPlugin, options).use(extendedMathPlugin);
}
/**
 * Renders `- [ ]` / `- [x]` list items as checkboxes.
 *
 * NOTE: this is a MarkReader addition, not a port. VS Code's preview has no task-list
 * plugin, so it renders the literal text `[x]` — fine in an editor next to the source,
 * misleading in a standalone reader.
 */
function taskListPlugin(md) {
    md.core.ruler.after('inline', 'task-lists', state => {
        for (let i = 0; i < state.tokens.length - 2; i++) {
            const open = state.tokens[i];
            const inline = state.tokens[i + 2];
            if (open.type !== 'list_item_open' || inline.type !== 'inline') {
                continue;
            }
            const match = /^\[([ xX])\]\s+/.exec(inline.content);
            if (!match) {
                continue;
            }
            const checked = match[1].toLowerCase() === 'x';
            inline.content = inline.content.slice(match[0].length);
            // The inline token has already been parsed into children, so the marker has to
            // be stripped from the first text child too.
            const firstText = inline.children?.find(c => c.type === 'text');
            if (firstText) {
                firstText.content = firstText.content.replace(/^\[([ xX])\]\s+/, '');
            }
            const checkbox = new state.Token('html_inline', '', 0);
            checkbox.content = `<input type="checkbox" disabled${checked ? ' checked' : ''}>`;
            inline.children?.unshift(checkbox);
            open.attrJoin('class', 'task-list-item');
        }
        return true;
    });
    return md;
}
/**
 * The set of markdown-it plugins for the current configuration.
 * Changing this requires `engine.reloadPlugins()`.
 */
export function getPlugins() {
    const plugins = [taskListPlugin];
    // Both conditions: the setting is the user's, and the load is the document's.
    if (getConfig().math && isMathPluginLoaded()) {
        plugins.push(mathPlugin);
    }
    // Mermaid is not a markdown-it plugin here — the engine emits a placeholder for
    // ```mermaid fences and the renderer hydrates them after layout. See engine.ts.
    return plugins;
}
//# sourceMappingURL=plugins.js.map