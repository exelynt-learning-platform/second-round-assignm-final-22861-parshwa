package com.ecommerce.service;

import com.ecommerce.dto.request.OrderRequest;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {
    OrderResponse createOrderFromCart(Long userId, OrderRequest request);
    OrderResponse getOrderById(Long orderId, Long userId);
    Page<OrderResponse> getOrdersByUserId(Long userId, Pageable pageable);
    Page<OrderResponse> getAllOrders(Pageable pageable);
    OrderResponse updateOrderStatus(Long orderId, Order.OrderStatus status);
    OrderResponse updatePaymentStatus(Long orderId, Order.PaymentStatus status, String paymentIntentId);
}
