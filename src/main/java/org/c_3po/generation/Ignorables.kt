package org.c_3po.generation

import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.stream.Collectors

/**
 * Noninstantiable utility class responsible for reading C-3PO ignore file.
 */
internal object Ignorables {
    private val LOG = LoggerFactory.getLogger(Ignorables::class.java)

    private val COMPLETE_IGNORABLES_REGEX = ".*\\s\\[[a-z, ]*\\]\\s*$".toRegex()
    private val SITEMAP_IGNORABLES_REGEX = ".*\\s\\[[a-z, ]*es[a-z, ]*\\]\\s*$".toRegex()
    private val RESULT_IGNORABLES_REGEX = ".*\\s\\[[a-z, ]*er[a-z, ]*\\]\\s*$".toRegex()
    private val BRACKETED_SUFFIX_REGEX = "\\s\\[[a-z, ]*\\]\\s*$".toRegex()

    /**
     * Reads the contents of an ignore file and returns a list of strings as glob patterns that represent
     * files and directories that should be ignored completely.
     * @param ignoreFile the path to the ignore file
     * @return a list of glob pattern strings (without 'glob:' prefix)
     */
    @JvmStatic
    fun readCompleteIgnorables(ignoreFile: Path) =
            read(ignoreFile) { line -> !line.matches(COMPLETE_IGNORABLES_REGEX) }


    /**
     * Reads the contents of an ignore file and returns a list of strings as glob patterns that represent
     * files and directories that should be ignored only when generating a sitemap.
     * @param ignoreFile the path to the ignore file
     * @return a list of glob pattern strings (without 'glob:' prefix)
     */
    @JvmStatic
    fun readSitemapIgnorables(ignoreFile: Path) =
            read(ignoreFile) { line -> line.matches(SITEMAP_IGNORABLES_REGEX) }


    /**
     * Reads the contents of an ignore file and returns a list  of strings as glob patterns that represent
     * files and directories that should not produce output but still should trigger builds when being modified
     * in autoBuild mode.
     * @param ignoreFile the path to the ignore file
     * @return a list of glob pattern strings (without 'glob:' prefix)
     */
    @JvmStatic
    fun readResultIgnorables(ignoreFile: Path) =
            read(ignoreFile) { line -> line.matches(RESULT_IGNORABLES_REGEX) }


    private fun read(ignoreFile: Path, filter: (String) -> Boolean): List<String> {
        var ignorablePaths: List<String> = ArrayList()
        val ignoreFileFileName = ignoreFile.fileName
        if (Files.exists(ignoreFile)) {
            if (Files.isRegularFile(ignoreFile) && Files.isReadable(ignoreFile)) {
                try {
                    ignorablePaths = ignoreFile.toFile().readLines()
                            .filter(filter)
                            .map { line -> line.replace(BRACKETED_SUFFIX_REGEX, "") }
                    LOG.info("'{}' read ignorables file successfully", ignoreFileFileName)
                } catch (e: IOException) {
                    LOG.error("Failed to read '{}' from '{}'. No files and directories will be ignored by C-3PO " +
                            "during processing", ignoreFileFileName, ignoreFile, e)
                }
            } else {
                LOG.info("Invalid '{}' file found. Make sure it's a regular file and readable",
                        ignoreFile)
            }
        } else {
            LOG.info("No '{}' file detected in directory '{}'. " +
                    "In a '{}' file you can exclude files and folders from being processed by C-3PO",
                    ignoreFileFileName, ignoreFile.parent, ignoreFileFileName)
        }

        return ignorablePaths
    }
}
