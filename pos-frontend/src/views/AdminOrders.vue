<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
// 🌟 引入刚下载的弹窗组件
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog';

// --- 类型定义 ---
interface Order {
  id: number;
  totalAmount: number;
  totalItems: number;
  createTime: string;
}

interface OrderItem {
  barcode: string;
  name: string;
  price: number;
  quantity: number;
}

// --- 状态数据 ---
const orderList = ref<Order[]>([]);
const isDialogOpen = ref(false);
const currentOrderItems = ref<OrderItem[]>([]);
const currentOrderId = ref<number | null>(null);

// --- 拉取全部订单 ---
const fetchOrders = async () => {
  try {
    const response = await fetch('http://localhost:8080/api/orders');
    if (response.ok) {
      orderList.value = await response.json();
    }
  } catch (error) {
    console.error("拉取订单失败", error);
  }
};

onMounted(() => fetchOrders());

// --- 🌟 核心：计算大屏数据 ---
const totalRevenue = computed(() => orderList.value.reduce((sum, order) => sum + order.totalAmount, 0));

const todayRevenue = computed(() => {
  const today = new Date().toDateString(); // 获取今天日期的字符串
  return orderList.value
      .filter(order => new Date(order.createTime).toDateString() === today)
      .reduce((sum, order) => sum + order.totalAmount, 0);
});

// --- 🌟 核心：点击某行查看明细 ---
const openOrderDetails = async (orderId: number) => {
  currentOrderId.value = orderId;
  isDialogOpen.value = true; // 打开弹窗
  currentOrderItems.value = []; // 清空上次的数据

  try {
    const response = await fetch(`http://localhost:8080/api/orders/${orderId}/items`);
    if (response.ok) {
      currentOrderItems.value = await response.json();
    }
  } catch (error) {
    console.error("拉取明细失败", error);
  }
};

// 格式化时间
const formatDate = (dateString: string) => {
  if (!dateString) return '未知时间';
  return new Date(dateString).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  });
};
</script>

<template>
  <div class="p-8 max-w-6xl mx-auto space-y-8">

    <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
      <Card class="bg-gradient-to-br from-blue-500 to-blue-600 text-white shadow-md border-0">
        <CardHeader class="pb-2"><CardTitle class="text-blue-100 text-sm font-medium">今日总收入</CardTitle></CardHeader>
        <CardContent><div class="text-4xl font-black">￥{{ todayRevenue.toFixed(2) }}</div></CardContent>
      </Card>

      <Card class="bg-gradient-to-br from-emerald-500 to-emerald-600 text-white shadow-md border-0">
        <CardHeader class="pb-2"><CardTitle class="text-emerald-100 text-sm font-medium">历史总收入</CardTitle></CardHeader>
        <CardContent><div class="text-4xl font-black">￥{{ totalRevenue.toFixed(2) }}</div></CardContent>
      </Card>

      <Card class="bg-gradient-to-br from-slate-700 to-slate-800 text-white shadow-md border-0">
        <CardHeader class="pb-2"><CardTitle class="text-slate-300 text-sm font-medium">累计成单数</CardTitle></CardHeader>
        <CardContent><div class="text-4xl font-black">{{ orderList.length }} <span class="text-xl font-normal text-slate-400">单</span></div></CardContent>
      </Card>
    </div>

    <Card class="shadow-sm border-slate-200">
      <CardHeader class="bg-white border-b pb-4">
        <CardTitle class="text-xl font-bold text-slate-800">流水明细</CardTitle>
      </CardHeader>
      <CardContent class="p-0">
        <Table>
          <TableHeader class="bg-slate-50">
            <TableRow>
              <TableHead class="font-bold">订单号</TableHead>
              <TableHead class="font-bold">交易时间</TableHead>
              <TableHead class="font-bold text-center">总件数</TableHead>
              <TableHead class="font-bold text-right">交易总额</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-if="orderList.length === 0">
              <TableCell colspan="4" class="h-32 text-center text-slate-500">暂无交易记录</TableCell>
            </TableRow>

            <TableRow
                v-for="order in orderList"
                :key="order.id"
                class="hover:bg-blue-50 transition-colors cursor-pointer group"
                @click="openOrderDetails(order.id)"
            >
              <TableCell class="font-mono font-medium text-blue-600 group-hover:underline"># {{ order.id }}</TableCell>
              <TableCell class="text-slate-500">{{ formatDate(order.createTime) }}</TableCell>
              <TableCell class="text-center text-slate-700">{{ order.totalItems }}</TableCell>
              <TableCell class="text-right text-emerald-600 font-bold text-lg">￥{{ order.totalAmount.toFixed(2) }}</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>

    <Dialog v-model:open="isDialogOpen">
      <DialogContent class="sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle>订单明细 <span class="text-slate-400 font-mono text-sm ml-2">#{{ currentOrderId }}</span></DialogTitle>
          <DialogDescription>
            该笔订单购买的商品清单。
          </DialogDescription>
        </DialogHeader>

        <div class="mt-4 border rounded-md">
          <Table>
            <TableHeader class="bg-slate-50">
              <TableRow>
                <TableHead>商品名称</TableHead>
                <TableHead class="text-center">数量</TableHead>
                <TableHead class="text-right">单价</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-if="currentOrderItems.length === 0">
                <TableCell colspan="3" class="text-center text-slate-400 py-4">加载中...</TableCell>
              </TableRow>
              <TableRow v-for="(item, idx) in currentOrderItems" :key="idx">
                <TableCell class="font-medium">{{ item.name }}</TableCell>
                <TableCell class="text-center">x {{ item.quantity }}</TableCell>
                <TableCell class="text-right">￥{{ item.price.toFixed(2) }}</TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </div>
      </DialogContent>
    </Dialog>

  </div>
</template>