<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue';
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useToast } from '@/components/ui/toast/use-toast';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog';
import { Search, Trash2, PackagePlus, Tags, Edit, X, TrendingUp, Info, Camera, ScanFace } from 'lucide-vue-next';

interface Product {
  id?: number;
  name: string;
  price: number;
  imageUrl?: string;
  barcode: string;
  barcodes: string[];
  brand?: string;
  specification?: string;
  manufacturer?: string;
  category?: string;
  note?: string;
  costPrice: number;
}

const { toast } = useToast();

const productForm = ref({
  barcodes: [] as string[],
  name: '',
  price: 0,
  costPrice: 0,
  imageUrl: '',
  brand: '',
  specification: '',
  manufacturer: '',
  category: '',
  note: ''
});

const currentBarcode = ref('');
const isSubmitting = ref(false);
const productList = ref<Product[]>([]);
const searchQuery = ref('');

const isEditDialogOpen = ref(false);
const isSavingEdit = ref(false);
const editForm = ref({ barcode: '', barcodes: [] as string[], name: '', price: 0, costPrice: 0 });

const isStatsDialogOpen = ref(false);
const currentStatsProduct = ref<Product | null>(null);
const currentStats = ref({ totalSales: 0, todaySales: 0 });
const isLoadingStats = ref(false);

const isDetailsDialogOpen = ref(false);
const currentDetailsProduct = ref<Product | null>(null);

// === 🌟 AI 实物采集相关状态 ===
const showCaptureModal = ref(false);
const videoRef = ref<HTMLVideoElement | null>(null);
const canvasRef = ref<HTMLCanvasElement | null>(null);
const isCapturing = ref(false);
let stream: MediaStream | null = null;
const capturedBlob = ref<Blob | null>(null); // 保存拍下的原图
const previewImageUrl = ref<string>('');     // 用于在表单中预览的本地地址

const openDetailsDialog = (item: Product) => {
  currentDetailsProduct.value = item;
  isDetailsDialogOpen.value = true;
};

const fetchAllProducts = async () => {
  try {
    const response = await fetch('http://localhost:8080/api/products');
    if (response.ok) productList.value = await response.json();
  } catch (error) {
    console.error("拉取商品列表失败", error);
  }
};

onMounted(() => fetchAllProducts());

const filteredProducts = computed(() => {
  const query = searchQuery.value.trim().toLowerCase();
  if (!query) return productList.value;
  return productList.value.filter(product =>
      product.name.toLowerCase().includes(query) || product.barcode.includes(query)
  );
});

// 联网解析
const handleEnterBarcode = async () => {
  const bc = currentBarcode.value.trim();
  if (!bc) return;
  if (productForm.value.barcodes.includes(bc)) {
    toast({ description: "该条码已在列表中", variant: "destructive" });
    currentBarcode.value = '';
    return;
  }

  toast({ description: "正在从云端检索商品信息..." });

  try {
    const response = await fetch(`http://localhost:8080/api/products/fetch-external?barcode=${bc}`);
    if (response.ok) {
      const data = await response.json();
      if (data.name) {
        productForm.value.name = data.name;
        productForm.value.price = data.price || 0;
        productForm.value.imageUrl = data.imageUrl;
        productForm.value.brand = data.brand || '';
        productForm.value.specification = data.specification || '';
        productForm.value.manufacturer = data.manufacturer || '';
        productForm.value.category = data.category || '';
        productForm.value.note = data.note || '';
        productForm.value.costPrice = 0;

        // 设置预览图为云端图片
        previewImageUrl.value = 'http://localhost:8080' + data.imageUrl;
        capturedBlob.value = null; // 清除之前的实物拍照缓存

        toast({ title: "解析成功", description: `已自动解析: ${data.name}` });
      } else {
        toast({ title: "云端无记录", description: "请手动补充商品信息并采集图片" });
      }
    }
  } catch (error) {
    toast({ title: "联网获取失败", description: "请手动录入", variant: "destructive" });
  }

  productForm.value.barcodes.push(bc);
  currentBarcode.value = '';
};

