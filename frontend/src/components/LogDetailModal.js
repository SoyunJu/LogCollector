import React, { useState, useEffect } from 'react';
import { LogCollectorApi } from '../api/logCollectorApi';
import { Modal, Button, Alert, Badge, ButtonGroup } from 'react-bootstrap';

const LogDetailModal = ({ log, onClose }) => {
const [incident, setIncident] = useState(null);
const [aiResult, setAiResult] = useState(null);
const [loadingAi, setLoadingAi] = useState(false);

// KB 연동을 위한 Incident 조회 (logHash 사용)
useEffect(() => {
if (log?.logHash) {
LogCollectorApi.getIncidentByHash(log.logHash)
.then(res => setIncident(res.data))
.catch(() => setIncident(null));
}
}, [log]);

const handleStatusChange = async (newStatus) => {
if (!window.confirm(`상태를 ${newStatus}로 변경하시겠습니까?`)) return;
try {
await LogCollectorApi.updateLogStatus(log.logId, newStatus);
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
// [수정] 백엔드 규격에 맞춰 logHash 사용
const res = await LogCollectorApi.analyzeAi(log.logHash);
setAiResult(res.data);
} catch (err) {
const errMsg = err.response?.data?.message || err.message || "시스템 오류";
alert("AI 분석 실패: " + errMsg);
} finally {
setLoadingAi(false);
}
};

return (
<Modal show={true} onHide={onClose} size="lg" centered>
    <Modal.Header closeButton>
        <Modal.Title>🔍 로그 상세 분석</Modal.Title>
    </Modal.Header>
    <Modal.Body>
        <div className="d-flex justify-content-between mb-3">
            <div>
                <Badge bg="dark" className="me-2">{log.serviceName}</Badge>
                <Badge bg={log.status === 'RESOLVED' ? 'success' : 'danger'}>{log.status}</Badge>
            </div>
            <small className="text-muted">{log.occurredTime ? new Date(log.occurredTime).toLocaleString() : ''}</small>
        </div>

        <h6>Summary / Message</h6>
        <div className="p-3 bg-light border rounded mb-3">
            {/* [수정] message가 없으면 summary 표시 */}
            {log.summary || log.message || "No content"}
        </div>

        <h6>Stack Trace</h6>
        <div className="p-3 bg-light border rounded mb-3 font-monospace small" style={{maxHeight: '200px', overflowY: 'auto'}}>
        {log.stackTrace || "(No stack trace available)"}
        </div>

        {/* AI 분석 결과 표시 영역 */}
        {aiResult && (
        <Alert variant="info" className="mt-3">
            <h6>🤖 AI 분석 결과</h6>
            <hr />
            <p><strong>원인:</strong> {aiResult.cause}</p>
            <p><strong>조치:</strong> {aiResult.suggestion}</p>
        </Alert>
        )}
    </Modal.Body>
    <Modal.Footer className="d-flex justify-content-between align-items-center">
        <ButtonGroup>
            <Button variant="outline-danger" size="sm" onClick={() => handleStatusChange('NEW')} disabled={log.status === 'NEW'}>NEW</Button>
            <Button variant="outline-warning" size="sm" onClick={() => handleStatusChange('ACKNOWLEDGED')} disabled={log.status === 'ACKNOWLEDGED'}>ACK</Button>
            <Button variant="outline-success" size="sm" onClick={() => handleStatusChange('RESOLVED')} disabled={log.status === 'RESOLVED'}>RESOLVED</Button>
        </ButtonGroup>

        <div className="d-flex gap-2">
            <Button style={{backgroundColor: '#6f42c1', color: 'white'}} onClick={handleAiAnalyze} disabled={loadingAi}>
            {loadingAi ? '분석 중...' : '🤖 AI 분석'}
            </Button>
            {incident && (
            <Button variant="primary" disabled>📝 KB 연결됨 (#{incident.id})</Button>
            )}
            <Button variant="secondary" onClick={onClose}>닫기</Button>
        </div>
    </Modal.Footer>
</Modal>
);
};

export default LogDetailModal;