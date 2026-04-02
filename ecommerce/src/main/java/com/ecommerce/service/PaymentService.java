package com.ecommerce.service;

import com.ecommerce.dto.response.PaymentIntentResponse;

public interface PaymentService {
    PaymentIntentResponse createPaymentIntent(Long orderId, Long userId);
    void handleWebhookEvent(String payload, String sigHeader);
}
