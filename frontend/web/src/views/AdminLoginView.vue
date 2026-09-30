<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NForm, NFormItem, NIcon, NInput, useMessage, type FormInst, type FormRules } from 'naive-ui'
import { ArrowBackOutline, KeyOutline, PersonOutline, ShieldCheckmarkOutline } from '@vicons/ionicons5'
import { adminLogin } from '@/api/admin'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const userStore = useUserStore()
const formRef = ref<FormInst | null>(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: { required: true, message: '请输入管理员账号', trigger: 'blur' },
  password: { required: true, message: '请输入密码', trigger: 'blur' },
}

async function submit(): Promise<void> {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    const tokens = await adminLogin(form)
    userStore.setTokens(tokens)
    await userStore.fetchUserInfo(true)
    message.success('管理员登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/admin'
    router.push(redirect)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '管理员登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="admin-login-page">
    <section class="admin-login-card">
      <button class="back-link" type="button" @click="router.push('/')">
        <n-icon :component="ArrowBackOutline" />
        返回前台
      </button>

      <div class="login-mark">
        <n-icon :component="ShieldCheckmarkOutline" />
      </div>
      <p class="eyebrow">Para BBS / Control Room</p>
      <h1>后台管理</h1>
      <p class="intro">使用管理员账号进入社区控制台。</p>

      <n-form ref="formRef" :model="form" :rules="rules" @keyup.enter="submit">
        <n-form-item label="管理员账号" path="username">
          <n-input v-model:value="form.username" placeholder="输入账号">
            <template #prefix><n-icon :component="PersonOutline" /></template>
          </n-input>
        </n-form-item>
        <n-form-item label="密码" path="password">
          <n-input v-model:value="form.password" type="password" show-password-on="click" placeholder="输入密码">
            <template #prefix><n-icon :component="KeyOutline" /></template>
          </n-input>
        </n-form-item>
        <n-button type="primary" block :loading="loading" @click="submit">
          进入控制台
        </n-button>
      </n-form>
    </section>
  </main>
</template>

<style scoped>

.admin-login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    var(--bg-secondary);
  color: var(--text-primary);
}

.admin-login-card {
  width: min(100%, 430px);
  padding: 38px;
  border: 1px solid var(--line-color);
  background: color-mix(in srgb, var(--bg-primary) 88%, transparent);
  box-shadow: none;
  border-radius: 28px;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  font: inherit;
}

.back-link:hover {
  color: var(--accent-color);
}

.login-mark {
  display: grid;
  width: 58px;
  height: 58px;
  margin: 48px 0 22px;
  place-items: center;
  border: 1px solid var(--accent-color);
  border-radius: 2px;
  color: var(--accent-color);
  font-size: 28px;
}

.eyebrow {
  margin: 0 0 9px;
  color: var(--accent-highlight);
  font-size: 0.76rem;
  letter-spacing: 0;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  font-size: 28px;
  line-height: 1.1;
}

.intro {
  margin: 14px 0 32px;
  color: var(--text-secondary);
  line-height: 1.7;
}

@media (max-width: 520px) {
  .admin-login-card {
    padding: 28px 22px;
    border-radius: 28px;
  }
}

</style>
