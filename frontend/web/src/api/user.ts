import { login as authLogin, register as authRegister } from '@/api/auth'
import type {
    AvatarVO,
    LoginDTO,
    PasswordEditDTO,
    UserInfo,
    UserInfoVO,
    UserProfileDTO,
    UserProfileVO,
    UserSex,
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

export function updateUserPassword(data: PasswordEditDTO): Promise<void> {
    return request<void>({
        url: '/users/me/password',
        method: 'put',
        data,
    })
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

// Legacy exports kept for current pages until Phase 2 rewires views.
export async function login(data: LoginDTO): Promise<UserInfo> {
    const token = await authLogin(data)

    return {
        id: null,
        username: data.username,
        nickname: null,
        avatarUrl: null,
        token: token.accessToken,
        refreshToken: token.refreshToken,
        tokenExpireIn: token.expireIn,
        roles: [],
    }
}

export function register(data: {
    username: string
    password: string
    nickname: string
    sex: number
    race: string
}): Promise<void> {
    return authRegister({
        username: data.username,
        password: data.password,
        nickname: data.nickname,
        sex: data.sex as UserSex,
        race: data.race,
    })
}

export async function getCurrentUserInfo(): Promise<UserInfo> {
    const profile = await getCurrentUserProfile()
    return toUserInfo(profile)
}
