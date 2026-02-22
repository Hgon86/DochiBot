import { useQuery } from '@tanstack/react-query'

import { getCurrentUser } from '@/shared/api/admin'
import type { MeResponse } from '@/shared/api/types'

export const CURRENT_USER_QUERY_KEY = ['auth', 'me'] as const

export const useCurrentUser = (enabled = true) => {
  return useQuery<MeResponse>({
    queryKey: CURRENT_USER_QUERY_KEY,
    queryFn: getCurrentUser,
    enabled,
    retry: false,
    staleTime: 60_000,
  })
}
