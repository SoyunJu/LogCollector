import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { LogCollectorApi } from '../api/logCollectorApi';
import { formatKst } from '../utils/date';
import { Container, Card, Table, Badge, Button, Form, Row, Col, Spinner, ButtonGroup } from 'react-bootstrap';

const IncidentDashboard = () => {
const [q, setQ] = useState({ serviceName: '', status: '', page: 0, size: 20 });
const [rows, setRows] = useState([]);
const [loading, setLoading] = useState(false);

const load = async () => {
setLoading(true);
try {
const res = await LogCollectorApi.searchIncidents(q);
const data = res.data?.content ?? res.data ?? [];
setRows(data);
} finally {
setLoading(false);
}
};

useEffect(() => { load(); }, [q.page, q.size]); // eslint-disable-line

const updateStatus = async (logHash, newStatus) => {
if(!window.confirm(`상태를 ${newStatus}로 변경하시겠습니까?`)) return;
await LogCollectorApi.updateIncidentStatus(logHash, newStatus);
await load();
};

const createDraft = async (incidentId) => {
if(!window.confirm('KB 초안을 생성하시겠습니까?')) return;
try {
const res = await LogCollectorApi.createDraft(incidentId);
alert(`Draft created! KB ID: ${res.data}`);
load();
} catch(e) {
alert('생성 실패: ' + e.message);
}
};

const getStatusBadge = (status) => {
switch(status) {
case 'OPEN': return <Badge bg="danger">OPEN</Badge>;
case 'UNDERWAY': return <Badge bg="primary">UNDERWAY</Badge>;
case 'RESOLVED': return <Badge bg="success">RESOLVED</Badge>;
case 'CLOSED': return <Badge bg="dark">CLOSED</Badge>;
case 'IGNORED': return <Badge bg="secondary">IGNORED</Badge>;
default: return <Badge bg="light" text="dark">{status}</Badge>;
}
};

return (
<Container className="page py-3">
    <Card className="mb-4 shadow-sm">
        <Card.Body>
            <div className="d-flex justify-content-between align-items-center mb-3">
                <h3 className="m-0">🚨 Incidents</h3>
                <small className="text-muted">Managed Issues</small>
            </div>
            <Row className="g-2">
                <Col md={5}>
                <Form.Control
                        placeholder="Service Name"
                        value={q.serviceName}
                        onChange={(e) => setQ({ ...q, serviceName: e.target.value })}
                />
                </Col>
                <Col md={4}>
                <Form.Select value={q.status} onChange={(e) => setQ({ ...q, status: e.target.value })}>
                <option value="">(All Status)</option>
                <option value="OPEN">OPEN</option>
                <option value="UNDERWAY">UNDERWAY</option>
                <option value="RESOLVED">RESOLVED</option>
                <option value="CLOSED">CLOSED</option>
                <option value="IGNORED">IGNORED</option>
                </Form.Select>
                </Col>
                <Col md={3}>
                <Button variant="primary" className="w-100" onClick={() => { setQ({ ...q, page: 0 }); load(); }} disabled={loading}>
                {loading ? <Spinner size="sm" animation="border" /> : 'Search'}
                </Button>
                </Col>
            </Row>
        </Card.Body>
    </Card>

    <Card className="shadow-sm">
        <Table hover responsive className="align-middle mb-0">
            <thead className="table-light">
            <tr>
                <th style={{width:'100px'}}>LogHash</th>
                <th style={{width:'150px'}}>Service</th>
                <th>Title / Summary</th>
                <th style={{width:'100px'}}>Status</th>
                <th style={{width:'160px'}}>Last Occurred</th>
                <th style={{width:'80px'}}>Count</th>
                <th style={{width:'80px'}}>KB</th>
                <th style={{width:'260px'}}>Actions</th>
            </tr>
            </thead>
            <tbody>
            {rows.length === 0 ? (
            <tr><td colSpan="8" className="text-center py-4 text-muted">데이터가 없습니다.</td></tr>
            ) : rows.map((r) => (
            <tr key={r.logHash}>
                <td><code className="small text-muted">{r.logHash?.substring(0,8)}...</code></td>
                <td><Badge bg="info" className="text-dark">{r.serviceName}</Badge></td>
                <td>
                    <Link to={`/incidents/${r.logHash}`} className="text-decoration-none fw-bold text-dark">
                    <div className="text-truncate" style={{maxWidth: '300px'}}>
                    {r.incidentTitle ?? r.summary ?? '(No Title)'}
                    </div>
                    </Link>
                </td>
                <td>{getStatusBadge(r.status)}</td>
                <td className="small text-muted">{formatKst(r.lastOccurredAt)}</td>
                <td><Badge pill bg="light" text="dark" className="border">{r.repeatCount ?? 0}</Badge></td>
                <td>
                    {r.kbArticleId ? (
                    <Link to={`/kb/${r.kbArticleId}`}><Badge bg="primary">KB</Badge></Link>
                    ) : '-'}
                </td>
                <td>
                    <div className="d-flex gap-1">
                        <ButtonGroup size="sm">
                            <Button variant="outline-primary" onClick={() => updateStatus(r.logHash, 'UNDERWAY')} active={r.status === 'UNDERWAY'}>Ing</Button>
                            <Button variant="outline-success" onClick={() => updateStatus(r.logHash, 'RESOLVED')} active={r.status === 'RESOLVED'}>Fix</Button>
                            <Button variant="outline-secondary" onClick={() => updateStatus(r.logHash, 'IGNORED')} active={r.status === 'IGNORED'}>Ign</Button>
                        </ButtonGroup>
                        {r.id && !r.kbArticleId && (
                        <Button size="sm" variant="warning" onClick={() => createDraft(r.id)} title="Create Draft">+KB</Button>
                        )}
                    </div>
                </td>
            </tr>
            ))}
            </tbody>
        </Table>

        <div className="d-flex justify-content-center gap-2 p-3">
            <Button variant="outline-primary" disabled={q.page === 0} onClick={() => setQ({ ...q, page: Math.max(0, q.page - 1) })}>Prev</Button>
            <span className="align-self-center">Page {q.page}</span>
            <Button variant="outline-primary" onClick={() => setQ({ ...q, page: q.page + 1 })}>Next</Button>
        </div>
    </Card>
</Container>
);
};

export default IncidentDashboard;