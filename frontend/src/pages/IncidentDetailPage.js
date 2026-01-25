import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { LogCollectorApi } from '../api/logCollectorApi';
import { Container, Card, Badge, Button, Row, Col, Spinner, Alert, Form } from 'react-bootstrap';
import { formatKst } from '../utils/date';


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
// 초기값 세팅
if (!title) setTitle(res.data?.incidentTitle ?? '');
if (!createdBy) setCreatedBy(res.data?.createdBy ?? '');
setStatus('');

// AI 결과가 이미 포함되어 있다면 세팅 (백엔드 응답 구조에 따라 다름, 여기선 예시)
// if (res.data.aiAnalysis) setAiResult(res.data.aiAnalysis);
} catch (e) {
console.error(e);
alert("데이터 로드 실패: " + (e.response?.data?.message || e.message));
} finally {
setLoading(false);
}
};

useEffect(() => { load(); }, [logHash]);

// [수정] Draft 수동 생성 핸들러 (incident.id 사용)
const createDraft = async () => {
if (!window.confirm("이 Incident를 기반으로 KB Draft를 생성하시겠습니까?")) return;
try {
if (!incident || !incident.id) {
alert("Incident 정보가 로드되지 않았습니다.");
return;
}
const res = await LogCollectorApi.createDraft(incident.id);
alert(`Draft 생성 완료! ID: ${res.data}`);
navigate(`/kb/${res.data}`);
} catch (e) {
alert("Draft 생성 실패: " + (e.response?.data?.message || e.message));
}
};

// [추가] 상세 정보 업데이트 핸들러
const updateDetails = async () => {
if (!window.confirm("입력한 정보로 Incident를 업데이트하시겠습니까?")) return;
try {
await LogCollectorApi.updateIncidentDetails(logHash, {
title: title || null,
createdBy: createdBy || null,
status: status || null,
});
alert("업데이트 되었습니다.");
load();
} catch (e) {
alert("업데이트 실패: " + (e.response?.data?.message || e.message));
}
};


// [수정] AI 분석 핸들러 (force 옵션 지원)
const handleAiAnalyze = async (force = false) => {
if (!incident) return;
if (force && !window.confirm("기존 분석 결과를 덮어쓰고 다시 AI 분석을 수행하시겠습니까?")) return;

setLoadingAi(true);
try {
const res = await LogCollectorApi.analyzeAi(logHash, force);
setAiResult(res.data);
if(force) alert("재분석이 완료되었습니다.");
} catch (err) {
alert('AI 분석 실패: ' + (err?.response?.data?.message ?? err?.message));
} finally {
setLoadingAi(false);
}
};

const getStatusBadge = (s) => {
switch (s) {
case 'OPEN': return <Badge bg="danger">OPEN</Badge>;
case 'UNDERWAY': return <Badge bg="primary">UNDERWAY</Badge>;
case 'RESOLVED': return <Badge bg="success">RESOLVED</Badge>;
case 'IGNORED': return <Badge bg="secondary">IGNORED</Badge>;
case 'CLOSED': return <Badge bg="dark">CLOSED</Badge>;
default: return <Badge bg="light" text="dark">{s}</Badge>;
}
};

if (loading && !incident) {
return <Container className="text-center py-5"><Spinner animation="border" variant="primary" /></Container>;
}
if (!incident) {
return <Container className="py-4"><Alert variant="danger">Incident Not Found: {logHash}</Alert></Container>;
}

return (
<Container className="page py-3">
    <div className="d-flex justify-content-between align-items-center mb-3">
        <h2 className="m-0">Incident Detail</h2>
        <div>
            <Button variant="outline-secondary" className="me-2" onClick={() => navigate(-1)}>Back</Button>
            <Button variant="success" onClick={createDraft} disabled={incident.status === 'RESOLVED'}>
            Create KB Draft
            </Button>
        </div>
    </div>

    {/* 기본 정보 카드 */}
    <Card className="mb-3 shadow-sm">
        <Card.Header className="d-flex justify-content-between align-items-center">
            <span className="fw-bold">{incident.serviceName}</span>
            {getStatusBadge(incident.status)}
        </Card.Header>
        <Card.Body>
            <Row className="mb-2">
                <Col sm={3} className="text-muted">Log Hash</Col>
                <Col sm={9}><code>{incident.logHash}</code></Col>
            </Row>
            <Row className="mb-2">
                <Col sm={3} className="text-muted">Error Level</Col>
                <Col sm={9}><Badge bg="warning" text="dark">{incident.errorLevel}</Badge></Col>
            </Row>
            <Row className="mb-2">
                <Col sm={3} className="text-muted">Occurred</Col>
                <Col sm={9}>
                {formatKst(incident.firstOccurredAt)} ~ {formatKst(incident.lastOccurredAt)}
                <Badge bg="info" className="ms-2">x{incident.repeatCount}</Badge>
                </Col>
            </Row>
            <Row className="mb-2">
                <Col sm={3} className="text-muted">Summary</Col>
                <Col sm={9}>{incident.summary}</Col>
            </Row>
        </Card.Body>
    </Card>

    {/* AI 분석 카드 */}
    <Row className="mb-3">
        <Col>
        <Card className="shadow-sm border-info">
            <Card.Header className="bg-info bg-opacity-10 d-flex justify-content-between align-items-center">
                <strong>🤖 AI Root Cause Analysis</strong>
                <div>
                    {!aiResult && !loadingAi && (
                    <Button variant="outline-primary" size="sm" onClick={() => handleAiAnalyze(false)}>
                    Analyze Issue
                    </Button>
                    )}
                    {aiResult && !loadingAi && (
                    <Button variant="outline-secondary" size="sm" onClick={() => handleAiAnalyze(true)}>
                    Re-Analyze
                    </Button>
                    )}
                    {loadingAi && <Spinner size="sm" animation="border" variant="primary" />}
                </div>
            </Card.Header>
            <Card.Body>
                {!aiResult && !loadingAi && (
                <div className="text-center text-muted py-2">
                    AI 분석 결과가 없습니다. 버튼을 눌러 분석을 시작하세요.
                </div>
                )}
                {aiResult && (
                <>
                <h6 className="fw-bold text-danger">🚩 Suspected Cause</h6>
                <p className="bg-light p-2 rounded border">{aiResult.cause}</p>

                <h6 className="fw-bold text-success mt-3">💡 Suggested Solution</h6>
                <p className="bg-light p-2 rounded border mb-0">{aiResult.suggestion}</p>
            </>
            )}
            </Card.Body>
        </Card>
        </Col>
    </Row>

    {/* 업데이트 폼 카드 */}
    <Card className="shadow-sm border-warning">
        <Card.Header className="bg-warning bg-opacity-10 text-dark">
            <strong>🔧 Update Status / Assignee</strong>
        </Card.Header>
        <Card.Body>
            <Row className="g-2 align-items-end">
                <Col md={4}>
                <Form.Label>Incident Title</Form.Label>
                <Form.Control value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Incident 제목 수정" />
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