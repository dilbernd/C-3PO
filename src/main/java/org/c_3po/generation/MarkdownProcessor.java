package org.c_3po.generation;

import org.commonmark.html.HtmlRenderer;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

/**
 * Responsible for processing markdown files.
 */
class MarkdownProcessor {
    private final static Logger LOG = LoggerFactory.getLogger(MarkdownProcessor.class);

    private final Parser parser;
    private final HtmlRenderer htmlRenderer;

    private MarkdownProcessor() {
        this.parser = Parser.builder().build();
        this.htmlRenderer = HtmlRenderer.builder().escapeHtml(true).build();
    }

    static MarkdownProcessor getInstance() {
        return new MarkdownProcessor();
    }

    String process(Path markdownFile) throws IOException {
        Objects.requireNonNull(markdownFile);
        if (Files.exists(markdownFile)) {
            List<String> lines = Files.readAllLines(markdownFile);
            StringBuffer sb = new StringBuffer(lines.size());
            lines.stream().forEach(l -> sb.append(l).append(System.lineSeparator()));
            Node document = parser.parse(sb.toString());
            String output = htmlRenderer.render(document);
            return output;
        } else {
            throw new FileNotFoundException("File '" + markdownFile.toAbsolutePath().toString() + "' not found.");
        }
    }
}
