package com.example.lkpos;

import org.apache.ibatis.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.net.URL;


import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

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

    // --- 🌟 新增：获取单个商品的历史销量和今日销量 ---
    @GetMapping("/{id}/stats")
    public Map<String, Object> getProductStats(@PathVariable Integer id) {
        System.out.println("正在查询商品ID: " + id + " 的销售数据...");
        Map<String, Object> stats = new HashMap<>();

        // 分别查询总销量和今日销量
        Integer totalSales = productMapper.getTotalSalesByProductId(id);
        Integer todaySales = productMapper.getTodaySalesByProductId(id);

        // 防空指针处理：如果没有卖出过，数据库的 SUM() 会返回 null
        stats.put("totalSales", totalSales == null ? 0 : totalSales);
        stats.put("todaySales", todaySales == null ? 0 : todaySales);

        return stats;
    }


    // --- 🌟 1. 新增：调用第三方 API 并下载图片本地化 ---
    @GetMapping("/fetch-external")
    public Product fetchExternalProduct(@RequestParam String barcode) {
        System.out.println("正在从 ShowAPI 云端拉取条码信息：" + barcode);
        Product result = new Product();
        result.barcode = barcode;

        try {
            // 1. 真实的 ShowAPI 接口地址
            String apiUrl = "https://route.showapi.com/66-22";

            // 2. 设置请求头为表单提交 (对应 curl 中的 -H 'content-type: application/x-www-form-urlencoded')
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            // 3. 封装表单参数 (对应 curl 中的 -d 'code=...&appKey=...')
            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("appKey", "86C53ef713e848F8Ae0a8264f7E6D095"); // ⚠️ 务必填入你的 appKey
            map.add("code", barcode);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
            RestTemplate restTemplate = new RestTemplate();

            // 4. 发送 POST 请求并接收 String 格式的原始响应
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, request, String.class);
            String rawData = response.getBody();

            System.out.println("====== ShowAPI 原始返回数据 ======");
            System.out.println(rawData);
            System.out.println("=================================");

            // 5. 手动解析 JSON
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> body = mapper.readValue(rawData, Map.class);

            // 6. 按照你截图里的格式精准解析
            if (body != null && "0".equals(String.valueOf(body.get("showapi_res_code")))) {
                Map<String, Object> data = (Map<String, Object>) body.get("showapi_res_body");

                if (data != null && data.get("goodsName") != null) {
                    // 解析名称
                    result.name = String.valueOf(data.get("goodsName"));

                    // 解析价格（防空字符串报错）
                    String priceStr = String.valueOf(data.get("price"));
                    if (priceStr != null && !priceStr.trim().isEmpty() && !"null".equals(priceStr)) {
                        try { result.price = Double.parseDouble(priceStr); }
                        catch (NumberFormatException e) { result.price = 0.0; }
                    } else { result.price = 0.0; }

                    // 解析图片并下载到本地
                    String netImageUrl = (String) data.get("img");
                    if (netImageUrl != null && !netImageUrl.trim().isEmpty()) {
                        String fileName = UUID.randomUUID().toString() + ".jpg";
                        Path localPath = Paths.get(System.getProperty("user.dir") + "/uploads/" + fileName);
                        try (InputStream in = new URL(netImageUrl).openStream()) {
                            Files.copy(in, localPath, StandardCopyOption.REPLACE_EXISTING);
                            result.imageUrl = "/uploads/" + fileName;
                            System.out.println("✅ 图片下载成功，本地路径：" + result.imageUrl);
                        } catch (Exception e) { System.err.println("❌ 图片下载失败: " + e.getMessage()); }
                    }

                    // 🌟 新增：精准抓取高价值商业数据！
                    result.brand = data.get("trademark") != null ? data.get("trademark").toString() : "未知品牌";
                    result.specification = data.get("spec") != null ? data.get("spec").toString() : "无规格";
                    result.manufacturer = data.get("manuName") != null ? data.get("manuName").toString() : "未知厂家";
                    result.category = data.get("gpcType") != null ? data.get("gpcType").toString() : "未分类";
                    result.note = data.get("note") != null ? data.get("note").toString() : "暂无详细说明";

                }
            } else {
                System.out.println("⚠️ API 返回错误：" + body.get("showapi_res_error"));
            }
        } catch (Exception e) {
            System.err.println("❌ 调用 ShowAPI 发生异常: " + e.getMessage());
        }
        return result;
    }

    // --- 🌟 2. 实体类补充 ---
    public static class Product {
        public Integer id;
        public String name;
        public double price;
        public List<String> barcodes;
        public String barcode;
        public String imageUrl;
        public double costPrice;

        // 🌟 新增的五个高价值商业字段
        public String brand;
        public String specification;
        public String manufacturer;
        public String category;
        public String note;

        public Product() {}
    }


    // --- MyBatis 数据访问层 ---
    @Mapper
    public interface ProductMapper {

        // 1. 根据条码查询商品信息 (包含图片)
        @Select("SELECT p.id, p.name, p.price, p.image_url as imageUrl FROM products p JOIN product_barcodes pb ON p.id = pb.product_id WHERE pb.barcode = #{barcode}")
        Product findByBarcode(String barcode);

        @Select("SELECT product_id FROM product_barcodes WHERE barcode = #{barcode} LIMIT 1")
        Integer findProductIdByBarcode(String barcode);

        // 2. 插入商品主表 (包含图片)
        // 🌟 升级：插入商品时带上新字段
        @Insert("INSERT INTO products (name, price, cost_price, image_url, brand, specification, manufacturer, category, note) " +
                "VALUES (#{name}, #{price}, #{costPrice}, #{imageUrl}, #{brand}, #{specification}, #{manufacturer}, #{category}, #{note})")
        @Options(useGeneratedKeys = true, keyProperty = "id")
        void insertProduct(Product product);

        // 3. 插入条码子表
        @Insert("INSERT INTO product_barcodes (barcode, product_id) VALUES (#{barcode}, #{productId})")
        void insertBarcode(@Param("barcode") String barcode, @Param("productId") Integer productId);

        // 4. 查询商品列表 (映射图片和条码集合)
        // 🌟 升级：查询列表时把新字段一起拉出来
        @Select("SELECT id, name, price, cost_price as costPrice, image_url as imageUrl, brand, specification, manufacturer, category, note FROM products ORDER BY id DESC")
        @Results({
                @Result(property = "id", column = "id"),
                @Result(property = "name", column = "name"),
                @Result(property = "price", column = "price"),
                @Result(property = "imageUrl", column = "imageUrl"),
                @Result(property = "brand", column = "brand"),
                @Result(property = "costPrice", column = "costPrice"),
                @Result(property = "specification", column = "specification"),
                @Result(property = "manufacturer", column = "manufacturer"),
                @Result(property = "category", column = "category"),
                @Result(property = "note", column = "note"),
                @Result(property = "barcodes", column = "id", many = @Many(select = "findBarcodesByProductId"))
        })
        List<Product> findAll();

        @Select("SELECT barcode FROM product_barcodes WHERE product_id = #{productId}")
        List<String> findBarcodesByProductId(Integer productId);

        @Delete("DELETE FROM products WHERE id = #{id}")
        void deleteProduct(Integer id);

        @Update("UPDATE products SET name = #{name}, price = #{price} WHERE id = #{id}, cost_price = #{costPrice} WHERE id = #{id}")
        void updateProduct(Product product);

        // 5. 销量统计报表
        @Select("SELECT SUM(oi.quantity) FROM order_items oi JOIN product_barcodes pb ON oi.barcode = pb.barcode WHERE pb.product_id = #{productId}")
        Integer getTotalSalesByProductId(Integer productId);

        @Select("SELECT SUM(oi.quantity) FROM order_items oi JOIN product_barcodes pb ON oi.barcode = pb.barcode JOIN orders o ON oi.order_id = o.id WHERE pb.product_id = #{productId} AND DATE(o.create_time) = CURDATE()")
        Integer getTodaySalesByProductId(Integer productId);

    }
}