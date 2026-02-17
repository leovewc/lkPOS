<script setup lang="ts">
import { ref, computed, nextTick } from 'vue';
import { useBarcodeScanner } from '@/hooks/useBarcodeScanner';
import { Button } from '@/components/ui/button';
import { useToast } from '@/components/ui/toast/use-toast';
import { Trash2, Plus, Minus } from 'lucide-vue-next';
// 🌟 导入 Shadcn 的表格系列组件
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';

interface CartItem {
  barcode: string;
  name: string;
  price: number;
  quantity: number;
  imageUrl?: string; //
}

const scannedBarcode = ref<string>('等待扫码...');
const cart = ref<CartItem[]>([]);

// === 手动输入相关状态 ===
const showManualInput = ref(false);
const manualBarcode = ref('');
const manualInputRef = ref<HTMLInputElement | null>(null); // 用来获取输入框的 DOM 以便自动聚焦

// === 🌟 核心逻辑：提取公共的查询商品方法 ===
const fetchProduct = (barcode: string) => {
  scannedBarcode.value = barcode;

  fetch(`http://localhost:8080/api/products/${barcode}`)
      .then(response => response.json())
      .then(product => {
        const existingItem = cart.value.find(item => item.barcode === product.barcode);
        if (existingItem) {
          existingItem.quantity += 1;
        } else {
          cart.value.unshift({ ...product, quantity: 1 });
        }
      })
      .catch(error => console.error('请求后端失败', error));
};

// === 🌟 监听扫码枪和人类的回车 ===
useBarcodeScanner(
    // 1. 扫码枪扫码成功触发
    (barcode) => fetchProduct(barcode),

    // 2. 人类敲击回车触发
    async () => {
      // 如果弹窗没开，就打开它
      if (!showManualInput.value) {
        showManualInput.value = true;
        // 必须等 Vue 把弹窗渲染出来后，再去让输入框获取焦点
        await nextTick();
        if (manualInputRef.value) {
          manualInputRef.value.focus();
        }
      }
    }
);

// 提交手动输入的条码
const submitManualBarcode = () => {
  if (manualBarcode.value.trim()) {
    fetchProduct(manualBarcode.value.trim());
  }
  closeManualInput();
};

// 关闭手动输入弹窗
const closeManualInput = () => {
  showManualInput.value = false;
  manualBarcode.value = '';
};

// 🌟 2. 初始化 toast 实例
const { toast } = useToast();

// 🌟 增加数量
const increaseQuantity = (item: CartItem) => {
  item.quantity++;
};

// 🌟 减少数量 (最小为 1)
const decreaseQuantity = (item: CartItem) => {
  if (item.quantity > 1) {
    item.quantity--;
  }
};

// 🌟 删除单个商品
const removeItem = (barcode: string) => {
  cart.value = cart.value.filter(item => item.barcode !== barcode);
  toast({
    description: "商品已从购物车移除",
  });
};

// 🌟 一键清空购物车
const clearCart = () => {
  if (cart.value.length === 0) return;

  if (confirm("确定要清空当前购物车吗？")) {
    cart.value = [];
    scannedBarcode.value = '等待扫码...';
    toast({
      description: "购物车已清空",
    });
  }
};

// === 结算逻辑 ===
const totalAmount = computed(() => cart.value.reduce((total, item) => total + (item.price * item.quantity), 0));
const totalItems = computed(() => cart.value.reduce((sum, item) => sum + item.quantity, 0));

