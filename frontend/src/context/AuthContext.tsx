import { createContext, useContext, useState, useEffect, ReactNode, useCallback, useRef } from 'react';
import { authApi, apiClient, organizationApi, type MeResponse, type SessionInfo, type OrganizationResponse } from '@/services/api';

interface AuthState {
  user: MeResponse | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  sessions: SessionInfo[];
  organizations: OrganizationResponse[];
  currentOrganization: OrganizationResponse | null;
}

interface AuthContextType extends AuthState {
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, displayName: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshAuth: () => Promise<void>;
  fetchSessions: () => Promise<void>;
  revokeSession: (sessionId: string) => Promise<void>;
  revokeAllSessions: () => Promise<void>;
  fetchOrganizations: () => Promise<void>;
  setCurrentOrganization: (org: OrganizationResponse | null) => void;
  createOrganization: (name: string, slug: string) => Promise<OrganizationResponse>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

// Only persist a workspace identifier, never JWTs or document data.
const workspaceKey = (userId: string) => `documind:lastOrganization:${userId}`;
function restoreWorkspace(userId: string, organizations: OrganizationResponse[]): OrganizationResponse | null {
  const savedId = localStorage.getItem(workspaceKey(userId));
  return organizations.find(o => o.id === savedId) ?? organizations[0] ?? null;
}

async function loadWorkspaces(userId: string): Promise<{ organizations: OrganizationResponse[]; currentOrganization: OrganizationResponse | null }> {
  const response = await organizationApi.list();
  if (!response.success || !response.data) throw new Error(response.error?.message || 'Unable to load organizations');
  const organizations = response.data;
  const currentOrganization = restoreWorkspace(userId, organizations);
  return { organizations, currentOrganization };
}


export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({
    user: null,
    accessToken: null,
    isAuthenticated: false,
    isLoading: true,
    sessions: [],
    organizations: [],
    currentOrganization: null,
  });

  const updateAuthToken = useCallback((token: string | null) => {
    apiClient.setAuthToken(token);
  }, []);

  const refreshAuth = useCallback(async () => {
    // Always attempt to restore session via refresh token (HttpOnly cookie)
    try {
      const refreshResponse = await authApi.refresh();
      if (refreshResponse.success && refreshResponse.data?.accessToken) {
        updateAuthToken(refreshResponse.data.accessToken);
        const meRes = await authApi.me();
        if (meRes.success && meRes.data) {
          const [sessionsResponse] = await Promise.all([
            authApi.sessions().catch(() => ({ success: true, data: [] })),
          ]);
          // Organizations live in PostgreSQL; reload them after every session restoration.
          // An organization-list failure must never be treated as authentication failure.
          let workspaces = { organizations: [] as OrganizationResponse[], currentOrganization: null as OrganizationResponse | null };
          try { workspaces = await loadWorkspaces(meRes.data!.id); }
          catch (error) { console.error('Unable to restore organizations', error); }
          setState(prev => ({
            ...prev,
            user: meRes.data!,
            accessToken: refreshResponse.data!.accessToken,
            isAuthenticated: true,
            isLoading: false,
            sessions: sessionsResponse.success && sessionsResponse.data ? sessionsResponse.data : [],
            ...workspaces,
          }));
          return;
        }
      }
      // Refresh failed - not authenticated
      updateAuthToken(null);
      setState(prev => ({ ...prev, isLoading: false }));
    } catch {
      updateAuthToken(null);
      setState(prev => ({ ...prev, isLoading: false }));
    }
  }, [updateAuthToken]);

  // React StrictMode deliberately replays mount effects in development.
  // Refresh tokens are rotated server-side, so repeating this request can
  // invalidate the session and clear the access token after a valid response.
  const initialRefreshStarted = useRef(false);
  useEffect(() => {
    if (initialRefreshStarted.current) return;
    initialRefreshStarted.current = true;
    void refreshAuth();
  }, [refreshAuth]);

