import React, { useEffect, useState } from 'react';
import { LogCollectorApi } from '../api/logCollectorApi';
import { formatKst } from '../utils/date';

const LogDashboard = () => {
const [q, setQ] = useState({ serviceName: '', keyword: '', status: '', isToday: false, page: 0, size: 20 });
const [rows, setRows] = useState([]);
const [loading, setLoading] = useState(false);

const load = async () => {
setLoading(true);
try {
const res = await LogCollectorApi.searchLogs(q);
const data = res.data?.content ?? res.data ?? [];
setRows(data);
} finally {
setLoading(false);
}
};

useEffect(() => { load(); }, [q.page, q.size]); // eslint-disable-line

const updateStatus = async (id, st) => {
await LogCollectorApi.updateLogStatus(id, st);
await load();
};

return (
<div className="page">
  <div className="card">
    <div className="spread">
      <h3>LC Logs</h3>
      <div className="small">GET /api/logs</div>
    </div>

    <div className="row">
      <input className="input" placeholder="serviceName" value={q.serviceName}
             onChange={(e) => setQ({ ...q, serviceName: e.target.value })} />
      <input className="input" placeholder="keyword" value={q.keyword}
             onChange={(e) => setQ({ ...q, keyword: e.target.value })} />
      <select className="select" value={q.status} onChange={(e) => setQ({ ...q, status: e.target.value })}>
      <option value="">(all)</option>
      <option value="NEW">NEW</option>
      <option value="ACKNOWLEDGED">ACKNOWLEDGED</option>
      <option value="RESOLVED">RESOLVED</option>
      <option value="IGNORED">IGNORED</option>
      </select>
      <label className="small">
        <input type="checkbox" checked={q.isToday} onChange={(e) => setQ({ ...q, isToday: e.target.checked })} />
        today
      </label>
      <button className="btn" type="button" onClick={() => { setQ({ ...q, page: 0 }); load(); }} disabled={loading}>
      Search
      </button>
    </div>
  </div>

  <table className="table">
    <thead>
    <tr>
      <th className="th">id</th>
      <th className="th">service</th>
      <th className="th">summary</th>
      <th className="th">status</th>
      <th className="th">occurred</th>
      <th className="th">actions</th>
    </tr>
    </thead>
    <tbody>
    {rows.map((r) => (
    <tr key={r.logId ?? r.id}>
      <td className="td">{r.logId ?? r.id}</td>
      <td className="td">{r.serviceName}</td>
      <td className="td truncate">{r.summary ?? '-'}</td>
      <td className="td">{r.status}</td>
      <td className="td">{formatKst(r.occurredTime ?? r.firstOccurredAt ?? r.lastOccurredAt)}</td>
      <td className="td">
        <div className="row">
          <button className="btn" type="button" onClick={() => updateStatus(r.logId ?? r.id, 'ACKNOWLEDGED')}>ACK</button>
          <button className="btn" type="button" onClick={() => updateStatus(r.logId ?? r.id, 'RESOLVED')}>RESOLVE</button>
          <button className="btn" type="button" onClick={() => updateStatus(r.logId ?? r.id, 'IGNORED')}>IGNORE</button>
        </div>
      </td>
    </tr>
    ))}
    </tbody>
  </table>

  <div className="row">
    <button className="btn" disabled={q.page === 0} onClick={() => setQ({ ...q, page: Math.max(0, q.page - 1) })}>Prev</button>
    <div className="small">page: {q.page}</div>
    <button className="btn" onClick={() => setQ({ ...q, page: q.page + 1 })}>Next</button>
  </div>
</div>
);
};

export default LogDashboard;
