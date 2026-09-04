package com.glv.gsysportal.exception;

import com.glv.gsysportal.observability.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every error response is an internal Error Code, never a Japanese message
 * (Requirements MD 30.12) - the Frontend resolves the code via i18n.
 *
 * <p><b>Phase 8-L (Production Reliability Foundation, Production Readiness
 * Audit §5-10)</b>: every response body now additionally carries {@code
 * timestamp}/{@code status}/{@code path}/{@code correlationId} alongside the
 * existing {@code errorCode} (and any exception-specific extra fields) via
 * the shared {@link #error} factory - purely additive, no existing field
 * was renamed/removed/retyped, so no Frontend Breaking Change. {@code
 * message} was deliberately NOT added as a field: this project's standing
 * principle (above) is that a response never carries a human-readable
 * message, only a code the Frontend resolves via i18n - adding a `message`
 * field would either duplicate `errorCode` or reintroduce exactly what that
 * principle forbids.
 *
 * <p>3 new catch-all handlers close the previous gap where an unhandled
 * exception (Legacy/Portal DB failure, an uncaught bug, or - newly relevant
 * since 4 Controllers use {@code @Valid} - a Bean Validation failure) fell
 * through to Spring Boot's default {@code /error} handling instead of this
 * project's {@code errorCode} convention: {@link #handleValidation},
 * {@link #handleLegacyUnavailable}, {@link #handlePortalDbError}, {@link
 * #handleUnexpected}. Spring resolves {@code @ExceptionHandler} by most
 * specific type first, so none of the ~40 existing handlers below change
 * behavior - these 3 only catch what nothing more specific already does.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static ResponseEntity<Map<String, Object>> error(HttpServletRequest request, HttpStatus status, String errorCode) {
        return error(request, status, errorCode, Map.of());
    }

    private static ResponseEntity<Map<String, Object>> error(HttpServletRequest request, HttpStatus status, String errorCode,
                                                               Map<String, Object> extra) {
        // Read back by CorrelationIdFilter's own per-request summary log line.
        request.setAttribute(CorrelationIdFilter.ERROR_CODE_ATTRIBUTE, errorCode);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("status", status.value());
        body.put("errorCode", errorCode);
        body.put("path", request.getRequestURI());
        body.put("correlationId", CorrelationIdFilter.currentCorrelationId(request));
        body.putAll(extra);
        return ResponseEntity.status(status).body(body);
    }

    // --- Phase 8-L: Structured Error Handling Foundation (§5-7) ---

    /** Spring's own validation failure ({@code @Valid} on a Controller
     * parameter) previously fell through to Spring Boot's default {@code
     * ProblemDetail} body, not this project's errorCode convention - the
     * first Controller to use {@code @Valid} (Phase 7-C3) never got a
     * matching handler until now. Field names only (never the violation
     * message text, consistent with never returning human-readable text). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField())
                .distinct()
                .toList();
        return error(request, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", Map.of("fields", fields));
    }

    /** {@code @PreAuthorize("hasRole('ADMIN')")} throws this DURING a
     * Controller method invocation (Method Security, Spring AOP proxy) -
     * unlike an unauthenticated request (rejected earlier, at the Filter
     * chain level, by {@code SecurityConfig}'s {@code authenticationEntryPoint},
     * which never reaches this class), this one WOULD be caught by the new
     * {@link #handleUnexpected} catch-all below (added this Phase) and
     * misreported as a 500 without this explicit handler - regression caught
     * by the existing 403 Authorization Tests (Phase 7-C1/7-C2A/7-C3/7-C5/
     * 7-C6) during this Phase's own regression run. Reproduces exactly the
     * same {@code errorCode} SecurityConfig's own {@code accessDeniedHandler}
     * already used, so this is not a Frontend Contract change - only reached
     * for the "authenticated but insufficient Role, from inside a Controller
     * method" case; the Filter-level 403 path is untouched. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return error(request, HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    /** Legacy G-SYS Adapter unavailable (connection refused/timeout/driver
     * error) - see {@code LegacyFailureTranslatingJdbcTemplate}. Never
     * thrown for a genuine empty result (0 rows), only for a real query
     * failure. */
    @ExceptionHandler(LegacyUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleLegacyUnavailable(LegacyUnavailableException ex, HttpServletRequest request) {
        log.warn("Legacy G-SYS Adapter unavailable: {}", ex.getCause() != null ? ex.getCause().getClass().getSimpleName() : "unknown cause");
        return error(request, HttpStatus.SERVICE_UNAVAILABLE, "LEGACY_UNAVAILABLE");
    }

    /** Portal (Prototype PostgreSQL) DB failure. Anything reaching here is,
     * by construction, NOT a Legacy failure - those are pre-translated to
     * {@link LegacyUnavailableException} above and never reach this handler
     * since it is not a {@code DataAccessException} subtype. */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handlePortalDbError(DataAccessException ex, HttpServletRequest request) {
        log.warn("Portal DB error: {}", ex.getClass().getSimpleName());
        return error(request, HttpStatus.SERVICE_UNAVAILABLE, "PORTAL_DB_ERROR");
    }

    /** Last-resort fallback for anything not already handled - never expose
     * the exception message or stack trace to the Client (logged server-side
     * only, at ERROR with the full stack trace for troubleshooting). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return error(request, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
    }

    // --- Existing Business exception handlers (unchanged errorCode/status/extra fields) ---

    @ExceptionHandler(MixedSupplierException.class)
    public ResponseEntity<Map<String, Object>> handleMixedSupplier(MixedSupplierException ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "MIXED_SUPPLIER_NOT_ALLOWED", Map.of("supplierCodes", ex.supplierCodes()));
    }

    @ExceptionHandler(SkuNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSkuNotFound(SkuNotFoundException ex, HttpServletRequest request) {
        return error(request, HttpStatus.NOT_FOUND, "SKU_NOT_FOUND", Map.of("skus", ex.missingSkus()));
    }

    @ExceptionHandler(DraftNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleDraftNotFound(DraftNotFoundException ex, HttpServletRequest request) {
        return error(request, HttpStatus.NOT_FOUND, "DRAFT_NOT_FOUND");
    }

    @ExceptionHandler(EmptySkuListException.class)
    public ResponseEntity<Map<String, Object>> handleEmptySkuList(EmptySkuListException ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "EMPTY_SKU_LIST");
    }

    @ExceptionHandler(InvalidOrderQtyException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidOrderQty(InvalidOrderQtyException ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "INVALID_ORDER_QTY");
    }

    @ExceptionHandler(NoOrderableItemsException.class)
    public ResponseEntity<Map<String, Object>> handleNoOrderableItems(NoOrderableItemsException ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "NO_ORDERABLE_ITEMS");
    }

    @ExceptionHandler(MissingUnitPriceException.class)
    public ResponseEntity<Map<String, Object>> handleMissingUnitPrice(MissingUnitPriceException ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "MISSING_UNIT_PRICE", Map.of("skus", ex.skus()));
    }

    @ExceptionHandler(InvalidOrderStatusException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidOrderStatus(InvalidOrderStatusException ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "INVALID_ORDER_STATUS");
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidStatusTransition(InvalidStatusTransitionException ex, HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION");
    }

    @ExceptionHandler(OrderNotEditableException.class)
    public ResponseEntity<Map<String, Object>> handleOrderNotEditable(OrderNotEditableException ex, HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, "ORDER_NOT_EDITABLE");
    }

    @ExceptionHandler(InvalidConfirmedQtyException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidConfirmedQty(InvalidConfirmedQtyException ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "INVALID_CONFIRMED_QTY");
    }

    @ExceptionHandler(SupplierResponseIncompleteException.class)
    public ResponseEntity<Map<String, Object>> handleSupplierResponseIncomplete(SupplierResponseIncompleteException ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "SUPPLIER_RESPONSE_INCOMPLETE");
    }

    @ExceptionHandler(AttentionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAttentionNotFound(AttentionNotFoundException ex, HttpServletRequest request) {
        return error(request, HttpStatus.NOT_FOUND, "ATTENTION_NOT_FOUND");
    }

    @ExceptionHandler(AttentionAlreadyResolvedException.class)
    public ResponseEntity<Map<String, Object>> handleAttentionAlreadyResolved(AttentionAlreadyResolvedException ex, HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, "ATTENTION_ALREADY_RESOLVED");
    }

    @ExceptionHandler(ReturnReasonRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleReturnReasonRequired(ReturnReasonRequiredException ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "RETURN_REASON_REQUIRED");
    }

    @ExceptionHandler(OrderNotApprovedException.class)
    public ResponseEntity<Map<String, Object>> handleOrderNotApproved(OrderNotApprovedException ex, HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, "ORDER_NOT_APPROVED");
    }

    @ExceptionHandler(InvalidEmailFormatException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidEmailFormat(InvalidEmailFormatException ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "INVALID_EMAIL_FORMAT");
    }

    @ExceptionHandler(SupplierCodeNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSupplierCodeNotFound(SupplierCodeNotFoundException ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "SUPPLIER_CODE_NOT_FOUND");
    }

    @ExceptionHandler(BrandCodeNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleBrandCodeNotFound(BrandCodeNotFoundException ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "BRAND_CODE_NOT_FOUND");
    }

    @ExceptionHandler(DuplicateSupplierContactException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateSupplierContact(DuplicateSupplierContactException ex, HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, "DUPLICATE_SUPPLIER_CONTACT");
    }

    @ExceptionHandler(SupplierContactNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSupplierContactNotFound(SupplierContactNotFoundException ex, HttpServletRequest request) {
        return error(request, HttpStatus.NOT_FOUND, "SUPPLIER_CONTACT_NOT_FOUND");
    }

    @ExceptionHandler(MailTemplateNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleMailTemplateNotFound(MailTemplateNotFoundException ex, HttpServletRequest request) {
        return error(request, HttpStatus.NOT_FOUND, "MAIL_TEMPLATE_NOT_FOUND");
    }

    @ExceptionHandler(InvalidLanguageException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidLanguage(InvalidLanguageException ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "INVALID_LANGUAGE");
    }

    @ExceptionHandler(InvalidContactTypeException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidContactType(InvalidContactTypeException ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "INVALID_CONTACT_TYPE");
    }

    @ExceptionHandler(InvalidTemplateTypeException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTemplateType(InvalidTemplateTypeException ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "INVALID_TEMPLATE_TYPE");
    }

    // --- Phase 7-C5: Supplier Response Revision / Agreement Workflow ---

    @ExceptionHandler(ResponseNotAgreeableException.class)
    public ResponseEntity<Map<String, Object>> handleResponseNotAgreeable(ResponseNotAgreeableException ex, HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, "RESPONSE_NOT_AGREEABLE");
    }

    @ExceptionHandler(UnacknowledgedAttentionException.class)
    public ResponseEntity<Map<String, Object>> handleUnacknowledgedAttention(UnacknowledgedAttentionException ex, HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, "UNACKNOWLEDGED_ATTENTION");
    }

    @ExceptionHandler(RevisionCreationNotAllowedException.class)
    public ResponseEntity<Map<String, Object>> handleRevisionCreationNotAllowed(RevisionCreationNotAllowedException ex, HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, "REVISION_CREATION_NOT_ALLOWED");
    }

    @ExceptionHandler(RevisionReasonRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleRevisionReasonRequired(RevisionReasonRequiredException ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "REVISION_REASON_REQUIRED");
    }

    @ExceptionHandler(OrderNotAgreedException.class)
    public ResponseEntity<Map<String, Object>> handleOrderNotAgreed(OrderNotAgreedException ex, HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, "ORDER_NOT_AGREED");
    }

    @ExceptionHandler(ReopenReasonRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleReopenReasonRequired(ReopenReasonRequiredException ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "REOPEN_REASON_REQUIRED");
    }

    // --- Phase 7-C7A: Fulfillment / Follow-up Foundation ---

    @ExceptionHandler(FollowUpCaseNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleFollowUpCaseNotFound(FollowUpCaseNotFoundException ex, HttpServletRequest request) {
        return error(request, HttpStatus.NOT_FOUND, "FOLLOW_UP_CASE_NOT_FOUND");
    }

    @ExceptionHandler(FollowUpCaseAlreadyClosedException.class)
    public ResponseEntity<Map<String, Object>> handleFollowUpCaseAlreadyClosed(FollowUpCaseAlreadyClosedException ex, HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, "FOLLOW_UP_CASE_ALREADY_CLOSED");
    }

    @ExceptionHandler(InvalidFollowUpReasonException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidFollowUpReason(InvalidFollowUpReasonException ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "INVALID_FOLLOW_UP_REASON");
    }

    // --- Phase 7-C6: Excel / Legacy Concurrency Control Foundation ---

    @ExceptionHandler(OfficialPoNotLinkedException.class)
    public ResponseEntity<Map<String, Object>> handleOfficialPoNotLinked(OfficialPoNotLinkedException ex, HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, "OFFICIAL_PO_NOT_LINKED");
    }

    @ExceptionHandler(LegacyPoNotFoundForBaselineException.class)
    public ResponseEntity<Map<String, Object>> handleLegacyPoNotFoundForBaseline(LegacyPoNotFoundForBaselineException ex, HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, "LEGACY_PO_NOT_FOUND_FOR_BASELINE");
    }

    @ExceptionHandler(IntegrationRequestRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrationRequestRequired(IntegrationRequestRequiredException ex, HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, "INTEGRATION_REQUEST_REQUIRED");
    }

    @ExceptionHandler(InvalidIntegrationIntentException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidIntegrationIntent(InvalidIntegrationIntentException ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "INVALID_INTEGRATION_INTENT");
    }

    // --- Phase 8-B: Price Change Foundation ---

    @ExceptionHandler(PriceChangeSetNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePriceChangeSetNotFound(PriceChangeSetNotFoundException ex, HttpServletRequest request) {
        return error(request, HttpStatus.NOT_FOUND, "PRICE_CHANGE_SET_NOT_FOUND");
    }

    @ExceptionHandler(PriceChangeSetNotEditableException.class)
    public ResponseEntity<Map<String, Object>> handlePriceChangeSetNotEditable(PriceChangeSetNotEditableException ex, HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, "PRICE_CHANGE_SET_NOT_EDITABLE");
    }

    @ExceptionHandler(PriceChangeSetDetailNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePriceChangeSetDetailNotFound(PriceChangeSetDetailNotFoundException ex, HttpServletRequest request) {
        return error(request, HttpStatus.NOT_FOUND, "PRICE_CHANGE_SET_DETAIL_NOT_FOUND");
    }

    @ExceptionHandler(DuplicateSkuInChangeSetException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateSkuInChangeSet(DuplicateSkuInChangeSetException ex, HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, "DUPLICATE_SKU_IN_CHANGE_SET");
    }

    // --- Phase 8-G: Arrival / Warehouse Stock Visibility Foundation ---

    @ExceptionHandler(ArrivalNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleArrivalNotFound(ArrivalNotFoundException ex, HttpServletRequest request) {
        return error(request, HttpStatus.NOT_FOUND, "ARRIVAL_NOT_FOUND");
    }

    // --- Phase 9-A: Official PO Number / Excel Generation (Production PO Workflow) ---

    @ExceptionHandler(InvalidOfficialPoNumberException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidOfficialPoNumber(InvalidOfficialPoNumberException ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "INVALID_OFFICIAL_PO_NUMBER");
    }

    @ExceptionHandler(DuplicateOfficialPoNumberException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateOfficialPoNumber(DuplicateOfficialPoNumberException ex, HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, "DUPLICATE_OFFICIAL_PO_NUMBER");
    }

    @ExceptionHandler(OfficialPoAlreadySubmittedException.class)
    public ResponseEntity<Map<String, Object>> handleOfficialPoAlreadySubmitted(OfficialPoAlreadySubmittedException ex, HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, "OFFICIAL_PO_ALREADY_SUBMITTED");
    }

    @ExceptionHandler(OfficialPoNumberRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleOfficialPoNumberRequired(OfficialPoNumberRequiredException ex, HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, "OFFICIAL_PO_NUMBER_REQUIRED");
    }

    @ExceptionHandler(OfficialPoPreflightBlockedException.class)
    public ResponseEntity<Map<String, Object>> handleOfficialPoPreflightBlocked(OfficialPoPreflightBlockedException ex, HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, "OFFICIAL_PO_PREFLIGHT_BLOCKED");
    }

    @ExceptionHandler(OfficialPoExcelNotGeneratedException.class)
    public ResponseEntity<Map<String, Object>> handleOfficialPoExcelNotGenerated(OfficialPoExcelNotGeneratedException ex, HttpServletRequest request) {
        return error(request, HttpStatus.NOT_FOUND, "OFFICIAL_PO_EXCEL_NOT_GENERATED");
    }

    // --- Phase 9-B: Import Folder Integration (Production PO Workflow) ---

    @ExceptionHandler(OfficialPoNotGeneratedException.class)
    public ResponseEntity<Map<String, Object>> handleOfficialPoNotGenerated(OfficialPoNotGeneratedException ex, HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, "OFFICIAL_PO_NOT_GENERATED");
    }
}
