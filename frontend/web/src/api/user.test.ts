import { afterEach, describe, expect, it, vi } from 'vitest'
import { getCurrentUserInfo } from './user'
import request from '@/utils/request'
import { DEFAULT_AVATAR_URL, resolveAvatarUrl } from '@/utils/avatar'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

afterEach(() => vi.clearAllMocks())

describe('current user avatar contract', () => {
  it('preserves avatarUrl from the backend through the user adapter and renderer', async () => {
    const avatarUrl = 'https://nobeta.cn/bbs/i/2026/8/20/adcb821636fe4c8d9f56b23c470d4a61.png'
    vi.mocked(request).mockResolvedValueOnce({
      id: '1', username: 'user', nickname: 'User', avatarUrl,
      backgroundImageUrl: null, sex: 1, race: '', signature: '', roles: [],
      createTime: '2025-10-09 04:24:36',
    })
    const user = await getCurrentUserInfo()
    expect(user.avatarUrl).toBe(avatarUrl)
    expect(resolveAvatarUrl(user.avatarUrl)).toBe(avatarUrl)
    expect(user).not.toHaveProperty('avatar')
  })

  it('uses the default avatar when the backend returns null', () => {
    expect(resolveAvatarUrl(null)).toBe(DEFAULT_AVATAR_URL)
  })
})
