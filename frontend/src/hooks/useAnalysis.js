import { useState, useEffect, useRef, useCallback } from 'react'
import { analysisApi } from '../services/api.js'

const TERMINAL_STATUSES = ['COMPLETED', 'FAILED']
const POLL_INTERVAL_MS = 2500

export function useAnalysis() {
  const [analysisId, setAnalysisId] = useState(null)
  const [status, setStatus] = useState(null)       // { status, statusMessage, errorMessage }
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const pollRef = useRef(null)

  const clearPoll = () => {
    if (pollRef.current) {
      clearInterval(pollRef.current)
      pollRef.current = null
    }
  }

  const pollStatus = useCallback(async (id) => {
    try {
      const statusRes = await analysisApi.getStatus(id)
      const statusData = statusRes.data
      setStatus(statusData)

      if (statusData.status === 'COMPLETED') {
        clearPoll()
        const resultRes = await analysisApi.getResult(id)
        setResult(resultRes.data)
        setLoading(false)
      } else if (statusData.status === 'FAILED') {
        clearPoll()
        setError(statusData.errorMessage || 'Analysis failed.')
        setLoading(false)
      }
    } catch (err) {
      clearPoll()
      setError(err.message)
      setLoading(false)
    }
  }, [])

  const startAnalysis = useCallback(async (url) => {
    clearPoll()
    setLoading(true)
    setError(null)
    setResult(null)
    setStatus(null)
    setAnalysisId(null)

    try {
      const res = await analysisApi.start(url)
      const { analysisId: id } = res.data
      setAnalysisId(id)

      // Poll every 2.5 seconds
      pollRef.current = setInterval(() => pollStatus(id), POLL_INTERVAL_MS)
      // Also poll immediately
      pollStatus(id)
    } catch (err) {
      setError(err.message)
      setLoading(false)
    }
  }, [pollStatus])

  // Cleanup on unmount
  useEffect(() => () => clearPoll(), [])

  return { startAnalysis, analysisId, status, result, loading, error }
}
