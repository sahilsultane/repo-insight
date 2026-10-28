import { FiStar, FiGitBranch, FiAlertCircle, FiExternalLink, FiLock, FiCalendar } from 'react-icons/fi'

export default function RepoHeader({ info }) {
  if (!info) return null
  const { name, owner, fullName, description, url, primaryLanguage, stars, forks, openIssues, license, updatedAt, isPrivate } = info

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <div className="flex items-center gap-2 mb-1">
            {isPrivate && (
              <span className="flex items-center gap-1 text-xs bg-yellow-100 text-yellow-700 border border-yellow-200 rounded-full px-2 py-0.5">
                <FiLock className="w-3 h-3" /> Private
              </span>
            )}
            <h1 className="text-2xl font-bold text-gray-900">{name}</h1>
          </div>
          <p className="text-sm text-gray-500 mb-1">
            <span className="font-medium text-gray-700">{owner}</span>
            <span className="mx-1">/</span>
            <span>{name}</span>
          </p>
          {description && (
            <p className="text-gray-600 mt-2 max-w-2xl">{description}</p>
          )}
        </div>

        <a
          href={url}
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center gap-2 px-4 py-2 bg-gray-900 hover:bg-gray-700 text-white text-sm rounded-lg font-medium transition-colors whitespace-nowrap"
        >
          <FiExternalLink className="w-4 h-4" />
          View on GitHub
        </a>
      </div>

      <div className="flex flex-wrap gap-4 mt-5 pt-4 border-t border-gray-100">
        {primaryLanguage && (
          <Stat label="Language" value={primaryLanguage} color="blue" />
        )}
        <Stat icon={<FiStar />} label="Stars" value={stars?.toLocaleString() ?? '0'} />
        <Stat icon={<FiGitBranch />} label="Forks" value={forks?.toLocaleString() ?? '0'} />
        <Stat icon={<FiAlertCircle />} label="Open Issues" value={openIssues?.toLocaleString() ?? '0'} />
        {license && <Stat label="License" value={license} />}
        {updatedAt && (
          <Stat
            icon={<FiCalendar />}
            label="Last Updated"
            value={new Date(updatedAt).toLocaleDateString()}
          />
        )}
      </div>
    </div>
  )
}

function Stat({ icon, label, value, color }) {
  return (
    <div className="flex items-center gap-1.5">
      {icon && <span className="text-gray-400 w-4 h-4">{icon}</span>}
      <span className="text-sm text-gray-500">{label}:</span>
      <span className={`text-sm font-semibold ${color ? `text-${color}-600` : 'text-gray-800'}`}>
        {value}
      </span>
    </div>
  )
}
