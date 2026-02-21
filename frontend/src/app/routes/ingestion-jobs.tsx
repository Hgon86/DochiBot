import { Link } from 'react-router-dom'

export const IngestionJobsPage = () => {
  return (
    <div className='mx-auto max-w-3xl px-6 py-16'>
      <h1 className='text-2xl font-semibold'>Ingestion Jobs</h1>
      <p className='mt-2 text-sm text-zinc-600'>Skeleton page (layoutless)</p>
      <div className='mt-6'>
        <Link className='text-sm underline' to='/'>
          Dashboard
        </Link>
      </div>
    </div>
  )
}
