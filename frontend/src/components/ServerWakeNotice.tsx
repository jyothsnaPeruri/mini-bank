import { useEffect, useState } from 'react'
import { subscribeToSlowServer } from '../api'

export function ServerWakeNotice() {
  const [slow, setSlow] = useState(false)
  useEffect(() => subscribeToSlowServer(setSlow), [])

  if (!slow) return null
  return (
    <div className="wake-notice" role="status">
      <span className="spinner" aria-hidden="true" />
      <span>
        <strong>Waking up the server…</strong> This demo runs on free hosting that sleeps when idle, so the first
        request can take up to a minute.
      </span>
    </div>
  )
}
