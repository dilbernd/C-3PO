package org.c_3po.generation

import io.bit3.jsass.CompilationException
import nz.net.ultraq.thymeleaf.LayoutDialect
import nz.net.ultraq.thymeleaf.decorators.SortingStrategy
import nz.net.ultraq.thymeleaf.decorators.strategies.GroupingStrategy
import org.c_3po.URL_PATH_DELIMITER
import org.c_3po.cmd.CmdArguments
import org.c_3po.generation.Ignorables.readCompleteIgnorables
import org.c_3po.generation.Ignorables.readResultIgnorables
import org.c_3po.generation.Ignorables.readSitemapIgnorables
import org.c_3po.generation.IgnorablesMatcher.Companion.from
import org.c_3po.generation.assets.AssetReferences.replaceAssetsReferences
import org.c_3po.generation.assets.Fingerprinter.fingerprintStylesheets
import org.c_3po.generation.crawl.RobotsGenerator
import org.c_3po.generation.crawl.RobotsGenerator.generate
import org.c_3po.generation.crawl.SiteStructure.Companion.getInstance
import org.c_3po.generation.crawl.SitemapGenerator.generate
import org.c_3po.generation.markdown.MarkdownProcessor
import org.c_3po.generation.sass.SassProcessor
import org.c_3po.io.FileFilters
import org.c_3po.util.StringUtils
import org.slf4j.LoggerFactory
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import org.thymeleaf.dom.Element
import org.thymeleaf.dom.Node
import org.thymeleaf.templateresolver.FileTemplateResolver
import org.thymeleaf.templateresolver.TemplateResolver
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.security.NoSuchAlgorithmException
import java.time.LocalDateTime
import java.time.temporal.ChronoField
import java.util.*

/**
 * Main class responsible for site generation.
 */
