import { Outlet, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useState, useEffect } from 'react';
import { useAuth } from '@/context/AuthContext';
import {
  LayoutDashboard,
  FileText,
  Search,
  Bot,
  Settings,
  ChevronLeft,
  ChevronRight,
  Menu,
  Zap,
  LogOut,
  User,
  Building2,
  ChevronDown,
} from 'lucide-react';
import { healthApi, OrganizationResponse } from '@/services/api';

interface NavItem {
  path: string;
  label: string;
  icon: React.ReactNode;
  comingSoon?: boolean;
}

const navItems: NavItem[] = [
  { path: '/', label: 'Dashboard', icon: <LayoutDashboard className="w-5 h-5" /> },
  { path: '/organizations', label: 'Organizations', icon: <Building2 className="w-5 h-5" /> },
  { path: '/documents', label: 'Documents', icon: <FileText className="w-5 h-5" /> },
  { path: '/search', label: 'Search', icon: <Search className="w-5 h-5" /> },
  { path: '/ai', label: 'AI Assistant', icon: <Bot className="w-5 h-5" /> },
  { path: '/settings', label: 'Settings', icon: <Settings className="w-5 h-5" /> },
  { path: '/tenant-settings', label: 'Tenant Configuration', icon: <Settings className="w-5 h-5" /> },
  { path: '/ai-settings', label: 'AI Settings', icon: <Bot className="w-5 h-5" /> },
  { path: '/admin', label: 'Administration', icon: <LayoutDashboard className="w-5 h-5" /> },
];

