import axios, { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081/api/v1';

class ApiClient {
  private client: AxiosInstance;

  constructor() {
    this.client = axios.create({
      baseURL: API_BASE_URL,
      timeout: 30000,
      headers: {
        'Content-Type': 'application/json',
      },
      withCredentials: true,
    });

    this.client.interceptors.request.use(
      (config: InternalAxiosRequestConfig) => {
        const requestId = crypto.randomUUID();
        config.headers['X-Request-ID'] = requestId;

        // Add CSRF token from cookie for state-changing requests
        if (['post', 'put', 'patch', 'delete'].includes(config.method?.toLowerCase() || '')) {
          const xsrfToken = document.cookie
            .split('; ')
            .find(row => row.startsWith('XSRF-TOKEN='))
            ?.split('=')[1];
          if (xsrfToken) {
            config.headers['X-XSRF-TOKEN'] = xsrfToken;
          }
        }

        return config;
      },
      (error) => Promise.reject(error)
    );

    this.client.interceptors.response.use(
      (response) => response,
      (error: AxiosError) => {
        if (error.response) {
          console.error(`API Error [${error.response.status}]:`, error.response.data);
          const payload = error.response.data as { error?: { message?: string; code?: string } } | undefined;
          if (payload?.error?.message) {
            error.message = payload.error.message;
          }
        } else if (error.request) {
          console.error('Network Error:', error.message);
        } else {
          console.error('Request Error:', error.message);
        }
        return Promise.reject(error);
      }
    );
  }

  async get<T>(url: string, params?: Record<string, unknown>) {
    const response = await this.client.get<T>(url, { params });
    return response.data;
  }

  async post<T>(url: string, data?: unknown, timeout?: number) {
    const response = await this.client.post<T>(url, data, timeout ? { timeout } : {});
    return response.data;
  }

  async put<T>(url: string, data?: unknown) {
    const response = await this.client.put<T>(url, data);
    return response.data;
  }

  async patch<T>(url: string, data?: unknown) {
    const response = await this.client.patch<T>(url, data);
    return response.data;
  }

  async delete<T>(url: string) {
    const response = await this.client.delete<T>(url);
    return response.data;
  }

  getClient(): AxiosInstance {
    return this.client;
  }

  setAuthToken(token: string | null) {
    if (token) {
      this.client.defaults.headers.common['Authorization'] = `Bearer ${token}`;
    } else {
      delete this.client.defaults.headers.common['Authorization'];
    }
  }
}

export const apiClient = new ApiClient();

export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: {
    code: string;
    message: string;
    details?: unknown;
    path: string;
    timestamp: string;
  };
  meta?: {
    timestamp: string;
    requestId?: string;
    additional?: Record<string, unknown>;
  };
}

export interface HealthCheckDetail {
  database?: string;
  status?: string;
  error?: string;
  [key: string]: unknown;
}

export interface HealthResponse {
  status: string;
  application: string;
  version: string;
  timestamp: string;
  checks: {
    database?: HealthCheckDetail;
    [key: string]: unknown;
  };
}

export interface SystemInfoResponse {
  name: string;
  version: string;
  description: string;
  timestamp: string;
  javaVersion: string;
  javaVendor: string;
  osName: string;
  osVersion: string;
  osArch: string;
}

export const healthApi = {
  check: () => apiClient.get<ApiResponse<HealthResponse>>('/health'),
  liveness: () => apiClient.get<ApiResponse<{ status: string; timestamp: string }>>('/health/live'),
  readiness: () => apiClient.get<ApiResponse<{ status: string; timestamp: string; checks: Record<string, unknown> }>>('/health/ready'),
};

export const systemApi = {
  info: () => apiClient.get<ApiResponse<SystemInfoResponse>>('/system/info'),
};

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: {
    id: string;
    email: string;
    displayName: string;
  };
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  displayName: string;
}

export interface RefreshResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface MeResponse {
  id: string;
  email: string;
  displayName: string;
  status: string;
}

export interface SessionInfo {
  id: string;
  deviceLabel: string | null;
  userAgent: string | null;
  createdAt: string;
  lastUsedAt: string | null;
  expiresAt: string;
  current: boolean;
}

export interface OrganizationResponse {
  id: string;
  name: string;
  slug: string;
  status: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  currentUserMembership: {
    role: string;
    status: string;
  };
}

export interface MemberResponse {
  id: string;
  email: string;
  displayName: string;
  role: string;
  status: string;
  joinedAt: string;
}

export interface MembershipListResponse {
  members: MemberResponse[];
  total: number;
}

export interface CreateOrganizationRequest {
  name: string;
  slug: string;
}

export interface UpdateOrganizationRequest {
  name?: string;
  status?: 'ACTIVE' | 'SUSPENDED';
}

export interface AddMemberRequest {
  email: string;
  role: 'ADMIN' | 'MEMBER' | 'VIEWER';
}

export interface UpdateMemberRequest {
  role?: 'OWNER' | 'ADMIN' | 'MEMBER' | 'VIEWER';
  status?: 'ACTIVE' | 'SUSPENDED';
}

