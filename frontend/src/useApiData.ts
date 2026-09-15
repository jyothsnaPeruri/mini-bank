import { useCallback, useEffect, useState } from 'react'
import { api, ApiError } from './api'

interface Result<T> {
  key: string
  data: T | null
  error: ApiError | null
}

/**
 * Loads JSON from the API for a component, re-fetching when the path changes or reload() is called.
 * While a new request is in flight the previous data stays visible, so tables don't flash empty.
 */
export function useApiData<T>(path: string) {
  const [version, setVersion] = useState(0)
  const key = `${path}#${version}`
  const [result, setResult] = useState<Result<T>>({ key: '', data: null, error: null })

  useEffect(() => {
    let cancelled = false
    api<T>(path)
      .then((data) => {
        if (!cancelled) setResult({ key, data, error: null })
      })
      .catch((error: ApiError) => {
        if (!cancelled) setResult((previous) => ({ key, data: previous.data, error }))
      })
    return () => {
      cancelled = true
    }
  }, [path, key])

  const reload = useCallback(() => setVersion((v) => v + 1), [])
  return { data: result.data, error: result.error, loading: result.key !== key, reload }
}
