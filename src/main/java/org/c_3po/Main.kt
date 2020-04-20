package org.c_3po

import org.c_3po.cmd.ArgumentsParser
import org.c_3po.generation.SiteGenerator.Companion.fromCmdArguments
import org.slf4j.LoggerFactory

object Main {
    private val LOG = LoggerFactory.getLogger(Main::class.java)

    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
        Thread.currentThread().uncaughtExceptionHandler = UncaughtExceptionHandler

        // TODO test out Thymeleaf layout for YodaConditions
        // TODO a c-3po.properties file on the classpath
        try {
            LOG.info("Hello There! I'm C-3PO! Which site do you wish me to generate?")

            // Parsing command line arguments
            val cmdArguments = ArgumentsParser().processCmdLineArguments(args)
            LOG.debug("src (source directory) is: {}", cmdArguments.sourceDirectory)
            LOG.debug("dest (destination directory) is: {}", cmdArguments.destinationDirectory)
            LOG.debug("autoBuild is: {}", cmdArguments.isAutoBuild.toString())

            // Do cmd arguments validation
            val cmdArgsValid = cmdArguments.validate()

            // Generate the site
            if (cmdArgsValid) {
                val siteGenerator = fromCmdArguments(cmdArguments)
                if (cmdArguments.isAutoBuild) {
                    siteGenerator.generateOnFileChange()
                } else {
                    siteGenerator.generate()
                }
            }
            LOG.debug("I'm going to shutdown.")
        } catch (ex: RuntimeException) {
            LOG.error("Caught a runtime exception in main method. Terminating with a non-zero exit code", ex)
            System.exit(1)
        }
    }

    private object UncaughtExceptionHandler : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            LOG.error("Caught an uncaught exception.", e)
        }
    }
}
