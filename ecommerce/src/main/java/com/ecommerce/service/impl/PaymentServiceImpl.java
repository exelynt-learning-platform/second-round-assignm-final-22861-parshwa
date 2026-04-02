package com.ecommerce.service.impl;

import com.ecommerce.dto.response.PaymentIntentResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.PaymentException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.service.OrderService;
import com.ecommerce.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Value("${stripe.webhook.secret}")
    private String stripeWebhookSecret;

    @Override
    @Transactional
    public PaymentIntentResponse createPaymentIntent(Long orderId, Long userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found with id: " + orderId));

        if (order.getPaymentStatus() == Order.PaymentStatus.PAID) {
            throw new BadRequestException("Order has already been paid");
        }
        if (order.getStatus() == Order.OrderStatus.CANCELLED) {
            throw new BadRequestException("Cannot pay for a cancelled order");
        }

        try {
            // Amount in cents (Stripe requires smallest currency unit)
            long amountInCents = order.getTotalPrice()
                    .multiply(BigDecimal.valueOf(100))
                    .longValue();

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency("usd")
                    .putMetadata("orderId", String.valueOf(order.getId()))
                    .putMetadata("userId", String.valueOf(userId))
                    .setDescription("Order #" + order.getId())
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build()
                    )
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);

            // Save intent ID against the order
            orderService.updatePaymentStatus(orderId,
                    Order.PaymentStatus.PROCESSING, intent.getId());

            log.info("Payment intent created: intentId={}, orderId={}", intent.getId(), orderId);

            return PaymentIntentResponse.builder()
                    .clientSecret(intent.getClientSecret())
                    .paymentIntentId(intent.getId())
                    .amount(amountInCents)
                    .currency("usd")
                    .status(intent.getStatus())
                    .orderId(orderId)
                    .build();

        } catch (StripeException e) {
            log.error("Stripe error creating payment intent for orderId={}: {}", orderId, e.getMessage());
            throw new PaymentException("Failed to create payment intent: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void handleWebhookEvent(String payload, String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, stripeWebhookSecret);
        } catch (SignatureVerificationException e) {
            log.error("Webhook signature verification failed: {}", e.getMessage());
            throw new PaymentException("Webhook signature verification failed");
        }

        log.info("Received Stripe webhook event: type={}, id={}", event.getType(), event.getId());

        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = dataObjectDeserializer.getObject()
                .orElseThrow(() -> new PaymentException("Failed to deserialize webhook event data"));

        switch (event.getType()) {
            case "payment_intent.succeeded" -> handlePaymentSuccess((PaymentIntent) stripeObject);
            case "payment_intent.payment_failed" -> handlePaymentFailure((PaymentIntent) stripeObject);
            case "payment_intent.canceled" -> handlePaymentCancelled((PaymentIntent) stripeObject);
            default -> log.debug("Unhandled event type: {}", event.getType());
        }
    }

    // ── Webhook event handlers ────────────────────────────────────────────────

    private void handlePaymentSuccess(PaymentIntent intent) {
        String orderIdStr = intent.getMetadata().get("orderId");
        if (orderIdStr == null) {
            log.warn("Payment intent {} has no orderId metadata", intent.getId());
            return;
        }
        Long orderId = Long.parseLong(orderIdStr);
        orderService.updatePaymentStatus(orderId, Order.PaymentStatus.PAID, intent.getId());
        log.info("Payment succeeded: intentId={}, orderId={}", intent.getId(), orderId);
    }

    private void handlePaymentFailure(PaymentIntent intent) {
        String orderIdStr = intent.getMetadata().get("orderId");
        if (orderIdStr == null) {
            log.warn("Payment intent {} has no orderId metadata", intent.getId());
            return;
        }
        Long orderId = Long.parseLong(orderIdStr);
        orderService.updatePaymentStatus(orderId, Order.PaymentStatus.FAILED, intent.getId());
        log.warn("Payment failed: intentId={}, orderId={}", intent.getId(), orderId);
    }

    private void handlePaymentCancelled(PaymentIntent intent) {
        String orderIdStr = intent.getMetadata().get("orderId");
        if (orderIdStr == null) return;
        Long orderId = Long.parseLong(orderIdStr);
        orderService.updatePaymentStatus(orderId, Order.PaymentStatus.FAILED, intent.getId());
        log.warn("Payment cancelled: intentId={}, orderId={}", intent.getId(), orderId);
    }
}
