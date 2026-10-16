# Third-Party Licenses

MarkReader is licensed under the GNU General Public License v3.0 (`LICENSE`). It redistributes
the material below, which keeps its own licence.

# Libraries

## Markwon

- Artifacts: `io.noties.markwon:core`, `ext-tables`, `syntax-highlight`, `image`
- Source: https://github.com/noties/Markwon
- License: Apache License, Version 2.0 ([full text](APACHE-2.0.txt))
- Copyright: Copyright 2019 Dimitry Ivanov (legal@noties.io)

## Prism4j

- Artifact: `io.noties:prism4j`
- Source: https://github.com/noties/Prism4j
- License: Apache License, Version 2.0 ([full text](APACHE-2.0.txt))
- Copyright: Copyright 2019 Dimitry Ivanov (legal@noties.io)

# Fonts

MarkReader includes the following third-party font files in
`app/src/main/res/font`.

## Merriweather

- Files: `merriweather_regular.ttf`, `merriweather_bold.ttf`
- Source: https://github.com/SorkinType/Merriweather
- License: SIL Open Font License, Version 1.1 ([full text](OFL-1.1.txt))
- Copyright: Copyright 2020 The Merriweather Project Authors
  (https://github.com/SorkinType/Merriweather/), with Reserved Font Name
  "Merriweather".

## Merriweather Sans

- Files: `merriweather_sans_regular.ttf`, `merriweather_sans_bold.ttf`
- Source: https://github.com/SorkinType/Merriweather-Sans
- License: SIL Open Font License, Version 1.1 ([full text](OFL-1.1.txt))
- Copyright: Copyright 2019 The Merriweather Project Authors
  (https://github.com/SorkinType/Merriweather-Sans)

## JetBrains Mono

- File: `jetbrains_mono_regular.ttf`
- Source: https://github.com/JetBrains/JetBrainsMono
- License: SIL Open Font License, Version 1.1 ([full text](OFL-1.1.txt))
- Copyright: Copyright 2020 The JetBrains Mono Project Authors
  (https://github.com/JetBrains/JetBrainsMono)

# Document renderer

`app/src/main/assets/renderer` is generated output copied from the `markreader-desktop` repo;
`MANIFEST.md` beside it records the provenance. Its own code is GPL-3.0-only.

## Visual Studio Code

- Files: `css/markdown.css`, `css/highlight.css`, `slugify.js`, and parts of `engine.js`,
  `frontMatter.js`, `plugins.js`, `document.js`, `util.js`
- Source: https://github.com/microsoft/vscode
- License: MIT
- Copyright: Copyright (c) Microsoft Corporation. All rights reserved.

## Bundled libraries

Files: `vendor/*.js`, and `css/katex.css` for KaTeX.

| Package | License | Copyright |
| --- | --- | --- |
| `markdown-it` | MIT | Copyright (c) 2014 Vitaly Puzrin, Alex Kocharin |
| `@vscode/markdown-it-katex` | MIT | Copyright (c) Microsoft Corporation |
| `katex` | MIT | Copyright (c) 2013-2020 Khan Academy and other contributors |
| `mermaid` | MIT | Copyright (c) 2014 - 2022 Knut Sveidqvist |
| `highlight.js` | BSD-3-Clause | Copyright (c) 2006, Ivan Sagalaev |
| `yaml` | ISC | Copyright Eemeli Aro <eemeli@gmail.com> |
| `DOMPurify` | Apache-2.0 | Copyright Dr.-Ing. Mario Heiderich, Cure53 |

DOMPurify is dual-licensed Apache-2.0 or MPL-2.0, and is taken here under Apache-2.0. That
license requires its full text to accompany the work; it is in [`APACHE-2.0.txt`](APACHE-2.0.txt)
beside this file rather than inline, being longer than everything else here combined.

## MIT License

    Permission is hereby granted, free of charge, to any person obtaining a copy of this
    software and associated documentation files (the "Software"), to deal in the Software
    without restriction, including without limitation the rights to use, copy, modify, merge,
    publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons
    to whom the Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all copies or
    substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
    INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
    PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
    FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
    OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
    DEALINGS IN THE SOFTWARE.

## BSD 3-Clause License

    Redistribution and use in source and binary forms, with or without modification, are
    permitted provided that the following conditions are met:

        * Redistributions of source code must retain the above copyright notice, this list of
          conditions and the following disclaimer.
        * Redistributions in binary form must reproduce the above copyright notice, this list
          of conditions and the following disclaimer in the documentation and/or other
          materials provided with the distribution.
        * Neither the name of the copyright holder nor the names of its contributors may be
          used to endorse or promote products derived from this software without specific
          prior written permission.

    THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
    IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
    MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
    REGENTS AND CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
    EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
    SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
    HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
    TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
    SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

## ISC License

    Permission to use, copy, modify, and/or distribute this software for any purpose with or
    without fee is hereby granted, provided that the above copyright notice and this permission
    notice appear in all copies.

    THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO
    THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT
    SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR
    ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF
    CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE
    OR PERFORMANCE OF THIS SOFTWARE.
