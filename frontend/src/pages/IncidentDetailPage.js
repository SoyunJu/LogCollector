import React, { useEffect, useMemo, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { LogCollectorApi } from '../api/logCollectorApi';
import {
Container,
Card,
Badge,
Button,
Row,
Col,
Spinner,
Alert,
Form,
Collapse,
ListGroup,
Table,
} from 'react-bootstrap';
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

// stackTrace 접기/펼치기
const [showStack, setShowStack] = useState(true); // UX: 상세 페이지 진입 시 보통 에러 확인이 주 목적이므로 펼쳐둠 (취향에 따라 false 변경 가능)

// 분석 UX: 1) KB/Addendum 확인 단계 -> 2) AI 분석 단계
const [kbChecked, setKbChecked] = useState(false);
const [kbInfoLoading, setKbInfoLoading] = useState(false);
const [kbArticleId, setKbArticleId] = useState(null);
const [addendumsTop3, setAddendumsTop3] = useState([]);
const [aiEnabled, setAiEnabled] = useState(false);

const hasStackTrace = useMemo(() => {
return !!(incident?.stackTrace && String(incident.stackTrace).trim());
}, [incident]);

// "연결된 KB id" 추정
const inferredKbArticleId = useMemo(() => {
if (!incident) return null;
return (
incident.kbArticleId ??
incident.kbId ??
incident.linkedKbId ??
incident.kb_article_id ??
null
);
}, [incident]);

const load = async () => {
setLoading(true);
try {
const res = await LogCollectorApi.getIncidentByLogHash(logHash);
setIncident(res.data);

// 초기값 세팅
if (!title) setTitle(res.data?.incidentTitle ?? '');
if (!createdBy) setCreatedBy(res.data?.createdBy ?? '');
setStatus(''); // Select Box 초기화 (변경시에만 값 할당)

// 화면 재진입 시 상태 초기화
setAiResult(null);
setKbChecked(false);
setKbInfoLoading(false);
setKbArticleId(null);
setAddendumsTop3([]);
setAiEnabled(false);
} catch (e) {
console.error(e);
alert('데이터 로드 실패: ' + (e.response?.data?.message || e.message));
} finally {
setLoading(false);
}
};

useEffect(() => {
load();
// eslint-disable-next-line react-hooks/exhaustive-deps
}, [logHash]);

// Draft 수동 생성
const createDraft = async () => {
if (!window.confirm('이 Incident를 기반으로 KB Draft를 생성하시겠습니까?')) return;
try {
if (!incident || !incident.id) {
alert('Incident 정보가 로드되지 않았습니다.');
return;
}
const res = await LogCollectorApi.createDraft(incident.id);
alert(`Draft 생성 완료! ID: ${res.data}`);
navigate(`/kb/${res.data}`);
} catch (e) {
alert('Draft 생성 실패: ' + (e.response?.data?.message || e.message));
}
};

// 상세 정보 업데이트
const updateDetails = async () => {
if (!window.confirm('입력한 정보로 Incident를 업데이트하시겠습니까?')) return;
try {
await LogCollectorApi.updateIncidentDetails(logHash, {
title: title || null,
createdBy: createdBy || null,
status: status || null,
});
alert('업데이트 되었습니다.');
load();
} catch (e) {
alert('업데이트 실패: ' + (e.response?.data?.message || e.message));
}
};

// (1단계) KB/Addendum 확인
const checkKbAndAddendums = async () => {
if (!incident) return;

setKbInfoLoading(true);
try {
const kbId = inferredKbArticleId;
setKbArticleId(kbId ?? null);

if (!kbId) {
setKbChecked(true);
setAiEnabled(true);
setAddendumsTop3([]);
return;
}

// [NOTE] 추후 LogCollectorApi로 이동 권장
const resp = await fetch(`/api/kb/articles/${kbId}/addendums`, {
method: 'GET',
headers: { 'Content-Type': 'application/json' },
});

if (!resp.ok) {
setKbChecked(true);
setAiEnabled(true);
setAddendumsTop3([]);
return;
}

const list = await resp.json();
const top3 = Array.isArray(list) ? list.slice(0, 3) : [];
setAddendumsTop3(top3);

setKbChecked(true);
setAiEnabled(true);
} catch (e) {
console.error(e);
setKbChecked(true);
setAiEnabled(true);
setAddendumsTop3([]);
} finally {
setKbInfoLoading(false);
}
};

const runAiAnalyze = async (force = false) => {
if (!incident) return;
if (!aiEnabled) {
alert('KB 확인 후에 AI 분석을 실행할 수 있습니다.');
return;
}
if (force && !window.confirm('기존 분석 결과를 덮어쓰고 다시 AI 분석을 수행하시겠습니까?')) return;

setLoadingAi(true);
try {
const res = await LogCollectorApi.analyzeAi(logHash, force);
setAiResult(res.data);
if (force) alert('재분석이 완료되었습니다.');
} catch (err) {
alert('AI 분석 실패: ' + (err?.response?.data?.message ?? err?.message));
} finally {
setLoadingAi(false);
}
};

const openLinkedKb = () => {
const kbId = kbArticleId ?? inferredKbArticleId ?? aiResult?.kbId ?? null;
if (!kbId) {
alert('연결된 KB가 없습니다.');
return;
}
navigate(`/kb/${kbId}`);
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

// --- Render Helpers ---

if (loading && !incident) {
return (
<Container className="text-center py-5">
    <Spinner animation="border" variant="primary" />
    <div className="mt-2 text-muted">Loading incident details...</div>
</Container>
);
}

if (!incident) {
return (
<Container className="py-4">
    <Alert variant="danger">Incident Not Found: {logHash}</Alert>
    <Button variant="outline-secondary" onClick={() => navigate(-1)}>Go Back</Button>
</Container>
);
}

const canOpenKb = !!(kbArticleId ?? inferredKbArticleId ?? aiResult?.kbId);

return (
<Container fluid="lg" className="py-4">
    {/* 1. Header Section */}
    <div className="d-flex justify-content-between align-items-center mb-4 border-bottom pb-3">
        <div>
            <Button variant="link" className="p-0 text-decoration-none text-muted mb-1" onClick={() => navigate(-1)}>
            &larr; Back to List
            </Button>
            <div className="d-flex align-items-center gap-3">
                <h3 className="m-0 fw-bold">Incident Detail</h3>
                {getStatusBadge(incident.status)}
            </div>
            <div className="text-muted small mt-1">Hash: <code>{incident.logHash}</code></div>
        </div>
        <div className="d-flex gap-2">
            {/* KB Action Buttons */}
            {canOpenKb && (
            <Button variant="outline-success" onClick={openLinkedKb}>
                Open KB Article
            </Button>
            )}
            <Button
                    variant="success"
                    onClick={createDraft}
                    disabled={incident.status === 'RESOLVED' || incident.status === 'CLOSED'}
            >
            Create KB Draft
            </Button>
        </div>
    </div>

    <Row>
        {/* 2. Left Column: Main Content (Summary, StackTrace, Analysis) */}
        <Col md={8}>

        {/* Summary Section */}
        <Card className="shadow-sm mb-4">
            <Card.Header className="bg-white fw-bold py-3">
                Error Summary
            </Card.Header>
            <Card.Body>
                <h5 className="text-break fw-normal mb-3" style={{ lineHeight: '1.6' }}>
                {incident.summary}
                </h5>

                <div className="d-flex align-items-center justify-content-between mb-2">
                    <span className="fw-bold text-muted small">Stack Trace</span>
                    <Button
                            variant="outline-secondary"
                            size="sm"
                            onClick={() => setShowStack((v) => !v)}
                    disabled={!hasStackTrace}
                    aria-controls="stacktrace-collapse"
                    aria-expanded={showStack}
                    >
                    {showStack ? 'Hide' : 'Show'}
                    </Button>
                </div>

                {!hasStackTrace && <Alert variant="light" className="text-center text-muted">No stack trace available.</Alert>}

                <Collapse in={showStack}>
                    <div id="stacktrace-collapse">
                  <pre
                          className="p-3 rounded border mb-0"
                          style={{
                          backgroundColor: '#f8f9fa',
                        fontSize: '0.85rem',
                        whiteSpace: 'pre-wrap',
                        wordBreak: 'break-all',
                        maxHeight: '500px',
                        overflowY: 'auto',
                        color: '#212529'
                        }}
                        >
                        {incident.stackTrace}
                        </pre>
                    </div>
                </Collapse>
            </Card.Body>
        </Card>

        {/* AI Analysis Section */}
        <Card className="shadow-sm border-0 mb-4" style={{ backgroundColor: '#f0f4f8' }}>
        <Card.Body>
            <div className="d-flex justify-content-between align-items-center mb-3">
                <h5 className="m-0 text-primary">
                    <span role="img" aria-label="robot">🤖</span> AI Root Cause Analysis
                </h5>
                <div className="d-flex gap-2">
                    {!kbChecked && !kbInfoLoading && (
                    <Button variant="primary" size="sm" onClick={checkKbAndAddendums}>
                        Step 1: Check Knowledge Base
                    </Button>
                    )}
                    {kbInfoLoading && <Spinner size="sm" animation="border" variant="primary" />}

                    {kbChecked && (
                    <Button
                            variant={aiResult ? "outline-primary" : "primary"}
                    size="sm"
                    onClick={() => runAiAnalyze(!!aiResult)} // 결과 있으면 force=true
                    disabled={loadingAi}
                    >
                    {loadingAi ? <Spinner as="span" animation="border" size="sm" /> : (aiResult ? 'Re-Analyze' : 'Step 2: Run AI Analysis')}
                    </Button>
                    )}
                </div>
            </div>

            {/* Step 1 Result: Addendums */}
            {kbChecked && (
            <div className="mb-3 animate__animated animate__fadeIn">
                {kbArticleId || inferredKbArticleId ? (
                addendumsTop3.length > 0 ? (
                <Alert variant="info" className="mb-3">
                    <strong>[Knowledge Base Found]</strong> 기존 해결 이력(Addendum)이 존재합니다. AI 분석 전 참고하세요.
                    <ListGroup className="mt-2 bg-white rounded">
                        {addendumsTop3.map((a, idx) => (
                        <ListGroup.Item key={a.id ?? idx} className="border-0 border-bottom">
                            <div className="fw-semibold small text-truncate">
                                {a.title ?? a.summary ?? `Addendum #${idx + 1}`}
                            </div>
                            <div className="text-muted small text-truncate">
                                {String(a.content).slice(0, 100)}
                            </div>
                        </ListGroup.Item>
                        ))}
                    </ListGroup>
                </Alert>
                ) : (
                <Alert variant="secondary" className="small">연결된 KB가 있지만, 추가 코멘트(Addendum)는 없습니다.</Alert>
                )
                ) : (
                <Alert variant="secondary" className="small">연결된 KB가 없습니다. 바로 AI 분석을 진행하세요.</Alert>
                )}
            </div>
            )}

            {/* Step 2 Result: AI Analysis */}
            {aiResult && (
            <div className="bg-white p-3 rounded border shadow-sm animate__animated animate__fadeInUp">
                <h6 className="fw-bold text-danger">Suspected Cause</h6>
                <p className="text-muted mb-3 small" style={{ whiteSpace: 'pre-wrap' }}>{aiResult.cause}</p>

                <h6 className="fw-bold text-success">Suggested Solution</h6>
                <p className="text-muted mb-0 small" style={{ whiteSpace: 'pre-wrap' }}>{aiResult.suggestion}</p>
            </div>
            )}
        </Card.Body>
        </Card>
        </Col>

        {/* 3. Right Column: Sidebar (Metadata & Management) */}
        <Col md={4}>

        {/* Management Card */}
        <Card className="shadow-sm mb-3">
            <Card.Header className="bg-light fw-bold">Management</Card.Header>
            <Card.Body>
                <Form>
                    <Form.Group className="mb-3">
                        <Form.Label className="small text-muted">Status</Form.Label>
                        <Form.Select
                                value={status || incident.status}
                                onChange={(e) => setStatus(e.target.value)}
                        size="sm"
                        >
                        <option value="OPEN">OPEN</option>
                        <option value="UNDERWAY">UNDERWAY</option>
                        <option value="RESOLVED">RESOLVED</option>
                        <option value="IGNORED">IGNORED</option>
                        <option value="CLOSED">CLOSED</option>
                        </Form.Select>
                    </Form.Group>

                    <Form.Group className="mb-3">
                        <Form.Label className="small text-muted">Assignee (CreatedBy)</Form.Label>
                        <Form.Control
                                size="sm"
                                value={createdBy}
                                onChange={(e) => setCreatedBy(e.target.value)}
                        placeholder="user or system"
                        />
                    </Form.Group>

                    <Form.Group className="mb-3">
                        <Form.Label className="small text-muted">Title (Display Name)</Form.Label>
                        <Form.Control
                                size="sm"
                                value={title}
                                onChange={(e) => setTitle(e.target.value)}
                        placeholder="Set custom title..."
                        />
                    </Form.Group>

                    <Button variant="primary" size="sm" className="w-100" onClick={updateDetails}>
                        Update Details
                    </Button>
                </Form>
            </Card.Body>
        </Card>

        {/* Metadata Card */}
        <Card className="shadow-sm mb-3">
            <Card.Header className="bg-light fw-bold">Metadata</Card.Header>
            <Table responsive className="mb-0 small" borderless>
                <tbody>
                <tr>
                    <td className="text-muted">Service</td>
                    <td className="fw-semibold text-end">{incident.serviceName}</td>
                </tr>
                <tr>
                    <td className="text-muted">Error Level</td>
                    <td className="text-end">
                        <Badge bg="warning" text="dark">{incident.errorLevel}</Badge>
                    </td>
                </tr>
                <tr>
                    <td className="text-muted">Count</td>
                    <td className="text-end fw-bold">{incident.repeatCount}</td>
                </tr>
                <tr>
                    <td className="text-muted">First Seen</td>
                    <td className="text-end">{formatKst(incident.firstOccurredAt)}</td>
                </tr>
                <tr>
                    <td className="text-muted">Last Seen</td>
                    <td className="text-end">{formatKst(incident.lastOccurredAt)}</td>
                </tr>
                </tbody>
            </Table>
        </Card>

        </Col>
    </Row>
</Container>
);
};

export default IncidentDetailPage;