import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { Alert } from '../components/Alert'
import { accountTypeLabel, formatAccountNumber, formatDateTime, money, signedMoney } from '../format'
import type { Account, LedgerEntry, Page } from '../types'
import { useApiData } from '../useApiData'

type Direction = '' | 'in' | 'out'

const PAGE_SIZE = 10

/** Start of the given local day as an ISO instant, so filters match the user's own timezone. */
const startOfDay = (date: string) => new Date(`${date}T00:00:00`).toISOString()
const startOfNextDay = (date: string) => {
  const next = new Date(`${date}T00:00:00`)
  next.setDate(next.getDate() + 1)
  return next.toISOString()
}

export function AccountPage() {
  const { id } = useParams()
  const { data: account, error: accountError } = useApiData<Account>(`/api/accounts/${id}`)

  const [direction, setDirection] = useState<Direction>('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [page, setPage] = useState(0)

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 300)
    return () => clearTimeout(timer)
  }, [search])

  const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) })
  if (direction) params.set('direction', direction)
  if (from) params.set('from', startOfDay(from))
  if (to) params.set('to', startOfNextDay(to))
  if (debouncedSearch.trim()) params.set('q', debouncedSearch.trim())
  const { data: history, error: historyError, loading } = useApiData<Page<LedgerEntry>>(
    `/api/accounts/${id}/transactions?${params}`,
  )

  // Any filter change goes back to the first page
  const withReset =
    <T,>(setter: (value: T) => void) =>
    (value: T) => {
      setter(value)
      setPage(0)
    }
  const hasFilters = direction || from || to || search
  const clearFilters = () => {
    setDirection('')
    setFrom('')
    setTo('')
    setSearch('')
    setDebouncedSearch('')
    setPage(0)
  }

  if (accountError) {
    return (
      <>
        <Link to="/" className="back-link">
          ← Accounts
        </Link>
        <Alert>{accountError.message}</Alert>
      </>
    )
  }

  return (
    <>
      <Link to="/" className="back-link">
        ← Accounts
      </Link>

      <div className="card account-hero">
        <div>
          <div className="account-card-top">
            {account && (
              <span className={`badge badge-${account.type.toLowerCase()}`}>{accountTypeLabel[account.type]}</span>
            )}
            {account?.status === 'FROZEN' && <span className="badge badge-frozen">Frozen</span>}
          </div>
          <h1>{account?.nickname ?? ' '}</h1>
          <p className="account-number">{account ? formatAccountNumber(account.accountNumber) : ' '}</p>
        </div>
        <div className="account-hero-balance">
          <span className="muted">Available balance</span>
          <strong className="amount-xl">{account ? money(account.balance) : '—'}</strong>
          <Link to={`/transfer?from=${id}`} className="btn btn-primary btn-sm">
            Transfer from this account
          </Link>
        </div>
      </div>

      {account?.status === 'FROZEN' && (
        <Alert tone="info">This account has been frozen by the bank. You can view it, but can't send or receive money.</Alert>
      )}

      <section className="section-gap" aria-labelledby="history-heading">
        <div className="section-header">
          <h2 id="history-heading">Transactions</h2>
          {history && (
            <span className="muted small">
              {history.totalElements} {history.totalElements === 1 ? 'transaction' : 'transactions'}
            </span>
          )}
        </div>

        <div className="card filters">
          <div className="segmented" role="group" aria-label="Direction">
            {(
              [
                ['', 'All'],
                ['in', 'Money in'],
                ['out', 'Money out'],
              ] as const
            ).map(([value, label]) => (
              <button
                key={value}
                type="button"
                className={direction === value ? 'active' : ''}
                aria-pressed={direction === value}
                onClick={() => withReset(setDirection)(value)}
              >
                {label}
              </button>
            ))}
          </div>
          <div className="field field-inline">
            <label htmlFor="from">From</label>
            <input id="from" type="date" value={from} max={to || undefined} onChange={(e) => withReset(setFrom)(e.target.value)} />
          </div>
          <div className="field field-inline">
            <label htmlFor="to">To</label>
            <input id="to" type="date" value={to} min={from || undefined} onChange={(e) => withReset(setTo)(e.target.value)} />
          </div>
          <div className="field field-inline field-grow">
            <label htmlFor="search">Search</label>
            <input
              id="search"
              type="search"
              placeholder="Description or name"
              value={search}
              onChange={(e) => withReset(setSearch)(e.target.value)}
            />
          </div>
          {hasFilters && (
            <button type="button" className="btn btn-ghost btn-sm" onClick={clearFilters}>
              Clear
            </button>
          )}
        </div>

        {historyError && <Alert>{historyError.message}</Alert>}

        <div className={loading ? 'card table-card is-loading' : 'card table-card'}>
          {history && history.content.length === 0 ? (
            <p className="empty">{hasFilters ? 'No transactions match these filters.' : 'No transactions yet.'}</p>
          ) : (
            <table className="ledger">
              <thead>
                <tr>
                  <th scope="col" className="col-date">
                    Date
                  </th>
                  <th scope="col">Details</th>
                  <th scope="col" className="num">
                    Amount
                  </th>
                  <th scope="col" className="num">
                    Balance
                  </th>
                </tr>
              </thead>
              <tbody>
                {history?.content.map((entry) => (
                  <tr key={entry.id}>
                    <td className="col-date nowrap muted">{formatDateTime(entry.createdAt)}</td>
                    <td>
                      <span className="activity-title">{entry.description ?? 'Transfer'}</span>
                      {entry.counterparty && (
                        <span className="muted small block">
                          {entry.amount < 0 ? 'To' : 'From'} {entry.counterparty}
                        </span>
                      )}
                      <span className="muted small block mobile-only">{formatDateTime(entry.createdAt)}</span>
                    </td>
                    <td className={entry.amount > 0 ? 'num amount credit' : 'num amount'}>{signedMoney(entry.amount)}</td>
                    <td className="num muted">{money(entry.balanceAfter)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {history && history.totalPages > 1 && (
          <nav className="pagination" aria-label="Pages">
            <button type="button" className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => setPage(page - 1)}>
              ← Newer
            </button>
            <span className="muted small">
              Page {history.page + 1} of {history.totalPages}
            </span>
            <button
              type="button"
              className="btn btn-ghost btn-sm"
              disabled={page + 1 >= history.totalPages}
              onClick={() => setPage(page + 1)}
            >
              Older →
            </button>
          </nav>
        )}
      </section>
    </>
  )
}
