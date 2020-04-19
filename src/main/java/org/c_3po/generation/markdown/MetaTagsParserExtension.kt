package org.c_3po.generation.markdown

import org.commonmark.parser.Parser
import org.commonmark.parser.Parser.ParserExtension

/**
 * Parser extension that is responsible for parsing meta tag syntax.
 */
internal class MetaTagsParserExtension private constructor() : ParserExtension {
    override fun extend(parserBuilder: Parser.Builder) {
        parserBuilder.customBlockParserFactory(MetaTagsParser.Factory())
    }

    companion object {
        val instance: MetaTagsParserExtension
            get() = MetaTagsParserExtension()
    }
}
