package com.example.lkpos;

import org.apache.ibatis.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderMapper orderMapper;

    // 🌟 接收前端传来的结账请求
    @PostMapping
    @Transactional // 🌟 事务注解：保证主表和子表要么一起成功，要么一起失败，防止账目错乱！
    public String createOrder(@RequestBody OrderRequest request) {
        System.out.println("收到结账请求，总金额：" + request.totalAmount);

        // 1. 组装主订单并插入数据库
        Order order = new Order();
        order.totalAmount = request.totalAmount;
        order.totalItems = request.totalItems;
        orderMapper.insertOrder(order);
        // 👆 运行完这行后，MyBatis 会自动把 MySQL 生成的自增 ID 塞回 order.id 里

        // 2. 遍历购物车里的每一项，绑定刚才生成的订单 ID，存入明细表
        for (OrderItem item : request.items) {
            item.orderId = order.id; // 关联主表
            orderMapper.insertOrderItem(item);
        }

        return "success";
    }

    @GetMapping
    public List<Order> getAllOrders() {
        System.out.println("正在拉取销售流水...");
        return orderMapper.findAllOrders();
    }

    // 🌟 1. 新增：根据订单ID获取具体卖了什么商品的接口
    @GetMapping("/{orderId}/items")
    public List<OrderItem> getOrderItems(@PathVariable Integer orderId) {
        System.out.println("正在查询订单明细，订单号：" + orderId);
        return orderMapper.findItemsByOrderId(orderId);
    }

    // --- MyBatis Mapper 层 ---
    @Mapper
    public interface OrderMapper {

        // 插入主表，并配置 useGeneratedKeys 自动获取数据库生成的自增主键
        @Insert("INSERT INTO orders (total_amount, total_items) VALUES (#{totalAmount}, #{totalItems})")
        @Options(useGeneratedKeys = true, keyProperty = "id")
        void insertOrder(Order order);

        // 插入明细表
        @Insert("INSERT INTO order_items (order_id, barcode, name, price, quantity) VALUES (#{orderId}, #{barcode}, #{name}, #{price}, #{quantity})")
        void insertOrderItem(OrderItem item);

        // 🌟 修改：使用 AS 取别名，让数据库字段和 Java 属性精准对齐
        @Select("SELECT id, " +
                "total_amount AS totalAmount, " +
                "total_items AS totalItems, " +
                "create_time AS createTime " +
                "FROM orders ORDER BY create_time DESC")
        List<Order> findAllOrders();

        // 🌟 2. 新增：连表/条件查询子表，注意用 AS 匹配驼峰命名
        @Select("SELECT id, " +
                "order_id AS orderId, " +
                "barcode, name, price, quantity " +
                "FROM order_items WHERE order_id = #{orderId}")
        List<OrderItem> findItemsByOrderId(Integer orderId);
    }

    // --- 内部实体类与 DTO ---

    // 专门用来接收前端 JSON 的对象
    public static class OrderRequest {
        public double totalAmount;
        public int totalItems;
        public List<OrderItem> items; // 对应前端的 cart 数组
    }

    public static class Order {
        public Integer id;
        public double totalAmount;
        public int totalItems;
        public String createTime; // 🌟 新增：用于接收数据库的结账时间
    }

    public static class OrderItem {
        public Integer id;
        public Integer orderId;
        public String barcode;
        public String name;
        public double price;
        public int quantity;
    }
}