  const login = async (email: string, password: string) => {
    const response = await authApi.login({ email, password });
    if (!response.success || !response.data) {
      throw new Error(response.error?.message || 'Login failed');
    }
    updateAuthToken(response.data.accessToken);
    // Fetch full user info including status
    const meResponse = await authApi.me();
    if (meResponse.success && meResponse.data) {
      setState(prev => ({
        ...prev,
        user: meResponse.data!,
        accessToken: response.data!.accessToken,
        isAuthenticated: true,
      }));
    } else {
      // Fallback to basic user info from login response
      setState(prev => ({
        ...prev,
        user: { ...response.data!.user, status: 'ACTIVE' } as MeResponse,
        accessToken: response.data!.accessToken,
        isAuthenticated: true,
      }));
    }
    // Restore organizations for this user after authentication.
    const signedInUserId = (meResponse.success && meResponse.data ? meResponse.data.id : response.data.user.id);
    try {
      const workspaces = await loadWorkspaces(signedInUserId);
      setState(prev => ({ ...prev, ...workspaces }));
    } catch (error) {
      console.error('Unable to load organizations', error);
    }

  };

  const register = async (email: string, password: string, displayName: string) => {
    const response = await authApi.register({ email, password, displayName });
    if (!response.success || !response.data) {
      throw new Error(response.error?.message || 'Registration failed');
    }
    updateAuthToken(response.data.accessToken);
    // Fetch full user info including status
    const meResponse = await authApi.me();
    if (meResponse.success && meResponse.data) {
      setState(prev => ({
        ...prev,
        user: meResponse.data!,
        accessToken: response.data!.accessToken,
        isAuthenticated: true,
      }));
    } else {
      // Fallback to basic user info from register response
      setState(prev => ({
        ...prev,
        user: { ...response.data!.user, status: 'ACTIVE' } as MeResponse,
        accessToken: response.data!.accessToken,
        isAuthenticated: true,
      }));
    }
    // Restore organizations for this user after authentication.
    const signedInUserId = (meResponse.success && meResponse.data ? meResponse.data.id : response.data.user.id);
    try {
      const workspaces = await loadWorkspaces(signedInUserId);
      setState(prev => ({ ...prev, ...workspaces }));
    } catch (error) {
      console.error('Unable to load organizations', error);
    }

  };

  const logout = async () => {
    try {
      await authApi.logout();
    } catch {
      // Ignore logout errors
    } finally {
      updateAuthToken(null);
      setState(prev => ({
        ...prev,
        user: null,
        accessToken: null,
        isAuthenticated: false,
        sessions: [],
        organizations: [],
        currentOrganization: null,
      }));
    }
  };

  const fetchSessions = useCallback(async () => {
    const response = await authApi.sessions();
    if (response.success && response.data) {
      setState(prev => ({ ...prev, sessions: response.data! }));
    }
  }, []);

  const revokeSession = async (sessionId: string) => {
    const response = await authApi.revokeSession(sessionId);
    if (response.success) {
      setState(prev => ({
        ...prev,
        sessions: prev.sessions.filter(s => s.id !== sessionId),
      }));
    }
  };

  const revokeAllSessions = async () => {
    const response = await authApi.revokeAllSessions();
    if (response.success) {
      setState(prev => ({ ...prev, sessions: [] }));
    }
  };

  const fetchOrganizations = async () => {
    const response = await organizationApi.list();
    if (response.success && response.data) {
      setState(prev => ({
        ...prev,
        organizations: response.data!,
        currentOrganization: prev.user ? restoreWorkspace(prev.user.id, response.data!) : null,
      }));
    }
  };

  const setCurrentOrganization = useCallback((org: OrganizationResponse | null) => {
    setState(prev => {
      if (prev.user && org && prev.organizations.some(item => item.id === org.id)) {
        localStorage.setItem(workspaceKey(prev.user.id), org.id);
      }
      return { ...prev, currentOrganization: org };
    });
  }, []);

  const createOrganization = async (name: string, slug: string) => {
    const response = await organizationApi.create({ name, slug });
    if (!response.success || !response.data) {
      throw new Error(response.error?.message || 'Failed to create organization');
    }
    setState(prev => {
      if (prev.user) localStorage.setItem(workspaceKey(prev.user.id), response.data!.id);
      return { ...prev, organizations: [...prev.organizations.filter(o => o.id !== response.data!.id), response.data!], currentOrganization: response.data! };
    });
    return response.data!;
  };

  return (
    <AuthContext.Provider value={{
      ...state,
      login,
      register,
      logout,
      refreshAuth,
      fetchSessions,
      revokeSession,
      revokeAllSessions,
      fetchOrganizations,
      setCurrentOrganization,
      createOrganization,
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

export { organizationApi };