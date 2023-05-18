package com.quyet.application.repository;

import com.quyet.application.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CartRepository extends JpaRepository<Cart, Long> {
    @Query(value = "SELECT * FROM cart WHERE product_id = ?1 AND size = ?2 AND user_id = ?3", nativeQuery = true)
    Cart findByProductIdAndSizeCorrespondingUser(String productId, Integer productSize, long userId);

    @Query(value = "SELECT * FROM cart WHERE user_id = ?1 ORDER BY created_at DESC", nativeQuery = true)
    List<Cart> getAllCartByUserAndOrderByDateDesc(long userId);
}
