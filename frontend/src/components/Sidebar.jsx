import { NavLink, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../store/authStore.js'
import {
  FiHome, FiSearch, FiCode, FiDatabase,
  FiBarChart2, FiInfo, FiLogOut, FiLogIn, FiGithub
} from 'react-icons/fi'

const navItems = [
  { to: '/',               icon: FiHome,     label: 'Home' },
  { to: '/about',          icon: FiInfo,      label: 'About' },
]

export default function Sidebar() {
  const { user, logout } = useAuthStore()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/')
  }

  return (
    <aside className="w-64 flex-shrink-0 bg-[#0f172a] text-white flex flex-col h-full">
      {/* Logo */}
      <div className="px-6 py-6 border-b border-white/10">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 bg-blue-500 rounded-lg flex items-center justify-center">
            <FiGithub className="w-5 h-5 text-white" />
          </div>
          <div>
            <p className="font-bold text-sm leading-tight">GitHub Repo</p>
            <p className="text-blue-400 font-bold text-sm leading-tight">Insight</p>
          </div>
        </div>
        <p className="text-xs text-gray-400 mt-2 leading-snug">
          AI-Powered Repository Analysis
        </p>
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-4 py-6 space-y-1">
        <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider px-2 mb-3">
          Navigation
        </p>
        {navItems.map(({ to, icon: Icon, label }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            className={({ isActive }) =>
              `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                isActive
                  ? 'bg-blue-600 text-white'
                  : 'text-gray-300 hover:bg-white/10 hover:text-white'
              }`
            }
          >
            <Icon className="w-4 h-4" />
            {label}
          </NavLink>
        ))}

        <div className="pt-4 mt-4 border-t border-white/10">
          <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider px-2 mb-3">
            Analysis Sections
          </p>
          {[
            { icon: FiSearch,   label: 'Repository Analysis' },
            { icon: FiCode,     label: 'Codebase' },
            { icon: FiDatabase, label: 'Database' },
            { icon: FiBarChart2,label: 'Visualizations' },
          ].map(({ icon: Icon, label }) => (
            <div
              key={label}
              className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm text-gray-400 cursor-default"
            >
              <Icon className="w-4 h-4" />
              {label}
            </div>
          ))}
        </div>
      </nav>

      {/* User / Auth */}
      <div className="px-4 py-4 border-t border-white/10">
        {user ? (
          <div>
            <div className="flex items-center gap-3 px-3 py-2 mb-2">
              <div className="w-8 h-8 rounded-full bg-blue-600 flex items-center justify-center text-sm font-bold">
                {user.username?.[0]?.toUpperCase() ?? 'U'}
              </div>
              <div className="overflow-hidden">
                <p className="text-sm font-medium truncate">{user.username}</p>
                <p className="text-xs text-gray-400 truncate">{user.email}</p>
              </div>
            </div>
            <button
              onClick={handleLogout}
              className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm text-gray-300 hover:bg-white/10 hover:text-white transition-colors"
            >
              <FiLogOut className="w-4 h-4" />
              Sign Out
            </button>
          </div>
        ) : (
          <NavLink
            to="/login"
            className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm text-gray-300 hover:bg-white/10 hover:text-white transition-colors"
          >
            <FiLogIn className="w-4 h-4" />
            Sign In
          </NavLink>
        )}
      </div>
    </aside>
  )
}
