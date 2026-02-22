import { useQuery } from '@tanstack/react-query'
import { Activity, ArrowRight, BookOpenText, FileStack, Radar, Sparkles } from 'lucide-react'
import { Link } from 'react-router-dom'

import { fetchDashboardSummary, formatDateTime } from '@/shared/api/admin'
import { cn } from '@/shared/lib'

const quickMenus = [
  {
    to: '/documents',
    title: 'Documents',
    description: '업로드, 상태 확인, 상세 조회, 재인덱싱',
    icon: FileStack,
    gradient: 'from-blue-500 to-cyan-500',
  },
  {
    to: '/ingestion-jobs',
    title: 'Ingestion Jobs',
    description: '작업 큐 현황 및 성공/실패 흐름 추적',
    icon: Activity,
    gradient: 'from-emerald-500 to-teal-500',
  },
  {
    to: '/chat',
    title: 'Chat Playground',
    description: 'RAG + LLM 응답 및 citation 확인',
    icon: BookOpenText,
    gradient: 'from-indigo-500 to-purple-500',
  },
  {
    to: '/monitoring',
    title: 'Monitoring',
    description: '현재는 메뉴/화면 뼈대만 제공',
    icon: Radar,
    gradient: 'from-orange-500 to-rose-500',
  },
] as const

const statCards = [
  {
    label: 'TOTAL DOCUMENTS',
    valueClassName: 'text-blue-300',
    dividerClassName: 'from-blue-500/30 to-cyan-500/30',
  },
  {
    label: 'COMPLETED DOCUMENTS',
    valueClassName: 'text-emerald-300',
    dividerClassName: 'from-emerald-500/30 to-teal-500/30',
  },
  {
    label: 'RUNNING JOBS',
    valueClassName: 'text-cyan-300',
    dividerClassName: 'from-cyan-500/30 to-blue-500/30',
  },
  {
    label: 'FAILED JOBS',
    valueClassName: 'text-rose-300',
    dividerClassName: 'from-rose-500/30 to-pink-500/30',
  },
] as const

export const DashboardPage = () => {
  const summaryQuery = useQuery({
    queryKey: ['dashboard-summary'],
    queryFn: fetchDashboardSummary,
    refetchInterval: 20_000,
    retry: false,
  })

  const documents = summaryQuery.data?.documents ?? []
  const jobs = summaryQuery.data?.jobs ?? []

  const completedDocuments = documents.filter(item => item.status === 'COMPLETED').length
  const failedJobs = jobs.filter(item => item.status === 'FAILED').length
  const runningJobs = jobs.filter(
    item => item.status === 'RUNNING' || item.status === 'QUEUED'
  ).length
  const statValues = [documents.length, completedDocuments, runningJobs, failedJobs]

  const recentTimestamp = [
    ...documents.map(item => item.updatedAt),
    ...jobs.map(item => item.createdAt),
  ]
    .filter(Boolean)
    .sort()
    .at(-1)

  return (
    <div className='space-y-6'>
      <section className='rounded-2xl border border-white/10 bg-linear-to-br from-white/[0.10] to-white/[0.03] p-6 shadow-2xl shadow-black/20'>
        <div className='flex flex-wrap items-start justify-between gap-4'>
          <div className='max-w-2xl'>
            <div className='inline-flex items-center gap-2 rounded-full border border-white/20 bg-white/10 px-3 py-1 text-xs text-foreground/80'>
              <Sparkles className='h-3.5 w-3.5' />
              Portfolio Mode
            </div>
            <h1 className='mt-4 bg-linear-to-r from-blue-300 via-violet-300 to-pink-300 bg-clip-text text-4xl font-semibold tracking-tight text-transparent'>
              DochiBot Admin Dashboard
            </h1>
            <p className='mt-3 text-sm leading-relaxed text-foreground/75'>
              문서 수집부터 인제션, 그리고 RAG 질의응답 검증까지 한 흐름으로 점검할 수 있는 운영
              홈입니다.
            </p>
          </div>

          <div className='rounded-xl border border-white/10 bg-black/30 px-4 py-3 text-right'>
            <p className='text-xs uppercase tracking-[0.16em] text-foreground/60'>Last Activity</p>
            <p className='mt-1 text-sm font-medium text-white'>{formatDateTime(recentTimestamp)}</p>
          </div>
        </div>
      </section>

      <section className='grid gap-4 sm:grid-cols-2 xl:grid-cols-4'>
        {statCards.map((card, index) => (
          <article
            key={card.label}
            className='rounded-2xl border border-white/10 bg-linear-to-br from-white/[0.08] to-white/[0.03] p-5 shadow-lg shadow-black/10 transition-transform hover:-translate-y-0.5'
          >
            <p className='text-xs uppercase tracking-[0.16em] text-foreground/60'>{card.label}</p>
            <p className={cn('mt-2 text-4xl font-semibold tabular-nums', card.valueClassName)}>
              {statValues[index]}
            </p>
            <div className={cn('mt-3 h-1 rounded-full bg-linear-to-r', card.dividerClassName)} />
          </article>
        ))}
      </section>

      {summaryQuery.isError ? (
        <section className='rounded-2xl border border-amber-300/30 bg-amber-500/10 px-4 py-3 text-sm text-amber-100'>
          대시보드 통계를 불러오지 못했습니다. 권한(ADMIN) 또는 백엔드 연결 상태를 확인해주세요.
        </section>
      ) : null}

      <section>
        <h2 className='text-2xl font-semibold text-white'>Quick Access</h2>
        <div className='mt-4 grid gap-4 md:grid-cols-2'>
          {quickMenus.map(menu => {
            const Icon = menu.icon
            return (
              <Link
                key={menu.to}
                to={menu.to}
                className='group rounded-2xl border border-white/10 bg-linear-to-br from-white/[0.10] to-white/[0.03] p-5 transition-colors hover:border-white/25 hover:bg-white/[0.12]'
              >
                <div className='flex items-start justify-between gap-3'>
                  <div className='flex items-start gap-3'>
                    <div
                      className={cn(
                        'flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-linear-to-br shadow-lg',
                        menu.gradient
                      )}
                    >
                      <Icon className='h-5 w-5 text-white' />
                    </div>
                    <div>
                      <h3 className='text-lg font-medium text-white'>{menu.title}</h3>
                      <p className='mt-1 text-sm leading-relaxed text-foreground/70'>
                        {menu.description}
                      </p>
                    </div>
                  </div>
                  <ArrowRight className='h-4 w-4 text-foreground/50 transition-transform group-hover:translate-x-1 group-hover:text-white' />
                </div>
              </Link>
            )
          })}
        </div>
      </section>
    </div>
  )
}
