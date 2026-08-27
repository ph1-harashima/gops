package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.response.PoPreviewResponse;
import com.glv.gsysportal.service.PoPreviewService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST, not GET, because Preview performs real (re-run every call) Backend
 * Validation with dedicated error codes (implementation instructions 2章/3章)
 * rather than being a pure idempotent resource fetch - matches the
 * literal endpoint given in implementation instructions 2章. Takes no
 * request body: Preview is built entirely from the persisted Draft, never
 * from anything the Frontend might send (implementation instructions 2章).
 */
@RestController
public class PoPreviewController {

    private final PoPreviewService poPreviewService;

    public PoPreviewController(PoPreviewService poPreviewService) {
        this.poPreviewService = poPreviewService;
    }

    @PostMapping("/api/orders/drafts/{id}/preview")
    public PoPreviewResponse preview(@PathVariable Long id) {
        return poPreviewService.preview(id);
    }
}
