package org.c_3po.generation.assets

import org.c_3po.URL_PATH_DELIMITER
import org.c_3po.io.FileFilters
import org.c_3po.util.ChecksumCalculator
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.NoSuchAlgorithmException
import java.util.*

object Fingerprinter {
    private val LOG = LoggerFactory.getLogger(Fingerprinter::class.java)

    private val SHA1_CSS_REGEX = "^.*\\.[0123456789abcdef]{40}\\.css$".toRegex()
    private val CSS_REGEX = ".css$".toRegex()

    // TODO: Tests
    //  - A reference to an old fingerprinted version is replaced by a new one
    @JvmStatic
    @Throws(IOException::class, NoSuchAlgorithmException::class)
    fun fingerprintStylesheets(dir: Path,
                               rootDestDir: Path): Map<String, String> {
        val substitutes = HashMap<String, String>()

        // If no valid directory, return empty map
        if (!Files.isDirectory(dir)) {
            return substitutes
        }
        val cssFilter = DirectoryStream.Filter { entry: Path ->
            val fileName = entry.toFile().name
            (Files.isRegularFile(entry)
                    && fileName.endsWith(".css")
                    && !fileName.matches(SHA1_CSS_REGEX))
        }
        Files.newDirectoryStream(dir, cssFilter).use { cssFiles ->
            for (cssFile in cssFiles) {
                LOG.info("Fingerprinting stylesheet file '$cssFile'")

                // Compute hash
                val sha1 = ChecksumCalculator.encodeHexString(ChecksumCalculator.computeSha1Hash(cssFile))

                // Create file
                val fileName = cssFile.fileName.toString()
                val fingerprintedFileName = fileName.replaceFirst(CSS_REGEX, ".$sha1.css")
                val fingerprintedFile = dir.resolve(fingerprintedFileName)
                if (!Files.exists(fingerprintedFile)) {
                    Files.copy(cssFile, fingerprintedFile)
                }

                // Add substitution
                // TODO: See if that works for subfolders in /css as well
                val dirAsUrlPath = rootDestDir.toAbsolutePath().relativize(dir.toAbsolutePath())

                // Note: Leading slash makes it comparable to "implicit schema and domain absolute URLs"
                substitutes["$URL_PATH_DELIMITER${dirAsUrlPath.resolve(fileName)}"] =
                        dirAsUrlPath.resolve(fingerprintedFileName).toString()

                // Purge any outdated fingerprinted versions of this file
                // TODO: Add substitutes from here as well
                purgeOutdatedFingerprintedVersions(dir, fileName, fingerprintedFileName)
            }
        }
        FileFilters.subDirStream(dir).use { subDirs ->
            for (subDir in subDirs) {
                substitutes.putAll(fingerprintStylesheets(subDir, rootDestDir))
            }
        }
        return substitutes
    }


    @Throws(IOException::class)
    private fun purgeOutdatedFingerprintedVersions(dir: Path,
                                                   fileName: String,
                                                   fingerprintedFileName: String) {
        val name = fileName.substring(0, fileName.lastIndexOf("."))
        val ext = fileName.substring(fileName.lastIndexOf(".") + 1)
        val outdatedFilter = DirectoryStream.Filter { entry: Path ->
            val entryFileName = entry.toFile().name
            (Files.isRegularFile(entry)
                    && entryFileName != fingerprintedFileName
                    && entryFileName.matches("^$name\\.[0123456789abcdef]{40}\\.$ext$".toRegex()))
        }
        Files.newDirectoryStream(dir, outdatedFilter).use { outdatedFiles ->
            outdatedFiles.forEach{Files.delete(it)}
        }
    }
}
