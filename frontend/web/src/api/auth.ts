import type { ApiResponse, TokenVO } from '@/types'
import { API_SUCCESS_CODE, LEGACY_API_SUCCESS_CODE } from '@/types'
import request from '@/utils/request'

interface CsrfToken { headerName: string; parameterName: string; token: string }
export interface SsoLoginResult { tokens: TokenVO; returnTo: string }
export interface IdentityProvider { loginUrl: string; registerUrl: string; passwordUrl: string }

function apiBase(): string {
    // OIDC 会话 Cookie 限定在此真实路径，不能改用业务 API 的 /api 代理别名。
    return '/bbs/api'
}

// 登录交接不使用业务 JWT 拦截器，避免旧账号令牌或自动刷新介入新登录。
async function sessionRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
    const response = await fetch(apiBase() + path, {
        ...options, credentials: 'same-origin', cache: 'no-store',
        headers: { Accept: 'application/json', ...options.headers },
    })
    if (!response.ok) throw new Error('登录交接失败，请重新登录')
    const payload = await response.json() as ApiResponse<T>
    if (payload.code !== API_SUCCESS_CODE && payload.code !== LEGACY_API_SUCCESS_CODE) {
        throw new Error(payload.msg || payload.message || '登录失败')
    }
    return payload.data as T
}

export function startSsoLogin(returnTo = '/', admin = false): void {
    const url = new URL(apiBase() + '/auth/sso/authorize/auth', window.location.origin)
    url.searchParams.set('returnTo', returnTo)
    if (admin) url.searchParams.set('admin', 'true')
    window.location.assign(url.href)
}

export function getIdentityProvider(): Promise<IdentityProvider> {
    return sessionRequest<IdentityProvider>('/auth/identity-provider')
}

export async function openAccountPage(page: 'register' | 'password'): Promise<void> {
    const provider = await getIdentityProvider()
    window.location.assign(page === 'register' ? provider.registerUrl : provider.passwordUrl)
}

export async function finishSsoLogin(): Promise<SsoLoginResult> {
    const csrf = await sessionRequest<CsrfToken>('/auth/sso/csrf')
    return sessionRequest<SsoLoginResult>('/auth/sso/session', {
        method: 'POST', headers: { [csrf.headerName]: csrf.token },
    })
}

export function refresh(refreshToken: string): Promise<TokenVO> {
    return request<TokenVO>({
        url: '/auth/refresh',
        method: 'post',
        params: { refreshToken },
    })
}

export function logout(): Promise<void> {
    return request<void>({
        url: '/auth/logout',
        method: 'post',
    })
}
