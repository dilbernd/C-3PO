package org.c_3po.generation.crawl

import org.c_3po.generation.GenerationException
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Utility class for turning an instance of [SiteStructure] into a XML sitemap
 * according to [http://www.sitemaps.org/](http://www.sitemaps.org/).
 */
object SitemapGenerator {
    private val LOG = LoggerFactory.getLogger(SitemapGenerator::class.java)
    private const val NAMESPACE_URI = "http://www.sitemaps.org/schemas/sitemap/0.9"
    private const val ELEM_URLSET = "urlset"

    /**
     * Generates a sitemap file compliant to the sitemap XML standard
     * defined at [http://www.sitemaps.org/protocol.html](http://www.sitemaps.org/protocol.html).
     *
     * @param siteStructure the SiteStructure that is to be written to a sitemap xml file
     * @param filePath the path to the **file** to where the sitemap should be written to
     */
    @JvmStatic
    @Throws(GenerationException::class)
    fun generate(siteStructure: SiteStructure, filePath: Path) =
            try {
                newSitemapDocument().apply {
                    documentElement.run {
                        siteStructure.toUrls().forEach { url ->
                            appendChild(newUrlElem(url, this@apply))
                        }
                    }
                }.also { writeToFile(it, filePath) }
            } catch (e: ParserConfigurationException) {
                LOG.debug("Failed to generate sitemap.xml. See enclosed exception for more details.", e)
                throw GenerationException("Failed to generate sitemap xml file", e)
            } catch (e: TransformerException) {
                LOG.debug("Failed to generate sitemap.xml. See enclosed exception for more details.", e)
                throw GenerationException("Failed to generate sitemap xml file", e)
            }

    @Throws(ParserConfigurationException::class)
    private fun newSitemapDocument() = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .domImplementation.createDocument(NAMESPACE_URI, ELEM_URLSET, null)

    private fun newUrlElem(url: String, document: Document) = document.createElement("url")
            .appendChild(newLocElem(url, document))

    private fun newLocElem(url: String, document: Document) =
            document.createElement("loc").also { it.textContent = url }

    @Throws(TransformerException::class)
    private fun writeToFile(document: Document, filePath: Path) {

        // Note: for XML UTF-8 is the default character encoding. Therefore we assume that
        // Transformer uses UTF-8 as well.
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.transform(DOMSource(document), StreamResult(filePath.toFile()))
    }
}
