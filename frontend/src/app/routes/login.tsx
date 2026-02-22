import { AlertCircle } from 'lucide-react'
import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/shared/auth/use-auth'

type LoginLocationState = {
  from?: {
    pathname?: string
    search?: string
    hash?: string
  }
}

export const LoginPage = () => {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const { login, loginWithGoogle, isLoading, error } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()

  const from = (location.state as LoginLocationState | null)?.from
  const redirectTo = from?.pathname ? `${from.pathname}${from.search ?? ''}${from.hash ?? ''}` : '/'

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    try {
      await login(email, password)
      navigate(redirectTo, { replace: true })
    } catch (_err) {
      // Error is handled by auth context
    }
  }

  return (
    <div className='relative flex min-h-screen items-center justify-center overflow-hidden px-4'>
      {/* Animated gradient background */}
      <div className='absolute inset-0 bg-linear-to-br from-[oklch(0.15_0.04_264)] via-[oklch(0.12_0.03_280)] to-[oklch(0.10_0.02_300)]' />

      {/* Floating gradient orbs for depth */}
      <div className='absolute left-1/4 top-1/4 h-96 w-96 animate-pulse rounded-full bg-[oklch(0.65_0.24_264)] opacity-20 blur-3xl motion-reduce:animate-none' />
      <div className='absolute bottom-1/4 right-1/4 h-80 w-80 animate-pulse rounded-full bg-[oklch(0.55_0.18_300)] opacity-20 blur-3xl animation-delay-2000 motion-reduce:animate-none' />

      {/* Login container */}
      <div className='relative z-10 w-full max-w-md space-y-8'>
        {/* Logo and Header */}
        <div className='text-center'>
          <div className='mb-4 inline-block rounded-2xl bg-linear-to-br from-[oklch(0.65_0.24_264)] to-[oklch(0.55_0.18_300)] p-3'>
            <svg
              className='h-12 w-12 text-white'
              fill='none'
              stroke='currentColor'
              viewBox='0 0 24 24'
            >
              <title>DochiBot Logo</title>
              <path
                strokeLinecap='round'
                strokeLinejoin='round'
                strokeWidth={2}
                d='M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z'
              />
            </svg>
          </div>
          <h1 className='text-balance bg-linear-to-r from-white via-white to-white/80 bg-clip-text text-4xl font-bold tracking-tight text-transparent'>
            DochiBot
          </h1>
          <p className='mt-3 text-base text-foreground/70'>Sign in to access the admin console</p>
        </div>

        {/* Login Form with glassmorphism */}
        <div className='group relative overflow-hidden rounded-2xl border border-white/10 bg-white/5 p-8 shadow-2xl backdrop-blur-xl transition-colors duration-300 hover:border-white/20 hover:bg-white/[0.07]'>
          {/* Subtle gradient overlay */}
          <div className='absolute inset-0 bg-linear-to-br from-white/[0.07] to-transparent opacity-50' />

          <form onSubmit={handleSubmit} className='relative space-y-6'>
            {error && (
              <Alert variant='destructive' className='border-destructive/50 bg-destructive/10'>
                <AlertCircle className='h-4 w-4' />
                <AlertDescription className='text-foreground'>{error}</AlertDescription>
              </Alert>
            )}

            <div className='space-y-2'>
              <Label htmlFor='email' className='text-foreground/90'>
                Email
              </Label>
              <Input
                id='email'
                name='email'
                type='email'
                autoComplete='email'
                spellCheck={false}
                placeholder='admin@dochibot.com…'
                value={email}
                onChange={e => setEmail(e.target.value)}
                required
                disabled={isLoading}
                className='border-white/20 bg-white/5 text-foreground placeholder:text-foreground/40 focus:border-primary focus:ring-primary/50'
              />
            </div>

            <div className='space-y-2'>
              <Label htmlFor='password' className='text-foreground/90'>
                Password
              </Label>
              <Input
                id='password'
                name='password'
                type='password'
                autoComplete='current-password'
                placeholder='••••••••'
                value={password}
                onChange={e => setPassword(e.target.value)}
                required
                disabled={isLoading}
                className='border-white/20 bg-white/5 text-foreground placeholder:text-foreground/40 focus:border-primary focus:ring-primary/50'
              />
            </div>

            <Button
              type='submit'
              className='w-full bg-linear-to-r from-[oklch(0.65_0.24_264)] to-[oklch(0.55_0.18_300)] text-lg font-semibold text-white shadow-lg shadow-primary/25 transition-shadow hover:shadow-xl hover:shadow-primary/40'
              disabled={isLoading}
            >
              {isLoading ? (
                <>
                  <Spinner className='mr-2 h-5 w-5' />
                  Signing in…
                </>
              ) : (
                'Sign in'
              )}
            </Button>

            <div className='relative'>
              <div className='absolute inset-0 flex items-center'>
                <span className='w-full border-t border-white/10' />
              </div>
              <div className='relative flex justify-center text-xs uppercase'>
                <span className='bg-transparent px-3 text-foreground/60'>Or continue with</span>
              </div>
            </div>

            <Button
              type='button'
              variant='outline'
              className='w-full border-white/20 bg-white/5 text-foreground backdrop-blur-sm transition-colors hover:border-white/30 hover:bg-white/10'
              onClick={loginWithGoogle}
              disabled={isLoading}
            >
              <svg className='mr-2 h-5 w-5' viewBox='0 0 24 24'>
                <title>Google</title>
                <path
                  fill='#4285F4'
                  d='M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z'
                />
                <path
                  fill='#34A853'
                  d='M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z'
                />
                <path
                  fill='#FBBC05'
                  d='M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z'
                />
                <path
                  fill='#EA4335'
                  d='M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z'
                />
              </svg>
              Continue with Google
            </Button>
          </form>
        </div>

        <p className='text-center text-sm text-foreground/60'>
          For internal use only. Unauthorized access is prohibited.
        </p>
      </div>
    </div>
  )
}
