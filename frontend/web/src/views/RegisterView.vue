<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useMessage, type FormInst, type FormRules, NForm, NFormItem, NInput, NIcon, NRadioGroup, NRadio } from 'naive-ui';
import { PersonOutline, LockClosedOutline, FlagOutline, GridOutline, ArrowForwardOutline } from '@vicons/ionicons5';
import { register } from '@/api/user';


const router = useRouter()
const message = useMessage()
const formRef = ref<FormInst | null>(null)
const loading = ref(false)

const registerForm = reactive({
    nickname: '',
    username: '',
    password: '',
    password2: '',
    sex: 2,
    race: ''
})

const rules: FormRules = {
    nickname: [
        { required: true, message: 'Nickname is required', trigger: 'blur' },
        { min: 1, max: 10, message: 'Nickname must be 1–10 characters', trigger: 'blur' }
    ],
    username: [
        { required: true, message: 'Username is required', trigger: 'blur' },
        {
            pattern: /^[a-zA-Z0-9!@#._-]{4,20}$/,
            message: 'Use 4–20 letters, numbers, or ! @ # . _ -',
            trigger: ['blur', 'input']
        }
    ],
    password: [
        { required: true, message: 'Password is required', trigger: 'blur' },
        {
            pattern: /^[a-zA-Z0-9!@#._-]{4,20}$/,
            message: 'Use 4–20 letters, numbers, or ! @ # . _ -',
            trigger: ['blur', 'input']
        }
    ],
    password2: {
        required: true,
        validator: (_, value) => {
            if (!value) return new Error('Please confirm your password')
            if (value !== registerForm.password) return new Error('Passwords do not match')
            return true
        },
        trigger: ['blur', 'input']
    },
    sex: {
        required: true,
        type: 'number',
        min: 0,
        max: 2,
        message: 'Please choose an identity',
        trigger: 'change'
    },
    race: [
        { required: true, message: 'Race is required', trigger: 'blur' },
        { min: 1, max: 10, message: 'Race must be 1–10 characters', trigger: 'blur' }
    ]
};

const showForm = ref(true);

const handleRegister = () => {
    formRef.value?.validate(async (errors) => {
        if (!errors) {
            loading.value = true;
            try {
                await register({
                    nickname: registerForm.nickname,
                    username: registerForm.username,
                    password: registerForm.password,
                    sex: registerForm.sex,
                    race: registerForm.race
                });
                message.success('Registration successful, please sign in');
                router.push('/login');
            } catch (error: any) {
                message.error(error.message || 'Registration failed');
            } finally {
                loading.value = false;
            }
        }
    });
};
</script>

<template>
    <div class="register-page" :class="{ 'loaded': showForm }">

        <div class="register-container animate-section" v-if="showForm">
            <div class="register-card animate-scale-in">
                <div class="header-section">
                    <router-link to="/" class="auth-brand">Para <i>BBS</i></router-link>
                    <h1 class="title">JOIN US</h1>
                    <div class="decorative-line"></div>
                </div>
                
                <n-form ref="formRef" :model="registerForm" :rules="rules" class="register-form" @keyup.enter="handleRegister">
                    <div class="fields-grid">
                        <n-form-item path="nickname">
                            <template #label>
                                <span class="field-label"><span>NICKNAME</span><small>1–10 characters</small></span>
                            </template>
                            <n-input v-model:value="registerForm.nickname" maxlength="10" placeholder="Your persona name" class="custom-input">
                                <template #prefix><n-icon :component="PersonOutline" /></template>
                            </n-input>
                        </n-form-item>
                        
                        <n-form-item path="username">
                            <template #label>
                                <span class="field-label"><span>USERNAME</span><small>4–20 · A–Z, 0–9, ! @ # . _ -</small></span>
                            </template>
                            <n-input v-model:value="registerForm.username" maxlength="20" placeholder="Your login ID" class="custom-input">
                                <template #prefix><n-icon :component="GridOutline" /></template>
                            </n-input>
                        </n-form-item>
                        
                        <n-form-item path="password">
                            <template #label>
                                <span class="field-label"><span>PASSWORD</span><small>4–20 · A–Z, 0–9, ! @ # . _ -</small></span>
                            </template>
                            <n-input type="password" show-password-on="click" v-model:value="registerForm.password" maxlength="20" placeholder="Create password" class="custom-input">
                                <template #prefix><n-icon :component="LockClosedOutline" /></template>
                            </n-input>
                        </n-form-item>
                        
                        <n-form-item path="password2">
                            <template #label>
                                <span class="field-label"><span>CONFIRM PASSWORD</span><small>Must match password</small></span>
                            </template>
                            <n-input type="password" show-password-on="click" v-model:value="registerForm.password2" placeholder="Confirm password" class="custom-input">
                                <template #prefix><n-icon :component="LockClosedOutline" /></template>
                            </n-input>
                        </n-form-item>
                        
                        <n-form-item path="sex" class="full-width">
                            <template #label>
                                <span class="field-label"><span>IDENTITY</span><small>Choose one</small></span>
                            </template>
                            <n-radio-group v-model:value="registerForm.sex" name="sex" class="custom-radio-group">
                                <n-radio :value="1">MALE</n-radio>
                                <n-radio :value="0">FEMALE</n-radio>
                                <n-radio :value="2">MYSTERY</n-radio>
                            </n-radio-group>
                        </n-form-item>
                        
                        <n-form-item path="race" class="full-width">
                            <template #label>
                                <span class="field-label"><span>RACE</span><small>1–10 characters</small></span>
                            </template>
                            <n-input v-model:value="registerForm.race" maxlength="10" placeholder="e.g. Human, Elf, Cyborg..." class="custom-input">
                                <template #prefix><n-icon :component="FlagOutline" /></template>
                            </n-input>
                        </n-form-item>
                    </div>
                    
                    <div class="form-actions">
                        <button class="submit-btn" @click.prevent="handleRegister" :disabled="loading">
                            <span>{{ loading ? 'CREATING...' : 'CREATE ACCOUNT' }}</span>
                            <n-icon :component="ArrowForwardOutline" />
                        </button>
                        
                        <div class="divider-wrapper">
                            <span class="divider-line"></span>
                            <span class="divider-text">OR</span>
                            <span class="divider-line"></span>
                        </div>
                        
                        <div class="secondary-actions">
                            <button class="text-btn" @click.prevent="router.push('/login')">ALREADY HAVE AN ACCOUNT?</button>
                            <span class="dot-separator">?</span>
                            <button class="text-btn" @click.prevent="router.push('/')">RETURN HOME</button>
                        </div>
                    </div>
                </n-form>
            </div>
        </div>
    </div>
</template>

<style scoped src="../styles/auth.css"></style>
