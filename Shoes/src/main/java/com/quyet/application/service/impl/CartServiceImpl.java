package com.quyet.application.service.impl;

import com.quyet.application.entity.Cart;
import com.quyet.application.entity.Product;
import com.quyet.application.exception.BadRequestException;
import com.quyet.application.exception.NotFoundException;
import com.quyet.application.model.dto.CartInfoDTO;
import com.quyet.application.repository.CartRepository;
import com.quyet.application.repository.ProductRepository;
import com.quyet.application.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class CartServiceImpl implements CartService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Override
    public boolean addTopCart(String productId, Integer productSize, boolean isAdd, long userId) {
        try{
            //verify exist product
            if(productSize == null){
                throw new BadRequestException("product size does not choosen");
            }

            Optional<Product> rs = productRepository.findById(productId);
            if (rs.isEmpty()) {
                throw new NotFoundException("Sản phẩm không tồn tại");
            }
            Product product = rs.get();

            if (product.getStatus() != 1) {
                throw new NotFoundException("Sản phâm không tồn tại");
            }

            Cart cart = cartRepository.findByProductIdAndSizeCorrespondingUser(productId,productSize, userId);
            if(!isAdd){
                if(cart != null){
                    if(cart.getQuantity() <=  1){
                        cartRepository.delete(cart);
                    }
                    else{
                        cart.setQuantity(cart.getQuantity() - 1);
                        cart.setModifiedAt(new Timestamp(System.currentTimeMillis()));
                        cartRepository.save(cart);
                    }
                }
            }
            else{
                //user add the first time to cart
                if(cart == null){
                    cart = new Cart();
                    cart.setGuid(UUID.randomUUID().toString().substring(6));
                    cart.setSize(productSize);
                    cart.setProductId(productId);
                    cart.setUser(userId);
                    cart.setQuantity(1);
                    cart.setCreatedAt(new Timestamp(System.currentTimeMillis()));
                    cart.setModifiedAt(new Timestamp(System.currentTimeMillis()));
                    cartRepository.save(cart);
                }
                else {
                    cart.setQuantity(cart.getQuantity() + 1);
                    cart.setModifiedAt(new Timestamp(System.currentTimeMillis()));
                    cartRepository.save(cart);
                }
            }

        }catch (Exception exception){
            return false;
        }

        return true;
    }

    @Override
    public List<CartInfoDTO> getAllCartAndOrderByDateDesc(long userId) {
        List<CartInfoDTO> cartInfoResponse = new ArrayList<>();

        try{
            List<Cart> carts = cartRepository.getAllCartByUserAndOrderByDateDesc(userId);
            if(carts != null){
                for (Cart cart: carts) {
                    CartInfoDTO cartInfoItem = new CartInfoDTO();
                    cartInfoItem.setCartId(cart.getId());
                    cartInfoItem.setProductId(cart.getProductId());
                    cartInfoItem.setSize(cart.getSize());
                    cartInfoItem.setQuantity(cart.getQuantity());

                    //retrieve info of current price
                    Optional<Product> product = productRepository.findById(cart.getProductId());
                    if(product.isEmpty()){
                        continue;
                    }

                    cartInfoItem.setProductName(product.get().getName());
                    cartInfoItem.setProductImage(product.get().getImages().get(0));
                    cartInfoItem.setPrice(product.get().getSalePrice() * cart.getQuantity());
                    cartInfoResponse.add(cartInfoItem);
                }
            }

        }catch (Exception ex){
            return new ArrayList<>();
        }
        return cartInfoResponse;
    }

    @Override
    public void deleteCartByUser(long id, Long cartId) {
        Optional<Cart> cart = cartRepository.findById(cartId);

        if(!cart.isEmpty() && cart.get().getUser() == id){
            cartRepository.delete(cart.get());
        }
    }

    @Override
    public List<Cart> getCartByListId(List<Long> item) {
        List<Cart> cartResponse = new ArrayList<>();

        for (Long cartId : item) {
            Optional<Cart> cart = cartRepository.findById(cartId);
            if(!cart.isEmpty())
            {
                cartResponse.add(cart.get());
            }
        }
        return cartResponse;
    }
}
