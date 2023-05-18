package com.quyet.application.service.impl;

import com.quyet.application.entity.*;
import com.quyet.application.exception.BadRequestException;
import com.quyet.application.exception.InternalServerException;
import com.quyet.application.exception.NotFoundException;
import com.quyet.application.model.dto.OrderDetailDTO;
import com.quyet.application.model.dto.OrderInfoDTO;
import com.quyet.application.model.request.CreateOrderRequest;
import com.quyet.application.model.request.UpdateDetailOrder;
import com.quyet.application.model.request.UpdateStatusOrderRequest;
import com.quyet.application.repository.*;
import com.quyet.application.service.OrderService;
import com.quyet.application.service.PromotionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.quyet.application.config.Contant.*;

@Controller
public class OrderServiceImpl implements OrderService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductSizeRepository productSizeRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PromotionService promotionService;

    @Autowired
    private StatisticRepository statisticRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private OrderDetailRepository orderDetailRepository;

    @Override
    public Page<Order> adminGetListOrders(String id, String name, String phone, String status, String product, int page) {
        page--;
        if (page < 0) {
            page = 0;
        }
        int limit = 10;
        Pageable pageable = PageRequest.of(page, limit, Sort.by("created_at").descending());
        return orderRepository.adminGetListOrder(id, name, phone, status, product, pageable);
    }

    @Transactional
    @Override
    public Order createOrder(CreateOrderRequest createOrderRequest, long userId) {
        Order order = new Order();

        long totalQuantity = 0;
        long totalPrice = 0;
        long discountPrice = 0;
        List<OrderDetail> orderDetails = new ArrayList<>();
        List<Cart> cartUse = new ArrayList<>();

        //Kiểm tra sản phẩm có tồn tại
        List<HashMap<String, Long>> orderItems = createOrderRequest.getOrderItems();
        for(HashMap<String, Long> orderItem : orderItems){
            Long cartId = orderItem.getOrDefault("cart", null);
            Long quantity = orderItem.getOrDefault("quantity", null);

            if(cartId == null || quantity == null || quantity <= 0){
                throw new NotFoundException("Yêu cầu đặt hàng không phù hợp");
            }

            //Lấy thông tin cart
            Optional<Cart> cartOptional = cartRepository.findById(cartId);
            if(cartOptional.isEmpty()) {
                throw new NotFoundException("Yêu cầu đặt hàng không phù hợp");
            }

            Cart cart = cartOptional.get();
            cartUse.add(cart);
            //Kiểm tra size có sẵn
            ProductSize productSize = productSizeRepository.checkProductAndSizeAvailable(cart.getProductId(), cart.getSize());
            Optional<Product> product = productRepository.findById(cart.getProductId());
            if (product.isEmpty() || productSize == null || productSize.getQuantity() < quantity) {
                throw new BadRequestException(String.format("Sản phẩm %s còn số lượng không quá %d", product.get().getName(), productSize.getQuantity()));
            }

            OrderDetail orderDetail = new OrderDetail();
            orderDetail.setProduct(cart.getProductId());
            orderDetail.setPrice(product.get().getSalePrice());
            orderDetail.setSize(cart.getSize());
            orderDetail.setQuantity(quantity);
            orderDetail.setCreatedAt(new Timestamp(System.currentTimeMillis()));
            orderDetail.setModifiedAt(new Timestamp(System.currentTimeMillis()));

            orderDetails.add(orderDetail);

            totalQuantity += quantity;
            totalPrice += product.get().getSalePrice() * quantity;
        }

        //caculating price with coupon
        if(createOrderRequest.getCouponCode() != null && !"".equals(createOrderRequest.getCouponCode())) {
            String coupon = createOrderRequest.getCouponCode();

            Promotion promotion = promotionService.checkPromotion(coupon);
            if (promotion == null) {
                throw new BadRequestException("Mã code không hợp lệ");
            }

            if(promotion.getDiscountType() == 1) {
                discountPrice = totalPrice * promotion.getDiscountValue() / 100;
            }
            else {
                discountPrice = totalPrice - promotion.getDiscountValue();
            }

            if(discountPrice > promotion.getMaximumDiscountValue()){
                discountPrice = promotion.getMaximumDiscountValue();
            }

            Order.UsedPromotion orderPromotion = new Order.UsedPromotion();
            orderPromotion.setCouponCode(coupon);
            orderPromotion.setDiscountValue(discountPrice);
            orderPromotion.setDiscountType(promotion.getDiscountType());
            orderPromotion.setMaximumDiscountValue(promotion.getMaximumDiscountValue());
            order.setPromotion(orderPromotion);

        }

        //Minus with discount price
        totalPrice -= discountPrice;

        User user = new User();
        user.setId(userId);
        order.setCreatedBy(user);
        order.setModifiedBy(user);
        order.setBuyer(user);
        order.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        order.setReceiverAddress(createOrderRequest.getReceiverAddress());
        order.setReceiverName(createOrderRequest.getReceiverName());
        order.setReceiverPhone(createOrderRequest.getReceiverPhone());
        order.setNote(createOrderRequest.getNote());
        order.setTotalPrice(totalPrice);
        order.setStatus(ORDER_STATUS);
        order.setCoupon(createOrderRequest.getCouponCode());
        order.setQuantity(Integer.valueOf(String.valueOf(totalQuantity)));

        //save summary order
        orderRepository.save(order);

        for (OrderDetail orderItem : orderDetails) {
            orderItem.setOrderId(order.getId());
        }

        //save detail order
        orderDetailRepository.saveAll(orderDetails);


        //delete cart
        cartRepository.deleteAll(cartUse);
        return order;
    }

    @Override
    public void updateDetailOrder(UpdateDetailOrder updateDetailOrder, long id, long userId) {
        //Kiểm trả có đơn hàng
        Optional<Order> rs = orderRepository.findById(id);
        if (rs.isEmpty()) {
            throw new NotFoundException("Đơn hàng không tồn tại");
        }

        Order order = rs.get();
        //Kiểm tra trạng thái đơn hàng
        if (order.getStatus() != ORDER_STATUS) {
            throw new BadRequestException("Chỉ cập nhật đơn hàng ở trạng thái chờ lấy hàng");
        }

        //Kiểm tra size sản phẩm
        Optional<Product> product = productRepository.findById(updateDetailOrder.getProductId());
        if (product.isEmpty()) {
            throw new BadRequestException("Sản phẩm không tồn tại");
        }
        //Kiểm tra giá
        if (product.get().getSalePrice() != updateDetailOrder.getProductPrice()) {
            throw new BadRequestException("Giá sản phẩm thay đổi vui lòng đặt hàng lại");
        }

        ProductSize productSize = productSizeRepository.checkProductAndSizeAvailable(updateDetailOrder.getProductId(), updateDetailOrder.getSize());
        if (productSize == null) {
            throw new BadRequestException("Size giày sản phẩm tạm hết, Vui lòng chọn sản phẩm khác");
        }

        //Kiểm tra khuyến mại
        if (updateDetailOrder.getCouponCode() != "") {
            Promotion promotion = promotionService.checkPromotion(updateDetailOrder.getCouponCode());
            if (promotion == null) {
                throw new NotFoundException("Mã khuyến mãi không tồn tại hoặc chưa được kích hoạt");
            }
            long promotionPrice = promotionService.calculatePromotionPrice(updateDetailOrder.getProductPrice(), promotion);
            if (promotionPrice != updateDetailOrder.getTotalPrice()) {
                throw new BadRequestException("Tổng giá trị đơn hàng thay đổi. Vui lòng kiểm tra và đặt lại đơn hàng");
            }
            Order.UsedPromotion usedPromotion = new Order.UsedPromotion(updateDetailOrder.getCouponCode(), promotion.getDiscountType(), promotion.getDiscountValue(), promotion.getMaximumDiscountValue());
            order.setPromotion(usedPromotion);
        }

        order.setModifiedAt(new Timestamp(System.currentTimeMillis()));
        order.setProduct(product.get());
        order.setSize(updateDetailOrder.getSize());
        order.setPrice(updateDetailOrder.getProductPrice());
        order.setTotalPrice(updateDetailOrder.getTotalPrice());


        order.setStatus(ORDER_STATUS);
        User user = new User();
        user.setId(userId);
        order.setModifiedBy(user);
        try {
            orderRepository.save(order);
        } catch (Exception e) {
            throw new InternalServerException("Lỗi khi cập nhật");
        }
    }


    @Override
    public Order findOrderById(long id) {
        Optional<Order> order = orderRepository.findById(id);
        if (order.isEmpty()) {
            throw new NotFoundException("Đơn hàng không tồn tại");
        }
        return order.get();
    }

    @Override
    public void updateStatusOrder(UpdateStatusOrderRequest updateStatusOrderRequest, long orderId, long userId) {
        Optional<Order> rs = orderRepository.findById(orderId);
        if (rs.isEmpty()) {
            throw new NotFoundException("Đơn hàng không tồn tại");
        }
        Order order = rs.get();
        //Kiểm tra trạng thái của đơn hàng
        boolean check = false;
        for (Integer status : LIST_ORDER_STATUS) {
            if (status == updateStatusOrderRequest.getStatus()) {
                check = true;
                break;
            }
        }
        if (!check) {
            throw new BadRequestException("Trạng thái đơn hàng không hợp lệ");
        }

        //Lấy danh sách sản phẩm đặt hàng
        List<OrderDetail> orderDetails = orderDetailRepository.findOrderDetailByOrderId(orderId);

        //Cập nhật trạng thái đơn hàng
        if (order.getStatus() == ORDER_STATUS) {
            //Đơn hàng ở trạng thái chờ lấy hàng
            if (updateStatusOrderRequest.getStatus() == DELIVERY_STATUS) {
                // Trừ đi sản phẩm đã đặt hàng khỏi kho cho vận chuyển
                for(OrderDetail orderDetail :  orderDetails){
                    productSizeRepository.minusOneProductBySize(orderDetail.getQuantity(), orderDetail.getProduct(), orderDetail.getSize());
                }
                //Đơn hàng ở trạng thái đã giao hàng
            } else if (updateStatusOrderRequest.getStatus() == COMPLETED_STATUS) {
                // Tăng lượt bán cho sản phẩm
                for(OrderDetail orderDetail :  orderDetails){
                    productSizeRepository.minusOneProductBySize(orderDetail.getQuantity(), orderDetail.getProduct(), orderDetail.getSize());
                    productRepository.plusOneProductTotalSold(orderDetail.getQuantity(), orderDetail.getProduct());
                }
            } else if (updateStatusOrderRequest.getStatus() != CANCELED_STATUS) {
                throw new BadRequestException("Không thế chuyển sang trạng thái này");
            }
            //Đơn hàng ở trạng thái đang giao hàng
        } else if (order.getStatus() == DELIVERY_STATUS) {
            //Đơn hàng ở trạng thái đã giao hàng
            if (updateStatusOrderRequest.getStatus() == COMPLETED_STATUS) {
                //Cộng một sản phẩm vào sản phẩm đã bán và cộng tiền
                // Tăng lượt bán cho sản phẩm
                for(OrderDetail orderDetail :  orderDetails){
                    productRepository.plusOneProductTotalSold(orderDetail.getQuantity(), orderDetail.getProduct());
                }
                //Đơn hàng ở trạng thái đã hủy
            } else if (updateStatusOrderRequest.getStatus() == RETURNED_STATUS) {
                //Cộng lại một sản phẩm đã bị trừ
                for(OrderDetail orderDetail :  orderDetails){
                    productSizeRepository.plusOneProductBySize(orderDetail.getQuantity(), orderDetail.getProduct(), orderDetail.getSize());
                }
                //Đơn hàng ở trạng thái đã trả hàng
            } else if (updateStatusOrderRequest.getStatus() == CANCELED_STATUS) {
                //Cộng lại một sản phẩm đã bị trừ
                for(OrderDetail orderDetail :  orderDetails){
                    productSizeRepository.plusOneProductBySize(orderDetail.getQuantity(), orderDetail.getProduct(), orderDetail.getSize());
                }
            } else if (updateStatusOrderRequest.getStatus() != DELIVERY_STATUS) {
                throw new BadRequestException("Không thế chuyển sang trạng thái này");
            }
            //Đơn hàng ở trạng thái đã giao hàng
        } else if (order.getStatus() == COMPLETED_STATUS) {
            //Đơn hàng đang ở trạng thái đã hủy
            if (updateStatusOrderRequest.getStatus() == RETURNED_STATUS) {
                //Cộng một sản phẩm đã bị trừ và trừ đi một sản phẩm đã bán và trừ số tiền
                for(OrderDetail orderDetail :  orderDetails){
                    productSizeRepository.plusOneProductBySize(orderDetail.getQuantity(), orderDetail.getProduct(), orderDetail.getSize());
                    productRepository.minusOneProductTotalSold(orderDetail.getQuantity(), orderDetail.getProduct());
                }
            } else if (updateStatusOrderRequest.getStatus() != COMPLETED_STATUS) {
                throw new BadRequestException("Không thế chuyển sang trạng thái này");
            }
        } else {
            if (order.getStatus() != updateStatusOrderRequest.getStatus()) {
                throw new BadRequestException("Không thế chuyển đơn hàng sang trạng thái này");
            }
        }

        User user = new User();
        user.setId(userId);
        order.setModifiedBy(user);
        order.setModifiedAt(new Timestamp(System.currentTimeMillis()));
        order.setStatus(updateStatusOrderRequest.getStatus());
        try {
            orderRepository.save(order);
        } catch (Exception e) {
            throw new InternalServerException("Lỗi khi cập nhật trạng thái");
        }
    }

    @Override
    public List<OrderInfoDTO> getListOrderOfPersonByStatus(int status, long userId) {
        List<OrderInfoDTO> orderListResponse = new ArrayList<>();

        List<Order> list = orderRepository.getListOrderOfPersonByStatus(status, userId);

        for (Order dto : list) {
            OrderInfoDTO orderInfo = new OrderInfoDTO();

            //retrieve info product
            StringBuilder productName = new StringBuilder();
            List<OrderDetail> orderItem = orderDetailRepository.findOrderDetailByOrderId(dto.getId());
            for(OrderDetail item: orderItem){
                Optional<Product> product = productRepository.findById(item.getProduct());
                if(!product.isEmpty()){
                    productName.append(product.get().getName()).append(": ").append(item.getQuantity()).append("<br />");
                }
            }
            orderInfo.setId(dto.getId());
            orderInfo.setProductImg("data:image/png;base64,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");
            orderInfo.setProductName(productName.toString());
            orderInfo.setTotalPrice(dto.getTotalPrice());

            orderListResponse.add(orderInfo);
        }
        return orderListResponse;
    }

    @Override
    public OrderDetailDTO userGetDetailById(long id, long userId) {
        OrderDetailDTO order = orderRepository.userGetDetailById(id, userId);
        if (order == null) {
            return null;
        }

        if (order.getStatus() == ORDER_STATUS) {
            order.setStatusText("Chờ lấy hàng");
        } else if (order.getStatus() == DELIVERY_STATUS) {
            order.setStatusText("Đang giao hàng");
        } else if (order.getStatus() == COMPLETED_STATUS) {
            order.setStatusText("Đã giao hàng");
        } else if (order.getStatus() == CANCELED_STATUS) {
            order.setStatusText("Đơn hàng đã trả lại");
        } else if (order.getStatus() == RETURNED_STATUS) {
            order.setStatusText("Đơn hàng đã hủy");
        }

        for (int i = 0; i < SIZE_VN.size(); i++) {
            if (SIZE_VN.get(i) == order.getSizeVn()) {
                order.setSizeUs(SIZE_US[i]);
                order.setSizeCm(SIZE_CM[i]);
            }
        }

        return order;
    }

    @Override
    public void userCancelOrder(long id, long userId) {
        Optional<Order> rs = orderRepository.findById(id);
        if (rs.isEmpty()) {
            throw new NotFoundException("Đơn hàng không tồn tại");
        }
        Order order = rs.get();
        if (order.getBuyer().getId() != userId) {
            throw new BadRequestException("Bạn không phải chủ nhân đơn hàng");
        }
        if (order.getStatus() != ORDER_STATUS) {
            throw new BadRequestException("Trạng thái đơn hàng không phù hợp để hủy. Vui lòng liên hệ với shop để được hỗ trợ");
        }

        order.setStatus(CANCELED_STATUS);
        orderRepository.save(order);
    }

    @Override
    public long getCountOrder() {
        return orderRepository.count();
    }

