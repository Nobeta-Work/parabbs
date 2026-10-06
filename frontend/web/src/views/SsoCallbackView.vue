<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { finishSsoLogin } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const error = ref('')

onMounted(async () => {
    if (route.query.error) {
        error.value = route.query.error === 'forbidden' ? '当前账号没有社区管理员权限。' : '登录失败，请重新登录。'
        return
    }
    try {
        const result = await finishSsoLogin()
        store.logout()
        store.setTokens(result.tokens)
        if (!await store.fetchUserInfo(false)) throw new Error('无法加载社区资料，请重试')
        const target = result.returnTo.startsWith('/') && !result.returnTo.startsWith('//')
            && !result.returnTo.includes('\\') ? result.returnTo : '/'
        await router.replace(target)
    } catch (cause) {
        error.value = cause instanceof Error ? cause.message : '登录失败，请重试'
    }
})
</script>

<template>
    <main class="login-page loaded">
        <section class="login-container">
            <div class="login-card">
                <h1>登录社区</h1>
                <p v-if="error" role="alert">{{ error }}</p>
                <p v-else role="status">正在完成登录…</p>
                <router-link v-if="error" to="/login" class="submit-btn">重新登录</router-link>
                <router-link to="/">返回首页</router-link>
            </div>
        </section>
    </main>
</template>

<style scoped src="../styles/auth.css"></style>
