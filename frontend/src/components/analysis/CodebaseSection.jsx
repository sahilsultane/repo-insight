import { useState, useCallback, useRef } from 'react'
import {
  FiCode, FiFolder, FiFile, FiChevronRight, FiChevronDown,
  FiX, FiSearch, FiLoader, FiExternalLink, FiCopy, FiCheck,
  FiAlertTriangle, FiRefreshCw, FiMaximize2, FiMinimize2,
} from 'react-icons/fi'
import { analysisApi } from '../../services/api.js'

// ─── Language detection ───────────────────────────────────────────────────────
function detectLanguage(path) {
  const ext = path.split('.').pop().toLowerCase()
  const map = {
    java: 'Java', kt: 'Kotlin', kts: 'Kotlin',
    py: 'Python', js: 'JavaScript', jsx: 'JavaScript',
    ts: 'TypeScript', tsx: 'TypeScript', go: 'Go', rs: 'Rust',
    cs: 'C#', cpp: 'C++', cc: 'C++', c: 'C', rb: 'Ruby',
    php: 'PHP', swift: 'Swift', sql: 'SQL', ddl: 'SQL',
    xml: 'XML', html: 'HTML', htm: 'HTML', css: 'CSS',
    scss: 'SCSS', json: 'JSON', yaml: 'YAML', yml: 'YAML',
    md: 'Markdown', mdx: 'Markdown', sh: 'Shell', bash: 'Shell',
    bat: 'Batch', gradle: 'Groovy', toml: 'TOML',
    properties: 'Properties', dockerfile: 'Dockerfile',
  }
  return map[ext] || null
}

function langColor(lang) {
  const c = {
    Java: 'bg-orange-100 text-orange-700',
    Kotlin: 'bg-purple-100 text-purple-700',
    Python: 'bg-blue-100 text-blue-700',
    JavaScript: 'bg-yellow-100 text-yellow-700',
    TypeScript: 'bg-blue-100 text-blue-700',
    SQL: 'bg-green-100 text-green-700',
    HTML: 'bg-red-100 text-red-700',
    CSS: 'bg-pink-100 text-pink-700',
    YAML: 'bg-gray-100 text-gray-700',
    JSON: 'bg-gray-100 text-gray-700',
    Markdown: 'bg-indigo-100 text-indigo-700',
    Groovy: 'bg-teal-100 text-teal-700',
  }
  return c[lang] || 'bg-gray-100 text-gray-600'
}

