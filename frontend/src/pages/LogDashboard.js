import React, { useEffect, useState } from 'react';
import { LogCollectorApi } from '../api/logCollectorApi';
import { formatKst } from '../utils/date';
import LogDetailModal from '../components/LogDetailModal';
import { Container, Card, Table, Badge, Button, Form, Row, Col, InputGroup } from 'react-bootstrap';

const LogDashboard = () => {
const [q, setQ] = useState({ serviceName: '', keyword: '', status: '', isToday: false, page: 0, size: 20 });
const [rows, setRows] = useState([]);
const [selectedLog, setSelectedLog] = useState(null);

const loadLogs = async () => {
try {
const res = await LogCollectorApi.searchLogs(q);
setRows(res.data?.content ?? []);
} catch (e) {
console.error(e);
}
};

useEffect(() => { loadLogs(); }, [q]);

const handleSearch = () => {
setQ({ ...q, page: 0 });
};

const resetFilters = () => {
setQ({ serviceName: '', keyword: '', status: '', isToday: false, page: 0, size: 20 });
};

return (
<Container className="page py-3">
  <Card className="mb-3 shadow-sm border-0 bg-light">
    <Card.Body className="py-3">
      <Row className="g-2 align-items-center">
        <Col md={3}>
        <Form.Control
                placeholder="Service Name"
                value={q.serviceName}
                onChange={(e) => setQ({ ...q, serviceName: e.target.value })}
        />
        </Col>
        <Col md={4}>
        <InputGroup>
          <Form.Control
                  placeholder="Search by keyword..."
                  value={q.keyword}
                  onChange={(e) => setQ({ ...q, keyword: e.target.value })}
          onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
          />
          <Button variant="primary" onClick={handleSearch}>Search</Button>
        </InputGroup>
        </Col>
        <Col md={2}>
        <Form.Select
                value={q.status}
                onChange={(e) => setQ({ ...q, status: e.target.value })}
        >
        <option value="">(All Status)</option>
        <option value="NEW">NEW</option>
        <option value="RESOLVED">RESOLVED</option>
        <option value="IGNORED">IGNORED</option>
        </Form.Select>
        </Col>
        <Col md={2}>
        <Form.Check
                type="switch"
                id="logs-today-switch"
                label="Today Only"
                checked={q.isToday}
                onChange={(e) => setQ({ ...q, isToday: e.target.checked })}
        />
        </Col>
        <Col md={1} className="text-end">
        <Button variant="outline-secondary" onClick={resetFilters}>Reset</Button>
        </Col>
      </Row>
    </Card.Body>
  </Card>

  <Card className="shadow-sm">
    <Card.Body>
      <div className="d-flex justify-content-between mb-3">
        <h3 className="m-0">🧾 Logs</h3>
        <div className="text-muted small align-self-center">
          Showing page {q.page + 1}
        </div>
      </div>
      <Table hover responsive>
        <thead className="table-light">
        <tr>
          <th>Service</th>
          <th>Summary</th>
          <th>Status</th>
          <th>Level</th>
          <th>Occurred</th>
          <th>Repeat</th>
          <th style={{width: '110px'}}>Action</th>
        </tr>
        </thead>
        <tbody>
        {rows.length === 0 ? (
        <tr><td colSpan="7" className="text-center py-4">No logs found.</td></tr>
        ) : rows.map(log => (
        <tr key={log.logId ?? log.id}>
          <td>{log.serviceName}</td>
          <td className="text-truncate" style={{maxWidth: '360px'}}>{log.summary || log.message || '(No summary)'}</td>
          <td>
            <Badge
                    bg={
                    log.status===
            'RESOLVED'
            ? 'success'
            : log.status === 'IGNORED'
            ? 'secondary'
            : 'warning' // NEW
            }
            >
            {log.status}
            </Badge>
          </td>
          <td><Badge bg="info">{log.logLevel}</Badge></td>
          <td>{formatKst(log.occurredTime)}</td>
          <td>{log.repeatCount}</td>
          <td>
            <Button variant="outline-primary" size="sm" onClick={() => setSelectedLog(log)}>
            View
            </Button>
          </td>
        </tr>
        ))}
        </tbody>
      </Table>

      <div className="d-flex justify-content-center gap-2 mt-3">
        <Button size="sm" variant="outline-primary" disabled={q.page === 0} onClick={() => setQ({ ...q, page: q.page - 1 })}>Prev</Button>
        <Button size="sm" variant="outline-primary" onClick={() => setQ({ ...q, page: q.page + 1 })}>Next</Button>
      </div>
    </Card.Body>
  </Card>

  {selectedLog && (
  <LogDetailModal
          log={selectedLog}
          onClose={() => setSelectedLog(null)}
  />
  )}
</Container>
);
};

export default LogDashboard;