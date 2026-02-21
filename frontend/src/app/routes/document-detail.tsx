import { Link, useParams } from 'react-router-dom'

export const DocumentDetailPage = () => {
  const { id } = useParams()

  return (
    <div className='mx-auto max-w-3xl px-6 py-16'>
      <h1 className='text-2xl font-semibold'>Document Detail</h1>
      <p className='mt-2 text-sm text-zinc-600'>Skeleton page (layoutless)</p>
      <div className='mt-6 rounded-xl border border-zinc-200 bg-white p-4'>
        <div className='text-xs text-zinc-500'>documentId</div>
        <div className='mt-1 font-mono text-sm'>{id}</div>
      </div>
      <div className='mt-6'>
        <Link className='text-sm underline' to='/documents'>
          Back to documents
        </Link>
      </div>
    </div>
  )
}
