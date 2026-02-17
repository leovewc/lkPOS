<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { Card, CardContent } from '@/components/ui/card';
import { Wallet, TrendingUp, ShoppingBag, Package, Crown } from 'lucide-vue-next';
import { useToast } from '@/components/ui/toast/use-toast';

const { toast } = useToast();
const stats = ref({
  today: { revenue: 0, cost: 0, profit: 0, orderCount: 0 },
  topProducts: [] as any[]
});
const isLoading = ref(true);

const fetchDashboardData = async () => {
  try {
    const response = await fetch('http://localhost:8080/api/stats/dashboard');
    if (response.ok) {
      stats.value = await response.json();
    }
  } catch (error) {
    toast({ title: "大盘数据加载失败", variant: "destructive" });
  } finally {
    isLoading.value = false;
  }
};

onMounted(() => {
  fetchDashboardData();
});
</script>

<template>
  <div class="min-h-screen bg-slate-100 p-6 md:p-10">
    <div class="max-w-7xl mx-auto space-y-8">

      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-3xl font-black text-slate-800 tracking-tight">商业洞察大盘</h1>
          <p class="text-slate-500 mt-1">实时监控门店营业状况与盈利能力</p>
        </div>
        <div class="bg-blue-100 text-blue-700 px-4 py-2 rounded-full font-bold text-sm animate-pulse">
          🟢 数据实时同步中
        </div>
      </div>

      <div v-if="isLoading" class="text-center py-20 text-slate-400">正在计算复杂报表...</div>

      <div v-else class="space-y-8">

        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">

          <Card class="border-0 shadow-lg shadow-blue-100/50 bg-gradient-to-br from-blue-500 to-indigo-600 text-white transform hover:-translate-y-1 transition-transform">
            <CardContent class="p-6">
              <div class="flex justify-between items-start">
                <div>
                  <p class="text-blue-100 text-sm font-semibold mb-1">今日总营业额</p>
                  <h3 class="text-4xl font-black">￥{{ stats.today.revenue.toFixed(2) }}</h3>
                </div>
                <div class="p-3 bg-white/20 rounded-xl backdrop-blur-sm"><Wallet class="h-6 w-6 text-white" /></div>
              </div>
            </CardContent>
          </Card>

          <Card class="border-0 shadow-lg shadow-emerald-100/50 bg-gradient-to-br from-emerald-500 to-teal-600 text-white transform hover:-translate-y-1 transition-transform">
            <CardContent class="p-6">
              <div class="flex justify-between items-start">
                <div>
                  <p class="text-emerald-100 text-sm font-semibold mb-1">今日净利润 (毛利)</p>
                  <h3 class="text-4xl font-black">￥{{ stats.today.profit.toFixed(2) }}</h3>
                </div>
                <div class="p-3 bg-white/20 rounded-xl backdrop-blur-sm"><TrendingUp class="h-6 w-6 text-white" /></div>
              </div>
              <div class="mt-4 text-sm text-emerald-100 flex items-center gap-2">
                <span class="bg-white/20 px-2 py-0.5 rounded text-xs font-bold">
                  毛利率 {{ stats.today.revenue > 0 ? ((stats.today.profit / stats.today.revenue) * 100).toFixed(1) : 0 }}%
                </span>
              </div>
            </CardContent>
          </Card>

          <Card class="border-0 shadow-sm bg-white">
            <CardContent class="p-6">
              <div class="flex justify-between items-start">
                <div>
                  <p class="text-slate-400 text-sm font-semibold mb-1">今日成交单数</p>
                  <h3 class="text-4xl font-black text-slate-800">{{ stats.today.orderCount }} <span class="text-base font-normal text-slate-400">单</span></h3>
                </div>
                <div class="p-3 bg-slate-50 rounded-xl"><ShoppingBag class="h-6 w-6 text-slate-400" /></div>
              </div>
            </CardContent>
          </Card>

          <Card class="border-0 shadow-sm bg-white">
            <CardContent class="p-6">
              <div class="flex justify-between items-start">
                <div>
                  <p class="text-slate-400 text-sm font-semibold mb-1">今日进货成本支出</p>
                  <h3 class="text-4xl font-black text-slate-800">￥{{ stats.today.cost.toFixed(2) }}</h3>
                </div>
                <div class="p-3 bg-orange-50 rounded-xl"><Package class="h-6 w-6 text-orange-400" /></div>
              </div>
            </CardContent>
          </Card>
        </div>

        <div class="bg-white rounded-2xl shadow-sm border border-slate-100 overflow-hidden">
          <div class="p-6 border-b border-slate-100 flex items-center gap-3">
            <Crown class="h-6 w-6 text-amber-500" />
            <h2 class="text-xl font-bold text-slate-800">历史热销单品 TOP 5</h2>
          </div>

          <div class="p-6">
            <div v-if="stats.topProducts.length === 0" class="text-center py-10 text-slate-400">暂无销售数据，快去收银台开单吧！</div>

            <div v-else class="space-y-6">
              <div v-for="(item, index) in stats.topProducts" :key="index" class="flex items-center gap-6 group">

                <div class="w-8 text-center font-black text-2xl" :class="index < 3 ? 'text-amber-500' : 'text-slate-300'">
                  {{ index + 1 }}
                </div>

                <div class="h-14 w-14 rounded-lg border bg-white flex items-center justify-center p-1 overflow-hidden flex-shrink-0">
                  <img v-if="item.imageUrl" :src="'http://localhost:8080' + item.imageUrl" class="object-contain h-full w-full group-hover:scale-110 transition-transform" />
                  <span v-else class="text-[10px] text-slate-400">无图</span>
                </div>

                <div class="flex-1">
                  <div class="flex justify-between mb-2">
                    <span class="font-bold text-slate-800">{{ item.name }}</span>
                    <span class="text-sm font-bold text-slate-600">共售出 <span class="text-emerald-600 text-lg">{{ item.totalSold }}</span> 件</span>
                  </div>
                  <div class="h-3 bg-slate-100 rounded-full overflow-hidden">
                    <div class="h-full bg-gradient-to-r from-emerald-400 to-teal-500 rounded-full transition-all duration-1000"
                         :style="{ width: `${(item.totalSold / stats.topProducts[0].totalSold) * 100}%` }">
                    </div>
                  </div>
                  <div class="mt-1 text-xs text-slate-400 text-right">
                    该单品累计贡献净利：<span class="text-emerald-500 font-bold">￥{{ item.totalProfit.toFixed(2) }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

      </div>
    </div>
  </div>
</template>