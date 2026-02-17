<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useToast } from '@/components/ui/toast/use-toast';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog';
// 🌟 引入了 X 图标用来删除单个条码标签
import { Search, Trash2, PackagePlus, Tags, Edit, X, Plus } from 'lucide-vue-next';

interface Product {
  id?: number;
  name: string;
  price: number;
  barcode: string; // 兼容老字段
  barcodes: string[]; // 🌟 商业版新字段：条码数组
}

const { toast } = useToast();

// 🌟 表单数据结构升级：barcodes 变成了数组
const productForm = ref({ barcodes: [] as string[], name: '', price: 0 });
const currentBarcode = ref(''); // 用来双向绑定当前正在输入的单个条码

const isSubmitting = ref(false);
const productList = ref<Product[]>([]);
const searchQuery = ref('');

const isEditDialogOpen = ref(false);
const isSavingEdit = ref(false);
const editForm = ref({ barcode: '', barcodes: [] as string[], name: '', price: 0 });

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

// --- 🌟 一品多码：动态添加条码标签 ---
const addBarcodeTag = () => {
  const bc = currentBarcode.value.trim();
  if (!bc) return;
  // 防止添加重复的条码
  if (productForm.value.barcodes.includes(bc)) {
    toast({ description: "该条码已在列表中", variant: "destructive" });
    currentBarcode.value = '';
    return;
  }
  productForm.value.barcodes.push(bc);
  currentBarcode.value = ''; // 清空输入框，准备扫下一个
};

// --- 🌟 一品多码：删除某个条码标签 ---
const removeBarcodeTag = (index: number) => {
  productForm.value.barcodes.splice(index, 1);
};

// --- 提交入库 ---
const handleSubmit = async () => {
  // 🌟 校验：现在要求必须至少录入一个条码标签
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
      toast({ title: "🎉 录入成功", description: `${productForm.value.name} 已加入数据库。` });
      // 清空表单
      productForm.value = { barcodes: [], name: '', price: 0 };
      fetchAllProducts();
    } else toast({ title: "录入失败", variant: "destructive" });
  } catch (error) {
    toast({ title: "网络错误", variant: "destructive" });
  } finally {
    isSubmitting.value = false;
  }
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
</script>

<template>
  <div class="min-h-screen bg-slate-50/50 p-6 md:p-10">
    <div class="max-w-7xl mx-auto flex flex-col lg:flex-row gap-8 items-start">

      <Card class="w-full lg:w-[380px] shadow-sm border-slate-200 sticky top-8 flex-shrink-0">
        <CardHeader class="bg-gradient-to-br from-slate-900 to-slate-800 text-white rounded-t-xl pb-8">
          <div class="flex items-center space-x-3 mb-2"><PackagePlus class="h-6 w-6 text-blue-400" /><CardTitle class="text-xl font-bold">商品入库</CardTitle></div>
          <CardDescription class="text-slate-400">支持一品多码，输入后按回车添加。</CardDescription>
        </CardHeader>
        <CardContent class="space-y-5 mt-6">

          <div class="space-y-3">
            <label class="text-sm font-semibold text-slate-700">物理条形码集合</label>

            <div class="flex flex-wrap gap-2" v-if="productForm.barcodes.length > 0">
              <div v-for="(bc, index) in productForm.barcodes" :key="index"
                   class="bg-blue-100 text-blue-800 px-3 py-1 rounded-md text-sm font-mono flex items-center shadow-sm border border-blue-200 animate-in zoom-in duration-200">
                {{ bc }}
                <button @click="removeBarcodeTag(index)" class="ml-2 text-blue-400 hover:text-red-500 transition-colors">
                  <X class="h-4 w-4" />
                </button>
              </div>
            </div>

            <div class="flex gap-2">
              <Input
                  v-model="currentBarcode"
                  @keydown.enter.prevent="addBarcodeTag"
                  class="font-mono bg-slate-50 flex-1"
                  placeholder="扫码或输入后按回车..."
              />
              <Button type="button" variant="secondary" @click="addBarcodeTag">添加</Button>
            </div>
          </div>

          <div class="space-y-2 border-t pt-4 mt-2">
            <label class="text-sm font-semibold text-slate-700">商品名称 (各口味统称)</label>
            <Input v-model="productForm.name" class="bg-slate-50" placeholder="例如: 农夫山泉 550ml" />
          </div>
          <div class="space-y-2">
            <label class="text-sm font-semibold text-slate-700">统一零售单价 (￥)</label>
            <Input v-model="productForm.price" type="number" step="0.01" class="font-mono text-lg bg-slate-50 text-blue-600 font-bold" />
          </div>
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
                <TableHead class="font-bold text-slate-700">商品名称</TableHead>
                <TableHead class="font-bold text-slate-700 w-[40%]">关联条码集合</TableHead>
                <TableHead class="font-bold text-slate-700 text-right">零售价</TableHead>
                <TableHead class="font-bold text-slate-700 text-right pr-6">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-if="filteredProducts.length === 0"><TableCell colspan="4" class="h-48 text-center text-slate-500">没有找到匹配的记录</TableCell></TableRow>

              <TableRow v-for="item in filteredProducts" :key="item.barcode" class="hover:bg-blue-50/50 transition-colors group">
                <TableCell class="font-medium text-slate-800 text-base">{{ item.name }}</TableCell>

                <TableCell>
                  <div class="flex flex-wrap gap-1.5">
                    <span v-for="bc in item.barcodes" :key="bc" class="bg-slate-100 border border-slate-200 text-slate-600 px-2 py-0.5 rounded text-xs font-mono">
                      {{ bc }}
                    </span>
                  </div>
                </TableCell>

                <TableCell class="text-right text-emerald-600 font-bold text-base">￥{{ item.price.toFixed(2) }}</TableCell>
                <TableCell class="text-right pr-4">
                  <div class="flex justify-end space-x-1 opacity-0 group-hover:opacity-100 transition-opacity duration-200">
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
        <DialogHeader>
          <DialogTitle>编辑商品信息</DialogTitle>
          <DialogDescription>目前仅支持修改主商品名称或统一价格，条码绑定关系暂不支持在此修改。</DialogDescription>
        </DialogHeader>
        <div class="space-y-4 py-4">
          <div class="space-y-2">
            <label class="text-sm font-semibold text-slate-700">关联条码</label>
            <div class="flex flex-wrap gap-1 mt-1">
              <span v-for="bc in editForm.barcodes" :key="bc" class="bg-slate-100 text-slate-500 px-2 py-1 rounded text-xs font-mono cursor-not-allowed">{{ bc }}</span>
            </div>
          </div>
          <div class="space-y-2"><label class="text-sm font-semibold text-slate-700">统一商品名称</label><Input v-model="editForm.name" /></div>
          <div class="space-y-2"><label class="text-sm font-semibold text-slate-700">统一单价 (￥)</label><Input v-model="editForm.price" type="number" step="0.01" class="text-blue-600 font-bold" /></div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="isEditDialogOpen = false">取消</Button>
          <Button class="bg-blue-600 hover:bg-blue-700" @click="submitEdit" :disabled="isSavingEdit">{{ isSavingEdit ? '保存中...' : '保存修改' }}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

  </div>
</template>