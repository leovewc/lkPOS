package com.example.lkpos;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductMapper productMapper;

    // --- 原有的：处理扫码枪的 GET 请求 ---
    @GetMapping("/{barcode}")
    public Product getProductByBarcode(@PathVariable String barcode) {
        System.out.println("正在从数据库查询条码：" + barcode);
        Product product = productMapper.findByBarcode(barcode);
        if (product == null) {
            return new Product(barcode, "数据库未记录该商品", 0.0);
        }
        return product;
    }

    // --- 🌟 新增的：处理前端表单的 POST 请求 ---
    @PostMapping
    public String addProduct(@RequestBody Product product) {
        System.out.println("接收到新增商品请求：条码=" + product.barcode + ", 名称=" + product.name);

        try {
            // 调用 MyBatis 执行插入
            productMapper.insertProduct(product);
            return "success";
        } catch (Exception e) {
            // 如果条码重复（主键冲突），会抛出异常
            System.err.println("插入数据库失败：" + e.getMessage());
            return "error";
        }
    }

    // --- 🌟 新增的：处理前端获取所有商品的 GET 请求 ---
    // 注意这里没有写 {barcode}，所以请求路径就是基础的 /api/products
    @GetMapping
    public List<Product> getAllProducts() {
        System.out.println("正在从数据库拉取全量商品列表...");
        return productMapper.findAll();
    }


    // --- MyBatis 数据访问层 (Mapper) ---
    @Mapper
    public interface ProductMapper {

        @Select("SELECT * FROM products WHERE barcode = #{barcode}")
        Product findByBarcode(String barcode);

        // --- 🌟 新增的：将商品对象插入数据库的 SQL ---
        @Insert("INSERT INTO products (barcode, name, price) VALUES (#{barcode}, #{name}, #{price})")
        void insertProduct(Product product);

        // --- 🌟 新增的：查询全部商品，并按条码排序 ---
        @Select("SELECT * FROM products ORDER BY barcode DESC")
        List<Product> findAll();
    }

    // --- 实体类 ---
    public static class Product {
        public String barcode;
        public String name;
        public double price;

        public Product() {}

        public Product(String barcode, String name, double price) {
            this.barcode = barcode;
            this.name = name;
            this.price = price;
        }
    }
}

