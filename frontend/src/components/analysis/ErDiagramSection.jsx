import { useState, useMemo, useCallback } from 'react'
import ReactFlow, {
  Background, Controls, MiniMap,
  useNodesState, useEdgesState,
  Handle, Position, Panel,
} from 'reactflow'
import 'reactflow/dist/style.css'
import { FiDatabase, FiKey, FiLink, FiMaximize2, FiMinimize2, FiInfo } from 'react-icons/fi'

// ─── Table node ───────────────────────────────────────────────────────────────
function TableNode({ data }) {
  const { tableName, columns = [], detectionMethod, entityClass } = data
  const isSql = detectionMethod === 'SQL_CREATE'
  const headerBg = isSql ? '#1e3a5f' : '#4338ca'
  const borderColor = isSql ? '#1e3a5f' : '#6366f1'

  return (
    <div className="bg-white rounded-xl overflow-hidden shadow-md"
      style={{ border: `2px solid ${borderColor}`, minWidth: 230 }}>

      {/* invisible handles for each column — precise edge routing */}
      {columns.map((col, i) => (
        <div key={`handles-${col.columnName}-${i}`}>
          {col.primaryKey && (
            <Handle
              type="target"
              position={Position.Left}
              id={`${tableName}__${col.columnName}__pk`}
              style={{ top: 48 + i * 28, left: -6, opacity: 0, width: 10, height: 10 }}
            />
          )}
          {col.foreignKey && (
            <Handle
              type="source"
              position={Position.Right}
              id={`${tableName}__${col.columnName}__fk`}
              style={{ top: 48 + i * 28, right: -6, opacity: 0, width: 10, height: 10 }}
            />
          )}
        </div>
      ))}

      {/* Fallback handles */}
      <Handle type="target" position={Position.Top} id={`${tableName}__top`} style={{ opacity: 0 }} />
      <Handle type="source" position={Position.Bottom} id={`${tableName}__bottom`} style={{ opacity: 0 }} />

      {/* Header */}
      <div className="px-3 py-2.5" style={{ background: headerBg }}>
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-1.5">
            <FiDatabase className="w-3.5 h-3.5 text-white/80 flex-shrink-0" />
            <span className="font-bold text-sm text-white">{tableName}</span>
          </div>
          <span className="text-xs text-white/60 flex-shrink-0">{isSql ? 'SQL' : 'JPA'}</span>
        </div>
        {entityClass && (
          <p className="text-xs text-blue-200/70 mt-0.5 font-mono">{entityClass}</p>
        )}
      </div>

      {/* Columns */}
      <div className="divide-y divide-gray-100">
        {columns.slice(0, 16).map((col) => (
          <div
            key={col.columnName}
            className={`flex items-center justify-between px-3 py-1.5 text-xs
              ${col.primaryKey && col.foreignKey ? 'bg-purple-50'
                : col.primaryKey ? 'bg-yellow-50'
                : col.foreignKey ? 'bg-blue-50'
                : 'bg-white'}`}
          >
            <div className="flex items-center gap-1.5">
              {col.primaryKey && col.foreignKey
                ? <span className="text-purple-500 font-bold text-xs">🔑🔗</span>
                : col.primaryKey
                  ? <FiKey className="w-3 h-3 text-yellow-500 flex-shrink-0" title="Primary Key" />
                  : col.foreignKey
                    ? <FiLink className="w-3 h-3 text-blue-500 flex-shrink-0" title={`FK → ${col.referencedTable}.${col.referencedColumn || 'id'}`} />
                    : <span className="w-3 flex-shrink-0" />}
              <span className={`font-mono ${col.primaryKey ? 'font-bold text-gray-900' : 'text-gray-700'}`}>
                {col.columnName}
              </span>
            </div>
            <div className="flex items-center gap-1 ml-2">
              <span className="text-gray-400 font-mono">{col.dataType}</span>
              {col.primaryKey && !col.foreignKey && (
                <span className="bg-yellow-100 text-yellow-700 font-bold text-xs px-1 rounded ml-1">PK</span>
              )}
              {col.foreignKey && !col.primaryKey && (
                <span className="bg-blue-100 text-blue-700 font-bold text-xs px-1 rounded ml-1"
                  title={`→ ${col.referencedTable}.${col.referencedColumn || 'id'}`}>FK</span>
              )}
              {col.primaryKey && col.foreignKey && (
                <span className="bg-purple-100 text-purple-700 font-bold text-xs px-1 rounded ml-1">PK+FK</span>
              )}
              {!col.nullable && !col.primaryKey && (
                <span className="text-red-400 text-xs">NN</span>
              )}
            </div>
          </div>
        ))}
        {columns.length > 16 && (
          <div className="px-3 py-1.5 text-xs text-gray-400 italic bg-gray-50">
            +{columns.length - 16} more columns
          </div>
        )}
        {columns.length === 0 && (
          <div className="px-3 py-2 text-xs text-gray-400 italic">No columns detected</div>
        )}
      </div>
    </div>
  )
}