class SiteGenerator private constructor(private val sourceDirectoryPath: Path,
                                        private val destinationDirectoryPath: Path,
                                        private val shouldFingerprintAssets: Boolean,
                                        completeIgnorables: List<String>,
                                        resultIgnorables: List<String>,
                                        private val settings: Properties?) {
    private val sourceHtmlFilter = DirectoryStream.Filter { entry: Path ->
        (!isCompleteIgnorable(entry) && !isResultIgnorable(entry) && FileFilters.htmlFilter.accept(entry))
    }


    private val markdownFilter = DirectoryStream.Filter { entry: Path ->
        Files.isRegularFile(entry) && !isCompleteIgnorable(entry) && !isResultIgnorable(entry)
                && entry.toFile().name.endsWith(".md")
    }


    private val markdownTemplateFilter = DirectoryStream.Filter { entry: Path ->
        Files.isRegularFile(entry) && !isCompleteIgnorable(entry)
                && entry.toFile().name == CONVENTIONAL_MARKDOWN_TEMPLATE_NAME
    }


    private val sassFilter = DirectoryStream.Filter { entry: Path ->
        val isSassFile = (entry.toFile().name.endsWith(".sass") || entry.toFile().name.endsWith(".scss"))
        Files.isRegularFile(entry) && !isCompleteIgnorable(entry) && !isResultIgnorable(entry) && isSassFile
    }


    private val staticFileFilter = DirectoryStream.Filter { entry: Path ->
        (Files.isRegularFile(entry) && !isCompleteIgnorable(entry) && !isResultIgnorable(entry) && !sourceHtmlFilter.accept(
                entry)
                && !markdownFilter.accept(entry) && !sassFilter.accept(entry))
    }


    private val templateEngine = setupTemplateEngine(sourceDirectoryPath)
    private val markdownProcessor = MarkdownProcessor.instance
    private val sassProcessor = SassProcessor.instance
    private var completeIgnorablesMatcher = from(sourceDirectoryPath, completeIgnorables)
    private var resultIgnorablesMatcher = from(sourceDirectoryPath, resultIgnorables)

    private val baseTemplateContext: Context
        get() = Context().apply {
            setVariable("year", LocalDateTime.now()[ChronoField.YEAR])
        }


    /**
     * Does a one time site generation.
     * @throws IOException
     */
    @Throws(IOException::class, GenerationException::class)
    fun generate() {
        buildWebsite()

        // TODO Check if there are any files in destination directory that are to be ignored
        //  (e.g. because ignore file has changed since last generation)
        //  Update 2020-03-02: Not sure if `generate` is the right place to do so.
    }


    /**
     * Does a site generation when a source file has been added, changed or deleted
     * @throws IOException
     */
    @Throws(IOException::class)
    fun generateOnFileChange() {
        buildPagesAndAssets(sourceDirectoryPath, destinationDirectoryPath)
        val watchService = FileSystems.getDefault().newWatchService()
        val watchKeyMap = registerWatchServices(sourceDirectoryPath, watchService)

        while (true) {
            val key: WatchKey = try {
                LOG.trace("In watcher loop waiting for a new change notification")
                watchService.take()
            } catch (ex: InterruptedException) {
                return  // stops the infinite loop
            }

            // Now that we have a "signaled" (as opposed to "ready" and "invalid") watch key,
            // let's see what's in there for us
            for (event in key.pollEvents()) {
                val kind = event.kind()
                LOG.debug("File '{}' with kind '{}' triggered a change", event.context(), event.kind())

                // Ignore the overflow event, that can happen always - i.e. it does
                // not have to be registered with the watcher
                if (kind === StandardWatchEventKinds.OVERFLOW) {
                    continue
                }

                // Depending on type of resource let's build the whole site or just a portion
                var changedPath = event.context() as Path
                if (Files.exists(changedPath)
                        && Files.isSameFile(changedPath, sourceDirectoryPath.resolve(C_3PO_IGNORE_FILE_NAME))) {
                    updateIgnorables(changedPath)
                } else {
                    val parent = watchKeyMap[key]
                    changedPath = parent!!.resolve(changedPath)
                    if (kind === StandardWatchEventKinds.ENTRY_CREATE || kind === StandardWatchEventKinds.ENTRY_MODIFY) {
                        if (sourceHtmlFilter.accept(changedPath) || sassFilter.accept(changedPath)) {
                            buildPagesAndAssets(sourceDirectoryPath, destinationDirectoryPath)
                        } else if (staticFileFilter.accept(changedPath)
                                || markdownFilter.accept(changedPath)
                                || markdownTemplateFilter.accept(changedPath)) {
                            val parentDir = sourceDirectoryPath.relativize(key.watchable() as Path)

                            // Changed static assets and markdown articles don't require a full rebuild
                            // because their contents isn't copied over into another file.
                            buildPagesAndAssets(parentDir, destinationDirectoryPath.resolve(parentDir))
                        } else if (Files.isDirectory(changedPath) && !isCompleteIgnorable(changedPath)) {
                            if (kind === StandardWatchEventKinds.ENTRY_CREATE) {
                                watchKeyMap[registerWatchService(watchService, changedPath)] = changedPath
                                LOG.debug("Registered autoBuild watcher for '{}'", changedPath)
                            }
                            buildPagesAndAssets(sourceDirectoryPath, destinationDirectoryPath)
                        } else {
                            LOG.warn("No particular action executed for '{}' that triggered a change with kind '{}'",
                                    event.context(), event.kind())
                        }
                    } else if (kind === StandardWatchEventKinds.ENTRY_DELETE) {
                        if (!isCompleteIgnorable(changedPath) && !isResultIgnorable(changedPath)) {
                            val targetPath = destinationDirectoryPath.resolve(changedPath)

                            // Delete files and directories in target directory
                            if (Files.exists(targetPath)) {
                                if (Files.isDirectory(targetPath)) {
                                    deleteDirectory(targetPath)
                                } else {
                                    Files.deleteIfExists(destinationDirectoryPath.resolve(changedPath))
                                }
                            }

                            // Cancel watcher for the path if there was one registered
                            if (watchKeyMap[key] == changedPath) {
                                key.cancel()
                                watchKeyMap.remove(key)
                                LOG.debug("Cancelled autoBuild watcher for '{}", changedPath)
                            }
                        }
                    }
                }

                // Reset the key -- this step is critical if you want to
                // receive further watch events. If the key is no longer valid,
                // the directory is inaccessible, so exit the loop.
                val valid = key.reset()
                if (!valid) {
                    break
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun registerWatchServices(rootDirectory: Path, watchService: WatchService): MutableMap<WatchKey, Path> {
        val watchKeyMap: MutableMap<WatchKey, Path> = HashMap()
        Files.walkFileTree(rootDirectory, object : SimpleFileVisitor<Path>() {
            @Throws(IOException::class)
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                return if (!isCompleteIgnorable(Objects.requireNonNull(dir).normalize())) {
                    watchKeyMap[registerWatchService(watchService, dir)] = dir
                    FileVisitResult.CONTINUE
                } else {
                    FileVisitResult.SKIP_SUBTREE
                }
            }
        })
        watchKeyMap.values.stream().forEach { path: Path? ->
            LOG.debug("Registered autoBuild watcher for '{}'", path)
        }
        return watchKeyMap
    }

    @Throws(IOException::class)
    private fun registerWatchService(watchService: WatchService, pathToWatch: Path): WatchKey {
        return if (Files.exists(pathToWatch)) {
            pathToWatch.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY)
        } else {
            throw FileNotFoundException("Path '$pathToWatch' to watch does not exist")
        }
    }

    @Throws(IOException::class, GenerationException::class)
    private fun buildWebsite() {
        buildPagesAndAssets(sourceDirectoryPath, destinationDirectoryPath)
        buildCrawlFiles()

        // TODO: Somehow use purge-css as well if the respective flag is set
        if (shouldFingerprintAssets) {
            fingerprintAssets()
        }
    }

    @Throws(IOException::class)
    private fun buildPagesAndAssets(sourceDir: Path, targetDir: Path) {
        LOG.debug("Building pages contained in '{}'", sourceDir)

        // Clear Thymeleaf's template cache
        templateEngine.clearTemplateCache()

        // Ensure targetDir exists
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir)
        }
        Files.newDirectoryStream(sourceDir, sourceHtmlFilter).use { htmlFilesStream ->
            for (htmlFile in htmlFilesStream) {
                LOG.trace("Generate '{}'", htmlFile)

                // Generate
                try {
                    val lines = listOf(
                            templateEngine.process(htmlFile.toString().replace(".html", ""),
                                    baseTemplateContext))
                    // Write to file
                    val destinationPath = targetDir.resolve(htmlFile.fileName)
                    try {
                        Files.write(destinationPath,
                                lines,
                                Charset.forName("UTF-8"),
                                StandardOpenOption.CREATE,
                                StandardOpenOption.WRITE,
                                StandardOpenOption.TRUNCATE_EXISTING)
                    } catch (e: IOException) {
                        LOG.error("Failed to write generated document to {}", destinationPath, e)
                    }
                } catch (ex: RuntimeException) {
                    LOG.warn("Thymeleaf failed to process '{}'. Reason: '{}'", htmlFile, ex.message)
                }
            }
        }
        Files.newDirectoryStream(sourceDir, markdownFilter).use { markdownFilesStream ->
            val iterator: Iterator<Path> = markdownFilesStream.iterator()
            if (iterator.hasNext()) {
                val markdownTemplatePath = sourceDir.resolve(CONVENTIONAL_MARKDOWN_TEMPLATE_NAME)
                if (Files.exists(markdownTemplatePath)) {
                    val markdownTemplateName = markdownTemplatePath.toString().replace(".html", "")
                    while (iterator.hasNext()) {
                        val markdownFile = iterator.next()
                        try {
                            // Process markdown
                            val mdResult = markdownProcessor.process(markdownFile)

                            // Integrate into Thymeleaf template
                            val context = baseTemplateContext
                            context.setVariable("markdownContent", mdResult.contentResult)
                            context.setVariable("markdownHead", mdResult.headResult)
                            context.setVariable("markdownFileName", markdownFile.toString())
                            val result = templateEngine.process(markdownTemplateName, context)

                            // Write result to file
                            val destinationPath = targetDir.resolve(markdownFile.fileName.toString().replace(
                                    ".md",
                                    ".html"))
                            Files.write(destinationPath,
                                    listOf(result),
                                    Charset.forName("UTF-8"),
                                    StandardOpenOption.CREATE,
                                    StandardOpenOption.WRITE,
                                    StandardOpenOption.TRUNCATE_EXISTING)
                        } catch (e: IOException) {
                            LOG.error("Failed to generate document from markdown '{}': [{}]", markdownFile, e.message)
                        }
                    }
                } else {
                    LOG.warn(
                            "Not processing markdown files in '{}' because expected template file '{}.html' is missing",
                            sourceDir, markdownTemplatePath)
                }
            }
        }
        Files.newDirectoryStream(sourceDir, sassFilter).use { sassFilesStream ->
            for (sassFile in sassFilesStream) {
                try {
                    val isNotSassPartial = !sassFile.toFile().name.startsWith("_")
                    if (isNotSassPartial) {
                        val result = sassProcessor.process(sassFile)
                        val destinationPath = targetDir.resolve(sassFile.fileName.toString()
                                .replace(".sass", ".css")
                                .replace(".scss", ".css"))
                        Files.write(destinationPath,
                                listOf(result),
                                Charset.forName("UTF-8"),
                                StandardOpenOption.CREATE,
                                StandardOpenOption.WRITE,
                                StandardOpenOption.TRUNCATE_EXISTING)
                    }
                } catch (e: CompilationException) {
                    LOG.error("Failed to process SASS file '{}'", sassFile, e)
                }
            }
        }
        Files.newDirectoryStream(sourceDir, staticFileFilter).use { staticFilesStream ->
            for (staticFile in staticFilesStream) {
                Files.copy(staticFile, targetDir.resolve(staticFile.fileName), StandardCopyOption.REPLACE_EXISTING)
            }
        }
        Files.newDirectoryStream(sourceDir) { entry: Path ->
            (Files.isDirectory(entry) && !isCompleteIgnorable(entry.normalize())
                    && !isResultIgnorable(entry.normalize()))
        }.use { subDirStream ->
            for (subDir in subDirStream) {
                LOG.trace("I'm going to build pages in this subdirectory [{}]", subDir)
                buildPagesAndAssets(subDir, targetDir.resolve(subDir.fileName))
            }
        }
    }


    /**
     * Builds the crawling-related files sitemap.xml and robots.txt.
     */
    private fun buildCrawlFiles() {
        val sitemapFileName = "sitemap.xml"
        val baseUrl = settings!!.getProperty("baseUrl")
        val noSitemapFileInSourceDir = !Files.exists(sourceDirectoryPath.resolve(sitemapFileName))
        val baseSiteUrlIsSet = !StringUtils.isBlank(baseUrl)
        if (noSitemapFileInSourceDir && baseSiteUrlIsSet) {
            try {
                val siteStructure = getInstance(baseUrl)

                // Capture site structure
                val sitemapIgnorablesMatcher = from(destinationDirectoryPath,
                        readSitemapIgnorables(sourceDirectoryPath.resolve(C_3PO_IGNORE_FILE_NAME)))
                Files.walkFileTree(destinationDirectoryPath, object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val isHtmlFile = file.toString().endsWith(".html")
                        val isNotIgnorable = !sitemapIgnorablesMatcher.matches(file)
                        if (isHtmlFile && isNotIgnorable) {
                            siteStructure.add(destinationDirectoryPath.relativize(file))
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        if (sitemapIgnorablesMatcher.matches(dir)) {
                            LOG.debug("Ignoring directory '{}' for sitemap generation", dir.toAbsolutePath())
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        return FileVisitResult.CONTINUE
                    }
                })

                // Build the sitemap.xml and robots.txt
                try {
                    // sitemap.xml
                    LOG.info("Building a sitemap xml file")
                    val sitemapFilePath = destinationDirectoryPath.resolve(sitemapFileName)
                    generate(siteStructure, sitemapFilePath)

                    // robots.txt
                    // TODO: This check should be moved one level up
                    if (!Files.exists(sourceDirectoryPath.resolve(RobotsGenerator.ROBOTS_TXT_FILE_NAME))) {
                        try {
                            LOG.info("Building a robots.txt file")
                            generate(destinationDirectoryPath,
                                    StringUtils.trimmedJoin(URL_PATH_DELIMITER, baseUrl, sitemapFileName))
                        } catch (e: GenerationException) {
                            LOG.warn("Wasn't able to generate a '{}' file. Proceeding.",
                                    RobotsGenerator.ROBOTS_TXT_FILE_NAME, e)
                        }
                    } else {
                        LOG.info("Found a robots.txt file in '{}'. Tip: ensure that the URL to the sitemap.xml " +
                                "file is included in robots.txt.", sourceDirectoryPath)
                    }
                } catch (e: GenerationException) {
                    LOG.warn("Failed to generate sitemap xml file", e)
                }
            } catch (e: IOException) {
                LOG.warn("Failed to generate sitemap file.", e)
            }
        }
    }


    @Throws(IOException::class)
    private fun fingerprintAssets() {
        val stylesheetDir = destinationDirectoryPath.resolve("css")
        val assetSubstitutes = HashMap<String, String>()
        try {
            assetSubstitutes.putAll(fingerprintStylesheets(stylesheetDir, destinationDirectoryPath))

            // TODO: Fingerprint media files, JS and so on
        } catch (e: NoSuchAlgorithmException) {
            LOG.warn("Failed to fingerprint assets. Beware that your cache busting may not work.")
        }

        // Replace references
        LOG.info(assetSubstitutes.toString())
        replaceAssetsReferences(destinationDirectoryPath, assetSubstitutes, settings!!)
    }


    private fun setupTemplateEngine(sourceDirectoryPath: Path): TemplateEngine {
        val templateEngine = TemplateEngine()

        // Note: we need two FileTemplateResolvers
        // one that is able to deal with absolute path template names like 'D:/data/dev/blog/index'
        // and one that is able to resolve relative path template names like '_layouts/main-layout'
        templateEngine.addTemplateResolver(newTemplateResolver(sourceDirectoryPath.toAbsolutePath()))
        templateEngine.addTemplateResolver(newTemplateResolver())
        templateEngine.addDialect(LayoutDialect(EnhancedGroupingStrategy()))
        return templateEngine
    }


    private fun newTemplateResolver(prefix: Path? = null): TemplateResolver {
        val templateResolver: TemplateResolver = FileTemplateResolver()

        // Instead of 'HTML5' this template mode allows void elements such as meta to have no closing tags
        templateResolver.templateMode = "LEGACYHTML5"
        templateResolver.prefix = if (prefix != null) "$prefix/" else ""
        templateResolver.suffix = ".html"
        templateResolver.characterEncoding = "UTF-8"
        return templateResolver
    }


    @Throws(IOException::class)
    private fun isCompleteIgnorable(path: Path) =
            completeIgnorablesMatcher.matches(path) || Files.exists(destinationDirectoryPath) && Files.exists(path)
                    && Files.isSameFile(path, destinationDirectoryPath)


    @Throws(IOException::class)
    private fun isResultIgnorable(path: Path) =
            resultIgnorablesMatcher.matches(path) || Files.exists(destinationDirectoryPath) && Files.exists(path)
                    && Files.isSameFile(path, destinationDirectoryPath)


    private fun updateIgnorables(ignorablesFile: Path) {
        val newCompleteIgnorables = getCompleteIgnorables(ignorablesFile)
        val newResultIgnorables = readResultIgnorables(ignorablesFile)
        cleanOutputFromAddedIgnorables(newCompleteIgnorables, completeIgnorablesMatcher.globPatterns)
        cleanOutputFromAddedIgnorables(newResultIgnorables, resultIgnorablesMatcher.globPatterns)
        completeIgnorablesMatcher = from(destinationDirectoryPath, newCompleteIgnorables)
        resultIgnorablesMatcher = from(destinationDirectoryPath, newResultIgnorables)
    }


    private fun cleanOutputFromAddedIgnorables(newIgnorables: List<String>, presentIgnorables: List<String>) {
        val addedIgnorables: MutableList<String> = ArrayList(newIgnorables)
        addedIgnorables.removeAll(presentIgnorables)
        try {
            if (!addedIgnorables.isEmpty() && Files.exists(destinationDirectoryPath)) {
                LOG.debug("Removing added ignorables '{}' after ignorables update", addedIgnorables)
                removeIgnorables(addedIgnorables, destinationDirectoryPath)
            }
        } catch (e: IOException) {
            LOG.error("IO error occurred when removing ignored files from target directory '{}'",
                    destinationDirectoryPath, e)
        }

        // Note: No action needed when file patterns have been **removed** from newIgnorables since
        // these should be included when c-3po processes the site the next time
    }

    /**
     * Removes the files and directories defined by ignorables from withing the given rootDirectory.
     *
     * @param ignorables a list of glob patterns defining the files and directories to remove
     * @param rootDirectory the root directory
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun removeIgnorables(ignorables: List<String>, rootDirectory: Path) {
        val ignorablesMatcher = from(rootDirectory, ignorables)
        Files.walkFileTree(rootDirectory, object : SimpleFileVisitor<Path>() {
            @Throws(IOException::class)
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes) =
                    if (ignorablesMatcher.matches(rootDirectory.relativize(dir).normalize())) {
                        LOG.debug("Deleting directory '{}'", dir)
                        deleteDirectory(dir)
                        FileVisitResult.SKIP_SUBTREE
                    } else FileVisitResult.CONTINUE

            @Throws(IOException::class)
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult? =
                    if (ignorablesMatcher.matches(rootDirectory.relativize(file).normalize())) {
                        LOG.debug("Deleting file '{}'", file)
                        Files.delete(file)
                        null
                    } else FileVisitResult.CONTINUE
        })
    }


    @Throws(IOException::class)
    private fun deleteDirectory(dir: Path) {
        Files.walkFileTree(dir, object : SimpleFileVisitor<Path>() {
            @Throws(IOException::class)
            override fun visitFile(file: Path, attrs: BasicFileAttributes) =
                    FileVisitResult.CONTINUE.also { Files.delete(file) }

            @Throws(IOException::class)
            override fun visitFileFailed(file: Path, exc: IOException) =
                    FileVisitResult.CONTINUE.also { Files.delete(file) }

            @Throws(IOException::class)
            override fun postVisitDirectory(dir: Path, exc: IOException?) =
                    if (exc == null) {
                        Files.delete(dir)
                        FileVisitResult.CONTINUE
                    } else {
                        throw exc
                    }
        })
    }


    /**
     * Enhancing / fixing Layout Dialect's GroupingStrategy which doesn't know about
     * icon elements in &lt;head&gt;.
     *
     * E.g. I had the problem of using &lt;link&gt; rel="icon"... in the layout
     * which is not known to GroupingStrategy. This somehow caused the very
     * important &lt;base&gt; element to be at the bottom of head which resulted in
     * CSS files etc. to not resolve correctly.
     */
    private class EnhancedGroupingStrategy internal constructor() : SortingStrategy {
        private val delegate = GroupingStrategy()

        override fun findPositionForContent(decoratorNodes: List<Node>, contentNode: Node) =
                if (contentNode is Element && contentNode.normalizedName == "base") {
                    0
                } else {
                    delegate.findPositionForContent(decoratorNodes, contentNode)
                }
    }


    companion object {
        private val LOG = LoggerFactory.getLogger(SiteGenerator::class.java)
        private const val C_3PO_IGNORE_FILE_NAME = ".c3poignore"
        private const val C_3PO_SETTINGS_FILE_NAME = ".c3posettings"
        private const val CONVENTIONAL_MARKDOWN_TEMPLATE_NAME = "md-template.html"

        /**
         * Factory method that creates a SiteGenerator from command line arguments.
         */
        @JvmStatic
        fun fromCmdArguments(cmdArguments: CmdArguments): SiteGenerator {
            Objects.requireNonNull(cmdArguments)
            val sourceDirectoryPath = Paths.get(cmdArguments.sourceDirectory)
            ensureValidSourceDirectory(sourceDirectoryPath)

            // Read in settings
            val settingsFilePath = sourceDirectoryPath.resolve(C_3PO_SETTINGS_FILE_NAME)
            var settings: Properties? = null
            try {
                settings = readSettings(settingsFilePath)
            } catch (e: IOException) {
                LOG.warn("Failed to load settings from file '{}'", settingsFilePath)
            }

            // Construct instance
            return SiteGenerator(sourceDirectoryPath,
                    Paths.get(cmdArguments.destinationDirectory),
                    cmdArguments.shouldFingerprintAssets(),
                    getCompleteIgnorables(sourceDirectoryPath),
                    readResultIgnorables(sourceDirectoryPath.resolve(C_3PO_IGNORE_FILE_NAME)),
                    settings)
        }


        private fun ensureValidSourceDirectory(sourceDirectoryPath: Path) {
            require(Files.exists(sourceDirectoryPath)) { "Source directory '$sourceDirectoryPath' does not exist." }
            require(Files.isDirectory(sourceDirectoryPath)) {
                "Source directory '$sourceDirectoryPath' is not a directory."
            }
        }


        @Throws(IOException::class)
        private fun readSettings(settingsFilePath: Path): Properties {
            val properties = Properties()
            if (Files.exists(settingsFilePath)) {
                properties.load(Files.newInputStream(settingsFilePath))
            }
            return properties
        }


        /**
         * Reads complete ignorables from ignore file and adds C-3PO standard files.
         */
        private fun getCompleteIgnorables(baseDirectory: Path): List<String> {
            val ignorables: MutableList<String> = ArrayList()

            // System standard ignorables
            ignorables.add(C_3PO_IGNORE_FILE_NAME)
            ignorables.add(C_3PO_SETTINGS_FILE_NAME)

            // User-specific ignorables
            val ignorablesFromFile = readCompleteIgnorables(baseDirectory.resolve(C_3PO_IGNORE_FILE_NAME))
            ignorables.addAll(ignorablesFromFile)
            return ignorables
        }
    }
}
