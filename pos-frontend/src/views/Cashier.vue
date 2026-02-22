<script setup lang="ts">
import { ref, computed, nextTick, onUnmounted } from 'vue';
import { useBarcodeScanner } from '@/hooks/useBarcodeScanner';
import { Button } from '@/components/ui/button';
import { useToast } from '@/components/ui/toast/use-toast';
import { Trash2, Plus, Minus, Camera } from 'lucide-vue-next';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

interface CartItem {
  barcode: string;
  name: string;
  price: number;
  quantity: number;
  imageUrl?: string;
}

const scannedBarcode = ref<string>('等待扫码...');
const cart = ref<CartItem[]>([]);

const showManualInput = ref(false);
const manualBarcode = ref('');
const manualInputRef = ref<HTMLInputElement | null>(null);

// === 🌟 AI 视觉识别相关状态 ===
const showScannerModal = ref(false);
const videoRef = ref<HTMLVideoElement | null>(null);
const canvasRef = ref<HTMLCanvasElement | null>(null);
const isRecognizing = ref(false);
let stream: MediaStream | null = null;

// === 核心逻辑：提取公共的查询商品方法 ===
const fetchProduct = (barcode: string) => {
  scannedBarcode.value = barcode;

  fetch(`http://localhost:8080/api/products/${barcode}`)
      .then(response => {
        if (!response.ok) throw new Error('Product not found');
        return response.json();
      })
      .then(product => {
        const existingItem = cart.value.find(item => item.barcode === product.barcode);
        if (existingItem) {
          existingItem.quantity += 1;
        } else {
          cart.value.unshift({ ...product, quantity: 1 });
        }
      })
      .catch(error => {
        console.error('请求后端失败', error);
        toast({ title: "查无此商品", description: "业务库中未找到该商品信息", variant: "destructive" });
      });
};

useBarcodeScanner(
    (barcode) => fetchProduct(barcode),
    async () => {
      if (!showManualInput.value && !showScannerModal.value) {
        showManualInput.value = true;
        await nextTick();
        if (manualInputRef.value) manualInputRef.value.focus();
      }
    }
);

const submitManualBarcode = () => {
  if (manualBarcode.value.trim()) fetchProduct(manualBarcode.value.trim());
  closeManualInput();
};

const closeManualInput = () => {
  showManualInput.value = false;
  manualBarcode.value = '';
};

// === 🌟 AI 视觉识别核心方法 ===
const openScannerModal = async () => {
  showScannerModal.value = true;
  await nextTick();
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: "environment", width: { ideal: 640 }, height: { ideal: 480 } }
    });
    if (videoRef.value) videoRef.value.srcObject = stream;
  } catch (err: any) {
    toast({ title: "摄像头打开失败", description: "请确保使用HTTPS访问或检查浏览器权限", variant: "destructive" });
    showScannerModal.value = false;
  }
};

const closeScannerModal = () => {
  if (stream) {
    stream.getTracks().forEach(track => track.stop());
    stream = null;
  }
  showScannerModal.value = false;
};

onUnmounted(() => {
  closeScannerModal(); // 组件销毁时确保关闭摄像头
});

// 拍照并提交给本地 Python AI 分析
const takeSnapshotAndRecognize = () => {
  if (!videoRef.value || !canvasRef.value) return;
  isRecognizing.value = true;

  const video = videoRef.value;
  const canvas = canvasRef.value;
  canvas.width = video.videoWidth;
  canvas.height = video.videoHeight;
  const ctx = canvas.getContext('2d');
  if (!ctx) return;

  ctx.drawImage(video, 0, 0, canvas.width, canvas.height);

  canvas.toBlob(async (blob) => {
    if (!blob) return;
    const formData = new FormData();
    formData.append('file', blob, 'snapshot.jpg');

    try {
      // ⚠️ 这里已经为你改成了本地 Python 服务的地址
      const API_URL = 'http://127.0.0.1:8000/api/recognize';

      const response = await fetch(API_URL, {
        method: 'POST',
        body: formData
      });
      const data = await response.json();

      if (data.code === 200) {
        toast({ title: "📸 识别成功", description: `置信度: ${(data.data.score * 100).toFixed(1)}% | 正在加入购物车` });
        fetchProduct(data.data.barcode);
        closeScannerModal();
      } else {
        toast({ title: "商品未收录", description: "AI 觉得眼生，请确保已在后台给它拍过照", variant: "destructive" });
      }
    } catch (error) {
      toast({ title: "AI 服务离线", description: "无法连接到 8000 端口，请检查本地 Python 引擎", variant: "destructive" });
    } finally {
      isRecognizing.value = false;
    }
  }, 'image/jpeg', 0.8);
};

