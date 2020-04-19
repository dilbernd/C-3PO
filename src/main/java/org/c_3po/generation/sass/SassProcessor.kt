package org.c_3po.generation.sass

import io.bit3.jsass.CompilationException
import io.bit3.jsass.Compiler
import io.bit3.jsass.Options
import io.bit3.jsass.OutputStyle
import io.bit3.jsass.context.FileContext
import java.nio.file.Path

/**
 * Responsible for compiling SASS files to CSS files.
 */
class SassProcessor private constructor() {
    @Throws(CompilationException::class)
    fun process(sassFile: Path): String {
        val compiler = Compiler()
        val options = Options()
        options.outputStyle = OutputStyle.COMPRESSED
        val fileContext = FileContext(sassFile.toUri(), null, options)
        val output = compiler.compile(fileContext)
        return output.css
    }

    companion object {
        @kotlin.jvm.JvmStatic
        val instance: SassProcessor
            get() = SassProcessor()
    }
}
