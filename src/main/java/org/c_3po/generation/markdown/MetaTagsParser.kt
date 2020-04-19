package org.c_3po.generation.markdown

import org.commonmark.node.Block
import org.commonmark.parser.block.*
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Parser implementation of custom markdown extension that allows to specify meta tags.
 */
internal class MetaTagsParser private constructor(private val metaTag: MetaTag) : AbstractBlockParser() {
    override fun getBlock(): Block = metaTag

    // Note: it might be useful to have a meta description tag that spans multiple lines, but
    // to keep it simple we don't support that by now
    override fun tryContinue(parserState: ParserState) = BlockContinue.none()

    class Factory : AbstractBlockParserFactory() {
        override fun tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser) =
                if (state.index > 0) {
                    BlockStart.none()
                } else {
                    val line = state.line
                    var matcher: Matcher
                    if (META_PATTERN.matcher(line).also { matcher = it }.find()) {
                        val name = matcher.group(1)
                        val content = matcher.group(2).trim { it <= ' ' }
                        val metaTag = MetaTag(name, content)
                        BlockStart.of(MetaTagsParser(metaTag)).atIndex(line.length)
                    } else {
                        BlockStart.none()
                    }
                }

        companion object {
            private val META_PATTERN = Pattern.compile("^\\\$meta-([a-zA-Z\\-_\\. ]*):(.*)")
        }
    }
}
