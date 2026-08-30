package com.glv.gsysportal.dto.request;

import java.math.BigDecimal;

/** Null clears the Proposed Price back to "not yet entered" - not an error. */
public record UpdateProposedPriceRequest(BigDecimal proposedPrcSellWTax) {
}
