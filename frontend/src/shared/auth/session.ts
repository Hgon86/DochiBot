import { create } from 'zustand'

type SessionState = {
  accessToken: string | null
  setAccessToken: (token: string) => void
  clear: () => void
}

/**
 * 메모리 기반 세션 스토어.
 *
 * 보안 정책:
 * - access token은 메모리에만 저장한다(localStorage/sessionStorage 금지).
 * - refresh token은 HttpOnly 쿠키로 저장되어 `credentials: 'include'`로만 전송된다고 가정한다.
 */
export const useSessionStore = create<SessionState>(set => ({
  accessToken: null,
  setAccessToken: token => set({ accessToken: token }),
  clear: () => set({ accessToken: null }),
}))

export const getAccessToken = (): string | null => {
  return useSessionStore.getState().accessToken
}

export const setAccessToken = (token: string): void => {
  useSessionStore.getState().setAccessToken(token)
}

export const clearSession = (): void => {
  useSessionStore.getState().clear()
}
