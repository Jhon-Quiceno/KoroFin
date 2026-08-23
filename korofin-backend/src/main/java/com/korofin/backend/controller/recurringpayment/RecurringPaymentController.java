package com.korofin.backend.controller.recurringpayment;

import com.korofin.backend.dto.recurringpayment.RecurringPaymentPayResponse;
import com.korofin.backend.dto.recurringpayment.RecurringPaymentRequest;
import com.korofin.backend.dto.recurringpayment.RecurringPaymentResponse;
import com.korofin.backend.dto.recurringpayment.RecurringPaymentUpdateRequest;
import com.korofin.backend.service.recurringpayment.RecurringPaymentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de los pagos recurrentes del usuario actual.
 */
@RestController
@RequestMapping("/api/recurring")
public class RecurringPaymentController {

    private final RecurringPaymentService recurringPaymentService;

    public RecurringPaymentController(RecurringPaymentService recurringPaymentService) {
        this.recurringPaymentService = recurringPaymentService;
    }

    @GetMapping
    public ResponseEntity<Page<RecurringPaymentResponse>> getRecurringPayments(
            @PageableDefault(size = 20, sort = "nextPaymentDate", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ResponseEntity.ok(recurringPaymentService.getRecurringPayments(pageable));
    }

    @PostMapping
    public ResponseEntity<RecurringPaymentResponse> createRecurringPayment(
            @Valid @RequestBody RecurringPaymentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recurringPaymentService.createRecurringPayment(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecurringPaymentResponse> updateRecurringPayment(
            @PathVariable("id") Long recurringPaymentId,
            @Valid @RequestBody RecurringPaymentUpdateRequest request
    ) {
        return ResponseEntity.ok(recurringPaymentService.updateRecurringPayment(recurringPaymentId, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecurringPayment(@PathVariable("id") Long recurringPaymentId) {
        recurringPaymentService.deleteRecurringPayment(recurringPaymentId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<RecurringPaymentResponse> toggleRecurringPayment(@PathVariable("id") Long recurringPaymentId) {
        return ResponseEntity.ok(recurringPaymentService.toggleRecurringPayment(recurringPaymentId));
    }

    @PatchMapping("/{id}/pay")
    public ResponseEntity<RecurringPaymentPayResponse> payRecurringPayment(@PathVariable("id") Long recurringPaymentId) {
        return ResponseEntity.ok(recurringPaymentService.payRecurringPayment(recurringPaymentId));
    }
}
