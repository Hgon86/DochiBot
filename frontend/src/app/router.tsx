import { createBrowserRouter } from 'react-router-dom'

import { ChatPage } from '@/app/routes/chat'
import { DashboardPage } from '@/app/routes/dashboard'
import { DocumentDetailPage } from '@/app/routes/document-detail'
import { DocumentsPage } from '@/app/routes/documents'
import { IngestionJobsPage } from '@/app/routes/ingestion-jobs'
import { LoginPage } from '@/app/routes/login'
import { OauthCallbackPage } from '@/app/routes/oauth-callback'

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/oauth/callback', element: <OauthCallbackPage /> },
  { path: '/', element: <DashboardPage /> },
  { path: '/documents', element: <DocumentsPage /> },
  { path: '/documents/:id', element: <DocumentDetailPage /> },
  { path: '/ingestion-jobs', element: <IngestionJobsPage /> },
  { path: '/chat', element: <ChatPage /> },
])
