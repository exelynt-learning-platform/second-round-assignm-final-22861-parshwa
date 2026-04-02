package com.ecommerce.service;

import com.ecommerce.dto.request.ProductRequest;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.entity.Product;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService Tests")
class ProductServiceTest {

    @Mock private ProductRepository productRepository;

    @InjectMocks private ProductServiceImpl productService;

    private Product product;
    private ProductRequest productRequest;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .id(1L).name("USB Hub").description("7-port USB 3.0 hub")
                .price(new BigDecimal("29.99")).stockQuantity(100)
                .imageUrl("https://cdn.example.com/usb-hub.jpg")
                .category("Electronics").active(true).build();

        productRequest = new ProductRequest();
        productRequest.setName("USB Hub");
        productRequest.setDescription("7-port USB 3.0 hub");
        productRequest.setPrice(new BigDecimal("29.99"));
        productRequest.setStockQuantity(100);
        productRequest.setImageUrl("https://cdn.example.com/usb-hub.jpg");
        productRequest.setCategory("Electronics");
    }

    // ── createProduct ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createProduct")
    class Create {

        @Test
        @DisplayName("saves and returns new product")
        void success() {
            when(productRepository.save(any(Product.class))).thenReturn(product);

            ProductResponse response = productService.createProduct(productRequest);

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getName()).isEqualTo("USB Hub");
            assertThat(response.getPrice()).isEqualByComparingTo("29.99");
            assertThat(response.getStockQuantity()).isEqualTo(100);
            assertThat(response.isActive()).isTrue();
            verify(productRepository).save(any(Product.class));
        }
    }

    // ── getProductById ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getProductById")
    class GetById {

        @Test
        @DisplayName("returns product for valid active id")
        void returnsProduct() {
            when(productRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(product));

            ProductResponse response = productService.getProductById(1L);

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getName()).isEqualTo("USB Hub");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for unknown id")
        void throwsForUnknownId() {
            when(productRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getProductById(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for soft-deleted product")
        void throwsForInactiveProduct() {
            product.setActive(false);
            when(productRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getProductById(1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── getAllProducts ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllProducts")
    class GetAll {

        @Test
        @DisplayName("returns paginated product list")
        void returnsPaginatedResults() {
            Pageable pageable = PageRequest.of(0, 20, Sort.by("createdAt"));
            Page<Product> page = new PageImpl<>(List.of(product), pageable, 1);
            when(productRepository.findByActiveTrue(pageable)).thenReturn(page);

            Page<ProductResponse> result = productService.getAllProducts(pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("USB Hub");
        }

        @Test
        @DisplayName("returns empty page when no products exist")
        void returnsEmptyPage() {
            Pageable pageable = PageRequest.of(0, 20);
            when(productRepository.findByActiveTrue(pageable))
                    .thenReturn(Page.empty(pageable));

            Page<ProductResponse> result = productService.getAllProducts(pageable);

            assertThat(result.getTotalElements()).isZero();
        }
    }

    // ── updateProduct ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateProduct")
    class Update {

        @Test
        @DisplayName("applies all field changes and saves")
        void updatesAllFields() {
            productRequest.setName("USB Hub Pro");
            productRequest.setPrice(new BigDecimal("49.99"));
            productRequest.setStockQuantity(50);

            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            ProductResponse response = productService.updateProduct(1L, productRequest);

            assertThat(response.getName()).isEqualTo("USB Hub Pro");
            assertThat(response.getPrice()).isEqualByComparingTo("49.99");
            assertThat(response.getStockQuantity()).isEqualTo(50);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for unknown id")
        void throwsForUnknown() {
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.updateProduct(99L, productRequest))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── deleteProduct ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteProduct")
    class Delete {

        @Test
        @DisplayName("soft-deletes product by setting active=false")
        void softDeletes() {
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            productService.deleteProduct(1L);

            assertThat(product.isActive()).isFalse();
            verify(productRepository).save(product);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for unknown id")
        void throwsForUnknown() {
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.deleteProduct(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── searchProducts ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("searchProducts")
    class Search {

        @Test
        @DisplayName("delegates to repository search and maps results")
        void delegatesSearch() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Product> page = new PageImpl<>(List.of(product), pageable, 1);
            when(productRepository.searchProducts("usb", pageable)).thenReturn(page);

            Page<ProductResponse> result = productService.searchProducts("usb", pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getCategory()).isEqualTo("Electronics");
        }
    }

    // ── getProductsByPriceRange ───────────────────────────────────────────────

    @Nested
    @DisplayName("getProductsByPriceRange")
    class PriceRange {

        @Test
        @DisplayName("returns products within range")
        void returnsProductsInRange() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Product> page = new PageImpl<>(List.of(product), pageable, 1);
            when(productRepository.findByPriceRange(
                    new BigDecimal("10.00"), new BigDecimal("50.00"), pageable))
                    .thenReturn(page);

            Page<ProductResponse> result = productService.getProductsByPriceRange(
                    new BigDecimal("10.00"), new BigDecimal("50.00"), pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getPrice())
                    .isEqualByComparingTo("29.99");
        }
    }
}
