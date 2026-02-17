package com.example.lkpos;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
@CrossOrigin // 允许前端跨域访问
public class StatsController {

    @Autowired
    private StatsMapper statsMapper;

    @GetMapping("/dashboard")
    public Map<String, Object> getDashboardData() {
        Map<String, Object> result = new HashMap<>();

        // 1. 获取今日核心财务数据
        Map<String, Object> todayStats = statsMapper.getTodayStats();
        if (todayStats == null || todayStats.get("revenue") == null) {
            todayStats = new HashMap<>();
            todayStats.put("revenue", 0.0);
            todayStats.put("cost", 0.0);
            todayStats.put("profit", 0.0);
            todayStats.put("orderCount", 0);
        }
        result.put("today", todayStats);

        // 2. 获取热销商品 TOP 5 榜单
        List<Map<String, Object>> topProducts = statsMapper.getTopProducts();
        result.put("topProducts", topProducts);

        return result;
    }

    @Mapper
    public interface StatsMapper {
        // 🌟 史诗级 SQL：一句话算出今日单量、营业额、总成本、净毛利！
        @Select("SELECT " +
                "COUNT(DISTINCT o.id) as orderCount, " +
                "IFNULL(SUM(oi.quantity * p.price), 0) as revenue, " +
                "IFNULL(SUM(oi.quantity * p.cost_price), 0) as cost, " +
                "IFNULL(SUM(oi.quantity * (p.price - p.cost_price)), 0) as profit " +
                "FROM orders o " +
                "JOIN order_items oi ON o.id = oi.order_id " +
                "JOIN product_barcodes pb ON oi.barcode = pb.barcode " +
                "JOIN products p ON pb.product_id = p.id " +
                "WHERE DATE(o.create_time) = CURDATE()")
        Map<String, Object> getTodayStats();

        // 🌟 算排行榜 SQL：按销量算出卖得最好的 5 件商品
        @Select("SELECT p.name, p.image_url as imageUrl, " +
                "SUM(oi.quantity) as totalSold, " +
                "SUM(oi.quantity * (p.price - p.cost_price)) as totalProfit " +
                "FROM order_items oi " +
                "JOIN product_barcodes pb ON oi.barcode = pb.barcode " +
                "JOIN products p ON pb.product_id = p.id " +
                "GROUP BY p.id, p.name, p.image_url " +
                "ORDER BY totalSold DESC LIMIT 5")
        List<Map<String, Object>> getTopProducts();
    }
}