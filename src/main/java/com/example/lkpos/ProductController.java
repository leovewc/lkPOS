package com.example.lkpos;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*")
public class ProductController {

    // 🌟 注入 MyBatis 的 Mapper 接口
    @Autowired
    private ProductMapper productMapper;

    @GetMapping("/{barcode}")
    public Product getProductByBarcode(@PathVariable String barcode) {
        System.out.println("正在从数据库查询条码：" + barcode);

        // 🌟 调用数据库查询
        Product product = productMapper.findByBarcode(barcode);

        // 如果数据库没查到，返回一个默认的“未知商品”对象，防止前端报错
        if (product == null) {
            return new Product(barcode, "数据库未记录该商品", 0.0);
        }

        return product;
    }

    // --- 🌟 MyBatis 数据访问层 (Mapper) ---
    @Mapper
    public interface ProductMapper {
        // 使用 SQL 语句直接查询数据库
        @Select("SELECT * FROM products WHERE barcode = #{barcode}")
        Product findByBarcode(String barcode);
    }

    // --- 🌟 实体类 (注意：属性名必须和数据库列名一致) ---
    public static class Product {
        public String barcode;
        public String name;
        public double price;

        // 无参构造函数（MyBatis 映射需要）
        public Product() {}

        public Product(String barcode, String name, double price) {
            this.barcode = barcode;
            this.name = name;
            this.price = price;
        }
    }
}