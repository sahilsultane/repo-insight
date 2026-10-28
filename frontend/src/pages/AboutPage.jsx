import TechStackCard from '../components/TechStackCard.jsx'
import { FiGithub, FiZap, FiShield, FiCode } from 'react-icons/fi'

export default function AboutPage() {
  return (
    <div className="max-w-4xl mx-auto px-8 py-12">
      <div className="mb-10">
        <h1 className="text-3xl font-extrabold text-gray-900 mb-3">About GitHub Repo Insight</h1>
        <p className="text-gray-500 text-lg">
          An AI-powered platform that analyzes real GitHub repositories and generates
          architecture diagrams, ER diagrams, and beginner-friendly explanations.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-10">
        {[
          { icon: FiGithub, title: 'Real Analysis', color: 'blue',
            desc: 'Connects to the GitHub REST API to fetch actual repository metadata, file trees, and source code. No fake data.' },
          { icon: FiCode, title: 'Java AST Engine', color: 'indigo',
            desc: 'Uses JavaParser to perform Abstract Syntax Tree analysis of Java/Spring Boot projects — detecting classes, annotations, endpoints, and entity relationships.' },
          { icon: FiZap, title: 'AI Explanations', color: 'purple',
            desc: 'Sends structured analysis context (not raw code) to Gemini or OpenAI to generate beginner-friendly explanations. The AI explains, the analyzer detects.' },
          { icon: FiShield, title: 'Secure by Design', color: 'green',
            desc: 'Sensitive values are redacted before any AI call. No repository secrets, passwords, or API keys are ever forwarded to AI providers.' },
        ].map(({ icon: Icon, title, color, desc }) => (
          <div key={title} className="bg-white rounded-xl border border-gray-200 p-6 shadow-sm">
            <div className={`w-10 h-10 bg-${color}-100 rounded-lg flex items-center justify-center mb-3`}>
              <Icon className={`w-5 h-5 text-${color}-600`} />
            </div>
            <h3 className="font-bold text-gray-800 mb-2">{title}</h3>
            <p className="text-sm text-gray-500 leading-relaxed">{desc}</p>
          </div>
        ))}
      </div>

      <div>
        <h2 className="text-xl font-bold text-gray-800 mb-5">Technology Stack</h2>
        <TechStackCard />
      </div>

      <div className="mt-10 bg-[#0f172a] text-white rounded-2xl p-6">
        <h2 className="font-bold text-lg mb-2">Analysis Pipeline</h2>
        <ol className="space-y-1.5 text-sm text-gray-300">
          {[
            'Validate GitHub URL → extract owner/repo',
            'Fetch repository metadata from GitHub REST API',
            'Fetch full file tree (recursive)',
            'Detect languages from file extensions',
            'Analyze pom.xml / build.gradle for frameworks & dependencies',
            'Fetch and sanitize application.properties / application.yml',
            'Run JavaParser AST on all .java source files (max 80)',
            'Detect Spring annotations: @RestController, @Service, @Repository, @Entity …',
            'Detect database technology from JDBC URL / dependencies',
            'Extract JPA entity fields → infer table/column schema',
            'Parse SQL CREATE TABLE statements if .sql files exist',
            'Build architecture graph (React Flow nodes/edges)',
            'Build ER diagram (table nodes + FK edges)',
            'Send structured context to Gemini/OpenAI for explanations',
            'Persist full result to PostgreSQL',
            'Return to React dashboard',
          ].map((step, i) => (
            <li key={i} className="flex items-start gap-2">
              <span className="text-blue-400 font-bold flex-shrink-0">{i + 1}.</span>
              {step}
            </li>
          ))}
        </ol>
      </div>
    </div>
  )
}
