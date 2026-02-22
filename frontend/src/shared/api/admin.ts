import { api, publicApi } from '@/shared/api/http'
import type {
  ChatResponse,
  CreateDocumentDownloadUrlResponse,
  CreateDocumentUploadUrlResponse,
  DocumentResponse,
  DocumentStatus,
  FinalizeDocumentUploadResponse,
  HealthResponse,
  IngestionJobStatus,
  ListDocumentsResponse,
  ListIngestionJobsResponse,
  MeResponse,
  ReindexDocumentResponse,
} from '@/shared/api/types'

const supportedMarkdownExtensions = ['.md', '.markdown'] as const

const detectExtension = (filename: string) => {
  const normalized = filename.trim().toLowerCase()
  if (normalized.endsWith('.pdf')) {
    return '.pdf'
  }
  if (supportedMarkdownExtensions.some(extension => normalized.endsWith(extension))) {
    return '.md'
  }
  return null
}

const detectSourceType = (filename: string): 'PDF' | 'TEXT' => {
  const extension = detectExtension(filename)
  if (extension === '.pdf') {
    return 'PDF'
  }
  if (extension === '.md') {
    return 'TEXT'
  }
  throw new Error('PDF(.pdf) 또는 Markdown(.md, .markdown) 파일만 업로드할 수 있습니다.')
}

const detectContentType = (file: File): string => {
  const extension = detectExtension(file.name)
  if (extension === '.pdf') {
    return 'application/pdf'
  }
  if (extension === '.md') {
    return 'text/markdown'
  }
  throw new Error('PDF(.pdf) 또는 Markdown(.md, .markdown) 파일만 업로드할 수 있습니다.')
}

export const isSupportedUploadFilename = (filename: string) => {
  return detectExtension(filename) !== null
}

export const getCurrentUser = () => {
  return api.get('auth/me').json<MeResponse>()
}

export const logout = () => {
  return publicApi.post('auth/logout').json<{ success: boolean }>()
}

export const listDocuments = (params?: {
  status?: DocumentStatus
  limit?: number
  offset?: number
}) => {
  const searchParams = new URLSearchParams()
  searchParams.set('limit', String(params?.limit ?? 100))
  searchParams.set('offset', String(params?.offset ?? 0))
  if (params?.status) {
    searchParams.set('status', params.status)
  }

  return api.get('documents', { searchParams }).json<ListDocumentsResponse>()
}

export const getDocument = (documentId: string) => {
  return api.get(`documents/${documentId}`).json<DocumentResponse>()
}

export const uploadDocument = async (input: { file: File; title?: string }) => {
  const contentType = detectContentType(input.file)
  const sourceType = detectSourceType(input.file.name)

  const upload = await api
    .post('documents/upload-url', {
      json: {
        originalFilename: input.file.name,
        contentType,
      },
    })
    .json<CreateDocumentUploadUrlResponse>()

  const uploadHeaders = new Headers(upload.requiredHeaders)
  if (!uploadHeaders.has('content-type')) {
    uploadHeaders.set('content-type', contentType)
  }

  const uploadResult = await fetch(upload.uploadUrl, {
    method: upload.method || 'PUT',
    headers: uploadHeaders,
    body: input.file,
  })
  if (!uploadResult.ok) {
    throw new Error(`파일 업로드에 실패했습니다. (${uploadResult.status})`)
  }

  return api
    .post('documents', {
      json: {
        documentId: upload.documentId,
        title: input.title?.trim() || input.file.name,
        sourceType,
        originalFilename: input.file.name,
        storageUri: upload.storageUri,
      },
    })
    .json<FinalizeDocumentUploadResponse>()
}

export const reindexDocument = (documentId: string) => {
  return api.post(`documents/${documentId}/reindex`).json<ReindexDocumentResponse>()
}

export const getDocumentDownloadUrl = (documentId: string) => {
  return api.get(`documents/${documentId}/download-url`).json<CreateDocumentDownloadUrlResponse>()
}

export const listIngestionJobs = (params?: {
  documentId?: string
  limit?: number
  offset?: number
}) => {
  const searchParams = new URLSearchParams()
  searchParams.set('limit', String(params?.limit ?? 100))
  searchParams.set('offset', String(params?.offset ?? 0))
  if (params?.documentId) {
    searchParams.set('documentId', params.documentId)
  }

  return api.get('ingestion-jobs', { searchParams }).json<ListIngestionJobsResponse>()
}

export const sendChatMessage = (payload: { message: string; sessionId?: string; topK: number }) => {
  return api.post('chat', { json: payload }).json<ChatResponse>()
}

export const getHealthStatus = () => {
  return publicApi.get('health').json<HealthResponse>()
}

export const fetchDashboardSummary = async () => {
  const [documents, jobs] = await Promise.all([
    listDocuments({ limit: 100, offset: 0 }),
    listIngestionJobs({ limit: 100, offset: 0 }),
  ])

  return {
    documents: documents.items,
    jobs: jobs.items,
  }
}

export const toShortId = (value: string) => `${value.slice(0, 8)}...${value.slice(-4)}`

export const formatDateTime = (value: string | null | undefined) => {
  if (!value) {
    return '-'
  }
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value))
}

export const isUuid = (value: string) => {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)
}

type StatusStyleKey = DocumentStatus | IngestionJobStatus

export const statusTone: Record<StatusStyleKey, string> = {
  PENDING: 'border-amber-300/40 bg-amber-500/10 text-amber-200',
  PROCESSING: 'border-sky-300/40 bg-sky-500/10 text-sky-200',
  COMPLETED: 'border-emerald-300/40 bg-emerald-500/10 text-emerald-200',
  FAILED: 'border-rose-300/40 bg-rose-500/10 text-rose-200',
  QUEUED: 'border-amber-300/40 bg-amber-500/10 text-amber-200',
  RUNNING: 'border-sky-300/40 bg-sky-500/10 text-sky-200',
  SUCCEEDED: 'border-emerald-300/40 bg-emerald-500/10 text-emerald-200',
}

export const statusLabel: Record<StatusStyleKey, string> = {
  PENDING: '대기',
  PROCESSING: '처리중',
  COMPLETED: '완료',
  FAILED: '실패',
  QUEUED: '대기',
  RUNNING: '실행중',
  SUCCEEDED: '성공',
}
