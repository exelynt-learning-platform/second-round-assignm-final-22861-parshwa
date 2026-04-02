package com.ecommerce.service;

import com.ecommerce.dto.request.CartItemRequest;
import com.ecommerce.dto.response.CartResponse;

public interface CartService {
    CartResponse getCartByUserId(Long userId);
    CartResponse addItemToCart(Long userId, CartItemRequest request);
    CartResponse updateCartItem(Long userId, Long cartItemId, CartItemRequest request);
    CartResponse removeItemFromCart(Long userId, Long cartItemId);
    void clearCart(Long userId);
}
