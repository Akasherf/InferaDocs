import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Search as SearchIcon,
  Loader2,
  X,
  FileText,
  Download,
  ArrowRight,
  Info,
  AlertCircle,
  Sparkles,
  Filter,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { apiClient, searchApi, type SearchHit } from '@/services/api';

type DocumentRecord = { id: string; filename: string; contentType: string; size: number; createdAt: string; sha256: string };

export function Search() {
  const { currentOrganization } = useAuth();
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<SearchHit[]>([]);
  const [searching, setSearching] = useState(false);
  const [mode, setMode] = useState<'keyword' | 'semantic' | 'hybrid'>('keyword');
  const [error, setError] = useState('');
  const [documents, setDocuments] = useState<DocumentRecord[]>([]);
  const [loadingDocs, setLoadingDocs] = useState(false);
  const [lastQuery, setLastQuery] = useState('');
  const searchInputRef = useRef<HTMLInputElement>(null);
  const org = currentOrganization?.id;
  const base = org ? `/organizations/${org}/documents` : '';

  // Load documents for reference
  const loadDocuments = useCallback(async () => {
    if (!org) { setDocuments([]); return; }
    setLoadingDocs(true);
    try {
      setDocuments(await apiClient.get<DocumentRecord[]>(`/organizations/${org}/documents`));
    } catch {
      // Ignore error, documents list is optional for search
    } finally {
      setLoadingDocs(false);
    }
  }, [org]);

  useEffect(() => { void loadDocuments(); }, [loadDocuments]);

  // Focus search input on mount
  useEffect(() => {
    searchInputRef.current?.focus();
  }, []);

  async function handleSearch(e: React.FormEvent) {
    e.preventDefault();
    if (!org || !searchQuery.trim()) return;
    const query = searchQuery.trim();
    if (query === lastQuery) return; // Avoid duplicate searches for the same mode

    setSearching(true);
    setError('');
    setLastQuery(query);

    try {
      const result = await searchApi.search(org, query, 50, mode);
      if (result.success && result.data) {
        setSearchResults(result.data);
      } else {
        throw new Error(result.error?.message || 'Search returned an unexpected response');
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
    setLastQuery('');
    searchInputRef.current?.focus();
  }

  function handleDownload(docId: string, filename: string) {
    apiClient.getClient()
      .get(`${base}/${docId}/download`, { responseType: 'blob' })
      .then(response => {
        const url = URL.createObjectURL(response.data);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        a.click();
        URL.revokeObjectURL(url);
      })
      .catch(() => setError('Download failed.'));
  }

  const indexedDocumentIds = new Set(searchResults.map(h => h.documentId));
  const isIndexed = (docId: string) => indexedDocumentIds.has(docId);

  return <div className="p-4 md:p-8 space-y-6">
    <div>
      <h1 className="text-2xl font-bold text-white">Search</h1>
      <p className="text-gray-400 mt-1">
        {org ? 'Search across all indexed documents in your organization.' : 'Select an organization to search its documents.'}
      </p>
    </div>

    {!org ? (
      <div className="card p-6 text-gray-300">
        Select or create an organization to search its documents.
        <Link to="/organizations" className="text-primary-400 underline font-medium ml-2">Create organization</Link>
      </div>
    ) : (
      <>
        {/* Search Section */}
        <section className="space-y-4">
          <div className="flex items-center gap-3">
            <label htmlFor="search-mode" className="text-sm text-gray-300">Search mode</label>
            <select id="search-mode" value={mode} onChange={e => { setMode(e.target.value as typeof mode); setLastQuery(''); setSearchResults([]); }}
              className="input max-w-48" aria-label="Search mode">
              <option value="keyword">Keyword</option>
              <option value="semantic">Semantic</option>
              <option value="hybrid">Hybrid</option>
            </select>
            {mode !== 'keyword' && <span className="text-xs text-gray-400">Requires Ollama embeddings for indexed documents</span>}
          </div>
          <form onSubmit={handleSearch} className="flex gap-2">
            <div className="relative flex-1 max-w-3xl">
              <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-500" size={20} />
              <input
                ref={searchInputRef}
                type="search"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search documents… (e.g., IRCTC, invoice, contract)"
                className="input pl-10 pr-10 w-full text-lg"
                disabled={searching}
                autoComplete="off"
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
            <button type="submit" className="btn-primary px-6" disabled={searching || !searchQuery.trim()}>
              {searching ? <Loader2 className="w-4 h-4 animate-spin" /> : 'Search'}
            </button>
          </form>

          {/* Search Tips */}
          <div className="flex items-center gap-2 text-xs text-gray-500">
            <Info className="w-3.5 h-3.5 flex-shrink-0" />
            <span>Use quotes for exact phrases: <code className="bg-dark-800 px-1.5 py-0.5 rounded text-gray-300">"exact phrase"</code></span>
            <span className="mx-1">•</span>
            <span>Prefix with - to exclude: <code className="bg-dark-800 px-1.5 py-0.5 rounded text-gray-300">-draft</code></span>
            <span className="mx-1">•</span>
            <span>Results ranked by relevance (ts_rank_cd)</span>
          </div>
        </section>

        {/* Error Display */}
        {error && (
          <div className="card p-4 border-red-500/30 bg-red-500/5 flex items-center gap-3 text-red-400">
            <AlertCircle className="w-5 h-5 flex-shrink-0" />
            <span>{error}</span>
            <button onClick={clearSearch} className="ml-auto text-red-400 hover:text-red-300">
              <X size={18} />
            </button>
          </div>
        )}

        {/* Results Section */}
        {searchResults.length > 0 && (
          <section className="space-y-4 animate-fade-in">
            <div className="flex items-center gap-3 flex-wrap">
              <div className="flex items-center gap-2 text-sm text-primary-400">
                <Sparkles className="w-4 h-4" />
                <span>Found <strong>{searchResults.length}</strong> match(es) for <strong>"{searchQuery}"</strong></span>
              </div>
              <button onClick={clearSearch} className="btn-ghost text-sm">
                <X className="w-4 h-4 mr-1" /> Clear
              </button>
            </div>

            <ul className="space-y-3" role="list" aria-label="Search results">
              {searchResults.map((hit, idx) => (
                <li
                  key={`${hit.documentId}-${hit.chunkIndex}-${idx}`}
                  className="card p-4 border-gray-700/50 hover:border-primary-500/30 transition-colors"
                >
                  <div className="flex items-start gap-3">
                    <FileText className="w-6 h-6 text-gray-500 flex-shrink-0 mt-0.5" />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap mb-2">
                        <Link
                          to={`/documents`}
                          className="font-medium text-white hover:text-primary-400 truncate max-w-[400px]"
                          title={hit.filename}
                        >
                          {hit.filename}
                        </Link>
                        <span className="text-xs text-gray-500 bg-gray-800 px-2 py-0.5 rounded whitespace-nowrap">
                          chunk #{hit.chunkIndex}
                        </span>
                        <span className="text-xs text-primary-400 bg-primary-500/10 px-2 py-0.5 rounded whitespace-nowrap font-mono">
                          score: {hit.score.toFixed(3)}
                        </span>
                        {isIndexed(hit.documentId) && (
                          <span className="text-xs text-green-400 bg-green-500/10 px-2 py-0.5 rounded whitespace-nowrap flex items-center gap-1">
                            <Sparkles className="w-3 h-3" /> Indexed
                          </span>
                        )}
                      </div>
                      <p className="text-gray-300 italic pl-6 border-l-2 border-gray-700 leading-relaxed">
                        {hit.excerpt}
                      </p>
                      <div className="flex items-center gap-3 mt-3 pt-3 border-t border-gray-800">
                        <button
                          onClick={() => handleDownload(hit.documentId, hit.filename)}
                          className="btn-ghost text-sm flex items-center gap-1.5"
                          title={`Download ${hit.filename}`}
                        >
                          <Download className="w-4 h-4" />
                          Download
                        </button>
                        <Link
                          to={`/documents`}
                          className="btn-ghost text-sm flex items-center gap-1.5"
                          title={`View ${hit.filename} in Documents`}
                        >
                          <ArrowRight className="w-4 h-4" />
                          View Document
                        </Link>
                      </div>
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          </section>
        )}

        {/* Empty Results State */}
        {lastQuery && !searching && searchResults.length === 0 && !error && (
          <section className="card p-8 text-center animate-fade-in">
            <SearchIcon className="w-12 h-12 text-gray-600 mx-auto mb-4" />
            <h3 className="text-lg font-medium text-gray-300 mb-2">No results for "{lastQuery}"</h3>
            <p className="text-gray-500 mb-4 max-w-md mx-auto">
              Try different keywords, check spelling, or use broader terms.
              Documents must be indexed before they appear in search results.
            </p>
            <div className="flex items-center justify-center gap-2 text-sm text-gray-500">
              <Info className="w-4 h-4" />
              <span>Go to <Link to="/documents" className="text-primary-400 underline hover:text-primary-300">Documents</Link> to index files for search.</span>
            </div>
          </section>
        )}

        {/* Initial State - No Search Performed */}
        {!lastQuery && !searching && !error && (
          <section className="card p-8 text-center">
            <SearchIcon className="w-12 h-12 text-gray-600 mx-auto mb-4" />
            <h3 className="text-lg font-medium text-gray-300 mb-2">Search your documents</h3>
            <p className="text-gray-500 mb-6 max-w-md mx-auto">
              Enter a search query above to find relevant content across all indexed documents in this organization.
            </p>
            <div className="space-y-3 max-w-md mx-auto text-left">
              <div className="flex items-center gap-3 p-3 bg-dark-800/50 rounded-lg border border-gray-700/50">
                <Sparkles className="w-5 h-5 text-primary-400 flex-shrink-0" />
                <div>
                  <p className="font-medium text-gray-200">Full-text search</p>
                  <p className="text-sm text-gray-500">PostgreSQL tsvector with relevance ranking (ts_rank_cd)</p>
                </div>
              </div>
              <div className="flex items-center gap-3 p-3 bg-dark-800/50 rounded-lg border border-gray-700/50">
                <FileText className="w-5 h-5 text-primary-400 flex-shrink-0" />
                <div>
                  <p className="font-medium text-gray-200">Supports PDF, DOCX, TXT</p>
                  <p className="text-sm text-gray-500">Text extracted and chunked for precise results</p>
                </div>
              </div>
              <div className="flex items-center gap-3 p-3 bg-dark-800/50 rounded-lg border border-gray-700/50">
                <Filter className="w-5 h-5 text-primary-400 flex-shrink-0" />
                <div>
                  <p className="font-medium text-gray-200">Web search syntax</p>
                  <p className="text-sm text-gray-500">Quotes for phrases, minus to exclude, auto-stemming</p>
                </div>
              </div>
            </div>
            {documents.length > 0 && (
              <div className="mt-6 pt-6 border-t border-gray-800">
                <p className="text-sm text-gray-500 mb-3">
                  {documents.length} document{documents.length !== 1 ? 's' : ''} in this organization:
                  {loadingDocs && <Loader2 className="w-4 h-4 animate-spin inline-block ml-2" />}
                </p>
                <div className="flex flex-wrap justify-center gap-2">
                  {documents.slice(0, 8).map(doc => (
                    <span
                      key={doc.id}
                      className="px-2 py-1 text-xs bg-dark-800 rounded border border-gray-700 text-gray-400 truncate max-w-[150px]"
                      title={doc.filename}
                    >
                      {doc.filename}
                    </span>
                  ))}
                  {documents.length > 8 && (
                    <span className="px-2 py-1 text-xs bg-primary-500/10 rounded border border-primary-500/30 text-primary-400">
                      +{documents.length - 8} more
                    </span>
                  )}
                </div>
              </div>
            )}
          </section>
        )}

        {/* Loading State */}
        {searching && (
          <section className="card p-8 text-center">
            <Loader2 className="w-8 h-8 animate-spin text-primary-400 mx-auto mb-3" />
            <p className="text-gray-400">Searching for "{searchQuery}"…</p>
          </section>
        )}
      </>
    )}
  </div>;
}