// === 🌟 AI 摄像头逻辑 ===
const openCaptureModal = async () => {
  showCaptureModal.value = true;
  await nextTick();
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: "environment", width: { ideal: 640 } }
    });
    if (videoRef.value) videoRef.value.srcObject = stream;
  } catch (err) {
    toast({ title: "摄像头调用失败", variant: "destructive" });
    showCaptureModal.value = false;
  }
};

const closeCaptureModal = () => {
  if (stream) {
    stream.getTracks().forEach(track => track.stop());
    stream = null;
  }
  showCaptureModal.value = false;
};

const takeSnapshot = () => {
  if (!videoRef.value || !canvasRef.value) return;
  isCapturing.value = true;
  const video = videoRef.value;
  const canvas = canvasRef.value;
  canvas.width = video.videoWidth;
  canvas.height = video.videoHeight;
  const ctx = canvas.getContext('2d');
  if (!ctx) return;

  ctx.drawImage(video, 0, 0, canvas.width, canvas.height);

  canvas.toBlob((blob) => {
    if (blob) {
      capturedBlob.value = blob;
      previewImageUrl.value = URL.createObjectURL(blob); // 实时替换左侧表单的预览图
      toast({ description: "📸 实物采集成功，已加入训练序列" });
    }
    closeCaptureModal();
    isCapturing.value = false;
  }, 'image/jpeg', 0.9);
};

// 🌟 终极版双写逻辑：先传Java存图 -> 存入业务库 -> 教会 Python AI
const handleSubmit = async () => {
  if (productForm.value.barcodes.length === 0 || !productForm.value.name) {
    return toast({ title: "表单不完整", description: "请至少录入一个条形码和商品名称", variant: "destructive" });
  }
  if (productForm.value.costPrice < 0 || productForm.value.price <= 0) {
    return toast({ title: "价格异常", variant: "destructive" });
  }

  isSubmitting.value = true;
  try {
    // ==========================================
    // 步骤 0. 🌟 如果有实物抓拍图片，先上传给 Java 后台保存！
    // ==========================================
    if (capturedBlob.value) {
      toast({ description: "正在上传实物照片..." });
      const imgFormData = new FormData();
      imgFormData.append('file', capturedBlob.value, 'live_capture.jpg');

      const uploadRes = await fetch('http://localhost:8080/api/products/upload', {
        method: 'POST',
        body: imgFormData
      });

      if (uploadRes.ok) {
        // 拿到 Java 返回的图片相对路径（如 /uploads/xxx.jpg），赋给表单！
        productForm.value.imageUrl = await uploadRes.text();
      }
    }

    // ==========================================
    // 步骤 1. 存入 MySQL 业务库 (此时 imageUrl 已经有值了)
    // ==========================================
    const response = await fetch('http://localhost:8080/api/products', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(productForm.value)
    });

    if (response.ok) {
      toast({ title: "🎉 业务库录入成功" });

      // ==========================================
      // 步骤 2. AI 视觉训练阶段
      // ==========================================
      if (capturedBlob.value || productForm.value.imageUrl) {
        toast({ description: "正在同步提取 AI 视觉特征..." });
        try {
          const aiFormData = new FormData();

          if (capturedBlob.value) {
            // 优先用实物图喂给 AI
            aiFormData.append('file', capturedBlob.value, 'live_capture.jpg');
          } else {
            // 没有实物图，就去 Java 拉取云端白底图喂给 AI
            const imgRes = await fetch(`http://localhost:8080${productForm.value.imageUrl}`);
            const imgBlob = await imgRes.blob();
            aiFormData.append('file', imgBlob, 'cloud_image.jpg');
          }

          aiFormData.append('barcode', productForm.value.barcodes[0]);
          aiFormData.append('name', productForm.value.name);

          const aiResponse = await fetch('http://127.0.0.1:8000/api/learn', {
            method: 'POST',
            body: aiFormData
          });

          if (aiResponse.ok) {
            toast({ title: "🧠 AI 学习完成", description: "商品已存入视觉记忆库！" });
          } else {
            toast({ title: "AI 学习失败", variant: "destructive" });
          }
        } catch (aiError) {
          toast({ title: "AI 引擎离线", description: "请检查 Python 服务", variant: "destructive" });
        }
      }

      // ==========================================
      // 步骤 3. 清空表单
      // ==========================================
      productForm.value = { barcodes: [], name: '', price: 0, costPrice: 0, imageUrl: '', brand: '', specification: '', manufacturer: '', category: '', note: '' };
      previewImageUrl.value = '';
      capturedBlob.value = null;
      fetchAllProducts();
    }
  } catch (error) {
    toast({ title: "网络错误", variant: "destructive" });
  } finally {
    isSubmitting.value = false;
  }
};

