import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { api, ApiError } from '../api'
import { useAuth } from '../useAuth'
import { Alert } from '../components/Alert'
import { accountTypeLabel, formatAccountNumber, formatDate, greeting, money, signedMoney } from '../format'
import type { Account, AccountType, LedgerEntry, Page } from '../types'
import { useApiData } from '../useApiData'

const MAX_ACCOUNTS = 4

interface RecentEntry extends LedgerEntry {
  accountNickname: string
  /** Set when both sides of a transfer are the user's own accounts, e.g. "Holiday fund → Everyday". */
  internalRoute?: string
}

/**
 * A move between two of your own accounts appears in both ledgers; show it once as "From → To".
 * Between own accounts the backend names the counterparty by account nickname, which is how we spot them.
 */
function mergeInternalTransfers(entries: RecentEntry[], ownNicknames: Set<string>): RecentEntry[] {
  const seen = new Set<string>()
  const merged: RecentEntry[] = []
  for (const entry of entries) {
    const internal = entry.transferId && entry.counterparty && ownNicknames.has(entry.counterparty)
    if (!internal) {
      merged.push(entry)
      continue
    }
    if (seen.has(entry.transferId!)) continue
    seen.add(entry.transferId!)
    const route =
      entry.amount < 0
        ? `${entry.accountNickname} → ${entry.counterparty}`
        : `${entry.counterparty} → ${entry.accountNickname}`
    merged.push({ ...entry, internalRoute: route })
  }
  return merged
}

export function DashboardPage() {
  const { user } = useAuth()
  const { data: accounts, error, loading, reload } = useApiData<Account[]>('/api/accounts')
  const [recent, setRecent] = useState<RecentEntry[] | null>(null)

  // Recent activity across all accounts: newest few from each, merged
  useEffect(() => {
    if (!accounts) return
    let cancelled = false
    Promise.all(
      accounts.map((account) =>
        api<Page<LedgerEntry>>(`/api/accounts/${account.id}/transactions?size=5`).then((page) =>
          page.content.map((entry) => ({ ...entry, accountNickname: account.nickname })),
        ),
      ),
    )
      .then((lists) => {
        if (cancelled) return
        const sorted = lists.flat().sort((a, b) => b.createdAt.localeCompare(a.createdAt))
        const ownNicknames = new Set(accounts.map((account) => account.nickname))
        setRecent(mergeInternalTransfers(sorted, ownNicknames).slice(0, 6))
      })
      .catch(() => {
        if (!cancelled) setRecent([])
      })
    return () => {
      cancelled = true
    }
  }, [accounts])

  const total = accounts?.reduce((sum, account) => sum + account.balance, 0) ?? 0
  const firstName = user?.fullName.split(' ')[0]

  return (
    <>
      <div className="page-header">
        <div>
          <p className="eyebrow">{greeting()}</p>
          <h1>{firstName}</h1>
        </div>
        <div className="total-balance">
          <span className="muted">Total across accounts</span>
          <strong className="amount-xl">{loading && !accounts ? '—' : money(total)}</strong>
        </div>
      </div>

      {error && <Alert>{error.message}</Alert>}

      <section aria-labelledby="accounts-heading">
        <div className="section-header">
          <h2 id="accounts-heading">Your accounts</h2>
          <Link to="/transfer" className="btn btn-primary btn-sm">
            Pay &amp; transfer
          </Link>
        </div>
        <div className="account-grid">
          {loading && !accounts && [0, 1].map((i) => <div key={i} className="card account-card skeleton" />)}
          {accounts?.map((account) => (
            <Link key={account.id} to={`/accounts/${account.id}`} className="card account-card">
              <div className="account-card-top">
                <span className={`badge badge-${account.type.toLowerCase()}`}>{accountTypeLabel[account.type]}</span>
                {account.status === 'FROZEN' && <span className="badge badge-frozen">Frozen</span>}
              </div>
              <h3>{account.nickname}</h3>
              <p className="account-number">{formatAccountNumber(account.accountNumber)}</p>
              <p className="amount-lg">{money(account.balance)}</p>
            </Link>
          ))}
          {accounts && accounts.length < MAX_ACCOUNTS && <OpenAccountCard onOpened={reload} />}
        </div>
      </section>

      <section aria-labelledby="recent-heading" className="section-gap">
        <div className="section-header">
          <h2 id="recent-heading">Recent activity</h2>
        </div>
        <div className="card">
          {recent === null ? (
            <p className="empty">Loading…</p>
          ) : recent.length === 0 ? (
            <p className="empty">No transactions yet.</p>
          ) : (
            <ul className="activity-list">
              {recent.map((entry) => (
                <li key={entry.id}>
                  <div>
                    <p className="activity-title">{entry.description ?? entry.counterparty ?? 'Transaction'}</p>
                    <p className="muted small">
                      {formatDate(entry.createdAt)} ·{' '}
                      {entry.internalRoute ??
                        `${entry.accountNickname}${entry.counterparty && entry.description ? ` · ${entry.counterparty}` : ''}`}
                    </p>
                  </div>
                  {entry.internalRoute ? (
                    <span className="amount muted">{money(Math.abs(entry.amount))}</span>
                  ) : (
                    <span className={entry.amount > 0 ? 'amount credit' : 'amount'}>{signedMoney(entry.amount)}</span>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>
    </>
  )
}

function OpenAccountCard({ onOpened }: { onOpened: () => void }) {
  const [open, setOpen] = useState(false)
  const [type, setType] = useState<AccountType>('SAVINGS')
  const [nickname, setNickname] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      await api('/api/accounts', { method: 'POST', body: { type, nickname } })
      setOpen(false)
      setNickname('')
      onOpened()
    } catch (err) {
      setError(err as ApiError)
    } finally {
      setSaving(false)
    }
  }

  if (!open) {
    return (
      <button type="button" className="card account-card account-card-new" onClick={() => setOpen(true)}>
        <span className="plus" aria-hidden="true">
          +
        </span>
        Open a new account
      </button>
    )
  }

  return (
    <form className="card account-card account-form" onSubmit={onSubmit}>
      <h3>New account</h3>
      {error && Object.keys(error.fieldErrors).length === 0 && <Alert>{error.message}</Alert>}
      <div className="field">
        <label htmlFor="new-type">Type</label>
        <select id="new-type" value={type} onChange={(e) => setType(e.target.value as AccountType)}>
          <option value="SAVINGS">Savings</option>
          <option value="EVERYDAY">Everyday</option>
        </select>
      </div>
      <div className="field">
        <label htmlFor="new-nickname">Name</label>
        <input
          id="new-nickname"
          value={nickname}
          placeholder="e.g. Emergency fund"
          onChange={(e) => setNickname(e.target.value)}
          aria-invalid={!!error?.fieldErrors.nickname}
        />
        {error?.fieldErrors.nickname && <p className="field-error">{error.fieldErrors.nickname}</p>}
      </div>
      <div className="button-row">
        <button type="button" className="btn btn-ghost btn-sm" onClick={() => setOpen(false)}>
          Cancel
        </button>
        <button type="submit" className="btn btn-primary btn-sm" disabled={saving}>
          {saving ? 'Opening…' : 'Open account'}
        </button>
      </div>
    </form>
  )
}
