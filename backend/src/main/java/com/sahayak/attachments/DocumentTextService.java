package com.sahayak.attachments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Turns an uploaded document (pdf, docx, txt, md, csv) into plain text via
 * Apache Tika, server-side — the model is never asked to "open" a file.
 *
 * Output is size-capped so a huge document can't blow the model's context
 * window; truncation is marked honestly instead of silently cutting off.
 * File contents are never logged — only filenames and failure reasons.
 */
@Service
public class DocumentTextService {

    /** Generous but safe: ~5k tokens per document. */
    public static final int MAX_CHARS_PER_DOC = 20_000;

    private static final Logger log = LoggerFactory.getLogger(DocumentTextService.class);

    /**
     * Extracted, capped text of one stored document, or null when the file
     * can't be parsed (corrupt, password-protected, …) — the caller tells the
     * model so it can be honest with the user.
     */
    public String extract(StoredFile file) {
        try {
            // The filename hints Tika's type detection (e.g. docx vs zip).
            ByteArrayResource resource = new ByteArrayResource(file.getContent()) {
                @Override
                public String getFilename() {
                    return file.getFilename();
                }
            };
            List<Document> parts = new TikaDocumentReader(resource).get();
            String text = parts.stream()
                    .map(Document::getText)
                    .filter(t -> t != null && !t.isBlank())
                    .collect(Collectors.joining("\n"))
                    .strip();
            if (text.isEmpty()) {
                return null; // e.g. a scanned PDF with no text layer
            }
            if (text.length() > MAX_CHARS_PER_DOC) {
                int cut = text.length() - MAX_CHARS_PER_DOC;
                text = text.substring(0, MAX_CHARS_PER_DOC)
                        + "\n[… truncated: " + cut + " more characters not shown]";
            }
            return text;
        } catch (Exception e) {
            log.warn("Could not extract text from \"{}\": {}", file.getFilename(), e.getMessage());
            return null;
        }
    }
}