const handleCheckout = async () => {
  if (cart.value.length === 0) {
    // 🌟 3. 替换失败弹窗（红色警告框）
    toast({
      title: "操作无效",
      description: "购物车是空的，无法进行结账！",
      variant: "destructive", // 触发危险/错误的红色样式
    });
    return;
  }

  const orderData = {
    totalAmount: totalAmount.value,
    totalItems: totalItems.value,
    items: cart.value
  };

  try {
    const response = await fetch('http://localhost:8080/api/orders', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(orderData)
    });

    if (response.ok) {
      // 🌟 4. 替换成功弹窗（优雅的白底弹窗）
      toast({
        title: "✅ 结账成功",
        description: `共卖出 ${totalItems.value} 件商品，收款 ￥${totalAmount.value.toFixed(2)}。数据已落库。`,
      });

      // 清空收银台
      cart.value = [];
      scannedBarcode.value = '等待扫码...';
    } else {
      toast({
        title: "结账失败",
        description: "后端接口返回异常，请检查控制台日志。",
        variant: "destructive",
      });
    }
  } catch (error) {
    console.error("提交订单失败", error);
    toast({
      title: "网络错误",
      description: "无法连接到后端服务器！",
      variant: "destructive",
    });
  }
};
</script>
<template>
  <div class="p-6 max-w-7xl mx-auto flex flex-col lg:flex-row gap-6 items-start mt-4">

    <div class="flex-1 w-full">
      <div class="p-4 bg-white rounded-xl mb-4 shadow-sm border border-slate-200">
        <h2 class="text-lg font-semibold text-slate-600">
          最近操作：<span class="text-blue-600 ml-2 font-mono">{{ scannedBarcode }}</span>
        </h2>
      </div>

      <div class="bg-white p-6 rounded-xl shadow-sm border border-slate-200 min-h-[600px]">
        <h3 class="text-xl font-bold mb-6 border-b border-slate-100 pb-3 flex justify-between items-end">
          <span>购物明细</span>
          <span class="text-sm text-slate-400 font-normal">敲击 Enter 键可手动输入条码</span>
        </h3>

        <Table>
          <TableHeader class="bg-slate-50/50">
            <TableRow>
              <TableHead class="w-16 text-center">图</TableHead> <TableHead class="w-[35%]">商品信息</TableHead>
              <TableHead class="w-[20%] text-center">数量</TableHead>
              <TableHead class="w-[15%]">单价</TableHead>
              <TableHead class="w-[15%]">小计</TableHead>
              <TableHead class="w-[10%] text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-if="cart.length === 0">
              <TableCell colspan="6" class="h-48 text-center text-slate-400">
                <div class="flex flex-col items-center justify-center space-y-2">
                  <span class="text-4xl">🛒</span>
                  <p>购物车空空如也，等待扫码中...</p>
                </div>
              </TableCell>
            </TableRow>

            <TableRow v-for="item in cart" :key="item.barcode" class="group hover:bg-slate-50/80 transition-colors">

              <TableCell>
                <div v-if="item.imageUrl" class="h-12 w-12 rounded-md border border-slate-200 bg-white overflow-hidden flex items-center justify-center shadow-sm hover:scale-110 transition-transform cursor-pointer">
                  <img :src="'http://localhost:8080' + item.imageUrl" class="object-contain h-full w-full" />
                </div>
                <div v-else class="h-12 w-12 rounded-md border border-slate-100 bg-slate-50 flex items-center justify-center text-[10px] text-slate-400 shadow-sm">
                  无图
                </div>
              </TableCell>

              <TableCell>
                <div class="font-bold text-slate-800 text-base">{{ item.name }}</div>
                <div class="text-xs text-slate-400 font-mono mt-1">{{ item.barcode }}</div>
              </TableCell>

              <TableCell>
                <div class="flex items-center justify-center space-x-3">
                  <Button variant="outline" size="icon" class="h-8 w-8 rounded-full border-slate-200" @click="decreaseQuantity(item)">
                    <Minus class="h-3 w-3" />
                  </Button>
                  <span class="w-8 text-center font-bold text-lg text-slate-700">{{ item.quantity }}</span>
                  <Button variant="outline" size="icon" class="h-8 w-8 rounded-full border-slate-200" @click="increaseQuantity(item)">
                    <Plus class="h-3 w-3" />
                  </Button>
                </div>
              </TableCell>

              <TableCell class="text-slate-500 font-medium">￥{{ item.price.toFixed(2) }}</TableCell>
              <TableCell class="text-emerald-600 font-bold text-lg">￥{{ (item.price * item.quantity).toFixed(2) }}</TableCell>

              <TableCell class="text-right">
                <Button variant="ghost" size="icon" class="text-slate-300 hover:text-red-500 hover:bg-red-50 transition-colors" @click="removeItem(item.barcode)">
                  <Trash2 class="h-5 w-5" />
                </Button>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>
    </div>

    <div class="w-full lg:w-96 bg-slate-900 text-white p-8 rounded-2xl shadow-2xl flex flex-col justify-between sticky top-8">
      <div>
        <h3 class="text-xl font-bold mb-8 text-slate-300 tracking-widest border-l-4 border-blue-500 pl-4">结算中心</h3>

        <div class="space-y-4 text-lg">
          <div class="flex justify-between text-slate-400">
            <span>商品总数</span>
            <span class="font-bold text-slate-200">{{ totalItems }} 件</span>
          </div>
          <div class="flex justify-between text-slate-400">
            <span>操作员</span>
            <span class="font-bold text-slate-200">Admin</span>
          </div>
        </div>
      </div>

      <div class="mt-12 border-t border-slate-700 pt-8">
        <div class="text-slate-400 mb-2 text-sm uppercase tracking-widest font-medium">应收总额</div>
        <div class="text-6xl font-black text-emerald-400 mb-10 break-all leading-tight">
          <span class="text-3xl mr-1">￥</span>{{ totalAmount.toFixed(2) }}
        </div>

        <div class="space-y-4">
          <Button
              variant="outline"
              class="w-full h-12 border-slate-700 text-slate-400 hover:bg-red-500/10 hover:text-red-400 hover:border-red-900/50 transition-all"
              @click="clearCart"
          >
            <Trash2 class="mr-2 h-4 w-4" />
            重置当前交易
          </Button>

          <Button
              class="w-full h-20 text-2xl font-black bg-blue-600 hover:bg-blue-700 shadow-[0_0_30px_rgba(37,99,235,0.3)] transition-all active:scale-[0.98]"
              @click="handleCheckout"
          >
            确认收款
          </Button>
        </div>
      </div>
    </div>

    <div v-if="showManualInput"
         class="fixed inset-0 bg-black/70 backdrop-blur-md flex items-center justify-center z-50 p-4"
         @click.self="closeManualInput">

      <div class="bg-white p-8 rounded-3xl shadow-2xl w-full max-w-md transform animate-in fade-in zoom-in duration-300">
        <div class="flex items-center space-x-3 mb-6">
          <div class="bg-blue-100 p-2 rounded-lg">⌨️</div>
          <h3 class="text-2xl font-bold text-slate-800">手动录入商品</h3>
        </div>

        <p class="text-slate-500 mb-6 text-sm leading-relaxed">请输入商品条形码并按回车。系统将自动检索数据库中的信息并添加至购物车。</p>

        <input
            ref="manualInputRef"
            v-model="manualBarcode"
            type="text"
            class="w-full text-3xl font-mono p-5 border-2 border-slate-200 rounded-2xl focus:border-blue-500 focus:ring-8 focus:ring-blue-50 outline-none transition-all mb-6 text-center tracking-widest"
            placeholder="690..."
            @keydown.enter="submitManualBarcode"
            @keydown.esc="closeManualInput"
        />

        <div class="flex gap-4">
          <Button variant="ghost" class="flex-1 h-14 text-slate-500 font-bold" @click="closeManualInput">取消 (Esc)</Button>
          <Button class="flex-1 h-14 bg-blue-600 hover:bg-blue-700 text-lg font-bold shadow-lg" @click="submitManualBarcode">确认录入</Button>
        </div>
      </div>
    </div>

  </div>
</template>