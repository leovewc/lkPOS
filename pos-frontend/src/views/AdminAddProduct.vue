<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useToast } from '@/components/ui/toast/use-toast';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog';
// 🌟 引入了 TrendingUp 图标用于打开统计面板
import { Search, Trash2, PackagePlus, Tags, Edit, X, TrendingUp, Info } from 'lucide-vue-next';

// 🌟 扩充 Product 接口
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
}

const { toast } = useToast();

const productForm = ref({
  barcodes: [] as string[],
  name: '',
  price: 0,
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
const editForm = ref({ barcode: '', barcodes: [] as string[], name: '', price: 0 });

// 🌟 新增：统计面板相关的状态
const isStatsDialogOpen = ref(false);
const currentStatsProduct = ref<Product | null>(null);
const currentStats = ref({ totalSales: 0, todaySales: 0 });
const isLoadingStats = ref(false);

// 并在下面新增两个变量用来控制详情弹窗
const isDetailsDialogOpen = ref(false);
const currentDetailsProduct = ref<Product | null>(null);

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

// 🌟 核心修改：敲回车时自动触发云端解析
const handleEnterBarcode = async () => {
  const bc = currentBarcode.value.trim();
  if (!bc) return;
  if (productForm.value.barcodes.includes(bc)) {
    toast({ description: "该条码已在列表中", variant: "destructive" });
    currentBarcode.value = '';
    return;
  }

  // 1. 给出正在解析的友好提示
  toast({ description: "正在从云端检索商品信息..." });

  try {
    // 2. 调用后端的 API 进行抓取和图片下载
    const response = await fetch(`http://localhost:8080/api/products/fetch-external?barcode=${bc}`);
    if (response.ok) {
      const data = await response.json();
      if (data.name) {
        // 3. 解析成功！自动把云端数据填入左侧表单
        productForm.value.name = data.name;
        productForm.value.price = data.price || 0;
        productForm.value.imageUrl = data.imageUrl; // 获取下载到本地的图片路径

        // 🌟 核心修复：把后端传过来的商业全息数据，完美赋给前端表单变量
        productForm.value.brand = data.brand || '';
        productForm.value.specification = data.specification || '';
        productForm.value.manufacturer = data.manufacturer || '';
        productForm.value.category = data.category || '';
        productForm.value.note = data.note || '';

        toast({ title: "解析成功", description: `已自动解析: ${data.name}` });
      } else {
        toast({ title: "云端库无此记录", description: "请手动补充商品名称和价格" });
      }
    }
  } catch (error) {
    toast({ title: "联网获取失败", description: "请手动录入", variant: "destructive" });
  }

  // 4. 无论云端有没有数据，都把这个条码做成标签贴上去
  productForm.value.barcodes.push(bc);
  currentBarcode.value = '';
};

// 🌟 记得修改 handleSubmit，保存成功后清空 imageUrl
const handleSubmit = async () => {
  if (productForm.value.barcodes.length === 0 || !productForm.value.name) {
    return toast({ title: "表单不完整", description: "请至少录入一个条形码和商品名称", variant: "destructive" });
  }
  isSubmitting.value = true;
  try {
    const response = await fetch('http://localhost:8080/api/products', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(productForm.value)
    });
    if (response.ok) {
      toast({ title: "🎉 录入成功" });
      // 🌟 清空表单，迎接下一个商品
      productForm.value = { barcodes: [], name: '', price: 0, imageUrl: '', brand: '', specification: '', manufacturer: '', category: '', note: '' };
      fetchAllProducts();
    } else toast({ title: "录入失败", variant: "destructive" });
  } catch (error) {
    toast({ title: "网络错误", variant: "destructive" });
  } finally {
    isSubmitting.value = false;
  }
};

const removeBarcodeTag = (index: number) => {
  productForm.value.barcodes.splice(index, 1);
};


const handleDelete = async (barcode: string, name: string) => {
  if (!confirm(`确定要彻底删除商品【${name}】及其所有关联条码吗？`)) return;
  try {
    const response = await fetch(`http://localhost:8080/api/products/${barcode}`, { method: 'DELETE' });
    if (response.ok) {
      toast({ title: "🗑️ 删除成功" });
      fetchAllProducts();
    } else toast({ title: "删除失败", variant: "destructive" });
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
    } else toast({ title: "修改失败", variant: "destructive" });
  } catch (error) {
    toast({ title: "网络错误", variant: "destructive" });
  } finally {
    isSavingEdit.value = false;
  }
};

