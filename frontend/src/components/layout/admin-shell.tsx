import { useQueryClient } from '@tanstack/react-query'
import {
  Activity,
  BookOpenText,
  FileStack,
  LayoutDashboard,
  LogOut,
  Radar,
  Sparkles,
} from 'lucide-react'
import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { logout } from '@/shared/api/admin'
import { clearSession } from '@/shared/auth/session'
import { useCurrentUser } from '@/shared/auth/use-current-user'
import { cn } from '@/shared/lib'

const menuItems = [
  {
    to: '/',
    label: 'Dashboard',
    description: '전체 흐름 요약',
    icon: LayoutDashboard,
  },
  {
    to: '/documents',
    label: 'Documents',
    description: '문서 업로드/관리',
    icon: FileStack,
  },
  {
    to: '/ingestion-jobs',
    label: 'Ingestion Jobs',
    description: '인제션 진행 상태',
    icon: Activity,
  },
  {
    to: '/chat',
    label: 'Chat Playground',
    description: 'RAG + LLM 테스트',
    icon: BookOpenText,
  },
  {
    to: '/monitoring',
    label: 'Monitoring',
    description: 'UI만 제공 (준비중)',
    icon: Radar,
  },
] as const

const navLinkClassName = (isActive: boolean) => {
  return cn(
    'group flex items-start gap-3 rounded-xl border px-3 py-3 transition-colors',
    isActive
      ? 'border-blue-400/30 bg-linear-to-r from-blue-500/20 to-purple-500/20 text-white shadow-lg shadow-blue-500/10'
      : 'border-white/5 bg-white/[0.02] text-foreground/70 hover:border-white/15 hover:bg-white/[0.06] hover:text-white'
  )
}

export const AdminShell = () => {
  const [isLoggingOut, setIsLoggingOut] = useState(false)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const currentUserQuery = useCurrentUser()

  const nowText = new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date())

  const handleLogout = async () => {
    try {
      setIsLoggingOut(true)
      await logout()
    } finally {
      clearSession()
      queryClient.removeQueries()
      navigate('/login', { replace: true })
      setIsLoggingOut(false)
    }
  }

  return (
    <div className='relative min-h-screen overflow-hidden bg-linear-to-br from-slate-950 via-slate-900 to-slate-950 text-foreground'>
      <a
        href='#main-content'
        className='sr-only z-50 rounded-md bg-white px-3 py-2 text-sm text-black focus:not-sr-only focus:fixed focus:left-3 focus:top-3'
      >
        메인 콘텐츠로 건너뛰기
      </a>
      <div className='pointer-events-none absolute left-[-180px] top-[-220px] h-[520px] w-[520px] rounded-full bg-linear-to-br from-blue-500/25 to-purple-500/20 blur-3xl motion-reduce:hidden' />
      <div className='pointer-events-none absolute bottom-[-200px] right-[-120px] h-[540px] w-[540px] rounded-full bg-linear-to-br from-fuchsia-500/20 to-cyan-500/15 blur-3xl motion-reduce:hidden' />

      <div className='relative mx-auto flex min-h-screen w-full max-w-[1400px] gap-5 px-4 py-5 md:px-6 md:py-6'>
        <aside className='hidden w-80 shrink-0 flex-col rounded-2xl border border-white/10 bg-white/[0.04] p-5 backdrop-blur-xl md:flex'>
          <div className='mb-6 rounded-2xl border border-white/10 bg-linear-to-br from-white/10 to-white/[0.03] p-5'>
            <div className='flex items-center gap-2 text-sm text-foreground/75'>
              <div className='flex h-7 w-7 items-center justify-center rounded-lg bg-linear-to-br from-blue-500 to-purple-500'>
                <Sparkles className='h-4 w-4 text-white' />
              </div>
              DochiBot
            </div>
            <h1 className='mt-3 text-2xl font-semibold text-white'>Admin Console</h1>
            <p className='mt-1 text-xs text-foreground/60'>문서 기반 질의응답 운영 대시보드</p>
          </div>

          <nav className='flex flex-1 flex-col gap-2'>
            {menuItems.map(item => {
              const Icon = item.icon
              return (
                <NavLink
                  key={item.to}
                  end={item.to === '/'}
                  to={item.to}
                  className={({ isActive }) => navLinkClassName(isActive)}
                >
                  <div
                    className={cn(
                      'mt-0.5 rounded-lg p-2',
                      item.to === '/' || item.to === '/documents' || item.to === '/chat'
                        ? 'bg-blue-500/20'
                        : 'bg-white/10',
                      'group-hover:bg-white/15'
                    )}
                  >
                    <Icon className='h-4 w-4' />
                  </div>
                  <div>
                    <p className='text-sm font-medium'>{item.label}</p>
                    <p className='text-xs text-inherit/80'>{item.description}</p>
                  </div>
                </NavLink>
              )
            })}
          </nav>

          <div className='mt-4 rounded-xl border border-white/10 bg-white/[0.03] p-3 text-xs text-foreground/60'>
            Monitoring 메뉴는 현재 UI 플레이스홀더로만 제공됩니다.
          </div>
        </aside>

        <div className='flex min-w-0 flex-1 flex-col gap-4'>
          <header className='rounded-2xl border border-white/10 bg-black/20 px-4 py-3 backdrop-blur-xl md:px-5'>
            <div className='flex flex-wrap items-center justify-between gap-3'>
              <div>
                <p className='text-xs uppercase tracking-[0.18em] text-foreground/60'>
                  Operational Workspace
                </p>
                <p className='text-sm text-foreground/80'>{nowText}</p>
              </div>

              <div className='flex items-center gap-2'>
                <div className='rounded-lg border border-white/10 bg-white/[0.03] px-3 py-2 text-right'>
                  <p className='text-xs text-foreground/60'>Signed in as</p>
                  <p className='text-sm font-medium text-white'>
                    {currentUserQuery.data?.username ?? 'unknown'}
                    <span className='ml-2 rounded-full border border-white/15 px-2 py-0.5 text-[10px] uppercase tracking-wide text-foreground/70'>
                      {currentUserQuery.data?.role ?? '-'}
                    </span>
                  </p>
                </div>
                <Button
                  type='button'
                  variant='outline'
                  className='border-white/20 bg-white/[0.03] text-foreground hover:bg-white/10'
                  onClick={handleLogout}
                  disabled={isLoggingOut}
                >
                  <LogOut className='h-4 w-4' />
                  {isLoggingOut ? '로그아웃 중…' : 'Logout'}
                </Button>
              </div>
            </div>

            <nav className='mt-3 flex gap-2 overflow-x-auto pb-1 md:hidden'>
              {menuItems.map(item => {
                const Icon = item.icon
                return (
                  <NavLink
                    key={item.to}
                    end={item.to === '/'}
                    to={item.to}
                    className={({ isActive }) =>
                      cn(
                        'flex shrink-0 items-center gap-2 rounded-lg border px-3 py-2 text-xs',
                        isActive
                          ? 'border-white/20 bg-white/12 text-white'
                          : 'border-white/10 bg-white/[0.03] text-foreground/70'
                      )
                    }
                  >
                    <Icon className='h-3.5 w-3.5' />
                    <span>{item.label}</span>
                  </NavLink>
                )
              })}
            </nav>
          </header>

          <main
            id='main-content'
            className='min-w-0 flex-1 rounded-2xl border border-white/10 bg-black/20 p-4 backdrop-blur-xl md:p-6'
          >
            <Outlet />
          </main>
        </div>
      </div>
    </div>
  )
}
