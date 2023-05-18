package com.quyet.application.model.dto;

import com.quyet.application.entity.Order;
import com.quyet.application.entity.OrderDetail;
import com.quyet.application.entity.Product;
import com.quyet.application.entity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.sql.Timestamp;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class OrderDetailV2DTO {
    private long id;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String note;
    private long totalPrice;
    private int quantity;
    private String coupon;
    private User buyer;
    private int status;
    private Timestamp createdAt;
    private Timestamp modifiedAt;
    private User createdBy;
    private User modifiedBy;
    private List<OrderItemDTO> orderDetail;
    private Order.UsedPromotion promotion;
    private long promotionPrice;
}