// ─── Simple line-numbered code viewer ────────────────────────────────────────
function CodeViewer({ content, language }) {
  const [copied, setCopied] = useState(false)
  const [wrap, setWrap] = useState(false)
  const [fullscreen, setFullscreen] = useState(false)

  const lines = content ? content.split('\n') : []

  function handleCopy() {
    navigator.clipboard.writeText(content).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    })
  }

  return (
    <div className={`flex flex-col ${fullscreen ? 'fixed inset-0 z-50 bg-gray-950' : 'h-full'}`}>
      {/* Toolbar */}
      <div className="flex items-center justify-between px-3 py-1.5 bg-gray-800 border-b border-gray-700 flex-shrink-0">
        <div className="flex items-center gap-2 text-xs text-gray-400">
          {language && (
            <span className="bg-gray-700 rounded px-2 py-0.5 font-mono font-bold text-gray-200">
              {language}
            </span>
          )}
          <span>{lines.length} lines · {content?.length?.toLocaleString()} chars</span>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setWrap(w => !w)}
            className={`text-xs px-2 py-0.5 rounded border transition-colors ${
              wrap
                ? 'bg-blue-700 border-blue-500 text-blue-100'
                : 'bg-gray-700 border-gray-600 text-gray-300 hover:bg-gray-600'
            }`}
          >
            Wrap
          </button>
          <button
            onClick={handleCopy}
            className="flex items-center gap-1 text-xs text-gray-400 hover:text-gray-100 transition-colors"
          >
            {copied
              ? <><FiCheck className="w-3.5 h-3.5 text-green-400" /><span className="text-green-400">Copied</span></>
              : <><FiCopy className="w-3.5 h-3.5" />Copy</>}
          </button>
          <button
            onClick={() => setFullscreen(f => !f)}
            className="text-gray-400 hover:text-gray-100 transition-colors"
            title={fullscreen ? 'Exit fullscreen' : 'Fullscreen'}
          >
            {fullscreen
              ? <FiMinimize2 className="w-3.5 h-3.5" />
              : <FiMaximize2 className="w-3.5 h-3.5" />}
          </button>
        </div>
      </div>

      {/* Code lines */}
      <div className="flex-1 overflow-auto bg-gray-950 font-mono text-xs text-gray-100">
        <table className="border-collapse min-w-full">
          <tbody>
            {lines.map((line, i) => (
              <tr key={i} className="hover:bg-white/5 group">
                <td className="text-right select-none text-gray-600 group-hover:text-gray-400 w-10 px-3 py-px border-r border-gray-800 text-right flex-shrink-0 transition-colors">
                  {i + 1}
                </td>
                <td className={`px-4 py-px ${wrap ? 'whitespace-pre-wrap break-all' : 'whitespace-pre'}`}>
                  {line || '\u00A0'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

// ─── File detail panel ─────────────────────────────────────────────────────────
function FileDetail({ path, analysisInfo, analysisId, repoInfo, onClose, allFiles }) {
  const fileName = path.split('/').pop()
  const language = analysisInfo?.language || detectLanguage(path) || 'plaintext'

  const [activeTab, setActiveTab] = useState('code')
  const [fileData, setFileData] = useState(null)      // { content, language, size, lines, githubUrl, sensitive }
  const [loading, setLoading] = useState(false)
  const [fetchError, setFetchError] = useState(null)
  const fetchedRef = useRef(false)

  // Fetch source on mount
  const fetchContent = useCallback(async () => {
    if (fetchedRef.current || loading) return
    fetchedRef.current = true
    setLoading(true)
    setFetchError(null)
    try {
      const res = await analysisApi.getFileContent(analysisId, path)
      setFileData(res.data)
    } catch (e) {
      setFetchError(e.message || 'Failed to load file')
    } finally {
      setLoading(false)
    }
  }, [path, analysisId])

  // Fetch immediately on open
  useState(() => { fetchContent() }, [])

  // Relationships: find files that import or use this class
  const relatedFiles = allFiles?.filter(f => {
    if (f.filePath === path) return false
    const thisName = fileName.replace('.java', '')
    return f.imports?.some(imp => imp.includes(thisName)) ||
           f.dependencies?.some(dep => dep.includes(thisName))
  }).slice(0, 6) || []

  // Database mapping for JPA entities
  const entityClass = analysisInfo?.classes?.find(c => c.springRole === 'ENTITY')

  const tabs = [
    { id: 'code',     label: 'Source Code' },
    { id: 'analysis', label: 'Analysis' },
    { id: 'ai',       label: 'AI Explanation' },
  ]
  if (relatedFiles.length > 0) tabs.push({ id: 'related', label: 'Related' })

  const githubUrl = fileData?.githubUrl ||
    (repoInfo ? `${repoInfo.url}/blob/${repoInfo.defaultBranch || 'main'}/${path}` : null)

  return (
    <div className="flex flex-col h-full bg-white">
      {/* Header */}
      <div className="flex items-start justify-between px-4 py-3 border-b border-gray-200 flex-shrink-0">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="font-bold text-gray-800">{fileName}</span>
            <span className={`text-xs rounded-full px-2 py-0.5 font-medium ${langColor(language)}`}>
              {language}
            </span>
            {analysisInfo?.fileType && analysisInfo.fileType !== 'JAVA_SOURCE' && (
              <span className="text-xs bg-green-100 text-green-700 rounded-full px-2 py-0.5 font-medium">
                {analysisInfo.fileType}
              </span>
            )}
          </div>
          <p className="text-xs text-gray-400 font-mono mt-0.5 truncate">{path}</p>
          {fileData && !fileData.sensitive && (
            <p className="text-xs text-gray-400 mt-0.5">
              {fileData.lines?.toLocaleString()} lines · {fileData.size?.toLocaleString()} chars
            </p>
          )}
        </div>
        <div className="flex items-center gap-1.5 ml-2 flex-shrink-0">
          {githubUrl && (
            <a
              href={githubUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
              title="Open on GitHub"
            >
              <FiExternalLink className="w-4 h-4" />
            </a>
          )}
          <button
            onClick={onClose}
            className="p-1.5 text-gray-400 hover:text-gray-700 hover:bg-gray-100 rounded-lg transition-colors"
          >
            <FiX className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-gray-200 bg-white flex-shrink-0 overflow-x-auto">
        {tabs.map(tab => (
          <button
            key={tab.id}
            onClick={() => { setActiveTab(tab.id); if (tab.id === 'code') fetchContent() }}
            className={`px-4 py-2.5 text-xs font-medium border-b-2 whitespace-nowrap transition-colors ${
              activeTab === tab.id
                ? 'border-blue-600 text-blue-600 bg-blue-50/50'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:bg-gray-50'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Tab content */}
      <div className="flex-1 overflow-hidden">

        {/* ── Source Code tab ── */}
        {activeTab === 'code' && (
          <div className="h-full">
            {loading && (
              <div className="flex items-center justify-center h-full gap-3">
                <FiLoader className="w-5 h-5 animate-spin text-blue-500" />
                <span className="text-sm text-gray-500">Fetching from GitHub...</span>
              </div>
            )}
            {fetchError && !loading && (
              <div className="m-4 p-4 bg-red-50 border border-red-200 rounded-xl">
                <div className="flex items-start gap-2">
                  <FiAlertTriangle className="w-4 h-4 text-red-500 flex-shrink-0 mt-0.5" />
                  <div>
                    <p className="text-sm font-semibold text-red-700">Could not fetch file</p>
                    <p className="text-xs text-red-600 mt-1">{fetchError}</p>
                  </div>
                </div>
                <div className="flex gap-2 mt-3">
                  <button
                    onClick={() => { fetchedRef.current = false; fetchContent() }}
                    className="flex items-center gap-1.5 text-xs text-blue-600 hover:underline"
                  >
                    <FiRefreshCw className="w-3 h-3" /> Retry
                  </button>
                  {githubUrl && (
                    <a href={githubUrl} target="_blank" rel="noopener noreferrer"
                      className="text-xs text-blue-600 hover:underline">
                      Open on GitHub →
                    </a>
                  )}
                </div>
              </div>
            )}
            {fileData && !loading && (
              fileData.sensitive ? (
                <div className="m-4 p-4 bg-amber-50 border border-amber-200 rounded-xl">
                  <p className="text-sm font-semibold text-amber-700">⚠️ Sensitive file</p>
                  <p className="text-sm text-amber-600 mt-1">
                    This file may contain credentials or secrets and its content is not displayed for security.
                  </p>
                  {githubUrl && (
                    <a href={githubUrl} target="_blank" rel="noopener noreferrer"
                      className="text-xs text-blue-600 hover:underline mt-2 block">
                      View on GitHub (requires auth) →
                    </a>
                  )}
                </div>
              ) : fileData.content ? (
                <CodeViewer content={fileData.content} language={fileData.language || language.toLowerCase()} />
              ) : (
                <div className="m-4 p-4 bg-gray-50 border border-gray-200 rounded-xl text-sm text-gray-500 italic">
                  File appears to be empty or binary.
                </div>
              )
            )}
          </div>
        )}

        {/* ── Analysis tab ── */}
        {activeTab === 'analysis' && (
          <div className="p-4 overflow-y-auto h-full space-y-4">
            {analysisInfo ? (
              <>
                {analysisInfo.purpose && (
                  <Section title="Purpose">
                    <p className="text-sm text-gray-700 leading-relaxed">{analysisInfo.purpose}</p>
                  </Section>
                )}

                {analysisInfo.classes?.length > 0 && (
                  <Section title={`Classes / Types (${analysisInfo.classes.length})`}>
                    <div className="space-y-2">
                      {analysisInfo.classes.map(cls => (
                        <div key={cls.name} className="bg-gray-50 border border-gray-200 rounded-xl p-3">
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="font-bold text-sm text-gray-900">{cls.name}</span>
                            <span className="text-xs bg-gray-200 text-gray-700 rounded-full px-2 py-0.5">{cls.type}</span>
                            {cls.springRole && (
                              <span className="text-xs bg-green-100 text-green-700 rounded-full px-2 py-0.5 font-medium">
                                @{cls.springRole}
                              </span>
                            )}
                          </div>
                          {cls.packageName && (
                            <p className="text-xs text-gray-400 font-mono mt-1">{cls.packageName}</p>
                          )}
                          {cls.extendsClass && (
                            <p className="text-xs text-gray-500 mt-1">
                              extends <span className="font-mono text-blue-600">{cls.extendsClass}</span>
                            </p>
                          )}
                          {cls.implementsInterfaces?.length > 0 && (
                            <p className="text-xs text-gray-500 mt-0.5">
                              implements <span className="font-mono text-blue-600">{cls.implementsInterfaces.join(', ')}</span>
                            </p>
                          )}
                          {cls.annotations?.length > 0 && (
                            <div className="flex flex-wrap gap-1 mt-2">
                              {cls.annotations.map(ann => (
                                <span key={ann} className="text-xs font-mono bg-indigo-50 text-indigo-700 rounded px-1.5 py-0.5">
                                  {ann}
                                </span>
                              ))}
                            </div>
                          )}
                          {cls.methods?.length > 0 && (
                            <div className="mt-2">
                              <p className="text-xs text-gray-400 mb-1">{cls.methods.length} method{cls.methods.length > 1 ? 's' : ''}:</p>
                              <div className="max-h-28 overflow-y-auto space-y-0.5">
                                {cls.methods.map((m, i) => (
                                  <p key={i} className="text-xs font-mono text-gray-600 truncate">{m}</p>
                                ))}
                              </div>
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  </Section>
                )}

                {analysisInfo.endpoints?.length > 0 && (
                  <Section title="REST Endpoints">
                    <div className="space-y-1.5">
                      {analysisInfo.endpoints.map((ep, i) => (
                        <div key={i} className="flex items-center gap-2">
                          <span className={`font-mono text-xs font-bold px-2 py-1 rounded ${httpColor(ep.httpMethod)}`}>
                            {ep.httpMethod}
                          </span>
                          <span className="font-mono text-sm text-gray-700">{ep.path}</span>
                          <span className="text-xs text-green-500 ml-auto">✓ detected</span>
                        </div>
                      ))}
                    </div>
                  </Section>
                )}

                {analysisInfo.imports?.length > 0 && (
                  <Section title={`Imports (${analysisInfo.imports.length})`}>
                    <div className="max-h-40 overflow-y-auto bg-gray-50 rounded-lg p-2 space-y-0.5">
                      {analysisInfo.imports.map((imp, i) => (
                        <p key={i} className="text-xs font-mono text-gray-500">{imp}</p>
                      ))}
                    </div>
                  </Section>
                )}

                {analysisInfo.dependencies?.length > 0 && (
                  <Section title="Dependencies">
                    <div className="flex flex-wrap gap-1.5">
                      {analysisInfo.dependencies.map(dep => (
                        <span key={dep} className="text-xs bg-blue-50 text-blue-700 border border-blue-200 rounded-full px-2 py-0.5 font-mono">
                          {dep}
                        </span>
                      ))}
                    </div>
                  </Section>
                )}
              </>
            ) : (
              <div className="flex flex-col items-center justify-center py-10 text-gray-400">
                <FiCode className="w-8 h-8 mb-3 opacity-30" />
                <p className="text-sm font-medium text-gray-500">No deep analysis for this file</p>
                <p className="text-xs mt-1 text-center max-w-xs">
                  {language === 'Java'
                    ? 'This Java file was not included in the AST analysis (may be a test or generated file).'
                    : `Deep static analysis is currently only supported for Java. Source code is available in the Code tab.`}
                </p>
              </div>
            )}
          </div>
        )}

        {/* ── AI Explanation tab ── */}
        {activeTab === 'ai' && (
          <div className="p-4 overflow-y-auto h-full">
            {analysisInfo?.aiExplanation ? (
              <div className="bg-gradient-to-br from-blue-50 to-indigo-50 border border-blue-100 rounded-xl p-4">
                <div className="flex items-center gap-2 mb-3">
                  <div className="w-6 h-6 bg-blue-600 rounded-full flex items-center justify-center text-white text-xs font-bold">AI</div>
                  <p className="text-xs font-bold uppercase tracking-wider text-blue-700">AI Explanation</p>
                </div>
                <p className="text-sm text-gray-700 leading-relaxed whitespace-pre-wrap">
                  {analysisInfo.aiExplanation}
                </p>
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center py-10 text-gray-400">
                <div className="w-10 h-10 bg-gray-100 rounded-full flex items-center justify-center mb-3 text-lg">AI</div>
                <p className="text-sm font-medium text-gray-500">No AI explanation generated</p>
                <p className="text-xs mt-1 text-center max-w-xs">
                  AI explanations are generated for the most important architectural files during analysis.
                </p>
              </div>
            )}
          </div>
        )}

        {/* ── Related Files tab ── */}
        {activeTab === 'related' && (
          <div className="p-4 overflow-y-auto h-full">
            {relatedFiles.length > 0 ? (
              <Section title="Files that reference this class">
                <div className="space-y-2">
                  {relatedFiles.map(f => (
                    <div key={f.filePath} className="flex items-center justify-between bg-gray-50 border border-gray-200 rounded-lg px-3 py-2">
                      <div>
                        <p className="text-sm font-medium text-gray-800">{f.fileName}</p>
                        <p className="text-xs text-gray-400 font-mono">{f.filePath}</p>
                      </div>
                      {f.fileType && (
                        <span className="text-xs bg-green-100 text-green-700 rounded-full px-2 py-0.5">{f.fileType}</span>
                      )}
                    </div>
                  ))}
                </div>
              </Section>
            ) : (
              <p className="text-sm text-gray-400 text-center py-8">No related files found.</p>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

// ─── Section helper ───────────────────────────────────────────────────────────
function Section({ title, children }) {
  return (
    <div>
      <p className="text-xs font-bold uppercase tracking-wider text-gray-400 mb-2">{title}</p>
      {children}
    </div>
  )
}

function httpColor(method) {
  return {
    GET: 'bg-green-100 text-green-700', POST: 'bg-blue-100 text-blue-700',
    PUT: 'bg-yellow-100 text-yellow-700', DELETE: 'bg-red-100 text-red-700',
    PATCH: 'bg-orange-100 text-orange-700',
  }[method] || 'bg-gray-100 text-gray-600'
}

// ─── Tree node ─────────────────────────────────────────────────────────────────
function TreeNode({ node, depth, onSelect, selected }) {
  const [open, setOpen] = useState(depth < 2)
  const isDir = node.type === 'directory'
  const isSelected = selected === node.path
  const lang = !isDir ? detectLanguage(node.path) : null

  // Root: just render children
  if (depth === 0 && isDir) {
    return (
      <ul className="space-y-0.5">
        {node.children?.map(child => (
          <TreeNode key={child.path} node={child} depth={1} onSelect={onSelect} selected={selected} />
        ))}
      </ul>
    )
  }

  return (
    <li>
      <div
        className={`flex items-center gap-1.5 py-1 pr-2 rounded-lg cursor-pointer transition-colors text-sm
          ${isSelected ? 'bg-blue-100 text-blue-700' : 'hover:bg-gray-100 text-gray-700'}`}
        style={{ paddingLeft: `${depth * 14 + 6}px` }}
        onClick={() => isDir ? setOpen(o => !o) : onSelect(node)}
      >
        {isDir
          ? <>
              {open
                ? <FiChevronDown className="w-3.5 h-3.5 flex-shrink-0 text-gray-400" />
                : <FiChevronRight className="w-3.5 h-3.5 flex-shrink-0 text-gray-400" />}
              <FiFolder className="w-3.5 h-3.5 flex-shrink-0 text-yellow-500" />
            </>
          : <>
              <span className="w-3.5 flex-shrink-0" />
              <FiFile className={`w-3.5 h-3.5 flex-shrink-0 ${node.isKeyFile ? 'text-blue-500' : 'text-gray-400'}`} />
            </>}
        <span className={`truncate flex-1 ${node.isKeyFile ? 'font-semibold' : ''}`}>{node.name}</span>
        {node.isKeyFile && (
          <span className="text-xs bg-blue-100 text-blue-600 rounded px-1 flex-shrink-0">key</span>
        )}
        {lang && !isDir && !node.isKeyFile && (
          <span className="text-xs text-gray-400 flex-shrink-0">{lang}</span>
        )}
      </div>
      {isDir && open && node.children && (
        <ul className="space-y-0.5">
          {node.children.map(child => (
            <TreeNode key={child.path} node={child} depth={depth + 1} onSelect={onSelect} selected={selected} />
          ))}
        </ul>
      )}
    </li>
  )
}

// ─── File search through tree ─────────────────────────────────────────────────
function flattenTree(node, results = []) {
  if (!node) return results
  if (node.type === 'file') results.push(node)
  if (node.children) node.children.forEach(c => flattenTree(c, results))
  return results
}

// ─── Main component ───────────────────────────────────────────────────────────
export default function CodebaseSection({ fileTree, analyzedFiles, analysisId, repoInfo }) {
  const [selected, setSelected] = useState(null)
  const [search, setSearch] = useState('')

  // Build a lookup map: filePath -> analysisInfo
  const analysisMap = {}
  if (analyzedFiles) {
    analyzedFiles.forEach(f => { analysisMap[f.filePath] = f })
  }

  // Flat list for search
  const allFiles = fileTree ? flattenTree(fileTree) : []

  // Filter
  const searchResults = search.length > 1
    ? allFiles.filter(f =>
        f.name.toLowerCase().includes(search.toLowerCase()) ||
        f.path.toLowerCase().includes(search.toLowerCase()))
    : []

  function selectFile(node) {
    setSelected({ path: node.path, info: analysisMap[node.path] || null })
    setSearch('')
  }

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      {/* Header */}
      <div className="flex items-center gap-2 mb-5">
        <div className="w-8 h-8 bg-indigo-100 rounded-lg flex items-center justify-center">
          <FiCode className="w-4 h-4 text-indigo-600" />
        </div>
        <h2 className="text-lg font-bold text-gray-800">Codebase Explorer</h2>
        <span className="text-xs text-gray-400 ml-1">— click any file to view source & analysis</span>
        <span className="ml-auto text-xs text-gray-400">{allFiles.length} files</span>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4" style={{ height: '560px' }}>
        {/* Left: Tree + Search */}
        <div className="flex flex-col bg-gray-50 rounded-xl border border-gray-200 overflow-hidden">
          {/* Search box */}
          <div className="p-3 border-b border-gray-200 flex-shrink-0">
            <div className="relative">
              <FiSearch className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-400" />
              <input
                type="text"
                value={search}
                onChange={e => setSearch(e.target.value)}
                placeholder="Search files by name or path..."
                className="w-full pl-9 pr-3 py-2 text-sm bg-white border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              {search && (
                <button onClick={() => setSearch('')} className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
                  <FiX className="w-3.5 h-3.5" />
                </button>
              )}
            </div>
          </div>

          {/* Search results or tree */}
          <div className="flex-1 overflow-y-auto p-2 scrollbar-thin">
            {search.length > 1 ? (
              searchResults.length > 0 ? (
                <ul className="space-y-0.5">
                  {searchResults.map(f => (
                    <li key={f.path}>
                      <button
                        onClick={() => selectFile(f)}
                        className={`w-full text-left flex items-center gap-2 px-3 py-2 rounded-lg hover:bg-gray-100 transition-colors
                          ${selected?.path === f.path ? 'bg-blue-100 text-blue-700' : 'text-gray-700'}`}
                      >
                        <FiFile className="w-3.5 h-3.5 flex-shrink-0 text-gray-400" />
                        <div className="min-w-0">
                          <p className="text-sm font-medium truncate">{f.name}</p>
                          <p className="text-xs text-gray-400 truncate font-mono">{f.path}</p>
                        </div>
                      </button>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-sm text-gray-400 text-center py-6">No files match "{search}"</p>
              )
            ) : fileTree ? (
              <TreeNode
                node={fileTree}
                depth={0}
                onSelect={selectFile}
                selected={selected?.path}
              />
            ) : (
              <p className="text-sm text-gray-400 italic p-4 text-center">No file tree available.</p>
            )}
          </div>
        </div>

        {/* Right: File detail */}
        <div className="rounded-xl border border-gray-200 overflow-hidden" style={{ height: '560px' }}>
          {selected ? (
            <FileDetail
              key={selected.path}
              path={selected.path}
              analysisInfo={selected.info}
              analysisId={analysisId}
              repoInfo={repoInfo}
              onClose={() => setSelected(null)}
              allFiles={analyzedFiles}
            />
          ) : (
            <div className="flex flex-col items-center justify-center h-full text-center text-gray-400 bg-gray-50 p-8">
              <FiCode className="w-12 h-12 mb-4 opacity-20" />
              <p className="text-sm font-medium text-gray-500">Select any file to view its source</p>
              <p className="text-xs mt-2 max-w-xs text-gray-400">
                All repository files are browsable. Source code is fetched live from GitHub.
                Java files also include deep AST analysis.
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
