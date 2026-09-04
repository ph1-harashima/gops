package com.glv.gsysportal.service.integration;

/**
 * Phase 9-B (Production PO Workflow §C/Phase 2): Port for handing an
 * already-generated Official PO Excel off to whatever mechanism ultimately
 * feeds {@code PrOfficialPoImportBatch} (design doc §7/§8 - "Fileを置くだけ"
 * is the recommended Integration shape; Portal never touches the Batch's
 * own trigger/scheduling).
 *
 * <p>Deliberately just this one method - no polling, no Success Detection
 * (design doc §9, still a later Phase). "Placed" means only "the byte[]
 * reached wherever this Adapter is configured to write it", never "Legacy
 * confirmed receipt".
 *
 * <p>Exactly one implementation is ever active per Spring profile
 * ({@link LocalFilesystemImportFolderAdapter} for local/demo/test,
 * {@link ProductionImportFolderAdapter} for production - unreachable here,
 * {@link com.glv.gsysportal.safety.SafetyGuardEnvironmentPostProcessor}
 * refuses to start under any profile but local/demo/test regardless).
 */
public interface OfficialPoImportFolderAdapter {

    /** @throws OfficialPoImportFolderWriteException on any failure to place
     *          the File - the caller (OfficialPoIntegrationService) maps
     *          this to the Integration Request's FAILED state, never lets
     *          it propagate as a raw 500. */
    void place(byte[] excelBytes, String fileName);
}
