import axios from 'axios'

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081'

// Base instance — short timeout for quick calls
const api = axios.create({
  baseURL: API_BASE,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

// Long-timeout instance — for analysis result fetch (AI takes time)
const apiLong = axios.create({
  baseURL: API_BASE,
  timeout: 300000, // 5 minutes
  headers: { 'Content-Type': 'application/json' },
})

// Attach JWT to all requests
const attachToken = (config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
}
api.interceptors.request.use(attachToken)
apiLong.interceptors.request.use(attachToken)

// Global error handler — extract readable message
const handleError = (err) => {
  const message = err.response?.data?.message || err.message || 'An error occurred'
  return Promise.reject(new Error(message))
}
api.interceptors.response.use((res) => res, handleError)
apiLong.interceptors.response.use((res) => res, handleError)

// ── Auth ──────────────────────────────────────────────────────────────────────
export const authApi = {
  register: (data) => api.post('/api/auth/register', data),
  login:    (data) => api.post('/api/auth/login', data),
}

// ── Analysis ──────────────────────────────────────────────────────────────────
export const analysisApi = {
  // Start analysis — quick POST, returns analysisId immediately
  start: (repositoryUrl) =>
    api.post('/api/repositories/analyze', { repositoryUrl }),

  // Poll status — lightweight, fast
  getStatus: (analysisId) =>
    api.get(`/api/analyses/${analysisId}/status`),

  // Fetch full result — uses long timeout (AI explanations take time)
  getResult: (analysisId) =>
    apiLong.get(`/api/analyses/${analysisId}`),

  // Chat — AI response may take a few seconds
  ask: (analysisId, question) =>
    apiLong.post(`/api/analyses/${analysisId}/ask`, { question }),

  // Fetch raw file content from GitHub on-demand
  getFileContent: (analysisId, filePath) =>
    api.get(`/api/analyses/${analysisId}/file-content`, {
      params: { path: filePath },
      timeout: 20000,
    }),
}

export default api
