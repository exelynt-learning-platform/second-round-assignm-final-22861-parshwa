package com.ecommerce.service;

import com.ecommerce.dto.request.OrderRequest;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.entity.*;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.InsufficientStockException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Tests")
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CartRepository cartRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private CartService cartService;

    @InjectMocks private OrderServiceImpl orderService;

    private User user;
    private Product product;
    private Cart cart;
    private CartItem cartItem;
    private OrderRequest orderRequest;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).username("bob").email("bob@example.com").build();

        product = Product.builder()
                .id(10L).name("Wireless Mouse")
                .price(new BigDecimal("39.99"))
                .stockQuantity(20).active(true).build();

        cart = Cart.builder().id(100L).user(user).items(new ArrayList<>()).build();

        cartItem = CartItem.builder()
                .id(1L).cart(cart).product(product)
                .quantity(2).priceAtAddition(new BigDecimal("39.99")).build();
        cart.getItems().add(cartItem);

        orderRequest = new OrderRequest();
        orderRequest.setShippingFullName("Bob Smith");
        orderRequest.setShippingAddressLine1("123 Main St");
        orderRequest.setShippingCity("Springfield");
        orderRequest.setShippingState("IL");
        orderRequest.setShippingPostalCode("62701");
        orderRequest.setShippingCountry("US");
    }

    // ── createOrderFromCart ───────────────────────────────────────────────────

    @Nested
    @DisplayName("createOrderFromCart")
    class CreateOrder {

        @Test
        @DisplayName("creates order, deducts stock, and clears cart on success")
        void success() {
            Order savedOrder = Order.builder()
                    .id(200L).user(user)
                    .orderItems(new ArrayList<>())
                    .totalPrice(new BigDecimal("79.98"))
                    .status(Order.OrderStatus.PENDING)
                    .paymentStatus(Order.PaymentStatus.PENDING)
                    .shippingFullName("Bob Smith")
                    .shippingAddressLine1("123 Main St")
                    .shippingCity("Springfield").shippingState("IL")
                    .shippingPostalCode("62701").shippingCountry("US")
                    .build();

            OrderItem oi = OrderItem.builder()
                    .id(1L).order(savedOrder).product(product)
                    .quantity(2).priceAtPurchase(new BigDecimal("39.99")).build();
            savedOrder.getOrderItems().add(oi);

            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
            when(productRepository.save(any(Product.class))).thenReturn(product);

            OrderResponse response = orderService.createOrderFromCart(1L, orderRequest);

            assertThat(response.getId()).isEqualTo(200L);
            assertThat(response.getTotalPrice()).isEqualByComparingTo("79.98");
            assertThat(response.getStatus()).isEqualTo(Order.OrderStatus.PENDING);
            assertThat(response.getPaymentStatus()).isEqualTo(Order.PaymentStatus.PENDING);

            // Stock should be deducted
            assertThat(product.getStockQuantity()).isEqualTo(18);

            // Cart should be cleared
            verify(cartService).clearCart(1L);
        }

        @Test
        @DisplayName("throws BadRequestException when cart is empty")
        void throwsOnEmptyCart() {
            cart.getItems().clear();
            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));

            assertThatThrownBy(() -> orderService.createOrderFromCart(1L, orderRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("empty cart");
        }

        @Test
        @DisplayName("throws BadRequestException when cart not found")
        void throwsWhenCartMissing() {
            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.createOrderFromCart(1L, orderRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Cart not found");
        }

        @Test
        @DisplayName("throws InsufficientStockException when stock is too low")
        void throwsOnInsufficientStock() {
            product.setStockQuantity(1); // only 1 in stock but cart wants 2

            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> orderService.createOrderFromCart(1L, orderRequest))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessageContaining("Wireless Mouse");

            // Stock must NOT be modified and cart must NOT be cleared
            assertThat(product.getStockQuantity()).isEqualTo(1);
            verify(cartService, never()).clearCart(anyLong());
        }
    }

    // ── getOrderById ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getOrderById")
    class GetOrder {

        @Test
        @DisplayName("returns order when it belongs to the user")
        void returnsOwnOrder() {
            Order order = Order.builder()
                    .id(200L).user(user).orderItems(new ArrayList<>())
                    .totalPrice(new BigDecimal("79.98"))
                    .status(Order.OrderStatus.PENDING)
                    .paymentStatus(Order.PaymentStatus.PENDING)
                    .shippingFullName("Bob").shippingAddressLine1("123 St")
                    .shippingCity("City").shippingState("ST")
                    .shippingPostalCode("00000").shippingCountry("US").build();

            when(orderRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.of(order));

            OrderResponse response = orderService.getOrderById(200L, 1L);

            assertThat(response.getId()).isEqualTo(200L);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for another user's order")
        void throwsForForeignOrder() {
            when(orderRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrderById(200L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── updatePaymentStatus ───────────────────────────────────────────────────

    @Nested
    @DisplayName("updatePaymentStatus")
    class UpdatePayment {

        private Order pendingOrder;

        @BeforeEach
        void setUpOrder() {
            OrderItem oi = OrderItem.builder()
                    .product(product).quantity(2)
                    .priceAtPurchase(new BigDecimal("39.99")).build();

            pendingOrder = Order.builder()
                    .id(200L).user(user)
                    .orderItems(new ArrayList<>(List.of(oi)))
                    .totalPrice(new BigDecimal("79.98"))
                    .status(Order.OrderStatus.PENDING)
                    .paymentStatus(Order.PaymentStatus.PENDING)
                    .shippingFullName("Bob").shippingAddressLine1("123 St")
                    .shippingCity("City").shippingState("ST")
                    .shippingPostalCode("00000").shippingCountry("US").build();
            oi.setOrder(pendingOrder);
        }

        @Test
        @DisplayName("marks order CONFIRMED when payment PAID")
        void confirmsOnPaymentPaid() {
            when(orderRepository.findById(200L)).thenReturn(Optional.of(pendingOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.updatePaymentStatus(
                    200L, Order.PaymentStatus.PAID, "pi_success_123");

            assertThat(response.getPaymentStatus()).isEqualTo(Order.PaymentStatus.PAID);
            assertThat(response.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
        }

        @Test
        @DisplayName("cancels order and restores stock on payment FAILED")
        void restoresStockOnFailure() {
            int stockBefore = product.getStockQuantity(); // 20

            when(orderRepository.findById(200L)).thenReturn(Optional.of(pendingOrder));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.updatePaymentStatus(
                    200L, Order.PaymentStatus.FAILED, "pi_failed_456");

            assertThat(response.getPaymentStatus()).isEqualTo(Order.PaymentStatus.FAILED);
            assertThat(response.getStatus()).isEqualTo(Order.OrderStatus.CANCELLED);
            // 2 items were deducted when order was created; they must be restored
            assertThat(product.getStockQuantity()).isEqualTo(stockBefore + 2);
            verify(productRepository).save(product);
        }
    }

    // ── getOrdersByUserId ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getOrdersByUserId: returns paginated orders for user")
    void getOrdersByUserId_returnsPaginatedResults() {
        Order order = Order.builder()
                .id(200L).user(user).orderItems(new ArrayList<>())
                .totalPrice(BigDecimal.TEN)
                .status(Order.OrderStatus.PENDING)
                .paymentStatus(Order.PaymentStatus.PENDING)
                .shippingFullName("Bob").shippingAddressLine1("123 St")
                .shippingCity("City").shippingState("ST")
                .shippingPostalCode("00000").shippingCountry("US").build();

        Pageable pageable = PageRequest.of(0, 10);
        Page<Order> page = new PageImpl<>(List.of(order), pageable, 1);
        when(orderRepository.findByUserIdWithItems(1L, pageable)).thenReturn(page);

        Page<OrderResponse> result = orderService.getOrdersByUserId(1L, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(200L);
    }
}
