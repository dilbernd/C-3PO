package org.c_3po.io

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object Directories {
    /**
     * Copies a directory recursively.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun copyDir(sourceDirectory: Path, targetDirectory: Path) {
        if (Files.exists(sourceDirectory)) {
            validateDirectory(sourceDirectory)
            validateDirectory(targetDirectory)
            if (!Files.exists(targetDirectory)) {
                Files.createDirectories(targetDirectory)
            }
            Files.newDirectoryStream(sourceDirectory).use { directoryStream ->
                for (entry in directoryStream) {
                    if (Files.isDirectory(entry)) {
                        copyDir(entry, targetDirectory.resolve(entry.fileName))
                    } else {
                        Files.copy(entry, targetDirectory.resolve(entry.fileName), StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        }
    }

    @Throws(IllegalArgumentException::class)
    private fun validateDirectory(directory: Path) {
        require(!(Files.exists(directory) && !Files.isDirectory(directory))) {
            "${directory.toAbsolutePath()} is not a directory"
        }
        require(!(Files.exists(directory) && !Files.isReadable(directory))) {
            "Read access denied for ${directory.toAbsolutePath()}"
        }
        require(!(Files.exists(directory) && !Files.isWritable(directory))) {
            "Write access denied for ${directory.toAbsolutePath()}"
        }
    }
}
