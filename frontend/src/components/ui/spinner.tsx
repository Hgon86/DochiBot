import { Loader2 } from 'lucide-react'

import { cn } from '@/shared/lib'

type SpinnerProps = {
  className?: string
}

export const Spinner = ({ className }: SpinnerProps) => {
  return <Loader2 className={cn('animate-spin', className)} aria-hidden='true' />
}