const nodeTypes = { DB_TABLE: TableNode }

// ─── Layout algorithm ─────────────────────────────────────────────────────────
function layoutTables(tables) {
  // Build adjacency to group related tables together
  const cols = Math.min(3, Math.ceil(Math.sqrt(tables.length)))
  const XGAP = 380, YGAP = 40
  const nodes = []
  const colHeights = Array(cols).fill(80)

  tables.forEach((table, i) => {
    const col = i % cols
    const x = col * XGAP + 60
    const y = colHeights[col]
    const nodeHeight = 56 + Math.min(16, table.columns?.length || 0) * 28 + 20
    colHeights[col] += nodeHeight + YGAP

    nodes.push({
      id: `table_${table.tableName}`,
      type: 'DB_TABLE',
      position: { x, y },
      data: {
        tableName: table.tableName,
        columns: table.columns || [],
        detectionMethod: table.detectionMethod,
        entityClass: table.entityClass,
      },
    })
  })
  return nodes
}

function buildEdges(tables) {
  const edges = []
  const seen = new Set()

  tables.forEach(table => {
    if (!table.columns) return
    table.columns.forEach(col => {
      if (!col.foreignKey || !col.referencedTable) return
      const targetExists = tables.some(t => t.tableName.toLowerCase() === col.referencedTable.toLowerCase())
      if (!targetExists) return

      const edgeId = `${table.tableName}.${col.columnName}->${col.referencedTable}.${col.referencedColumn || 'id'}`
      if (seen.has(edgeId)) return
      seen.add(edgeId)

      const refCol = col.referencedColumn || 'id'
      const sourceHandle = `${table.tableName}__${col.columnName}__fk`
      const targetHandle = `${col.referencedTable}__${refCol}__pk`

      edges.push({
        id: edgeId,
        source: `table_${table.tableName}`,
        target: `table_${col.referencedTable}`,
        sourceHandle,
        targetHandle,
        type: 'smoothstep',
        animated: false,
        label: `${col.columnName} → ${refCol}`,
        labelStyle: { fontSize: 10, fill: '#64748b', fontWeight: 500 },
        labelBgStyle: { fill: '#ffffff', fillOpacity: 0.95 },
        labelBgPadding: [4, 2],
        labelBgBorderRadius: 4,
        style: { stroke: '#3b82f6', strokeWidth: 2 },
        markerEnd: { type: 'arrowclosed', color: '#3b82f6', width: 14, height: 14 },
        data: {
          sourceTable: table.tableName,
          sourceColumn: col.columnName,
          targetTable: col.referencedTable,
          targetColumn: refCol,
          cardinality: 'N:1',
          evidence: `FOREIGN KEY (${col.columnName}) REFERENCES ${col.referencedTable}(${refCol})`,
        },
      })
    })
  })
  return edges
}

// ─── Edge detail panel ────────────────────────────────────────────────────────
function EdgePanel({ edge, onClose }) {
  if (!edge) return null
  const d = edge.data || {}
  return (
    <div className="bg-white border border-gray-200 rounded-xl shadow-xl p-4 w-72 text-xs">
      <div className="flex items-center justify-between mb-3">
        <span className="font-bold text-sm text-gray-800">Relationship Detail</span>
        <button onClick={onClose} className="text-gray-400 hover:text-gray-700 text-base leading-none">×</button>
      </div>
      <div className="space-y-2">
        <Row label="From"        value={`${d.sourceTable}.${d.sourceColumn}`} mono />
        <Row label="To"          value={`${d.targetTable}.${d.targetColumn}`} mono />
        <Row label="Type"        value="FOREIGN KEY" />
        <Row label="Cardinality" value={d.cardinality || 'N:1'} />
        {d.evidence && <Row label="Evidence" value={d.evidence} mono />}
      </div>
    </div>
  )
}

function Row({ label, value, mono }) {
  return (
    <div className="flex items-start gap-2">
      <span className="text-gray-400 w-20 flex-shrink-0">{label}:</span>
      <span className={`text-gray-800 font-medium break-all ${mono ? 'font-mono' : ''}`}>{value}</span>
    </div>
  )
}

