import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAnalysis } from '../hooks/useAnalysis.js'
import ProgressSteps from '../components/ProgressSteps.jsx'
import TechStackCard from '../components/TechStackCard.jsx'
import {
  FiGithub, FiZap, FiSearch, FiCode, FiDatabase, FiGrid
} from 'react-icons/fi'

const EXAMPLE_REPOS = [
  'https://github.com/spring-projects/spring-petclinic',
  'https://github.com/eugenp/tutorials',
  'https://github.com/gothinkster/spring-boot-realworld-example-app',
]

export default function HomePage() {
  const [url, setUrl] = useState('')
  const [urlError, setUrlError] = useState('')
  const navigate = useNavigate()
  const { startAnalysis, status, loading, error, analysisId } = useAnalysis()

  function validateUrl(value) {
    const pattern = /^https:\/\/github\.com\/[a-zA-Z0-9._-]+\/[a-zA-Z0-9._-]+/
    return pattern.test(value.trim())
  }

  async function handleAnalyze(e) {
    e.preventDefault()
    setUrlError('')

    if (!url.trim()) {
      setUrlError('Please enter a GitHub repository URL.')
      return
    }
    if (!validateUrl(url)) {
      setUrlError('Must be a valid GitHub URL: https://github.com/owner/repository')
      return
    }

    await startAnalysis(url.trim())
  }

  // Navigate to result page when done
  if (analysisId && status?.status === 'COMPLETED') {
    navigate(`/analysis/${analysisId}`)
    return null
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Hero */}
      <div className="bg-gradient-to-br from-[#0f172a] via-[#1e3a8a] to-[#0f172a] text-white px-8 py-16">
        <div className="max-w-3xl mx-auto text-center">
          <div className="inline-flex items-center gap-2 bg-blue-500/20 border border-blue-500/30 rounded-full px-4 py-1.5 text-sm text-blue-300 mb-6">
            <FiZap className="w-3.5 h-3.5" />
            AI-Powered · Real Analysis · No Fake Data
          </div>

          <h1 className="text-4xl md:text-5xl font-extrabold leading-tight mb-4">
            Understand Any
            <span className="text-blue-400"> GitHub Repository</span>
          </h1>
          <p className="text-lg text-gray-300 mb-10 max-w-2xl mx-auto">
            Paste a GitHub URL and get a simple, easy-to-understand explanation of the project,
            its codebase, and database — with interactive architecture and ER diagrams.
          </p>

          {/* Input Form */}
          <form onSubmit={handleAnalyze} className="bg-white/10 backdrop-blur rounded-2xl p-6 border border-white/20">
            <div className="flex flex-col sm:flex-row gap-3">
              <div className="flex-1 relative">
                <FiGithub className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
                <input
                  type="text"
                  value={url}
                  onChange={(e) => { setUrl(e.target.value); setUrlError('') }}
                  placeholder="https://github.com/username/repository"
                  className="w-full pl-12 pr-4 py-3.5 bg-white text-gray-900 rounded-xl text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
                  disabled={loading}
                />
              </div>
              <button
                type="submit"
                disabled={loading}
                className="flex items-center justify-center gap-2 px-6 py-3.5 bg-blue-600 hover:bg-blue-500 disabled:opacity-60 disabled:cursor-not-allowed text-white font-semibold rounded-xl transition-colors whitespace-nowrap"
              >
                {loading ? (
                  <>
                    <svg className="animate-spin w-4 h-4" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
                    </svg>
                    Analyzing...
                  </>
                ) : (
                  <>
                    <FiSearch className="w-4 h-4" />
                    Analyze Repository
                  </>
                )}
              </button>
            </div>

            {urlError && (
              <p className="mt-2 text-sm text-red-300 text-left">{urlError}</p>
            )}

            {/* Progress */}
            {loading && status && (
              <div className="mt-5">
                <ProgressSteps status={status} />
              </div>
            )}

            {/* Error */}
            {error && (
              <div className="mt-4 p-3 bg-red-500/20 border border-red-400/30 rounded-lg text-red-200 text-sm text-left">
                {error}
              </div>
            )}
          </form>

          {/* Example repos */}
          <div className="mt-5 flex flex-wrap justify-center gap-2">
            <span className="text-xs text-gray-400 self-center">Try:</span>
            {EXAMPLE_REPOS.map((r) => (
              <button
                key={r}
                onClick={() => setUrl(r)}
                className="text-xs bg-white/10 hover:bg-white/20 border border-white/20 rounded-full px-3 py-1 text-gray-300 transition-colors font-mono"
              >
                {r.replace('https://github.com/', '')}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Features */}
      <div className="max-w-5xl mx-auto px-8 py-16">
        <h2 className="text-2xl font-bold text-center text-gray-800 mb-10">
          What You Get
        </h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
          {[
            { icon: FiSearch,   color: 'blue',   title: 'Repository Overview',    desc: 'Stars, forks, license, description, and detected technologies — fetched live from GitHub.' },
            { icon: FiCode,     color: 'indigo', title: 'Codebase Explanation',   desc: 'Interactive file tree. Click any file to see its purpose, classes, endpoints, and AI explanation.' },
            { icon: FiDatabase, color: 'green',  title: 'Database Analysis',      desc: 'Detects MySQL/PostgreSQL from config. Extracts JPA entities and SQL scripts into a full schema.' },
            { icon: FiGrid,     color: 'purple', title: 'Architecture Diagram',   desc: 'Auto-generated React Flow graph showing Controller → Service → Repository → Database layers.' },
            { icon: FiGrid,     color: 'orange', title: 'ER Diagram',             desc: 'Interactive entity-relationship diagram built from actual JPA entities and SQL CREATE statements.' },
            { icon: FiZap,      color: 'pink',   title: 'AI Chat',                desc: 'Ask questions like "Where does login happen?" and get answers grounded in real detected files.' },
          ].map(({ icon: Icon, color, title, desc }) => (
            <div key={title} className="bg-white rounded-xl border border-gray-200 p-6 shadow-sm hover:shadow-md transition-shadow">
              <div className={`w-10 h-10 rounded-lg bg-${color}-100 flex items-center justify-center mb-4`}>
                <Icon className={`w-5 h-5 text-${color}-600`} />
              </div>
              <h3 className="font-semibold text-gray-800 mb-2">{title}</h3>
              <p className="text-sm text-gray-500 leading-relaxed">{desc}</p>
            </div>
          ))}
        </div>

        {/* Tech Stack */}
        <div className="mt-16">
          <h2 className="text-2xl font-bold text-center text-gray-800 mb-3">Built With</h2>
          <p className="text-center text-gray-500 text-sm mb-10">The full technology stack powering this platform</p>
          <TechStackCard />
        </div>
      </div>
    </div>
  )
}
