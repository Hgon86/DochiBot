import { useQuery } from '@tanstack/react-query'
import { RefreshCw, Search } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  formatDateTime,
  isUuid,
  listIngestionJobs,
  statusLabel,
  statusTone,
  toShortId,
} from '@/shared/api/admin'
import type { IngestionJobStatus } from '@/shared/api/types'

const statusFilters: Array<'ALL' | IngestionJobStatus> = [
  'ALL',
  'QUEUED',
  'RUNNING',
  'SUCCEEDED',
  'FAILED',
]

export const IngestionJobsPage = () => {
  const [statusFilter, setStatusFilter] = useState<'ALL' | IngestionJobStatus>('ALL')
  const [documentIdFilter, setDocumentIdFilter] = useState('')

  const normalizedDocumentId = documentIdFilter.trim()
  const queryDocumentId = isUuid(normalizedDocumentId) ? normalizedDocumentId : undefined

  const jobsQuery = useQuery({
    queryKey: ['ingestion-jobs', queryDocumentId],
    queryFn: () =>
      listIngestionJobs({
        documentId: queryDocumentId,
        limit: 100,
        offset: 0,
      }),
    refetchInterval: 10_000,
    retry: false,
  })

  const jobs = jobsQuery.data?.items ?? []

  const filteredJobs = useMemo(() => {
    if (statusFilter === 'ALL') {
      return jobs
    }
    return jobs.filter(job => job.status === statusFilter)
  }, [jobs, statusFilter])

  const queuedCount = jobs.filter(job => job.status === 'QUEUED').length
  const runningCount = jobs.filter(job => job.status === 'RUNNING').length
  const succeededCount = jobs.filter(job => job.status === 'SUCCEEDED').length
  const failedCount = jobs.filter(job => job.status === 'FAILED').length

  return (
    <div className='space-y-6'>
      <section className='rounded-2xl border border-white/10 bg-linear-to-br from-white/[0.10] to-white/[0.03] p-5 shadow-lg shadow-black/10'>
        <h1 className='text-2xl font-semibold text-white'>Ingestion Jobs</h1>
        <p className='mt-2 text-sm text-foreground/70'>
          인제션 큐 상태를 확인하고 실패/재시도 흐름을 점검합니다.
        </p>
      </section>

      <section className='grid gap-4 sm:grid-cols-2 lg:grid-cols-4'>
        <article className='rounded-xl border border-white/10 bg-linear-to-br from-white/[0.08] to-white/[0.03] p-4'>
          <p className='text-xs uppercase tracking-wide text-foreground/60'>Queued</p>
          <p className='mt-2 text-2xl font-semibold text-amber-300'>{queuedCount}</p>
        </article>
        <article className='rounded-xl border border-white/10 bg-linear-to-br from-white/[0.08] to-white/[0.03] p-4'>
          <p className='text-xs uppercase tracking-wide text-foreground/60'>Running</p>
          <p className='mt-2 text-2xl font-semibold text-sky-300'>{runningCount}</p>
        </article>
        <article className='rounded-xl border border-white/10 bg-linear-to-br from-white/[0.08] to-white/[0.03] p-4'>
          <p className='text-xs uppercase tracking-wide text-foreground/60'>Succeeded</p>
          <p className='mt-2 text-2xl font-semibold text-emerald-300'>{succeededCount}</p>
        </article>
        <article className='rounded-xl border border-white/10 bg-linear-to-br from-white/[0.08] to-white/[0.03] p-4'>
          <p className='text-xs uppercase tracking-wide text-foreground/60'>Failed</p>
          <p className='mt-2 text-2xl font-semibold text-rose-300'>{failedCount}</p>
        </article>
      </section>

      <section className='rounded-2xl border border-white/10 bg-linear-to-br from-white/[0.09] to-white/[0.03] p-4'>
        <div className='flex flex-wrap items-center gap-2'>
          <div className='relative w-full max-w-sm'>
            <Search className='pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-foreground/40' />
            <Input
              name='documentIdFilter'
              autoComplete='off'
              value={documentIdFilter}
              onChange={event => setDocumentIdFilter(event.target.value)}
              placeholder='문서 ID로 필터 (UUID)…'
              className='border-white/20 bg-white/5 pl-9 text-foreground placeholder:text-foreground/40'
            />
          </div>

          <select
            aria-label='인제션 상태 필터'
            className='h-9 rounded-md border border-white/20 bg-white/5 px-3 text-sm text-foreground'
            value={statusFilter}
            onChange={event => setStatusFilter(event.target.value as 'ALL' | IngestionJobStatus)}
          >
            {statusFilters.map(status => (
              <option key={status} value={status} className='bg-zinc-900'>
                {status === 'ALL' ? '전체 상태' : status}
              </option>
            ))}
          </select>

          <Button
            type='button'
            variant='outline'
            className='border-white/20 bg-white/5 text-foreground'
            onClick={() => jobsQuery.refetch()}
            disabled={jobsQuery.isFetching}
          >
            <RefreshCw className={jobsQuery.isFetching ? 'h-4 w-4 animate-spin' : 'h-4 w-4'} />
            새로고침
          </Button>
        </div>

        {!queryDocumentId && normalizedDocumentId ? (
          <p className='mt-2 text-xs text-amber-200'>
            UUID 형식이 아니므로 문서ID 필터를 적용하지 않았습니다.
          </p>
        ) : null}

        <div className='mt-4 overflow-hidden rounded-xl border border-white/10'>
          <div
            className='max-h-[420px] overflow-auto'
            style={{ contentVisibility: 'auto', containIntrinsicSize: '420px' }}
          >
            <table className='w-full min-w-[880px] border-collapse text-sm'>
              <thead className='sticky top-0 bg-black/30 text-left text-xs uppercase tracking-wide text-foreground/60'>
                <tr>
                  <th className='px-4 py-3'>Job ID</th>
                  <th className='px-4 py-3'>Document</th>
                  <th className='px-4 py-3'>Status</th>
                  <th className='px-4 py-3'>Attempts</th>
                  <th className='px-4 py-3'>Chunks</th>
                  <th className='px-4 py-3'>Created</th>
                  <th className='px-4 py-3'>Finished</th>
                </tr>
              </thead>
              <tbody>
                {jobsQuery.isLoading ? (
                  <tr>
                    <td className='px-4 py-8 text-center text-foreground/60' colSpan={7}>
                      작업 목록을 불러오는 중…
                    </td>
                  </tr>
                ) : filteredJobs.length === 0 ? (
                  <tr>
                    <td className='px-4 py-8 text-center text-foreground/60' colSpan={7}>
                      표시할 작업이 없습니다.
                    </td>
                  </tr>
                ) : (
                  filteredJobs.map(job => (
                    <tr key={job.id} className='border-t border-white/5'>
                      <td className='px-4 py-3 font-mono text-xs text-foreground/80'>
                        {toShortId(job.id)}
                      </td>
                      <td className='px-4 py-3'>
                        <Link
                          to={`/documents/${job.documentId}`}
                          className='font-mono text-xs text-sky-200 hover:underline'
                        >
                          {toShortId(job.documentId)}
                        </Link>
                      </td>
                      <td className='px-4 py-3'>
                        <span
                          className={`inline-flex rounded-full border px-2.5 py-1 text-xs ${statusTone[job.status]}`}
                        >
                          {statusLabel[job.status]}
                        </span>
                      </td>
                      <td className='px-4 py-3 text-foreground/80'>
                        {job.attemptCount}/{job.maxAttempts}
                      </td>
                      <td className='px-4 py-3 text-foreground/80'>{job.chunkCount ?? '-'}</td>
                      <td className='px-4 py-3 text-foreground/70'>
                        {formatDateTime(job.createdAt)}
                      </td>
                      <td className='px-4 py-3 text-foreground/70'>
                        {formatDateTime(job.finishedAt)}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>

        {jobsQuery.isError ? (
          <p className='mt-3 text-xs text-amber-200'>
            작업 조회에 실패했습니다. 관리자 권한(ADMIN)과 API 연결 상태를 확인해주세요.
          </p>
        ) : null}
      </section>
    </div>
  )
}
