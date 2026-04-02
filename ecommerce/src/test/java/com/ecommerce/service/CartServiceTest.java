package com.ecommerce.service;

import com.ecommerce.dto.request.CartItemRequest;
import com.ecommerce.dto.response.CartResponse;
import com.ecommerce.entity.*;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.InsufficientStockException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CartItemRepository;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.impl.CartServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartService Tests")
class CartServiceTest {

    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private CartServiceImpl cartService;

    private User user;
    private Product product;
    private Cart cart;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).username("alice").email("alice@example.com").build();

        product = Product.builder()
                .id(10L)
                .name("Mechanical Keyboard")
                .price(new BigDecimal("149.99"))
                .stockQuantity(50)
                .active(true)
                .build();

        cart = Cart.builder()
                .id(100L)
                .user(user)
                .items(new ArrayList<>())
                .build();
    }

    // ── getCartByUserId ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("getCartByUserId")
    class GetCart {

        @Test
        @DisplayName("returns existing cart with items")
        void returnsExistingCart() {
            CartItem item = CartItem.builder()
                    .id(1L).cart(cart).product(product)
                    .quantity(2).priceAtAddition(product.getPrice()).build();
            cart.getItems().add(item);

            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));

            CartResponse response = cartService.getCartByUserId(1L);

            assertThat(response.getId()).isEqualTo(100L);
            assertThat(response.getItems()).hasSize(1);
            assertThat(response.getTotalItems()).isEqualTo(2);
            assertThat(response.getTotalPrice()).isEqualByComparingTo("299.98");
        }

        @Test
        @DisplayName("creates and returns empty cart when none exists")
        void createsCartWhenAbsent() {
            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.empty());
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(cartRepository.save(any(Cart.class))).thenReturn(cart);

            CartResponse response = cartService.getCartByUserId(1L);

            assertThat(response).isNotNull();
            verify(cartRepository).save(any(Cart.class));
        }
    }

    // ── addItemToCart ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("addItemToCart")
    class AddItem {

        @Test
        @DisplayName("adds new item when product not already in cart")
        void addsNewItem() {
            CartItemRequest request = new CartItemRequest();
            request.setProductId(10L);
            request.setQuantity(3);

            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));
            when(productRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(product));
            when(cartItemRepository.findByCartIdAndProductId(100L, 10L)).thenReturn(Optional.empty());
            when(cartRepository.save(any(Cart.class))).thenReturn(cart);

            CartResponse response = cartService.addItemToCart(1L, request);

            assertThat(response).isNotNull();
            verify(cartRepository).save(any(Cart.class));
        }

        @Test
        @DisplayName("increments quantity when product already in cart")
        void incrementsExistingItem() {
            CartItem existingItem = CartItem.builder()
                    .id(5L).cart(cart).product(product)
                    .quantity(2).priceAtAddition(product.getPrice()).build();
            cart.getItems().add(existingItem);

            CartItemRequest request = new CartItemRequest();
            request.setProductId(10L);
            request.setQuantity(3);

            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));
            when(productRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(product));
            when(cartItemRepository.findByCartIdAndProductId(100L, 10L))
                    .thenReturn(Optional.of(existingItem));
            when(cartItemRepository.save(any(CartItem.class))).thenReturn(existingItem);
            when(cartRepository.save(any(Cart.class))).thenReturn(cart);

            cartService.addItemToCart(1L, request);

            assertThat(existingItem.getQuantity()).isEqualTo(5);
            verify(cartItemRepository).save(existingItem);
        }

        @Test
        @DisplayName("throws InsufficientStockException when stock too low")
        void throwsOnInsufficientStock() {
            product.setStockQuantity(2);
            CartItemRequest request = new CartItemRequest();
            request.setProductId(10L);
            request.setQuantity(10);

            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));
            when(productRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> cartService.addItemToCart(1L, request))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessageContaining("Mechanical Keyboard")
                    .hasMessageContaining("requested 10, available 2");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for unknown product")
        void throwsForUnknownProduct() {
            CartItemRequest request = new CartItemRequest();
            request.setProductId(999L);
            request.setQuantity(1);

            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));
            when(productRepository.findByIdAndActiveTrue(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.addItemToCart(1L, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── updateCartItem ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateCartItem")
    class UpdateItem {

        @Test
        @DisplayName("updates quantity successfully")
        void updatesQuantity() {
            CartItem item = CartItem.builder()
                    .id(5L).cart(cart).product(product)
                    .quantity(2).priceAtAddition(product.getPrice()).build();

            CartItemRequest request = new CartItemRequest();
            request.setProductId(10L);
            request.setQuantity(7);

            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));
            when(cartItemRepository.findById(5L)).thenReturn(Optional.of(item));
            when(cartItemRepository.save(any(CartItem.class))).thenReturn(item);
            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));

            cartService.updateCartItem(1L, 5L, request);

            assertThat(item.getQuantity()).isEqualTo(7);
        }

        @Test
        @DisplayName("throws BadRequestException when item belongs to different cart")
        void throwsOnWrongCart() {
            Cart otherCart = Cart.builder().id(999L).user(user).items(new ArrayList<>()).build();
            CartItem foreignItem = CartItem.builder()
                    .id(5L).cart(otherCart).product(product)
                    .quantity(1).priceAtAddition(product.getPrice()).build();

            CartItemRequest request = new CartItemRequest();
            request.setProductId(10L);
            request.setQuantity(1);

            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));
            when(cartItemRepository.findById(5L)).thenReturn(Optional.of(foreignItem));

            assertThatThrownBy(() -> cartService.updateCartItem(1L, 5L, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("does not belong to the current user's cart");
        }
    }

    // ── removeItemFromCart ────────────────────────────────────────────────────

    @Nested
    @DisplayName("removeItemFromCart")
    class RemoveItem {

        @Test
        @DisplayName("removes item from cart successfully")
        void removesItem() {
            CartItem item = CartItem.builder()
                    .id(5L).cart(cart).product(product)
                    .quantity(1).priceAtAddition(product.getPrice()).build();
            cart.getItems().add(item);

            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));
            when(cartItemRepository.findById(5L)).thenReturn(Optional.of(item));
            when(cartRepository.save(any(Cart.class))).thenReturn(cart);

            cartService.removeItemFromCart(1L, 5L);

            assertThat(cart.getItems()).isEmpty();
            verify(cartRepository).save(cart);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for non-existent item")
        void throwsForMissingItem() {
            when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));
            when(cartItemRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.removeItemFromCart(1L, 999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── clearCart ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("clearCart")
    class ClearCart {

        @Test
        @DisplayName("clears all items when cart exists")
        void clearsItems() {
            CartItem item = CartItem.builder()
                    .id(5L).cart(cart).product(product)
                    .quantity(2).priceAtAddition(product.getPrice()).build();
            cart.getItems().add(item);

            when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
            when(cartRepository.save(any(Cart.class))).thenReturn(cart);

            cartService.clearCart(1L);

            assertThat(cart.getItems()).isEmpty();
            verify(cartRepository).save(cart);
        }

        @Test
        @DisplayName("does nothing when cart does not exist")
        void noOpWhenCartAbsent() {
            when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());

            assertThatNoException().isThrownBy(() -> cartService.clearCart(1L));
            verify(cartRepository, never()).save(any());
        }
    }
}
