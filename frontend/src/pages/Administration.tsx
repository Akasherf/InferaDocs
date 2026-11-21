import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { workspaceApi, type Overview } from '@/services/workspace';
import { SystemDetails } from '@/components/SystemDetails';

const names:Record<string,string>={documents:'Active documents',chunks:'Indexed chunks',embeddedChunks:'Embedded chunks',conversations:'AI conversations',messages:'AI messages',activeMembers:'Active members',queuedJobs:'Queued jobs',runningJobs:'Running jobs',failedJobs:'Failed jobs',completedJobs:'Completed jobs'};
export function Administration(){
 const {currentOrganization:org}=useAuth();const [overview,setOverview]=useState<Overview|null>(null);const [error,setError]=useState('');const [loading,setLoading]=useState(false);
 const admin=org?.currentUserMembership.role==='OWNER'||org?.currentUserMembership.role==='ADMIN';
 const load=async()=>{if(!org||!admin)return;setLoading(true);setError('');try{const r=await workspaceApi.overview(org.id);if(!r.data)throw new Error(r.error?.message||'Unable to load metrics');setOverview(r.data);}catch(e){setError(e instanceof Error?e.message:'Unable to load metrics');}finally{setLoading(false);}};
 useEffect(()=>{setOverview(null);void load();},[org?.id,admin]);
 return <div className="max-w-6xl mx-auto pb-12 space-y-6">
  <div className="flex flex-wrap gap-4 justify-between"><div><h1 className="text-2xl font-bold text-white">Workspace Administration</h1><p className="text-dark-400">Real organization-scoped operational counters and recent audit events.</p></div><button className="btn-primary" disabled={loading||!admin} onClick={()=>void load()}>{loading?'Refreshing…':'Refresh metrics'}</button></div>
  {org && <p className="text-sm text-dark-400">Workspace: {org.name} · Role: {org.currentUserMembership.role}</p>}
  {!admin && <div role="alert" className="card p-5">Owner or admin access required.</div>}
  {error && <div role="alert" className="card p-4 text-red-400">{error}</div>}
  {overview && admin && <>
    <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-5 gap-3">{Object.entries(overview.counters).map(([key,val])=><div className="card p-4" key={key}><p className="text-xs text-dark-400">{names[key]||key}</p><p className="text-2xl font-bold text-white mt-2">{val.toLocaleString()}</p></div>)}</div>
    <section className="card p-5"><h2 className="text-lg font-semibold mb-3">Recent audit events</h2>{!overview.recentEvents.length?<p className="text-dark-400">No events recorded for this workspace.</p>:<div className="space-y-2">{overview.recentEvents.map((e,i)=><div key={i} className="flex flex-wrap justify-between gap-3 border-b border-dark-700 py-2 text-sm"><span className="text-dark-200">{e.eventType}</span><span className={e.outcome==='SUCCESS'?'text-green-400':'text-amber-300'}>{e.outcome}</span><time className="text-dark-400">{new Date(e.createdAt).toLocaleString()}</time></div>)}</div>}</section>
    <SystemDetails />
    <section className="card p-5 space-y-2"><h2 className="text-lg font-semibold">Management</h2><div className="flex flex-wrap gap-4"><Link className="text-primary-400 hover:underline" to={`/org/${org!.id}`}>Members and roles</Link><Link className="text-primary-400 hover:underline" to="/tenant-settings">Tenant and retention settings</Link><Link className="text-primary-400 hover:underline" to="/ai-settings">AI settings</Link></div><p className="text-xs text-dark-400">Counts reflect the current workspace and only persisted records; no synthetic telemetry.</p></section>
  </>}
 </div>;
}
