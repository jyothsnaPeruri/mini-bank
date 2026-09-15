import { NavLink, Outlet } from 'react-router'
import { useAuth } from '../useAuth'
import { Logo } from './Logo'
import { ServerWakeNotice } from './ServerWakeNotice'

export function Layout() {
  const { user, logout } = useAuth()
  const isAdmin = user?.role === 'ADMIN'

  return (
    <div className="app-shell">
      <div className="demo-strip">Portfolio demo · not a real bank · no real money</div>
      <header className="topbar">
        <div className="topbar-inner">
          <NavLink to={isAdmin ? '/admin' : '/'} className="topbar-brand" aria-label="Mini Bank home">
            <Logo />
          </NavLink>
          <nav className="topnav" aria-label="Main">
            {isAdmin ? (
              <NavLink to="/admin">Admin</NavLink>
            ) : (
              <>
                <NavLink to="/" end>
                  Accounts
                </NavLink>
                <NavLink to="/transfer">Pay &amp; transfer</NavLink>
              </>
            )}
          </nav>
          <div className="topbar-user">
            <span className="avatar" aria-hidden="true">
              {user?.fullName.charAt(0).toUpperCase()}
            </span>
            <span className="topbar-name">{user?.fullName}</span>
            <button type="button" className="btn btn-ghost btn-sm" onClick={logout}>
              Sign out
            </button>
          </div>
        </div>
      </header>
      <ServerWakeNotice />
      <main className="page">
        <Outlet />
      </main>
    </div>
  )
}
