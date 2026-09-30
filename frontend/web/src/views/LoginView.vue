<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useMessage, type FormInst, type FormRules, NForm, NFormItem, NInput, NIcon } from 'naive-ui';
import { login } from '@/api/auth';
import { useUserStore } from '@/stores/user';
import { PersonOutline, LockClosedOutline, ArrowForwardOutline } from '@vicons/ionicons5';


const router = useRouter()
const route = useRoute()
const store = useUserStore()
const message = useMessage()
const formRef = ref<FormInst | null>(null)

const loginForm = reactive({
    username: '',
    password: '',
})

const rules: FormRules = {
    username: { required: true, message: 'Username is required', trigger: 'blur' },
    password: { required: true, message: 'Password is required', trigger: 'blur' }
}

const showForm = ref(true)
const loading = ref(false)

const handleLogin = async () => {
    formRef.value?.validate(async (errors) => {
        if (!errors) {
            loading.value = true
            try {
                const tokens = await login(loginForm)
                store.setTokens(tokens)
                await store.fetchUserInfo(false)
                message.success('Welcome back')
                router.push(typeof route.query.redirect === 'string' ? route.query.redirect : '/')
            } catch (error: any) {
                message.error(error.message || 'Login failed')
            } finally {
                loading.value = false
            }
        }
    })
}
</script>

<template>
    <div class="login-page" :class="{ 'loaded': showForm }">

        <div class="login-container animate-section" v-if="showForm">
            <div class="login-card animate-scale-in">
                <div class="header-section">
                    <router-link to="/" class="auth-brand">Para <i>BBS</i></router-link>
                    <h1 class="title">SIGN IN</h1>
                    <div class="decorative-line"></div>
                </div>
                
                <n-form ref="formRef" :model="loginForm" :rules="rules" class="login-form" @keyup.enter="handleLogin">
                    <n-form-item label="USERNAME" path="username">
                        <n-input v-model:value="loginForm.username" placeholder="Your username" class="custom-input">
                            <template #prefix>
                                <n-icon :component="PersonOutline" />
                            </template>
                        </n-input>
                    </n-form-item>
                    <n-form-item label="PASSWORD" path="password">
                        <n-input type="password" show-password-on="click" v-model:value="loginForm.password" placeholder="Your password" class="custom-input">
                            <template #prefix>
                                <n-icon :component="LockClosedOutline" />
                            </template>
                        </n-input>
                    </n-form-item>
                    
                    <div class="form-actions">
                        <button class="submit-btn" @click.prevent="handleLogin" :disabled="loading">
                            <span>{{ loading ? 'AUTHENTICATING...' : 'SIGN IN' }}</span>
                            <n-icon :component="ArrowForwardOutline" />
                        </button>
                        
                        <div class="divider-wrapper">
                            <span class="divider-line"></span>
                            <span class="divider-text">OR</span>
                            <span class="divider-line"></span>
                        </div>
                        
                        <div class="secondary-actions">
                            <button class="text-btn" @click.prevent="router.push('/register')">CREATE ACCOUNT</button>
                            <span class="dot-separator">•</span>
                            <button class="text-btn" @click.prevent="router.push('/')">RETURN HOME</button>
                        </div>
                    </div>
                </n-form>
            </div>
        </div>
    </div>
</template>

<style scoped src="../styles/auth.css"></style>
