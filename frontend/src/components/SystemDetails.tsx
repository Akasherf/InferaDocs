import { useEffect, useState } from 'react';
import { systemApi, type SystemInfoResponse } from '@/services/api';

/** Technical runtime information belongs in Administration, not the primary dashboard. */
export function SystemDetails() {
  const [info, setInfo] = useState<SystemInfoResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [unavailable, setUnavailable] = useState(false);

  useEffect(() => {
    let mounted = true;
    systemApi.info()
      .then(response => {
        if (!mounted) return;
        if (response.success && response.data) {
          setInfo(response.data);
        } else {
          setUnavailable(true);
        }
      })
      .catch(() => { if (mounted) setUnavailable(true); })
      .finally(() => { if (mounted) setLoading(false); });
    return () => { mounted = false; };
  }, []);

  const fields = info ? [
    ['Application', info.name],
    ['Version', info.version],
    ['Java version', info.javaVersion],
    ['Java vendor', info.javaVendor],
    ['Operating system', info.osName],
    ['OS version', info.osVersion],
    ['Architecture', info.osArch],
    ['Last reported', new Date(info.timestamp).toLocaleString()],
  ] : [];

  return (
    <section className="card p-5" aria-labelledby="runtime-details-heading">
      <h2 id="runtime-details-heading" className="text-lg font-semibold mb-3">System Information</h2>
      {loading && <p className="text-sm text-dark-400">Loading system information…</p>}
      {!loading && unavailable && <p className="text-sm text-dark-400">System information is currently unavailable.</p>}
      {info && (
        <dl className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 text-sm">
          {fields.map(([label, value]) => (
            <div key={label}>
              <dt className="text-dark-400">{label}</dt>
              <dd className="font-mono text-dark-100 break-words">{value}</dd>
            </div>
          ))}
        </dl>
      )}
    </section>
  );
}