// === 购物车基础操作 ===
const { toast } = useToast();
const increaseQuantity = (item: CartItem) => item.quantity++;
const decreaseQuantity = (item: CartItem) => { if (item.quantity > 1) item.quantity--; };
const removeItem = (barcode: string) => { cart.value = cart.value.filter(item => item.barcode !== barcode); };
const clearCart = () => {
  if (cart.value.length === 0) return;
  if (confirm("确定要清空当前购物车吗？")) {
    cart.value = [];
    scannedBarcode.value = '等待扫码...';
  }
};

const totalAmount = computed(() => cart.value.reduce((total, item) => total + (item.price * item.quantity), 0));
const totalItems = computed(() => cart.value.reduce((sum, item) => sum + item.quantity, 0));

const handleCheckout = async () => {
  if (cart.value.length === 0) {
    toast({ title: "操作无效", description: "购物车是空的！", variant: "destructive" });
    return;
  }
  const orderData = { totalAmount: totalAmount.value, totalItems: totalItems.value, items: cart.value };
  try {
    const response = await fetch('http://localhost:8080/api/orders', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(orderData)
    });
    if (response.ok) {
      toast({ title: "✅ 结账成功", description: `收款 ￥${totalAmount.value.toFixed(2)}。` });
      cart.value = [];
      scannedBarcode.value = '等待扫码...';
    } else {
      toast({ title: "结账失败", variant: "destructive" });
    }
  } catch (error) {
    toast({ title: "网络错误", variant: "destructive" });
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
        <div class="mb-6 border-b border-slate-100 pb-3 flex justify-between items-end">
          <div class="flex items-center">
            <h3 class="text-xl font-bold">购物明细</h3>

            <Button variant="outline" size="sm" class="ml-6 text-indigo-600 border-indigo-200 hover:bg-indigo-50 hover:text-indigo-700 font-bold shadow-sm" @click="openScannerModal">
              <Camera class="w-4 h-4 mr-2" />
              AI 视觉识别收款
            </Button>

          </div>
          <span class="text-sm text-slate-400 font-normal hidden sm:block">敲击 Enter 键可手动输入</span>
        </div>

        <Table>
          <TableHeader class="bg-slate-50/50">
            <TableRow>
              <TableHead class="w-16 text-center">图</TableHead>
              <TableHead class="w-[35%]">商品信息</TableHead>
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
                  <p>购物车空空如也，等待扫码或 AI 识别...</p>
                </div>
              </TableCell>
            </TableRow>
            <TableRow v-for="item in cart" :key="item.barcode" class="group hover:bg-slate-50/80 transition-colors">
              <TableCell>
                <div v-if="item.imageUrl" class="h-12 w-12 rounded-md border border-slate-200 bg-white overflow-hidden flex items-center justify-center shadow-sm">
                  <img :src="'http://localhost:8080' + item.imageUrl" class="object-contain h-full w-full" />
                </div>
                <div v-else class="h-12 w-12 rounded-md border border-slate-100 bg-slate-50 flex items-center justify-center text-[10px] text-slate-400">无图</div>
              </TableCell>
              <TableCell>
                <div class="font-bold text-slate-800 text-base">{{ item.name }}</div>
                <div class="text-xs text-slate-400 font-mono mt-1">{{ item.barcode }}</div>
              </TableCell>
              <TableCell>
                <div class="flex items-center justify-center space-x-3">
                  <Button variant="outline" size="icon" class="h-8 w-8 rounded-full border-slate-200" @click="decreaseQuantity(item)"><Minus class="h-3 w-3" /></Button>
                  <span class="w-8 text-center font-bold text-lg text-slate-700">{{ item.quantity }}</span>
                  <Button variant="outline" size="icon" class="h-8 w-8 rounded-full border-slate-200" @click="increaseQuantity(item)"><Plus class="h-3 w-3" /></Button>
                </div>
              </TableCell>
              <TableCell class="text-slate-500 font-medium">￥{{ item.price.toFixed(2) }}</TableCell>
              <TableCell class="text-emerald-600 font-bold text-lg">￥{{ (item.price * item.quantity).toFixed(2) }}</TableCell>
              <TableCell class="text-right">
                <Button variant="ghost" size="icon" class="text-slate-300 hover:text-red-500 hover:bg-red-50" @click="removeItem(item.barcode)"><Trash2 class="h-5 w-5" /></Button>
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
          <div class="flex justify-between text-slate-400"><span>商品总数</span><span class="font-bold text-slate-200">{{ totalItems }} 件</span></div>
          <div class="flex justify-between text-slate-400"><span>操作员</span><span class="font-bold text-slate-200">Admin</span></div>
        </div>
      </div>
      <div class="mt-12 border-t border-slate-700 pt-8">
        <div class="text-slate-400 mb-2 text-sm uppercase tracking-widest font-medium">应收总额</div>
        <div class="text-6xl font-black text-emerald-400 mb-10 break-all leading-tight"><span class="text-3xl mr-1">￥</span>{{ totalAmount.toFixed(2) }}</div>
        <div class="space-y-4">
          <Button variant="outline" class="w-full h-12 border-slate-700 text-slate-400 hover:bg-red-500/10 hover:text-red-400 hover:border-red-900/50" @click="clearCart"><Trash2 class="mr-2 h-4 w-4" />重置当前交易</Button>
          <Button class="w-full h-20 text-2xl font-black bg-blue-600 hover:bg-blue-700 shadow-[0_0_30px_rgba(37,99,235,0.3)] active:scale-[0.98]" @click="handleCheckout">确认收款</Button>
        </div>
      </div>
    </div>

    <div v-if="showManualInput" class="fixed inset-0 bg-black/70 backdrop-blur-md flex items-center justify-center z-50 p-4" @click.self="closeManualInput">
      <div class="bg-white p-8 rounded-3xl shadow-2xl w-full max-w-md transform animate-in fade-in zoom-in duration-300">
        <div class="flex items-center space-x-3 mb-6"><div class="bg-blue-100 p-2 rounded-lg">⌨️</div><h3 class="text-2xl font-bold text-slate-800">手动录入</h3></div>
        <input ref="manualInputRef" v-model="manualBarcode" type="text" class="w-full text-3xl font-mono p-5 border-2 border-slate-200 rounded-2xl focus:border-blue-500 outline-none mb-6 text-center tracking-widest" placeholder="690..." @keydown.enter="submitManualBarcode" @keydown.esc="closeManualInput"/>
        <div class="flex gap-4">
          <Button variant="ghost" class="flex-1 h-14 text-slate-500 font-bold" @click="closeManualInput">取消 (Esc)</Button>
          <Button class="flex-1 h-14 bg-blue-600 hover:bg-blue-700 text-lg font-bold shadow-lg" @click="submitManualBarcode">确认检索</Button>
        </div>
      </div>
    </div>

    <div v-if="showScannerModal" class="fixed inset-0 bg-slate-900/90 backdrop-blur-sm flex items-center justify-center z-50 p-4" @click.self="closeScannerModal">
      <div class="bg-white p-6 rounded-3xl shadow-2xl w-full max-w-md flex flex-col items-center animate-in fade-in zoom-in duration-300">
        <div class="flex items-center space-x-2 mb-4 w-full justify-center relative">
          <h3 class="text-xl font-black text-slate-800 tracking-wider">📸 AI 智能收银</h3>
        </div>

        <p class="text-xs text-slate-500 mb-4 text-center">请将顾客购买的商品对准镜头</p>

        <div class="relative w-full rounded-2xl overflow-hidden bg-black mb-6 border-4 border-indigo-50 aspect-[4/3] flex items-center justify-center">
          <video ref="videoRef" autoplay playsinline class="w-full h-full object-cover"></video>
          <div class="absolute inset-8 border-2 border-indigo-400/50 border-dashed rounded-lg pointer-events-none"></div>
          <div v-if="isRecognizing" class="absolute inset-0 bg-indigo-900/20 backdrop-blur-[2px] flex items-center justify-center">
            <span class="text-white font-bold tracking-widest animate-pulse">正在检索云端特征...</span>
          </div>
        </div>

        <canvas ref="canvasRef" style="display: none;"></canvas>

        <div class="flex gap-4 w-full">
          <Button variant="ghost" class="flex-1 h-14 text-slate-500 font-bold bg-slate-100" @click="closeScannerModal">放弃</Button>
          <Button class="flex-1 h-14 bg-indigo-600 hover:bg-indigo-700 text-lg font-bold shadow-lg" :disabled="isRecognizing" @click="takeSnapshotAndRecognize">
            {{ isRecognizing ? 'AI 识别中...' : '拍照识别并加入购物车' }}
          </Button>
        </div>
      </div>
    </div>

  </div>
</template>