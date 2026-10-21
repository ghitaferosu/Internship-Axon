import axios, {type InternalAxiosRequestConfig} from 'axios'
import {getAccessToken, getRefreshToken, saveTokens, clearTokens} from "./tokenStorage.ts";

export const api = axios.create({
    baseURL: 'http://localhost:8080',
    headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
    const token = getAccessToken()
    if (token) {
        config.headers.Authorization = `Bearer ${token}`
    }
    return config
})

interface RetriableRequest extends InternalAxiosRequestConfig {
    _retry?: boolean
}
let refreshPromise: Promise<string> | null = null

async function refreshAccessToken(): Promise<string> {
    const refreshToken = getRefreshToken()
    if (!refreshToken) {
        throw new Error('Nu exista refresh token')
    }
    const response = await axios.post(`${api.defaults.baseURL}/api/auth/refresh`, {
        token: refreshToken,
    })
    saveTokens(response.data.accessToken, response.data.refreshToken)
    return response.data.accessToken
}

api.interceptors.response.use(
    (response) => response,
    async (error) => {
        const originalRequest = error.config as RetriableRequest

        const shouldRefresh =
            error.response?.status === 401 &&
            error.response?.data?.code === 'EXPIRED_TOKEN' &&
            !originalRequest._retry

        if (!shouldRefresh) {
            return Promise.reject(error)
        }

        originalRequest._retry = true

        try {
            if (!refreshPromise) {
                refreshPromise = refreshAccessToken().finally(() => {
                    refreshPromise = null
                })
            }

            const newAccessToken = await refreshPromise

            originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
            return api(originalRequest)
        } catch (refreshError) {
            clearTokens()
            return Promise.reject(refreshError)
        }
    },
)


export function getErrorMessage(error: unknown): string {
    if (axios.isAxiosError(error)) {
        if (error.response) {
            const data = error.response.data as { message?: string } | string | undefined
            if (typeof data === 'string' && data.trim()) {
                return data
            }
            if (data && typeof data === 'object' && data.message) {
                return data.message
            }
            return `Eroare ${error.response.status}`
        }
        return 'Serviciul nu este disponibil. Incearca din nou.'
    }
    return 'A aparut o eroare neasteptata.'
}

export async function getJson<T>(path: string, params?: Record<string, unknown>): Promise<T> {
    try {
        const res = await api.get<T>(path, params ? { params } : undefined)
        return res.data
    } catch (err) {
        throw new Error(getErrorMessage(err), { cause: err })
    }
}

export async function postJson<T>(path: string, body?: unknown): Promise<T> {
    try {
        const res = await api.post<T>(path, body)
        return res.data
    } catch (err) {
        throw new Error(getErrorMessage(err), { cause: err })
    }
}