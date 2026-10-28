import { useState } from 'react'
import { FiStar, FiChevronDown, FiChevronUp } from 'react-icons/fi'

export default function KeyFilesSection({ keyFiles }) {
  if (!keyFiles?.length) return null

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <div className="flex items-center gap-2 mb-5">
        <div className="w-8 h-8 bg-yellow-100 rounded-lg flex items-center justify-center">
          <FiStar className="w-4 h-4 text-yellow-600" />
        </div>
        <h2 className="text-lg font-bold text-gray-800">Key Files Walkthrough</h2>
        <span className="ml-1 text-xs text-gray-400">({keyFiles.length} files)</span>
      </div>

      <div className="space-y-3">
        {keyFiles.map((kf) => (
          <KeyFileCard key={kf.filePath} kf={kf} />
        ))}
      </div>
    </div>
  )
}

function KeyFileCard({ kf }) {
  const [open, setOpen] = useState(false)
  const fileName = kf.fileName || kf.filePath?.split('/').pop()

  return (
    <div className="border border-gray-200 rounded-xl overflow-hidden">
      <button
        onClick={() => setOpen(!open)}
        className="w-full flex items-start gap-3 px-4 py-3.5 hover:bg-gray-50 transition-colors text-left"
      >
        {/* Score bar */}
        <div className="flex-shrink-0 mt-1">
          <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center text-white font-bold text-sm">
            {kf.importanceScore ?? '?'}
          </div>
        </div>

        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="font-bold text-gray-800">{fileName}</span>
            <span className="font-mono text-xs text-gray-400 truncate">{kf.filePath}</span>
          </div>
          <p className="text-sm text-gray-500 mt-0.5 line-clamp-2">{kf.reason}</p>
        </div>

        <div className="flex-shrink-0 text-gray-400">
          {open ? <FiChevronUp /> : <FiChevronDown />}
        </div>
      </button>

      {open && (
        <div className="px-4 pb-4 border-t border-gray-100 pt-3 space-y-3">
          {kf.purpose && (
            <div>
              <p className="text-xs font-bold uppercase tracking-wider text-gray-400 mb-1">Purpose</p>
              <p className="text-sm text-gray-600">{kf.purpose}</p>
            </div>
          )}
          {kf.aiExplanation && (
            <div>
              <p className="text-xs font-bold uppercase tracking-wider text-blue-500 mb-1">
                AI Explanation
              </p>
              <p className="text-sm text-gray-600 bg-blue-50 border border-blue-100 rounded-lg p-3 leading-relaxed">
                {kf.aiExplanation}
              </p>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
