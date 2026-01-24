import React, { useEffect, useState } from 'react';
import { LogCollectorApi } from '../api/logCollectorApi';
import { formatKst } from '../utils/date';

const KbDashboard = () => {
const [q, setQ] = useState({ status: '', keyword: '', createdBy: '', page: 0, size: 20 });
const [rows, setRows] = useState([]);
const [loading, setLoading] = useState(false);

const load = async () => {
setLoading(true);
try {
const res = await LogCollectorApi.listKb(q);
const data = res.data?.content ?? res.data ?? [];
setRows(data);
} finally {
setLoading(false);
}
};

useEffect(() => { load(); }, [q.page, q.size]); // eslint-disable-line

return (
<div className="page">
    <div className="card">
        <div className="spread">
            <h3>KB Articles</h3>
            <div className="small">GET /api/kb</div>
        </div>

        <div className="row">
            <select className="select" value={q.status} onChange={(e) => setQ({ ...q, status: e.target.value })}>
            <option value="">(all)</option>
            <option value="DRAFT">DRAFT</option>
            <option value="IN_PROGRESS">IN_PROGRESS</option>
            <option value="PUBLISHED">PUBLISHED</option>
            <option value="ARCHIVED">ARCHIVED</option>
            </select>

            <input className="input" placeholder="keyword" value={q.keyword}
                   onChange={(e) => setQ({ ...q, keyword: e.target.value })} />

            <input className="input" placeholder="createdBy(system/user/admin)" value={q.createdBy}
                   onChange={(e) => setQ({ ...q, createdBy: e.target.value })} />

            <button className="btn" type="button" onClick={() => { setQ({ ...q, page: 0 }); load(); }} disabled={loading}>
            Search
            </button>
        </div>
    </div>

    <table className="table">
        <thead>
        <tr>
            <th className="th">id</th>
            <th className="th">status</th>
            <th className="th">title</th>
            <th className="th">createdBy</th>
            <th className="th">lastActivity</th>
            <th className="th">actions</th>
        </tr>
        </thead>
        <tbody>
        {rows.map((r) => (
        <tr key={r.id}>
            <td className="td">{r.id}</td>
            <td className="td">{r.status}</td>
            <td className="td truncate">{r.incidentTitle ?? '-'}</td>
            <td className="td">{r.createdBy ?? '-'}</td>
            <td className="td">{formatKst(r.lastActivityAt ?? r.updatedAt ?? r.createdAt)}</td>
            <td className="td">
                <a className="btn" href={`/kb/${r.id}`}>Detail</a>
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

export default KbDashboard;
