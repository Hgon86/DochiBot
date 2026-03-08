import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { LoaderCircle, RefreshCw, Search, Trash2, UploadCloud } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  deleteDocument,
  formatDateTime,
  isSupportedUploadFilename,
  listDocuments,
  reindexDocument,
  statusLabel,
  statusTone,
  toShortId,
  uploadDocument,
} from '@/shared/api/admin'
import type { DocumentStatus } from '@/shared/api/types'

const statusFilters: Array<'ALL' | DocumentStatus> = [
  'ALL',
  'PENDING',
  'PROCESSING',
  'COMPLETED',
  'FAILED',
]

export const DocumentsPage = () => {
  const queryClient = useQueryClient()
  const [statusFilter, setStatusFilter] = useState<'ALL' | DocumentStatus>('ALL')
  const [searchKeyword, setSearchKeyword] = useState('')
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [uploadTitle, setUploadTitle] = useState('')
  const [pendingDeleteDocumentId, setPendingDeleteDocumentId] = useState<string | null>(null)
  const [pendingReindexDocumentId, setPendingReindexDocumentId] = useState<string | null>(null)
  const [feedback, setFeedback] = useState<string | null>(null)

  const documentsQuery = useQuery({
    queryKey: ['documents', statusFilter],
    queryFn: () =>
      listDocuments({
        status: statusFilter === 'ALL' ? undefined : statusFilter,
        limit: 100,
        offset: 0,
      }),
    refetchInterval: 15_000,
    retry: false,
  })

  const uploadMutation = useMutation({
    mutationFn: async () => {
      if (!selectedFile) {
        throw new Error('업로드할 파일을 먼저 선택해주세요.')
      }
      return uploadDocument({ file: selectedFile, title: uploadTitle })
    },
    onSuccess: async result => {
      setFeedback(`업로드를 시작했습니다. (문서 ${toShortId(result.documentId)})`)
      setSelectedFile(null)
      setUploadTitle('')
      await queryClient.invalidateQueries({ queryKey: ['documents'] })
      await queryClient.invalidateQueries({ queryKey: ['dashboard-summary'] })
      await queryClient.invalidateQueries({ queryKey: ['ingestion-jobs'] })
    },
    onError: error => {
      setFeedback(error instanceof Error ? error.message : '업로드에 실패했습니다.')
    },
  })

  const reindexMutation = useMutation({
    mutationFn: (documentId: string) => reindexDocument(documentId),
    onMutate: documentId => {
      setPendingReindexDocumentId(documentId)
    },
    onSuccess: async result => {
      setFeedback(`재인덱싱 작업을 생성했습니다. (작업 ${toShortId(result.jobId)})`)
      await queryClient.invalidateQueries({ queryKey: ['documents'] })
      await queryClient.invalidateQueries({ queryKey: ['dashboard-summary'] })
      await queryClient.invalidateQueries({ queryKey: ['ingestion-jobs'] })
    },
    onError: error => {
      setFeedback(error instanceof Error ? error.message : '재인덱싱 요청에 실패했습니다.')
    },
    onSettled: () => {
      setPendingReindexDocumentId(null)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (documentId: string) => deleteDocument(documentId),
    onMutate: documentId => {
      setPendingDeleteDocumentId(documentId)
    },
    onSuccess: async (_, documentId) => {
      setFeedback(`문서를 삭제했습니다. (${toShortId(documentId)})`)
      await queryClient.invalidateQueries({ queryKey: ['documents'] })
      await queryClient.invalidateQueries({ queryKey: ['dashboard-summary'] })
      await queryClient.invalidateQueries({ queryKey: ['ingestion-jobs'] })
    },
    onError: error => {
      setFeedback(error instanceof Error ? error.message : '문서 삭제에 실패했습니다.')
    },
    onSettled: () => {
      setPendingDeleteDocumentId(null)
    },
  })

  const documents = documentsQuery.data?.items ?? []

  const filteredDocuments = useMemo(() => {
    const keyword = searchKeyword.trim().toLowerCase()
    if (!keyword) {
      return documents
    }

    return documents.filter(item => {
      const target = `${item.title} ${item.originalFilename ?? ''} ${item.id}`.toLowerCase()
      return target.includes(keyword)
    })
  }, [documents, searchKeyword])

  return (
    <div className='space-y-6'>
      <section className='rounded-2xl border border-white/10 bg-linear-to-br from-white/[0.10] to-white/[0.03] p-5 shadow-lg shadow-black/10'>
        <h1 className='text-2xl font-semibold text-white'>Documents</h1>
        <p className='mt-2 text-sm text-foreground/70'>
          문서를 업로드하고 인제션 상태를 추적합니다. 재인덱싱도 이 화면에서 바로 실행할 수
          있습니다.
        </p>
      </section>

      <section className='grid gap-4 lg:grid-cols-[1.1fr_2fr]'>
        <article className='rounded-2xl border border-white/10 bg-linear-to-br from-white/[0.09] to-white/[0.03] p-4'>
          <div className='flex items-center gap-2 text-white'>
            <UploadCloud className='h-4 w-4' />
            <h2 className='text-base font-medium'>New Upload</h2>
          </div>

          <div className='mt-4 space-y-3'>
            <Input
              name='uploadFile'
              type='file'
              accept='.pdf,.md,.markdown'
              onChange={event => {
                const nextFile = event.target.files?.[0] ?? null
                if (!nextFile) {
                  setSelectedFile(null)
                  setFeedback(null)
                  return
                }

                if (!isSupportedUploadFilename(nextFile.name)) {
                  setSelectedFile(null)
                  setFeedback(
                    'PDF(.pdf) 또는 Markdown(.md, .markdown) 파일만 업로드할 수 있습니다.'
                  )
                  return
                }

                setSelectedFile(nextFile)
                setFeedback(null)
              }}
              className='border-white/20 bg-white/5 text-foreground file:mr-3 file:rounded-md file:border-0 file:bg-white/15 file:px-3 file:py-1 file:text-xs file:text-white'
            />
            <p className='text-xs text-foreground/60'>
              지원 형식: PDF(.pdf), Markdown(.md, .markdown)
            </p>
            <Input
              name='uploadTitle'
              autoComplete='off'
              placeholder='문서 제목 (선택)'
              value={uploadTitle}
              onChange={event => setUploadTitle(event.target.value)}
              className='border-white/20 bg-white/5 text-foreground placeholder:text-foreground/40'
            />
            <Button
              type='button'
              className='w-full'
              onClick={() => uploadMutation.mutate()}
              disabled={!selectedFile || uploadMutation.isPending}
            >
              {uploadMutation.isPending ? (
                <>
                  <LoaderCircle className='h-4 w-4 animate-spin' />
                  업로드 중…
                </>
              ) : (
                'Upload & Queue'
              )}
            </Button>
          </div>

          {feedback ? (
            <p
              aria-live='polite'
              className='mt-3 rounded-lg border border-white/10 bg-black/20 px-3 py-2 text-xs text-foreground/80'
            >
              {feedback}
            </p>
          ) : null}
        </article>

        <article className='rounded-2xl border border-white/10 bg-linear-to-br from-white/[0.09] to-white/[0.03] p-4'>
          <div className='flex flex-wrap items-center gap-2'>
            <div className='relative w-full max-w-sm'>
              <Search className='pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-foreground/40' />
              <Input
                value={searchKeyword}
                onChange={event => setSearchKeyword(event.target.value)}
                name='documentSearch'
                autoComplete='off'
                placeholder='제목 / 파일명 / 문서ID 검색…'
                className='border-white/20 bg-white/5 pl-9 text-foreground placeholder:text-foreground/40'
              />
            </div>

            <select
              aria-label='문서 상태 필터'
              className='h-9 rounded-md border border-white/20 bg-white/5 px-3 text-sm text-foreground'
              value={statusFilter}
              onChange={event => setStatusFilter(event.target.value as 'ALL' | DocumentStatus)}
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
              onClick={() => documentsQuery.refetch()}
              disabled={documentsQuery.isFetching}
            >
              <RefreshCw
                className={documentsQuery.isFetching ? 'h-4 w-4 animate-spin' : 'h-4 w-4'}
              />
              새로고침
            </Button>
          </div>

          <div className='mt-4 overflow-hidden rounded-xl border border-white/10'>
            <div
              className='max-h-[420px] overflow-auto'
              style={{ contentVisibility: 'auto', containIntrinsicSize: '420px' }}
            >
              <table className='w-full min-w-[760px] border-collapse text-sm'>
                <thead className='sticky top-0 bg-black/30 text-left text-xs uppercase tracking-wide text-foreground/60'>
                  <tr>
                    <th className='px-4 py-3'>Title</th>
                    <th className='px-4 py-3'>Status</th>
                    <th className='px-4 py-3'>Source</th>
                    <th className='px-4 py-3'>Updated</th>
                    <th className='px-4 py-3 text-right'>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {documentsQuery.isLoading ? (
                    <tr>
                      <td className='px-4 py-8 text-center text-foreground/60' colSpan={5}>
                        문서 목록을 불러오는 중…
                      </td>
                    </tr>
                  ) : filteredDocuments.length === 0 ? (
                    <tr>
                      <td className='px-4 py-8 text-center text-foreground/60' colSpan={5}>
                        표시할 문서가 없습니다.
                      </td>
                    </tr>
                  ) : (
                    filteredDocuments.map(document => (
                      <tr key={document.id} className='border-t border-white/5'>
                        <td className='px-4 py-3'>
                          <p className='max-w-[280px] truncate font-medium text-white'>
                            {document.title}
                          </p>
                          <p className='mt-1 text-xs text-foreground/55'>
                            {toShortId(document.id)}
                          </p>
                        </td>
                        <td className='px-4 py-3'>
                          <span
                            className={`inline-flex rounded-full border px-2.5 py-1 text-xs ${statusTone[document.status]}`}
                          >
                            {statusLabel[document.status]}
                          </span>
                        </td>
                        <td className='px-4 py-3 text-foreground/80'>{document.sourceType}</td>
                        <td className='px-4 py-3 text-foreground/70'>
                          {formatDateTime(document.updatedAt || document.createdAt)}
                        </td>
                        <td className='px-4 py-3'>
                          <div className='flex justify-end gap-2'>
                            <Button type='button' variant='outline' size='sm' asChild>
                              <Link to={`/documents/${document.id}`}>상세</Link>
                            </Button>
                            <Button
                              type='button'
                              variant='outline'
                              size='sm'
                              onClick={() => reindexMutation.mutate(document.id)}
                              disabled={pendingReindexDocumentId === document.id}
                            >
                              {pendingReindexDocumentId === document.id ? '처리중…' : '재인덱싱'}
                            </Button>
                            <Button
                              type='button'
                              variant='destructive'
                              size='sm'
                              onClick={() => {
                                const confirmed = window.confirm(
                                  `문서 "${document.title}"을(를) 삭제할까요? 이 작업은 되돌릴 수 없습니다.`
                                )
                                if (!confirmed) {
                                  return
                                }
                                deleteMutation.mutate(document.id)
                              }}
                              disabled={
                                pendingDeleteDocumentId === document.id ||
                                document.status === 'PROCESSING'
                              }
                            >
                              {pendingDeleteDocumentId === document.id ? (
                                '삭제중…'
                              ) : (
                                <>
                                  <Trash2 className='h-4 w-4' />
                                  삭제
                                </>
                              )}
                            </Button>
                          </div>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>

          {documentsQuery.isError ? (
            <p className='mt-3 text-xs text-amber-200'>
              목록 조회에 실패했습니다. 관리자 권한(ADMIN)과 API 연결 상태를 확인해주세요.
            </p>
          ) : null}
        </article>
      </section>
    </div>
  )
}