export const authApi = {
  login: (data: LoginRequest) => apiClient.post<ApiResponse<AuthResponse>>('/auth/login', data),
  register: (data: RegisterRequest) => apiClient.post<ApiResponse<AuthResponse>>('/auth/register', data),
  refresh: () => apiClient.post<ApiResponse<RefreshResponse>>('/auth/refresh'),
  logout: () => apiClient.post<ApiResponse<void>>('/auth/logout'),
  me: () => apiClient.get<ApiResponse<MeResponse>>('/auth/me'),
  sessions: () => apiClient.get<ApiResponse<SessionInfo[]>>('/auth/sessions'),
  revokeSession: (sessionId: string) => apiClient.delete<ApiResponse<void>>(`/auth/sessions/${sessionId}`),
  revokeAllSessions: () => apiClient.delete<ApiResponse<void>>('/auth/sessions'),
};

export const organizationApi = {
  create: (data: CreateOrganizationRequest) => apiClient.post<ApiResponse<OrganizationResponse>>('/organizations', data),
  list: () => apiClient.get<ApiResponse<OrganizationResponse[]>>('/organizations'),
  get: (organizationId: string) => apiClient.get<ApiResponse<OrganizationResponse>>(`/organizations/${organizationId}`),
  update: (organizationId: string, data: UpdateOrganizationRequest) => apiClient.patch<ApiResponse<OrganizationResponse>>(`/organizations/${organizationId}`, data),
  delete: (organizationId: string) => apiClient.delete<ApiResponse<void>>(`/organizations/${organizationId}`),
  getMembers: (organizationId: string) => apiClient.get<ApiResponse<MembershipListResponse>>(`/organizations/${organizationId}/members`),
  addMember: (organizationId: string, data: AddMemberRequest) => apiClient.post<ApiResponse<MemberResponse>>(`/organizations/${organizationId}/members`, data),
  updateMember: (organizationId: string, memberId: string, data: UpdateMemberRequest) => apiClient.patch<ApiResponse<MemberResponse>>(`/organizations/${organizationId}/members/${memberId}`, data),
  removeMember: (organizationId: string, memberId: string) => apiClient.delete<ApiResponse<void>>(`/organizations/${organizationId}/members/${memberId}`),
};

export interface IndexResult {
  documentId: string;
  chunks: number;
}

export interface SearchHit {
  documentId: string;
  filename: string;
  chunkIndex: number;
  excerpt: string;
  score: number;
}

export const searchApi = {
  index: (organizationId: string, documentId: string) => apiClient.post<ApiResponse<IndexResult>>(`/organizations/${organizationId}/documents/${documentId}/index`),
  search: (organizationId: string, q: string, limit = 10, mode: 'keyword' | 'semantic' | 'hybrid' = 'keyword') =>
    apiClient.get<ApiResponse<SearchHit[]>>(`/organizations/${organizationId}/${mode === 'keyword' ? 'search' : mode === 'semantic' ? 'semantic-search' : 'hybrid-search'}`, { q, limit }),
  embed: (organizationId: string, documentId: string) =>
    apiClient.post<ApiResponse<IndexResult>>(`/organizations/${organizationId}/documents/${documentId}/embed`),};

// Stage 4C — citations are sourced from authorized document chunks on the backend.
export interface RagCitation {
  number: number;
  documentId: string;
  filename: string;
  chunkIndex: number;
  excerpt: string;
}
export interface RagAnswer {
  answer: string;
  grounded: boolean;
  citations: RagCitation[];
}
export const ragApi = {
  ask: (organizationId: string, question: string) =>
    apiClient.post<ApiResponse<RagAnswer>>(`/organizations/${organizationId}/ai/ask`, { question }, 180000),
};

// Stage 5 — organization-scoped conversation history
export type AiConversation = { id: string; title: string; createdAt: string; updatedAt: string };
export type AiMessage = { id: string; role: 'USER' | 'ASSISTANT'; content: string; grounded: boolean | null; citations: RagCitation[]; createdAt: string };
export const conversationApi = {
  list: (org: string) => apiClient.get<ApiResponse<AiConversation[]>>(`/organizations/${org}/ai/conversations`),
  create: (org: string, title: string) => apiClient.post<ApiResponse<AiConversation>>(`/organizations/${org}/ai/conversations`, { title }),
  rename: (org: string, id: string, title: string) => apiClient.patch<ApiResponse<string>>(`/organizations/${org}/ai/conversations/${id}`, { title }),
  remove: (org: string, id: string) => apiClient.delete<ApiResponse<string>>(`/organizations/${org}/ai/conversations/${id}`),
  messages: (org: string, id: string) => apiClient.get<ApiResponse<AiMessage[]>>(`/organizations/${org}/ai/conversations/${id}/messages`),
  ask: (org: string, id: string, question: string) => apiClient.post<ApiResponse<RagAnswer>>(`/organizations/${org}/ai/conversations/${id}/ask`, { question }, 180000),
};
