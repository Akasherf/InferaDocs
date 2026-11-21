import { useEffect, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { workspaceApi, type WorkspacePreferences } from '@/services/workspace';

export function AiSettings(){
 const {currentOrganization:org}=useAuth(); const [prefs,setPrefs]=useState<WorkspacePreferences|null>(null);
 const [models,setModels]=useState<string[]>([]);const [modelsAvailable,setModelsAvailable]=useState<boolean|null>(null);
 const [error,setError]=useState(''); const [message,setMessage]=useState(''); const [saving,setSaving]=useState(false);
 const admin=org?.currentUserMembership.role==='OWNER'||org?.currentUserMembership.role==='ADMIN';
 const orgId=org?.id;
 useEffect(()=>{setPrefs(null);setModels([]);setModelsAvailable(null);setError('');setMessage('');if(!orgId)return;
   void workspaceApi.models(orgId).then(r=>{setModels(r.data?.models??[]);setModelsAvailable(r.data?.available??false);}).catch(()=>setModelsAvailable(false));
   void workspaceApi.get(orgId).then(r=>{if(r.data)setPrefs(r.data);else setError(r.error?.message||'Unable to load settings')}).catch((e:unknown)=>setError(e instanceof Error?e.message:'Unable to load settings'));
 },[orgId]);
 const save=async(e:React.FormEvent)=>{e.preventDefault();if(!org||!prefs||!admin)return;
 setSaving(true);setError('');setMessage('');try{const r=await workspaceApi.save(org.id,prefs);if(!r.data)throw new Error(r.error?.message||'Save failed');setPrefs(r.data);setMessage('Saved. New AI answers use these settings.');}
 catch(e){setError(e instanceof Error?e.message:'Save failed');}finally{setSaving(false);}};
 return <div className="max-w-4xl mx-auto space-y-6 pb-12"><div><h1 className="text-2xl font-bold text-white">AI Model & Retrieval Settings</h1><p className="text-dark-400">Set the answering model and grounded retrieval defaults for this workspace.</p></div>
 {org && <div className="text-dark-400 text-sm">Workspace: {org.name} · {org.currentUserMembership.role}</div>}
 {error && <div role="alert" className="card p-3 text-red-400">{error}</div>}{message && <div role="status" className="card p-3 text-green-400">{message}</div>}
 {prefs && <form onSubmit={save} className="card p-6 space-y-5">
  <label className="block">Ollama model name{models.length>0 && <select aria-label="Installed Ollama models" className="w-full mt-2 bg-dark-800 border border-dark-600 rounded-lg p-2" disabled={!admin||saving} value={models.includes(prefs.aiModel)?prefs.aiModel:''} onChange={e=>e.target.value&&setPrefs({...prefs,aiModel:e.target.value})}><option value="">Select an installed model…</option>{models.map(name=><option key={name} value={name}>{name}</option>)}</select>}{modelsAvailable===false && <p className="text-amber-300 text-xs mt-2">Ollama model discovery unavailable. You can still enter an installed model manually.</p>}<input className="w-full mt-2 bg-dark-800 border border-dark-600 rounded-lg p-2" required maxLength={120} disabled={!admin||saving} value={prefs.aiModel} onChange={e=>setPrefs({...prefs,aiModel:e.target.value})}/><span className="text-dark-400 text-xs">Use an installed local model, e.g. llama3.1:8b. The backend's Ollama server URL is fixed.</span></label>
  <label className="block">Answer temperature ({prefs.aiTemperature.toFixed(2)})<input aria-label="Answer temperature" className="block w-full mt-2" type="range" min={0} max={1} step={0.05} disabled={!admin||saving} value={prefs.aiTemperature} onChange={e=>setPrefs({...prefs,aiTemperature:Number(e.target.value)})}/></label>
  <label className="block">RAG retrieval candidates ({prefs.aiTopK})<input aria-label="Retrieval candidates" className="block w-full mt-2" type="range" min={1} max={12} disabled={!admin||saving} value={prefs.aiTopK} onChange={e=>setPrefs({...prefs,aiTopK:Number(e.target.value)})}/></label>
  <label className="flex items-start gap-3"><input type="checkbox" checked={prefs.aiRerank} onChange={e=>setPrefs({...prefs,aiRerank:e.target.checked})} disabled={!admin||saving}/><span>Enable experimental hybrid reranking for new RAG answers<span className="block text-dark-400 text-xs">Leave disabled if relevance does not improve on your dataset.</span></span></label>
  <button className="btn-primary" disabled={!admin||saving} type="submit">{saving?'Saving…':'Save AI settings'}</button>
  <p className="text-xs text-dark-400">Settings apply to new AI responses. Existing embeddings always retain the configured 768-dimensional embedding model; changing the answer model does not re-embed documents.</p>
 </form>}
 </div>;
}
