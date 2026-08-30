package com.glv.gsysportal.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Add a single SKU to a Change Set (Product Selection - SKU個別選択). */
public record AddPriceChangeDetailRequest(@NotBlank String itemCd) {
}