// ─── Main ─────────────────────────────────────────────────────────────────────
export default function ErDiagramSection({ tables }) {
  const initialNodes = useMemo(() => layoutTables(tables || []), [tables])
  const initialEdges = useMemo(() => buildEdges(tables || []), [tables])

  const [nodes, , onNodesChange] = useNodesState(initialNodes)
  const [edges, , onEdgesChange] = useEdgesState(initialEdges)
  const [selectedEdge, setSelectedEdge] = useState(null)
  const [fullscreen, setFullscreen] = useState(false)

  const onEdgeClick  = useCallback((_, e) => setSelectedEdge(e), [])
  const onPaneClick  = useCallback(() => setSelectedEdge(null), [])

  const pkCount = useMemo(() =>
    (tables || []).flatMap(t => t.columns || []).filter(c => c.primaryKey).length, [tables])
  const fkCount = useMemo(() =>
    (tables || []).flatMap(t => t.columns || []).filter(c => c.foreignKey).length, [tables])

  const hasData = nodes.length > 0

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      {/* Header */}
      <div className="flex items-center gap-2 mb-4">
        <div className="w-8 h-8 bg-blue-100 rounded-lg flex items-center justify-center">
          <FiDatabase className="w-4 h-4 text-blue-600" />
        </div>
        <h2 className="text-lg font-bold text-gray-800">Database ER Diagram</h2>
        {hasData && (
          <span className="text-xs text-gray-400 ml-1">
            {(tables || []).length} table{(tables || []).length !== 1 ? 's' : ''} · {initialEdges.length} relationship{initialEdges.length !== 1 ? 's' : ''}
          </span>
        )}
        <button
          onClick={() => setFullscreen(f => !f)}
          className="ml-auto p-1.5 rounded-lg hover:bg-gray-100 text-gray-500 transition-colors"
          title={fullscreen ? 'Exit fullscreen' : 'Fullscreen'}
        >
          {fullscreen ? <FiMinimize2 className="w-4 h-4" /> : <FiMaximize2 className="w-4 h-4" />}
        </button>
      </div>

      {/* Stats */}
      {hasData && (
        <div className="flex flex-wrap gap-3 mb-4">
          {[
            { icon: '🔑', label: 'Primary Keys', val: pkCount, c: 'bg-yellow-50 border-yellow-200 text-yellow-700' },
            { icon: '🔗', label: 'Foreign Keys', val: fkCount, c: 'bg-blue-50 border-blue-200 text-blue-700' },
            { icon: '↔', label: 'Relationships', val: initialEdges.length, c: 'bg-green-50 border-green-200 text-green-700' },
          ].map(s => (
            <div key={s.label} className={`flex items-center gap-2 text-xs border rounded-lg px-3 py-1.5 font-medium ${s.c}`}>
              <span>{s.icon}</span><span>{s.val} {s.label}</span>
            </div>
          ))}
        </div>
      )}

      {/* Diagram */}
      {hasData ? (
        <div
          className="border border-gray-200 rounded-xl overflow-hidden relative"
          style={{ height: fullscreen ? '80vh' : 540 }}
        >
          <ReactFlow
            nodes={nodes} edges={edges}
            onNodesChange={onNodesChange} onEdgesChange={onEdgesChange}
            nodeTypes={nodeTypes}
            onEdgeClick={onEdgeClick} onPaneClick={onPaneClick}
            fitView fitViewOptions={{ padding: 0.12 }}
            minZoom={0.1} maxZoom={2}
            attributionPosition="bottom-right"
          >
            <Background color="#e5e7eb" gap={20} />
            <Controls />
            <MiniMap zoomable pannable nodeStrokeWidth={2} />
            {selectedEdge && (
              <Panel position="bottom-left">
                <EdgePanel edge={selectedEdge} onClose={() => setSelectedEdge(null)} />
              </Panel>
            )}
          </ReactFlow>
        </div>
      ) : (
        <div className="bg-gray-50 border border-gray-200 rounded-xl p-8 text-center text-gray-400">
          <FiDatabase className="w-10 h-10 mx-auto mb-3 opacity-30" />
          <p className="text-sm">No database tables detected.</p>
          <p className="text-xs mt-1">Supported: SQL CREATE TABLE, JPA @Entity, Hibernate</p>
        </div>
      )}

      {/* Legend */}
      {hasData && (
        <div className="flex flex-wrap items-center gap-5 mt-3 pt-3 border-t border-gray-100 text-xs text-gray-500">
          <span className="flex items-center gap-1.5">
            <FiKey className="w-3 h-3 text-yellow-500" />
            <span className="bg-yellow-100 text-yellow-700 px-1.5 rounded font-bold">PK</span>
            Primary Key
          </span>
          <span className="flex items-center gap-1.5">
            <FiLink className="w-3 h-3 text-blue-400" />
            <span className="bg-blue-100 text-blue-700 px-1.5 rounded font-bold">FK</span>
            Foreign Key
          </span>
          <span className="flex items-center gap-1.5">
            <svg width="28" height="8"><line x1="0" y1="4" x2="20" y2="4" stroke="#3b82f6" strokeWidth="2"/><polygon points="16,1 24,4 16,7" fill="#3b82f6"/></svg>
            Relationship
          </span>
          <span className="flex items-center gap-1.5 ml-auto text-gray-400 italic">
            <FiInfo className="w-3 h-3" /> Click an edge for details
          </span>
        </div>
      )}
    </div>
  )
}
