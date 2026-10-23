// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 DigiGate
// Parse at Markdown token boundaries; never rewrite source or generated HTML.
// Undelimited inference needs a known TeX command, a power/subscript, or a spaced
// relation. Plain words, code, paths, links and unspaced prose such as a+b=2 stay literal.
const MAX_BARE_LENGTH = 2048;
const COMMANDS = new Set(('implies dots ldots cdots vdots ddots alpha beta gamma delta epsilon varepsilon zeta eta theta vartheta iota kappa lambda mu nu xi pi varpi rho varrho sigma varsigma tau upsilon phi varphi chi psi omega Gamma Delta Theta Lambda Xi Pi Sigma Upsilon Phi Psi Omega sin cos tan cot sec csc arcsin arccos arctan sinh cosh tanh log ln exp lim min max sup inf frac dfrac tfrac sqrt left right middle infty cdot times div pm mp le leq ge geq ne neq approx equiv sim simeq cong propto perp parallel angle degree in notin ni subset supset subseteq supseteq cup cap emptyset varnothing forall exists neg land lor to mapsto rightarrow leftarrow Rightarrow Leftrightarrow iff sum prod int iint iiint oint partial nabla vec overline underline hat widehat widetilde tilde bar dot ddot mathbb mathcal mathrm mathbf mathit mathsf mathtt text textrm textbf operatorname limits nolimits binom dbinom tbinom det gcd mod bmod pmod lvert rvert lVert rVert langle rangle').split(' '));
const GROUP_TEXT = new Set('text textrm textbf operatorname mathbb mathcal mathrm mathbf mathit mathsf mathtt'.split(' '));
export function hasTexSyntax(text) {
    return /\\(?:[([]|%|[A-Za-z]+)|[A-Za-z0-9)}][\^_]|\S[ \t]+[=<>][ \t]+\S/.test(text);
}
function escaped(src, pos) {
    let n = 0;
    while (pos > 0 && src[--pos] === '\\') n++;
    return n % 2 === 1;
}
function push(state, silent, content, end, display = false) {
    if (!silent) {
        const token = state.push(display ? 'math_block' : 'math_inline', 'math', 0);
        token.content = content;
        token.block = display;
        token.markup = display ? '\\[' : '\\(';
    }
    state.pos = end;
    return true;
}
const missingClosers = new WeakMap();
function hasCloser(state, closer, start) {
    let missing = missingClosers.get(state);
    if (!missing) { missing = new Set(); missingClosers.set(state, missing); }
    if (missing.has(closer)) return false;
    if (state.src.indexOf(closer, start) !== -1) return true;
    missing.add(closer);
    return false;
}
function explicitTex(state, silent) {
    const start = state.pos;
    const opener = state.src.slice(start, start + 2);
    if (opener !== '\\(' && opener !== '\\[') return false;
    const close = opener === '\\(' ? '\\)' : '\\]';
    if (!hasCloser(state, close, start + 2)) return false;
    let end = start + 2;
    while ((end = state.src.indexOf(close, end)) !== -1 && end < state.posMax) {
        if (!escaped(state.src, end)) {
            const content = state.src.slice(start + 2, end);
            if (!content.trim()) return false;
            return push(state, silent, content, end + 2, opener === '\\[');
        }
        end += 2;
    }
    return false;
}
// Cache the next evidence position: scanning long plain text must not repeatedly
// search its whole suffix. Each state owns its cache, including nested link parsing.
const signalCache = new WeakMap();
function nextSignal(state) {
    let cached = signalCache.get(state);
    if (!cached || cached.pos !== -1 && cached.pos < state.pos) {
        const re = /\\|[\^_]|[ \t][=<>][ \t]/g;
        re.lastIndex = state.pos;
        const match = re.exec(state.src);
        cached = { pos: match ? match.index : -1 };
        signalCache.set(state, cached);
    }
    return cached.pos;
}
const rejectedSpans = new WeakMap();
function bareTex(state, silent) {
    if (state.linkLevel > 0) return false;
    const src = state.src, start = state.pos;
    if (start < (rejectedSpans.get(state) || 0)) return false;
    if (start && /[\w\\/:]/.test(src[start - 1])) return false;
    if (!/[A-Za-z0-9(\\]/.test(src[start] || '')) return false;
    const signal = nextSignal(state);
    if (signal === -1 || signal >= start + MAX_BARE_LENGTH) return false;
    let pos = start, command = false, ascii = false;
    const stack = [];
    const scanEnd = Math.min(state.posMax, start + MAX_BARE_LENGTH);
    while (pos < scanEnd) {
        const ch = src[pos];
        if (ch === '\\') {
            if (src[pos + 1] === '{') { stack.push('escaped'); pos += 2; continue; }
            if (src[pos + 1] === '}') { if (stack.at(-1) !== 'escaped') break; stack.pop(); pos += 2; continue; }
            if (/[%!,;: ]/.test(src[pos + 1] || '')) {
                if (src[pos + 1] === '%') command = true;
                pos += 2; continue;
            }
            const match = /^\\([A-Za-z]+)/.exec(src.slice(pos, scanEnd));
            if (!match || !COMMANDS.has(match[1])) break;
            command = true;
            pos += match[0].length;
            // Text/font/operator arguments have their own grammar: Chinese prose,
            // multi-letter names, escaped punctuation and nested balanced groups.
            if (GROUP_TEXT.has(match[1])) {
                if (src[pos] === '*' && match[1] === 'operatorname') pos++;
                while (/[ \t]/.test(src[pos] || 'x')) pos++;
                if (src[pos] !== '{') return false;
                let depth = 1; pos++;
                while (pos < scanEnd && depth) {
                    if (src[pos] === '\\') { pos += 2; continue; }
                    if (src[pos] === '{') depth++;
                    if (src[pos] === '}') depth--;
                    pos++;
                }
                if (depth) return false;
            }
        } else if (/[A-Za-z]/.test(ch)) {
            const word = /^[A-Za-z]+/.exec(src.slice(pos))[0];
            if (word.length !== 1) break;
            pos++;
        } else if (/[0-9]/.test(ch)) {
            pos += /^\d+(?:\.\d+)?/.exec(src.slice(pos))[0].length;
        } else if (ch === '{' || ch === '(' || ch === '[') { stack.push(ch); pos++; }
        else if (ch === '}' || ch === ')' || ch === ']') {
            const open = { '}': '{', ')': '(', ']': '[' }[ch];
            // Intervals may mix round/square endpoints.
            if (stack.at(-1) !== open && !(')]'.includes(ch) && ['(', '['].includes(stack.at(-1)))) break;
            stack.pop(); pos++;
        } else if (ch === ',' && stack.length) pos++;
        else if (ch === '^' || ch === '_') { ascii = true; pos++; }
        else if (/[=<>]/.test(ch) && /[ \t]/.test(src[pos - 1] || '') && /[ \t]/.test(src[pos + 1] || '')) { ascii = true; pos++; }
        else if (/[ \t=+\-*/!>']/.test(ch)) pos++;
        else if (ch === '<' && !/[A-Za-z/!]/.test(src[pos + 1] || '')) pos++;
        else break;
    }
    if ((pos >= scanEnd || stack.length && pos >= scanEnd - 32) && scanEnd < state.posMax) {
        // Reject this bounded window once. Retrying every suffix of the same
        // malformed/oversized run otherwise costs O(n * MAX_BARE_LENGTH).
        rejectedSpans.set(state, scanEnd);
        return false;
    }
    if ((!command && !ascii) || stack.length) return false;
    let content = src.slice(start, pos).trimEnd();
    content = content.replace(/[ \t=+\-*/^_<>]+$/, '');
    if (!content || !hasTexSyntax(content)) return false;
    // An incomplete command argument must not become an error-colored partial formula.
    if (/\\(?:frac|dfrac|tfrac|sqrt|binom|left|right)\s*$/.test(content)) return false;
    return push(state, silent, content, start + content.length);
}
// Whitespace and a digit after the closer indicate ordinary prices, not math.
function dollars(state, silent) {
    const src = state.src, start = state.pos;
    if (src[start] !== '$' || src[start + 1] === '$') return false;
    if (/\s/.test(src[start + 1] || ' ') || escaped(src, start)) return false;
    let end = start + 1;
    while ((end = src.indexOf('$', end)) !== -1 && end < state.posMax) {
        if (src.slice(start, end).includes('\n')) return false;
        if (!escaped(src, end) && src[end + 1] !== '$' && !/\s/.test(src[end - 1]) && !/\d/.test(src[end + 1] || '')) {
            return push(state, silent, src.slice(start + 1, end), end + 1);
        }
        if (src[end + 1] === '$') return false;
        end++;
    }
    return false;
}
// A real block rule is required: inline parsing has already lost blank lines and
// interpreted alignment rows as Markdown. Unlike the vendor, require a closer so
// an unfinished $$ cannot consume the rest of the document as mathematics.
function displayBlock(state, startLine, endLine, silent) {
    const start = state.bMarks[startLine] + state.tShift[startLine];
    if (state.sCount[startLine] - state.blkIndent >= 4) return false;
    const opener = state.src.slice(start, start + 2);
    if (opener !== '$$' && opener !== '\\[') return false;
    const closer = opener === '$$' ? '$$' : '\\]';
    if (!hasCloser(state, closer, start + 2)) return false;
    for (let line = startLine; line < endLine; line++) {
        const begin = line === startLine ? start + 2 : state.bMarks[line] + state.tShift[line];
        const end = state.eMarks[line];
        if (line > startLine && begin < end && state.tShift[line] < state.blkIndent) return false;
        let pos = state.src.indexOf(closer, begin);
        while (pos !== -1 && pos < end) {
            if (!escaped(state.src, pos) && !state.src.slice(pos + 2, end).trim()) {
                if (silent) return true;
                let content = state.src.slice(start + 2, state.eMarks[startLine]);
                if (line === startLine) content = state.src.slice(start + 2, pos) + (opener === '$$' ? '\n' : '');
                else content = (content.trim() ? content + '\n' : '') + state.getLines(startLine + 1, line, state.blkIndent, true) + state.src.slice(begin, pos);
                if (!content.trim()) return false;
                const token = state.push('math_block', 'math', 0);
                token.block = true; token.content = content; token.markup = opener;
                token.map = [startLine, line + 1]; state.line = line + 1;
                return true;
            }
            pos = state.src.indexOf(closer, pos + 2);
        }
    }
    return false;
}
export function extendedMathPlugin(md) {
    const textRule = md.inline.ruler.__rules__.find(rule => rule.name === 'text').fn;
    md.inline.ruler.at('text', (state, silent) => {
        const start = state.pos, pending = state.pending;
        if (!textRule(state, silent)) return false;
        const end = state.pos, nextPending = state.pending;
        state.pos = start;
        const signal = nextSignal(state);
        if (signal !== -1 && signal <= end + MAX_BARE_LENGTH) {
            for (let pos = Math.max(start + 1, signal - MAX_BARE_LENGTH + 1); pos < end; pos++) {
                if (!/[A-Za-z0-9(]/.test(state.src[pos])) continue;
                state.pos = pos;
                if (bareTex(state, true)) {
                    state.pos = pos; state.pending = pending + state.src.slice(start, pos);
                    return true;
                }
            }
        }
        state.pos = end; state.pending = nextPending;
        return true;
    });
    md.inline.ruler.before('escape', 'tex-delimiters', explicitTex);
    md.inline.ruler.before('text', 'bare-tex', bareTex);
    md.inline.ruler.at('math_inline', dollars);
    md.block.ruler.at('math_block', displayBlock, { alt: ['paragraph', 'reference', 'blockquote', 'list'] });
    const renderCode = md.renderer.rules.code_block;
    md.renderer.rules.code_block = (tokens, index, options, env, renderer) => {
        const lines = tokens[index].content.trimEnd().split('\n');
        const copiedListProse = lines.length > 0 && lines.every(line => /^\s*(?:\*\s+){2,}[\u3400-\u9fff]/u.test(line));
        if (copiedListProse) {
            return '<p class="copied-list-prose">' + lines.map(line => md.renderInline(line.replace(/^\s*(?:\*\s+){2,}/, ''), env)).join('<br>') + '</p>\n';
        }
        return renderCode ? renderCode(tokens, index, options, env, renderer) : renderer.renderToken(tokens, index, options);
    };
    return md;
}
