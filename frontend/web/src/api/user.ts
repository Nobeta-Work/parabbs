import { openAccountPage } from '@/api/auth'
import type {
    AvatarVO,
    UserInfo,
    UserInfoVO,
    UserProfileDTO,
    UserProfileVO,
} from '@/types'
import request from '@/utils/request'

function toNumberId(id: number | string | null | undefined): number {
    if (id === null || id === undefined) {
        return 0
    }

    return Number(id)
}

function toUserInfo(
    user: UserInfoVO | UserProfileVO,
    token: string | null = null,
    refreshToken: string | null = null,
    tokenExpireIn: string | null = null,
): UserInfo {
    return {
        id: toNumberId(user.id),
        username: 'username' in user ? user.username : null,
        nickname: user.nickname,
        avatarUrl: user.avatarUrl,
        backgroundImageUrl: user.backgroundImageUrl,
        token,
        refreshToken,
        tokenExpireIn,
        sex: user.sex,
        race: user.race,
        signature: user.signature,
        roles: 'roles' in user ? user.roles : [],
        createTime: user.createTime,
    }
}

export function getCurrentUserProfile(): Promise<UserProfileVO> {
    return request<UserProfileVO>({
        url: '/users/me',
        method: 'get',
    })
}

export function getUserInfo(id: number | string): Promise<UserInfoVO> {
    return request<UserInfoVO>({
        url: `/users/${id}`,
        method: 'get',
    })
}

export function updateCurrentUserProfile(data: UserProfileDTO): Promise<void> {
    return request<void>({
        url: '/users/me',
        method: 'put',
        data,
    })
}

export function openPasswordSettings(): Promise<void> {
    return openAccountPage('password')
}

export function updateCurrentUserAvatar(file: File): Promise<AvatarVO> {
    const formData = new FormData()
    formData.append('file', file)

    return request<AvatarVO>({
        url: '/users/me/avatar',
        method: 'post',
        data: formData,
    })
}

export async function getCurrentUserInfo(): Promise<UserInfo> {
    const profile = await getCurrentUserProfile()
    return toUserInfo(profile)
}
