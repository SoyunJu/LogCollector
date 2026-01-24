import React, { useEffect, useState } from 'react';
import { LogCollectorApi } from '../api/logCollectorApi';
import { formatKst } from '../utils/date';
import { Link } from 'react-router-dom';

const IncidentDashboard = () => {
const [q, setQ] = useState({ serviceName: '', status: '', page: 0, size: 20 });
const [rows, setRows] = useState([]);
const [loading, setLoading] = useState(false);

const load = async () => {
setLoading(true);
try {
// /api/incidents/search 가 있으므로 그걸 우선 사용
const res = await LogCollectorApi.searchIncidents(q);
const data = res.data?.content ?? res.data ?? [];
setRows(data);
} finally {
setLoading(false);
}
};

useEffect(() => { load(); }, [q.page, q.size]); // eslint-disable-line

const updateStatus = async (logHash, newStatus) => {
await LogCollectorApi.updateIncidentStatus(logHash, newStatus);
await load();
};

const createDraft = async (incidentId) => {
const res = await LogCollectorApi.createDraft(incidentId);
alert('Draft created. kbArticleId=' + res.data);
};

return (
<div className="page">
    <div className="card">
        <div className="spread">
            <h3>Incidents</h3>
            <div className="small">GET /api/incidents/search</div>
        </div>

        <div className="row">
            <input className="input" placeholder="serviceName" value={q.serviceName}
                   onChange={(e) => setQ({ ...q, serviceName: e.target.value })} />
            <select className="select" value={q.status} onChange={(e) => setQ({ ...q, status: e.target.value })}>
            <option value="">(all)</option>
            <option value="OPEN">OPEN</option>
            <option value="UNDERWAY">UNDERWAY</option>
            <option value="RESOLVED">RESOLVED</option>
            <option value="CLOSED">CLOSED</option>
            <option value="IGNORED">IGNORED</option>
            </select>

            <button className="btn" type="button" onClick={() => { setQ({ ...q, page: 0 }); load(); }} disabled={loading}>
            Search
            </button>
        </div>
    </div>

    <table className="table">
        <thead>
        <tr>
            <th className="th">logHash</th>
            <th className="th">service</th>
            <th className="th">title/summary</th>
            <th className="th">status</th>
            <th className="th">lastOccurred</th>
            <th className="th">repeat</th>
            <th className="th">kbArticleId</th>
            <th className="th">actions</th>
        </tr>
        </thead>
        <tbody>
        {rows.map((r) => (
        <tr key={r.logHash}>
            <td className="td mono">{r.logHash}</td>
            <td className="td">{r.serviceName}</td>
                  <td className="td truncate">
                    {r.logHash ? (
                      <Link to={`/incidents/${r.logHash}`}>
                        {r.summary ?? '(no summary)'}
                      </Link>
                    ) : (
                      r.summary ?? '-'
                    )}
                  </td>
            <td className="td">{r.status}</td>
            <td className="td">{formatKst(r.lastOccurredAt)}</td>
            <td className="td">{r.repeatCount ?? '-'}</td>
            <td className="td">{r.kbArticleId ?? '-'}</td>
            <td className="td">
                <div className="row">
                    <button className="btn" type="button" onClick={() => updateStatus(r.logHash, 'UNDERWAY')}>UNDERWAY</button>
                    <button className="btn" type="button" onClick={() => updateStatus(r.logHash, 'RESOLVED')}>RESOLVED</button>
                    <button className="btn" type="button" onClick={() => updateStatus(r.logHash, 'IGNORED')}>IGNORED</button>
                    {r.id && (
                    <button className="btn" type="button" onClick={() => createDraft(r.id)}>CreateDraft</button>
                    )}
                    {r.kbArticleId && (
                    <a className="btn" href={`/kb/${r.kbArticleId}`}>KB</a>
                    )}
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

export default IncidentDashboard;
