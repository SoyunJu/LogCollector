import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { LogCollectorApi } from '../api/logCollectorApi';
import { formatKst } from '../utils/date';
import { Container, Card, Table, Badge, Button, Accordion, Form, Row, Col, InputGroup } from 'react-bootstrap';

const IncidentDashboard = () => {
// [수정] 검색 필터 State
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
setQ({ ...q, page: 0 }); // 검색 시 1페이지로 리셋
loadIncidents();
};

return (
<Container className="page py-3">
  {/* 1. Mini Log View (최근 NEW 로그) */}
  <Accordion className="mb-4 shadow-sm" defaultActiveKey="0">
    <Accordion.Item eventKey="0">
      <Accordion.Header>🔥 <strong>Real-time Error Logs (Recent 5 NEW)</strong></Accordion.Header>
      <Accordion.Body className="p-0">
        <Table size="sm" hover className="mb-0">
          <thead><tr className="bg-light"><th>Service</th><th>Message</th><th>Time</th><th>Action</th></tr></thead>
          <tbody>
          {miniLogs.map(log => (
          <tr key={log.id}>
            <td><Badge bg="secondary">{log.serviceName}</Badge></td>
            <td><div className="text-truncate" style={{maxWidth:'300px'}}>{log.summary}</div></td>
            <td className="small">{formatKst(log.occurredTime)}</td>
            <td><Link to="/logs" className="btn btn-sm btn-outline-primary py-0">Go</Link></td>
          </tr>
          ))}
          </tbody>
        </Table>
      </Accordion.Body>
    </Accordion.Item>
  </Accordion>

  {/* 2. Search Filter */}
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
        {/* [수정] Action 헤더 추가 */}
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
            {r.title || r.logSummary}
            </Link>
          </td>
          <td><Badge bg={r.status==='RESOLVED'?'success': r.status==='CLOSED'?'secondary':'danger'}>{r.status}</Badge></td>
          <td>{formatKst(r.lastOccurredAt)}</td>
          {/* [수정] View 버튼 추가 */}
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