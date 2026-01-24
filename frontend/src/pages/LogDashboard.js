import React, { useEffect, useState, useRef } from 'react';
import { LogCollectorApi } from '../api/logCollectorApi';
import { formatKst } from '../utils/date';
// [추가] 모달 컴포넌트 임포트 (파일 경로 확인 필요, 없으면 제거 가능하지만 편의성을 위해 권장)
import LogDetailModal from '../components/LogDetailModal';
import { Container, Card, Table, Badge, Button, Form, Row, Col, Spinner } from 'react-bootstrap';

const LogDashboard = () => {
const [q, setQ] = useState({ serviceName: '', keyword: '', status: '', isToday: false, page: 0, size: 20 });
const [rows, setRows] = useState([]);
const [loading, setLoading] = useState(false);

// [추가] 상세 보기용 State
const [selectedLog, setSelectedLog] = useState(null);

// [추가] 자동 새로고침 (Live Mode)
const [autoRefresh, setAutoRefresh] = useState(false);
const intervalRef = useRef(null);

const load = async (isBackground = false) => {
if(!isBackground) setLoading(true);
try {
const res = await LogCollectorApi.searchLogs(q);
const data = res.data?.content ?? res.data ?? [];
setRows(data);
} finally {
if(!isBackground) setLoading(false);
}
};

useEffect(() => { load(); }, [q.page, q.size, q.status, q.isToday]); // 검색 조건 변경 시 로드

// [추가] Auto Refresh 로직
useEffect(() => {
if (autoRefresh) {
intervalRef.current = setInterval(() => { load(true); }, 3000);
} else {
clearInterval(intervalRef.current);
}
return () => clearInterval(intervalRef.current);
}, [autoRefresh, q]);

const updateStatus = async (id, st) => {
// 자동 갱신 중 방해되지 않도록 confirm 후 처리
if(!window.confirm(`${st} 상태로 변경하시겠습니까?`)) return;
await LogCollectorApi.updateLogStatus(id, st);
await load();
};

const getStatusBadge = (status) => {
switch(status) {
case 'NEW': return <Badge bg="danger">NEW</Badge>;
case 'ACKNOWLEDGED': return <Badge bg="warning" text="dark">ACK</Badge>;
case 'RESOLVED': return <Badge bg="success">RESOLVED</Badge>;
case 'IGNORED': return <Badge bg="secondary">IGNORED</Badge>;
default: return <Badge bg="light" text="dark">{status}</Badge>;
}
};

return (
<Container className="page py-3">
  <Card className="mb-4 shadow-sm">
    <Card.Body>
      <div className="d-flex justify-content-between align-items-center mb-3">
        <div className="d-flex align-items-center gap-3">
          <h3 className="m-0">📊 LC Logs</h3>
          <Form.Check
                  type="switch"
                  id="auto-refresh-switch"
                  label={autoRefresh ? "Live On (3s)" : "Auto Refresh Off"}
          checked={autoRefresh}
          onChange={(e) => setAutoRefresh(e.target.checked)}
          className={autoRefresh ? "text-success fw-bold" : "text-muted"}
          />
        </div>
        <small className="text-muted">Filtered: {rows.length}</small>
      </div>
      <Row className="g-2">
        <Col md={3}>
        <Form.Control
                placeholder="Service Name"
                value={q.serviceName}
                onChange={(e) => setQ({ ...q, serviceName: e.target.value })}
        />
        </Col>
        <Col md={3}>
        <Form.Control
                placeholder="Keyword (Message)"
                value={q.keyword}
                onChange={(e) => setQ({ ...q, keyword: e.target.value })}
        />
        </Col>
        <Col md={2}>
        <Form.Select value={q.status} onChange={(e) => setQ({ ...q, status: e.target.value })}>
        <option value="">(All Status)</option>
        <option value="NEW">NEW</option>
        <option value="ACKNOWLEDGED">ACK</option>
        <option value="RESOLVED">RESOLVED</option>
        <option value="IGNORED">IGNORED</option>
        </Form.Select>
        </Col>
        <Col md={2} className="d-flex align-items-center justify-content-center border rounded bg-light">
        <Form.Check
                type="checkbox"
                label="Today Only"
                checked={q.isToday}
                onChange={(e) => setQ({ ...q, isToday: e.target.checked })}
        className="mb-0"
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
        <th style={{width:'60px'}}>ID</th>
        <th style={{width:'150px'}}>Service</th>
        <th>Summary</th>
        <th style={{width:'100px'}}>Status</th>
        <th style={{width:'180px'}}>Occurred</th>
        <th style={{width:'220px'}}>Actions</th>
      </tr>
      </thead>
      <tbody>
      {rows.length === 0 ? (
      <tr><td colSpan="6" className="text-center py-4 text-muted">로그 데이터가 없습니다.</td></tr>
      ) : rows.map((r) => (
      <tr key={r.logId ?? r.id} style={{cursor: 'pointer'}} onClick={() => setSelectedLog(r)}>
      <td className="text-muted">#{r.logId ?? r.id}</td>
      <td><Badge bg="info" className="text-dark">{r.serviceName}</Badge></td>
      <td>
        <div className="text-truncate" style={{maxWidth: '350px'}} title={r.summary}>
        {r.summary || <span className="text-muted">(No summary)</span>}
        </div>
      </td>
      <td>{getStatusBadge(r.status)}</td>
      <td className="small text-muted font-monospace">
        {formatKst(r.occurredTime ?? r.firstOccurredAt ?? r.lastOccurredAt)}
      </td>
      <td onClick={(e) => e.stopPropagation()}>
      <div className="d-flex gap-1">
        <Button size="sm" variant="outline-warning" onClick={() => updateStatus(r.logId ?? r.id, 'ACKNOWLEDGED')}>ACK</Button>
        <Button size="sm" variant="outline-success" onClick={() => updateStatus(r.logId ?? r.id, 'RESOLVED')}>Fix</Button>
        <Button size="sm" variant="outline-secondary" onClick={() => updateStatus(r.logId ?? r.id, 'IGNORED')}>Ign</Button>
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

  {/* 로그 상세 모달 연결 */}
  {selectedLog && (
  <LogDetailModal log={selectedLog} onClose={() => { setSelectedLog(null); load(); }} />
  )}
</Container>
);
};

export default LogDashboard;