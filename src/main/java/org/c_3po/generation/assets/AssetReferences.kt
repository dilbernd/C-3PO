package org.c_3po.generation.assets

import org.c_3po.URL_PATH_DELIMITER
import org.c_3po.io.FileFilters
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

object AssetReferences {
    private val LOG = LoggerFactory.getLogger(AssetReferences::class.java)

    // TODO: Instead of assetSubstitutes pass an object holding keys for stylesheet substitutes, image substitutes
    //  and so on. This will allow to be more efficient by knowing which substitutes are relevant
    //  for which type of reference.
    @JvmStatic
    @Throws(IOException::class)
    fun replaceAssetsReferences(dir: Path, assetSubstitutes: Map<String, String>, generatorSettings: Properties) {
        // Replace references
        // TODO: Replace all refs in all docs in one pass
        // TODO: If site is built into a non-empty destination dir, also replace outdated fingerprinted refs, though
        //  that might be a feature of the calling code whose job is to supply the assetSubstitutes map.
        Files.newDirectoryStream(dir, FileFilters.htmlFilter).use { htmlFiles ->
            for (htmlFile in htmlFiles) {
                val doc = Jsoup.parse(htmlFile.toFile(), "UTF-8")
                replaceStylesheetReferences(doc, assetSubstitutes, htmlFile, generatorSettings)
                Files.write(htmlFile, doc.outerHtml().toByteArray())
            }
        }
        FileFilters.subDirStream(dir).use { subDirs ->
            for (subDir in subDirs) {
                replaceAssetsReferences(subDir, assetSubstitutes, generatorSettings)
            }
        }
    }

    private fun replaceStylesheetReferences(doc: Document,
                                            stylesheetSubstitutes: Map<String, String>,
                                            docPath: Path,
                                            generatorSettings: Properties) {
        // TODO: Check if there any other way to reference a stylesheet?
        val elements = doc.select("link[rel='stylesheet']")
        for (element in elements) {
            val href = element.attr("href")
            try {
                val hrefURI = URI(href)
                if (isAssetControlledByWebsite(hrefURI, URI(generatorSettings.getProperty("baseUrl")))) {
                    val assetPath = translateToAssetPath(hrefURI, determineBaseURI(doc, generatorSettings))
                    val substitutePath = stylesheetSubstitutes[assetPath]
                    if (substitutePath != null) {
                        // Note: Replace the asset's name only and leave the URL untouched otherwise.
                        val substituteFileName = Paths.get(substitutePath).fileName.toString()
                        element.attr("href", href.replaceAfterLast(URL_PATH_DELIMITER, substituteFileName))
                    } else {
                        LOG.warn("Failed to substitute asset resource '$href' found in $docPath")
                    }
                }
            } catch (ignored: URISyntaxException) {
            }
        }
    }

    /**
     * Determines if the given URI is controlled by the website being built.
     *
     * If the website's baseURL contains either a www or non-www host, www
     * and non-www assets are considered to be served by this origin.
     * If the baseURL does not contain a www or non-www host, thus contains
     * a sub-domain different than "www", the given URI is only considered
     * to be an internal asset if it's URI matches the same host.
     */
    private fun isAssetControlledByWebsite(hrefURI: URI, baseURI: URI) =
            // TODO: Finish implementation.
            if (hrefURI.host != null) hrefURI.host == baseURI.host else true


    private fun translateToAssetPath(hrefURI: URI, baseURI: URI) =
            /*
                The difficulty is to look at an URL and identify which
                asset is referenced by it.

                Types of URLs:

                https://example.com/index.html ==> Absolute URL
                //example.com/index.html ==> Implicit schema absolute URL, or better known as protocol-relative URI
                /css/main.css ==> Implicit schema and host absolute URL
                css/main.css ==> Document-relative URL

                Specs
                - Absolute URLs and implicit schema absolute URLs that reference the host either with or without www
                subdomain are considered to be resources held by the website that is being built.
                - Absolute URLs and implicit schema absolute URLs whose root domain is the same but have a different
                subdomain, e.g. blog.example.com are considered to not be held by this website. They are treated as foreign
                domains.
                - Any other absolute URLs are considered to be resources from third parties and thus are not replaced.

                - An absolute URL with implicit schema and host is the easiest. It should only be normalized and then be
                compared if any asset path is matching.

                - A relative URL is relative to the document it is referenced by. So, I'll need to construct the URI
                of the document and then `resolve()` the relative URLs. Then query the path portion via `getPath()`
                to obtain the asset path.
                - For a relative URL, always see if there are `<base>` elements in the parent document. Before calling
                resolve, be sure to resolve (?!) the base elements' `href` value. If there are multiple base elements,
                use the first one that has an href attribute.
                See https://html.spec.whatwg.org/multipage/urls-and-fetching.html#document-base-url.
            */
            when {
                isDocumentRelativeURI(hrefURI) -> {
                    // TODO: Take <base> into account
                    // TODO: Add leading slash might be brittle but fact is that .getPath() after .resolve()
                    //  does not render a leading slash if relative URI is of form `foo/bar.css` and baseURI
                    //  does not have a path portion. It might be even more complicated like that.
                    baseURI.resolve(hrefURI).path.let { uriPath ->
                        if (uriPath.startsWith(URL_PATH_DELIMITER)) uriPath else "$URL_PATH_DELIMITER$uriPath"
                    }
                }
                isProtocolRelativeURI(hrefURI) -> hrefURI.path
                isHostRelativeURI(hrefURI) -> hrefURI.toString()
                hrefURI.isAbsolute -> hrefURI.path
                else -> {
                    // TODO: Decide if this should be a warning. I think so.
                    hrefURI.toString()
                }
            }


    // TODO: Require the document's URI. What? Maybe the <base> tag is meant.
    @Throws(URISyntaxException::class)
    private fun determineBaseURI(doc: Document, generatorSettings: Properties) =
            URI(generatorSettings.getProperty("baseUrl"))


    private fun isHostRelativeURI(uri: URI) =
            uri.host == null && uri.toString().startsWith("/")


    private fun isProtocolRelativeURI(uri: URI): Boolean {
        return uri.scheme == null && uri.host != null && uri.toString().startsWith("//")
    }

    private fun isDocumentRelativeURI(uri: URI) =
            if (uri.host != null) false else !uri.path.startsWith("/")
}
