<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
// 🌟 引入我们刚刚下载的表格组件
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

interface Product {
  barcode: string;
  name: string;
  price: number;
}

const productForm = ref({ barcode: '', name: '', price: 0 });
const isSubmitting = ref(false);
const productList = ref<Product[]>([]); // 🌟 存放从数据库拉取的所有商品

// 🌟 拉取商品列表的方法
const fetchAllProducts = async () => {
  try {
    const response = await fetch('http://localhost:8080/api/products');
    if (response.ok) {
      productList.value = await response.json();
    }
  } catch (error) {
    console.error("拉取商品列表失败", error);
  }
};

// 页面加载时自动拉取数据
onMounted(() => {
  fetchAllProducts();
});

const handleSubmit = async () => {
  if (!productForm.value.barcode || !productForm.value.name) return alert("信息不能为空！");
  isSubmitting.value = true;

  try {
    const response = await fetch('http://localhost:8080/api/products', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(productForm.value)
    });

    if (response.ok) {
      productForm.value = { barcode: '', name: '', price: 0 };
      // 🌟 录入成功后，立刻重新拉取最新列表，实现无刷新更新！
      fetchAllProducts();
    } else {
      alert("录入失败，条码可能已存在。");
    }
  } catch (error) {
    console.error("网络异常", error);
  } finally {
    isSubmitting.value = false;
  }
};
</script>

<template>
  <div class="min-h-screen bg-slate-50 p-8">
    <div class="max-w-6xl mx-auto flex flex-col lg:flex-row gap-8 items-start">

      <Card class="w-full lg:w-1/3 shadow-sm border-slate-200">
        <CardHeader>
          <CardTitle class="text-xl font-bold">录入新商品</CardTitle>
          <CardDescription>信息将实时同步至数据库。</CardDescription>
        </CardHeader>
        <CardContent class="space-y-4">
          <div class="space-y-2">
            <label class="text-sm font-medium">商品条形码 (Barcode)</label>
            <Input v-model="productForm.barcode" placeholder="例如: 6921..." />
          </div>
          <div class="space-y-2">
            <label class="text-sm font-medium">商品名称 (Name)</label>
            <Input v-model="productForm.name" placeholder="例如: 原味薯片" />
          </div>
          <div class="space-y-2">
            <label class="text-sm font-medium">零售价 (Price)</label>
            <Input v-model="productForm.price" type="number" step="0.01" />
          </div>
        </CardContent>
        <CardFooter>
          <Button class="w-full bg-slate-900 hover:bg-slate-800" @click="handleSubmit" :disabled="isSubmitting">
            {{ isSubmitting ? '保存中...' : '保存商品' }}
          </Button>
        </CardFooter>
      </Card>

      <Card class="w-full lg:w-2/3 shadow-sm border-slate-200 overflow-hidden">
        <CardHeader class="bg-white border-b pb-4">
          <CardTitle class="text-xl font-bold text-slate-800">商品库存总览</CardTitle>
          <CardDescription>共计 {{ productList.length }} 种商品</CardDescription>
        </CardHeader>

        <CardContent class="p-0">
          <Table>
            <TableHeader class="bg-slate-50">
              <TableRow>
                <TableHead class="font-bold text-slate-700 w-[180px]">条形码</TableHead>
                <TableHead class="font-bold text-slate-700">商品名称</TableHead>
                <TableHead class="font-bold text-slate-700 text-right">单价</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-if="productList.length === 0">
                <TableCell colspan="3" class="h-24 text-center text-slate-500">
                  数据库空空如也，快去左边录入几个商品吧！
                </TableCell>
              </TableRow>

              <TableRow v-for="item in productList" :key="item.barcode" class="hover:bg-slate-50/50">
                <TableCell class="font-mono text-slate-500">{{ item.barcode }}</TableCell>
                <TableCell class="font-medium text-slate-800">{{ item.name }}</TableCell>
                <TableCell class="text-right text-emerald-600 font-bold">
                  ￥{{ item.price.toFixed(2) }}
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </CardContent>
      </Card>

    </div>
  </div>
</template>