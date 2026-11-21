import { Link } from 'react-router-dom';
import { Home, AlertTriangle, ArrowLeft } from 'lucide-react';

export function NotFound() {
  return (
    <div className="min-h-[60vh] flex items-center justify-center px-4">
      <div className="text-center max-w-md">
        <div className="w-20 h-20 mx-auto mb-6 rounded-2xl bg-dark-800/50 border border-dark-700/50 flex items-center justify-center">
          <AlertTriangle className="w-10 h-10 text-yellow-500" />
        </div>
        <h1 className="text-4xl font-bold text-dark-50 mb-2">404</h1>
        <h2 className="text-xl font-semibold text-dark-300 mb-4">Page Not Found</h2>
        <p className="text-dark-400 mb-8 max-w-sm mx-auto">
          The page you're looking for doesn't exist or has been moved. This feature may be coming in a future stage.
        </p>
        <div className="flex flex-col sm:flex-row items-center justify-center gap-3">
          <Link to="/" className="btn-primary flex items-center gap-2">
            <Home className="w-4 h-4" />
            Go to Dashboard
          </Link>
          <button
            onClick={() => window.history.back()}
            className="btn-secondary flex items-center gap-2"
          >
            <ArrowLeft className="w-4 h-4" />
            Go Back
          </button>
        </div>
        <div className="mt-8 pt-8 border-t border-dark-700/50">
          <p className="text-xs text-dark-500 mb-3">Available in Stage 1:</p>
          <div className="flex flex-wrap items-center justify-center gap-2 text-sm">
            <span className="px-3 py-1 bg-green-500/20 text-green-400 border border-green-500/30 rounded-full">Dashboard</span>
            <span className="px-3 py-1 bg-green-500/20 text-green-400 border border-green-500/30 rounded-full">Health Checks</span>
            <span className="px-3 py-1 bg-green-500/20 text-green-400 border border-green-500/30 rounded-full">System Info</span>
          </div>
          <p className="text-xs text-dark-500 mt-3">Coming in future stages:</p>
          <div className="flex flex-wrap items-center justify-center gap-2 text-sm">
            <span className="px-3 py-1 bg-yellow-500/20 text-yellow-400 border border-yellow-500/30 rounded-full">Documents</span>
            <span className="px-3 py-1 bg-yellow-500/20 text-yellow-400 border border-yellow-500/30 rounded-full">Search</span>
            <span className="px-3 py-1 bg-yellow-500/20 text-yellow-400 border border-yellow-500/30 rounded-full">AI Chat</span>
            <span className="px-3 py-1 bg-yellow-500/20 text-yellow-400 border border-yellow-500/30 rounded-full">Settings</span>
          </div>
        </div>
      </div>
    </div>
  );
}