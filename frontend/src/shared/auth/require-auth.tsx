import { Navigate, Outlet, useLocation } from 'react-router-dom'

import { Spinner } from '@/components/ui/spinner'
import { useCurrentUser } from '@/shared/auth/use-current-user'

const shouldBypassAuthInDev = (): boolean => {
  return import.meta.env.DEV && import.meta.env.VITE_DEV_BYPASS_AUTH === 'true'
}

export const RequireAuth = () => {
  const location = useLocation()
  const bypassAuth = shouldBypassAuthInDev()
  const currentUserQuery = useCurrentUser(!bypassAuth)

  if (bypassAuth) {
    return <Outlet />
  }

  if (currentUserQuery.isLoading) {
    return (
      <div className='flex min-h-screen items-center justify-center'>
        <div className='flex items-center gap-3 rounded-xl border border-white/10 bg-white/5 px-5 py-3 text-foreground/80 backdrop-blur'>
          <Spinner className='h-5 w-5' />
          <span>세션을 확인하는 중입니다…</span>
        </div>
      </div>
    )
  }

  if (currentUserQuery.isError || !currentUserQuery.data) {
    return <Navigate to='/login' replace state={{ from: location }} />
  }

  return <Outlet />
}
