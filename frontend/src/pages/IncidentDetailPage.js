import React, { useEffect, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { LogCollectorApi } from '../api/logCollectorApi';
import { formatKst } from '../utils/date';
import { Container, Card, Badge, Button, Row, Col, Spinner, Alert, Form, InputGroup } from 'react-bootstrap';

const IncidentDetailPage = () => {
const { logHash } = useParams();
const navigate = useNavigate();

const [incident, setIncident] = useState(null);
const [loading, setLoading] = useState(false);

// updateDetails용 입력값
const [title, setTitle] = useState('');
const [createdBy, setCreatedBy] = useState('');
const [status, setStatus] = useState('');

// AI 분석
const [aiResult, setAiResult] = useState(null);
const [loadingAi, setLoadingAi] = useState(false);

const load = async () => {
setLoading(true);
try {
// [수정] 사용자가 제공한 메서드명 getIncidentByLogHash 사용
const res = await LogCollectorApi.getIncidentByLogHash(logHash);
setIncident(res.data);
setTitle(res.data?.incidentTitle ?? '');
setCreatedBy(res.data?.createdBy ?? '');
setStatus(''); // 상세 수정 폼의 status는 초기화 (변경시에만 값 주입)
} catch (e) {
console.error(e);
// 에러 처리 필요 시 추가
} finally {
setLoading(false);
}
};

useEffect(() => {
load();
}, [logHash]);

const updateDetails = async () => {
try {
await LogCollectorApi.updateIncidentDetails(logHash, {
title: title || undefined,
createdBy: createdBy || undefined,
status: status || undefined,
});
alert('업데이트 되었습니다.');
await load();
} catch (e) {
alert('업데이트 실패: ' + e.message);
}
};

const analyzeAi = async () => {
if (!logHash) return;
setLoadingAi(true);
try {
const res = await LogCollectorApi.analyzeAi(logHash);
setAiResult(res.data);
} catch (err) {
alert('AI 분석 실패: ' + (err?.response?.data?.message ?? err?.message ?? String(err)));
} finally {
setLoadingAi(false);
}
};

// IGNORED 해제 = newStatus를 OPEN으로 되돌림(백엔드가 outbox로 LC 반영)
const unignore = async () => {
if (!window.confirm('IGNORED 해제(OPEN으로 변경) 하시겠습니까?')) return;
await LogCollectorApi.updateIncidentStatus(logHash, 'OPEN');
await load();
};

// REOPEN = OPEN으로 전이 + (recurAt은 서버가 OPEN 전이 시 set 해주는 전제)
const reopen = async () => {
if (!window.confirm('REOPEN 처리(OPEN 전이) 하시겠습니까?')) return;
await LogCollectorApi.updateIncidentStatus(logHash, 'OPEN');
await load();
};

const getStatusBadge = (status) => {
switch (status) {
case 'OPEN': return <Badge bg="danger">OPEN</Badge>;
case 'UNDERWAY': return <Badge bg="primary">UNDERWAY</Badge>;
case 'RESOLVED': return <Badge bg="success">RESOLVED</Badge>;
case 'IGNORED': return <Badge bg="secondary">IGNORED</Badge>;
case 'CLOSED': return <Badge bg="dark">CLOSED</Badge>;
default: return <Badge bg="light" text="dark">{status}</Badge>;
}
};

if (loading && !incident) {
return (
<Container className="text-center py-5">
    <Spinner animation="border" variant="primary" />
    <p className="mt-2 text-muted">Loading incident details...</p>
</Container>
);
}

if (!incident) {
return (
<Container className="py-4">
    <Alert variant="danger">No incident found for hash: {logHash}</Alert>
</Container>
);
}

const canUnignore = incident.status === 'IGNORED';
const canReopen = incident.status === 'RESOLVED' || incident.status === 'CLOSED';

return (
<Container className="page py-3">
    {/* 뒤로가기 및 헤더 */}
    <div className="mb-3">
        <Button variant="link" className="text-decoration-none p-0 mb-1 text-muted" onClick={() => navigate('/incidents')}>
        &larr; Back to Incidents
        </Button>
    </div>

    <Card className="mb-4 shadow-sm">
        <Card.Header className="d-flex justify-content-between align-items-center bg-white">
            <div className="d-flex align-items-center gap-2">
                <h3 className="m-0">Incident Detail</h3>
                {getStatusBadge(incident.status)}
            </div>
            <small className="font-monospace text-muted">{logHash}</small>
        </Card.Header>

        <Card.Body>
            {/* 기본 정보 Row 1 */}
            <Row className="mb-3">
                <Col md={4}>
                <strong>Service:</strong> <span className="text-primary">{incident.serviceName}</span>
                </Col>
                <Col md={4}>
                <strong>Error Code:</strong> <code>{incident.errorCode ?? '-'}</code>
                </Col>
                <Col md={4}>
                <strong>Repeat Count:</strong> <Badge bg="info" pill>{incident.repeatCount}</Badge>
                </Col>
            </Row>

            {/* 타임스탬프 정보 Row 2 */}
            <Row className="mb-3 small text-muted">
                <Col md={3}><strong>First:</strong><br/> {formatKst(incident.firstOccurredAt)}</Col>
                <Col md={3}><strong>Last:</strong><br/> {formatKst(incident.lastOccurredAt)}</Col>
                <Col md={3}><strong>Resolved:</strong><br/> {formatKst(incident.resolvedAt)}</Col>
                <Col md={3}><strong>Reopened:</strong><br/> {formatKst(incident.reopenedAt)}</Col>
            </Row>

            <div className="text-muted small mb-4">
                <strong>Created By:</strong> {incident.createdBy ?? '-'}
            </div>

            <hr />

            {/* 액션 버튼 영역 */}
            <div className="d-flex flex-wrap gap-2">
                <Button variant="outline-primary" onClick={analyzeAi} disabled={loadingAi}>
                    {loadingAi ? <><Spinner size="sm" animation="border"/> AI 분석 중...</> : '🤖 AI 분석'}
                </Button>

                {incident.kbArticleId ? (
                <Link to={`/kb/${incident.kbArticleId}`}>
                <Button variant="outline-info">🔗 KB 연결됨 (#{incident.kbArticleId})</Button>
                </Link>
                ) : (
                <Button variant="secondary" disabled>KB 없음</Button>
                )}

                <Button variant="warning" onClick={unignore} disabled={!canUnignore}>
                    UNIGNORE (To OPEN)
                </Button>

                <Button variant="dark" onClick={reopen} disabled={!canReopen}>
                    REOPEN
                </Button>
            </div>

            {/* AI 분석 결과 표시 */}
            {aiResult && (
            <Alert variant="info" className="mt-4 mb-0">
                <div className="d-flex justify-content-between align-items-start">
                    <h5 className="alert-heading">🤖 AI 분석 결과</h5>
                    <Button variant="close" onClick={() => setAiResult(null)} aria-label="Close" />
                </div>
                <hr />
                <p><strong>Cause:</strong> {aiResult.cause ?? '-'}</p>
                <p className="mb-0"><strong>Suggestion:</strong> {aiResult.suggestion ?? '-'}</p>
            </Alert>
            )}
        </Card.Body>
    </Card>

    {/* updateDetails 검증용 패널 */}
    <Card border="warning" className="shadow-sm">
        <Card.Header className="bg-warning bg-opacity-10 text-dark">
            <strong>🔧 updateDetails (검증용)</strong>
        </Card.Header>
        <Card.Body>
            <Row className="g-2 align-items-end">
                <Col md={4}>
                <Form.Label>Incident Title</Form.Label>
                <Form.Control
                        placeholder="title"
                        value={title}
                        onChange={(e) => setTitle(e.target.value)}
                />
                </Col>
                <Col md={3}>
                <Form.Label>Created By</Form.Label>
                <Form.Control
                        placeholder="user/system"
                        value={createdBy}
                        onChange={(e) => setCreatedBy(e.target.value)}
                />
                </Col>
                <Col md={3}>
                <Form.Label>Status Change</Form.Label>
                <Form.Select value={status} onChange={(e) => setStatus(e.target.value)}>
                <option value="">(No Change)</option>
                <option value="OPEN">OPEN</option>
                <option value="UNDERWAY">UNDERWAY</option>
                <option value="RESOLVED">RESOLVED</option>
                <option value="IGNORED">IGNORED</option>
                <option value="CLOSED">CLOSED</option>
                </Form.Select>
                </Col>
                <Col md={2}>
                <Button variant="primary" className="w-100" onClick={updateDetails}>Update</Button>
                </Col>
            </Row>
            <div className="mt-2 d-flex justify-content-end">
                <Button variant="link" size="sm" onClick={load}>🔄 Data Reload</Button>
            </div>
        </Card.Body>
    </Card>
</Container>
);
};

export default IncidentDetailPage;