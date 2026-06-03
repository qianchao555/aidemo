import { createRouter, createWebHistory } from 'vue-router'

function getUser(): { role?: string } | null {
  try {
    const raw = localStorage.getItem('currentUser')
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/agent/chat'
    },
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/auth/Login.vue'),
      meta: { title: '登录', noAuth: true }
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
      meta: { title: 'FAQ 列表', roles: ['admin'] }
    },
    {
      path: '/faq/high-freq',
      name: 'HighFreqFaq',
      component: () => import('@/views/faq/HighFreqFaq.vue'),
      meta: { title: '高频 FAQ', roles: ['admin'] }
    },
    {
      path: '/knowledge',
      name: 'KnowledgeBase',
      component: () => import('@/views/knowledge/KnowledgeBase.vue'),
      meta: { title: '知识库管理', roles: ['admin'] }
    }
  ]
})

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('authToken')

  if (to.meta.noAuth) {
    // 已登录用户访问登录页 → 跳转到聊天页
    if (token) {
      next('/agent/chat')
      return
    }
    next()
    return
  }

  if (!token) {
    next('/login')
    return
  }

  if (to.meta.roles) {
    const user = getUser()
    const requiredRoles = to.meta.roles as string[]
    if (!user || !user.role || !requiredRoles.includes(user.role)) {
      next('/agent/chat')
      return
    }
  }

  next()
})

export default router
