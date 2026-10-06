import { createRouter, createWebHistory, type RouteRecordRaw } from "vue-router";
import { useUserStore } from "../stores/user";
import MainLayout from '@/layouts/MainLayout.vue'
import { ROUTE_NAMES } from './contracts'

// 路由配置数组
const routes: Array<RouteRecordRaw> = [
    {
        path: '/',
        component: MainLayout,
        children: [
            {
                path: '',
                name: ROUTE_NAMES.index,
                component: () => import('@/views/IndexView.vue'),
                meta: {
                    requiresAuth: false,
                    title: 'Para BBS | 彼记',
                }
            },
            {
                path: 'blog',
                name: ROUTE_NAMES.blogList,
                component: () => import('@/views/BlogListView.vue'),
                meta: {
                    requiresAuth: false,
                    title: '博客列表 | Para BBS',
                }
            },
            {
                path: 'blog/:id',
                name: ROUTE_NAMES.blogPublicDetail,
                component: () => import('@/views/BlogPublicDetailView.vue'),
                meta: {
                    requiresAuth: false,
                    title: '博客详情 | Para BBS',
                    fullBleed: true,
                }
            },
            {
                path: 'workspace',
                name: ROUTE_NAMES.workspace,
                component: () => import('@/views/WorkspaceView.vue'),
                meta: {
                    requiresAuth: true,
                    roles: ['ROLE_USER'],
                    title: '工作台 | Para BBS',
                }
            },
            {
                path: 'workspace/blogs/:id',
                name: ROUTE_NAMES.workspaceBlog,
                component: () => import('@/views/BlogPrivateDetailView.vue'),
                meta: {
                    requiresAuth: true,
                    roles: ['ROLE_USER'],
                    title: '编辑博客 | Para BBS',
                }
            },
            {
                path: 'admin',
                name: ROUTE_NAMES.admin,
                component: () => import('@/views/AdminView.vue'),
                meta: {
                    requiresAuth: true,
                    roles: ['ROLE_ADMIN'],
                    title: '后台管理 | Para BBS',
                },
            },
            {
                path: ':uid',
                name: ROUTE_NAMES.userProfile,
                component: () => import('@/views/UserProfile.vue'),
                meta: {
                    requiresAuth: false,
                    title: '个人主页 | Para BBS',
                    fullBleed: true,
                }
            }
        ]
    },
    {
        path: '/login/callback',
        name: 'SsoCallback',
        component: () => import('@/views/SsoCallbackView.vue'),
        meta: { requiresAuth: false, title: '正在登录' },
    },
    // 登录页面
    {
        path: '/login',
        name: ROUTE_NAMES.login,
        component: () => import('@/views/LoginView.vue'),
        meta: {
            requiresAuth: false,
            title: '登录',
        },
    },
    // 注册页面
    {
        path: '/register',
        name: ROUTE_NAMES.register,
        component: () => import('@/views/RegisterView.vue'),
        meta: {
            requiresAuth: false,
            title: '注册',
        },
    },
    {
        path: '/admin/login',
        name: ROUTE_NAMES.adminLogin,
        component: () => import('@/views/AdminLoginView.vue'),
        meta: {
            requiresAuth: false,
            title: '后台登录 | Para BBS',
        },
    },
    // 404页面
    {
        path: '/:pathMatch(.*)*',
        name: ROUTE_NAMES.notFound,
        component: () => import('@/views/NotFoundView.vue'),
        meta: {
            requiresAuth: false,
            title: '页面不存在',
        },
    },
]

// 创建路由实例
const router = createRouter({
    history: createWebHistory('/bbs'),
    routes,
})

// 路由守卫
router.beforeEach(async (to, _, next) => {
    const userStore = useUserStore()
    const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)
    const requiredRoles = to.matched.flatMap((record) => record.meta.roles ?? [])

    // 设置页面标题
    if (to.meta.title) {
        document.title = to.meta.title as string
    }

    // 如果有 token 但没有用户信息，说明是刷新页面，需要重新获取用户信息。
    // 公共页面不能因为 /users/me 暂时失败就清空刚登录写入的 token。
    if (to.name !== 'SsoCallback' && userStore.token && !userStore.userInfo) {
        try {
            await userStore.fetchUserInfo(requiresAuth)
        } catch (error) {
            console.error('Failed to fetch user info:', error)
        }
    }

    if (requiresAuth && !userStore.isAuthenticated) {
        next({ path: to.path.startsWith('/admin') ? '/admin/login' : '/login', query: { redirect: to.fullPath } })
    } else if (requiredRoles.length > 0 && !userStore.hasAnyRole(requiredRoles)) {
        next(to.path.startsWith('/admin') ? '/admin/login' : '/')
    } else {
        next()
    }
})

export default router
