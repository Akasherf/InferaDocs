import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import {
  FileText,
  Search,
  Bot,
  Settings,
  Zap,
  CheckCircle,
  XCircle,
  AlertCircle,
  Loader2,
  Building2,
  Sparkles,
  ArrowRight,
} from 'lucide-react';
import { healthApi, apiClient, type HealthResponse } from '@/services/api';
import { workspaceApi } from '@/services/workspace';
import { useAuth } from '@/context/AuthContext';

interface FeatureCardProps {
  icon: React.ReactNode;
  title: string;
  description: string;
  status: 'active' | 'planned' | 'in-progress';
  href?: string;
}

function FeatureCard({ icon, title, description, status, href }: FeatureCardProps) {
  const statusConfig = {
    active: { label: 'Active', className: 'bg-green-500/20 text-green-400 border border-green-500/30' },
    planned: { label: 'Planned', className: 'bg-yellow-500/20 text-yellow-400 border border-yellow-500/30' },
    'in-progress': { label: 'In Progress', className: 'bg-blue-500/20 text-blue-400 border border-blue-500/30' },
  };
  const config = statusConfig[status];

  const content = (
    <div className="card-hover p-6 group">
      <div className="flex items-start gap-4">
        <div className="flex-shrink-0 w-12 h-12 rounded-xl bg-primary-600/20 flex items-center justify-center text-primary-400 group-hover:bg-primary-600/30 transition-colors">
          {icon}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-2">
            <h3 className="font-semibold text-dark-100">{title}</h3>
            <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${config.className}`}>
              {config.label}
            </span>
          </div>
          <p className="text-dark-400 text-sm">{description}</p>
        </div>
      </div>
    </div>
  );

  if (href) {
    return <Link to={href}>{content}</Link>;
  }
  return content;
}

function StatusIndicator({ label, status, description }: { label: string; status: 'healthy' | 'unhealthy' | 'checking'; description?: string }) {
  const icons = {
    healthy: <CheckCircle className="w-5 h-5 text-green-500" />,
    unhealthy: <XCircle className="w-5 h-5 text-red-500" />,
    checking: <Loader2 className="w-5 h-5 text-yellow-500 animate-spin" />,
  };

  const labels = {
    healthy: 'Healthy',
    unhealthy: 'Unhealthy',
    checking: 'Checking...',
  };

  return (
    <div className="card p-4">
      <div className="flex items-center gap-3">
        <div className="flex-shrink-0">{icons[status]}</div>
        <div>
          <p className="font-medium text-dark-100">{label}</p>
          <p className="text-sm text-dark-400 capitalize">{labels[status]}</p>
          {description && <p className="text-xs text-dark-500 mt-1">{description}</p>}
        </div>
      </div>
    </div>
  );
}

interface MetricCardProps {
  icon: React.ReactNode;
  label: string;
  value: string | number;
  subtitle?: string;
  color: string;
  href?: string;
}

function MetricCard({ icon, label, value, subtitle, color, href }: MetricCardProps) {
  const content = (
    <div className="card-hover p-5 group">
      <div className="flex items-start gap-3">
        <div className={`flex-shrink-0 w-10 h-10 rounded-lg flex items-center justify-center text-white ${color}`}>
          {icon}
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-sm text-dark-400">{label}</p>
          <p className="text-2xl font-bold text-dark-50 group-hover:text-primary-400 transition-colors">{value}</p>
          {subtitle && <p className="text-xs text-dark-500 mt-1">{subtitle}</p>}
        </div>
      </div>
    </div>
  );

  if (href) {
    return <Link to={href}>{content}</Link>;
  }
  return content;
}

export function Dashboard() {
  const { organizations, currentOrganization } = useAuth();
  const isWorkspaceAdmin = currentOrganization?.currentUserMembership?.role === 'OWNER'
    || currentOrganization?.currentUserMembership?.role === 'ADMIN';
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  // Only show measured data. The documents endpoint returns an unwrapped array;
  // admin overview provides organization-scoped persisted chunk/conversation counts.
  const [metrics, setMetrics] = useState<{
    docCount: number | null;
    chunkCount: number | null;
    conversationCount: number | null;
    loadingMetrics: boolean;
  }>({ docCount: null, chunkCount: null, conversationCount: null, loadingMetrics: false });

  useEffect(() => {
    let cancelled = false;
    const orgId = currentOrganization?.id;
    setMetrics({ docCount: null, chunkCount: null, conversationCount: null, loadingMetrics: Boolean(orgId) });
    if (!orgId) return;

    const loadMetrics = async () => {
      const [documentsResult, overviewResult] = await Promise.allSettled([
        apiClient.get<{ id: string }[]>(`/organizations/${encodeURIComponent(orgId)}/documents`),
        isWorkspaceAdmin ? workspaceApi.overview(orgId) : Promise.resolve(null),
      ]);
      if (cancelled) return;

      const docCount = documentsResult.status === 'fulfilled' && Array.isArray(documentsResult.value)
        ? documentsResult.value.length : null;
      const counters = overviewResult.status === 'fulfilled' && overviewResult.value?.success
        ? overviewResult.value.data?.counters : undefined;
      setMetrics({
        docCount: counters?.documents ?? docCount,
        chunkCount: counters?.chunks ?? null,
        conversationCount: counters?.conversations ?? null,
        loadingMetrics: false,
      });
    };
    void loadMetrics();
    return () => { cancelled = true; };
  }, [currentOrganization?.id, isWorkspaceAdmin]);

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        setError(null);
        const healthRes = await healthApi.check();
        if (healthRes.success) {
          setHealth(healthRes.data || null);
        } else {
          setHealth(null);
          setError('Unable to load service health.');
        }
      } catch {
        setHealth(null);
        setError('Unable to connect to the backend health API.');
      } finally {
        setLoading(false);
      }
    };

    fetchData();
    const interval = setInterval(fetchData, 30000);
    return () => clearInterval(interval);
  }, []);

  const features = [
    {
      icon: <FileText className="w-6 h-6" />,
      title: 'Document Management',
      description: 'Upload, list, download, and delete PDF, DOCX, and TXT documents (up to 20 MB).',
      status: 'active' as const,
      href: '/documents',
    },
    {
      icon: <Search className="w-6 h-6" />,
      title: 'Full-Text Search',
      description: 'PostgreSQL tsvector search with relevance ranking (ts_rank_cd), web search syntax, and excerpt highlighting.',
      status: 'active' as const,
      href: '/search',
    },
    {
      icon: <Building2 className="w-6 h-6" />,
      title: 'Organization Settings',
      description: 'Manage members, roles (OWNER/ADMIN/MEMBER/VIEWER), and organization status.',
      status: 'active' as const,
      href: '/organizations',
    },
    {
      icon: <Sparkles className="w-6 h-6" />,
      title: 'Hybrid Search (Vector + Keyword)',
      description: 'Combine pgvector embeddings with full-text search for semantic retrieval.',
      status: 'active' as const,
      href: '/search',
    },
    {
      icon: <Bot className="w-6 h-6" />,
      title: 'AI-Powered RAG',
      description: 'Ask document-grounded questions with citations and persistent conversation history.',
      status: 'active' as const,
      href: '/ai',
    },
    {
      icon: <Zap className="w-6 h-6" />,
      title: 'Asynchronous Processing Pipeline',
      description: 'Asynchronous document processing with status polling and retries.',
      status: 'active' as const,
      href: '/documents',
    },
    {
      icon: <Settings className="w-6 h-6" />,
      title: 'Tenant Configuration',
      description: 'Per-workspace upload limits and manually applied retention policies.',
      status: 'active' as const,
      href: '/tenant-settings',
    },
    {
      icon: <Bot className="w-6 h-6" />,
      title: 'AI Settings',
      description: 'Workspace model selection and retrieval preferences.',
      status: 'active' as const,
      href: '/ai-settings',
    },
    ...(isWorkspaceAdmin ? [{
      icon: <Building2 className="w-6 h-6" />,
      title: 'Administration & Monitoring',
      description: 'Organization-scoped operational counters and audit events.',
      status: 'active' as const,
      href: '/admin',
    }] : []),
  ];

  const activeFeatures = features.filter(f => f.status === 'active').length;

  return (
    <div className="space-y-6 animate-fade-in">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-dark-50">Dashboard</h1>
          <p className="text-dark-400 mt-1">Welcome to InferaDocs - Enterprise Document Intelligence Platform</p>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={() => window.location.reload()}
            disabled={loading}
            className="btn-secondary flex items-center gap-2"
          >
            <Loader2 className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
        </div>
      </div>

      {/* Error Banner */}
      {error && (
        <div className="card border-red-500/30 bg-red-500/10 p-4 flex items-center gap-3">
          <AlertCircle className="w-5 h-5 text-red-400 flex-shrink-0" />
          <p className="text-red-300 text-sm">{error}</p>
          <button
            onClick={() => window.location.reload()}
            className="ml-auto btn-secondary text-xs"
          >
            Retry
          </button>
        </div>
      )}

      {/* Organization Metrics */}
      <section aria-labelledby="metrics-heading">
        <h2 id="metrics-heading" className="text-lg font-semibold text-dark-100 mb-4">Organization Overview</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <MetricCard
            icon={<Building2 className="w-5 h-5" />}
            label="Organizations"
            value={organizations.length}
            subtitle="Accessible workspaces"
            color="bg-primary-600"
            href="/organizations"
          />
          <MetricCard
            icon={<FileText className="w-5 h-5" />}
            label="Documents"
            value={metrics.loadingMetrics ? '…' : (metrics.docCount ?? '—')}
            subtitle={!currentOrganization ? 'Select a workspace' : metrics.docCount === null ? 'Count unavailable' : 'In current workspace'}
            color="bg-blue-600"
            href="/documents"
          />
          <MetricCard
            icon={<Sparkles className="w-5 h-5" />}
            label="Indexed Chunks"
            value={metrics.loadingMetrics ? '…' : (metrics.chunkCount ?? '—')}
            subtitle={isWorkspaceAdmin ? (metrics.chunkCount === null ? 'Count unavailable' : 'Indexed in this workspace') : 'Admin-only metric'}
            color="bg-purple-600"
            href="/search"
          />
          <MetricCard
            icon={<Search className="w-5 h-5" />}
            label="AI Conversations"
            value={metrics.loadingMetrics ? '…' : (metrics.conversationCount ?? '—')}
            subtitle={isWorkspaceAdmin ? (metrics.conversationCount === null ? 'Count unavailable' : 'Saved in this workspace') : 'Admin-only metric'}
            color="bg-green-600"
            href="/ai"
          />
        </div>
      </section>

      {/* System Status */}
      <section aria-labelledby="system-status-heading">
        <h2 id="system-status-heading" className="text-lg font-semibold text-dark-100 mb-4">System Status</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <StatusIndicator
            label="Backend API"
            status={health?.status === 'UP' ? 'healthy' : loading ? 'checking' : 'unhealthy'}
            description={health ? `v${health.version}` : undefined}
          />
          <StatusIndicator
            label="Database"
            status={health?.checks?.database?.status === 'UP' ? 'healthy' : loading ? 'checking' : 'unhealthy'}
            description="PostgreSQL + pgvector"
          />
        </div>
      </section>

      {/* Features Grid */}
      <section aria-labelledby="features-heading">
        <div className="flex items-center justify-between mb-4">
          <h2 id="features-heading" className="text-lg font-semibold text-dark-100">Platform Capabilities</h2>
          <span className="text-sm text-dark-500">{activeFeatures} available capabilities</span>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {features.map((feature) => (
            <FeatureCard key={feature.title} {...feature} />
          ))}
        </div>
      </section>

      {/* Quick Actions */}
      <section aria-labelledby="quick-actions-heading">
        <h2 id="quick-actions-heading" className="text-lg font-semibold text-dark-100 mb-4">Quick Actions</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
          <Link to="/documents" className="card-hover p-4 text-left group flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-blue-600/20 flex items-center justify-center text-blue-400 group-hover:bg-blue-600/30 transition-colors">
              <FileText className="w-5 h-5" />
            </div>
            <div>
              <p className="font-medium text-dark-100 group-hover:text-primary-400 transition-colors">Upload Document</p>
              <p className="text-xs text-dark-500">PDF, DOCX, TXT · workspace limit applies</p>
            </div>
            <ArrowRight className="w-5 h-5 text-dark-500 ml-auto group-hover:text-primary-400 transition-colors" />
          </Link>
          <Link to="/search" className="card-hover p-4 text-left group flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-purple-600/20 flex items-center justify-center text-purple-400 group-hover:bg-purple-600/30 transition-colors">
              <Search className="w-5 h-5" />
            </div>
            <div>
              <p className="font-medium text-dark-100 group-hover:text-primary-400 transition-colors">Search Documents</p>
              <p className="text-xs text-dark-500">Full-text with relevance ranking</p>
            </div>
            <ArrowRight className="w-5 h-5 text-dark-500 ml-auto group-hover:text-primary-400 transition-colors" />
          </Link>
          <Link to="/ai" className="card-hover p-4 text-left group flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-green-600/20 flex items-center justify-center text-green-400">
              <Bot className="w-5 h-5" />
            </div>
            <div>
              <p className="font-medium text-dark-100">Ask AI Assistant</p>
              <p className="text-xs text-dark-500">Cited answers and saved conversations</p>
            </div>
            <ArrowRight className="w-5 h-5 text-dark-500 ml-auto" />
          </Link>
          <Link to="/tenant-settings" className="card-hover p-4 text-left group flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-orange-600/20 flex items-center justify-center text-orange-400 group-hover:bg-orange-600/30 transition-colors">
              <Settings className="w-5 h-5" />
            </div>
            <div>
              <p className="font-medium text-dark-100 group-hover:text-primary-400 transition-colors">Configure Tenant</p>
              <p className="text-xs text-dark-500">Retention, upload limits, policies</p>
            </div>
            <ArrowRight className="w-5 h-5 text-dark-500 ml-auto group-hover:text-primary-400 transition-colors" />
          </Link>
        </div>
      </section>

    </div>
  );
}