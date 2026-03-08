export type DocumentSourceType = 'PDF' | 'TEXT'

export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export type IngestionJobStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

export type UserRole = 'USER' | 'ADMIN'

export type AuthProvider = 'CREDENTIALS' | 'GOOGLE'

export type MeResponse = {
  id: string
  username: string
  role: UserRole
  provider: AuthProvider
  createdAt: string | null
}

export type DocumentResponse = {
  id: string
  title: string
  sourceType: DocumentSourceType
  originalFilename: string | null
  storageUri: string | null
  status: DocumentStatus
  errorMessage: string | null
  createdByUserId: string | null
  createdAt: string | null
  updatedAt: string | null
}

export type ListDocumentsResponse = {
  items: DocumentResponse[]
}

export type CreateDocumentUploadUrlResponse = {
  documentId: string
  bucket: string
  key: string
  storageUri: string
  uploadUrl: string
  method: string
  expiresInSeconds: number
  requiredHeaders: Record<string, string>
}

export type FinalizeDocumentUploadResponse = {
  documentId: string
  status: DocumentStatus
  ingestionJobId: string
}

export type ReindexDocumentResponse = {
  jobId: string
  status: IngestionJobStatus
}

export type CreateDocumentDownloadUrlResponse = {
  downloadUrl: string
  expiresInSeconds: number
}

export type IngestionJobResponse = {
  id: string
  documentId: string
  status: IngestionJobStatus
  chunkCount: number | null
  embeddingModel: string | null
  embeddingDims: number | null
  attemptCount: number
  maxAttempts: number
  nextRunAt: string | null
  startedAt: string | null
  finishedAt: string | null
  errorMessage: string | null
  createdAt: string | null
}

export type ListIngestionJobsResponse = {
  items: IngestionJobResponse[]
}

export type ChatCitation = {
  documentId: string
  documentTitle: string
  snippet: string
  page: number | null
  section: string | null
  score: number | null
}

export type ChatResponse = {
  answer: string
  citations: ChatCitation[]
  sessionId: string
}

export type ChatStreamMetadataEvent = {
  sessionId: string | null
  citations: ChatCitation[]
}

export type ChatStreamDeltaEvent = {
  delta: string | null
}

export type ChatStreamDoneEvent = {
  sessionId: string | null
  answer: string | null
}

export type ChatStreamErrorEvent = {
  sessionId: string | null
  message: string | null
}

export type HealthResponse = {
  status: string
}
