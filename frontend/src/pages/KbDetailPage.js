import React, { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { LogCollectorApi } from '../api/logCollectorApi';
import { formatKst } from '../utils/date';

const KbDetailPage = () => {
const params = useParams();
const kbArticleId = useMemo(() => params.kbArticleId, [params.kbArticleId]);

const [kb, setKb] = useState(null);
const [loading, setLoading] = useState(false);

const [addendumPage, setAddendumPage] = useState(0);
const [addendumSize, setAddendumSize] = useState(20);

const [title, setTitle] = useState('');
const [content, setContent] = useState('');
const [createdBy, setCreatedBy] = useState('user');

const load = async () => {
setLoading(true);
try {
const res = await LogCollectorApi.getKbDetail(kbArticleId, { addendumPage, addendumSize });
setKb(res.data);
setTitle(res.data?.incidentTitle ?? '');
setContent(res.data?.content ?? '');
} finally {
setLoading(false);
}
};

useEffect(() => { load(); }, [kbArticleId, addendumPage, addendumSize]); // eslint-disable-line

const saveAppend = async () => {
await LogCollectorApi.postKbArticle(kbArticleId, { title, content, createdBy });
await load();
};

const updateDraft = async () => {
await LogCollectorApi.updateDraft(kbArticleId, { title, content, createdBy });
await load();
};

if (!kb) return <div className="page">{loading ? 'Loading...' : 'No data'}</div>;

return (
<div className="page">
    <div className="card">
        <div className="spread">
            <h3>KB Detail #{kb.id}</h3>
            <div className="small">GET /api/kb/{'{id}'}</div>
        </div>

        <div className="row small">
            <div>Status: {kb.status}</div>
            <div>Service: {kb.serviceName ?? '-'}</div>
            <div>ErrorCode: {kb.errorCode ?? '-'}</div>
        </div>

        <div className="row small">
            <div>CreatedAt: {formatKst(kb.createdAt)}</div>
            <div>UpdatedAt: {formatKst(kb.updatedAt)}</div>
            <div>LastActivity: {formatKst(kb.lastActivityAt)}</div>
        </div>
    </div>

    <div className="card">
        <div className="row">
            <input className="input" value={title} placeholder="incidentTitle"
                   onChange={(e) => setTitle(e.target.value)} />
            <select className="select" value={createdBy} onChange={(e) => setCreatedBy(e.target.value)}>
            <option value="user">user</option>
            <option value="system">system</option>
            <option value="admin">admin</option>
            </select>
        </div>

        <div className="row">
          <textarea className="textarea" value={content} placeholder="content (append + latest content)"
                    onChange={(e) => setContent(e.target.value)} />
            </div>

            <div className="row">
            <button className="btn primary" type="button" onClick={saveAppend} disabled={loading}>Save / Append</button>
            <button className="btn" type="button" onClick={updateDraft} disabled={loading}>Update Draft</button>
            </div>
            </div>

            <div className="card">
            <div className="spread">
            <h4>Addendums</h4>
            <div className="small">
            page={kb.addendumPage ?? addendumPage}, size={kb.addendumSize ?? addendumSize},
            total={kb.addendumTotal ?? '-'}, hasNext={(kb.addendumHasNext ?? false) ? 'Y' : 'N'}
            </div>
            </div>

            <div className="row">
            <button className="btn" disabled={addendumPage === 0} onClick={() => setAddendumPage(Math.max(0, addendumPage - 1))}>Prev</button>
            <button className="btn" disabled={!(kb.addendumHasNext ?? false)} onClick={() => setAddendumPage(addendumPage + 1)}>Next</button>
            <select className="select" value={addendumSize} onChange={(e) => { setAddendumPage(0); setAddendumSize(Number(e.target.value)); }}>
            <option value={10}>10</option>
            <option value={20}>20</option>
            <option value={50}>50</option>
            <option value={100}>100</option>
            </select>
            </div>

            <ul>
            {(kb.addendums ?? []).map((a) => (
            <li key={a.id}>
            <div className="small">#{a.id} / {a.createdBy} / {formatKst(a.createdAt)}</div>
            <div className="mono">{a.content}</div>
            </li>
            ))}
            </ul>
            </div>
            </div>
            );
            };

            export default KbDetailPage;
