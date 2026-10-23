# Mobile presentation fixture

Tables keep their columns readable. Swipe the table, not the document.

| Feature | Details / long content | Status | Example |
| :--- | :--- | :---: | ---: |
| Readability | A long sentence should wrap within a sensible column width instead of making every cell a narrow vertical ribbon. 支持中文单元格自然换行。 | Ready | `fontSize: 18` |
| Paths | https://example.com/very/long/unbroken/identifier/abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz | Yes | 123.45 |
| Alignment | **Bold**, *italic*, and [a link](https://example.com) survive rendering. | OK | 7 |

## Code / exact whitespace

```javascript
// Preserve indentation, tabs, trailing spaces, and a terminal newline.
const message = "Hello, mobile reader";
function greet(name) {
	return `${message}: ${name}`;  
}
const endpoint = "https://example.com/api/very-long-endpoint/abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz0123456789";
console.log(greet("世界"));
```

```python
# Theme contrast matters for comments too.
def square(value):
    return value ** 2
```

```unknown-language
<script>alert('literal text, never executable')</script>
    four spaces
```

```
plain text without a language
```

| 简短 | 数值 |
| --- | ---: |
| 甲 | 42 |
| 乙 | 81 |

<table><thead><tr><th>Raw HTML</th><th>Long cell</th></tr></thead><tbody><tr><td>Semantic table</td><td>UnbrokenIdentifierABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789</td></tr></tbody></table>

## After blocks

This paragraph must remain within the viewport even while code or tables are scrolled horizontally.
