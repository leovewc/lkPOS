import { createRouter, createWebHistory } from 'vue-router'
import Cashier from '@/views/Cashier.vue'
import AdminAddProduct from '@/views/AdminAddProduct.vue'
import AdminOrders from '@/views/AdminOrders.vue'

const router = createRouter({
    history: createWebHistory(),
    routes: [
        {
            path: '/',            // 根路径，默认打开就是收银台
            name: 'Cashier',
            component: Cashier
        },
        {
            path: '/admin',       // 后台管理路径
            name: 'Admin',
            component: AdminAddProduct
        },
        {
            path: '/orders',
            name: 'Orders',
            component: AdminOrders
        }
    ]
})

export default router