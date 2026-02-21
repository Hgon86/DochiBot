import { useCallback, useState } from 'react'

import { publicApi } from '@/shared/api/http'
import { setAccessToken } from '@/shared/auth/session'

type LoginResponse = {
  accessToken: string
  expiresInSeconds: number
}

/**
 * 로그인 화면에서 사용하는 인증 훅.
 * - 아이디/비밀번호 로그인: `/api/v1/auth/login`
 * - Google 로그인: 백엔드 OAuth2 authorize 엔드포인트로 리다이렉트
 */
export const useAuth = () => {
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const login = useCallback(async (email: string, password: string) => {
    setIsLoading(true)
    setError(null)

    try {
      const data = await publicApi
        .post('auth/login', {
          json: {
            username: email,
            password,
          },
        })
        .json<LoginResponse>()

      setAccessToken(data.accessToken)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Login failed')
      throw e
    } finally {
      setIsLoading(false)
    }
  }, [])

  const loginWithGoogle = useCallback(() => {
    window.location.href = '/api/v1/auth/oauth2/authorize/google'
  }, [])

  return {
    login,
    loginWithGoogle,
    isLoading,
    error,
  }
}
