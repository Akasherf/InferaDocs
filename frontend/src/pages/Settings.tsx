import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { organizationApi } from '@/services/api';

export function Settings() {
  const { currentOrganization, setCurrentOrganization, fetchOrganizations, sessions, fetchSessions, revokeSession, revokeAllSessions } = useAuth();
  const [name, setName] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busySession, setBusySession] = useState('');
  const org = currentOrganization;
  const canEdit = org?.currentUserMembership.role === 'OWNER' || org?.currentUserMembership.role === 'ADMIN';
  useEffect(() => { setName(org?.name ?? ''); setError(''); setNotice(''); }, [org?.id, org?.name]);
  useEffect(() => { void fetchSessions().catch(() => setError('Unable to load sessions')); }, [fetchSessions]);

  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!org || !canEdit || !name.trim() || saving) return;
    setError(''); setNotice(''); setSaving(true);
    try {
      const result = await organizationApi.update(org.id, { name: name.trim() });
      if (!result.success || !result.data) throw new Error(result.error?.message || 'Could not update workspace');
      setCurrentOrganization(result.data);
      await fetchOrganizations();
      setNotice('Workspace name saved.');
    } catch (e) { setError(e instanceof Error ? e.message : 'Could not save changes'); }
    finally { setSaving(false); }
  };
  const revoke = async (id: string) => {
    setError(''); setBusySession(id);
    try { await revokeSession(id); setNotice('Session revoked.'); }
    catch (e) { setError(e instanceof Error ? e.message : 'Could not revoke session'); }
    finally { setBusySession(''); }
  };
  const revokeAll = async () => {
    if (!window.confirm('Revoke all sessions? You may need to sign in again.')) return;
    setError(''); setBusySession('all');
    try { await revokeAllSessions(); setNotice('Sessions revoked.'); }
    catch (e) { setError(e instanceof Error ? e.message : 'Could not revoke sessions'); }
    finally { setBusySession(''); }
  };
  return (
    <div className="max-w-5xl mx-auto space-y-6 pb-12">
      <div><h1 className="text-2xl font-bold text-white">Settings & Security</h1><p className="text-dark-400 mt-1">Manage workspace details, membership and account sessions.</p></div>
      {error && <div role="alert" className="card p-3 border border-red-500/40 text-red-300">{error}</div>}
      {notice && <div role="status" className="card p-3 border border-green-500/40 text-green-300">{notice}</div>}
      <section className="card p-6 space-y-4">
        <h2 className="text-lg font-semibold text-white">Current workspace</h2>
        {!org ? <p className="text-dark-400">Choose an organization to manage workspace settings.</p> : <>
          <div className="grid sm:grid-cols-2 gap-3 text-sm text-dark-300"><div>Workspace ID: <code className="break-all">{org.id}</code></div><div>Your role: <strong>{org.currentUserMembership.role}</strong></div><div>Slug: {org.slug}</div><div>Status: {org.status}</div></div>
          <form onSubmit={save} className="flex flex-col sm:flex-row items-end gap-3">
            <label className="block flex-1 w-full text-sm text-dark-300">Workspace name
              <input className="w-full mt-2 rounded-lg border border-dark-600 bg-dark-800 px-3 py-2 text-white" value={name} onChange={e => setName(e.target.value)} maxLength={255} minLength={1} disabled={!canEdit || saving} required />
            </label>
            <button type="submit" className="btn-primary" disabled={!canEdit || saving || !name.trim() || name.trim() === org.name}>{saving ? 'Saving…' : 'Save name'}</button>
          </form>
          {!canEdit && <p className="text-sm text-dark-400">Only workspace owners and administrators can update workspace details.</p>}
          <Link className="text-primary-400 hover:underline" to={`/org/${org.id}`}>Manage members and roles</Link>
        </>}
      </section>
      <section className="card p-6 space-y-4">
        <div className="flex gap-3 flex-wrap items-center justify-between"><h2 className="text-lg font-semibold text-white">Login sessions</h2><button className="text-sm text-red-400 hover:underline disabled:opacity-40" disabled={!!busySession || sessions.length === 0} onClick={revokeAll}>Revoke all sessions</button></div>
        {sessions.length === 0 ? <p className="text-sm text-dark-400">No sessions available, or the session list has not loaded.</p> :
          <div className="space-y-2">{sessions.map(session => <div key={session.id} className="rounded-lg border border-dark-700 p-3 flex flex-wrap justify-between gap-3 items-center"><div className="text-sm text-dark-200"><span>{session.current ? 'Current session' : 'Other session'}</span><p className="text-dark-400 text-xs">Last used: {session.lastUsedAt ? new Date(session.lastUsedAt).toLocaleString() : 'Unknown'}</p></div><button className="text-sm text-red-400 hover:underline disabled:opacity-40" disabled={!!busySession} onClick={() => revoke(session.id)}>{busySession === session.id ? 'Revoking…' : 'Revoke'}</button></div>)}</div>}
      </section>
      <section className="card p-6"><h2 className="text-lg font-semibold text-white mb-2">Document and AI data</h2><p className="text-sm text-dark-400">Documents, search results and AI conversations are scoped to the selected workspace. Use the Documents and AI Assistant pages to manage their respective records. Data-retention policies and automated processing jobs are not configurable in this release.</p></section>
    </div>
  );
}
