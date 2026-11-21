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