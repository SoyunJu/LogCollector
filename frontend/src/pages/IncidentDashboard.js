import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { LogCollectorApi } from '../api/logCollectorApi';
import { formatKst } from '../utils/date';
import { Container, Card, Table, Badge, Button, Accordion, Form, Row, Col, InputGroup } from 'react-bootstrap';

const IncidentDashboard = () => {
// ... (기존 State: q, rows, miniLogs 등 유지) ...
const [q, setQ] = useState({ query: '', status: '', page: 0, size: 20 });
const [rows, setRows] = useState([]);
const [miniLogs, setMiniLogs] = useState([]);

const loadIncidents = async () => {
try {
const res = await LogCollectorApi.searchIncidents(q);
setRows(res.data?.content ?? []);
} catch(e) {}
};

const loadMiniLogs = async () => {
try {
const res = await LogCollectorApi.searchLogs({ page: 0, size: 5, status: 'NEW' });
setMiniLogs(res.data?.content ?? []);
} catch(e) {}
};

useEffect(() => { loadIncidents(); }, [q.page, q.size]);
useEffect(() => { loadMiniLogs(); }, []);

const handleSearch = () => {
setQ({ ...q, page: 0 });
loadIncidents();
};

return (
<Container className="page py-3">
    {/* ... (Mini Log View 및 Search Filter 기존 코드 유지) ... */}

    {/* ... Search Filter 생략 ... */}
    <Card className="mb-3 shadow-sm border-0 bg-light">
        <Card.Body className="py-3">
            <Row className="g-2 align-items-center">
                <Col md={2}>
                <Form.Select
                        value={q.status}
                        onChange={(e) => setQ({...q, status: e.target.value})}
                >
                <option value="">(All Status)</option>
                <option value="OPEN">OPEN</option>
                <option value="UNDERWAY">UNDERWAY</option>
                <option value="RESOLVED">RESOLVED</option>
                <option value="CLOSED">CLOSED</option>
                <option value="IGNORED">IGNORED</option>
                </Form.Select>
                </Col>
                <Col md={8}>
                <InputGroup>
                    <Form.Control
                            placeholder="Search by Title, Summary or Error Code..."
                            value={q.query || ''}
                    onChange={(e) => setQ({...q, query: e.target.value})}
                    onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                    />
                    <Button variant="primary" onClick={handleSearch}>Search</Button>
                </InputGroup>
                </Col>
                <Col md={2} className="text-end">
                <Button variant="outline-secondary" onClick={() => { setQ({query:'', status:'', page:0, size:20}); loadIncidents(); }}>
                Reset
                </Button>
                </Col>
            </Row>
        </Card.Body>
    </Card>

    {/* 3. Incidents List */}
    <Card className="shadow-sm">
        <Card.Body>
            <div className="d-flex justify-content-between mb-3">
                <h3 className="m-0">🚨 Incidents</h3>
                <div className="text-muted small align-self-center">
                    Showing page {q.page + 1}
                </div>
            </div>
            <Table hover responsive>
                <thead className="table-light">
                <tr>
                    <th>Service</th>
                    <th>Title</th>
                    <th>Status</th>
                    <th>Last Occurred</th>
                    <th style={{width: '100px'}}>Action</th>
                </tr>
                </thead>
                <tbody>
                {rows.length === 0 ? <tr><td colSpan="5" className="text-center py-4">No incidents found.</td></tr> : rows.map(r => (
                <tr key={r.id}>
                    <td>{r.serviceName}</td>
                    <td>
                        <Link to={`/incidents/${r.logHash}`} className="fw-bold text-dark text-decoration-none">
                        {/* [수정] title이 없으면 incidentTitle을, 그래도 없으면 Summary 표시 */}
                        {r.incidentTitle || r.title || r.logSummary || "(No Title)"}
                        </Link>
                    </td>
                    <td><Badge bg={r.status==='RESOLVED'?'success': r.status==='CLOSED'?'secondary':'danger'}>{r.status}</Badge></td>
                    <td>{formatKst(r.lastOccurredAt)}</td>
                    <td>
                        <Link to={`/incidents/${r.logHash}`}>
                        <Button variant="outline-primary" size="sm">View</Button>
                        </Link>
                    </td>
                </tr>
                ))}
                </tbody>
            </Table>

            {/* Pagination */}
            <div className="d-flex justify-content-center gap-2 mt-3">
                <Button size="sm" variant="outline-primary" disabled={q.page === 0} onClick={() => setQ({...q, page: q.page - 1})}>Prev</Button>
                <Button size="sm" variant="outline-primary" onClick={() => setQ({...q, page: q.page + 1})}>Next</Button>
            </div>
        </Card.Body>
    </Card>
</Container>
);
};
export default IncidentDashboard;