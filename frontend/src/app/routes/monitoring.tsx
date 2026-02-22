import { useQuery } from '@tanstack/react-query'
import { Activity, Clock4, HardDriveDownload, ShieldCheck } from 'lucide-react'

import { getHealthStatus } from '@/shared/api/admin'

const placeholderCards = [
  {
    title: 'Queue Throughput',
    description: '실시간 작업 처리량 차트 예정',
    icon: Activity,
  },
  {
    title: 'API Latency',
    description: '엔드포인트별 p95 지연시간 예정',
    icon: Clock4,
  },
  {
    title: 'Storage Usage',
    description: '객체 스토리지 사용량 시각화 예정',
    icon: HardDriveDownload,
  },
] as const

export const MonitoringPage = () => {
  const healthQuery = useQuery({
    queryKey: ['monitoring-health'],
    queryFn: getHealthStatus,
    refetchInterval: 20_000,
    retry: false,
  })

  return (
    <div className='space-y-6'>
      <section className='rounded-2xl border border-white/10 bg-linear-to-br from-white/[0.10] to-white/[0.03] p-5 shadow-lg shadow-black/10'>
        <div className='flex flex-wrap items-start justify-between gap-4'>
          <div>
            <h1 className='text-2xl font-semibold text-white'>Monitoring</h1>
            <p className='mt-2 text-sm text-foreground/70'>
              현재는 메뉴 구조만 선반영한 단계입니다. 운영 지표는 추후에 확장합니다.
            </p>
          </div>

          <div className='rounded-xl border border-white/10 bg-black/20 px-4 py-3 text-right'>
            <p className='text-xs uppercase tracking-wide text-foreground/60'>Backend Health</p>
            <p className='mt-1 text-sm font-medium text-white'>
              {healthQuery.data?.status ?? (healthQuery.isLoading ? 'CHECKING…' : 'UNAVAILABLE')}
            </p>
          </div>
        </div>
      </section>

      <section className='grid gap-4 md:grid-cols-3'>
        {placeholderCards.map(card => {
          const Icon = card.icon
          return (
            <article
              key={card.title}
              className='rounded-2xl border border-dashed border-white/15 bg-linear-to-br from-white/[0.08] to-white/[0.02] p-4'
            >
              <div className='flex items-center gap-2 text-foreground/70'>
                <Icon className='h-4 w-4' />
                <span className='text-xs uppercase tracking-wide'>Coming Soon</span>
              </div>
              <h2 className='mt-3 text-sm font-medium text-white'>{card.title}</h2>
              <p className='mt-1 text-xs text-foreground/70'>{card.description}</p>
            </article>
          )
        })}
      </section>

      <section className='rounded-2xl border border-white/10 bg-gradient-to-r from-emerald-500/10 to-cyan-500/10 p-5'>
        <div className='flex items-center gap-2 text-emerald-100'>
          <ShieldCheck className='h-5 w-5' />
          <h2 className='font-medium'>현재 단계 안내</h2>
        </div>
        <p className='mt-2 text-sm text-emerald-50/80'>
          이 페이지는 포트폴리오 IA 완성도를 위한 자리입니다. 실제 모니터링 데이터 연동은 다음
          단계에서 진행하면 됩니다.
        </p>
      </section>
    </div>
  )
}
