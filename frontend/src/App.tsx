import { Routes, Route, Navigate, Outlet } from 'react-router-dom';
import { Layout } from '@/components/Layout';
import { Dashboard } from '@/pages/Dashboard';
import { Documents } from '@/pages/Documents';
import { Login } from '@/pages/Login';
import { Register } from '@/pages/Register';
import { OrganizationWorkspace } from '@/pages/OrganizationWorkspace';
import { Search } from '@/pages/Search';
import { Settings } from '@/pages/Settings';
import { AiChat } from '@/pages/AiChat';
import { TenantSettings } from '@/pages/TenantSettings';
import { AiSettings } from '@/pages/AiSettings';
import { Administration } from '@/pages/Administration';
import { NotFound } from '@/pages/NotFound';
import { useAuth } from '@/context/AuthContext';

// Protected route wrapper
function ProtectedRoute() {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-dark-950">
        <div className="w-8 h-8 border-4 border-primary-600 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  return isAuthenticated ? <Outlet /> : <Navigate to="/login" replace />;
}

// Public route wrapper (redirects authenticated users)
function PublicRoute() {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-dark-950">
        <div className="w-8 h-8 border-4 border-primary-600 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  return isAuthenticated ? <Navigate to="/" replace /> : <Outlet />;
}

function App() {
  return (
    <Routes>
      {/* Public routes */}
      <Route element={<PublicRoute />}>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
      </Route>

      {/* Protected routes */}
      <Route element={<ProtectedRoute />}>
        <Route path="/" element={<Layout />}>
          <Route index element={<Dashboard />} />
          <Route path="organizations" element={<OrganizationWorkspace />} />
          <Route path="org/:organizationId" element={<OrganizationWorkspace />} />
          <Route path="documents" element={<Documents />} />
          <Route path="search" element={<Search />} />
          <Route path="ai" element={<AiChat />} />
          <Route path="tenant-settings" element={<TenantSettings />} />
          <Route path="ai-settings" element={<AiSettings />} />
          <Route path="admin" element={<Administration />} />
          <Route path="settings" element={<Settings />} />
          <Route path="*" element={<NotFound />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default App;