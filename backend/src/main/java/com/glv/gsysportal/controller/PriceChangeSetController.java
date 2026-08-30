package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.request.AddPriceChangeDetailRequest;
import com.glv.gsysportal.dto.request.AddPriceChangeItemGroupRequest;
import com.glv.gsysportal.dto.request.CreatePriceChangeSetRequest;
import com.glv.gsysportal.dto.request.UpdatePriceChangeNoteRequest;
import com.glv.gsysportal.dto.request.UpdateProposedPriceRequest;
import com.glv.gsysportal.dto.response.PriceChangeCandidateResponse;
import com.glv.gsysportal.dto.response.PriceChangeSetDetailResponse;
import com.glv.gsysportal.dto.response.PriceChangeSetSummary;
import com.glv.gsysportal.security.CurrentUserProvider;
import com.glv.gsysportal.service.PriceChangeSetService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Price Change Foundation (Phase 8-B). Requires authentication like every
 * other {@code /api/**} endpoint (SecurityConfig's {@code anyRequest().authenticated()}
 * default - no path-specific matcher was added, matching {@code
 * OrderDraftController}'s own precedent for endpoints with no permitAll need).
 *
 * <p>Deliberately NO {@code @PreAuthorize} anywhere in this Controller
 * (Phase 8-B Section 14: "権限差を設ける必要がSource/Target Designから確定
 * できない場合、新Ruleを追加しない" - both OPERATOR and ADMIN may view/
 * create/edit a DRAFT Change Set equally, matching {@link
 * com.glv.gsysportal.controller.OrderDraftController}'s own lack of role
 * gating for the equivalent Order Draft operations).
 */
@RestController
public class PriceChangeSetController {

    private final PriceChangeSetService priceChangeSetService;
    private final CurrentUserProvider currentUserProvider;

    public PriceChangeSetController(PriceChangeSetService priceChangeSetService,
                                     CurrentUserProvider currentUserProvider) {
        this.priceChangeSetService = priceChangeSetService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/api/price-changes/legacy-items")
    public List<PriceChangeCandidateResponse> searchCandidates(
            @RequestParam(required = false) String brandCode,
            @RequestParam(required = false) String itemGrpCd,
            @RequestParam(required = false) String keyword) {
        return priceChangeSetService.searchCandidates(brandCode, itemGrpCd, keyword);
    }

    @GetMapping("/api/price-changes/item-groups")
    public List<String> listItemGroupCodes() {
        return priceChangeSetService.listItemGroupCodes();
    }

    @GetMapping("/api/price-changes")
    public List<PriceChangeSetSummary> list(@RequestParam(required = false) String status) {
        return priceChangeSetService.list(status);
    }

    @PostMapping("/api/price-changes")
    public ResponseEntity<PriceChangeSetDetailResponse> createDraft(
            @Valid @RequestBody(required = false) CreatePriceChangeSetRequest request) {
        String note = request == null ? null : request.note();
        PriceChangeSetDetailResponse response = priceChangeSetService.createDraft(note, currentUserProvider.currentUsername());
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/api/price-changes/{id}")
    public PriceChangeSetDetailResponse getDetail(@PathVariable Long id) {
        return priceChangeSetService.getDetail(id);
    }

    @PostMapping("/api/price-changes/{id}/details")
    public PriceChangeSetDetailResponse addDetail(@PathVariable Long id,
                                                   @Valid @RequestBody AddPriceChangeDetailRequest request) {
        return priceChangeSetService.addDetail(id, request.itemCd(), currentUserProvider.currentUsername());
    }

    @PostMapping("/api/price-changes/{id}/details/by-item-group")
    public PriceChangeSetDetailResponse addItemGroupDetails(@PathVariable Long id,
                                                              @Valid @RequestBody AddPriceChangeItemGroupRequest request) {
        return priceChangeSetService.addItemGroupDetails(id, request.itemGrpCd(), currentUserProvider.currentUsername());
    }

    @PutMapping("/api/price-changes/{id}/details/{detailId}/proposed-price")
    public PriceChangeSetDetailResponse updateProposedPrice(@PathVariable Long id, @PathVariable Long detailId,
                                                              @RequestBody UpdateProposedPriceRequest request) {
        return priceChangeSetService.updateProposedPrice(id, detailId, request.proposedPrcSellWTax(),
                currentUserProvider.currentUsername());
    }

    @DeleteMapping("/api/price-changes/{id}/details/{detailId}")
    public PriceChangeSetDetailResponse removeDetail(@PathVariable Long id, @PathVariable Long detailId) {
        return priceChangeSetService.removeDetail(id, detailId, currentUserProvider.currentUsername());
    }

    @PutMapping("/api/price-changes/{id}/note")
    public PriceChangeSetDetailResponse updateNote(@PathVariable Long id,
                                                     @RequestBody UpdatePriceChangeNoteRequest request) {
        return priceChangeSetService.updateNote(id, request.note(), currentUserProvider.currentUsername());
    }
}
