package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.response.ArrivalDetailResponse;
import com.glv.gsysportal.dto.response.ArrivalSummaryResponse;
import com.glv.gsysportal.dto.response.PageResponse;
import com.glv.gsysportal.service.ArrivalService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Arrival Visibility Foundation (Phase 8-G 15章). READ ONLY, open to any
 * authenticated user - same access pattern as every other GET endpoint in
 * this codebase (viewing Arrival status is never a destructive action). */
@RestController
public class ArrivalController {

    private final ArrivalService arrivalService;

    public ArrivalController(ArrivalService arrivalService) {
        this.arrivalService = arrivalService;
    }

    @GetMapping("/api/arrivals")
    public PageResponse<ArrivalSummaryResponse> list(
            @RequestParam(required = false) String supplierCode,
            @RequestParam(required = false) String brandCode,
            @RequestParam(required = false) String poNumber,
            @RequestParam(required = false) String invoiceNumber,
            @RequestParam(required = false) String skuKeyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate arrivalDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate arrivalDateTo,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return arrivalService.list(supplierCode, brandCode, poNumber, invoiceNumber, skuKeyword,
                arrivalDateFrom, arrivalDateTo, page, size);
    }

    @GetMapping("/api/arrivals/{supplierCode}/{poNumber}/{invoiceNumber}")
    public ArrivalDetailResponse detail(@PathVariable String supplierCode, @PathVariable String poNumber,
                                         @PathVariable String invoiceNumber) {
        return arrivalService.detail(supplierCode, poNumber, invoiceNumber);
    }
}
