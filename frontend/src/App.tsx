import type { ReactNode } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router'
import { useAuth } from './useAuth'
import { Layout } from './components/Layout'
import { AccountPage } from './pages/AccountPage'
import { AdminPage } from './pages/AdminPage'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage } from './pages/LoginPage'
import { TransferPage } from './pages/TransferPage'

function RequireAuth({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const location = useLocation()
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return children
}

function CustomerOnly({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  return user?.role === 'ADMIN' ? <Navigate to="/admin" replace /> : children
}

function AdminOnly({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  return user?.role === 'ADMIN' ? children : <Navigate to="/" replace />
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<CustomerOnly><DashboardPage /></CustomerOnly>} />
        <Route path="accounts/:id" element={<CustomerOnly><AccountPage /></CustomerOnly>} />
        <Route path="transfer" element={<CustomerOnly><TransferPage /></CustomerOnly>} />
        <Route path="admin" element={<AdminOnly><AdminPage /></AdminOnly>} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
