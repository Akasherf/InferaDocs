import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { FileText, Upload, Trash2, Download, Search, Loader2, Sparkles, X } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { apiClient, searchApi, type SearchHit } from '@/services/api';
import { DocumentProcessingStatus } from '@/components/DocumentProcessingStatus';

type DocumentRecord = { id: string; filename: string; contentType: string; size: number; createdAt: string; sha256: string };

export function Documents() {
  const { currentOrganization } = useAuth();
  const [documents, setDocuments] = useState<DocumentRecord[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<SearchHit[]>([]);
  const [searching, setSearching] = useState(false);
  const [indexingDocId, setIndexingDocId] = useState<string | null>(null);
  const [indexSuccess, setIndexSuccess] = useState('');
  const org = currentOrganization?.id;
  const canManageProcessing = currentOrganization?.currentUserMembership?.role !== 'VIEWER';
  const base = org ? `/organizations/${org}/documents` : '';

  const load = useCallback(async () => {
    if (!org) { setDocuments([]); return; }
    setLoading(true); setError('');
    try {
      setDocuments(await apiClient.get<DocumentRecord[]>(`/organizations/${org}/documents`));
    } catch {
      setError('Could not load documents. Check your permissions and backend connection.');
    } finally {
      setLoading(false);
    }
  }, [org]);

  useEffect(() => { void load(); }, [load]);

  async function upload(file: File) {
    if (!org) return;
    if (file.size === 0 || file.size > 20 * 1024 * 1024) { setError('File must be between 1 byte and 20 MB.'); return; }
    setBusy(true); setError('');
    try {
      const form = new FormData(); form.append('file', file);
      await apiClient.getClient().post(base, form, { headers: { 'Content-Type': undefined } });
      await load();
    } catch {
      setError('Upload failed. Accepted formats: PDF, DOCX and TXT (up to 20 MB).');
    } finally {
      setBusy(false);
    }
  }

  async function download(doc: DocumentRecord) {
    try {
      const response = await apiClient.getClient().get(`${base}/${doc.id}/download`, { responseType: 'blob' });
      const url = URL.createObjectURL(response.data);
      const a = document.createElement('a'); a.href = url; a.download = doc.filename; a.click();
      URL.revokeObjectURL(url);
    } catch {
      setError('Download failed.');
    }
  }

  async function remove(doc: DocumentRecord) {
    if (!window.confirm(`Delete ${doc.filename}?`)) return;
    setBusy(true);
    try {
      await apiClient.delete(`${base}/${doc.id}`);
      await load();
      setSearchResults([]);
    } catch {
      setError('Unable to delete document.');
    } finally {
      setBusy(false);
    }
  }

  async function indexDocument(docId: string) {
    if (!org) return;
    setIndexingDocId(docId);
    setError('');
    setIndexSuccess('');
    try {
      const result = await searchApi.index(org, docId);
      if (!result.success || !result.data) {
        throw new Error(result.error?.message || 'Indexing returned an unexpected response');
      }
      setIndexSuccess(`Indexed ${result.data.chunks} text chunk(s). You can now search this document.`);
      await load();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Indexing failed';
      setError(message);
    } finally {
      setIndexingDocId(null);
    }
  }

  async function embedDocument(docId: string) {
    if (!org) return;
    setIndexingDocId(docId);
    setError(''); setIndexSuccess('');
    try {
      const result = await searchApi.embed(org, docId);
      if (!result.success || !result.data) throw new Error(result.error?.message || 'Embedding failed');
      setIndexSuccess(`Generated embeddings for ${result.data.chunks} chunk(s). Semantic and hybrid search are ready.`);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Embedding failed');
    } finally { setIndexingDocId(null); }
  }

  async function handleSearch(e: React.FormEvent) {
    e.preventDefault();
    if (!org || !searchQuery.trim()) return;
    setSearching(true);
    setError('');
    try {
      const result = await searchApi.search(org, searchQuery.trim(), 20);
      if (result.success && result.data) {
        setSearchResults(result.data);
      }
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Search failed';
      setError(message);
      setSearchResults([]);
    } finally {
      setSearching(false);
    }
  }

  function clearSearch() {
    setSearchQuery('');
    setSearchResults([]);
    setError('');
  }

  return <div className="p-4 md:p-8 space-y-6">
    <div>
      <h1 className="text-2xl font-bold text-white">Documents</h1>
      <p className="text-gray-400 mt-1">Secure files for your current organization.</p>
    </div>

    {!org ? (
      <div className="card p-6 text-gray-300">
        Select or create an organization to manage its documents.
        <Link to="/organizations" className="text-primary-400 underline font-medium">Create organization</Link>
      </div>
    ) : (
      <>
        {/* Search Section */}
        <section className="space-y-4">
          <form onSubmit={handleSearch} className="flex gap-2">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-500" size={20} />
              <input
                type="search"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search documents…"
                className="input pl-10 pr-10 w-full"
                disabled={searching}
              />
              {searchQuery && (
                <button
                  type="button"
                  onClick={clearSearch}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-500 hover:text-gray-200"
                  aria-label="Clear search"
                >
                  <X size={18} />
                </button>
              )}
            </div>
            <button type="submit" className="btn-primary" disabled={searching || !searchQuery.trim()}>
              {searching ? <Loader2 className="w-4 h-4 animate-spin" /> : 'Search'}
            </button>
          </form>

          {searchResults.length > 0 && (
            <div className="card p-4 border-primary-500/30 bg-primary-500/5 animate-fade-in">
              <div className="flex items-center gap-2 text-sm text-primary-400 mb-3">
                <Sparkles className="w-4 h-4" />
                <span>Found {searchResults.length} match(es) for "{searchQuery}"</span>
                <button onClick={clearSearch} className="ml-auto text-gray-400 hover:text-gray-200">
                  <X size={16} />
                </button>
              </div>
              <ul className="space-y-2">
                {searchResults.map((hit, idx) => (
                  <li key={`${hit.documentId}-${hit.chunkIndex}-${idx}`} className="text-sm text-gray-300 bg-dark-800/50 p-3 rounded-lg">
                    <div className="flex items-center gap-2 mb-1">
                      <FileText className="w-4 h-4 text-gray-500" />
                      <span className="font-medium text-white">{hit.filename}</span>
                      <span className="text-xs text-gray-500 bg-gray-800 px-1.5 py-0.5 rounded">
                        chunk #{hit.chunkIndex}
                      </span>
                      <span className="text-xs text-primary-400 bg-primary-500/10 px-1.5 py-0.5 rounded">
                        score: {hit.score.toFixed(3)}
                      </span>
                    </div>
                    <p className="text-gray-400 italic pl-6 border-l-2 border-gray-700">
                      {hit.excerpt}
                    </p>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </section>

        {/* Upload Section */}
        <label className="card p-6 border-2 border-dashed border-gray-700 hover:border-blue-500 cursor-pointer flex flex-col items-center gap-3 text-gray-200">
          <Upload className="h-8 w-8" />
          <span>{busy ? 'Uploading…' : 'Choose a PDF, DOCX or TXT file (20 MB maximum)'}</span>
          <input
            disabled={busy}
            className="sr-only"
            type="file"
            accept=".pdf,.docx,.txt"
            onChange={e => { const f = e.target.files?.[0]; if (f) void upload(f); e.target.value = ''; }}
          />
        </label>

        {error && <p role="alert" className="text-red-400">{error}</p>}
        {indexSuccess && <p role="status" className="text-green-400">{indexSuccess}</p>}

        {/* Documents Table */}
        {loading ? (
          <p className="text-gray-400">Loading documents…</p>
        ) : documents.length === 0 ? (
          <div className="card p-8 text-center text-gray-400">No documents uploaded yet.</div>
        ) : (
          <div className="card overflow-x-auto">
            <table className="w-full text-sm text-left text-gray-200">
              <thead className="text-gray-400 border-b border-gray-700">
                <tr>
                  <th className="p-4">File</th>
                  <th className="p-4">Size</th>
                  <th className="p-4">Uploaded</th>
                  <th className="p-4">Processing</th>
                  <th className="p-4">Actions</th>
                </tr>
              </thead>
              <tbody>
                {documents.map(d => (
                  <tr key={d.id} className="border-b border-gray-800">
                    <td className="p-4">
                      <span className="flex items-center gap-2">
                        <FileText size={18} />
                        {d.filename}
                      </span>
                    </td>
                    <td className="p-4">{(d.size / 1024).toFixed(1)} KB</td>
                    <td className="p-4">{d.createdAt ? new Date(d.createdAt).toLocaleString() : '—'}</td>
                    <td className="p-4">
                      <DocumentProcessingStatus key={`${org}:${d.id}`} organizationId={org} documentId={d.id} canManage={canManageProcessing} />
                    </td>
                    <td className="p-4">
                      <div className="flex gap-3">
                        <button
                          title="Download"
                          aria-label={`Download ${d.filename}`}
                          onClick={() => void download(d)}
                        >
                          <Download size={18} />
                        </button>
                        <button
                          disabled={busy || indexingDocId === d.id}
                          title="Index / Reindex for Search"
                          aria-label={`Index ${d.filename} for search`}
                          onClick={() => void indexDocument(d.id)}
                          className="text-primary-400 hover:text-primary-300 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                          {indexingDocId === d.id ? (
                            <Loader2 className="w-5 h-5 animate-spin" />
                          ) : (
                            <Sparkles className="w-5 h-5" />
                          )}
                        </button>
                        <button
                          disabled={busy || indexingDocId === d.id}
                          title="Generate semantic embeddings (index text first)"
                          aria-label={`Generate embeddings for ${d.filename}`}
                          onClick={() => void embedDocument(d.id)}
                          className="text-violet-400 hover:text-violet-300 disabled:opacity-50"
                        >
                          <span className="font-bold text-xs">AI</span>
                        </button>
                        <button
                          disabled={busy}
                          title="Delete"
                          aria-label={`Delete ${d.filename}`}
                          onClick={() => void remove(d)}
                        >
                          <Trash2 size={18} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </>
    )}
  </div>;
}