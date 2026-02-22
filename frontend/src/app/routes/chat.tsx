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
          content: response.answer,
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

  return (
    <div className='space-y-5'>
      <section className='rounded-2xl border border-white/10 bg-linear-to-br from-white/[0.10] to-white/[0.03] p-5 shadow-lg shadow-black/10'>
        <h1 className='text-2xl font-semibold text-white'>Chat Playground</h1>
        <p className='mt-2 text-sm text-foreground/70'>
          질문을 보내면 RAG 기반 답변과 citation을 함께 확인할 수 있습니다.
        </p>
      </section>

      <section className='grid gap-4 xl:grid-cols-[2fr_1fr]'>
        <article className='flex min-h-[580px] flex-col rounded-2xl border border-white/10 bg-linear-to-br from-white/[0.09] to-white/[0.03]'>
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
                      ? 'max-w-[85%] rounded-2xl rounded-br-md border border-cyan-300/35 bg-linear-to-br from-cyan-500/25 to-blue-500/20 px-4 py-3 text-sm text-cyan-50'
                      : 'max-w-[90%] space-y-3 rounded-2xl rounded-bl-md border border-white/10 bg-black/25 px-4 py-3 text-sm text-foreground/85'
                  }
                >
                  {message.role === 'assistant' ? (
                    <div className='prose prose-invert prose-sm max-w-none'>
                      <ReactMarkdown remarkPlugins={[remarkGfm]}>{message.content}</ReactMarkdown>
                    </div>
                  ) : (
                    <p className='whitespace-pre-wrap'>{message.content}</p>
                  )}

                  {message.role === 'assistant' && message.citations.length > 0 ? (
                    <div className='space-y-2 rounded-xl border border-white/10 bg-white/[0.03] p-3'>
                      <p className='text-xs uppercase tracking-wide text-foreground/60'>
                        Citations
                      </p>
                      <ul className='space-y-2 text-xs text-foreground/75'>
                        {message.citations.map((citation, index) => (
                          <li key={`${message.id}-${citation.documentId}-${index}`}>
                            <p className='font-medium text-foreground/90'>
                              {citation.documentTitle} ({toShortId(citation.documentId)})
                            </p>
                            <p className='mt-1 line-clamp-2 text-foreground/70'>
                              {citation.snippet}
                            </p>
                            <p className='mt-1 text-foreground/50'>
                              {citation.page ? `p.${citation.page}` : 'page -'}
                              {citation.score ? ` · score ${citation.score.toFixed(3)}` : ''}
                            </p>
                          </li>
                        ))}
                      </ul>
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
              <Input
                value={input}
                onChange={event => setInput(event.target.value)}
                name='chatMessage'
                autoComplete='off'
                placeholder='질문을 입력하세요… 예: 환불 정책은 어떻게 되나요?'
                className='border-white/20 bg-white/5 text-foreground placeholder:text-foreground/40'
              />
              <Button type='submit' disabled={!input.trim() || chatMutation.isPending}>
                <SendHorizontal className='h-4 w-4' />
                전송
              </Button>
            </div>
          </form>
        </article>

        <article className='space-y-4 rounded-2xl border border-white/10 bg-linear-to-br from-white/[0.09] to-white/[0.03] p-4'>
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

          <div className='rounded-xl border border-white/10 bg-black/20 p-3 text-xs text-foreground/70'>
            - Session ID를 유지하면 대화 컨텍스트를 이어서 테스트할 수 있습니다.
            <br />- Top K는 RAG 컨텍스트 청크 수를 의미합니다.
          </div>
        </article>
      </section>
    </div>
  )
}
