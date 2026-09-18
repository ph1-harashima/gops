package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.PortalMailSettings;
import com.glv.gsysportal.dto.response.PortalMailSettingsResponse;
import com.glv.gsysportal.repository.prototype.PortalMailSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Gap Analysis §11 (docs/gulliver-20260917-phase1-gap-analysis.md 11章):
 * Default CC Foundation. {@code get()} is used ONLY by the Frontend to
 * prefill the send-time CC Override field - never read by
 * {@code EmailSendService} itself, so this Service has no path that could
 * turn into an automatic "always CC" Business Rule.
 */
@Service
public class PortalMailSettingsService {

    private final PortalMailSettingsRepository repository;

    public PortalMailSettingsService(PortalMailSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public PortalMailSettingsResponse get() {
        return repository.findById(PortalMailSettings.SINGLETON_ID)
                .map(this::toResponse)
                .orElseGet(() -> new PortalMailSettingsResponse(List.of(), null, null));
    }

    @Transactional(transactionManager = "prototypeTransactionManager")
    public PortalMailSettingsResponse update(List<String> defaultCc, String performedBy) {
        PortalMailSettings settings = repository.findById(PortalMailSettings.SINGLETON_ID)
                .orElseGet(PortalMailSettings::new);
        List<String> cleaned = defaultCc == null ? List.of() : defaultCc.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .toList();
        settings.setDefaultCc(cleaned.isEmpty() ? null : String.join(",", cleaned));
        settings.setUpdatedBy(performedBy);
        settings.setUpdatedAt(OffsetDateTime.now());
        return toResponse(repository.save(settings));
    }

    private PortalMailSettingsResponse toResponse(PortalMailSettings s) {
        List<String> defaultCc = s.getDefaultCc() == null || s.getDefaultCc().isBlank()
                ? List.of() : List.of(s.getDefaultCc().split(","));
        return new PortalMailSettingsResponse(defaultCc, s.getUpdatedBy(), s.getUpdatedAt());
    }
}
