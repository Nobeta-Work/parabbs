import defaultAvatarUrl from '@/assets/images/default-avatar.png'

export const DEFAULT_AVATAR_URL = defaultAvatarUrl

export const resolveAvatarUrl = (avatarUrl?: string | null) =>
  avatarUrl?.trim() || DEFAULT_AVATAR_URL
