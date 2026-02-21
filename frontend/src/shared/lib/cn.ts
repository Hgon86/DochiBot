import clsx, { type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

/**
 * Tailwind className 헬퍼.
 * - clsx: 조건부 className 생성
 * - tailwind-merge: Tailwind 유틸 충돌/중복 병합
 */
export const cn = (...inputs: ClassValue[]) => twMerge(clsx(inputs))
