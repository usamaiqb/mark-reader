# Changelog

All notable changes to MarkReader are documented in this file.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.0.4] - 2026-09-12

### Improved
- Right-to-left documents (Arabic, Hebrew) now derive their text direction from the content instead of the app locale.
- Collapsing the viewer chrome now gives the reader the space back, so immersive reading uses the full screen.

### Fixed
- Stopped the collapsing chrome from resizing the reader's scroll container and causing scroll jumps.
- Empty files can now be opened in the editor, with an Edit action offered from the empty-file state itself.
- Corrected stale navigation and save callbacks in the UI.

### Changed
- Adopted a Gradle version catalog and added detekt and lint configuration, with JVM unit tests for the reader's scroll-anchor and segment-splitting logic.

## [1.0.3] - 2026-08-21

### Added
- Bundled a sans reading font for better on-screen readability.
- Reading progress bar with a percentage badge in the viewer.
- A tappable Rendered/Raw mode pill and a dedicated reader surface-flip button.
- Auto-hiding viewer chrome on scroll and an animated search transition.
- Overhauled search UX: auto-focus, debounced highlighting, jump-to-first-match, and rounded text-hugging match highlights.

### Improved
- Reworked the reader screen with rounded icons, haptic feedback on controls, a restyled table-of-contents sheet, and an expressive export bottom sheet.
- Updated GitHub Actions CI dependencies.

### Fixed
- Stopped scroll stutter on upward scroll and preserved the scroll anchor across line-wrap toggles.
- Hardened file open, read, and save paths: safer error handling, bounded reads with BOM stripping, shared-text handling via cache file, and more reliable persistable URI grants.

## [1.0.2] - 2026-08-02

### Added
- Added a recent files list on the home screen for quicker access to previously opened documents.
- Added a dynamic colors toggle in settings, with preference persistence and viewer UI support.

### Improved
- Updated app descriptions to better highlight note-taking support.
- Updated GitHub Actions dependencies for checkout, Java setup, and release publishing.

## [1.0.1] - 2026-07-14

### Added
- Live reader preview in Settings, rendering sample prose and code with your actual colors, fonts, size, spacing, and alignment, with a light/dark toggle
- GitHub repository link in the About section

### Improved
- Redesigned Settings screen with Material 3 expressive UI: grouped rows with leading icons and animated value badges, a collapsing top app bar, restyled picker sheets, and haptic feedback on selections

## [1.0.0] - 2026-04-08

### Added
- Markdown rendering with full CommonMark support including tables
- Syntax highlighting for code files via Prism4j
- Built-in editor with Edit/Preview tabs for markdown files
- Formatting toolbar for markdown editing (bold, italic, headings, code blocks, links, lists)
- Open files from any file manager via ACTION_VIEW intent
- Save edited files in place or export via Save As
- Create new files from HomeScreen
- Material You design with dynamic color, dark and light theme support
- Edge-to-edge display
