package org.c_3po.generation.crawl

import org.c_3po.URL_PATH_DELIMITER
import java.nio.file.Path
import java.util.*

/**
 * Represents the structure of a generated website.
 */
class SiteStructure private constructor(baseUrl: String) {
    val baseUrl: String = withTrailingSlash(baseUrl)

    private val paths: MutableList<Path> = ArrayList()

    /**
     * Adds a new page to the site structure defined by the passed path
     * @param path must be a relative path
     * @throws IllegalArgumentException if passed path is null or absolute
     */
    fun add(path: Path) {
        require(!path.isAbsolute) { "Path must not be an absolute path." }
        paths.add(path)
    }

    fun toUrls(): List<String> = paths.map { pagePath -> baseUrl + toUrlPart(pagePath) }

    private fun withTrailingSlash(s: String) = if (s.endsWith(URL_PATH_DELIMITER)) s else s + URL_PATH_DELIMITER

    private fun toUrlPart(pagePath: Path) = pagePath.map(Path::toString).joinToString(URL_PATH_DELIMITER)

    companion object {
        @JvmStatic
        fun getInstance(baseUrl: String) = SiteStructure(baseUrl)
    }
}
