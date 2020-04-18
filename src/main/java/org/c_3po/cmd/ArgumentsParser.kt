package org.c_3po.cmd

import org.slf4j.LoggerFactory

/**
 * Responsible for parsing and processing command line arguments.
 */
// TODO: Should be an object, refactor after “touch one package at a time” conversion
class ArgumentsParser {
    fun processCmdLineArguments(args: Array<String>): CmdArguments {
        var sourceDirectoryName = ""
        var destinationDirectoryName = ""
        var autoBuild = false
        var i = 0
        while (i < args.size) {
            val argument = args[i]
            LOG.debug("Command line argument: $argument")
            if ("-src" == argument && i < args.size - 1) {
                val sourceDirArgument = args[i + 1]
                sourceDirectoryName = sourceDirArgument
                i++
            }
            if ("-dest" == argument && i < args.size - 1) {
                val destinationDirArgument = args[i + 1]
                destinationDirectoryName = destinationDirArgument
                i++
            }
            if ("-a" == argument) {
                autoBuild = true
            }
            i++
        }

        // TODO: Introduce cmd switch for fingerprinting.
        // TODO: Properly document switch for users.
        return CmdArguments(sourceDirectoryName, destinationDirectoryName, autoBuild, true)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ArgumentsParser::class.java)
    }
}
