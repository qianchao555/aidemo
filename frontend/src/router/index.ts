import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/agent/chat'
    },
    {
      path: '/agent/chat',
      name: 'Chat',
      component: () => import('@/views/agent/ChatView.vue'),
      meta: { title: '智能问答' }
    },
    {
      path: '/faq/list',
      name: 'FaqList',
      component: () => import('@/views/faq/FaqList.vue'),
      meta: { title: 'FAQ 列表' }
    },
    {
      path: '/faq/high-freq',
      name: 'HighFreqFaq',
      component: () => import('@/views/faq/HighFreqFaq.vue'),
      meta: { title: '高频 FAQ' }
    },
    {
      path: '/knowledge',
      name: 'KnowledgeBase',
      component: () => import('@/views/knowledge/KnowledgeBase.vue'),
      meta: { title: '知识库管理' }
    }
  ]
})

export default router
