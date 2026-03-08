import { useMutation } from '@tanstack/react-query'
import { LoaderCircle, SendHorizontal } from 'lucide-react'
import { useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { sendChatMessage, toShortId } from '@/shared/api/admin'
import type { ChatCitation } from '@/shared/api/types'

type UiMessage = {
  id: string
  role: 'user' | 'assistant'
  content: string
  citations: ChatCitation[]
}

type CitationGroup = {
  documentId: string
  documentTitle: string
  citations: ChatCitation[]
}

const BOX_DRAWING_REGEX = /[\u2500-\u257f]+/g
const SEARCH_PREFIX_REGEX = /\[(?:D|S|B)]\s*/g

const sanitizeAssistantContent = (content: string) => {
  const sanitized = content
    .replace(/<think\b[^>]*>[\s\S]*?<\/think>/gi, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim()

  return sanitized || '응답을 생성하지 못했습니다.'
}

const sanitizeCitationText = (value: string | null | undefined) => {
  if (!value) {
    return ''
  }

  return value
    .replace(SEARCH_PREFIX_REGEX, '')
    .replace(BOX_DRAWING_REGEX, ' ')
    .replace(/\s{2,}/g, ' ')
    .trim()
}

const formatCitationSection = (section: string | null | undefined, documentTitle: string) => {
  const sanitized = sanitizeCitationText(section)
  if (!sanitized) {
    return null
  }

  if (sanitized === documentTitle) {
    return null
  }

  return sanitized.startsWith(`${documentTitle} > `)
    ? sanitized.slice(documentTitle.length + 3)
    : sanitized
}

const groupCitationsByDocument = (citations: ChatCitation[]): CitationGroup[] => {
  const groups = new Map<string, CitationGroup>()

  citations.forEach(citation => {
    const existing = groups.get(citation.documentId)
    if (existing) {
      existing.citations.push(citation)
      return
    }

    groups.set(citation.documentId, {
      documentId: citation.documentId,
      documentTitle: citation.documentTitle,
      citations: [citation],
    })
  })

  return Array.from(groups.values())
}

const createMessageId = () => {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export const ChatPage = () => {
  const [sessionId, setSessionId] = useState('')
  const [topK, setTopK] = useState(8)
  const [input, setInput] = useState('')
  const [messages, setMessages] = useState<UiMessage[]>([
    {
      id: createMessageId(),
      role: 'assistant',
      content:
        '안녕하세요. 이 화면은 RAG + LLM 응답과 citation 확인을 위한 테스트 공간입니다. 질문을 입력해 보세요.',
      citations: [],
    },
  ])

  const chatMutation = useMutation({
    mutationFn: sendChatMessage,
  })

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()

    const message = input.trim()
    if (!message || chatMutation.isPending) {
      return
    }

    setInput('')
    setMessages(prev => [
      ...prev,
      {
        id: createMessageId(),
        role: 'user',
        content: message,
        citations: [],
      },
    ])

    try {
      const response = await chatMutation.mutateAsync({
        message,
        topK,
        sessionId: sessionId.trim() || undefined,
      })

      setSessionId(response.sessionId)
      setMessages(prev => [
        ...prev,
        {
          id: createMessageId(),
          role: 'assistant',
          content: sanitizeAssistantContent(response.answer),
          citations: response.citations,
        },
      ])
    } catch (error) {
      setMessages(prev => [
        ...prev,
        {
          id: createMessageId(),
          role: 'assistant',
          content: error instanceof Error ? error.message : '요청 처리 중 오류가 발생했습니다.',
          citations: [],
        },
      ])
    }
  }

  const handleComposerKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      event.currentTarget.form?.requestSubmit()
    }
  }

  return (
    <div className='space-y-5'>
      <section className='rounded-2xl border border-white/10 bg-linear-to-br from-white/[0.10] to-white/[0.03] p-5 shadow-lg shadow-black/10'>
        <h1 className='text-2xl font-semibold text-white'>Chat Playground</h1>
        <p className='mt-2 text-sm text-foreground/70'>
          질문을 보내면 RAG 기반 답변과 citation을 함께 확인할 수 있습니다.
        </p>
      </section>

      <section className='grid items-start gap-4 xl:grid-cols-[minmax(0,1fr)_320px]'>
        <article className='min-w-0 overflow-hidden rounded-2xl border border-white/10 bg-linear-to-br from-white/[0.09] to-white/[0.03]'>
          <div className='flex min-h-[580px] flex-col'>
            <div className='border-b border-white/10 px-4 py-3 text-sm text-foreground/75'>
              Conversation
            </div>

            <div className='flex-1 space-y-4 overflow-y-auto px-4 py-4'>
              {messages.map(message => (
                <div
                  key={message.id}
                  className={message.role === 'user' ? 'flex justify-end' : 'flex justify-start'}
                >
                  <div
                    className={
                      message.role === 'user'
                        ? 'max-w-[85%] min-w-0 rounded-2xl rounded-br-md border border-cyan-300/35 bg-linear-to-br from-cyan-500/25 to-blue-500/20 px-4 py-3 text-sm text-cyan-50'
                        : 'max-w-[92%] min-w-0 space-y-3 rounded-2xl rounded-bl-md border border-white/10 bg-black/25 px-4 py-3 text-sm text-foreground/85'
                    }
                  >
                    {message.role === 'assistant' ? (
                      <div className='prose prose-invert prose-sm max-w-none break-words'>
                        <ReactMarkdown remarkPlugins={[remarkGfm]}>{message.content}</ReactMarkdown>
                      </div>
                    ) : (
                      <p className='break-words whitespace-pre-wrap'>{message.content}</p>
                    )}

                    {message.role === 'assistant' && message.citations.length > 0 ? (
                      <div className='min-w-0 space-y-2 overflow-hidden rounded-xl border border-white/10 bg-white/[0.03] p-3'>
                        <p className='text-xs uppercase tracking-wide text-foreground/60'>
                          Citations
                        </p>
                        <div className='space-y-3 text-xs text-foreground/75'>
                          {groupCitationsByDocument(message.citations).map(group => (
                            <div
                              key={`${message.id}-${group.documentId}`}
                              className='min-w-0 rounded-lg border border-white/8 bg-black/15 p-3'
                            >
                              <div className='flex flex-wrap items-center gap-2'>
                                <p className='min-w-0 break-all font-medium text-foreground/90'>
                                  {group.documentTitle}
                                </p>
                                <span className='rounded-full border border-white/10 bg-white/[0.04] px-2 py-0.5 text-[11px] text-foreground/60'>
                                  {toShortId(group.documentId)}
                                </span>
                              </div>
                              <div className='mt-2 space-y-2'>
                                {group.citations.map((citation, index) => (
                                  <div
                                    key={`${message.id}-${group.documentId}-${index}`}
                                    className='rounded-md border border-white/8 bg-white/[0.02] p-2.5'
                                  >
                                    <p className='max-h-[4.5rem] overflow-hidden break-all text-foreground/70'>
                                      {sanitizeCitationText(citation.snippet)}
                                    </p>
                                    <div className='mt-2 flex flex-wrap gap-1.5 text-[11px] text-foreground/55'>
                                      <span className='rounded-full border border-white/10 px-2 py-0.5'>
                                        {citation.page ? `p.${citation.page}` : 'page 정보 없음'}
                                      </span>
                                      {formatCitationSection(
                                        citation.section,
                                        group.documentTitle
                                      ) ? (
                                        <span className='max-w-full truncate rounded-full border border-white/10 px-2 py-0.5'>
                                          {formatCitationSection(
                                            citation.section,
                                            group.documentTitle
                                          )}
                                        </span>
                                      ) : null}
                                      {citation.score ? (
                                        <span className='rounded-full border border-white/10 px-2 py-0.5'>
                                          score {citation.score.toFixed(3)}
                                        </span>
                                      ) : null}
                                    </div>
                                  </div>
                                ))}
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>
                    ) : null}
                  </div>
                </div>
              ))}

              {chatMutation.isPending ? (
                <div className='flex justify-start'>
                  <div className='inline-flex items-center gap-2 rounded-xl border border-white/10 bg-black/25 px-3 py-2 text-xs text-foreground/75'>
                    <LoaderCircle className='h-3.5 w-3.5 animate-spin' />
                    답변 생성 중…
                  </div>
                </div>
              ) : null}
            </div>

            <form onSubmit={handleSubmit} className='border-t border-white/10 p-4'>
              <div className='flex items-end gap-2'>
                <textarea
                  value={input}
                  onChange={event => setInput(event.target.value)}
                  onKeyDown={handleComposerKeyDown}
                  name='chatMessage'
                  autoComplete='off'
                  placeholder='질문을 입력하세요… 예: 환불 정책은 어떻게 되나요?'
                  rows={3}
                  className='min-h-[96px] w-full resize-y rounded-md border border-white/20 bg-white/5 px-3 py-2 text-sm text-foreground placeholder:text-foreground/40 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring'
                />
                <Button type='submit' disabled={!input.trim() || chatMutation.isPending}>
                  <SendHorizontal className='h-4 w-4' />
                  전송
                </Button>
              </div>
              <p className='mt-2 text-xs text-foreground/60'>Enter 전송, Shift+Enter 줄바꿈</p>
            </form>
          </div>
        </article>

        <article className='min-w-0 space-y-4 rounded-2xl border border-white/10 bg-linear-to-br from-white/[0.09] to-white/[0.03] p-4 xl:sticky xl:top-6'>
          <div>
            <h2 className='text-sm font-medium text-white'>Chat Options</h2>
            <p className='mt-1 text-xs text-foreground/65'>질답 테스트 설정값을 조절합니다.</p>
          </div>

          <label className='block text-xs text-foreground/65' htmlFor='sessionId'>
            Session ID
          </label>
          <Input
            id='sessionId'
            name='sessionId'
            autoComplete='off'
            value={sessionId}
            onChange={event => setSessionId(event.target.value)}
            placeholder='자동 생성됨 (비워두면 자동)…'
            className='border-white/20 bg-white/5 text-foreground placeholder:text-foreground/40'
          />

          <label className='block text-xs text-foreground/65' htmlFor='topK'>
            Top K: {topK}
          </label>
          <input
            id='topK'
            type='range'
            aria-label='RAG Top K 설정'
            min={1}
            max={12}
            value={topK}
            onChange={event => setTopK(Number(event.target.value))}
            className='w-full accent-primary'
          />

          <div className='space-y-2 rounded-xl border border-white/10 bg-black/20 p-3 text-xs text-foreground/70'>
            <p>Session ID를 유지하면 대화 컨텍스트를 이어서 테스트할 수 있습니다.</p>
            <p>Top K는 RAG 컨텍스트 청크 수를 의미합니다.</p>
          </div>
        </article>
      </section>
    </div>
  )
}
