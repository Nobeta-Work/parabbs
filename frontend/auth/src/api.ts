export interface CsrfToken { headerName: string; parameterName: string; token: string }
export interface Account {
  id: number
  subject: string
  username: string
  enabled: boolean
  createTime: string
  updateTime: string
}

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message) }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  let response: Response
  try {
    response = await fetch(`/auth/api${path}`, {
      ...options,
      credentials: 'same-origin',
      headers: { Accept: 'application/json', ...options.headers },
    })
  } catch {
    throw new ApiError(0, '无法连接认证服务，请检查网络后重试。')
  }
  if (!response.ok) {
    // Both ProblemDetail and Boot /error are possible; user-facing messages use status.
    throw new ApiError(response.status, response.status >= 500
      ? '认证服务暂时不可用，请稍后重试。' : '请求未完成，请检查填写内容后重试。')
  }
  if (response.status === 204) return undefined as T
  try { return await response.json() as T }
  catch { throw new ApiError(502, '认证服务返回异常，请稍后重试。') }
}

export const getCsrf = () => request<CsrfToken>('/csrf', { cache: 'no-store' })
export const getAccount = () => request<Account>('/accounts/me', { cache: 'no-store' })

async function write(path: string, method: string, data: unknown) {
  // Fetch immediately before each write so a login or another tab cannot leave a stale token.
  const csrf = await getCsrf()
  return request<Account | undefined>(path, {
    method,
    headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    body: JSON.stringify(data),
  })
}

export const registerAccount = (username: string, password: string) => write('/accounts/register', 'POST', { username, password })
export const changePassword = (currentPassword: string, newPassword: string) => write('/accounts/me/password', 'PUT', { currentPassword, newPassword })

export function validatePassword(password: string): string | undefined {
  if (password.trim().length === 0 || password.length < 12) return '密码至少需要 12 个字符。'
  if (new TextEncoder().encode(password).length > 72) return '密码不能超过 72 个 UTF-8 字节，请缩短密码。'
}
