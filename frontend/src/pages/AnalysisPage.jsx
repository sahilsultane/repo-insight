import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { analysisApi } from '../services/api.js'
import ProgressSteps from '../components/ProgressSteps.jsx'
import RepoHeader from '../components/analysis/RepoHeader.jsx'
import ProjectSummary from '../components/analysis/ProjectSummary.jsx'
import TechSection from '../components/analysis/TechSection.jsx'
import CodebaseSection from '../components/analysis/CodebaseSection.jsx'
import DatabaseSection from '../components/analysis/DatabaseSection.jsx'
import KeyFilesSection from '../components/analysis/KeyFilesSection.jsx'
import ArchitectureSection from '../components/analysis/ArchitectureSection.jsx'
import ErDiagramSection from '../components/analysis/ErDiagramSection.jsx'
import HowItWorksSection from '../components/analysis/HowItWorksSection.jsx'
import AiChatSection from '../components/analysis/AiChatSection.jsx'
import { FiArrowLeft, FiRefreshCw } from 'react-icons/fi'

const POLL_MS = 2500

export default function AnalysisPage() {
  const { analysisId } = useParams()
  const navigate = useNavigate()
  const [status, setStatus] = useState(null)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [activeSection, setActiveSection] = useState('summary')

  useEffect(() => {
    let interval
    let cancelled = false

    async function poll() {
      try {
        const statusRes = await analysisApi.getStatus(analysisId)
        if (cancelled) return
        setStatus(statusRes.data)

        if (statusRes.data.status === 'COMPLETED') {
          clearInterval(interval)
          const resultRes = await analysisApi.getResult(analysisId)
          if (!cancelled) setResult(resultRes.data)
        } else if (statusRes.data.status === 'FAILED') {
          clearInterval(interval)
          if (!cancelled) setError(statusRes.data.errorMessage || 'Analysis failed.')
        }
      } catch (e) {
        if (!cancelled) { setError(e.message); clearInterval(interval) }
      }
    }

    poll()
    interval = setInterval(poll, POLL_MS)
    return () => { cancelled = true; clearInterval(interval) }
  }, [analysisId])

  const SECTIONS = [
    { id: 'summary',      label: 'Summary' },
    { id: 'tech',         label: 'Tech Stack' },
    { id: 'codebase',     label: 'Codebase' },
    { id: 'database',     label: 'Database' },
    { id: 'keyfiles',     label: 'Key Files' },
    { id: 'architecture', label: 'Architecture' },
    { id: 'er',           label: 'ER Diagram' },
    { id: 'howitworks',   label: 'How It Works' },
    { id: 'chat',         label: 'AI Chat' },
  ]

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen p-8">
        <div className="bg-red-50 border border-red-200 rounded-2xl p-8 max-w-lg text-center">
          <div className="text-4xl mb-4">⚠️</div>
          <h2 className="text-xl font-bold text-red-700 mb-2">Analysis Failed</h2>
          <p className="text-red-600 mb-6">{error}</p>
          <button
            onClick={() => navigate('/')}
            className="flex items-center gap-2 mx-auto px-5 py-2.5 bg-red-600 hover:bg-red-500 text-white rounded-lg font-medium"
          >
            <FiArrowLeft className="w-4 h-4" /> Try Another Repository
          </button>
        </div>
      </div>
    )
  }

  if (!result) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen p-8 bg-gradient-to-br from-[#0f172a] to-[#1e3a8a]">
        <div className="text-center text-white max-w-lg">
          <div className="w-16 h-16 border-4 border-blue-400 border-t-transparent rounded-full animate-spin mx-auto mb-6" />
          <h2 className="text-2xl font-bold mb-2">Analyzing Repository</h2>
          <p className="text-gray-300 text-sm mb-8">
            Fetching files, running AST analysis, and generating AI explanations...
          </p>
          {status && <ProgressSteps status={status} />}
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Top nav bar */}
      <div className="bg-white border-b border-gray-200 sticky top-0 z-40">
        <div className="max-w-6xl mx-auto px-6 py-3 flex items-center gap-4">
          <button
            onClick={() => navigate('/')}
            className="flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-800 transition-colors"
          >
            <FiArrowLeft className="w-4 h-4" /> New Analysis
          </button>
          <div className="h-4 w-px bg-gray-300" />
          <span className="text-sm font-semibold text-gray-700 truncate">
            {result.repositoryInfo?.fullName || analysisId}
          </span>
          <div className="flex-1" />
          <div className="flex items-center gap-1 overflow-x-auto scrollbar-thin">
            {SECTIONS.map((s) => (
              <button
                key={s.id}
                onClick={() => {
                  setActiveSection(s.id)
                  document.getElementById(s.id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
                }}
                className={`whitespace-nowrap px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
                  activeSection === s.id
                    ? 'bg-blue-600 text-white'
                    : 'text-gray-500 hover:bg-gray-100'
                }`}
              >
                {s.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      <div className="max-w-6xl mx-auto px-6 py-8 space-y-8">
        <div id="summary">
          <RepoHeader info={result.repositoryInfo} />
          <div className="mt-6">
            <ProjectSummary
              summary={result.projectSummary}
              repoInfo={result.repositoryInfo}
            />
          </div>
        </div>

        <div id="tech">
          <TechSection
            languages={result.detectedLanguages}
            frameworks={result.detectedFrameworks}
            dependencies={result.detectedDependencies}
          />
        </div>

        <div id="codebase">
          <CodebaseSection
            fileTree={result.fileTree}
            analyzedFiles={result.analyzedFiles}
            analysisId={analysisId}
            repoInfo={result.repositoryInfo}
          />
        </div>

        <div id="database">
          <DatabaseSection
            dbInfo={result.databaseInfo}
            tables={result.databaseTables}
            explanation={result.databaseExplanation}
          />
        </div>

        <div id="keyfiles">
          <KeyFilesSection keyFiles={result.keyFiles} />
        </div>

        <div id="architecture">
          <ArchitectureSection
            graph={result.architectureGraph}
            explanation={result.architectureExplanation}
          />
        </div>

        <div id="er">
          <ErDiagramSection
            tables={result.databaseTables}
          />
        </div>

        <div id="howitworks">
          <HowItWorksSection explanation={result.howItWorksExplanation} />
        </div>

        <div id="chat">
          <AiChatSection analysisId={analysisId} />
        </div>
      </div>
    </div>
  )
}
