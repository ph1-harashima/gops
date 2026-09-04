package com.glv.gsysportal.service.integration;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Phase 9-B (Production PO Workflow §10): the Production shape of the
 * Import Folder hand-off - Config/Interface/Adapter implemented per the
 * user's explicit instruction, deliberately never connected. Real
 * Production Path/credentials/transport (SMB share, SFTP, or whatever the
 * eventual Customer-confirmed mechanism turns out to be - design doc §16's
 * Decision Matrix recommends a dedicated Integration Worker, credential-
 * isolated from the Web-facing Portal Server) are Source-unconfirmed
 * (design doc §21 CUSTOMER REVIEW #9) and therefore NOT implemented here -
 * only the seam that a future Phase would fill in once that answer exists.
 *
 * <p><b>This class can never actually run in this environment</b>:
 * {@code @Profile("production")} means Spring only creates this bean under
 * an active "production" profile, and
 * {@link com.glv.gsysportal.safety.SafetyGuardEnvironmentPostProcessor}
 * refuses to even start {@code SpringApplication.run()} under that profile
 * (or any profile outside local/demo/test) - so this code path is provably
 * unreachable, not merely "not tested". No test exercises this class's
 * {@link #place} method for that reason (a Contract Test asserting the
 * exception message would be the only thing to verify, and would just
 * duplicate this Javadoc).
 */
@Component
@Profile("production")
public class ProductionImportFolderAdapter implements OfficialPoImportFolderAdapter {

    @Override
    public void place(byte[] excelBytes, String fileName) {
        throw new UnsupportedOperationException(
                "Production Official PO Import Folder hand-off is not implemented - "
                        + "the real transport/credential mechanism is unresolved CUSTOMER REVIEW "
                        + "(docs/official-po-integration-detailed-design.md 21章 #9). "
                        + "This code path is unreachable in this environment (Safety Gate).");
    }
}
