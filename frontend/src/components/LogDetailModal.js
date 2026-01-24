import React, { useState, useEffect } from 'react';
import { LogCollectorApi } from '../api/logCollectorApi';
import { Link } from 'react-router-dom'; // [추가] 링크 이동을 위해 임포트
import { Modal, Button, Alert, Badge, ButtonGroup, Spinner, Card } from 'react-bootstrap';
import { formatKst } from '../utils/date';

const LogDetailModal = ({ log, onClose }) => {
const [incident, setIncident] = useState(null);
const [aiResult, setAiResult] = useState(null);
const [loadingAi, setLoadingAi] = useState(false);

useEffect(() => {
if (log?.logHash) {
LogCollectorApi.getIncidentByHash(log.logHash)
.then(res => setIncident(res.data))
.catch(() => setIncident(null));
}
setAiResult(null);
}, [log]);

const handleStatusChange = async (newStatus) => {
if (!window.confirm(`상태를 ${newStatus}로 변경하시겠습니까?`)) return;
try {
await LogCollectorApi.updateLogStatus(log.logId ?? log.id, newStatus);
alert('상태가 변경되었습니다.');
onClose();
} catch (err) {
alert('상태 변경 실패: ' + (err.response?.data?.message || err.message));
}
};

const handleAiAnalyze = async () => {
if (!log.logHash) {
alert("로그 해시가 없어 분석할 수 없습니다.");
return;
}
setLoadingAi(true);
try {
const res = await LogCollectorApi.analyzeAi(log.logHash);
setAiResult(res.data);
} catch (err) {
const errMsg = err.response?.data?.message || err.message || "시스템 오류";
alert("AI 분석 실패: " + errMsg);
} finally {
setLoadingAi(false);
}
};

if (!log) return null;

return (
<Modal show={true} onHide={onClose} size="lg" centered>
    <Modal.Header closeButton>
        <Modal.Title className="d-flex align-items-center gap-2">
            <Badge bg="dark">{log.serviceName}</Badge>
            <span>Log Detail</span>
        </Modal.Title>
    </Modal.Header>

    <Modal.Body>
        {/* 상단 요약 정보 */}
        <div className="d-flex justify-content-between mb-3 bg-light p-2 rounded">
            <div>
                <strong>Status: </strong>
                <Badge bg={log.status === 'RESOLVED' ? 'success' : log.status === 'IGNORED' ? 'secondary' : 'danger'}>
                {log.status}
                </Badge>
            </div>
            <div className="text-muted small">
                {formatKst(log.occurredTime ?? log.createdAt)}
            </div>
        </div>

        <h6 className="fw-bold">Message</h6>
        <div className="p-3 bg-white border rounded mb-3 text-break">
            {log.summary || log.message || "(No message)"}
        </div>

        {log.stackTrace && (
        <>
        <h6 className="fw-bold">Stack Trace</h6>
        <div className="p-3 bg-dark text-light border rounded mb-3 font-monospace small" style={{maxHeight: '200px', overflowY: 'auto', whiteSpace: 'pre-wrap'}}>
        {log.stackTrace}
        </div>
    </>
    )}

    {aiResult && (
    <Alert variant="info" className="mt-3">
        <h6>🤖 AI Analysis</h6>
        <hr />
        <p><strong>Cause:</strong> {aiResult.cause}</p>
        <p><strong>Suggestion:</strong> {aiResult.suggestion}</p>
    </Alert>
    )}
    </Modal.Body>

    <Modal.Footer className="justify-content-between">
        <ButtonGroup>
            <Button variant="outline-danger" size="sm" onClick={() => handleStatusChange('NEW')} disabled={log.status === 'NEW'}>NEW</Button>
            <Button variant="outline-warning" size="sm" onClick={() => handleStatusChange('ACKNOWLEDGED')} disabled={log.status === 'ACKNOWLEDGED'}>ACK</Button>
            <Button variant="outline-success" size="sm" onClick={() => handleStatusChange('RESOLVED')} disabled={log.status === 'RESOLVED'}>FIX</Button>
        </ButtonGroup>

        <div className="d-flex gap-2">
            <Button style={{backgroundColor: '#6f42c1', borderColor: '#6f42c1'}} onClick={handleAiAnalyze} disabled={loadingAi}>
            {loadingAi ? <Spinner size="sm" animation="border"/> : '🤖 AI 분석'}
            </Button>

            {/* [수정] KB 버튼 활성화 및 Link 연결 */}
            {incident ? (
            <Button variant="primary" as={Link} to={`/kb/${incident.kbArticleId}`}>
                📝 KB 연결됨 (#{incident.kbArticleId})
            </Button>
            ) : (
            <Button variant="secondary" disabled>KB 미연동</Button>
            )}

            <Button variant="outline-secondary" onClick={onClose}>Close</Button>
        </div>
    </Modal.Footer>
</Modal>
);
};

export default LogDetailModal;