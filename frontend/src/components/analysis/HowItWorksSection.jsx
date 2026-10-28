import { FiZap } from 'react-icons/fi'
import { AiUnavailableBanner } from './ProjectSummary.jsx'

const AI_HINTS = ['ℹ️', 'AI explanation unavailable', 'Configure']

function isUnavailable(text) {
  if (!text) return false
  return AI_HINTS.some((h) => text.includes(h))
}

export default function HowItWorksSection({ explanation }) {
  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <div className="flex items-center gap-2 mb-4">
        <div className="w-8 h-8 bg-orange-100 rounded-lg flex items-center justify-center">
          <FiZap className="w-4 h-4 text-orange-600" />
        </div>
        <h2 className="text-lg font-bold text-gray-800">How It Works</h2>
        <span className="ml-auto text-xs bg-orange-50 text-orange-600 border border-orange-200 rounded-full px-2.5 py-0.5 font-medium">
          AI Generated
        </span>
      </div>

      {isUnavailable(explanation) ? (
        <AiUnavailableBanner />
      ) : (
        <div className="bg-orange-50 border border-orange-100 rounded-xl p-5">
          {explanation ? (
            <p className="text-gray-700 leading-relaxed whitespace-pre-wrap text-sm">{explanation}</p>
          ) : (
            <p className="text-gray-400 italic text-sm">How-it-works explanation not available.</p>
          )}
        </div>
      )}
    </div>
  )
}