const removeBarcodeTag = (index: number) => {
  productForm.value.barcodes.splice(index, 1);
};

// 🌟 双删逻辑：删除业务库 + 抹除 AI 记忆
const handleDelete = async (barcode: string, name: string) => {
  if (!confirm(`确定要彻底删除商品【${name}】及其所有关联条码吗？`)) return;
  try {
    const response = await fetch(`http://localhost:8080/api/products/${barcode}`, { method: 'DELETE' });
    if (response.ok) {
      try {
        await fetch(`http://127.0.0.1:8000/api/product/${barcode}`, { method: 'DELETE' });
        toast({ title: "🗑️ 彻底删除成功", description: "业务数据与 AI 视觉记忆均已清除" });
      } catch (aiErr) {
        toast({ title: "🗑️ 业务删除成功", description: "注意：AI 引擎离线，未能清除视觉残留" });
      }
      fetchAllProducts();
    }
  } catch (error) {
    toast({ title: "网络错误", variant: "destructive" });
  }
};

const openEditDialog = (item: Product) => {
  editForm.value = { ...item };
  isEditDialogOpen.value = true;
};

const submitEdit = async () => {
  if (!editForm.value.name) return toast({ title: "名称不能为空", variant: "destructive" });
  isSavingEdit.value = true;
  try {
    const response = await fetch(`http://localhost:8080/api/products/${editForm.value.barcode}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(editForm.value)
    });
    if (response.ok) {
      toast({ title: "✏️ 修改成功" });
      isEditDialogOpen.value = false;
      fetchAllProducts();
    }
  } catch (error) {
    toast({ title: "网络错误", variant: "destructive" });
  } finally {
    isSavingEdit.value = false;
  }
};

const openStatsDialog = async (item: Product) => {
  currentStatsProduct.value = item;
  isStatsDialogOpen.value = true;
  isLoadingStats.value = true;
  currentStats.value = { totalSales: 0, todaySales: 0 };
  try {
    const response = await fetch(`http://localhost:8080/api/products/${item.id}/stats`);
    if (response.ok) currentStats.value = await response.json();
  } catch (error) {
    toast({ title: "获取统计数据失败", variant: "destructive" });
  } finally {
    isLoadingStats.value = false;
  }
};
</script>

<template>
  <div class="min-h-screen bg-slate-50/50 p-6 md:p-10">
    <div class="max-w-7xl mx-auto flex flex-col lg:flex-row gap-8 items-start">

      <Card class="w-full lg:w-[380px] shadow-sm border-slate-200 sticky top-8 flex-shrink-0">
        <CardHeader class="bg-gradient-to-br from-slate-900 to-slate-800 text-white rounded-t-xl pb-8">
          <div class="flex items-center space-x-3 mb-2"><PackagePlus class="h-6 w-6 text-blue-400" /><CardTitle class="text-xl font-bold">商品入库</CardTitle></div>
          <CardDescription class="text-slate-400">敲击回车自动联网获取商品信息。</CardDescription>
        </CardHeader>
        <CardContent class="space-y-5 mt-6">
          <div class="space-y-3">
            <label class="text-sm font-semibold text-slate-700">物理条形码</label>
            <div class="flex flex-wrap gap-2" v-if="productForm.barcodes.length > 0">
              <div v-for="(bc, index) in productForm.barcodes" :key="index" class="bg-blue-100 text-blue-800 px-3 py-1 rounded-md text-sm font-mono flex items-center shadow-sm">
                {{ bc }} <button @click="removeBarcodeTag(index)" class="ml-2 hover:text-red-500"><X class="h-4 w-4" /></button>
              </div>
            </div>
            <div class="flex gap-2">
              <Input v-model="currentBarcode" @keydown.enter.prevent="handleEnterBarcode" class="font-mono bg-slate-50 flex-1" placeholder="扫码后按回车..." />
              <Button type="button" variant="secondary" @click="handleEnterBarcode">添加</Button>
            </div>
          </div>

          <div class="space-y-3 border-t pt-4 mt-2">
            <div class="flex justify-between items-center">
              <label class="text-sm font-semibold text-slate-700">商品图 (用于AI特征提取)</label>
              <Button size="sm" variant="outline" class="text-indigo-600 border-indigo-200 hover:bg-indigo-50 font-bold" @click="openCaptureModal">
                <Camera class="w-4 h-4 mr-2" /> 采集实物
              </Button>
            </div>

            <div v-if="previewImageUrl" class="h-32 w-full border-2 border-dashed border-slate-200 rounded-xl overflow-hidden bg-white flex items-center justify-center relative group">
              <img :src="previewImageUrl" class="object-contain h-full w-full" />
              <div v-if="capturedBlob" class="absolute bottom-2 right-2 bg-indigo-500 text-white text-[10px] px-2 py-1 rounded shadow-sm">实物采集</div>
            </div>
            <div v-else class="h-32 w-full border-2 border-dashed border-slate-200 rounded-xl bg-slate-50 flex flex-col items-center justify-center text-slate-400">
              <ScanFace class="w-8 h-8 mb-2 opacity-50" />
              <span class="text-xs">暂无图片，AI 无法学习</span>
            </div>
          </div>

          <div class="space-y-2 border-t pt-4"><label class="text-sm font-semibold text-slate-700">商品名称</label><Input v-model="productForm.name" class="bg-slate-50" placeholder="例如: 农夫山泉 550ml" /></div>
          <div class="space-y-2">
            <label class="text-sm font-semibold text-slate-700">进货成本价 (￥)</label>
            <Input v-model="productForm.costPrice" type="number" step="1" class="font-mono text-lg bg-orange-50 text-orange-600 font-bold border-orange-200" placeholder="0.00" />
          </div>
          <div class="space-y-2">
            <label class="text-sm font-semibold text-slate-700">统一零售单价 (￥)</label>
            <Input v-model="productForm.price" type="number" step="1" class="font-mono text-lg bg-emerald-50 text-emerald-600 font-bold border-emerald-200" />
          </div>
        </CardContent>
        <CardFooter><Button class="w-full h-12 text-base font-bold bg-blue-600 hover:bg-blue-700" @click="handleSubmit" :disabled="isSubmitting">{{ isSubmitting ? '同步存储中...' : '录入业务与AI库' }}</Button></CardFooter>
      </Card>

      <Card class="w-full flex-1 shadow-sm border-slate-200 overflow-hidden">
        <CardHeader class="bg-white border-b border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-4 py-5">
          <div>
            <div class="flex items-center space-x-2"><Tags class="h-5 w-5 text-blue-600" /><CardTitle class="text-xl font-bold text-slate-800">商品库存管理</CardTitle></div>
            <CardDescription class="mt-1">已登记 {{ productList.length }} 种主商品</CardDescription>
          </div>
          <div class="relative w-full sm:w-72">
            <Search class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
            <Input v-model="searchQuery" placeholder="搜索条码或名称..." class="pl-9 bg-slate-50 rounded-full" />
          </div>
        </CardHeader>
        <CardContent class="p-0">
          <Table>
            <TableHeader class="bg-slate-50/80">
              <TableRow>
                <TableHead class="font-bold text-slate-700 w-16 text-center">图</TableHead>
                <TableHead class="font-bold text-slate-700">商品名称</TableHead>
                <TableHead class="font-bold text-slate-700 w-[35%]">关联条码</TableHead>
                <TableHead class="font-bold text-slate-700 text-right">单价</TableHead>
                <TableHead class="font-bold text-slate-700 text-right pr-6">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-for="item in filteredProducts" :key="item.barcode" class="hover:bg-blue-50/50 transition-colors group">
                <TableCell>
                  <div v-if="item.imageUrl" class="h-10 w-10 rounded border bg-white overflow-hidden flex items-center justify-center">
                    <img :src="'http://localhost:8080' + item.imageUrl" class="object-contain h-full w-full" />
                  </div>
                  <div v-else class="h-10 w-10 rounded border bg-slate-50 flex items-center justify-center text-[10px] text-slate-400">无图</div>
                </TableCell>
                <TableCell class="font-medium text-slate-800 text-base">{{ item.name }}</TableCell>
                <TableCell>
                  <div class="flex flex-wrap gap-1.5"><span v-for="bc in item.barcodes" :key="bc" class="bg-slate-100 border border-slate-200 text-slate-600 px-2 py-0.5 rounded text-xs font-mono">{{ bc }}</span></div>
                </TableCell>
                <TableCell class="text-right text-emerald-600 font-bold text-base">￥{{ item.price.toFixed(2) }}</TableCell>
                <TableCell class="text-right pr-4">
                  <div class="flex justify-end space-x-1 opacity-0 group-hover:opacity-100 transition-opacity duration-200">
                    <Button variant="ghost" size="icon" class="text-slate-400 hover:text-indigo-600 hover:bg-indigo-50" @click="openDetailsDialog(item)"><Info class="h-4 w-4" /></Button>
                    <Button variant="ghost" size="icon" class="text-slate-400 hover:text-emerald-600 hover:bg-emerald-50" @click="openStatsDialog(item)"><TrendingUp class="h-4 w-4" /></Button>
                    <Button variant="ghost" size="icon" class="text-slate-400 hover:text-blue-600 hover:bg-blue-50" @click="openEditDialog(item)"><Edit class="h-4 w-4" /></Button>
                    <Button variant="ghost" size="icon" class="text-slate-400 hover:text-red-600 hover:bg-red-50" @click="handleDelete(item.barcode, item.name)"><Trash2 class="h-4 w-4" /></Button>
                  </div>
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>

    <div v-if="showCaptureModal" class="fixed inset-0 bg-slate-900/90 backdrop-blur-sm flex items-center justify-center z-50 p-4" @click.self="closeCaptureModal">
      <div class="bg-white p-6 rounded-3xl shadow-2xl w-full max-w-md flex flex-col items-center animate-in fade-in zoom-in duration-300">
        <div class="flex items-center space-x-2 mb-4 w-full justify-center relative">
          <h3 class="text-xl font-black text-slate-800 tracking-wider">📸 AI 实物采样</h3>
        </div>

        <p class="text-xs text-slate-500 mb-4 text-center">请将商品置于框内并保持清晰，这会让 AI 认得更准</p>

        <div class="relative w-full rounded-2xl overflow-hidden bg-black mb-6 border-4 border-indigo-50 aspect-[4/3] flex items-center justify-center">
          <video ref="videoRef" autoplay playsinline class="w-full h-full object-cover"></video>
          <div class="absolute inset-8 border-2 border-white/40 border-dashed rounded-lg pointer-events-none"></div>
          <div class="absolute inset-0 border-[3px] border-indigo-500/30 rounded-xl pointer-events-none"></div>
        </div>

        <canvas ref="canvasRef" style="display: none;"></canvas>

        <div class="flex gap-4 w-full">
          <Button variant="ghost" class="flex-1 h-14 text-slate-500 font-bold bg-slate-100" @click="closeCaptureModal">取消</Button>
          <Button class="flex-1 h-14 bg-indigo-600 hover:bg-indigo-700 text-lg font-bold shadow-lg" :disabled="isCapturing" @click="takeSnapshot">
            {{ isCapturing ? '处理中...' : '拍摄并使用' }}
          </Button>
        </div>
      </div>
    </div>

    <Dialog v-model:open="isEditDialogOpen"><DialogContent><DialogHeader><DialogTitle>编辑</DialogTitle></DialogHeader><div class="space-y-2"><Input v-model="editForm.name" /><Input v-model="editForm.price" /></div><DialogFooter><Button @click="submitEdit">保存</Button></DialogFooter></DialogContent></Dialog>
  </div>
</template>