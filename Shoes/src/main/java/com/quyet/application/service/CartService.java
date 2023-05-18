package com.quyet.application.service;

import com.quyet.application.entity.Cart;
import com.quyet.application.model.dto.CartInfoDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface CartService {
    boolean addTopCart(String productId, Integer productSize, boolean isAdd, long userId);

    List<CartInfoDTO> getAllCartAndOrderByDateDesc(long userId);

    void deleteCartByUser(long id, Long cartId);

    List<Cart> getCartByListId(List<Long> item);
}
