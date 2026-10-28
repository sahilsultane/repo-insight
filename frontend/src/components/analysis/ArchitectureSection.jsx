import { useCallback } from 'react'
import ReactFlow, {
  Background,
  Controls,
  MiniMap,
  useNodesState,
  useEdgesState,
} from 'reactflow'
import 'reactflow/dist/style.css'
import { FiGrid } from 'react-icons/fi'

const NODE_COLORS = {
  CLIENT:     { bg: '#f0f9ff', border: '#0ea5e9', text: '#0369a1' },
  CONTROLLER: { bg: '#eff6ff', border: '#3b82f6', text: '#1d4ed8' },
  SERVICE:    { bg: '#f0fdf4', border: '#22c55e', text: '#15803d' },
  REPOSITORY: { bg: '#fef9c3', border: '#eab308', text: '#a16207' },
  ENTITY:     { bg: '#fdf4ff', border: '#a855f7', text: '#7e22ce' },
  CONFIG:     { bg: '#fff7ed', border: '#f97316', text: '#c2410c' },
  DATABASE:   { bg: '#f0fdf4', border: '#16a34a', text: '#15803d' },
  COMPONENT:  { bg: '#f9fafb', border: '#6b7280', text: '#374151' },
  DEFAULT:    { bg: '#f9fafb', border: '#d1d5db', text: '#374151' },
}

function buildFlowNodes(graphNodes) {
  return (graphNodes || []).map((n) => {
    const style = NODE_COLORS[n.type] || NODE_COLORS.DEFAULT
    const classes = n.data?.classes
    const count = n.data?.count
    const tables = n.data?.tables

    return {
      id: n.id,
      type: 'default',
      position: n.position || { x: 0, y: 0 },
      data: {
        label: (
          <div className="text-center px-3 py-2 min-w-[130px]">
            <p className="font-bold text-sm" style={{ color: style.text }}>{n.label}</p>
            {classes && <p className="text-xs mt-0.5 opacity-70">{classes}</p>}
            {count && count > 0 && (
              <p className="text-xs mt-0.5 opacity-60">{count} class{count > 1 ? 'es' : ''}</p>
            )}
            {tables !== undefined && (
              <p className="text-xs mt-0.5 opacity-60">{tables} table{tables !== 1 ? 's' : ''}</p>
            )}
          </div>
        ),
      },
      style: {
        background: style.bg,
        border: `2px solid ${style.border}`,
        borderRadius: '10px',
        padding: 0,
        width: 'auto',
      },
    }
  })
}

function buildFlowEdges(graphEdges) {
  return (graphEdges || []).map((e) => ({
    id: e.id,
    source: e.source,
    target: e.target,
    label: e.label,
    type: 'smoothstep',
    animated: false,
    style: { stroke: '#6366f1', strokeWidth: 2 },
    labelStyle: { fontSize: 11, fill: '#6b7280' },
    labelBgStyle: { fill: '#fff', fillOpacity: 0.8 },
  }))
}

export default function ArchitectureSection({ graph, explanation }) {
  const nodes = buildFlowNodes(graph?.nodes)
  const edges = buildFlowEdges(graph?.edges)
  const [rfNodes, , onNodesChange] = useNodesState(nodes)
  const [rfEdges, , onEdgesChange] = useEdgesState(edges)

  const hasGraph = nodes.length > 0

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <div className="flex items-center gap-2 mb-5">
        <div className="w-8 h-8 bg-purple-100 rounded-lg flex items-center justify-center">
          <FiGrid className="w-4 h-4 text-purple-600" />
        </div>
        <h2 className="text-lg font-bold text-gray-800">Architecture Visualization</h2>
      </div>

      {explanation && (
        <div className="bg-purple-50 border border-purple-100 rounded-xl p-4 mb-5">
          <p className="text-xs font-bold uppercase tracking-wider text-purple-600 mb-1.5">AI Explanation</p>
          <p className="text-sm text-gray-700 leading-relaxed whitespace-pre-wrap">{explanation}</p>
        </div>
      )}

      {hasGraph ? (
        <div className="h-[440px] border border-gray-200 rounded-xl overflow-hidden">
          <ReactFlow
            nodes={rfNodes}
            edges={rfEdges}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            fitView
            fitViewOptions={{ padding: 0.2 }}
            attributionPosition="bottom-right"
          >
            <Background color="#e5e7eb" gap={16} />
            <Controls />
            <MiniMap zoomable pannable />
          </ReactFlow>
        </div>
      ) : (
        <div className="text-sm text-gray-400 italic bg-gray-50 rounded-xl p-4 border border-gray-200">
          Architecture graph could not be generated. The repository may not follow a detectable layered structure.
        </div>
      )}
    </div>
  )
}
