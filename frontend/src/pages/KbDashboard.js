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
} finally {
setLoading(false);
}
};

useEffect(() => { load(); }, [q.page, q.size]);

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
    <Card className="mb-4 shadow-sm">
        <Card.Body>
            <div className="d-flex justify-content-between align-items-center mb-3">
                <h3 className="m-0">📚 KB Articles</h3>
                <small className="text-muted">Total: {rows.length} items</small>
            </div>
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
                {/* [수정] CreatedBy를 SelectBox로 변경 (system, user, admin) */}
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

    <Card className="shadow-sm">
        <Table hover responsive className="align-middle mb-0">
            <thead className="table-light">
            <tr>
                <th style={{width:'80px'}}>ID</th>
                <th style={{width:'120px'}}>Status</th>
                <th>Title</th>
                <th style={{width:'150px'}}>Author</th>
                <th style={{width:'180px'}}>Last Activity</th>
                <th style={{width:'100px'}}>Action</th>
            </tr>
            </thead>
            <tbody>
            {rows.length === 0 ? (
            <tr><td colSpan="6" className="text-center py-4 text-muted">문서가 없습니다.</td></tr>
            ) : rows.map((r) => (
            <tr key={r.id}>
                <td><small className="text-muted">#{r.id}</small></td>
                <td>{getStatusBadge(r.status)}</td>
                <td>
                    <Link to={`/kb/${r.id}`} className="text-decoration-none fw-bold text-dark text-truncate d-block" style={{maxWidth: '400px'}}>
                    {r.incidentTitle || '(No Title)'}
                    </Link>
                </td>
                <td><small>{r.createdBy ?? '-'}</small></td>
                <td className="small text-muted">{formatKst(r.lastActivityAt ?? r.updatedAt ?? r.createdAt)}</td>
                <td>
                    <Link to={`/kb/${r.id}`} className="btn btn-sm btn-outline-primary">Detail</Link>
                </td>
            </tr>
            ))}
            </tbody>
        </Table>

        {/* 페이지네이션 생략 (기존 코드 유지) */}
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