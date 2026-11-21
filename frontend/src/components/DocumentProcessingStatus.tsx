import { useCallback, useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { AlertCircle, CheckCircle2, Clock3, Loader2, RefreshCw, RotateCcw, Play } from 'lucide-react';
import { apiClient, type ApiResponse } from '@/services/api';

/** Mirrors Stage V9 ProcessingService.Job; no fabricated percentage is shown. */
export interface ProcessingJob {
  id: string;
  documentId: string;
  status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  stage: string;
  attempts: number;
  maxAttempts: number;
  chunkCount: number | null;
  lastError: string | null;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
}

type DataOrEnvelope<T> = T | ApiResponse<T>;

function unpack<T>(value: DataOrEnvelope<T>): T {
  if (value !== null && typeof value === 'object' && 'success' in value) {
    const wrapped = value as ApiResponse<T>;
    if (!wrapped.success || wrapped.data === undefined) {
      throw new Error(wrapped.error?.message || 'Processing API returned an invalid response');
    }
    return wrapped.data;
  }
  return value as T;
}

function processingError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status;
    const serverMessage = (error.response?.data as {error?: {message?: string}} | undefined)?.error?.message;
    if (status === 500) return 'Processing API HTTP 500. Check the backend exception logs and Flyway V9 migration.';
    if (status) return `Processing API HTTP ${status}: ${serverMessage || error.message}`;
  }
  return error instanceof Error ? error.message : 'Unable to load processing status';
}

function stageText(job: ProcessingJob): string {
  if (job.status === 'SUCCEEDED') return 'Completed';
  if (job.status === 'FAILED') return 'Failed';
  if (job.status === 'QUEUED' && job.stage === 'RETRY_WAIT') return 'Waiting to retry';
  if (job.status === 'QUEUED') return 'Queued';
  switch (job.stage) {
    case 'INDEXING': return 'Indexing text';
    case 'EMBEDDING': return 'Generating embeddings';
    case 'RECOVERED': return 'Recovering';
    default: return 'Processing';
  }
}

const pillStyle: Record<ProcessingJob['status'], string> = {
  QUEUED: 'bg-amber-500/10 text-amber-300 border-amber-500/30',
  RUNNING: 'bg-blue-500/10 text-blue-300 border-blue-500/30',
  SUCCEEDED: 'bg-green-500/10 text-green-300 border-green-500/30',
  FAILED: 'bg-red-500/10 text-red-300 border-red-500/30',
};

interface Props {
  organizationId: string;
  documentId: string;
  canManage: boolean;
}

/** One initial GET per listed document, then polls only while its latest job is active. */
export function DocumentProcessingStatus({ organizationId, documentId, canManage }: Props) {
  const url = `/organizations/${organizationId}/documents/${documentId}/processing`;
  const [job, setJob] = useState<ProcessingJob | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState('');
  const [working, setWorking] = useState(false);
  const mounted = useRef(true);
  const inFlight = useRef(false);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  const refresh = useCallback(async (quiet = false) => {
    if (inFlight.current) return;
    inFlight.current = true;
    try {
      const raw = await apiClient.get<DataOrEnvelope<ProcessingJob[]>>(url);
      const jobs = unpack(raw);
      if (!Array.isArray(jobs)) throw new Error('Processing endpoint did not return a job list');
      if (mounted.current) {
        setJob(jobs[0] ?? null); // backend orders newest first
        setError('');
      }
    } catch (e: unknown) {
      if (mounted.current) setError(processingError(e));
    } finally {
      inFlight.current = false;
      if (mounted.current && !quiet) setLoaded(true);
    }
  }, [url]);

  useEffect(() => { void refresh(); }, [refresh]);

  useEffect(() => {
    if (job?.status !== 'QUEUED' && job?.status !== 'RUNNING') return;
    const timer = window.setInterval(() => void refresh(true), 3000);
    return () => window.clearInterval(timer);
  }, [job?.status, refresh]);

  const action = async (kind: 'enqueue' | 'retry') => {
    if (!canManage || working) return;
    setWorking(true);
    setError('');
    try {
      const path = kind === 'retry' && job ? `${url}/${job.id}/retry` : url;
      const response = await apiClient.post<DataOrEnvelope<ProcessingJob>>(path);
      const next = unpack(response);
      if (!next || !next.id || !next.status) throw new Error('Processing endpoint returned an invalid job');
      if (mounted.current) setJob(next);
      // A subsequent refresh reconciles with any concurrent worker state changes.
      await refresh(true);
    } catch (e: unknown) {
      if (mounted.current) setError(processingError(e));
    } finally {
      if (mounted.current) setWorking(false);
    }
  };

  if (!loaded) {
    return <span className="flex items-center gap-1.5 text-xs text-gray-400"><Loader2 size={14} className="animate-spin" /> Loading status</span>;
  }

  return <div className="min-w-[170px] max-w-[260px] space-y-1.5 text-xs" aria-live="polite">
    {job ? (
      <>
        <span className={`inline-flex items-center gap-1.5 px-2 py-1 border rounded-full font-medium ${pillStyle[job.status]}`}>
          {job.status === 'RUNNING' ? <Loader2 size={13} className="animate-spin" />
            : job.status === 'SUCCEEDED' ? <CheckCircle2 size={13} />
            : job.status === 'FAILED' ? <AlertCircle size={13} />
            : <Clock3 size={13} />}
          {stageText(job)}
        </span>
        {job.status === 'RUNNING' && <p className="text-gray-400">Processing in background</p>}
        {(job.status === 'RUNNING' || job.status === 'QUEUED') &&
          <p className="text-gray-500">Attempt {job.attempts}/{job.maxAttempts}</p>}
        {job.status === 'SUCCEEDED' && job.chunkCount !== null &&
          <p className="text-green-300/80">{job.chunkCount} searchable chunk(s)</p>}
        {job.status === 'FAILED' && job.lastError &&
          <p className="text-red-300 break-words" title={job.lastError}>{job.lastError}</p>}
      </>
    ) : <p className="text-gray-400">{error ? 'Processing status unavailable' : 'No processing job'}</p>}
    {error && <p role="alert" className="text-red-400 break-words">{error}</p>}
    <div className="flex flex-wrap items-center gap-2">
      <button type="button" title="Refresh processing status" aria-label="Refresh processing status"
        onClick={() => void refresh()} className="text-gray-300 hover:text-white disabled:opacity-50" disabled={working}>
        <RefreshCw size={15} />
      </button>
      {canManage && (!job || job.status === 'FAILED') && (
        <button type="button" disabled={working} onClick={() => void action(job ? 'retry' : 'enqueue')}
          className="inline-flex items-center gap-1 text-primary-400 hover:text-primary-300 disabled:opacity-50">
          {working ? <Loader2 size={14} className="animate-spin" />
            : job ? <RotateCcw size={14} /> : <Play size={14} />}
          {job ? 'Retry' : 'Process'}
        </button>
      )}
      {canManage && job?.status === 'SUCCEEDED' && (
        <button type="button" disabled={working} onClick={() => void action('enqueue')}
          className="inline-flex items-center gap-1 text-gray-400 hover:text-gray-200 disabled:opacity-50">
          <RotateCcw size={14} /> Reprocess
        </button>
      )}
    </div>
  </div>;
}
