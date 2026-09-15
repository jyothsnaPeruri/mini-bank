const BASE_URL = import.meta.env.VITE_API_URL ?? ''

/** A failed API call, shaped from the backend's problem+json body. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string | undefined
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, code?: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

let getToken: () => string | null = () => null
let onUnauthorized: () => void = () => {}

export function configureApi(options: { getToken: () => string | null; onUnauthorized: () => void }) {
  getToken = options.getToken
  onUnauthorized = options.onUnauthorized
}

// The free hosting plan puts the server to sleep when idle. Requests slower than this
// show a "waking up" notice instead of leaving the user staring at a spinner.
const SLOW_REQUEST_MS = 3000
let slowRequests = 0
const slowListeners = new Set<(slow: boolean) => void>()

function setSlow(delta: number) {
  slowRequests += delta
  slowListeners.forEach((listener) => listener(slowRequests > 0))
}

export function subscribeToSlowServer(listener: (slow: boolean) => void) {
  slowListeners.add(listener)
  return () => {
    slowListeners.delete(listener)
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH'
  body?: unknown
  headers?: Record<string, string>
}

export async function api<T>(path: string, { method = 'GET', body, headers = {} }: RequestOptions = {}): Promise<T> {
  const token = getToken()
  let markedSlow = false
  const slowTimer = setTimeout(() => {
    markedSlow = true
    setSlow(1)
  }, SLOW_REQUEST_MS)

  let response: Response
  try {
    response = await fetch(BASE_URL + path, {
      method,
      headers: {
        ...(body !== undefined && { 'Content-Type': 'application/json' }),
        ...(token && { Authorization: `Bearer ${token}` }),
        ...headers,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, "Can't reach the server. Check your connection and try again.", 'network_error')
  } finally {
    clearTimeout(slowTimer)
    if (markedSlow) setSlow(-1)
  }

  if (response.status === 401 && token) {
    onUnauthorized()
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}))
    throw new ApiError(
      response.status,
      problem.detail ?? `Something went wrong (error ${response.status}). Please try again.`,
      problem.code,
      problem.errors ?? {},
    )
  }
  return response.json() as Promise<T>
}

/** Fire-and-forget request that starts waking the server before the user needs it. */
export function warmUpServer() {
  fetch(BASE_URL + '/actuator/health').catch(() => {})
}
