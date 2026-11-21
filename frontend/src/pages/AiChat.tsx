import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Bot, Send, Loader2, FileText, Plus, Trash2, Pencil } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { conversationApi, type AiConversation, type AiMessage, type RagCitation } from '@/services/api';

export function AiChat() {
  const {currentOrganization}=useAuth();
  const org=currentOrganization?.id;
  const [chats,setChats]=useState<AiConversation[]>([]);
  const [selected,setSelected]=useState<string|null>(null);
  const [messages,setMessages]=useState<AiMessage[]>([]);
  const [question,setQuestion]=useState('');
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState('');
  async function reload(o:string) {
    const r=await conversationApi.list(o);
    if(!r.success || !r.data) throw new Error(r.error?.message || 'Could not load conversations');
    setChats(r.data);
  }
  useEffect(()=>{let active=true; setChats([]);setSelected(null);setMessages([]);setError('');
    if(org) conversationApi.list(org).then(r=>{if(active){if(r.success)setChats(r.data||[]);else setError(r.error?.message||'Could not load conversations');}}).catch(e=>{if(active)setError(String(e));});
    return()=>{active=false};
  },[org]);
  useEffect(()=>{let active=true;setMessages([]);setError('');
    if(org&&selected) conversationApi.messages(org,selected).then(r=>{if(active){if(r.success)setMessages(r.data||[]);else setError(r.error?.message||'Could not load messages');}}).catch(e=>{if(active)setError(String(e));});
    return()=>{active=false};
  },[org,selected]);
  async function create(){if(!org||busy)return;setBusy(true);setError('');try{
    const r=await conversationApi.create(org,'New conversation');
    if(!r.success||!r.data)throw Error(r.error?.message||'Create failed');
    await reload(org);setSelected(r.data.id);
  }catch(e){setError(String(e))}finally{setBusy(false)}}
  async function rename(id:string,title:string){if(!org)return;const name=window.prompt('Conversation title',title);if(!name||!name.trim())return;
    try{await conversationApi.rename(org,id,name.trim());await reload(org)}catch(e){setError(String(e))}}
  async function remove(id:string){if(!org||!window.confirm('Permanently delete this conversation and its messages?'))return;
    try{await conversationApi.remove(org,id);if(selected===id)setSelected(null);await reload(org)}catch(e){setError(String(e))}}
  async function submit(e:React.FormEvent){e.preventDefault();const q=question.trim();if(!org||!selected||!q||busy)return;
    setBusy(true);setError('');setQuestion('');
    try {const r=await conversationApi.ask(org,selected,q);if(!r.success||!r.data)throw Error(r.error?.message||'Question failed');
      const m=await conversationApi.messages(org,selected);if(!m.success||!m.data)throw Error(m.error?.message||'Reload failed');
      setMessages(m.data);await reload(org);
    }catch(e){setQuestion(q);setError(String(e))}finally{setBusy(false)}}
  return <div className="p-4 md:p-8 max-w-6xl mx-auto space-y-4">
    <div className="flex items-center gap-3"><Bot className="text-primary-400"/><div><h1 className="text-2xl font-bold text-white">AI Document Assistant</h1><p className="text-gray-400 text-sm">Persistent conversations · organization-scoped history</p></div></div>
    {!org?<p className="text-gray-300">Select an <Link to="/organizations" className="underline">organization</Link> first.</p>:<div className="grid gap-4 md:grid-cols-[250px_1fr]">
      <aside className="card p-3 space-y-3"><button onClick={create} disabled={busy} className="btn btn-primary flex gap-2 items-center w-full justify-center"><Plus size={16}/>New conversation</button>
        <div className="space-y-1 max-h-[60vh] overflow-y-auto">{chats.map(c=><div key={c.id} className={`flex gap-1 items-center rounded p-2 ${selected===c.id?'bg-gray-700':''}`}>
          <button className="flex-1 text-left text-gray-200 text-sm truncate" onClick={()=>setSelected(c.id)} title={c.title}>{c.title}</button>
          <button aria-label="Rename conversation" title="Rename" onClick={()=>rename(c.id,c.title)}><Pencil size={14}/></button>
          <button aria-label="Delete conversation" title="Delete" onClick={()=>remove(c.id)}><Trash2 size={14}/></button></div>)}
          {!chats.length&&<p className="text-gray-400 text-sm">No saved conversations</p>}</div></aside>
      <main className="space-y-3"><div className="card p-5 min-h-[330px] max-h-[60vh] overflow-y-auto space-y-5" aria-live="polite">
        {!selected?<p className="text-gray-400">Choose a conversation or create one to start.</p>:!messages.length?<p className="text-gray-400">Ask a question about your organization's indexed documents.</p>:null}
        {messages.map(m=><div key={m.id} className="border-b border-gray-700 pb-3 space-y-2">
          <div className="font-semibold text-primary-300">{m.role==='USER'?'You':'Assistant'}</div>
          <div className="whitespace-pre-wrap text-gray-200">{m.content}</div>
          {m.role==='ASSISTANT'&&m.citations?.length>0&&<div className="space-y-2">{m.citations.map((c:RagCitation)=><div key={`${c.documentId}-${c.chunkIndex}`} className="rounded border border-gray-700 p-2 text-sm">
            <div className="flex gap-2 items-center text-primary-300"><FileText size={14}/>[{c.number}] {c.filename} — chunk {c.chunkIndex}</div><p className="text-gray-400 whitespace-pre-wrap">{c.excerpt}</p></div>)}</div>}
        </div>)}{busy&&<p className="text-gray-400 flex gap-2"><Loader2 size={16} className="animate-spin"/>Processing…</p>}</div>
        {error&&<p role="alert" className="text-red-400">{error}</p>}
        <form onSubmit={submit} className="flex gap-2"><textarea className="input flex-1" rows={2} maxLength={1000} disabled={!selected||busy} value={question} onChange={e=>setQuestion(e.target.value)} placeholder="Ask a question about your documents"/><button className="btn btn-primary flex gap-2 items-center" disabled={!selected||busy||!question.trim()}><Send size={16}/>Ask</button></form>
        <p className="text-xs text-gray-500">Messages persist in PostgreSQL. Always verify answer claims against their sources.</p>
      </main></div>}
  </div>
}
