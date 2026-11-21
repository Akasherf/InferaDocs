import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { workspaceApi, type WorkspacePreferences, type RetentionPreview } from '@/services/workspace';

export function TenantSettings() {
  const { currentOrganization: org } = useAuth();
  const [prefs, setPrefs] = useState<WorkspacePreferences|null>(null);
  const [preview,setPreview] = useState<RetentionPreview|null>(null);
  const [loading,setLoading] = useState(false);
  const [error,setError] = useState('');
  const [message,setMessage] = useState('');
  const admin = org?.currentUserMembership.role === 'OWNER' || org?.currentUserMembership.role === 'ADMIN';
  useEffect(() => { setPrefs(null); setError(''); setPreview(null);
    if (!org) return;
    void workspaceApi.get(org.id).then(r => { if(r.data) setPrefs(r.data); else setError(r.error?.message || 'Unable to load preferences'); })
      .catch((e:unknown) => setError(e instanceof Error ? e.message : 'Unable to load preferences'));
    if (admin) void workspaceApi.retentionPreview(org.id).then(r=>setPreview(r.data ?? null)).catch(()=>{});
  },[org?.id,admin]);
  const save = async (e:React.FormEvent) => {e.preventDefault(); if(!org || !prefs || !admin)return;
    setLoading(true);setError('');setMessage('');
    try { const r=await workspaceApi.save(org.id,prefs); if(!r.data)throw new Error(r.error?.message || 'Unable to save');
      setPrefs(r.data);setMessage('Workspace preferences saved.');
      const p=await workspaceApi.retentionPreview(org.id);setPreview(p.data ?? null);
    }catch(e){setError(e instanceof Error?e.message:'Unable to save');} finally{setLoading(false);}
  };
  const apply = async () => { if (!org || !admin || !preview?.eligibleDocuments) return;
    const accepted=window.confirm(`Soft-delete ${preview.eligibleDocuments} documents older than ${preview.retentionDays} days? They will disappear from search, but files in object storage are NOT physically purged.`);
    if (!accepted) return;
    setLoading(true);setError('');setMessage('');
    try { const r=await workspaceApi.applyRetention(org.id);if(!r.data)throw new Error(r.error?.message || 'Retention action failed');
      setMessage(`${r.data.softDeletedDocuments} documents soft-deleted. Object storage was not purged.`);
      const p=await workspaceApi.retentionPreview(org.id);setPreview(p.data ?? null);
    }catch(e){setError(e instanceof Error?e.message:'Retention action failed');}finally{setLoading(false);}
  };
  return <div className="max-w-4xl mx-auto space-y-6 pb-12">
    <div><h1 className="text-2xl font-bold text-white">Tenant Configuration</h1><p className="text-dark-400">Workspace-scoped upload limits and document retention.</p></div>
    {!org ? <div className="card p-5">Choose a workspace first.</div> : <div className="text-sm text-dark-400">Workspace: <strong>{org.name}</strong> · Your role: {org.currentUserMembership.role} · <Link className="text-primary-400" to="/settings">Account settings</Link></div>}
    {error && <div role="alert" className="card p-3 text-red-400">{error}</div>}
    {message && <div role="status" className="card p-3 text-green-400">{message}</div>}
    {prefs && <form className="card p-6 space-y-5" onSubmit={save}>
      <div><label className="block mb-2 font-medium">Maximum upload size (MB)</label><input aria-label="Maximum upload size" type="number" min={1} max={20} className="w-full bg-dark-800 border border-dark-600 rounded-lg p-2" disabled={!admin || loading} value={prefs.maxUploadMb} onChange={e=>setPrefs({...prefs,maxUploadMb:Number(e.target.value)})}/><p className="text-xs text-dark-400 mt-1">Enforced for new uploads; existing files are unchanged. System maximum is 20 MB.</p></div>
      <div><label className="block mb-2 font-medium">Document retention (days)</label><input aria-label="Retention days" type="number" min={0} max={3650} className="w-full bg-dark-800 border border-dark-600 rounded-lg p-2" disabled={!admin || loading} value={prefs.retentionDays} onChange={e=>setPrefs({...prefs,retentionDays:Number(e.target.value)})}/><p className="text-xs text-dark-400 mt-1">0 = unlimited. Changes do not delete any document automatically.</p></div>
      <button disabled={!admin || loading} type="submit" className="btn-primary">{loading?'Saving…':'Save tenant preferences'}</button>
      {!admin && <p className="text-dark-400">Only owners and admins can edit workspace preferences.</p>}
    </form>}
    {admin && <section className="card p-6 space-y-3"><h2 className="text-lg font-semibold">Retention review and manual cleanup</h2>
      <p className="text-sm text-dark-300">Eligible documents: <strong>{preview?.eligibleDocuments ?? '—'}</strong> {preview?.retentionDays ? `(older than ${preview.retentionDays} days)` : '(retention disabled)'}</p>
      <p className="text-sm text-amber-300">Cleanup is a manual soft-delete. Indexed content becomes unavailable through active search, but raw objects remain in MinIO until separately purged. Do not treat this as full regulatory erasure.</p>
      <button className="btn-primary" type="button" disabled={loading || !preview?.eligibleDocuments} onClick={apply}>Apply retention to eligible documents</button>
    </section>}
  </div>;
}
