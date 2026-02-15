<script setup lang="ts">
import { ref } from 'vue';
import { useBarcodeScanner } from './hooks/useBarcodeScanner';

const scannedBarcode = ref<string>('等待扫码...');
const scanHistory = ref<any[]>([]); // 改为存对象数组

useBarcodeScanner((barcode) => {
  scannedBarcode.value = barcode;

  // 🌟 向你的 Spring Boot 发送请求！
  fetch(`http://localhost:8080/api/products/${barcode}`)
      .then(response => response.json())
      .then(product => {
        console.log('后端返回的商品信息：', product);
        // 把后端查到的商品塞进列表里显示
        scanHistory.value.unshift(product);
      })
      .catch(error => {
        console.error('请求后端失败，后端启动了吗？', error);
      });
});
</script>

<template>
  <div style="padding: 50px; font-family: sans-serif; max-width: 600px; margin: 0 auto;">
    <h1>🛒 极简收银台 Demo</h1>
    <p style="color: gray;">请直接使用扫码枪。</p>

    <div style="margin-top: 30px; padding: 20px; background: #f0f8ff; border-radius: 8px;">
      <h2>当前扫码：<span style="color: #42b883;">{{ scannedBarcode }}</span></h2>
    </div>

    <div style="margin-top: 30px; text-align: left;">
      <h3>已扫商品列表：</h3>
      <ul>
        <li v-for="(item, index) in scanHistory" :key="index" style="padding: 10px 0; border-bottom: 1px solid #eee;">
          <span style="font-size: 1.2em; font-weight: bold;">{{ item.name }}</span>
          <span style="color: red; margin-left: 15px;">￥{{ item.price.toFixed(2) }}</span>
          <br><span style="color: gray; font-size: 0.8em;">条码: {{ item.barcode }}</span>
        </li>
      </ul>
    </div>
  </div>
</template>