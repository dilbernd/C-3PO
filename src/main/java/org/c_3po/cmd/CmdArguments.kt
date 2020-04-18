package org.c_3po.cmd

import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Value class holding command line arguments.
 */
data class CmdArguments(val sourceDirectory: String, val destinationDirectory: String, val isAutoBuild: Boolean,
                        val fingerprintAssets: Boolean) {

    @Throws(IOException::class)
    fun validate() = isSrcAndDestNotTheSame

    // TODO: Should be a straight-up property, refactor after “touch one package at a time” conversion
    fun shouldFingerprintAssets() = fingerprintAssets

    @get:Throws(IOException::class)
    private val isSrcAndDestNotTheSame: Boolean
        get() {
            val srcPath = Paths.get(sourceDirectory)
            val destpath = Paths.get(destinationDirectory)
            val dirsAreTheSame = if (Files.exists(srcPath) && Files.exists(destpath)) {
                Files.isSameFile(srcPath, destpath)
            } else {
                srcPath == destpath
            }
            if (dirsAreTheSame) {
                LOG.error("'src' and 'dest' locate the same directory, please use different directories")
            }
            return !dirsAreTheSame
        }

    companion object {
        private val LOG = LoggerFactory.getLogger(CmdArguments::class.java)
    }
}
