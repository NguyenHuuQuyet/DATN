package com.quyet.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CartInfoDTO {
    private long cartId;

    private String productId;

    private String productName;

    private String productImage;

    private int size;

    private int quantity;

    private long price;
}
