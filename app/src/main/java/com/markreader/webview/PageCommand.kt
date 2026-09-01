package com.markreader.webview

/**
 * Something the app asks the page to do about search or the outline.
 *
 * These are the only *imperative* things the reader sends into the page. Everything else it
 * pushes — the document, the settings, the content inset — is state, and state is applied by
 * naming its new value. "Move to the next match" is not a value: the match set only exists in the
 * page, so stepping through it has to be a request, and the result comes back as an event.
 *
 * Delivered over a buffered channel rather than as `State`, so a request that arrives while the
 * WebView is being rebuilt — every toggle into raw mode and back destroys it — is held and
 * delivered to the page that replaces it, instead of being lost or replayed forever.
 */
sealed interface PageCommand {

    /** Find [query], paint every match, reveal the first. An empty query clears the find. */
    data class Find(val query: String) : PageCommand

    /** Move to another match, wrapping at either end. [delta] is 1 for next, -1 for previous. */
    data class StepFind(val delta: Int) : PageCommand

    /** The search affordance closed; drop the query and its highlights. */
    data object CloseFind : PageCommand

    /** Scroll a heading into view, named by one of the slugs the outline reported. */
    data class Reveal(val slug: String) : PageCommand
}
