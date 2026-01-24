import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { LogCollectorApi } from '../api/logCollectorApi';
import { formatKst } from '../utils/date';
import { Container, Card, Table, Badge, Button, Form, Row, Col, Spinner } from 'react-bootstrap';

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
} catch (e) {
console.error(e);
} finally {
setLoading(false);
}
};

useEffect(() => { load(); }, [q.page, q.size]);

// 스케줄러 수동 실행
const runScheduler = async () => {
if(!window.confirm("스케줄러(Outbox 처리 등)를 즉시 실행하시겠습니까?")) return;
try {
await LogCollectorApi.runScheduler();
alert("스케줄러 실행 요청이 전송되었습니다.");
} catch(e) {
alert("실패: " + (e.response?.data?.message || e.message));
}
};

const getStatusBadge = (status) => {
switch (status) {
case 'PUBLISHED': return <Badge bg="success">PUBLISHED</Badge>;
case 'DRAFT': return <Badge bg="secondary">DRAFT</Badge>;
case 'IN_PROGRESS': return <Badge bg="primary">IN_PROGRESS</Badge>;
case 'ARCHIVED': return <Badge bg="dark">ARCHIVED</Badge>;
default: return <Badge bg="light" text="dark">{status}</Badge>;
}
};

return (
<Container className="page py-3">
    {/* 상단 헤더 */}
    <div className="d-flex justify-content-between align-items-center mb-3">
        <h3 className="m-0">📚 KB Articles</h3>
        <Button variant="outline-danger" size="sm" onClick={runScheduler}>
            ⚡ Run Scheduler Now
        </Button>
    </div>

    {/* 검색 필터 */}
    <Card className="mb-4 shadow-sm">
        <Card.Body>
            <Row className="g-2">
                <Col md={3}>
                <Form.Select value={q.status} onChange={(e) => setQ({ ...q, status: e.target.value })}>
                <option value="">(All Status)</option>
                <option value="DRAFT">DRAFT</option>
                <option value="IN_PROGRESS">IN_PROGRESS</option>
                <option value="PUBLISHED">PUBLISHED</option>
                <option value="ARCHIVED">ARCHIVED</option>
                </Form.Select>
                </Col>
                <Col md={3}>
                <Form.Select value={q.createdBy} onChange={(e) => setQ({ ...q, createdBy: e.target.value })}>
                <option value="">(All Authors)</option>
                <option value="system">SYSTEM</option>
                <option value="user">USER</option>
                <option value="admin">ADMIN</option>
                </Form.Select>
                </Col>
                <Col md={4}>
                <Form.Control
                        placeholder="Keyword (Title)"
                        value={q.keyword}
                        onChange={(e) => setQ({ ...q, keyword: e.target.value })}
                onKeyDown={(e) => e.key === 'Enter' && load()}
                />
                </Col>
                <Col md={2}>
                <Button variant="primary" className="w-100" onClick={() => { setQ({ ...q, page: 0 }); load(); }} disabled={loading}>
                {loading ? <Spinner size="sm" animation="border" /> : 'Search'}
                </Button>
                </Col>
            </Row>
        </Card.Body>
    </Card>

    {/* 목록 테이블 */}
    <Card className="shadow-sm">
        <Table hover responsive className="align-middle mb-0">
            <thead className="table-light">
            <tr>
                <th style={{width:'60px'}}>ID</th>
                <th style={{width:'100px'}}>Status</th>
                <th>Title</th>
                <th style={{width:'120px'}}>Author</th>
                <th style={{width:'160px'}}>Last Activity</th>
                <th style={{width:'80px'}}>Actions</th>
            </tr>
            </thead>
            <tbody>
            {rows.length === 0 ? (
            <tr><td colSpan="6" className="text-center py-4 text-muted">문서가 없습니다.</td></tr>
            ) : rows.map((r) => (
            <tr key={r.id}>
                <td className="text-muted small">#{r.id}</td>
                <td>{getStatusBadge(r.status)}</td>
                <td>
                    <Link to={`/kb/${r.id}`} className="text-decoration-none fw-bold text-dark">
                    {r.title || r.incidentTitle || <span className="text-muted fst-italic">(No Title)</span>}
                    </Link>
                </td>
                <td>
                    <Badge bg="light" text="dark" className="border">
                        {r.createdBy ?? 'Unknown'}
                    </Badge>
                </td>
                <td className="small text-muted">{formatKst(r.lastActivityAt ?? r.updatedAt ?? r.createdAt)}</td>
                <td>
                    <Link to={`/kb/${r.id}`} className="btn btn-sm btn-outline-secondary">
                    View
                    </Link>
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

export default KbDashboard;