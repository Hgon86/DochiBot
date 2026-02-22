import { AlertCircle } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { Alert, AlertDescription } from '@/components/ui/alert'
import { Spinner } from '@/components/ui/spinner'
import { publicApi } from '@/shared/api/http'
import { setAccessToken } from '@/shared/auth/session'

type RefreshResponse = {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
}

/**
 * 백엔드 OAuth2 콜백(쿠키 세팅) 이후 진입하는 프론트 라우트.
 *
 * refresh_token(HttpOnly 쿠키)로 access token을 발급받아 메모리 세션에 저장한다.
 */
export const OauthCallbackPage = () => {
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let alive = true

    ;(async () => {
      try {
        const data = await publicApi.post('auth/refresh').json<RefreshResponse>()
        if (!alive) {
          return
        }

        setAccessToken(data.accessToken)
        navigate('/', { replace: true })
      } catch (e) {
        if (!alive) {
          return
        }

        setError(e instanceof Error ? e.message : 'OAuth callback failed')
        navigate('/login', { replace: true })
      }
    })()

    return () => {
      alive = false
    }
  }, [navigate])

  return (
    <div className='flex min-h-screen items-center justify-center px-4'>
      <div className='w-full max-w-md space-y-4 text-center'>
        <div className='flex items-center justify-center gap-2 text-foreground/80'>
          <Spinner className='h-5 w-5' />
          <span>로그인 처리 중…</span>
        </div>
        {error && (
          <Alert variant='destructive' className='border-destructive/50 bg-destructive/10'>
            <AlertCircle className='h-4 w-4' />
            <AlertDescription className='text-foreground'>{error}</AlertDescription>
          </Alert>
        )}
      </div>
    </div>
  )
}
