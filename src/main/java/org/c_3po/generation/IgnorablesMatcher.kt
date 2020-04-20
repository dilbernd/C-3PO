package org.c_3po.generation

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher

/**
 * Helps to recognize ignorable files and directories.
 */
@Suppress("DataClassPrivateConstructor") // data class means we have less code
data class IgnorablesMatcher private constructor(val basePath: Path, val globPatterns: List<String>) {
    private val pathMatchers: List<PathMatcher> =
            globPatterns.map { globPattern -> FileSystems.getDefault().getPathMatcher("glob:$globPattern") }

    fun matches(path: Path): Boolean {
        val relativePath = path.normalize().let {
            if (it.startsWith(basePath)) basePath.relativize(it) else it
        }
        return pathMatchers.any { matcher -> matcher.matches(relativePath) }
    }

    companion object {
        @JvmStatic
        fun from(basePath: Path,
                 globPatterns: List<String>): IgnorablesMatcher {
            return IgnorablesMatcher(basePath, globPatterns)
        }
    }
}
