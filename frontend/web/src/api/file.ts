import request from '@/utils/request'
import type { AvatarVO } from '@/types'

export type ImagePurpose = 'BACKGROUND' | 'COVER'

export interface ImageUploadVO {
    url: string
    purpose: ImagePurpose
}

export function uploadManagedImage(file: File, purpose: ImagePurpose): Promise<ImageUploadVO> {
    const data = toFileForm(file)
    data.append('purpose', purpose)
    return request<ImageUploadVO>({ url: '/images', method: 'post', data })
}

function toFileForm(file: File): FormData {
    const formData = new FormData()
    formData.append('file', file)
    return formData
}

export function uploadAvatar(file: File): Promise<AvatarVO> {
    return request<AvatarVO>({
        url: '/uploadAvatar',
        method: 'post',
        data: toFileForm(file),
    })
}

export function uploadImage(file: File): Promise<string | { data: string }> {
    return request<string | { data: string }>({
        url: '/uploadImage',
        method: 'post',
        data: toFileForm(file),
    })
}
