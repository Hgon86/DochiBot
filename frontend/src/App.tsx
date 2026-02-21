import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ErrorBoundary } from 'react-error-boundary'
import { RouterProvider } from 'react-router-dom'

import { router } from '@/app/router'

const queryClient = new QueryClient()

const App = () => {
  return (
    <QueryClientProvider client={queryClient}>
      <ErrorBoundary
        fallbackRender={({ error, resetErrorBoundary }) => (
          <div className='mx-auto max-w-3xl px-6 py-16'>
            <h1 className='text-2xl font-semibold'>Something went wrong</h1>
            <p className='mt-2 wrap-break-word text-sm text-zinc-600'>{String(error)}</p>
            <button
              type='button'
              className='mt-6 rounded-lg bg-zinc-950 px-4 py-2 text-sm font-semibold text-white'
              onClick={resetErrorBoundary}
            >
              Reload UI
            </button>
          </div>
        )}
      >
        <RouterProvider router={router} />
      </ErrorBoundary>
    </QueryClientProvider>
  )
}

export default App
