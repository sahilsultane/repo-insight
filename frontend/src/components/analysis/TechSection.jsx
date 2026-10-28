import { FiLayers } from 'react-icons/fi'

const CATEGORY_COLORS = {
  WEB:           'bg-blue-100 text-blue-700 border-blue-200',
  ORM:           'bg-green-100 text-green-700 border-green-200',
  SECURITY:      'bg-red-100 text-red-700 border-red-200',
  DATABASE:      'bg-purple-100 text-purple-700 border-purple-200',
  TESTING:       'bg-yellow-100 text-yellow-700 border-yellow-200',
  UTILITY:       'bg-gray-100 text-gray-700 border-gray-200',
  MIGRATION:     'bg-orange-100 text-orange-700 border-orange-200',
  CACHE:         'bg-cyan-100 text-cyan-700 border-cyan-200',
  MESSAGING:     'bg-pink-100 text-pink-700 border-pink-200',
  DOCUMENTATION: 'bg-indigo-100 text-indigo-700 border-indigo-200',
  TEMPLATE:      'bg-teal-100 text-teal-700 border-teal-200',
  DEFAULT:       'bg-gray-100 text-gray-600 border-gray-200',
}

export default function TechSection({ languages, frameworks, dependencies }) {
  if (!languages?.length && !frameworks?.length) return null

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <div className="flex items-center gap-2 mb-5">
        <div className="w-8 h-8 bg-indigo-100 rounded-lg flex items-center justify-center">
          <FiLayers className="w-4 h-4 text-indigo-600" />
        </div>
        <h2 className="text-lg font-bold text-gray-800">Technology Stack</h2>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Languages */}
        {languages?.length > 0 && (
          <div>
            <h3 className="text-xs font-bold uppercase tracking-wider text-gray-400 mb-3">
              Languages Detected
            </h3>
            <div className="flex flex-wrap gap-2">
              {languages.map((lang) => (
                <span key={lang} className="px-3 py-1 bg-slate-100 text-slate-700 border border-slate-200 rounded-full text-sm font-medium">
                  {lang}
                </span>
              ))}
            </div>
          </div>
        )}

        {/* Frameworks */}
        {frameworks?.length > 0 && (
          <div>
            <h3 className="text-xs font-bold uppercase tracking-wider text-gray-400 mb-3">
              Frameworks & Libraries
            </h3>
            <div className="flex flex-wrap gap-2">
              {frameworks.map((fw) => {
                const colorClass = CATEGORY_COLORS[fw.category] || CATEGORY_COLORS.DEFAULT
                return (
                  <div key={fw.name} className="group relative">
                    <span className={`px-3 py-1 border rounded-full text-sm font-medium cursor-default ${colorClass}`}>
                      {fw.name}
                      {fw.version && fw.version !== 'managed' && (
                        <span className="ml-1 opacity-60 text-xs">v{fw.version}</span>
                      )}
                    </span>
                    {fw.detectionSource && (
                      <span className="absolute bottom-full left-1/2 -translate-x-1/2 mb-1 px-2 py-1 bg-gray-900 text-white text-xs rounded whitespace-nowrap opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none z-10">
                        from {fw.detectionSource}
                      </span>
                    )}
                  </div>
                )
              })}
            </div>
          </div>
        )}
      </div>

      {/* Dependency count */}
      {dependencies?.length > 0 && (
        <p className="text-xs text-gray-400 mt-4 border-t border-gray-100 pt-4">
          {dependencies.length} total dependencies detected from build file.
        </p>
      )}
    </div>
  )
}
