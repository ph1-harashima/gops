package com.glv.gsysportal.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every error response is an internal Error Code, never a Japanese message
 * (Requirements MD 30.12) - the Frontend resolves the code via i18n.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MixedSupplierException.class)
    public ResponseEntity<Map<String, Object>> handleMixedSupplier(MixedSupplierException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("errorCode", "MIXED_SUPPLIER_NOT_ALLOWED");
        body.put("supplierCodes", ex.supplierCodes());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(SkuNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSkuNotFound(SkuNotFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("errorCode", "SKU_NOT_FOUND");
        body.put("skus", ex.missingSkus());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(DraftNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleDraftNotFound(DraftNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("errorCode", "DRAFT_NOT_FOUND"));
    }

    @ExceptionHandler(EmptySkuListException.class)
    public ResponseEntity<Map<String, Object>> handleEmptySkuList(EmptySkuListException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("errorCode", "EMPTY_SKU_LIST"));
    }

    @ExceptionHandler(InvalidOrderQtyException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidOrderQty(InvalidOrderQtyException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("errorCode", "INVALID_ORDER_QTY"));
    }

    @ExceptionHandler(NoOrderableItemsException.class)
    public ResponseEntity<Map<String, Object>> handleNoOrderableItems(NoOrderableItemsException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("errorCode", "NO_ORDERABLE_ITEMS"));
    }

    @ExceptionHandler(MissingUnitPriceException.class)
    public ResponseEntity<Map<String, Object>> handleMissingUnitPrice(MissingUnitPriceException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("errorCode", "MISSING_UNIT_PRICE");
        body.put("skus", ex.skus());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(InvalidOrderStatusException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidOrderStatus(InvalidOrderStatusException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("errorCode", "INVALID_ORDER_STATUS"));
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidStatusTransition(InvalidStatusTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("errorCode", "INVALID_STATUS_TRANSITION"));
    }

    @ExceptionHandler(OrderNotEditableException.class)
    public ResponseEntity<Map<String, Object>> handleOrderNotEditable(OrderNotEditableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("errorCode", "ORDER_NOT_EDITABLE"));
    }

    @ExceptionHandler(InvalidConfirmedQtyException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidConfirmedQty(InvalidConfirmedQtyException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("errorCode", "INVALID_CONFIRMED_QTY"));
    }

    @ExceptionHandler(SupplierResponseIncompleteException.class)
    public ResponseEntity<Map<String, Object>> handleSupplierResponseIncomplete(SupplierResponseIncompleteException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("errorCode", "SUPPLIER_RESPONSE_INCOMPLETE"));
    }

    @ExceptionHandler(AttentionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAttentionNotFound(AttentionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("errorCode", "ATTENTION_NOT_FOUND"));
    }

    @ExceptionHandler(AttentionAlreadyResolvedException.class)
    public ResponseEntity<Map<String, Object>> handleAttentionAlreadyResolved(AttentionAlreadyResolvedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("errorCode", "ATTENTION_ALREADY_RESOLVED"));
    }

    @ExceptionHandler(ReturnReasonRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleReturnReasonRequired(ReturnReasonRequiredException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("errorCode", "RETURN_REASON_REQUIRED"));
    }

    @ExceptionHandler(OrderNotApprovedException.class)
    public ResponseEntity<Map<String, Object>> handleOrderNotApproved(OrderNotApprovedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("errorCode", "ORDER_NOT_APPROVED"));
    }

    @ExceptionHandler(InvalidEmailFormatException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidEmailFormat(InvalidEmailFormatException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("errorCode", "INVALID_EMAIL_FORMAT"));
    }

    @ExceptionHandler(SupplierCodeNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSupplierCodeNotFound(SupplierCodeNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("errorCode", "SUPPLIER_CODE_NOT_FOUND"));
    }

    @ExceptionHandler(BrandCodeNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleBrandCodeNotFound(BrandCodeNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("errorCode", "BRAND_CODE_NOT_FOUND"));
    }

    @ExceptionHandler(DuplicateSupplierContactException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateSupplierContact(DuplicateSupplierContactException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("errorCode", "DUPLICATE_SUPPLIER_CONTACT"));
    }

    @ExceptionHandler(SupplierContactNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSupplierContactNotFound(SupplierContactNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("errorCode", "SUPPLIER_CONTACT_NOT_FOUND"));
    }

    @ExceptionHandler(MailTemplateNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleMailTemplateNotFound(MailTemplateNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("errorCode", "MAIL_TEMPLATE_NOT_FOUND"));
    }

    @ExceptionHandler(InvalidLanguageException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidLanguage(InvalidLanguageException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("errorCode", "INVALID_LANGUAGE"));
    }

    @ExceptionHandler(InvalidContactTypeException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidContactType(InvalidContactTypeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("errorCode", "INVALID_CONTACT_TYPE"));
    }

    @ExceptionHandler(InvalidTemplateTypeException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTemplateType(InvalidTemplateTypeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("errorCode", "INVALID_TEMPLATE_TYPE"));
    }
}
