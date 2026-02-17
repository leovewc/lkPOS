package com.example.lkpos;

import org.apache.ibatis.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductMapper productMapper;

    // 1. 前台扫码：任意一个口味的条码，都能顺藤摸瓜查出主商品信息
    @GetMapping("/{barcode}")
    public Product getProductByBarcode(@PathVariable String barcode) {
        Product product = productMapper.findByBarcode(barcode);
        if (product == null) {
            Product empty = new Product();
            empty.barcode = barcode;
            empty.name = "数据库未记录该商品";
            empty.price = 0.0;
            return empty;
        }
        // 🌟 极其关键：把当前扫中的真实物理条码塞回去，保证前台收银台购物车里的逻辑不崩溃！
        product.barcode = barcode;
        return product;
    }

    // 2. 后台录入：保存主商品，并批量保存多个关联条码
    @PostMapping
    @Transactional // 🌟 数据库事务：保证主表(商品)和子表(条码)要么一起成功，要么一起失败，防止脏数据！
    public String addProduct(@RequestBody Product product) {
        try {
            // 第一步：插入主表 (products) 并拿到数据库自动生成的内部流水 ID
            productMapper.insertProduct(product);

            // 第二步：遍历前端传来的条码数组，全部挂载到刚才生成的 product.id 下
            if (product.barcodes != null && !product.barcodes.isEmpty()) {
                for (String bc : product.barcodes) {
                    productMapper.insertBarcode(bc, product.id);
                }
            }
            return "success";
        } catch (Exception e) {
            System.err.println("入库失败：" + e.getMessage());
            return "error";
        }
    }

    // 3. 后台列表：查询所有商品，并自动带出它们的“一品多码”集合
    @GetMapping
    public List<Product> getAllProducts() {
        List<Product> products = productMapper.findAll();
        // 为了兼容目前的前端表格，我们把多个条码用逗号拼成一个字符串，塞给前端的单数 barcode 字段
        for (Product p : products) {
            if (p.barcodes != null && !p.barcodes.isEmpty()) {
                p.barcode = String.join(", ", p.barcodes);
            }
        }
        return products;
    }

    // 4. 删除商品：顺藤摸瓜级联删除
    @DeleteMapping("/{barcode}")
    public String deleteProduct(@PathVariable String barcode) {
        try {
            // 如果传过来的是 "6901, 6902" 这种逗号拼接的字符串，我们只取第一个条码去找主商品
            String firstBarcode = barcode.split(",")[0].trim();

            // 先查到它背后真正的主商品 ID
            Integer productId = productMapper.findProductIdByBarcode(firstBarcode);
            if (productId != null) {
                productMapper.deleteProduct(productId);
                // 🌟 得益于建表时的 ON DELETE CASCADE，主商品一删，子表里的几十个口味条码会自动瞬间消失！
            }
            return "success";
        } catch (Exception e) {
            return "error";
        }
    }

    // 5. 修改商品信息
    @PutMapping("/{barcode}")
    public String updateProduct(@PathVariable String barcode, @RequestBody Product product) {
        try {
            String firstBarcode = barcode.split(",")[0].trim();
            Integer productId = productMapper.findProductIdByBarcode(firstBarcode);
            if (productId != null) {
                product.id = productId;
                productMapper.updateProduct(product);
            }
            return "success";
        } catch (Exception e) {
            return "error";
        }
    }

    // --- MyBatis 数据访问层 ---
    @Mapper
    public interface ProductMapper {

        // 🌟 连表查询 (JOIN)：根据子表条码，查主表商品
        @Select("SELECT p.id, p.name, p.price FROM products p JOIN product_barcodes pb ON p.id = pb.product_id WHERE pb.barcode = #{barcode}")
        Product findByBarcode(String barcode);

        @Select("SELECT product_id FROM product_barcodes WHERE barcode = #{barcode} LIMIT 1")
        Integer findProductIdByBarcode(String barcode);

        // 插入主表，并配置 @Options 自动将 MySQL 生成的自增 ID 塞回到传入对象的 id 属性中
        @Insert("INSERT INTO products (name, price) VALUES (#{name}, #{price})")
        @Options(useGeneratedKeys = true, keyProperty = "id")
        void insertProduct(Product product);

        // 插入子表
        @Insert("INSERT INTO product_barcodes (barcode, product_id) VALUES (#{barcode}, #{productId})")
        void insertBarcode(@Param("barcode") String barcode, @Param("productId") Integer productId);

        // 🌟 一对多嵌套查询：查主表的同时，自动调用 findBarcodesByProductId 查子表，并将结果注入 barcodes 集合
        @Select("SELECT * FROM products ORDER BY id DESC")
        @Results({
                @Result(property = "id", column = "id"),
                @Result(property = "name", column = "name"),
                @Result(property = "price", column = "price"),
                @Result(property = "barcodes", column = "id", many = @Many(select = "findBarcodesByProductId"))
        })
        List<Product> findAll();

        @Select("SELECT barcode FROM product_barcodes WHERE product_id = #{productId}")
        List<String> findBarcodesByProductId(Integer productId);

        @Delete("DELETE FROM products WHERE id = #{id}")
        void deleteProduct(Integer id);

        @Update("UPDATE products SET name = #{name}, price = #{price} WHERE id = #{id}")
        void updateProduct(Product product);
    }

    // --- 实体类 ---
    public static class Product {
        public Integer id;
        public String name;
        public double price;

        // 🌟 商业版核心：一个商品对应多个物理条码的集合
        public List<String> barcodes;

        // 🌟 兼容字段：用于和旧版前端做无缝衔接
        public String barcode;

        public Product() {}
    }
}