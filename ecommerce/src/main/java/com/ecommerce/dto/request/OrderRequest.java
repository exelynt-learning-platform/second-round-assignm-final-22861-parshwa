package com.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrderRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 100)
    private String shippingFullName;

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 200)
    private String shippingAddressLine1;

    @Size(max = 200)
    private String shippingAddressLine2;

    @NotBlank(message = "City is required")
    @Size(max = 100)
    private String shippingCity;

    @NotBlank(message = "State is required")
    @Size(max = 100)
    private String shippingState;

    @NotBlank(message = "Postal code is required")
    @Size(max = 20)
    private String shippingPostalCode;

    @NotBlank(message = "Country is required")
    @Size(max = 100)
    private String shippingCountry;

    @Size(max = 20)
    private String shippingPhone;
}
