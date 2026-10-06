<script setup lang="ts">
import { ref } from 'vue'
import { openAccountPage, startSsoLogin } from '@/api/auth'

const error = ref('')
const loading = ref(false)
async function register(): Promise<void> {
    loading.value = true
    error.value = ''
    try { await openAccountPage('register') }
    catch { error.value = '无法打开账号注册页面，请重试'; loading.value = false }
}
</script>

<template>
    <div class="register-page loaded">
        <div class="register-container">
            <div class="register-card">
                <div class="header-section">
                    <router-link to="/" class="auth-brand">Para <i>BBS</i></router-link>
                    <h1 class="title">创建 BBS 账号</h1>
                    <div class="decorative-line"></div>
                </div>
                <p>注册后返回社区登录，昵称等社区资料可以在个人页面完善。</p>
                <p v-if="error" role="alert">{{ error }}</p>
                <div class="form-actions">
                    <button type="button" class="submit-btn" :disabled="loading" @click="register">前往注册</button>
                    <div class="secondary-actions">
                        <button type="button" class="text-btn" @click="startSsoLogin()">已有账号，继续登录</button>
                        <router-link to="/" class="text-btn">返回首页</router-link>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<style scoped src="../styles/auth.css"></style>
