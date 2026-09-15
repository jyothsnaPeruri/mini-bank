import { useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { api, ApiError } from '../api'
import { Alert } from '../components/Alert'
import { formatAccountNumber, money } from '../format'
import type { Account, Payee, Transfer } from '../types'
import { useApiData } from '../useApiData'

type Mode = 'own' | 'payee'
type Step = 'edit' | 'review' | 'done'

const MAX_TRANSFER = 10_000
const AMOUNT_PATTERN = /^\d+(\.\d{1,2})?$/

export function TransferPage() {
  const [searchParams] = useSearchParams()
  const { data: accounts, error: accountsError, reload: reloadAccounts } = useApiData<Account[]>('/api/accounts')
  const { data: recentPayees } = useApiData<Payee[]>('/api/transfers/recent-payees')

  const [chosenMode, setChosenMode] = useState<Mode | null>(null)
  const [fromId, setFromId] = useState(searchParams.get('from') ?? '')
  const [toId, setToId] = useState('')
  const [payeeNumber, setPayeeNumber] = useState('')
  const [lookup, setLookup] = useState<{ number: string; payee?: Payee; error?: string } | null>(null)
  const [amount, setAmount] = useState('')
  const [description, setDescription] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const [step, setStep] = useState<Step>('edit')
  const [idempotencyKey, setIdempotencyKey] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  const [result, setResult] = useState<Transfer | null>(null)

  const activeAccounts = accounts?.filter((account) => account.status === 'ACTIVE') ?? []
  const canMoveBetweenOwn = activeAccounts.length > 1
  const mode: Mode = chosenMode ?? (canMoveBetweenOwn ? 'own' : 'payee')

  // Sensible defaults without extra state: first active account, and a different one to send to
  const from = activeAccounts.find((a) => a.id === fromId) ?? activeAccounts[0]
  const ownTargets = activeAccounts.filter((a) => a.id !== from?.id)
  const toOwn = ownTargets.find((a) => a.id === toId) ?? ownTargets[0]

  // Confirm the payee's name as soon as a full account number is typed
  const digits = payeeNumber.replace(/\D/g, '')
  useEffect(() => {
    if (digits.length !== 10) return
    let cancelled = false
    api<Payee>(`/api/accounts/lookup?number=${digits}`)
      .then((found) => !cancelled && setLookup({ number: digits, payee: found }))
      .catch((err: ApiError) => !cancelled && setLookup({ number: digits, error: err.message }))
    return () => {
      cancelled = true
    }
  }, [digits])
  // Only trust a lookup result for the number currently typed
  const payee = lookup?.number === digits ? (lookup.payee ?? null) : null
  const payeeError = lookup?.number === digits ? (lookup.error ?? null) : null

  const validate = () => {
    const errors: Record<string, string> = {}
    if (!from) errors.fromAccountId = 'Choose an account to pay from'
    if (mode === 'own' && !toOwn) errors.to = 'Choose an account to transfer to'
    if (mode === 'payee') {
      if (digits.length !== 10) errors.to = 'Enter a 10-digit account number'
      else if (!payee) errors.to = payeeError ?? 'Checking account number…'
      else if (from && activeAccounts.some((a) => a.accountNumber === digits && a.id === from.id))
        errors.to = "You can't pay the account you're paying from"
    }
    const value = Number(amount)
    if (!AMOUNT_PATTERN.test(amount.trim()) || value <= 0) errors.amount = 'Enter an amount like 25 or 25.50'
    else if (value > MAX_TRANSFER) errors.amount = `Transfers are limited to ${money(MAX_TRANSFER)}`
    else if (from && value > from.balance) errors.amount = `That's more than the ${money(from.balance)} available`
    return errors
  }

  const onReview = (event: FormEvent) => {
    event.preventDefault()
    const errors = validate()
    setFieldErrors(errors)
    setError(null)
    if (Object.keys(errors).length > 0) return
    // One key per payment attempt: retrying "Confirm" re-sends the same key, so the server
    // returns the original transfer instead of sending the money again
    setIdempotencyKey(crypto.randomUUID())
    setStep('review')
  }

  const onConfirm = async () => {
    if (!from) return
    setSubmitting(true)
    setError(null)
    try {
      const transfer = await api<Transfer>('/api/transfers', {
        method: 'POST',
        headers: { 'Idempotency-Key': idempotencyKey },
        body: {
          fromAccountId: from.id,
          toAccountNumber: mode === 'own' ? toOwn?.accountNumber : digits,
          amount: Number(amount),
          description: description.trim() || null,
        },
      })
      setResult(transfer)
      setStep('done')
      reloadAccounts()
    } catch (err) {
      const apiError = err as ApiError
      setError(apiError)
      if (Object.keys(apiError.fieldErrors).length > 0) {
        setFieldErrors(apiError.fieldErrors)
        setStep('edit')
      }
    } finally {
      setSubmitting(false)
    }
  }

  const startOver = () => {
    setStep('edit')
    setAmount('')
    setDescription('')
    setPayeeNumber('')
    setError(null)
    setFieldErrors({})
    setResult(null)
  }

  if (accountsError) return <Alert>{accountsError.message}</Alert>
  if (!accounts) return <p className="empty">Loading your accounts…</p>

  if (activeAccounts.length === 0) {
    return (
      <div className="narrow">
        <h1>Pay &amp; transfer</h1>
        <Alert tone="info">You don't have an active account to pay from.</Alert>
      </div>
    )
  }

  if (step === 'done' && result) {
    return (
      <div className="narrow">
        <div className="card success-card">
          <div className="success-icon" aria-hidden="true">
            ✓
          </div>
          <h1>{money(result.amount)} sent</h1>
          <p className="muted">
            to {mode === 'own' ? toOwn?.nickname ?? 'your account' : payee?.holderName} ·{' '}
            {formatAccountNumber(result.toAccountNumber)}
          </p>
          <p className="muted small">Reference {result.id.slice(0, 8).toUpperCase()}</p>
          <div className="button-row center">
            <button type="button" className="btn btn-ghost" onClick={startOver}>
              Make another transfer
            </button>
            <Link to="/" className="btn btn-primary">
              Back to accounts
            </Link>
          </div>
        </div>
      </div>
    )
  }

  if (step === 'review' && from) {
    const toLabel = mode === 'own' ? toOwn?.nickname : payee?.holderName
    const toNumber = mode === 'own' ? toOwn?.accountNumber : digits
    return (
      <div className="narrow">
        <h1>Check and confirm</h1>
        <div className="card review-card">
          <dl>
            <div>
              <dt>From</dt>
              <dd>
                {from.nickname} <span className="muted">· {formatAccountNumber(from.accountNumber)}</span>
              </dd>
            </div>
            <div>
              <dt>To</dt>
              <dd>
                {toLabel} <span className="muted">· {toNumber && formatAccountNumber(toNumber)}</span>
              </dd>
            </div>
            <div>
              <dt>Amount</dt>
              <dd className="amount-lg">{money(Number(amount))}</dd>
            </div>
            {description.trim() && (
              <div>
                <dt>Description</dt>
                <dd>{description.trim()}</dd>
              </div>
            )}
          </dl>
          {error && (
            <Alert>
              {error.message}
              {error.status === 0 && ' It is safe to press Confirm again - you will not be charged twice.'}
            </Alert>
          )}
          <div className="button-row">
            <button type="button" className="btn btn-ghost" disabled={submitting} onClick={() => setStep('edit')}>
              Edit
            </button>
            <button type="button" className="btn btn-primary" disabled={submitting} onClick={onConfirm}>
              {submitting ? 'Sending…' : `Confirm and send ${money(Number(amount))}`}
            </button>
          </div>
        </div>
        <details className="under-the-hood">
          <summary>What happens when you press Confirm?</summary>
          <ol>
            <li>
              The request carries a unique <code>Idempotency-Key</code>. If it's sent twice (a double-click or a
              retry after a dropped connection), the server returns the first result instead of paying again.
            </li>
            <li>
              The server locks both accounts (<code>SELECT … FOR UPDATE</code>), always in the same order so two
              opposite transfers can't deadlock.
            </li>
            <li>
              It checks the balance, moves the money and writes a ledger entry on each side, all in one database
              transaction. If any step fails, nothing is saved.
            </li>
          </ol>
        </details>
      </div>
    )
  }

  return (
    <div className="narrow">
      <h1>Pay &amp; transfer</h1>
      <div className="tabs" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={mode === 'own'}
          className={mode === 'own' ? 'tab active' : 'tab'}
          onClick={() => setChosenMode('own')}
        >
          Between my accounts
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={mode === 'payee'}
          className={mode === 'payee' ? 'tab active' : 'tab'}
          onClick={() => setChosenMode('payee')}
        >
          Pay someone
        </button>
      </div>

      <form className="card form-card" onSubmit={onReview} noValidate>
        {error && Object.keys(error.fieldErrors).length === 0 && <Alert>{error.message}</Alert>}

        <div className="field">
          <label htmlFor="from">From</label>
          <select id="from" value={from?.id ?? ''} onChange={(e) => setFromId(e.target.value)}>
            {activeAccounts.map((account) => (
              <option key={account.id} value={account.id}>
                {account.nickname} · {money(account.balance)} available
              </option>
            ))}
          </select>
          {fieldErrors.fromAccountId && <p className="field-error">{fieldErrors.fromAccountId}</p>}
        </div>

        {mode === 'own' ? (
          <div className="field">
            <label htmlFor="to">To</label>
            {canMoveBetweenOwn ? (
              <select id="to" value={toOwn?.id ?? ''} onChange={(e) => setToId(e.target.value)}>
                {ownTargets.map((account) => (
                  <option key={account.id} value={account.id}>
                    {account.nickname} · {money(account.balance)}
                  </option>
                ))}
              </select>
            ) : (
              <p className="field-hint">
                You need a second account for this. <Link to="/">Open one from your accounts page</Link>, or pay
                someone instead.
              </p>
            )}
            {fieldErrors.to && <p className="field-error">{fieldErrors.to}</p>}
          </div>
        ) : (
          <div className="field">
            <label htmlFor="payee">Their account number</label>
            <input
              id="payee"
              inputMode="numeric"
              autoComplete="off"
              placeholder="10 digits"
              value={payeeNumber}
              maxLength={12}
              onChange={(e) => setPayeeNumber(e.target.value)}
              aria-invalid={!!fieldErrors.to}
            />
            {payee && <p className="payee-confirm">✓ Paying {payee.holderName}</p>}
            {payeeError && <p className="field-error">{payeeError}</p>}
            {!payee && !payeeError && fieldErrors.to && <p className="field-error">{fieldErrors.to}</p>}
            {recentPayees && recentPayees.length > 0 && (
              <div className="recent-payees">
                <span className="muted small">Recent</span>
                {recentPayees.map((recent) => (
                  <button
                    key={recent.accountNumber}
                    type="button"
                    className={digits === recent.accountNumber ? 'chip active' : 'chip'}
                    onClick={() => setPayeeNumber(recent.accountNumber)}
                  >
                    {recent.holderName}
                  </button>
                ))}
              </div>
            )}
          </div>
        )}

        <div className="field">
          <label htmlFor="amount">Amount (AUD)</label>
          <div className="input-prefix">
            <span aria-hidden="true">$</span>
            <input
              id="amount"
              inputMode="decimal"
              autoComplete="off"
              placeholder="0.00"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              aria-invalid={!!fieldErrors.amount}
            />
          </div>
          {fieldErrors.amount && <p className="field-error">{fieldErrors.amount}</p>}
        </div>

        <div className="field">
          <label htmlFor="description">
            Description <span className="muted">(optional)</span>
          </label>
          <input
            id="description"
            maxLength={140}
            placeholder="e.g. Rent share"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </div>

        <button type="submit" className="btn btn-primary btn-block" disabled={mode === 'own' && !canMoveBetweenOwn}>
          Review transfer
        </button>
      </form>
    </div>
  )
}
