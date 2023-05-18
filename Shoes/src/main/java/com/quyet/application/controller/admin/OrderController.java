package com.quyet.application.controller.admin;

import com.quyet.application.entity.*;
import com.quyet.application.exception.BadRequestException;
import com.quyet.application.model.dto.*;
import com.quyet.application.model.request.CreateOrderRequest;
import com.quyet.application.model.request.UpdateDetailOrder;
import com.quyet.application.model.request.UpdateStatusOrderRequest;
import com.quyet.application.repository.OrderDetailRepository;
import com.quyet.application.security.CustomUserDetails;
import com.quyet.application.service.OrderService;
import com.quyet.application.service.ProductService;
import com.quyet.application.service.PromotionService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

import static com.quyet.application.config.Contant.*;

@Controller
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductService productService;

    @Autowired
    private PromotionService promotionService;

    @Autowired
    private OrderDetailRepository orderDetailRepository;

    @GetMapping("/admin/orders")
    public String getListOrderPage(Model model,
                                   @RequestParam(defaultValue = "1") Integer page,
                                   @RequestParam(defaultValue = "", required = false) String id,
                                   @RequestParam(defaultValue = "", required = false) String name,
                                   @RequestParam(defaultValue = "", required = false) String phone,
                                   @RequestParam(defaultValue = "", required = false) String status,
                                   @RequestParam(defaultValue = "", required = false) String product) {

        //Lấy danh sách sản phẩm
        List<ShortProductInfoDTO> productList = productService.getListProduct();
        model.addAttribute("productList", productList);


        //Lấy danh sách đơn hàng
        Page<Order> orderPage = orderService.adminGetListOrders(id, name, phone, status, product, page);
        model.addAttribute("orderPage", orderPage.getContent());
        model.addAttribute("totalPages", orderPage.getTotalPages());
        model.addAttribute("currentPage", orderPage.getPageable().getPageNumber() + 1);

        return "admin/order/list";
    }

    @GetMapping("/admin/orders/create")
    public String createOrderPage(Model model) {

        //Get list product
        List<ShortProductInfoDTO> products = productService.getAvailableProducts();
        model.addAttribute("products", products);

        // Get list size
        model.addAttribute("sizeVn", SIZE_VN);

//        //Get list valid promotion
        List<Promotion> promotions = promotionService.getAllValidPromotion();
        model.addAttribute("promotions", promotions);
        return "admin/order/create";
    }

    @PostMapping("/api/admin/orders")
    public ResponseEntity<Object> createOrder(@Valid @RequestBody CreateOrderRequest createOrderRequest) {
        User user = ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUser();
        Order order = orderService.createOrder(createOrderRequest, user.getId());
        return ResponseEntity.ok(order);
    }

    @GetMapping("/admin/orders/update/{id}")
    public String updateOrderPage(Model model, @PathVariable long id) {

        OrderDetailV2DTO orderResponse = new OrderDetailV2DTO();
        List<OrderItemDTO> orderItems = new ArrayList<>();

        Order order = orderService.findOrderById(id);
        BeanUtils.copyProperties(order, orderResponse);

        //get order detail
        List<OrderDetail> orderDetails = orderDetailRepository.findOrderDetailByOrderId(order.getId());
        for(OrderDetail orderDetail: orderDetails){
            OrderItemDTO orderItem = new OrderItemDTO();
            BeanUtils.copyProperties(orderDetail, orderItem);

            //retrieve product name
            Product product = productService.getProductById(orderDetail.getProduct());
            orderItem.setProductName(product.getName());
            orderItem.setSlug(product.getSlug());
            orderItems.add(orderItem);
        }

        // check khuyến mãi
        if(order.getPromotion() != null){
            Order.UsedPromotion usedPromotion = order.getPromotion();

            OrderItemDTO orderItem = new OrderItemDTO();
            orderItem.setProductName(usedPromotion.getCouponCode() + " (giảm giá áp dụng)");
            orderItem.setPrice(usedPromotion.getDiscountValue());
            orderItem.setQuantity(1);
            orderItems.add(orderItem);
        }

        OrderItemDTO orderItem = new OrderItemDTO();
        orderItem.setProductName("Tổng đơn hàng");
        orderItem.setPrice(order.getTotalPrice());
        orderItem.setQuantity(order.getQuantity());
        orderItems.add(orderItem);
        orderResponse.setOrderDetail(orderItems);

        if (order.getStatus() == ORDER_STATUS) {
            // Get list product to select
            List<ShortProductInfoDTO> products = productService.getAvailableProducts();
            model.addAttribute("products", products);

            // Get list valid promotion
            List<Promotion> promotions = promotionService.getAllValidPromotion();
            model.addAttribute("promotions", promotions);
            if (order.getPromotion() != null) {
                boolean validPromotion = false;
                for (Promotion promotion : promotions) {
                    if (promotion.getCouponCode().equals(order.getPromotion().getCouponCode())) {
                        validPromotion = true;
                        break;
                    }
                }
                if (!validPromotion) {
                    promotions.add(new Promotion(order.getPromotion()));
                }
            }

            // Check size available
            //boolean sizeIsAvailable = productService.checkProductSizeAvailable(order.getProduct().getId(), order.getSize());
            model.addAttribute("sizeIsAvailable", true);
        }

        model.addAttribute("order", orderResponse);
        return "admin/order/edit";
    }

    @PutMapping("/api/admin/orders/update-detail/{id}")
    public ResponseEntity<Object> updateDetailOrder(@Valid @RequestBody UpdateDetailOrder detailOrder, @PathVariable long id) {
        User user = ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUser();
        orderService.updateDetailOrder(detailOrder, id, user.getId());
        return ResponseEntity.ok("Cập nhật thành công");
    }

    @PutMapping("/api/admin/orders/update-status/{id}")
    public ResponseEntity<Object> updateStatusOrder(@Valid @RequestBody UpdateStatusOrderRequest updateStatusOrderRequest, @PathVariable long id) {
        User user = ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUser();
        orderService.updateStatusOrder(updateStatusOrderRequest, id, user.getId());
        return ResponseEntity.ok("Cập nhật trạng thái thành công");
    }

    @GetMapping("/tai-khoan/lich-su-giao-dich")
    public String getOrderHistoryPage(Model model){

        //Get list order pending
        User user =((CustomUserDetails)SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUser();
        List<OrderInfoDTO> orderList = orderService.getListOrderOfPersonByStatus(ORDER_STATUS,user.getId());
        model.addAttribute("orderList",orderList);

        return "shop/order_history";
    }

    @GetMapping("/api/get-order-list")
    public ResponseEntity<Object> getListOrderByStatus(@RequestParam int status) {
        // Validate status
        if (!LIST_ORDER_STATUS.contains(status)) {
            throw new BadRequestException("Trạng thái đơn hàng không hợp lệ");
        }

        User user = ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUser();
        List<OrderInfoDTO> orders = orderService.getListOrderOfPersonByStatus(status, user.getId());

        return ResponseEntity.ok(orders);
    }

    @GetMapping("/tai-khoan/lich-su-giao-dich/{id}")
    public String getDetailOrderPage(Model model, @PathVariable int id) {
        User user = ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUser();

        OrderDetailV2DTO orderResponse = new OrderDetailV2DTO();
        List<OrderItemDTO> orderItems = new ArrayList<>();

        Order order = orderService.findOrderById(id);
        BeanUtils.copyProperties(order, orderResponse);

        //get order detail
        long totalPrice = 0;
        List<OrderDetail> orderDetails = orderDetailRepository.findOrderDetailByOrderId(order.getId());
        for(OrderDetail orderDetail: orderDetails){
            OrderItemDTO orderItem = new OrderItemDTO();
            BeanUtils.copyProperties(orderDetail, orderItem);

            //retrieve product name
            Product product = productService.getProductById(orderDetail.getProduct());
            orderItem.setProductName(product.getName());
            orderItem.setSlug(product.getSlug());
            orderItem.setProductImage(product.getImages().get(0));
            orderItems.add(orderItem);

            totalPrice += orderItem.getQuantity() * orderItem.getPrice();
        }
        orderResponse.setOrderDetail(orderItems);

        // check khuyến mãi
        long discountPrice = 0;
        if(order.getPromotion() != null){
            String coupon = order.getCoupon();

            Promotion promotion = promotionService.checkPromotion(coupon);

            if(promotion.getDiscountType() == 1) {
                discountPrice = totalPrice * promotion.getDiscountValue() / 100;
            }
            else {
                discountPrice = totalPrice - promotion.getDiscountValue();
            }

            if(discountPrice > promotion.getMaximumDiscountValue()){
                discountPrice = promotion.getMaximumDiscountValue();
            }
        }

        model.addAttribute("tempPrice", totalPrice);
        model.addAttribute("discountPrice", discountPrice);
        model.addAttribute("order", orderResponse);

        return "shop/order-detail";
    }

    @PostMapping("/api/cancel-order/{id}")
    public ResponseEntity<Object> cancelOrder(@PathVariable long id) {
        User user = ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUser();

        orderService.userCancelOrder(id, user.getId());

        return ResponseEntity.ok("Hủy đơn hàng thành công");
    }

    @GetMapping("/admin/orders/export-bill/{orderId}")
    public String exportPdfOrder(Model model, @PathVariable long orderId) {
        OrderDetailV2DTO orderResponse = new OrderDetailV2DTO();
        List<OrderItemDTO> orderItems = new ArrayList<>();

        Order order = orderService.findOrderById(orderId);
        BeanUtils.copyProperties(order, orderResponse);

        //get order detail
        List<OrderDetail> orderDetails = orderDetailRepository.findOrderDetailByOrderId(order.getId());
        for(OrderDetail orderDetail: orderDetails){
            OrderItemDTO orderItem = new OrderItemDTO();
            BeanUtils.copyProperties(orderDetail, orderItem);

            //retrieve product name
            Product product = productService.getProductById(orderDetail.getProduct());
            orderItem.setProductName(product.getName());
            orderItem.setSlug(product.getSlug());
            orderItems.add(orderItem);
        }

        // check khuyến mãi
        if(order.getPromotion() != null){
            Order.UsedPromotion usedPromotion = order.getPromotion();

            OrderItemDTO orderItem = new OrderItemDTO();
            orderItem.setProductName(usedPromotion.getCouponCode() + " (giảm giá áp dụng)");
            orderItem.setPrice(usedPromotion.getDiscountValue());
            orderItem.setQuantity(1);
            orderItems.add(orderItem);
        }

        orderResponse.setOrderDetail(orderItems);

        model.addAttribute("order", orderResponse);
        return "admin/order/bill";
    }
}