export function Layout() {
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [healthStatus, setHealthStatus] = useState<'checking' | 'healthy' | 'unhealthy'>('checking');
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout, organizations, currentOrganization, setCurrentOrganization } = useAuth();
  useEffect(() => {
    checkHealth();
    const interval = setInterval(checkHealth, 30000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    document.documentElement.classList.add('dark');
    document.documentElement.style.colorScheme = 'dark';
  }, []);

  const checkHealth = async () => {
    try {
      const response = await healthApi.check();
      if (response.success && response.data?.status === 'UP') {
        setHealthStatus('healthy');
      } else {
        setHealthStatus('unhealthy');
      }
    } catch {
      setHealthStatus('unhealthy');
    }
  };

  const toggleSidebar = () => {
    if (window.innerWidth < 1024) {
      setMobileMenuOpen(!mobileMenuOpen);
    } else {
      setSidebarOpen(!sidebarOpen);
    }
  };

  const toggleCollapse = () => {
    setSidebarCollapsed(!sidebarCollapsed);
  };

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const handleOrgSwitch = (org: OrganizationResponse) => {
    setCurrentOrganization(org);
    navigate(`/org/${org.id}`);
    setUserMenuOpen(false);
  };

  return (
    <div className="min-h-screen bg-dark-950 flex">
      {/* Mobile Overlay */}
      {mobileMenuOpen && (
        <div
          className="fixed inset-0 bg-black/50 z-40 lg:hidden"
          onClick={() => setMobileMenuOpen(false)}
          aria-hidden="true"
        />
      )}

      {/* Sidebar */}
      <aside
        className={`fixed lg:relative z-50 h-full transition-all duration-300 ease-in-out bg-dark-900/80 border-r border-dark-700/50 backdrop-blur-sm flex flex-col ${
          sidebarCollapsed ? 'w-16' : 'w-64'
        } ${!sidebarOpen && 'lg:hidden'}`}
        aria-label="Main navigation"
      >
        {/* Logo / Brand */}
        <div className="flex items-center justify-between h-16 px-4 border-b border-dark-700/50">
          {!sidebarCollapsed && (
            <NavLink to="/" className="flex items-center gap-2 text-xl font-bold text-dark-50">
              <div className="w-8 h-8 rounded-lg bg-primary-600 flex items-center justify-center">
                <Zap className="w-5 h-5 text-white" />
              </div>
              <span>InferaDocs</span>
            </NavLink>
          )}
          {sidebarCollapsed && (
            <NavLink to="/" className="flex items-center justify-center text-xl font-bold text-dark-50 w-full" title="InferaDocs">
              <div className="w-8 h-8 rounded-lg bg-primary-600 flex items-center justify-center">
                <Zap className="w-5 h-5 text-white" />
              </div>
            </NavLink>
          )}
          <button
            onClick={toggleCollapse}
            className="lg:hidden p-1 rounded-lg text-dark-400 hover:text-dark-100 hover:bg-dark-800 transition-colors"
            aria-label={sidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          >
            {sidebarCollapsed ? <ChevronRight className="w-5 h-5" /> : <ChevronLeft className="w-5 h-5" />}
          </button>
        </div>

        {/* Navigation */}
        <nav className="flex-1 overflow-y-auto p-3 space-y-1" aria-label="Primary navigation">
          {navItems.map((item) => {
            const isActive = location.pathname === item.path || (item.path !== '/' && location.pathname.startsWith(item.path));
            return (
              <NavLink
                key={item.path}
                to={item.path}
                className={({ isActive: active }) =>
                  `flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-all duration-200 ${
                    active
                      ? 'bg-primary-600/20 text-primary-400 border border-primary-600/30'
                      : 'text-dark-400 hover:bg-dark-800/50 hover:text-dark-100'
                  } ${sidebarCollapsed ? 'justify-center' : ''}`
                }
                title={sidebarCollapsed ? item.label : undefined}
                aria-current={isActive ? 'page' : undefined}
              >
                <span className="flex-shrink-0">{item.icon}</span>
                {!sidebarCollapsed && (
                  <>
                    <span>{item.label}</span>
                    {item.comingSoon && (
                      <span className="ml-auto px-1.5 py-0.5 text-xs font-medium bg-yellow-500/20 text-yellow-400 border border-yellow-500/30 rounded-full">
                        Soon
                      </span>
                    )}
                  </>
                )}
              </NavLink>
            );
          })}
        </nav>

        {/* Footer / System Status */}
        <div className="p-3 border-t border-dark-700/50">
          {!sidebarCollapsed && (
            <div className="space-y-3">
              <div className="flex items-center gap-3 p-3 rounded-lg bg-dark-800/50 border border-dark-700/50">
                <div className={`w-2 h-2 rounded-full ${healthStatus === 'healthy' ? 'bg-green-500' : healthStatus === 'unhealthy' ? 'bg-red-500' : 'bg-yellow-500 animate-pulse'}`} />
                <div className="flex-1 min-w-0">
                  <p className="text-xs font-medium text-dark-300 truncate">Backend Connection</p>
                  <p className="text-xs text-dark-500 capitalize">{healthStatus}</p>
                </div>
              </div>

            </div>
          )}
        </div>
      </aside>

      {/* Main Content */}
      <div className="flex-1 flex flex-col min-w-0 lg:ml-0">
        {/* Top Header */}
        <header className="sticky top-0 z-30 h-16 bg-dark-900/80 border-b border-dark-700/50 backdrop-blur-sm flex items-center justify-between px-4 lg:px-6">
          <div className="flex items-center gap-4">
            <button
              onClick={toggleSidebar}
              className="lg:hidden p-2 rounded-lg text-dark-400 hover:text-dark-100 hover:bg-dark-800 transition-colors"
              aria-label="Toggle menu"
            >
              <Menu className="w-6 h-6" />
            </button>
            <h1 className="text-lg font-semibold text-dark-100 hidden sm:block">
              {navItems.find((item) => location.pathname === item.path || (item.path !== '/' && location.pathname.startsWith(item.path)))?.label || 'Dashboard'}
            </h1>
          </div>

          <div className="flex items-center gap-3">
            {/* User Menu */}
            <div className="relative" data-testid="user-menu-container">
              <button
                data-testid="user-menu"
                onClick={() => setUserMenuOpen(!userMenuOpen)}
                className="flex items-center gap-2 p-2 rounded-lg text-dark-300 hover:text-dark-100 hover:bg-dark-800 transition-colors"
                aria-label="User menu"
                aria-expanded={userMenuOpen}
                aria-haspopup="true"
              >
                <div className="w-8 h-8 rounded-full bg-primary-600/20 flex items-center justify-center text-primary-400">
                  <User className="w-5 h-5" />
                </div>
                <span className="hidden sm:block font-medium text-dark-100 max-w-[150px] truncate">
                  {user?.displayName || user?.email}
                </span>
                <ChevronDown className="w-4 h-4 text-dark-500 hidden sm:block" />
              </button>

              {userMenuOpen && (
                <>
                  <div
                    className="fixed inset-0 z-40"
                    onClick={() => setUserMenuOpen(false)}
                    aria-hidden="true"
                  />
                  <div className="absolute right-0 mt-2 w-56 card shadow-lg border border-dark-700/50 py-1 z-50 animate-fade-in">
                    <div className="px-3 py-2 border-b border-dark-700/50">
                      <p className="font-medium text-dark-100 truncate">{user?.displayName || user?.email}</p>
                      <p className="text-xs text-dark-500 truncate">{user?.email}</p>
                    </div>
                    {organizations.length > 0 && (
                      <>
                        <div className="px-3 py-2 text-xs font-medium text-dark-500 uppercase tracking-wider">
                          Organizations
                        </div>
                        {organizations.map((org: OrganizationResponse) => (
                          <button
                            key={org.id}
                            data-testid="org-option"
                            onClick={() => handleOrgSwitch(org)}
                            className={`w-full px-3 py-2 text-left text-sm flex items-center gap-2 transition-colors ${
                              currentOrganization?.id === org.id
                                ? 'bg-primary-600/10 text-primary-400'
                                : 'text-dark-300 hover:bg-dark-800/50 hover:text-dark-100'
                            }`}
                          >
                            <Building2 className="w-4 h-4 flex-shrink-0" />
                            <span className="truncate">{org.name}</span>
                            {currentOrganization?.id === org.id && (
                              <span className="ml-auto text-xs text-primary-400">Current</span>
                            )}
                          </button>
                        ))}
                        <hr className="my-1 border-dark-700/50" />
                      </>
                    )}
                    <button type="button" onClick={() => { navigate('/organizations'); setUserMenuOpen(false); }} className="w-full px-3 py-2 text-left text-sm text-primary-400 hover:bg-dark-800/50 flex items-center gap-2">
                      <Building2 className="w-4 h-4" /> Manage / Create organizations
                    </button>
                    <button
                      data-testid="logout-button"
                      onClick={handleLogout}
                      className="w-full px-3 py-2 text-left text-sm text-red-400 hover:bg-red-500/10 flex items-center gap-2"
                    >
                      <LogOut className="w-4 h-4" />
                      Logout
                    </button>
                  </div>
                </>
              )}
            </div>
          </div>
        </header>

        {/* Page Content */}
        <main className="flex-1 overflow-y-auto p-4 lg:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}