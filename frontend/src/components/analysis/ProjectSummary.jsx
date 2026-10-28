import { FiInfo, FiAlertCircle } from 'react-icons/fi'

const AI_UNAVAILABLE_HINTS = [
  'ℹ️ AI explanation not available',
  'ℹ️ Gemini API key is invalid',
  'AI explanation unavailable',
  'Configure OPENAI_API_KEY',
]

function isAiUnavailable(text) {
  if (!text) return false
  return AI_UNAVAILABLE_HINTS.some((hint) => text.includes(hint))
}

export default function ProjectSummary({ summary }) {
  const unavailable = isAiUnavailable(summary)

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <div className="flex items-center gap-2 mb-4">
        <div className="w-8 h-8 bg-blue-100 rounded-lg flex items-center justify-center">
          <FiInfo className="w-4 h-4 text-blue-600" />
        </div>
        <h2 className="text-lg font-bold text-gray-800">Project Summary</h2>
        <span className="ml-auto text-xs bg-blue-50 text-blue-600 border border-blue-200 rounded-full px-2.5 py-0.5 font-medium">
          AI Generated
        </span>
      </div>

      {unavailable ? (
        <AiUnavailableBanner />
      ) : (
        <p className="text-gray-700 leading-relaxed whitespace-pre-wrap">
          {summary || 'Generating summary...'}
        </p>
      )}
    </div>
  )
}

export function AiUnavailableBanner() {
  return (
    <div className="flex items-start gap-3 bg-amber-50 border border-amber-200 rounded-xl p-4">
      <FiAlertCircle className="w-5 h-5 text-amber-500 flex-shrink-0 mt-0.5" />
      <div>
        <p className="text-sm font-semibold text-amber-700 mb-1">AI Key Not Configured</p>
        <p className="text-sm text-amber-600">
          The repository was analysed successfully — all code, database, and architecture data is shown below.
          To also see AI-generated text summaries, add a valid Gemini API key to your{' '}
          <code className="bg-amber-100 px-1 rounded">.env</code> file.
        </p>
        <a
          href="https://aistudio.google.com/app/apikey"
          target="_blank"
          rel="noopener noreferrer"
          className="inline-block mt-2 text-xs text-amber-700 underline hover:text-amber-900"
        >
          Get a free Gemini key → aistudio.google.com/app/apikey
        </a>
      </div>
    </div>
  )
}
