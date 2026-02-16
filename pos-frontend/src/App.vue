<script setup lang="ts">
// 🌟 1. 引入全局的 Toaster 容器
import Toaster from '@/components/ui/toast/Toaster.vue';
</script>

<template>
  <div class="min-h-screen bg-slate-50 text-slate-900">
    <nav class="bg-white shadow-sm border-b px-6 py-4 flex justify-between items-center">
      <div class="text-xl font-black tracking-tighter">lkPOS <span class="text-blue-600">System</span></div>
      <div class="space-x-4 flex items-center">
        <router-link to="/" class="px-4 py-2 rounded-md hover:bg-slate-100 font-medium transition-colors">🛒 前台收银</router-link>
        <div class="h-6 w-px bg-slate-200 mx-2"></div>
        <router-link to="/admin" class="px-4 py-2 rounded-md hover:bg-slate-100 font-medium transition-colors">📦 商品管理</router-link>
        <router-link to="/orders" class="px-4 py-2 rounded-md hover:bg-slate-100 font-medium transition-colors">📊 销售报表</router-link>
      </div>
    </nav>

    <main class="container mx-auto">
      <router-view />
    </main>

    <Toaster />
  </div>
</template>

<style>
.router-link-active {
  color: #2563eb;
  background-color: #eff6ff;
}
</style>