package org.c_3po.generation.markdown

import org.commonmark.node.CustomBlock
import org.commonmark.node.Visitor

/**
 * An AST block node representing a meta tag.
 */
class MetaTag internal constructor(val name: String, val content: String) : CustomBlock() {
    override fun accept(visitor: Visitor) {
        visitor.visit(this)
    }
}
