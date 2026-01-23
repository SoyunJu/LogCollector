import React, { useEffect, useState } from 'react';
import { LogCollectorApi } from '../api/logCollectorApi';
import { Table, Badge, Card, Row, Col, Modal, Button, Form, Spinner, InputGroup } from 'react-bootstrap';

const IncidentDashboard = () => {
const [incidents, setIncidents] = useState([]);
const [loading, setLoading] = useState(true);

// 랭킹 데이터
const [topRepeat, setTopRepeat] = useState([]);
const [topHost, setTopHost] = useState([]);

// 검색 필터 상태
const [searchParams, setSearchParams] = useState({
serviceName: '',
status: '',
level: '',
keyword: '',
startDate: '',
endDate: '',
});

const [selectedIncident, setSelectedIncident] = useState(null);

// 데이터 조회 함수
const fetchData = async () => {
setLoading(true);
try {
// 1. 검색 조건 정리 (빈 값은 전송 제외)
const params = {};
if (searchParams.serviceName) params.serviceName = searchParams.serviceName;
if (searchParams.status) params.status = searchParams.status;
if (searchParams.level) params.level = searchParams.level; // 서버 IncidentSearch.level (ErrorLevel)
if (searchParams.keyword) params.keyword = searchParams.keyword;
if (searchParams.startDate) params.startDate = `${searchParams.startDate}T00:00:00`;
if (searchParams.endDate) params.endDate = `${searchParams.endDate}T23:59:59`;
params.size = 20; // 페이지 사이즈 고정

// 2. API 호출 (목록 + 랭킹 2종)
const [listRes, rankRepeatRes, rankHostRes] = await Promise.allSettled([
LogCollectorApi.getIncidents(params),
LogCollectorApi.getIncidentTop('repeatCount'),
LogCollectorApi.getIncidentTop('hostCount'),
]);

// 3. 결과 반영
if (listRes.status === 'fulfilled') setIncidents(listRes.value.data.content || []);
if (rankRepeatRes.status === 'fulfilled') setTopRepeat(rankRepeatRes.value.data || []);
if (rankHostRes.status === 'fulfilled') setTopHost(rankHostRes.value.data || []);
} catch (err) {
console.error('데이터 로딩 실패:', err);
} finally {
setLoading(false);
}
};

// 초기 로딩
useEffect(() => {
fetchData();
// eslint-disable-next-line
}, []);

// 검색 핸들러
const handleSearch = (e) => {
e.preventDefault();
fetchData();
};

// 입력값 변경 핸들러
const handleInputChange = (e) => {
const { name, value } = e.target;
setSearchParams((prev) => ({ ...prev, [name]: value }));
};

// 상태 변경 핸들러
const handleStatusChange = async (newStatus) => {
if (!selectedIncident) return;
try {
await LogCollectorApi.updateIncidentStatus(selectedIncident.logHash, newStatus);
alert(`상태가 ${newStatus}로 변경되었습니다.`);
setSelectedIncident(null);
fetchData();
} catch (err) {
alert('상태 변경 실패: ' + (err.response?.data?.message || err.message));
}
};

// 뱃지 색상 헬퍼
const getStatusBadge = (status) => {
switch (status) {
case 'OPEN':
return 'danger';
case 'UNDERWAY':
return 'warning';
case 'RESOLVED':
return 'success';
case 'PUBLISHED':
return 'primary';
default:
return 'secondary';
}
};

if (loading && incidents.length === 0) {
return (
<div className="text-center p-5">
    <Spinner animation="border" />
</div>
);
}

return (
<>
<h4 className="fw-bold mb-4">🚨 인시던트 관리 (Incident Management)</h4>

{/* 1. 상단 랭킹 카드 */}
<Row className="mb-4">
    <Col md={6}>
    <Card className="shadow-sm border-0 h-100">
        <Card.Header className="bg-white fw-bold text-danger">🔥 최다 발생 (Top 5 Repeat)</Card.Header>
        <Card.Body className="p-2">
            {topRepeat.length === 0 ? (
            <div className="text-center small text-muted">데이터 없음</div>
            ) : (
            topRepeat.map((item, idx) => (
            <div key={idx} className="d-flex justify-content-between border-bottom p-2 small">
                <span className="text-truncate" style={{ maxWidth: '70%' }}>
                {item.summary || '(summary 없음)'}
                </span>
                <Badge bg="danger">{item.metricValue ?? 0} 회</Badge>
            </div>
            ))
            )}
        </Card.Body>
    </Card>
    </Col>

    <Col md={6}>
    <Card className="shadow-sm border-0 h-100">
        <Card.Header className="bg-white fw-bold text-primary">🌍 최다 영향 호스트 (Top 5 Impact)</Card.Header>
        <Card.Body className="p-2">
            {topHost.length === 0 ? (
            <div className="text-center small text-muted">데이터 없음</div>
            ) : (
            topHost.map((item, idx) => (
            <div key={idx} className="d-flex justify-content-between border-bottom p-2 small">
                <span className="text-truncate" style={{ maxWidth: '70%' }}>
                {item.serviceName || '(service 없음)'} ({item.errorCode || '-'})
                </span>
                <Badge bg="primary">{item.hostCount ?? 0} 대</Badge>
            </div>
            ))
            )}
        </Card.Body>
    </Card>
    </Col>
</Row>

{/* 2. 검색 필터 영역 */}
<Card className="mb-4 shadow-sm border-0 bg-light">
    <Card.Body>
        <Form onSubmit={handleSearch}>
            <Row className="g-2">
                <Col md={2}>
                <Form.Select name="status" value={searchParams.status} onChange={handleInputChange}>
                    <option value="">모든 상태</option>
                    <option value="OPEN">OPEN</option>
                    <option value="UNDERWAY">UNDERWAY</option>
                    <option value="RESOLVED">RESOLVED</option>
                    <option value="PUBLISHED">PUBLISHED</option>
                </Form.Select>
                </Col>

                <Col md={2}>
                <Form.Select name="level" value={searchParams.level} onChange={handleInputChange}>
                    <option value="">모든 레벨</option>
                    <option value="FATAL">FATAL</option>
                    <option value="ERROR">ERROR</option>
                    <option value="WARN">WARN</option>
                </Form.Select>
                </Col>

                <Col md={3}>
                <Form.Control
                        type="text"
                        placeholder="서비스명 (예: Payment)"
                        name="serviceName"
                        value={searchParams.serviceName}
                        onChange={handleInputChange}
                />
                </Col>

                <Col md={3}>
                <Form.Control
                        type="text"
                        placeholder="키워드 검색 (제목/요약)"
                        name="keyword"
                        value={searchParams.keyword}
                        onChange={handleInputChange}
                />
                </Col>

                <Col md={2}>
                <Button variant="dark" type="submit" className="w-100">
                    🔍 검색
                </Button>
                </Col>
            </Row>

            <Row className="g-2 mt-1">
                <Col md={3}>
                <InputGroup size="sm">
                    <InputGroup.Text>From</InputGroup.Text>
                    <Form.Control type="date" name="startDate" value={searchParams.startDate} onChange={handleInputChange} />
                </InputGroup>
                </Col>

                <Col md={3}>
                <InputGroup size="sm">
                    <InputGroup.Text>To</InputGroup.Text>
                    <Form.Control type="date" name="endDate" value={searchParams.endDate} onChange={handleInputChange} />
                </InputGroup>
                </Col>
            </Row>
        </Form>
    </Card.Body>
</Card>

{/* 3. 인시던트 목록 테이블 */}
<Card className="shadow-sm border-0">
    <Table hover responsive className="mb-0 align-middle">
        <thead className="table-light">
        <tr>
            <th>ID</th>
            <th>서비스</th>
            <th>상태</th>
            <th>레벨</th>
            <th>요약</th>
            <th>발생 시각</th>
            <th className="text-center">호스트</th>
            <th className="text-center">반복</th>
        </tr>
        </thead>
        <tbody>
        {incidents.length === 0 ? (
        <tr>
            <td colSpan="8" className="text-center py-5 text-muted">
                검색 결과가 없습니다.
            </td>
        </tr>
        ) : (
        incidents.map((inc) => (
        <tr key={inc.id} onClick={() => setSelectedIncident(inc)} style={{ cursor: 'pointer' }}>
        <td>#{inc.id}</td>
        <td className="fw-bold">{inc.serviceName}</td>
        <td>
            <Badge bg={getStatusBadge(inc.status)}>{inc.status}</Badge>
        </td>
        <td>
            <Badge bg="secondary" className="text-dark bg-opacity-25">
                {inc.errorLevel}
            </Badge>
        </td>
        <td className="text-truncate" style={{ maxWidth: '300px' }}>
        {inc.summary}
        </td>
        <td className="small text-muted">{inc.lastOccurredAt ? new Date(inc.lastOccurredAt).toLocaleString() : '-'}</td>
        <td className="text-center">{inc.hostCount ?? 0}</td>
        <td className="text-center fw-bold">{inc.repeatCount ?? 0}</td>
        </tr>
        ))
        )}
        </tbody>
    </Table>
</Card>

{/* 4. 상세 모달 */}
{selectedIncident && (
<Modal show={true} onHide={() => setSelectedIncident(null)} centered size="lg">
<Modal.Header closeButton>
    <Modal.Title>
        <Badge bg={getStatusBadge(selectedIncident.status)} className="me-2">
            {selectedIncident.status}
        </Badge>
        Incident #{selectedIncident.id}
    </Modal.Title>
</Modal.Header>

<Modal.Body>
    <Row className="mb-3">
        <Col md={6}>
        <p>
            <strong>Service:</strong> {selectedIncident.serviceName}
        </p>
        </Col>
        <Col md={6}>
        <p>
            <strong>Level:</strong> {selectedIncident.errorLevel}
        </p>
        </Col>
        <Col md={6}>
        <p>
            <strong>Error Code:</strong> {selectedIncident.errorCode}
        </p>
        </Col>
        <Col md={6}>
        <p>
            <strong>Time:</strong>{' '}
            {selectedIncident.lastOccurredAt ? new Date(selectedIncident.lastOccurredAt).toLocaleString() : '-'}
        </p>
        </Col>
    </Row>

    <Form.Label className="fw-bold">Summary</Form.Label>
    <div className="p-3 bg-light border rounded mb-3">{selectedIncident.summary}</div>

    <hr />
    <div className="d-flex justify-content-between align-items-center">
        <span className="text-muted small">Hash: {selectedIncident.logHash}</span>
        <div className="d-flex gap-2">
            <Button
                    size="sm"
                    variant="outline-danger"
                    onClick={() => handleStatusChange('OPEN')}
            disabled={selectedIncident.status === 'OPEN'}
            >
            Re-Open
            </Button>
            <Button
                    size="sm"
                    variant="outline-success"
                    onClick={() => handleStatusChange('RESOLVED')}
            disabled={selectedIncident.status === 'RESOLVED'}
            >
            Resolve
            </Button>
        </div>
    </div>
</Modal.Body>
</Modal>
)}
</>
);
};

export default IncidentDashboard;
