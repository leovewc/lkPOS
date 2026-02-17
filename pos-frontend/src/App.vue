<script setup lang="ts">
// 🌟 1. 引入全局的 Toaster 容器
import Toaster from '@/components/ui/toast/Toaster.vue';
</script>

<template>
  <div class="min-h-screen bg-slate-50 text-slate-900">
    <nav class="bg-white shadow-sm border-b px-6 py-4 flex justify-between items-center sticky top-0 z-50">
      <div class="text-xl font-black tracking-tighter flex items-center gap-2">
        <span class="text-2xl">🏪</span>
        lkPOS <span class="text-blue-600">System</span>
      </div>

      <div class="flex items-center space-x-1 md:space-x-2">
        <router-link to="/" class="px-4 py-2 rounded-md font-medium transition-all hover:bg-slate-100 flex items-center gap-2">
          🛒 前台收银
        </router-link>

        <div class="h-6 w-px bg-slate-200 mx-2"></div>

        <router-link to="/admin" class="px-4 py-2 rounded-md font-medium transition-all hover:bg-slate-100 flex items-center gap-2">
          📦 商品管理
        </router-link>

        <router-link to="/orders" class="px-4 py-2 rounded-md font-medium transition-all hover:bg-slate-100 flex items-center gap-2">
          🧾 交易流水
        </router-link>

        <router-link to="/dashboard" class="px-4 py-2 rounded-md font-bold transition-all hover:bg-emerald-50 hover:text-emerald-600 flex items-center gap-2">
          📈 商业大盘
        </router-link>
      </div>
    </nav>

    <main class="container mx-auto pb-10">
      <router-view />
    </main>

    <Toaster />
  </div>
</template>

<style>
/* 路由激活时的默认高亮样式 */
.router-link-active {
  color: #2563eb;
  background-color: #eff6ff;
}

/* 针对大盘入口激活时的特殊高亮（老板专属翡翠绿） */
a[href="/dashboard"].router-link-active {
  color: #059669; /* emerald-600 */
  background-color: #ecfdf5; /* emerald-50 */
}
</style>