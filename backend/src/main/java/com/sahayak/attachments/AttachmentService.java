package com.sahayak.attachments;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Validates and stores uploaded attachments. Every rule the client enforces is
 * re-checked here — the client guard is only for UX, this is the real gate.
 * Type is verified by extension AND (for images) by magic bytes, so a renamed
 * or spoofed file is rejected rather than trusted.
 */
@Service
public class AttachmentService {

    public static final long MAX_FILE_BYTES = 15L * 1024 * 1024; // 15 MB — mirrors the client

    private static final Set<String> IMAGE_EXTS = Set.of("png", "jpg", "jpeg", "webp", "gif");
    private static final Set<String> DOC_EXTS = Set.of("pdf", "docx", "txt", "md", "csv");

    /** Canonical MIME per extension — we store our own value, never the client's claim. */
    private static final Map<String, String> MIME_BY_EXT = Map.ofEntries(
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("webp", "image/webp"),
            Map.entry("gif", "image/gif"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("txt", "text/plain"),
            Map.entry("md", "text/markdown"),
            Map.entry("csv", "text/csv"));

    /** What a controller returns to the client after a successful upload. */
    public record AttachmentRef(Long id, String filename, String mime, String kind, long size) {
        static AttachmentRef from(StoredFile f) {
            return new AttachmentRef(f.getId(), f.getFilename(), f.getMime(), f.getKind(), f.getSizeBytes());
        }
    }

    private final StoredFileRepository repository;

    public AttachmentService(StoredFileRepository repository) {
        this.repository = repository;
    }

    public AttachmentRef store(Long userId, Long conversationId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw bad("The uploaded file is empty.");
        }
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            throw bad("The uploaded file has no name.");
        }
        String ext = extensionOf(original);
        boolean isImage = IMAGE_EXTS.contains(ext);
        boolean isDoc = DOC_EXTS.contains(ext);
        if (!isImage && !isDoc) {
            throw bad("\"" + original + "\" isn't a supported file type.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw bad("\"" + original + "\" is larger than the 15 MB limit.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read the uploaded file.");
        }
        if (bytes.length == 0) {
            throw bad("The uploaded file is empty.");
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw bad("\"" + original + "\" is larger than the 15 MB limit.");
        }
        // For images, confirm the bytes actually match the claimed type.
        if (isImage && !imageBytesMatch(ext, bytes)) {
            throw bad("\"" + original + "\" doesn't look like a valid " + ext.toUpperCase() + " image.");
        }

        StoredFile stored = new StoredFile();
        stored.setUserId(userId);
        stored.setConversationId(conversationId);
        stored.setFilename(sanitizeName(original));
        stored.setMime(MIME_BY_EXT.getOrDefault(ext, "application/octet-stream"));
        stored.setKind(isImage ? "image" : "doc");
        stored.setSizeBytes(bytes.length);
        stored.setContent(bytes);
        return AttachmentRef.from(repository.save(stored));
    }

    /** Owner-scoped read; empty if the file doesn't exist or belongs to someone else. */
    public Optional<StoredFile> fetch(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId);
    }

    /**
     * Owner-scoped batch read for a chat turn. Ids that don't exist or belong
     * to someone else are silently dropped — the model only ever sees the
     * caller's own files.
     */
    public List<StoredFile> fetchAll(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .distinct()
                .limit(5)
                .map(id -> repository.findByIdAndUserId(id, userId))
                .flatMap(Optional::stream)
                .toList();
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
    }

    /** Strips any path components a client may have smuggled in the filename. */
    private static String sanitizeName(String name) {
        String base = name.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.strip();
        if (base.isEmpty()) {
            base = "file";
        }
        return base.length() > 255 ? base.substring(base.length() - 255) : base;
    }

    /** Cheap magic-byte check so a .png that's really something else is rejected. */
    private static boolean imageBytesMatch(String ext, byte[] b) {
        return switch (ext) {
            case "png" -> b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47;
            case "jpg", "jpeg" -> b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
            case "gif" -> b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8';
            case "webp" -> b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            default -> false;
        };
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