// --- 🌟 打开数据统计面板 ---
const openStatsDialog = async (item: Product) => {
  currentStatsProduct.value = item;
  isStatsDialogOpen.value = true;
  isLoadingStats.value = true;
  // 先清零，防止显示上一次的数据
  currentStats.value = { totalSales: 0, todaySales: 0 };

  try {
    // 调用后端刚刚写好的统计接口
    const response = await fetch(`http://localhost:8080/api/products/${item.id}/stats`);
    if (response.ok) {
      currentStats.value = await response.json();
    }
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
            <label class="text-sm font-semibold text-slate-700">物理条形码集合</label>
            <div class="flex flex-wrap gap-2" v-if="productForm.barcodes.length > 0">
              <div v-for="(bc, index) in productForm.barcodes" :key="index" class="bg-blue-100 text-blue-800 px-3 py-1 rounded-md text-sm font-mono flex items-center shadow-sm border border-blue-200 animate-in zoom-in duration-200">
                {{ bc }} <button @click="removeBarcodeTag(index)" class="ml-2 text-blue-400 hover:text-red-500 transition-colors"><X class="h-4 w-4" /></button>
              </div>
            </div>

            <div class="flex gap-2">
              <Input v-model="currentBarcode" @keydown.enter.prevent="handleEnterBarcode" class="font-mono bg-slate-50 flex-1" placeholder="扫码后按回车自动解析..." />
              <Button type="button" variant="secondary" @click="handleEnterBarcode">添加</Button>
            </div>
          </div>

          <div class="space-y-2 border-t pt-4 mt-2" v-if="productForm.imageUrl">
            <label class="text-sm font-semibold text-slate-700">商品图</label>
            <div class="h-24 w-24 border rounded-md overflow-hidden bg-white flex items-center justify-center">
              <img :src="'http://localhost:8080' + productForm.imageUrl" class="object-contain h-full w-full" />
            </div>
          </div>

          <div class="space-y-2 border-t pt-4 mt-2" v-else>
          </div>

          <div class="space-y-2"><label class="text-sm font-semibold text-slate-700">商品名称 (各口味统称)</label><Input v-model="productForm.name" class="bg-slate-50" placeholder="例如: 农夫山泉 550ml" /></div>
          <div class="space-y-2"><label class="text-sm font-semibold text-slate-700">统一零售单价 (￥)</label><Input v-model="productForm.price" type="number" step="0.01" class="font-mono text-lg bg-slate-50 text-blue-600 font-bold" /></div>
        </CardContent>
        <CardFooter><Button class="w-full h-12 text-base font-bold bg-blue-600 hover:bg-blue-700" @click="handleSubmit" :disabled="isSubmitting">{{ isSubmitting ? '写入中...' : '确认保存全部信息' }}</Button></CardFooter>
      </Card>

      <Card class="w-full flex-1 shadow-sm border-slate-200 overflow-hidden">
        <CardHeader class="bg-white border-b border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-4 py-5">
          <div>
            <div class="flex items-center space-x-2"><Tags class="h-5 w-5 text-blue-600" /><CardTitle class="text-xl font-bold text-slate-800">商品库存管理</CardTitle></div>
            <CardDescription class="mt-1">已登记 {{ productList.length }} 种主商品</CardDescription>
          </div>
          <div class="relative w-full sm:w-72">
            <Search class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
            <Input v-model="searchQuery" placeholder="搜索条码或商品名称..." class="pl-9 bg-slate-50 rounded-full" />
          </div>
        </CardHeader>

        <CardContent class="p-0">
          <Table>
            <TableHeader class="bg-slate-50/80">
              <TableRow>
                <TableHead class="font-bold text-slate-700 w-16 text-center">图</TableHead>
                <TableHead class="font-bold text-slate-700">商品名称</TableHead>
                <TableHead class="font-bold text-slate-700 w-[35%]">关联条码集合</TableHead>
                <TableHead class="font-bold text-slate-700 text-right">零售价</TableHead>
                <TableHead class="font-bold text-slate-700 text-right pr-6">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-if="filteredProducts.length === 0"><TableCell colspan="5" class="h-48 text-center text-slate-500">没有找到匹配的记录</TableCell></TableRow>

              <TableRow v-for="item in filteredProducts" :key="item.barcode" class="hover:bg-blue-50/50 transition-colors group">

                <TableCell>
                  <div v-if="item.imageUrl" class="h-10 w-10 rounded border bg-white overflow-hidden flex items-center justify-center">
                    <img :src="'http://localhost:8080' + item.imageUrl" class="object-contain h-full w-full" />
                  </div>
                  <div v-else class="h-10 w-10 rounded border bg-slate-50 flex items-center justify-center text-[10px] text-slate-400">无图</div>
                </TableCell>

                <TableCell class="font-medium text-slate-800 text-base">{{ item.name }}</TableCell>

                <TableCell>
                  <div class="flex flex-wrap gap-1.5">
                    <span v-for="bc in item.barcodes" :key="bc" class="bg-slate-100 border border-slate-200 text-slate-600 px-2 py-0.5 rounded text-xs font-mono">{{ bc }}</span>
                  </div>
                </TableCell>
                <TableCell class="text-right text-emerald-600 font-bold text-base">￥{{ item.price.toFixed(2) }}</TableCell>

                <TableCell class="text-right pr-4">

                  <div class="flex justify-end space-x-1 opacity-0 group-hover:opacity-100 transition-opacity duration-200">
                    <Button variant="ghost" size="icon" class="text-slate-400 hover:text-indigo-600 hover:bg-indigo-50" @click="openDetailsDialog(item)" title="商品全息档案">
                      <Info class="h-4 w-4" />
                    </Button>
                    <Button variant="ghost" size="icon" class="text-slate-400 hover:text-emerald-600 hover:bg-emerald-50" @click="openStatsDialog(item)" title="查看销量报表">
                      <TrendingUp class="h-4 w-4" />
                    </Button>
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

    <Dialog v-model:open="isEditDialogOpen">
      <DialogContent class="sm:max-w-[425px]">
        <DialogHeader><DialogTitle>编辑商品信息</DialogTitle></DialogHeader>
        <div class="space-y-4 py-4">
          <div class="space-y-2"><label class="text-sm font-semibold text-slate-700">商品名称</label><Input v-model="editForm.name" /></div>
          <div class="space-y-2"><label class="text-sm font-semibold text-slate-700">统一单价 (￥)</label><Input v-model="editForm.price" type="number" step="0.01" class="text-blue-600 font-bold" /></div>
        </div>
        <DialogFooter><Button variant="outline" @click="isEditDialogOpen = false">取消</Button><Button class="bg-blue-600 hover:bg-blue-700" @click="submitEdit">保存修改</Button></DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog v-model:open="isStatsDialogOpen">
      <DialogContent class="sm:max-w-[500px] border-0 shadow-2xl overflow-hidden p-0">
        <div class="bg-gradient-to-r from-emerald-500 to-teal-600 p-6 text-white relative">
          <h2 class="text-2xl font-black mb-1 flex items-center gap-2">
            <TrendingUp class="h-6 w-6" /> 商品销售洞察
          </h2>
          <p class="text-emerald-100 text-sm opacity-90">{{ currentStatsProduct?.name }}</p>
        </div>

        <div class="p-6 bg-slate-50">
          <div v-if="isLoadingStats" class="flex justify-center py-10">
            <span class="text-slate-400 animate-pulse">正在从数据库拉取数据...</span>
          </div>

          <div v-else class="grid grid-cols-2 gap-4">
            <Card class="border-0 shadow-sm shadow-emerald-100/50 bg-white">
              <CardContent class="p-5 flex flex-col items-center justify-center text-center">
                <span class="text-slate-400 text-sm font-semibold mb-2">历史累计销量</span>
                <div class="text-4xl font-black text-slate-800">
                  {{ currentStats.totalSales }} <span class="text-base text-slate-400 font-normal">件</span>
                </div>
              </CardContent>
            </Card>

            <Card class="border-0 shadow-sm shadow-orange-100/50 bg-white relative overflow-hidden">
              <CardContent class="p-5 flex flex-col items-center justify-center text-center">
                <div class="absolute top-0 right-0 bg-orange-100 text-orange-600 text-[10px] font-bold px-2 py-1 rounded-bl-lg">今日</div>
                <span class="text-slate-400 text-sm font-semibold mb-2">今日售出</span>
                <div class="text-4xl font-black text-orange-500">
                  {{ currentStats.todaySales }} <span class="text-base text-orange-300 font-normal">件</span>
                </div>
              </CardContent>
            </Card>
          </div>

          <div class="mt-6 text-xs text-slate-400 text-center flex items-center justify-center gap-1">
            <span>💡 数据已跨条码聚合汇总，统计实时生效。</span>
          </div>
        </div>

        <div class="bg-white p-4 border-t flex justify-end">
          <Button variant="outline" @click="isStatsDialogOpen = false">关闭面板</Button>
        </div>
      </DialogContent>
    </Dialog>

  </div>

  *** 商品详情弹窗
  <Dialog v-model:open="isDetailsDialogOpen">
    <DialogContent class="sm:max-w-[600px] p-0 border-0 overflow-hidden shadow-2xl">

      <div class="bg-gradient-to-br from-indigo-50 to-slate-100 p-8 flex items-center gap-6 border-b border-slate-200">
        <div class="h-32 w-32 bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden flex-shrink-0 flex items-center justify-center p-2">
          <img v-if="currentDetailsProduct?.imageUrl" :src="'http://localhost:8080' + currentDetailsProduct.imageUrl" class="object-contain h-full w-full" />
          <span v-else class="text-slate-400 text-sm">暂无图片</span>
        </div>
        <div>
          <div class="inline-block px-3 py-1 bg-indigo-100 text-indigo-700 text-xs font-bold rounded-full mb-2">
            {{ currentDetailsProduct?.category || '未分类' }}
          </div>
          <h2 class="text-2xl font-black text-slate-800 leading-tight mb-2">{{ currentDetailsProduct?.name }}</h2>
          <p class="text-3xl font-black text-emerald-600">￥{{ currentDetailsProduct?.price.toFixed(2) }}</p>
        </div>
      </div>

      <div class="p-8 bg-white space-y-6">
        <div class="grid grid-cols-2 gap-y-6 gap-x-8">
          <div>
            <p class="text-xs font-bold text-slate-400 uppercase tracking-wider mb-1">所属品牌</p>
            <p class="text-slate-800 font-medium">{{ currentDetailsProduct?.brand || '未知' }}</p>
          </div>
          <div>
            <p class="text-xs font-bold text-slate-400 uppercase tracking-wider mb-1">包装规格</p>
            <p class="text-slate-800 font-medium">{{ currentDetailsProduct?.specification || '未知' }}</p>
          </div>
          <div class="col-span-2">
            <p class="text-xs font-bold text-slate-400 uppercase tracking-wider mb-1">生产企业</p>
            <p class="text-slate-800 font-medium">{{ currentDetailsProduct?.manufacturer || '未知' }}</p>
          </div>
        </div>

        <div class="pt-6 border-t border-slate-100">
          <p class="text-xs font-bold text-slate-400 uppercase tracking-wider mb-2">详细参数与备注</p>
          <p class="text-sm text-slate-600 leading-relaxed bg-slate-50 p-4 rounded-lg border border-slate-100">
            {{ currentDetailsProduct?.note || '暂无更多详细描述。' }}
          </p>
        </div>
      </div>

      <div class="bg-slate-50 p-4 flex justify-end">
        <Button @click="isDetailsDialogOpen = false">关闭档案</Button>
      </div>
    </DialogContent>
  </Dialog>
</template>