import { createBrowserRouter } from 'react-router-dom'

import { ChatPage } from '@/app/routes/chat'
import { DashboardPage } from '@/app/routes/dashboard'
import { DocumentDetailPage } from '@/app/routes/document-detail'
import { DocumentsPage } from '@/app/routes/documents'
import { IngestionJobsPage } from '@/app/routes/ingestion-jobs'
import { LoginPage } from '@/app/routes/login'
import { MonitoringPage } from '@/app/routes/monitoring'
import { OauthCallbackPage } from '@/app/routes/oauth-callback'
import { AdminShell } from '@/components/layout/admin-shell'
import { RequireAuth } from '@/shared/auth/require-auth'

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/oauth/callback', element: <OauthCallbackPage /> },
  {
    path: '/',
    element: <RequireAuth />,
    children: [
      {
        element: <AdminShell />,
        children: [
          { index: true, element: <DashboardPage /> },
          { path: 'documents', element: <DocumentsPage /> },
          { path: 'documents/:id', element: <DocumentDetailPage /> },
          { path: 'ingestion-jobs', element: <IngestionJobsPage /> },
          { path: 'chat', element: <ChatPage /> },
          { path: 'monitoring', element: <MonitoringPage /> },
        ],
      },
    ],
  },
])
