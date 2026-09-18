package com.glv.gsysportal.service.excel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Gap Analysis C-1 (docs/gulliver-20260917-phase1-gap-analysis.md 7章):
 * local-filesystem store for the Official PO PDF, mirroring
 * {@link OfficialPoExcelStorageService}'s own design exactly (same config
 * key, same per-Profile subdirectory discipline) but under its own "pdf"
 * subfolder so Excel/PDF artifacts never collide even if a future Phase
 * reuses the same base file name for both. Never the Import Folder hand-off
 * destination - PDF is a Portal-only reference artifact (7章's Gap Matrix).
 */
@Service
public class OfficialPoPdfStorageService {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final Path baseDir;

    public OfficialPoPdfStorageService(@Value("${app.official-po.storage.base-dir}") String baseDir,
                                        Environment environment) {
        String[] activeProfiles = environment.getActiveProfiles();
        String profile = activeProfiles.length > 0 ? activeProfiles[0] : "default";
        this.baseDir = Paths.get(baseDir, profile, "pdf");
    }

    public String store(Long orderId, int revisionNo, byte[] bytes) {
        try {
            Files.createDirectories(baseDir);
            String fileName = "order-" + orderId + "-rev" + revisionNo + "-" + OffsetDateTime.now().format(FILE_TS) + ".pdf";
            Files.write(baseDir.resolve(fileName), bytes);
            return fileName;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store Official PO PDF for Order " + orderId, e);
        }
    }

    public byte[] load(String fileKey) {
        try {
            return Files.readAllBytes(baseDir.resolve(fileKey));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load Official PO PDF: " + fileKey, e);
        }
    }
}
