import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth, organizationApi } from '@/context/AuthContext';
import type { MemberResponse, OrganizationResponse } from '@/services/api';
import {
  Users,
  Plus,
  MoreVertical,
  Trash2,
  Edit,
  AlertCircle,
  Loader2,
  CheckCircle,
  XCircle,
  Building2,
} from 'lucide-react';

type MemberRole = 'OWNER' | 'ADMIN' | 'MEMBER' | 'VIEWER';
type MemberStatus = 'ACTIVE' | 'SUSPENDED';

const roleColors: Record<MemberRole, string> = {
  OWNER: 'bg-purple-500/20 text-purple-400 border-purple-500/30',
  ADMIN: 'bg-blue-500/20 text-blue-400 border-blue-500/30',
  MEMBER: 'bg-green-500/20 text-green-400 border-green-500/30',
  VIEWER: 'bg-gray-500/20 text-gray-400 border-gray-500/30',
};

const statusColors: Record<MemberStatus, string> = {
  ACTIVE: 'bg-green-500',
  SUSPENDED: 'bg-yellow-500',
};

export function OrganizationWorkspace() {
  const navigate = useNavigate();
  const { organizationId } = useParams<{ organizationId: string }>();
  const {
    user,
    organizations,
    setCurrentOrganization,
    createOrganization,
  } = useAuth();

  const [org, setOrg] = useState<OrganizationResponse | null>(null);
  const [members, setMembers] = useState<MemberResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreateOrgModal, setShowCreateOrgModal] = useState(false);
  const [showAddMemberModal, setShowAddMemberModal] = useState(false);
  const [newOrgName, setNewOrgName] = useState('');
  const [newOrgSlug, setNewOrgSlug] = useState('');
  const [newMemberEmail, setNewMemberEmail] = useState('');
  const [newMemberRole, setNewMemberRole] = useState<'ADMIN' | 'MEMBER' | 'VIEWER'>('MEMBER');
  const [creatingOrg, setCreatingOrg] = useState(false);
  const [addingMember, setAddingMember] = useState(false);
  const [memberActions, setMemberActions] = useState<Record<string, boolean>>({});

  useEffect(() => {
    if (organizationId) {
      // Find organization in list or fetch it
      const found = organizations.find(o => o.id === organizationId);
      if (found) {
        setOrg(found);
        setCurrentOrganization(found);
        fetchMembers(organizationId);
      } else {
        // Fetch organization directly
        organizationApi.get(organizationId).then(res => {
          if (res.success && res.data) {
            setOrg(res.data);
            setCurrentOrganization(res.data);
            fetchMembers(organizationId);
          } else {
            setError('Organization not found');
            setLoading(false);
          }
        }).catch(() => {
          setError('Failed to load organization');
          setLoading(false);
        });
      }
    } else {
      setLoading(false);
    }
  }, [organizationId, organizations, setCurrentOrganization]);

  const fetchMembers = async (orgId: string) => {
    try {
      const response = await organizationApi.getMembers(orgId);
      if (response.success && response.data) {
        setMembers(response.data.members);
      }
    } catch {
      setError('Failed to load members');
    } finally {
      setLoading(false);
    }
  };

  const handleCreateOrg = async () => {
    if (!newOrgName.trim() || !newOrgSlug.trim()) return;
    setCreatingOrg(true);
    try {
      const newOrg = await createOrganization(newOrgName.trim(), newOrgSlug.trim().toLowerCase());
      navigate(`/org/${newOrg.id}`);
      setShowCreateOrgModal(false);
      setNewOrgName('');
      setNewOrgSlug('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create organization');
    } finally {
      setCreatingOrg(false);
    }
  };

  const handleAddMember = async () => {
    if (!newMemberEmail.trim() || !org) return;
    setAddingMember(true);
    try {
      await organizationApi.addMember(org.id, { email: newMemberEmail.trim().toLowerCase(), role: newMemberRole });
      fetchMembers(org.id);
      setShowAddMemberModal(false);
      setNewMemberEmail('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to add member');
    } finally {
      setAddingMember(false);
    }
  };

  const handleUpdateMember = async (memberId: string, role: MemberRole, status: MemberStatus) => {
    if (!org) return;
    try {
      await organizationApi.updateMember(org.id, memberId, { role, status });
      fetchMembers(org.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update member');
    }
  };

  const handleRemoveMember = async (memberId: string) => {
    if (!org || !confirm('Are you sure you want to remove this member?')) return;
    try {
      await organizationApi.removeMember(org.id, memberId);
      fetchMembers(org.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to remove member');
    }
  };

  const toggleMemberMenu = (memberId: string) => {
    setMemberActions(prev => ({
      ...prev,
      [memberId]: !prev[memberId],
    }));
  };

  const canManageMembers = org?.currentUserMembership?.role === 'OWNER' || org?.currentUserMembership?.role === 'ADMIN';

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="w-8 h-8 animate-spin text-primary-400" />
      </div>
    );
  }

  if (error && !org) {
    return (
      <div className="card p-6 text-center">
        <AlertCircle className="w-12 h-12 text-red-400 mx-auto mb-4" />
        <h2 className="text-lg font-semibold text-dark-100 mb-2">Error</h2>
        <p className="text-dark-400 mb-4">{error}</p>
        <button onClick={() => navigate('/')} className="btn-primary">
          Go to Dashboard
        </button>
      </div>
    );
  }

  if (!org) {
    return (
      <div className="card p-6 text-center">
        <Building2 className="w-12 h-12 text-dark-500 mx-auto mb-4" />
        <h2 className="text-lg font-semibold text-dark-100 mb-2">No Organization Selected</h2>
        <p className="text-dark-400 mb-4">Choose an existing organization or create another one. Your workspaces are saved in the database.</p>
        {organizations.length > 0 && (
          <div className="mx-auto max-w-md space-y-2 text-left">
            {organizations.map(item => (
              <button key={item.id} type="button" className="btn-secondary w-full text-left" onClick={() => navigate(`/org/${item.id}`)}>
                {item.name}
              </button>
            ))}
          </div>
        )}
        <form
          className="mx-auto mt-6 max-w-md space-y-4 text-left"
          onSubmit={(event) => { event.preventDefault(); void handleCreateOrg(); }}
        >
          <label className="block text-sm font-medium text-dark-300">
            Organization name
            <input
              className="input mt-2 w-full"
              value={newOrgName}
              onChange={(event) => setNewOrgName(event.target.value)}
              placeholder="InferaDocs Demo"
              required
            />
          </label>
          <label className="block text-sm font-medium text-dark-300">
            Organization slug
            <input
              className="input mt-2 w-full"
              value={newOrgSlug}
              onChange={(event) => setNewOrgSlug(event.target.value.toLowerCase().replace(/[^a-z0-9-]/g, ''))}
              placeholder="inferadocs-demo"
              required
              pattern="[a-z0-9]+(-[a-z0-9]+)*"
            />
          </label>
          <button type="submit" className="btn-primary w-full" disabled={creatingOrg}>
            {creatingOrg ? 'Creating organization…' : 'Create Organization'}
          </button>
        </form>
      </div>
    );
  }

  return (
    <div className="space-y-6 animate-fade-in">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <nav className="flex items-center gap-2 text-sm text-dark-400 mb-2">
            <button onClick={() => navigate('/')} className="hover:text-dark-200">Dashboard</button>
            <span>/</span>
            <span className="text-dark-300">{org.name}</span>
          </nav>
          <h1 className="text-2xl font-bold text-dark-50 flex items-center gap-2">
            <Building2 className="w-6 h-6 text-primary-400" />
            {org.name}
            <span className={`px-2 py-0.5 text-xs font-medium rounded-full border ${
              org.status === 'ACTIVE'
                ? 'bg-green-500/20 text-green-400 border-green-500/30'
                : 'bg-yellow-500/20 text-yellow-400 border-yellow-500/30'
            }`}>
              {org.status}
            </span>
          </h1>
          <p className="text-dark-400 mt-1">Manage members and organization settings</p>
        </div>
        <div className="flex items-center gap-3">
          <button type="button" onClick={() => { setError(null); setShowCreateOrgModal(true); }} className="btn-secondary flex items-center gap-2">
            <Plus className="w-4 h-4" /> New Organization
          </button>
          {canManageMembers && (
            <button onClick={() => setShowAddMemberModal(true)} className="btn-primary flex items-center gap-2">
              <Plus className="w-4 h-4" />
              Add Member
            </button>
          )}
        </div>
      </div>

      {/* Members Section */}
      <section aria-labelledby="members-heading">
        <div className="flex items-center justify-between mb-4">
          <h2 id="members-heading" className="text-lg font-semibold text-dark-100">
            Members ({members.length})
          </h2>
        </div>

        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-dark-700/50 bg-dark-800/50">
                  <th className="text-left p-4 font-medium text-dark-300">Member</th>
                  <th className="text-left p-4 font-medium text-dark-300">Role</th>
                  <th className="text-left p-4 font-medium text-dark-300">Status</th>
                  <th className="text-left p-4 font-medium text-dark-300">Joined</th>
                  {canManageMembers && (
                    <th className="text-left p-4 font-medium text-dark-300">Actions</th>
                  )}
                </tr>
              </thead>
              <tbody>
                {members.map((member) => (
                  <tr key={member.id} className="border-b border-dark-700/30 hover:bg-dark-800/50 transition-colors">
                    <td className="p-4">
                      <div className="flex items-center gap-3">
                        <div className="w-9 h-9 rounded-full bg-primary-600/20 flex items-center justify-center text-primary-400 font-medium">
                          {member.displayName.charAt(0).toUpperCase()}
                        </div>
                        <div>
                          <p className="font-medium text-dark-100">{member.displayName}</p>
                          <p className="text-sm text-dark-500">{member.email}</p>
                          {member.id === user?.id && (
                            <span className="text-xs text-primary-400 bg-primary-600/10 px-2 py-0.5 rounded">
                              You
                            </span>
                          )}
                        </div>
                      </div>
                    </td>
                    <td className="p-4">
                      <span className={`px-2 py-1 text-xs font-medium rounded-full border ${roleColors[member.role as MemberRole]}`}>
                        {member.role}
                      </span>
                    </td>
                    <td className="p-4">
                      <span className="flex items-center gap-2">
                        <span className={`w-2 h-2 rounded-full ${statusColors[member.status as MemberStatus]}`} />
                        <span className="text-sm capitalize text-dark-300">{member.status.toLowerCase()}</span>
                      </span>
                    </td>
                    <td className="p-4 text-sm text-dark-400">
                      {new Date(member.joinedAt).toLocaleDateString()}
                    </td>
                    {canManageMembers && (
                      <td className="p-4">
                        <div className="relative">
                          <button
                            onClick={() => toggleMemberMenu(member.id)}
                            className="p-1.5 rounded-lg text-dark-400 hover:text-dark-100 hover:bg-dark-800 transition-colors"
                            aria-label="Member actions"
                          >
                            <MoreVertical className="w-5 h-5" />
                          </button>
                          {memberActions[member.id] && (
                            <div className="absolute right-0 top-full mt-1 z-10 card min-w-[160px] shadow-lg border border-dark-700/50 py-1 animate-fade-in">
                              <button
                                onClick={() => {
                                  const newRole = member.role === 'ADMIN' ? 'MEMBER' : 'ADMIN';
                                  if (member.role !== 'OWNER' && member.id !== user?.id) {
                                    handleUpdateMember(member.id, newRole, member.status as MemberStatus);
                                  }
                                  setMemberActions(prev => ({ ...prev, [member.id]: false }));
                                }}
                                disabled={member.role === 'OWNER' || member.id === user?.id}
                                className="w-full px-3 py-2 text-left text-sm text-dark-300 hover:bg-dark-800/50 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
                              >
                                <Edit className="w-4 h-4" />
                                {member.role === 'ADMIN' ? 'Demote to Member' : 'Promote to Admin'}
                              </button>
                              <button
                                onClick={() => {
                                  const newStatus = member.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
                                  if (member.id !== user?.id) {
                                    handleUpdateMember(member.id, member.role as MemberRole, newStatus);
                                  }
                                  setMemberActions(prev => ({ ...prev, [member.id]: false }));
                                }}
                                disabled={member.id === user?.id}
                                className="w-full px-3 py-2 text-left text-sm text-dark-300 hover:bg-dark-800/50 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
                              >
                                {member.status === 'ACTIVE' ? (
                                  <>
                                    <XCircle className="w-4 h-4" />
                                    Suspend
                                  </>
                                ) : (
                                  <>
                                    <CheckCircle className="w-4 h-4" />
                                    Activate
                                  </>
                                )}
                              </button>
                              <hr className="my-1 border-dark-700/50" />
                              <button
                                onClick={() => {
                                  if (member.id !== user?.id && member.role !== 'OWNER') {
                                    handleRemoveMember(member.id);
                                  }
                                  setMemberActions(prev => ({ ...prev, [member.id]: false }));
                                }}
                                disabled={member.id === user?.id || member.role === 'OWNER'}
                                className="w-full px-3 py-2 text-left text-sm text-red-400 hover:bg-red-500/10 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
                              >
                                <Trash2 className="w-4 h-4" />
                                Remove
                              </button>
                            </div>
                          )}
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {members.length === 0 && (
            <div className="p-8 text-center">
              <Users className="w-12 h-12 text-dark-500 mx-auto mb-4" />
              <p className="text-dark-400">No members yet</p>
              {canManageMembers && (
                <button onClick={() => setShowAddMemberModal(true)} className="btn-primary mt-4">
                  Add First Member
                </button>
              )}
            </div>
          )}
        </div>
      </section>

      {/* Create Organization Modal */}
      {showCreateOrgModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="card w-full max-w-md animate-slide-up">
            <div className="p-4 border-b border-dark-700/50 flex items-center justify-between">
              <h2 className="text-lg font-semibold text-dark-100">Create Organization</h2>
              <button onClick={() => setShowCreateOrgModal(false)} className="p-1 text-dark-400 hover:text-dark-100">
                ✕
              </button>
            </div>
            <div className="p-4 space-y-4">
              <div>
                <label className="block text-sm font-medium text-dark-300 mb-1.5">Name</label>
                <input
                  type="text"
                  value={newOrgName}
                  onChange={(e) => setNewOrgName(e.target.value)}
                  className="input"
                  placeholder="My Company"
                  autoFocus
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-dark-300 mb-1.5">Slug</label>
                <input
                  type="text"
                  value={newOrgSlug}
                  onChange={(e) => setNewOrgSlug(e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, ''))}
                  className="input"
                  placeholder="my-company"
                />
                <p className="text-xs text-dark-500 mt-1">Used in URLs. Lowercase, numbers, and hyphens only.</p>
              </div>
              <div className="flex justify-end gap-3 pt-2">
                <button onClick={() => setShowCreateOrgModal(false)} className="btn-secondary">
                  Cancel
                </button>
                <button onClick={handleCreateOrg} disabled={creatingOrg || !newOrgName.trim() || !newOrgSlug.trim()} className="btn-primary">
                  {creatingOrg ? <Loader2 className="w-4 h-4 animate-spin" /> : 'Create'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Add Member Modal */}
      {showAddMemberModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="card w-full max-w-md animate-slide-up">
            <div className="p-4 border-b border-dark-700/50 flex items-center justify-between">
              <h2 className="text-lg font-semibold text-dark-100">Add Member</h2>
              <button onClick={() => setShowAddMemberModal(false)} className="p-1 text-dark-400 hover:text-dark-100">
                ✕
              </button>
            </div>
            <div className="p-4 space-y-4">
              <div>
                <label className="block text-sm font-medium text-dark-300 mb-1.5">Email</label>
                <input
                  type="email"
                  value={newMemberEmail}
                  onChange={(e) => setNewMemberEmail(e.target.value)}
                  className="input"
                  placeholder="colleague@company.com"
                  autoFocus
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-dark-300 mb-1.5">Role</label>
                <select
                  value={newMemberRole}
                  onChange={(e) => setNewMemberRole(e.target.value as 'ADMIN' | 'MEMBER' | 'VIEWER')}
                  className="input"
                >
                  <option value="ADMIN">Admin - Full access</option>
                  <option value="MEMBER">Member - Standard access</option>
                  <option value="VIEWER">Viewer - Read-only access</option>
                </select>
              </div>
              <div className="flex justify-end gap-3 pt-2">
                <button onClick={() => setShowAddMemberModal(false)} className="btn-secondary">
                  Cancel
                </button>
                <button onClick={handleAddMember} disabled={addingMember || !newMemberEmail.trim()} className="btn-primary">
                  {addingMember ? <Loader2 className="w-4 h-4 animate-spin" /> : 'Add Member'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}