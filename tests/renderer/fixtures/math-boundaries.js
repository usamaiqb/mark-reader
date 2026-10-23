// SPDX-License-Identifier: GPL-3.0-only
// Synthetic adversarial cases, not replacements for absent equations in user data.
export const mathBoundaries = [
    ['ASCII powers from original answer', String.raw`虚数 i^1 = i, i^2 = -1, i^3 = -i, i^4 = 1。`, ['i^1 = i', 'i^2 = -1', 'i^3 = -i', 'i^4 = 1'], '虚数 , , , 。'],
    ['ASCII sum from original answer', String.raw`最后 9i^9 + 10i^{10} = 9i - 10。`, ['9i^9 + 10i^{10} = 9i - 10'], '最后 。'],
    ['spaced relation and Chinese', '解得 m = 2 或 m = -1。', ['m = 2', 'm = -1'], '解得  或 。'],
    ['chained derivative', String.raw`导数 f''(x) = 6x - 6 = 0，结束。`, ["f''(x) = 6x - 6 = 0"], '导数 ，结束。'],
    ['font and multi-letter operator', String.raw`空间 \mathbb{R} 与 \operatorname{rank}(A) = 2。`, [String.raw`\mathbb{R}`, String.raw`\operatorname{rank}(A) = 2`], '空间  与 。'],
    ['Chinese text argument', String.raw`概率 \text{成功概率} = \frac{1}{2}。`, [String.raw`\text{成功概率} = \frac{1}{2}`], '概率 。'],
    ['nested text and escaped punctuation', String.raw`结论 \text{rate {net} \%} = 50\%。`, [String.raw`\text{rate {net} \%} = 50\%`], '结论 。'],
    ['binomial command', String.raw`值 \binom{n}{k} = \frac{n!}{k!(n-k)!}。`, [String.raw`\binom{n}{k} = \frac{n!}{k!(n-k)!}`], '值 。'],
    ['sqrt and fraction', String.raw`根 \sqrt[3]{x} + \dfrac{1}{2} = y。`, [String.raw`\sqrt[3]{x} + \dfrac{1}{2} = y`], '根 。'],
    ['parenthesized inline', String.raw`前\(\frac{1}{2} + x^2\)后`, [String.raw`\frac{1}{2} + x^2`], '前后'],
    ['display aligned blank line', '\\[\n\\begin{aligned}\nx &= 1 \\\\\n\ny &= 2\n\\end{aligned}\n\\]', ['\\begin{aligned}\nx &= 1 \\\\\n\ny &= 2\n\\end{aligned}\n'], ''],
    ['display cases', '$$\nf(x)=\\begin{cases}x^2 & x>0 \\\\ -x & x\\le 0\\end{cases}\n$$', ['f(x)=\\begin{cases}x^2 & x>0 \\\\ -x & x\\le 0\\end{cases}\n'], ''],
    ['emphasis boundary', String.raw`前 **\alpha = 2** 后 *i^2 = -1*。`, [String.raw`\alpha = 2`, 'i^2 = -1'], '前  后 。'],
    ['table cells', '| 项 | 值 |\n| --- | --- |\n| A | \\frac{1}{2} |\n| B | i^2 = -1 |', [String.raw`\frac{1}{2}`, 'i^2 = -1'], '项值AB'],
    ['escaped punctuation explicit', String.raw`前\(\text{a\_b\#c\&d\$} + 50\%\)后`, [String.raw`\text{a\_b\#c\&d\$} + 50\%`], '前后'],
    ['multiline inline', '\\(x^2 +\ny^2 = 1\\)', ['x^2 +\ny^2 = 1'], ''],
    ['inline display with prose', String.raw`前 \[x^2 = 1\] 后`, ['x^2 = 1'], '前  后'],
    ['aligned dollar display', '$$\n\\begin{aligned}x &= 1 \\\\ y &= 2\\end{aligned}\n$$', ['\\begin{aligned}x &= 1 \\\\ y &= 2\\end{aligned}\n'], ''],
    ['unclosed display preserves prose', '$$\nx=1\n后续普通正文', [], '$$ x=1 后续普通正文'],
    ['unclosed brackets preserve prose', '\\[\nx=1\n后续普通正文', [], '[ x=1 后续普通正文'],
];
