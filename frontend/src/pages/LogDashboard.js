import React, { useEffect, useState } from 'react';
import { LogCollectorApi } from '../api/logCollectorApi';
import LogDetailModal from '../components/LogDetailModal';
import { Badge, Table, Form, Button, Card, Row, Col } from 'react-bootstrap';

const LogDashboard = () => {
const [logs, setLogs] = useState([]);
const [selectedLog, setSelectedLog] = useState(null);
// serviceName 필터 추가
const [filter, setFilter] = useState({ isToday: false, status: '', serviceName: '' });

const fetchLogs = async () => {
try {
const res = await LogCollectorApi.getLogs({
isToday: filter.isToday,
status: filter.status || null,
serviceName: filter.serviceName || null,
size: 20
});
setLogs(res.data.content);
} catch (err) { console.error(err); }
};

useEffect(() => { fetchLogs(); }, [filter]);

// 상태에 따른 뱃지 색상
const getBadgeVariant = (status) => {
if (status === 'NEW') return 'danger';
if (status === 'RESOLVED') return 'success';
if (status === 'ACKNOWLEDGED') return 'warning';
return 'secondary'; // IGNORED
};

return (
<div>
  <Card className="mb-4 shadow-sm border-0">
    <Card.Body>
      <Row className="g-3 align-items-center">
        <Col xs="auto">
        <Form.Check
                type="switch" id="today-switch" label="오늘 발생만 보기"
                checked={filter.isToday} onChange={e => setFilter({...filter, isToday: e.target.checked})}
        />
        </Col>
        <Col xs="auto">
        <Form.Select value={filter.status} onChange={e => setFilter({...filter, status: e.target.value})}>
        <option value="">전체 상태</option>
        <option value="NEW">🔴 NEW</option>
        <option value="ACKNOWLEDGED">🟡 ACKNOWLEDGED</option>
        <option value="RESOLVED">🟢 RESOLVED</option>
        <option value="IGNORED">⚪ IGNORED</option>
        </Form.Select>
        </Col>
        {/* 서비스명 검색 필터 */}
        <Col xs="auto">
        <Form.Control
                type="text"
                placeholder="서비스명 검색..."
                value={filter.serviceName}
                onChange={e => setFilter({...filter, serviceName: e.target.value})}
        />
        </Col>
        <Col xs="auto">
        <Form.Control
                type="text"
                placeholder="키워드 검색..."
                value={filter.keyword}
                onChange={e => setFilter({...filter, keyword: e.target.value})}
        />
        </Col>
        <Col className="text-end">
        <Button variant="primary" onClick={fetchLogs}>새로고침</Button>
        </Col>
      </Row>
    </Card.Body>
  </Card>

  <Card className="shadow-sm border-0">
    <Table hover responsive className="mb-0 align-middle">
      <thead className="table-light">
      <tr>
        <th>서비스</th>
        <th>상태</th>
        <th>에러 코드</th>
        <th>요약</th>
        <th className="text-center">영향 서버</th>
        <th className="text-center">횟수</th>
      </tr>
      </thead>
      <tbody>
      {logs.length === 0 ? (
      <tr><td colSpan="6" className="text-center py-5">데이터가 없습니다.</td></tr>
      ) : logs.map(log => (
      <tr key={log.logId} onClick={() => setSelectedLog(log)} style={{cursor: 'pointer'}}>
      <td className="fw-bold">{log.serviceName}</td>
      <td><Badge bg={getBadgeVariant(log.status)}>{log.status}</Badge></td>
      <td className="small text-muted font-monospace">{log.errorCode}</td>
      <td className="text-truncate" style={{maxWidth: '350px'}}>{log.summary}</td>
      <td className="text-center">{log.impactedHostCount}</td>
      <td className="text-center fw-bold">{log.repeatCount}</td>
      </tr>
      ))}
      </tbody>
    </Table>
  </Card>

  {selectedLog && (
  <LogDetailModal
          log={selectedLog}
          onClose={() => { setSelectedLog(null); fetchLogs(); }}
  />
  )}
</div>
);
};

export default LogDashboard;