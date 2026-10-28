const STEPS = [
  { key: 'FETCHING',   label: 'Fetching repository' },
  { key: 'ANALYZING',  label: 'Analyzing code' },
  { key: 'GENERATING', label: 'Generating AI explanations' },
  { key: 'COMPLETED',  label: 'Done' },
]

const ORDER = { PENDING: 0, FETCHING: 1, ANALYZING: 2, GENERATING: 3, COMPLETED: 4, FAILED: 5 }

export default function ProgressSteps({ status }) {
  const currentOrder = ORDER[status?.status] ?? 0
  const msg = status?.statusMessage

  return (
    <div>
      {/* Message */}
      {msg && (
        <p className="text-sm text-blue-200 mb-3 text-center">{msg}</p>
      )}

      {/* Steps */}
      <div className="flex items-center justify-center gap-1">
        {STEPS.map((step, i) => {
          const stepOrder = ORDER[step.key]
          const done = currentOrder > stepOrder
          const active = currentOrder === stepOrder
          return (
            <div key={step.key} className="flex items-center gap-1">
              <div className="flex flex-col items-center">
                <div className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold transition-colors
                  ${done   ? 'bg-green-500 text-white' :
                    active ? 'bg-blue-400 text-white animate-pulse' :
                             'bg-white/20 text-gray-400'}`}>
                  {done ? '✓' : i + 1}
                </div>
                <span className={`text-xs mt-1 whitespace-nowrap ${active ? 'text-blue-300' : 'text-gray-500'}`}>
                  {step.label}
                </span>
              </div>
              {i < STEPS.length - 1 && (
                <div className={`h-0.5 w-8 mb-4 rounded ${done ? 'bg-green-500' : 'bg-white/20'}`} />
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
