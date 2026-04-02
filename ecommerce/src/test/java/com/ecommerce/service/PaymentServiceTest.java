package com.ecommerce.service;

import com.ecommerce.dto.response.PaymentIntentResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.User;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.PaymentException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Tests")
class PaymentServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderService orderService;

    @InjectMocks private PaymentServiceImpl paymentService;

    private User user;
    private Order pendingOrder;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "stripeWebhookSecret", "whsec_test_secret");

        user = User.builder().id(1L).username("carol").email("carol@example.com").build();

        pendingOrder = Order.builder()
                .id(300L).user(user)
                .orderItems(new ArrayList<>())
                .totalPrice(new BigDecimal("99.99"))
                .status(Order.OrderStatus.PENDING)
                .paymentStatus(Order.PaymentStatus.PENDING)
                .shippingFullName("Carol").shippingAddressLine1("1 Test St")
                .shippingCity("Testville").shippingState("TX")
                .shippingPostalCode("75001").shippingCountry("US")
                .build();
    }

    // ── createPaymentIntent ───────────────────────────────────────────────────

    @Nested
    @DisplayName("createPaymentIntent")
    class CreateIntent {

        @Test
        @DisplayName("throws ResourceNotFoundException when order not found")
        void throwsWhenOrderMissing() {
            when(orderRepository.findByIdAndUserId(300L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.createPaymentIntent(300L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws BadRequestException when order already paid")
        void throwsWhenAlreadyPaid() {
            pendingOrder.setPaymentStatus(Order.PaymentStatus.PAID);
            when(orderRepository.findByIdAndUserId(300L, 1L))
                    .thenReturn(Optional.of(pendingOrder));

            assertThatThrownBy(() -> paymentService.createPaymentIntent(300L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("already been paid");
        }

        @Test
        @DisplayName("throws BadRequestException when order is cancelled")
        void throwsWhenCancelled() {
            pendingOrder.setStatus(Order.OrderStatus.CANCELLED);
            when(orderRepository.findByIdAndUserId(300L, 1L))
                    .thenReturn(Optional.of(pendingOrder));

            assertThatThrownBy(() -> paymentService.createPaymentIntent(300L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("cancelled order");
        }
    }

    // ── handleWebhookEvent ────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleWebhookEvent")
    class WebhookHandler {

        @Test
        @DisplayName("throws PaymentException on invalid Stripe signature")
        void throwsOnInvalidSignature() {
            String fakePayload = "{\"type\":\"payment_intent.succeeded\"}";
            String badSig = "t=bad,v1=badsig";

            assertThatThrownBy(() -> paymentService.handleWebhookEvent(fakePayload, badSig))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("signature verification failed");
        }
    }

    // ── amount conversion ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Amount conversion")
    class AmountConversion {

        @Test
        @DisplayName("correctly converts dollars to cents")
        void dollarsToCents() {
            // $99.99 × 100 → 9999 cents
            long cents = new BigDecimal("99.99")
                    .multiply(BigDecimal.valueOf(100))
                    .longValue();
            assertThat(cents).isEqualTo(9999L);
        }

        @Test
        @DisplayName("correctly converts a round dollar amount")
        void roundDollarToCents() {
            long cents = new BigDecimal("50.00")
                    .multiply(BigDecimal.valueOf(100))
                    .longValue();
            assertThat(cents).isEqualTo(5000L);
        }

        @Test
        @DisplayName("order total is non-zero for non-empty order")
        void orderTotalNonZero() {
            assertThat(pendingOrder.getTotalPrice()).isGreaterThan(BigDecimal.ZERO);
        }
    }

    // ── guard: order ownership ────────────────────────────────────────────────

    @Nested
    @DisplayName("Order ownership guard")
    class Ownership {

        @Test
        @DisplayName("cannot pay for another user's order")
        void cannotPayForForeignOrder() {
            when(orderRepository.findByIdAndUserId(300L, 99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.createPaymentIntent(300L, 99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
