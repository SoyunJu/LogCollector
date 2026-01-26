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
                type="text"
                placeholder="Service Name"
                value={q.serviceName}
                onChange={(e) => setQ({ ...q, serviceName: e.target.value })}
        />
        </Col>
        <Col md={4}>
        <InputGroup>
          <Form.Control
                  type="text"
                  placeholder="Keyword (message, trace)"
                  value={q.keyword}
                  onChange={(e) => setQ({ ...q, keyword: e.target.value })}
          onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
          />
          <Button variant="primary" onClick={handleSearch}>Search</Button>
        </InputGroup>
        </Col>
        <Col md={2}>
        <Form.Check
                type="switch"
                id="is-today-switch"
                label="Today Only"
                checked={q.isToday}
                onChange={(e) => setQ({ ...q, isToday: e.target.checked })}
        />
        </Col>
        <Col md={1} className="text-end">
        <Button variant="outline-secondary" size="sm" onClick={resetFilters}>Reset</Button>
        </Col>
      </Row>
    </Card.Body>
  </Card>

  <Card className="shadow-sm border-0">
    <Card.Header className="bg-white fw-bold">Raw Error Logs</Card.Header>
    <Table hover responsive className="mb-0 align-middle">
      <thead className="bg-light">
      <tr>
        <th style={{width: '150px'}}>Service</th>
        <th>Summary</th>
        {/* [삭제] Status 컬럼 삭제됨 */}
        <th style={{width: '100px'}}>Level</th>
        <th style={{width: '180px'}}>Time</th>
        <th style={{width: '80px'}}>Count</th>
        <th style={{width: '100px'}}>Action</th>
      </tr>
      </thead>
      <tbody>
      {rows.length === 0 ? (
      <tr><td colSpan="6" className="text-center py-4 text-muted">No logs found.</td></tr>
      ) : rows.map(log => (
      <tr key={log.logId ?? log.id}>
        <td>{log.serviceName}</td>
        <td className="text-truncate" style={{maxWidth: '360px'}}>
        {log.summary || log.message || '(No summary)'}
        </td>
        {/* [삭제] Status 데이터 셀 삭제됨 */}
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

    <div className="d-flex justify-content-center gap-2 mt-3 mb-3">
      <Button size="sm" variant="outline-primary" disabled={q.page === 0} onClick={() => setQ({ ...q, page: q.page - 1 })}>Prev</Button>
      <Button size="sm" variant="outline-primary" onClick={() => setQ({ ...q, page: q.page + 1 })}>Next</Button>
    </div>
  </Card>

  {/* Modal */}
  <LogDetailModal
          show={!!selectedLog}
          log={selectedLog}
          onHide={() => setSelectedLog(null)}
  />
</Container>
);
};

export default LogDashboard;