package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.ManufacturerChannel;
import com.glv.gsysportal.dto.request.ManufacturerChannelRequest;
import com.glv.gsysportal.dto.response.ManufacturerChannelResponse;
import com.glv.gsysportal.exception.BrandCodeNotFoundException;
import com.glv.gsysportal.exception.DuplicateManufacturerChannelException;
import com.glv.gsysportal.exception.InvalidManufacturerChannelException;
import com.glv.gsysportal.exception.ManufacturerChannelNotFoundException;
import com.glv.gsysportal.exception.SupplierCodeNotFoundException;
import com.glv.gsysportal.repository.legacy.OfficialPoPreflightReadRepository;
import com.glv.gsysportal.repository.prototype.ManufacturerChannelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Phase 9-D: Manufacturer Channel Master CRUD - mirrors
 * {@code SupplierContactService}'s shape exactly (ADMIN only, Backend-
 * enforced Validation, same Legacy READ ONLY Supplier/Brand existence
 * check).
 */
@Service
public class ManufacturerChannelService {

    private static final Set<String> VALID_CHANNELS = Set.of(
            ManufacturerChannel.CHANNEL_EMAIL, ManufacturerChannel.CHANNEL_EDI);

    private final ManufacturerChannelRepository repository;
    private final OfficialPoPreflightReadRepository legacyMasterReadRepository;

    public ManufacturerChannelService(ManufacturerChannelRepository repository,
                                       OfficialPoPreflightReadRepository legacyMasterReadRepository) {
        this.repository = repository;
        this.legacyMasterReadRepository = legacyMasterReadRepository;
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public List<ManufacturerChannelResponse> list() {
        return repository.findAllByOrderBySupplierCodeAscBrandCodeAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(transactionManager = "prototypeTransactionManager")
    public ManufacturerChannelResponse create(ManufacturerChannelRequest request, String performedBy) {
        validate(request);
        rejectDuplicate(request.supplierCode(), request.brandCode());

        OffsetDateTime now = OffsetDateTime.now();
        ManufacturerChannel channel = new ManufacturerChannel();
        applyRequest(channel, request);
        channel.setCreatedBy(performedBy);
        channel.setCreatedAt(now);
        channel.setUpdatedBy(performedBy);
        channel.setUpdatedAt(now);

        return toResponse(repository.save(channel));
    }

    @Transactional(transactionManager = "prototypeTransactionManager")
    public ManufacturerChannelResponse update(Long id, ManufacturerChannelRequest request, String performedBy) {
        ManufacturerChannel channel = repository.findById(id).orElseThrow(() -> new ManufacturerChannelNotFoundException(id));
        validate(request);
        boolean identityChanged = !channel.getSupplierCode().equals(request.supplierCode())
                || !Objects.equals(channel.getBrandCode(), request.brandCode());
        if (identityChanged && request.active()) {
            rejectDuplicate(request.supplierCode(), request.brandCode());
        }

        applyRequest(channel, request);
        channel.setUpdatedBy(performedBy);
        channel.setUpdatedAt(OffsetDateTime.now());

        return toResponse(repository.save(channel));
    }

    private void applyRequest(ManufacturerChannel channel, ManufacturerChannelRequest request) {
        channel.setSupplierCode(request.supplierCode());
        channel.setBrandCode(request.brandCode());
        channel.setChannel(request.channel());
        channel.setActive(request.active());
    }

    private void validate(ManufacturerChannelRequest request) {
        if (!VALID_CHANNELS.contains(request.channel())) {
            throw new InvalidManufacturerChannelException(request.channel());
        }
        if (!legacyMasterReadRepository.supplierExists(request.supplierCode())) {
            throw new SupplierCodeNotFoundException(request.supplierCode());
        }
        if (request.brandCode() != null && legacyMasterReadRepository.findBrandName(request.brandCode()) == null) {
            throw new BrandCodeNotFoundException(request.brandCode());
        }
    }

    private void rejectDuplicate(String supplierCode, String brandCode) {
        boolean duplicate = brandCode == null
                ? repository.existsBySupplierCodeAndBrandCodeIsNullAndActiveTrue(supplierCode)
                : repository.existsBySupplierCodeAndBrandCodeAndActiveTrue(supplierCode, brandCode);
        if (duplicate) {
            throw new DuplicateManufacturerChannelException(supplierCode, brandCode);
        }
    }

    private ManufacturerChannelResponse toResponse(ManufacturerChannel c) {
        return new ManufacturerChannelResponse(
                c.getId(), c.getSupplierCode(), c.getBrandCode(), c.getChannel(), c.isActive(),
                c.getCreatedBy(), c.getCreatedAt(), c.getUpdatedBy(), c.getUpdatedAt()
        );
    }
}
