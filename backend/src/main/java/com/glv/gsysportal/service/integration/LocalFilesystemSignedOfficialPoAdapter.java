package com.glv.gsysportal.service.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Phase C: local/demo/test-only Signed PO storage, mirroring {@code
 * OfficialPoPdfStorageService}'s own design exactly (same config key, same
 * per-Profile subdirectory discipline) but under its own "signed"
 * subfolder so a signed PDF can never collide with, or be confused for,
 * the unsigned/formal PDF artifact even though both live under the same
 * base directory. See {@link SignedOfficialPoAdapter}'s own Javadoc for
 * why no Production implementation exists this Phase.
 */
@Component
@Profile({"local", "demo", "test"})
public class LocalFilesystemSignedOfficialPoAdapter implements SignedOfficialPoAdapter {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final Path baseDir;

    public LocalFilesystemSignedOfficialPoAdapter(@Value("${app.official-po.storage.base-dir}") String baseDir,
                                                    Environment environment) {
        String[] activeProfiles = environment.getActiveProfiles();
        String profile = activeProfiles.length > 0 ? activeProfiles[0] : "default";
        this.baseDir = Paths.get(baseDir, profile, "signed");
    }

    @Override
    public String storeSignedPdf(Long orderId, int revisionNo, byte[] signedPdfBytes) {
        try {
            Files.createDirectories(baseDir);
            String fileName = "order-" + orderId + "-rev" + revisionNo + "-signed-" + OffsetDateTime.now().format(FILE_TS) + ".pdf";
            Files.write(baseDir.resolve(fileName), signedPdfBytes);
            return fileName;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store signed Official PO PDF for Order " + orderId, e);
        }
    }

    @Override
    public byte[] loadSignedPdf(String fileKey) {
        try {
            return Files.readAllBytes(baseDir.resolve(fileKey));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load signed Official PO PDF: " + fileKey, e);
        }
    }
}
