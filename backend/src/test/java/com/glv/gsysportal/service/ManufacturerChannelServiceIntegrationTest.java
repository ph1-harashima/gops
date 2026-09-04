package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.ManufacturerChannel;
import com.glv.gsysportal.dto.request.ManufacturerChannelRequest;
import com.glv.gsysportal.dto.response.ManufacturerChannelResponse;
import com.glv.gsysportal.exception.BrandCodeNotFoundException;
import com.glv.gsysportal.exception.DuplicateManufacturerChannelException;
import com.glv.gsysportal.exception.InvalidManufacturerChannelException;
import com.glv.gsysportal.exception.ManufacturerChannelNotFoundException;
import com.glv.gsysportal.exception.SupplierCodeNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 9-D: Manufacturer Channel Master CRUD - mirrors
 * SupplierContactServiceIntegrationTest's own coverage shape. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class ManufacturerChannelServiceIntegrationTest {

    private static final String ADMIN = "admin-tester";

    @Autowired
    private ManufacturerChannelService service;

    @Test
    void createRejectsInvalidChannel() {
        assertThrows(InvalidManufacturerChannelException.class, () -> service.create(
                new ManufacturerChannelRequest("SUP_ALPHA", null, "FAX", true), ADMIN));
    }

    @Test
    void createRejectsUnknownSupplierCode() {
        assertThrows(SupplierCodeNotFoundException.class, () -> service.create(
                new ManufacturerChannelRequest("NO_SUCH_SUPPLIER", null, ManufacturerChannel.CHANNEL_EMAIL, true), ADMIN));
    }

    @Test
    void createRejectsUnknownBrandCode() {
        assertThrows(BrandCodeNotFoundException.class, () -> service.create(
                new ManufacturerChannelRequest("SUP_ALPHA", "NO_SUCH_BRAND", ManufacturerChannel.CHANNEL_EMAIL, true), ADMIN));
    }

    @Test
    void createAndListRoundTrips() {
        ManufacturerChannelResponse created = service.create(
                new ManufacturerChannelRequest("SUP_ALPHA", "BR_OUTDOOR", ManufacturerChannel.CHANNEL_EDI, true), ADMIN);

        assertEquals(ManufacturerChannel.CHANNEL_EDI, created.channel());
        assertTrue(service.list().stream().anyMatch(c -> c.id().equals(created.id())));
    }

    @Test
    void createRejectsDuplicateActiveIdentity() {
        service.create(new ManufacturerChannelRequest("SUP_ALPHA", "BR_OUTDOOR", ManufacturerChannel.CHANNEL_EMAIL, true), ADMIN);

        assertThrows(DuplicateManufacturerChannelException.class, () -> service.create(
                new ManufacturerChannelRequest("SUP_ALPHA", "BR_OUTDOOR", ManufacturerChannel.CHANNEL_EDI, true), ADMIN));
    }

    @Test
    void reAddingAfterDeactivatingIsAllowed() {
        ManufacturerChannelResponse created = service.create(
                new ManufacturerChannelRequest("SUP_ALPHA", "BR_OUTDOOR", ManufacturerChannel.CHANNEL_EMAIL, true), ADMIN);
        service.update(created.id(), new ManufacturerChannelRequest("SUP_ALPHA", "BR_OUTDOOR", ManufacturerChannel.CHANNEL_EMAIL, false), ADMIN);

        ManufacturerChannelResponse recreated = service.create(
                new ManufacturerChannelRequest("SUP_ALPHA", "BR_OUTDOOR", ManufacturerChannel.CHANNEL_EDI, true), ADMIN);
        assertEquals(ManufacturerChannel.CHANNEL_EDI, recreated.channel());
    }

    @Test
    void updateOnUnknownIdThrowsNotFound() {
        assertThrows(ManufacturerChannelNotFoundException.class, () -> service.update(999_999L,
                new ManufacturerChannelRequest("SUP_ALPHA", null, ManufacturerChannel.CHANNEL_EMAIL, true), ADMIN));
    }
}
