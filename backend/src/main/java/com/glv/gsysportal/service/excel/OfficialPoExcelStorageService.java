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
 * Phase 9-A: local-filesystem store for Excel bytes {@link OfficialPoExcelGenerator}
 * produces - the audit/history copy design doc §15 calls "S3 (or Portal
 * Local)". Deliberately separate from Phase 9-B's Import Folder Adapter:
 * this is Portal's own artifact store (always local, always safe), the
 * Import Folder is the Legacy hand-off destination (Phase 9-B, Port+Adapter
 * with a Production stub).
 *
 * <p>Root directory is entirely config-driven -
 * {@code app.official-po.storage.base-dir}. The active Spring profile is
 * appended to it here (read from {@link Environment}, NOT a
 * {@code ${spring.profiles.active}} YAML placeholder - {@code @ActiveProfiles}
 * -driven tests set active profiles directly on the Environment without
 * populating a resolvable property by that name) so local/demo/test never
 * share a directory, with no path ever hardcoded in Java (Production Path
 * §10's "Hardcodeしない" applies equally here, even though this store
 * itself is never a real Production integration point -
 * {@link com.glv.gsysportal.safety.SafetyGuardEnvironmentPostProcessor}
 * refuses to start under any profile but local/demo/test regardless).
 */
@Service
public class OfficialPoExcelStorageService {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final Path baseDir;

    public OfficialPoExcelStorageService(@Value("${app.official-po.storage.base-dir}") String baseDir,
                                          Environment environment) {
        String[] activeProfiles = environment.getActiveProfiles();
        String profile = activeProfiles.length > 0 ? activeProfiles[0] : "default";
        this.baseDir = Paths.get(baseDir, profile);
    }

    /** Stores {@code bytes} under a name unique per (orderId, revisionNo,
     * generation attempt) and returns the fileKey - a relative filename, not
     * an absolute path, so it can be persisted safely as
     * {@code official_po_integration_request.generated_file_key}. */
    public String store(Long orderId, int revisionNo, byte[] bytes) {
        try {
            Files.createDirectories(baseDir);
            String fileName = "order-" + orderId + "-rev" + revisionNo + "-" + OffsetDateTime.now().format(FILE_TS) + ".xlsx";
            Files.write(baseDir.resolve(fileName), bytes);
            return fileName;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store Official PO Excel for Order " + orderId, e);
        }
    }

    public byte[] load(String fileKey) {
        try {
            return Files.readAllBytes(baseDir.resolve(fileKey));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load Official PO Excel: " + fileKey, e);
        }
    }
}