//    public void statistic(long amount, int quantity, Order order) {
//        Statistic statistic = statisticRepository.findByCreatedAT();
//        if (statistic != null){
//            statistic.setOrder(order);
//            statistic.setSales(statistic.getSales() + amount);
//            statistic.setQuantity(statistic.getQuantity() + quantity);
//            statistic.setProfit(statistic.getSales() - (statistic.getQuantity() * order.getProduct().getPrice()));
//            statisticRepository.save(statistic);
//        }else {
//            Statistic statistic1 = new Statistic();
//            statistic1.setOrder(order);
//            statistic1.setSales(amount);
//            statistic1.setQuantity(quantity);
//            statistic1.setProfit(amount - (quantity * order.getProduct().getPrice()));
//            statistic1.setCreatedAt(new Timestamp(System.currentTimeMillis()));
//            statisticRepository.save(statistic1);
//        }
//    }

    public void updateStatistic(long amount, int quantity, Order order) {
        Statistic statistic = statisticRepository.findByCreatedAT();
        if (statistic != null) {
            statistic.setOrder(order);
            statistic.setSales(statistic.getSales() - amount);
            statistic.setQuantity(statistic.getQuantity() - quantity);
            statistic.setProfit(statistic.getSales() - (statistic.getQuantity() * order.getProduct().getPrice()));
            statisticRepository.save(statistic);
        }
    }
}
