import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Download, LoaderCircle, RotateCcw } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import {
  formatDateTime,
  getDocument,
  getDocumentDownloadUrl,
  reindexDocument,
  statusLabel,
  statusTone,
  toShortId,
} from '@/shared/api/admin'

export const DocumentDetailPage = () => {
  const { id } = useParams<{ id: string }>()
  const queryClient = useQueryClient()

  const documentQuery = useQuery({
    queryKey: ['document', id],
    queryFn: () => {
      if (!id) {
        throw new Error('문서 ID가 없습니다.')
      }
      return getDocument(id)
    },
    enabled: Boolean(id),
    retry: false,
  })

  const downloadMutation = useMutation({
    mutationFn: async (documentId: string) => {
      const result = await getDocumentDownloadUrl(documentId)
      window.open(result.downloadUrl, '_blank', 'noopener,noreferrer')
      return result
    },
  })

  const reindexMutation = useMutation({
    mutationFn: (documentId: string) => reindexDocument(documentId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['documents'] })
      await queryClient.invalidateQueries({ queryKey: ['dashboard-summary'] })
      await queryClient.invalidateQueries({ queryKey: ['ingestion-jobs'] })
      await queryClient.invalidateQueries({ queryKey: ['document', id] })
    },
  })

  if (!id) {
    return (
      <div className='rounded-xl border border-amber-300/30 bg-amber-500/10 p-4 text-sm text-amber-100'>
        문서 ID가 없습니다.
      </div>
    )
  }

  const document = documentQuery.data

  return (
    <div className='space-y-6'>
      <section className='rounded-2xl border border-white/10 bg-linear-to-br from-white/[0.10] to-white/[0.03] p-5 shadow-lg shadow-black/10'>
        <div className='flex flex-wrap items-start justify-between gap-4'>
          <div>
            <p className='text-xs uppercase tracking-wide text-foreground/60'>Document Detail</p>
            <h1 className='mt-1 text-2xl font-semibold text-white'>
              {document?.title ?? '문서를 불러오는 중…'}
            </h1>
            <p className='mt-2 text-sm text-foreground/70'>ID: {toShortId(id)}</p>
          </div>

          {document ? (
            <span
              className={`inline-flex rounded-full border px-3 py-1.5 text-xs ${statusTone[document.status]}`}
            >
              {statusLabel[document.status]}
            </span>
          ) : null}
        </div>
      </section>

      <section className='grid gap-4 lg:grid-cols-[2fr_1fr]'>
        <article className='rounded-2xl border border-white/10 bg-linear-to-br from-white/[0.09] to-white/[0.03] p-5'>
          {documentQuery.isLoading ? (
            <div className='text-sm text-foreground/70'>문서 정보를 조회하는 중…</div>
          ) : documentQuery.isError || !document ? (
            <div className='text-sm text-amber-100'>문서 조회에 실패했습니다.</div>
          ) : (
            <dl className='grid gap-4 sm:grid-cols-2'>
              <div className='rounded-xl border border-white/10 bg-black/20 p-3'>
                <dt className='text-xs text-foreground/55'>Title</dt>
                <dd className='mt-1 text-sm text-white'>{document.title}</dd>
              </div>
              <div className='rounded-xl border border-white/10 bg-black/20 p-3'>
                <dt className='text-xs text-foreground/55'>Source Type</dt>
                <dd className='mt-1 text-sm text-white'>{document.sourceType}</dd>
              </div>
              <div className='rounded-xl border border-white/10 bg-black/20 p-3 sm:col-span-2'>
                <dt className='text-xs text-foreground/55'>Storage URI</dt>
                <dd className='mt-1 break-all font-mono text-xs text-foreground/85'>
                  {document.storageUri ?? '-'}
                </dd>
              </div>
              <div className='rounded-xl border border-white/10 bg-black/20 p-3'>
                <dt className='text-xs text-foreground/55'>Created</dt>
                <dd className='mt-1 text-sm text-white'>{formatDateTime(document.createdAt)}</dd>
              </div>
              <div className='rounded-xl border border-white/10 bg-black/20 p-3'>
                <dt className='text-xs text-foreground/55'>Updated</dt>
                <dd className='mt-1 text-sm text-white'>{formatDateTime(document.updatedAt)}</dd>
              </div>
            </dl>
          )}
        </article>

        <article className='rounded-2xl border border-white/10 bg-linear-to-br from-white/[0.09] to-white/[0.03] p-5'>
          <h2 className='text-sm font-medium text-white'>Actions</h2>
          <div className='mt-3 flex flex-col gap-2'>
            <Button
              type='button'
              variant='outline'
              className='justify-start border-white/20 bg-white/5 text-foreground'
              onClick={() => downloadMutation.mutate(id)}
              disabled={downloadMutation.isPending}
            >
              {downloadMutation.isPending ? (
                <LoaderCircle className='h-4 w-4 animate-spin' />
              ) : (
                <Download className='h-4 w-4' />
              )}
              다운로드 링크 열기
            </Button>

            <Button
              type='button'
              variant='outline'
              className='justify-start border-white/20 bg-white/5 text-foreground'
              onClick={() => reindexMutation.mutate(id)}
              disabled={reindexMutation.isPending}
            >
              {reindexMutation.isPending ? (
                <LoaderCircle className='h-4 w-4 animate-spin' />
              ) : (
                <RotateCcw className='h-4 w-4' />
              )}
              재인덱싱 실행
            </Button>

            <Button type='button' variant='ghost' asChild>
              <Link to='/documents'>목록으로 돌아가기</Link>
            </Button>
          </div>
        </article>
      </section>
    </div>
  )
}
