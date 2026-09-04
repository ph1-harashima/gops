package com.glv.gsysportal.service.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Phase 9-B: local/demo/test-only Import Folder Adapter. Writes to a plain
 * local directory ({@code {base-dir}/{profile}/upload}, mirroring
 * {@code PrOfficialPoImportBatch}'s own Upload/work/backup layout name -
 * design doc §7 - though this Adapter only ever writes to Upload; work/
 * backup belong to the Batch itself, which does not run in this
 * environment). Never a network/UNC path, never Production - the base
 * directory root is config-driven (never hardcoded), and the active
 * profile is appended in code (not a YAML placeholder - see
 * {@code OfficialPoExcelStorageService}'s Javadoc for why), so
 * local/demo/test can never collide, matching the "接続方式を
 * Hardcodeしない" Working Assumption (§3) even though this Adapter itself
 * is never the real Production connection.
 */
@Component
@Profile({"local", "demo", "test"})
public class LocalFilesystemImportFolderAdapter implements OfficialPoImportFolderAdapter {

    private final Path uploadDir;

    public LocalFilesystemImportFolderAdapter(@Value("${app.official-po.import-folder.base-dir}") String baseDir,
                                               Environment environment) {
        String[] activeProfiles = environment.getActiveProfiles();
        String profile = activeProfiles.length > 0 ? activeProfiles[0] : "default";
        this.uploadDir = Paths.get(baseDir, profile, "upload");
    }

    @Override
    public void place(byte[] excelBytes, String fileName) {
        try {
            Files.createDirectories(uploadDir);
            Files.write(uploadDir.resolve(fileName), excelBytes);
        } catch (IOException e) {
            throw new OfficialPoImportFolderWriteException("Failed to write Official PO Excel to local Import Folder: " + fileName, e);
        }
    }
}
