const currency = new Intl.NumberFormat('en-AU', { style: 'currency', currency: 'AUD' })
const shortDate = new Intl.DateTimeFormat('en-AU', { day: 'numeric', month: 'short', year: 'numeric' })
const dateTime = new Intl.DateTimeFormat('en-AU', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })

export const money = (amount: number) => currency.format(amount)

/** "+$20.00" / "−$20.00" for statement lines. */
export const signedMoney = (amount: number) => (amount > 0 ? '+' : amount < 0 ? '−' : '') + currency.format(Math.abs(amount))

export const formatDate = (iso: string) => shortDate.format(new Date(iso))

export const formatDateTime = (iso: string) => dateTime.format(new Date(iso))

/** 1234567890 -> "1234 567 890", easier to read out and compare. */
export const formatAccountNumber = (number: string) => number.replace(/^(\d{4})(\d{3})(\d{3})$/, '$1 $2 $3')

export const accountTypeLabel = { EVERYDAY: 'Everyday', SAVINGS: 'Savings' } as const

export function greeting(now = new Date()) {
  const hour = now.getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 18) return 'Good afternoon'
  return 'Good evening'
}
