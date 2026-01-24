import React, { useEffect, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { LogCollectorApi } from '../api/logCollectorApi';
import { formatKst } from '../utils/date';
import { Container, Card, Badge, Button, Row, Col, Spinner, Alert, Form, Accordion } from 'react-bootstrap';

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
const res = await LogCollectorApi.getIncidentByLogHash(logHash);
setIncident(res.data);
// 초기값 세팅 (변경시에만 payload에 포함됨)
if (!title) setTitle(res.data?.incidentTitle ?? '');
if (!createdBy) setCreatedBy(res.data?.createdBy ?? '');
setStatus('');
} catch (e) {
console.error(e);
alert("데이터 로드 실패: " + e.message);
} finally {
setLoading(false);
}
};

useEffect(() => { load(); }, [logHash]);

// [수정] Draft 수동 생성 핸들러
const createDraft = async () => {
if (!window.confirm('이 Incident에 대한 KB 초안(Draft)을 생성하시겠습니까?')) return;
try {
// incident.id가 필요합니다. (Response DTO에 id 포함되어 있다고 가정)
await LogCollectorApi.createDraft(incident.id);
alert('초안이 생성되었습니다.');
load(); // KB ID 갱신을 위해 재로딩
} catch (e) {
alert('초안 생성 실패: ' + e.message);
}
};

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
alert('AI 분석 실패: ' + (err?.response?.data?.message ?? err?.message));
} finally {
setLoadingAi(false);
}
};

const unignore = async () => {
if (!window.confirm('IGNORED 해제(OPEN으로 변경) 하시겠습니까?')) return;
await LogCollectorApi.updateIncidentStatus(logHash, 'OPEN');
await load();
};

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
return <Container className="text-center py-5"><Spinner animation="border" variant="primary" /></Container>;
}
if (!incident) {
return <Container className="py-4"><Alert variant="danger">Incident Not Found: {logHash}</Alert></Container>;
}

const canUnignore = incident.status === 'IGNORED';
const canReopen = incident.status === 'RESOLVED' || incident.status === 'CLOSED';

return (
<Container className="page py-3">
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
            <Row className="mb-3">
                <Col md={4}><strong>Service:</strong> <span className="text-primary fw-bold">{incident.serviceName}</span></Col>
                <Col md={4}><strong>Error Code:</strong> <code>{incident.errorCode ?? '-'}</code></Col>
                <Col md={4}><strong>Repeat Count:</strong> <Badge bg="info" pill>{incident.repeatCount}</Badge></Col>
            </Row>
            <Row className="mb-3 small text-muted">
                <Col md={3}><strong>First:</strong><br/> {formatKst(incident.firstOccurredAt)}</Col>
                <Col md={3}><strong>Last:</strong><br/> {formatKst(incident.lastOccurredAt)}</Col>
                <Col md={3}><strong>Resolved:</strong><br/> {formatKst(incident.resolvedAt)}</Col>
                <Col md={3}><strong>Reopened:</strong><br/> {formatKst(incident.reopenedAt)}</Col>
            </Row>
            <div className="text-muted small mb-4">
                <strong>Assigned To:</strong> {incident.createdBy || <span className="text-warning">(Unassigned)</span>}
            </div>

            <hr />

            {/* [추가] Stack Trace Viewer */}
            <Accordion className="mb-4">
                <Accordion.Item eventKey="0">
                    <Accordion.Header>📜 Stack Trace / Log Summary</Accordion.Header>
                    <Accordion.Body className="bg-light">
                        <pre className="mb-0" style={{ fontSize: '0.85rem', whiteSpace: 'pre-wrap' }}>
                        {incident.stackTrace || incident.summary || "(No Content Available)"}
                        </pre>
                    </Accordion.Body>
                </Accordion.Item>
            </Accordion>

            <div className="d-flex flex-wrap gap-2">
                <Button variant="outline-primary" onClick={analyzeAi} disabled={loadingAi}>
                    {loadingAi ? <><Spinner size="sm" animation="border"/> Analyzing...</> : '🤖 AI Analysis'}
                </Button>

                {/* [수정] KB 연결 상태에 따른 버튼 분기 */}
                {incident.kbArticleId ? (
                <Link to={`/kb/${incident.kbArticleId}`}>
                <Button variant="outline-info">🔗 View KB (#{incident.kbArticleId})</Button>
                </Link>
                ) : (
                <Button variant="success" onClick={createDraft}>
                    ⚡ Create KB Draft
                </Button>
                )}

                <Button variant="warning" onClick={unignore} disabled={!canUnignore}>UNIGNORE</Button>
                <Button variant="dark" onClick={reopen} disabled={!canReopen}>REOPEN</Button>
            </div>

            {aiResult && (
            <Alert variant="info" className="mt-4 mb-0">
                <div className="d-flex justify-content-between align-items-start">
                    <h5 className="alert-heading">🤖 AI Insight</h5>
                    <Button variant="close" onClick={() => setAiResult(null)} />
                </div>
                <hr />
                <p><strong>Cause:</strong> {aiResult.cause ?? '-'}</p>
                <p className="mb-0"><strong>Suggestion:</strong> {aiResult.suggestion ?? '-'}</p>
            </Alert>
            )}
        </Card.Body>
    </Card>

    {/* Admin / Validation Panel */}
    <Card border="warning" className="shadow-sm">
        <Card.Header className="bg-warning bg-opacity-10 text-dark">
            <strong>🔧 Update Status / Assignee</strong>
        </Card.Header>
        <Card.Body>
            <Row className="g-2 align-items-end">
                <Col md={4}>
                <Form.Label>Incident Title</Form.Label>
                <Form.Control value={title} onChange={(e) => setTitle(e.target.value)} />
                </Col>
                <Col md={3}>
                <Form.Label>Assignee (CreatedBy)</Form.Label>
                <Form.Control value={createdBy} onChange={(e) => setCreatedBy(e.target.value)} placeholder="user/system" />
                </Col>
                <Col md={3}>
                <Form.Label>Force Status</Form.Label>
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
        </Card.Body>
    </Card>
</Container>
);
};

export default IncidentDetailPage;