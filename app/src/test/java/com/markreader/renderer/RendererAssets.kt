package com.markreader.renderer

import java.io.File

/**
 * The vendored renderer, read off disk by the tests that hold two of its files against each
 * other.
 *
 * There are several such pairs and they all fail the same silent way: the page asks for something
 * by a name the other side no longer uses, gets a 404 or a miss, and renders — just wrongly, and
 * only on one host. `BundledFontsTest` is the first of them and the desktop repo learned the
 * lesson the hard way on the stylesheet split. Reading the real files is the only kind of test
 * that catches it; a copy of the value in the test would agree with itself.
 */
object RendererAssets {

    private const val DIRECTORY = "src/main/assets/renderer"

    /** One file under `assets/renderer/`, by its path there. */
    fun read(name: String): String = file(name).readText()

    fun exists(name: String): Boolean = runCatching { file(name) }.isSuccess

    /**
     * Walks up from the working directory, which Gradle sets to the module and other runners do
     * not, so a test is not the thing that breaks when it is run from the IDE.
     */
    private fun file(name: String): File {
        val relative = "$DIRECTORY/$name"
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            for (candidate in listOf(File(directory, relative), File(directory, "app/$relative"))) {
                if (candidate.isFile) {
                    return candidate
                }
            }
            directory = directory.parentFile
        }
        throw AssertionError("no $relative above ${File("").absolutePath}")
    }
}
