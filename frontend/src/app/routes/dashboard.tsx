import { Link } from 'react-router-dom'

export const DashboardPage = () => {
  return (
    <div className='mx-auto max-w-3xl px-6 py-16'>
      <h1 className='text-2xl font-semibold'>Dashboard</h1>
      <p className='mt-2 text-sm text-zinc-600'>Skeleton page (layoutless)</p>
      <nav className='mt-6 flex flex-wrap gap-3'>
        <Link className='text-sm underline' to='/documents'>
          Documents
        </Link>
        <Link className='text-sm underline' to='/ingestion-jobs'>
          Ingestion Jobs
        </Link>
        <Link className='text-sm underline' to='/chat'>
          Chat Playground
        </Link>
        <Link className='text-sm underline' to='/login'>
          Login
        </Link>
      </nav>
    </div>
  )
}
