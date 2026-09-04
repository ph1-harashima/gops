package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.ManufacturerChannel;
import com.glv.gsysportal.dto.request.ManufacturerChannelRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Phase 9-D: Resolution priority - mirrors
 * SupplierContactResolutionServiceIntegrationTest's own precedence tests. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class ManufacturerChannelResolutionServiceIntegrationTest {

    private static final String ADMIN = "admin-tester";

    @Autowired
    private ManufacturerChannelService channelService;
    @Autowired
    private ManufacturerChannelResolutionService resolutionService;

    @Test
    void noMasterRowResolvesNull_neverGuessed() {
        assertNull(resolutionService.resolve("SUP_ALPHA", "BR_OUTDOOR"));
    }

    @Test
    void brandSpecificTierWinsOverSupplierOnlyTier() {
        channelService.create(new ManufacturerChannelRequest("SUP_ALPHA", null, ManufacturerChannel.CHANNEL_EMAIL, true), ADMIN);
        channelService.create(new ManufacturerChannelRequest("SUP_ALPHA", "BR_OUTDOOR", ManufacturerChannel.CHANNEL_EDI, true), ADMIN);

        assertEquals(ManufacturerChannel.CHANNEL_EDI, resolutionService.resolve("SUP_ALPHA", "BR_OUTDOOR"));
    }

    @Test
    void fallsBackToSupplierOnlyTierWhenNoBrandSpecificMatch() {
        channelService.create(new ManufacturerChannelRequest("SUP_ALPHA", null, ManufacturerChannel.CHANNEL_EMAIL, true), ADMIN);

        assertEquals(ManufacturerChannel.CHANNEL_EMAIL, resolutionService.resolve("SUP_ALPHA", "BR_OUTDOOR"));
    }

    @Test
    void inactiveRowsAreNeverResolved() {
        var created = channelService.create(new ManufacturerChannelRequest("SUP_ALPHA", "BR_OUTDOOR", ManufacturerChannel.CHANNEL_EDI, true), ADMIN);
        channelService.update(created.id(), new ManufacturerChannelRequest("SUP_ALPHA", "BR_OUTDOOR", ManufacturerChannel.CHANNEL_EDI, false), ADMIN);

        assertNull(resolutionService.resolve("SUP_ALPHA", "BR_OUTDOOR"));
    }
}
