package org.c_3po.io

import java.io.IOException
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * A collection of file filters and related helpers.
 */
object FileFilters {
    var htmlFilter = DirectoryStream.Filter { entry: Path ->
        Files.isRegularFile(entry) && entry.toFile().name.endsWith(".html")
    }

    @Throws(IOException::class)
    fun subDirStream(dir: Path) =
        Files.newDirectoryStream(dir) { entry: Path -> Files.isDirectory(entry) }
}
