// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 DigiGate
export const defaultConfig = {
    // markdown-it — same defaults as VS Code's markdown.preview.* settings
    breaks: false,
    linkify: true,
    typographer: false,
    theme: 'system',
    frontMatter: 'table',
    math: true,
    mermaid: true,
    showToc: true,
    wordWrap: true,
    fontSize: 14,
    lineHeight: 22,
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe WPC", "Segoe UI", system-ui, "Ubuntu", "Droid Sans", sans-serif',
    mathMacros: {},
};
let current = defaultConfig;
export function getConfig() {
    return current;
}
const listeners = [];
/**
 * Notified after every `setConfig`. The host uses this to persist the configuration — the
 * renderer itself has nowhere to put it, and deliberately doesn't know where it ends up.
 */
export function onConfigChanged(listener) {
    listeners.push(listener);
}
export function setConfig(patch) {
    current = { ...current, ...patch };
    for (const listener of listeners) {
        listener(current);
    }
    return current;
}
/**
 * Narrow view handed to the engine, mirroring what `#getConfig()` returned in
 * the original `MarkdownItEngine`.
 */
export function getMarkdownItConfig() {
    const { breaks, linkify, typographer } = current;
    return { breaks, linkify, typographer };
}
// -------------------------------------------------------------------- host defaults
/**
 * Defaults supplied by the host, replacing the ones this module ships.
 *
 * `defaultConfig` above is a *desktop* default set — the font stack is Segoe-first, and 14px
 * over 22px is a mouse-and-keyboard reading size. A phone wants neither. Rather than teach
 * this module about platforms, the host says what it wants at start-up and these become the
 * baseline that stored settings are then applied over.
 *
 * Call before `setConfig(parseConfig(...))`, or the user's stored settings are overwritten by
 * the host's defaults instead of the other way round.
 */
export function setHostDefaults(defaults) {
    current = { ...current, ...defaults };
}
// ------------------------------------------------------------------ stored settings
function asBoolean(value) {
    return typeof value === 'boolean' ? value : undefined;
}
function oneOf(allowed) {
    return (value) => typeof value === 'string' && allowed.includes(value)
        ? value
        : undefined;
}
function inRange(min, max) {
    return (value) => typeof value === 'number' && Number.isFinite(value) && value >= min && value <= max
        ? value
        : undefined;
}
function nonEmptyString(value) {
    return typeof value === 'string' && value.trim().length > 0 ? value : undefined;
}
function stringRecord(value) {
    if (typeof value !== 'object' || value === null || Array.isArray(value)) {
        return undefined;
    }
    const record = {};
    for (const [key, entry] of Object.entries(value)) {
        if (typeof entry === 'string') {
            record[key] = entry;
        }
    }
    return record;
}
/**
 * One validator per setting. Everything that reaches `parseConfig` came off disk and may have
 * been hand-edited, written by an older version, or corrupted — so each value is checked on the
 * way in rather than spread over the defaults on trust. The table is exhaustive by
 * construction, so a setting cannot be added without deciding how it is validated; what does
 * and does not reach the disk is `sessionOnly` below.
 */
const validators = {
    breaks: asBoolean,
    linkify: asBoolean,
    typographer: asBoolean,
    theme: oneOf(['light', 'dark', 'system']),
    frontMatter: oneOf(['hide', 'codeBlock', 'table']),
    math: asBoolean,
    mermaid: asBoolean,
    showToc: asBoolean,
    wordWrap: asBoolean,
    // Bounds, not just types: a zero or negative size would render an unreadable document with
    // no way to fix it from inside the app.
    fontSize: inRange(6, 96),
    lineHeight: inRange(8, 200),
    fontFamily: nonEmptyString,
    mathMacros: stringRecord,
};
/**
 * Settings that deliberately do not survive a restart.
 *
 * Turning math or mermaid off is how you get at the source of a formula or a diagram that is
 * rendering badly — a per-document escape hatch, not a preference. Persisting it would leave
 * every later document silently unrendered with nothing on screen to say why: the View menu
 * builds plain items, not check items (see `menu.rs`), so the state is invisible once you have
 * forgotten setting it. Both start on, every launch.
 *
 * Skipped on the way in as well as on the way out, so a value written by an older build is
 * ignored and then dropped by the next write.
 */
const sessionOnly = new Set(['math', 'mermaid']);
/** The settings that travel to disk and back: everything validated, less the session-only ones. */
const persistedKeys = Object.keys(validators)
    .filter(key => !sessionOnly.has(key));
/**
 * Turns stored JSON into a patch for `setConfig`. Unknown keys are dropped and invalid values
 * fall back to the default for that one setting, so a single bad entry costs only itself.
 */
export function parseConfig(raw) {
    if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) {
        return {};
    }
    const stored = raw;
    const patch = {};
    for (const key of persistedKeys) {
        if (!(key in stored)) {
            continue;
        }
        const validate = validators[key];
        const value = validate(stored[key]);
        if (value !== undefined) {
            patch[key] = value;
        }
    }
    return patch;
}
/** The persisted settings as a plain, JSON-safe object, ready to hand to the host. */
export function serializeConfig() {
    const settings = {};
    for (const key of persistedKeys) {
        // The macro table is the one setting held as an object, and is copied so the host
        // cannot reach back into the live configuration through it.
        settings[key] = key === 'mathMacros' ? { ...current.mathMacros } : current[key];
    }
    return settings;
}
//# sourceMappingURL=config.js.map