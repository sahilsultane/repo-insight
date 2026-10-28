import { FiDatabase, FiKey, FiLink } from 'react-icons/fi'
import { AiUnavailableBanner } from './ProjectSummary.jsx'

const AI_HINTS = ['ℹ️', 'AI explanation unavailable', 'Configure']
function isUnavailable(text) {
  if (!text) return false
  return AI_HINTS.some((h) => text.includes(h))
}

export default function DatabaseSection({ dbInfo, tables, explanation }) {
  const hasTables = tables?.length > 0

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <div className="flex items-center gap-2 mb-5">
        <div className="w-8 h-8 bg-green-100 rounded-lg flex items-center justify-center">
          <FiDatabase className="w-4 h-4 text-green-600" />
        </div>
        <h2 className="text-lg font-bold text-gray-800">Database Overview</h2>
      </div>

      {/* DB Info tiles */}
      {dbInfo && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-5">
          <InfoTile label="Technology"   value={dbInfo.technology}    highlight={dbInfo.confidence === 'DETECTED'} />
          <InfoTile label="Detected From" value={dbInfo.detectionMethod} />
          <InfoTile label="Uses JPA"     value={dbInfo.usesJpa      ? 'Yes' : 'No'} />
          <InfoTile label="Uses Hibernate" value={dbInfo.usesHibernate ? 'Yes' : 'No'} />
        </div>
      )}

      {/* AI Explanation */}
      {explanation && (
        <div className="mb-5">
          {isUnavailable(explanation) ? (
            <AiUnavailableBanner />
          ) : (
            <div className="bg-green-50 border border-green-100 rounded-xl p-4">
              <p className="text-xs font-bold uppercase tracking-wider text-green-600 mb-1.5">AI Explanation</p>
              <p className="text-sm text-gray-700 leading-relaxed whitespace-pre-wrap">{explanation}</p>
            </div>
          )}
        </div>
      )}

      {/* Tables */}
      {hasTables ? (
        <div>
          <h3 className="text-sm font-bold text-gray-700 mb-3">
            Detected Tables / Entities ({tables.length})
          </h3>
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3">
            {tables.map((table) => (
              <TableCard key={table.tableName} table={table} />
            ))}
          </div>
        </div>
      ) : (
        <div className="text-sm text-gray-400 italic bg-gray-50 rounded-xl p-4 border border-gray-200">
          No database tables detected. The repository may not use a relational database,
          or the schema could not be extracted.
        </div>
      )}
    </div>
  )
}

function TableCard({ table }) {
  return (
    <div className="border border-gray-200 rounded-xl overflow-hidden">
      <div className="bg-[#0f172a] text-white px-4 py-2.5">
        <div className="flex items-center justify-between">
          <span className="font-bold text-sm">{table.tableName}</span>
          <span className="text-xs text-gray-400">
            {table.detectionMethod === 'JPA_ENTITY' ? '⚡ JPA' : '📄 SQL'}
          </span>
        </div>
        {table.entityClass && (
          <p className="text-xs text-blue-300 mt-0.5">entity: {table.entityClass}</p>
        )}
      </div>
      <div className="divide-y divide-gray-100">
        {table.columns?.map((col) => (
          <div key={col.columnName} className="flex items-center justify-between px-4 py-1.5 text-sm">
            <div className="flex items-center gap-1.5">
              {col.primaryKey && <FiKey  className="w-3 h-3 text-yellow-500 flex-shrink-0" />}
              {col.foreignKey && <FiLink className="w-3 h-3 text-blue-500  flex-shrink-0" />}
              {!col.primaryKey && !col.foreignKey && <span className="w-3" />}
              <span className={`font-mono ${col.primaryKey ? 'font-bold text-gray-800' : 'text-gray-700'}`}>
                {col.columnName}
              </span>
            </div>
            <div className="flex items-center gap-1.5">
              <span className="text-xs text-gray-400 font-mono">{col.dataType}</span>
              {col.primaryKey && <span className="text-xs bg-yellow-100 text-yellow-700 rounded px-1">PK</span>}
              {col.foreignKey && (
                <span className="text-xs bg-blue-100 text-blue-700 rounded px-1">
                  FK→{col.referencedTable}
                </span>
              )}
              {!col.nullable && !col.primaryKey && (
                <span className="text-xs bg-red-50 text-red-500 rounded px-1">NN</span>
              )}
            </div>
          </div>
        ))}
        {(!table.columns || table.columns.length === 0) && (
          <p className="px-4 py-2 text-xs text-gray-400 italic">No column details available</p>
        )}
      </div>
    </div>
  )
}

function InfoTile({ label, value, highlight }) {
  return (
    <div className="bg-gray-50 border border-gray-200 rounded-xl px-4 py-3">
      <p className="text-xs text-gray-400 mb-0.5">{label}</p>
      <p className={`text-sm font-semibold ${highlight ? 'text-green-600' : 'text-gray-700'}`}>
        {value || 'Not detected'}
      </p>
    </div>
  )
}
