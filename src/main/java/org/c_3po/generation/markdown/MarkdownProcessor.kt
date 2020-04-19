package org.c_3po.generation.markdown

import org.commonmark.html.HtmlRenderer
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.CustomBlock
import org.commonmark.parser.Parser
import org.slf4j.LoggerFactory
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/**
 * Responsible for processing markdown files.
 */
class MarkdownProcessor private constructor() {
    private val parser = Parser.builder().extensions(listOf(MetaTagsParserExtension.instance)).build()
    private val htmlRenderer = HtmlRenderer.builder().build()

    @Throws(IOException::class)
    fun process(markdownFile: Path) = if (Files.exists(markdownFile)) {
        LOG.debug("Processing markdown file '{}'", markdownFile)

        // Read in contents
        val lines = Files.readAllLines(markdownFile)
        val sb = StringBuilder(lines.size)
        lines.stream().forEach { l: String? ->
            sb.append(l).append(System.lineSeparator())
        }

        // Parse markdown and produce HTML content
        val document = parser.parse(sb.toString())
        val output = htmlRenderer.render(document)

        // Process meta tags
        val metaTagsVisitor = MetaTagsVisitor()
        document.accept(metaTagsVisitor)
        Result(metaTagsVisitor.result, output)
    } else {
        throw FileNotFoundException("File '" + markdownFile.toAbsolutePath().toString() + "' not found.")
    }

    class Result(val headResult: Head,
                 val contentResult: String)

    class Head {
        private val metaTags: MutableMap<String, String> = HashMap()
        var title = ""

        fun addMetaTag(name: String, content: String) {
            metaTags[name] = content
        }

        fun getMetaTags(): Map<String, String> {
            return HashMap(metaTags)
        }
    }

    private class MetaTagsVisitor : AbstractVisitor() {
        val result = Head()
        override fun visit(customBlock: CustomBlock) {
            if (customBlock is MetaTag) {
                val metaTag = customBlock
                if (TITLE == metaTag.name) {
                    result.title = metaTag.content
                } else {
                    result.addMetaTag(metaTag.name, metaTag.content)
                }
            }
        }

        companion object {
            private const val TITLE = "title"
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(MarkdownProcessor::class.java)

        @kotlin.jvm.JvmStatic
        val instance: MarkdownProcessor
            get() = MarkdownProcessor()
    }
}
