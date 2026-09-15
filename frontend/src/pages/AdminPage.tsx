import { useState } from 'react'
import { api, ApiError } from '../api'
import { Alert } from '../components/Alert'
import { accountTypeLabel, formatAccountNumber, formatDate, money } from '../format'
import type { Account, AccountStatus, AdminCustomer, AdminStats } from '../types'
import { useApiData } from '../useApiData'

export function AdminPage() {
  const stats = useApiData<AdminStats>('/api/admin/stats')
  const customers = useApiData<AdminCustomer[]>('/api/admin/customers')
  const [query, setQuery] = useState('')
  const [pendingId, setPendingId] = useState<string | null>(null)
  const [actionError, setActionError] = useState<ApiError | null>(null)

  const setStatus = async (account: Account, status: AccountStatus) => {
    setPendingId(account.id)
    setActionError(null)
    try {
      await api(`/api/admin/accounts/${account.id}`, { method: 'PATCH', body: { status } })
      customers.reload()
    } catch (err) {
      setActionError(err as ApiError)
    } finally {
      setPendingId(null)
    }
  }

  const needle = query.trim().toLowerCase()
  const visible =
    customers.data?.filter(
      (customer) =>
        !needle ||
        customer.fullName.toLowerCase().includes(needle) ||
        customer.email.includes(needle) ||
        customer.accounts.some((account) => account.accountNumber.includes(needle)),
    ) ?? []

  return (
    <>
      <div className="page-header">
        <div>
          <p className="eyebrow">Bank operations</p>
          <h1>Admin</h1>
        </div>
      </div>

      {stats.error && <Alert>{stats.error.message}</Alert>}
      <div className="stat-grid">
        <Stat label="Customers" value={stats.data?.customers} />
        <Stat label="Accounts" value={stats.data?.accounts} />
        <Stat label="Total deposits" value={stats.data && money(stats.data.totalDeposits)} />
        <Stat label="Transfers (24h)" value={stats.data?.transfersLast24Hours} />
      </div>

      <section className="section-gap" aria-labelledby="customers-heading">
        <div className="section-header">
          <h2 id="customers-heading">Customers</h2>
          <input
            type="search"
            className="search-input"
            placeholder="Search name, email or account"
            aria-label="Search customers"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
        {customers.error && <Alert>{customers.error.message}</Alert>}
        {actionError && <Alert>{actionError.message}</Alert>}
        <div className="card table-card">
          <table className="ledger admin-table">
            <thead>
              <tr>
                <th scope="col">Customer</th>
                <th scope="col">Joined</th>
                <th scope="col">Accounts</th>
              </tr>
            </thead>
            <tbody>
              {visible.map((customer) => (
                <tr key={customer.id}>
                  <td>
                    <span className="activity-title">{customer.fullName}</span>
                    <span className="muted small block">{customer.email}</span>
                    {customer.role === 'ADMIN' && <span className="badge badge-admin">Admin</span>}
                  </td>
                  <td className="nowrap muted">{formatDate(customer.createdAt)}</td>
                  <td>
                    {customer.accounts.length === 0 && <span className="muted small">No accounts</span>}
                    <ul className="admin-accounts">
                      {customer.accounts.map((account) => (
                        <li key={account.id}>
                          <span>
                            {account.nickname}{' '}
                            <span className="muted small">
                              {accountTypeLabel[account.type]} · {formatAccountNumber(account.accountNumber)}
                            </span>
                          </span>
                          <span className="amount">{money(account.balance)}</span>
                          {account.status === 'ACTIVE' ? (
                            <button
                              type="button"
                              className="btn btn-danger-ghost btn-sm"
                              disabled={pendingId === account.id}
                              onClick={() => setStatus(account, 'FROZEN')}
                            >
                              Freeze
                            </button>
                          ) : (
                            <button
                              type="button"
                              className="btn btn-ghost btn-sm"
                              disabled={pendingId === account.id}
                              onClick={() => setStatus(account, 'ACTIVE')}
                            >
                              Unfreeze
                            </button>
                          )}
                        </li>
                      ))}
                    </ul>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {customers.data && visible.length === 0 && <p className="empty">No customers match “{query}”.</p>}
        </div>
      </section>
    </>
  )
}

function Stat({ label, value }: { label: string; value: string | number | undefined | null }) {
  return (
    <div className="card stat">
      <span className="muted small">{label}</span>
      <strong>{value ?? '—'}</strong>
    </div>
  )
}
