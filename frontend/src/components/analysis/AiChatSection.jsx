import { useState, useRef, useEffect } from 'react'
import { analysisApi } from '../../services/api.js'
import { FiMessageSquare, FiSend, FiUser, FiCpu } from 'react-icons/fi'

const SUGGESTED_QUESTIONS = [
  'Where does user login happen?',
  'Which class connects to the database?',
  'What REST endpoints does this app expose?',
  'What is the main purpose of this project?',
  'How does data flow from controller to database?',
]

export default function AiChatSection({ analysisId }) {
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const bottomRef = useRef(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  async function sendMessage(question) {
    const q = question || input.trim()
    if (!q || loading) return

    setInput('')
    setMessages((prev) => [...prev, { role: 'user', content: q }])
    setLoading(true)

    try {
      const res = await analysisApi.ask(analysisId, q)
      setMessages((prev) => [...prev, { role: 'assistant', content: res.data.answer }])
    } catch (err) {
      setMessages((prev) => [
        ...prev,
        { role: 'assistant', content: `Error: ${err.message}`, isError: true },
      ])
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <div className="flex items-center gap-2 mb-5">
        <div className="w-8 h-8 bg-pink-100 rounded-lg flex items-center justify-center">
          <FiMessageSquare className="w-4 h-4 text-pink-600" />
        </div>
        <h2 className="text-lg font-bold text-gray-800">Ask About This Repository</h2>
        <span className="ml-auto text-xs bg-pink-50 text-pink-600 border border-pink-200 rounded-full px-2.5 py-0.5 font-medium">
          AI Chat
        </span>
      </div>

      {/* Suggested questions */}
      {messages.length === 0 && (
        <div className="mb-4">
          <p className="text-xs text-gray-400 mb-2 font-medium">Suggested questions:</p>
          <div className="flex flex-wrap gap-2">
            {SUGGESTED_QUESTIONS.map((q) => (
              <button
                key={q}
                onClick={() => sendMessage(q)}
                disabled={loading}
                className="text-xs px-3 py-1.5 bg-gray-100 hover:bg-blue-50 hover:text-blue-700 border border-gray-200 hover:border-blue-200 rounded-full transition-colors disabled:opacity-50"
              >
                {q}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Chat messages */}
      <div className="bg-gray-50 border border-gray-200 rounded-xl p-4 h-72 overflow-y-auto scrollbar-thin space-y-3 mb-4">
        {messages.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full text-gray-400 text-sm">
            <FiMessageSquare className="w-8 h-8 mb-2 opacity-30" />
            <p>Ask a question about this repository</p>
            <p className="text-xs mt-1 opacity-70">Answers are grounded in the actual analysis results</p>
          </div>
        ) : (
          messages.map((msg, i) => (
            <div key={i} className={`flex gap-2.5 ${msg.role === 'user' ? 'flex-row-reverse' : ''}`}>
              <div className={`w-7 h-7 rounded-full flex-shrink-0 flex items-center justify-center
                ${msg.role === 'user' ? 'bg-blue-600' : 'bg-gray-200'}`}>
                {msg.role === 'user'
                  ? <FiUser className="w-3.5 h-3.5 text-white" />
                  : <FiCpu className="w-3.5 h-3.5 text-gray-600" />}
              </div>
              <div className={`max-w-[80%] rounded-xl px-4 py-2.5 text-sm leading-relaxed
                ${msg.role === 'user'
                  ? 'bg-blue-600 text-white rounded-tr-none'
                  : msg.isError
                    ? 'bg-red-50 border border-red-200 text-red-600'
                    : 'bg-white border border-gray-200 text-gray-700 rounded-tl-none'}`}>
                <p className="whitespace-pre-wrap">{msg.content}</p>
              </div>
            </div>
          ))
        )}

        {loading && (
          <div className="flex gap-2.5">
            <div className="w-7 h-7 rounded-full bg-gray-200 flex items-center justify-center">
              <FiCpu className="w-3.5 h-3.5 text-gray-600" />
            </div>
            <div className="bg-white border border-gray-200 rounded-xl rounded-tl-none px-4 py-3">
              <div className="flex gap-1">
                {[0, 1, 2].map((i) => (
                  <span
                    key={i}
                    className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"
                    style={{ animationDelay: `${i * 0.15}s` }}
                  />
                ))}
              </div>
            </div>
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      {/* Input */}
      <form
        onSubmit={(e) => { e.preventDefault(); sendMessage() }}
        className="flex gap-2"
      >
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Ask anything about this repository..."
          disabled={loading}
          className="flex-1 px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50 bg-white"
        />
        <button
          type="submit"
          disabled={loading || !input.trim()}
          className="px-4 py-2.5 bg-blue-600 hover:bg-blue-500 disabled:opacity-50 disabled:cursor-not-allowed text-white rounded-xl transition-colors"
        >
          <FiSend className="w-4 h-4" />
        </button>
      </form>
    </div>
  )
}
