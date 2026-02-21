import ky, { type Options } from 'ky'

import { clearSession, getAccessToken, setAccessToken } from '@/shared/auth/session'

type RefreshResponse = {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
}

const SKIP_AUTH_HEADER = 'x-skip-auth-refresh'
const RETRY_HEADER = 'x-auth-retry'

/**
 * API Base URL.
 *
 * Dev:
 * - 기본값은 `/api/v1`이고, Vite proxy로 CORS를 회피한다.
 * Prod:
 * - `VITE_API_BASE_URL`로 덮어쓴다.
 */
const resolveApiBaseUrl = (): string => {
  return import.meta.env.VITE_API_BASE_URL ?? '/api/v1'
}

/**
 * ky의 `Options['headers']` 유니온 타입에서 특정 헤더 값을 읽는다.
 */
const getHeaderValue = (headers: Options['headers'] | undefined, name: string): string | null => {
  if (!headers) {
    return null
  }
  if (headers instanceof Headers) {
    return headers.get(name)
  }
  if (Array.isArray(headers)) {
    const match = headers.find(([k]) => k.toLowerCase() === name.toLowerCase())
    return match ? match[1] : null
  }

  const record = headers as Record<string, string>
  const key = Object.keys(record).find(k => k.toLowerCase() === name.toLowerCase())
  return key ? record[key] : null
}

/**
 * `1`이면 Authorization 주입/refresh 재시도를 스킵한다.
 * (auth 엔드포인트 호출 시 무한 루프 방지 목적)
 */
const shouldSkipAuth = (options: Options): boolean => {
  return getHeaderValue(options.headers, SKIP_AUTH_HEADER) === '1'
}

/**
 * refresh 후 재시도는 1회만 허용해 무한 루프를 방지한다.
 */
const hasRetried = (options: Options): boolean => {
  return getHeaderValue(options.headers, RETRY_HEADER) === '1'
}

let refreshPromise: Promise<string> | null = null

/**
 * refresh_token 쿠키를 이용해 access token을 재발급한다.
 *
 * 동시성:
 * - 여러 요청이 동시에 401이 나도 refresh는 1회만 수행하고 나머지는 같은 Promise를 기다린다.
 */
const refreshAccessTokenOnce = async (rawApi: typeof ky): Promise<string> => {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      const data = await rawApi
        .post('auth/refresh', {
          headers: {
            [SKIP_AUTH_HEADER]: '1',
          },
        })
        .json<RefreshResponse>()

      setAccessToken(data.accessToken)
      return data.accessToken
    })().finally(() => {
      refreshPromise = null
    })
  }

  return refreshPromise
}

export const publicApi = ky.create({
  prefixUrl: resolveApiBaseUrl(),
  credentials: 'include',
})

/**
 * ky instance for API calls.
 * - Adds Authorization header from in-memory session (Zustand)
 * - On 401: refreshes once, then retries the original request once
 */
export const api = ky.create({
  prefixUrl: resolveApiBaseUrl(),
  credentials: 'include',
  hooks: {
    beforeRequest: [
      (request, options) => {
        if (shouldSkipAuth(options)) {
          return
        }

        const token = getAccessToken()
        if (!token) {
          return
        }

        request.headers.set('authorization', `Bearer ${token}`)
      },
    ],
    afterResponse: [
      async (request, options, response) => {
        if (shouldSkipAuth(options) || hasRetried(options)) {
          return response
        }
        if (response.status !== 401) {
          return response
        }

        try {
          const token = await refreshAccessTokenOnce(publicApi)

          const retryHeaders = new Headers(options.headers)
          retryHeaders.delete(SKIP_AUTH_HEADER)
          retryHeaders.set(RETRY_HEADER, '1')
          retryHeaders.set('authorization', `Bearer ${token}`)

          return api(request.clone(), {
            ...options,
            headers: retryHeaders,
          })
        } catch {
          clearSession()
          return response
        }
      },
    ],
  },
})
