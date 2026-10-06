<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { startSsoLogin } from '@/api/auth'

const route = useRoute()
const returnTo = computed(() => typeof route.query.redirect === 'string' ? route.query.redirect : '/')
</script>

<template>
    <div class="login-page loaded">
        <div class="login-container">
            <div class="login-card">
                <div class="header-section">
                    <router-link to="/" class="auth-brand">Para <i>BBS</i></router-link>
                    <h1 class="title">登录社区</h1>
                    <div class="decorative-line"></div>
                </div>
                <p>使用你的 BBS 账号登录。</p>
                <div class="form-actions">
                    <button type="button" class="submit-btn" @click="startSsoLogin(returnTo)">继续登录</button>
                    <div class="secondary-actions">
                        <router-link to="/register" class="text-btn">创建账号</router-link>
                        <router-link to="/" class="text-btn">返回首页</router-link>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<style scoped src="../styles/auth.css"></style>
