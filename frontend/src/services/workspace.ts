import { apiClient, type ApiResponse } from '@/services/api';

export interface WorkspacePreferences {
  retentionDays: number;
  maxUploadMb: number;
  aiModel: string;
  aiTemperature: number;
  aiTopK: number;
  aiRerank: boolean;
}
export interface AdminCounters {
  documents: number; chunks: number; embeddedChunks: number;
  conversations: number; messages: number; activeMembers: number;
  queuedJobs: number; runningJobs: number; failedJobs: number; completedJobs: number;
}
export interface RecentEvent { eventType: string; outcome: string; createdAt: string }
export interface Overview { counters: AdminCounters; recentEvents: RecentEvent[] }
export interface RetentionPreview { retentionDays: number; eligibleDocuments: number; action: string }
export interface RetentionOutcome { softDeletedDocuments: number; note: string }
const root = (orgId: string) => `/organizations/${encodeURIComponent(orgId)}`;
export const workspaceApi = {
  models: (id: string) => apiClient.get<ApiResponse<{models:string[];available:boolean}>>(`${root(id)}/ai/models`),
  get: (id: string) => apiClient.get<ApiResponse<WorkspacePreferences>>(`${root(id)}/preferences`),
  save: (id: string, data: WorkspacePreferences) => apiClient.put<ApiResponse<WorkspacePreferences>>(`${root(id)}/preferences`,data),
  overview: (id: string) => apiClient.get<ApiResponse<Overview>>(`${root(id)}/admin/overview`),
  retentionPreview: (id: string) => apiClient.get<ApiResponse<RetentionPreview>>(`${root(id)}/admin/retention/preview`),
  applyRetention: (id: string) => apiClient.post<ApiResponse<RetentionOutcome>>(`${root(id)}/admin/retention/apply`),
};
