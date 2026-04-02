package com.ecommerce.controller;

import com.ecommerce.dto.request.PaymentRequest;
import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.dto.response.PaymentIntentResponse;
import com.ecommerce.security.UserDetailsImpl;
import com.ecommerce.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * POST /api/payments/create-intent
     * Create a Stripe PaymentIntent for an order.
     * Returns the clientSecret needed by the frontend Stripe.js SDK.
     */
    @PostMapping("/create-intent")
    public ResponseEntity<ApiResponse<PaymentIntentResponse>> createPaymentIntent(
            @AuthenticationPrincipal UserDetailsImpl user,
            @Valid @RequestBody PaymentRequest request) {
        PaymentIntentResponse response = paymentService
                .createPaymentIntent(request.getOrderId(), user.getId());
        return ResponseEntity.ok(ApiResponse.success("Payment intent created", response));
    }

    /**
     * POST /api/payments/webhook
     * Stripe webhook endpoint — receives payment lifecycle events.
     * Must be publicly accessible (no JWT required).
     * Register this URL in your Stripe Dashboard → Webhooks.
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        log.info("Received Stripe webhook");
        paymentService.handleWebhookEvent(payload, sigHeader);
        return ResponseEntity.ok("Webhook received");
    }
}
