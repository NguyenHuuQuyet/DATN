package com.quyet.application.model.dto;

import com.quyet.application.entity.OrderDetail;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderItemDTO extends OrderDetail {
    private String productName;
    private String productImage;
    private String slug;
}
