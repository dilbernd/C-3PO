package org.c_3po.generation.crawl

import org.c_3po.generation.GenerationException
import org.c_3po.util.StringUtils
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/**
 * Utility class that generates a simple robots.txt file.
 */
object RobotsGenerator {
    const val ROBOTS_TXT_FILE_NAME = "robots.txt"

    @kotlin.jvm.JvmStatic
    @Throws(GenerationException::class)
    fun generate(parentDirectoryPath: Path, sitemapUrl: String) {
        try {
            Files.createDirectories(parentDirectoryPath)
            val robotsFilePath = parentDirectoryPath.resolve(ROBOTS_TXT_FILE_NAME)
            robotsFilePath.toFile().writeText(createContents(sitemapUrl))
        } catch (e: IOException) {
            throw GenerationException("Failed to write '$ROBOTS_TXT_FILE_NAME' file to '$parentDirectoryPath'", e)
        }
    }

    private fun createContents(sitemapUrl: String): String {
        val sitemapUrlLine = if (!StringUtils.isBlank(sitemapUrl))
            "\nSitemap: $sitemapUrl"
        else
            ""

        return """
    # www.robotstxt.org/

    # Allow crawling of all content
    User-agent: *
    Disallow:
    $sitemapUrlLine""".trimIndent()
    }
}
