export type Role = 'CUSTOMER' | 'ADMIN'
export type AccountType = 'EVERYDAY' | 'SAVINGS'
export type AccountStatus = 'ACTIVE' | 'FROZEN'
export type EntryType = 'OPENING_DEPOSIT' | 'TRANSFER_IN' | 'TRANSFER_OUT'

export interface User {
  id: string
  email: string
  fullName: string
  role: Role
}

export interface AuthResponse {
  token: string
  user: User
}

export interface Account {
  id: string
  accountNumber: string
  type: AccountType
  nickname: string
  balance: number
  currency: string
  status: AccountStatus
  createdAt: string
}

export interface LedgerEntry {
  id: string
  transferId: string | null
  type: EntryType
  amount: number
  balanceAfter: number
  description: string | null
  counterparty: string | null
  createdAt: string
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Payee {
  accountNumber: string
  holderName: string
}

export interface Transfer {
  id: string
  fromAccountNumber: string
  toAccountNumber: string
  toAccountHolder: string
  amount: number
  description: string | null
  createdAt: string
}

export interface AdminStats {
  customers: number
  accounts: number
  totalDeposits: number
  transfersLast24Hours: number
}

export interface AdminCustomer {
  id: string
  email: string
  fullName: string
  role: Role
  createdAt: string
  accounts: Account[]
}
