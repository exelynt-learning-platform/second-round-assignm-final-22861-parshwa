package com.ecommerce.dto.response;

import com.ecommerce.entity.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long id;
    private Long userId;
    private String username;
    private List<OrderItemResponse> orderItems;
    private BigDecimal totalPrice;
    private Order.OrderStatus status;
    private Order.PaymentStatus paymentStatus;
    private String paymentIntentId;

    // Shipping
    private String shippingFullName;
    private String shippingAddressLine1;
    private String shippingAddressLine2;
    private String shippingCity;
    private String shippingState;
    private String shippingPostalCode;
    private String shippingCountry;
    private String shippingPhone;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
