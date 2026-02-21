import { Link } from 'react-router-dom'

export const DocumentsPage = () => {
  return (
    <div className='mx-auto max-w-3xl px-6 py-16'>
      <h1 className='text-2xl font-semibold'>Documents</h1>
      <p className='mt-2 text-sm text-zinc-600'>Skeleton page (layoutless)</p>
      <div className='mt-6 flex flex-wrap gap-3'>
        <Link className='text-sm underline' to='/'>
          Dashboard
        </Link>
        <Link className='text-sm underline' to='/documents/00000000-0000-0000-0000-000000000000'>
          Example document detail
        </Link>
      </div>
    </div>
  )
}
