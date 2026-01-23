import React, { useState, useEffect } from 'react';
import { LogCollectorApi } from '../api/logCollectorApi';
import { Modal, Button, Alert, Badge, ButtonGroup } from 'react-bootstrap';

const LogDetailModal = ({ log, onClose }) => {
const [incident, setIncident] = useState(null);
const [aiResult, setAiResult] = useState(null);
const [loadingAi, setLoadingAi] = useState(false);

// KB 연동을 위한 Incident 조회
useEffect(() => {
if (log.logHash) {
LogCollectorApi.getIncidentByHash(log.logHash)
.then(res => setIncident(res.data))
.catch(() => setIncident(null));
}
}, [log]);

// [수정] 상태 변경 핸들러 (모든 상태 지원)
const handleStatusChange = async (newStatus) => {
if (!window.confirm(`상태를 ${newStatus}로 변경하시겠습니까?`)) return;
try {
await LogCollectorApi.updateLogStatus(log.logId, newStatus);
alert('상태가 변경되었습니다.');
onClose(); // 목록 갱신을 위해 닫기
} catch (err) {
alert('상태 변경 실패: ' + err.message);
}
};

const handleAiAnalyze = async () => {
setLoadingAi(true);
try {
const res = await LogCollectorApi.analyzeAi(log.logId);
setAiResult(res.data);
} catch (err) {
alert("분석 실패");
} finally {
setLoadingAi(false);
}
};

const handleCreateKb = async () => {
if (!incident) return;
try {
await LogCollectorApi.createKbDraft(incident.id);
alert("KB 초안이 생성되었습니다. Knowledge Base 탭에서 확인하세요.");
} catch (err) {
alert("초안 생성 실패 (이미 존재하거나 오류)");
}
};

return (
<Modal show={true} onHide={onClose} size="lg" centered>
    <Modal.Header closeButton className="bg-light">
        <Modal.Title>
            🔍 로그 상세 <Badge bg="secondary" className="ms-2">ID: {log.logId}</Badge>
        </Modal.Title>
    </Modal.Header>
    <Modal.Body>
        <div className="mb-3">
            <strong>현재 상태: </strong> <Badge bg="dark">{log.status}</Badge>
        </div>
        <div className="p-3 bg-light border rounded font-monospace small mb-3 text-break">
            {log.summary}
        </div>

        {/* AI 결과 표시 */}
        {aiResult && (
        <Alert variant="info" className="mt-3">
            <h6>🤖 AI 분석 결과</h6>
            <hr/>
            <p><strong>원인:</strong> {aiResult.cause}</p>
            <p><strong>조치:</strong> {aiResult.suggestion}</p>
        </Alert>
        )}
    </Modal.Body>
    <Modal.Footer className="d-flex justify-content-between align-items-center">
        {/* [수정] 상태 변경 버튼 그룹 */}
        <ButtonGroup>
            <Button variant="outline-danger" size="sm" onClick={() => handleStatusChange('NEW')} disabled={log.status === 'NEW'}>NEW</Button>
            <Button variant="outline-warning" size="sm" onClick={() => handleStatusChange('ACKNOWLEDGED')} disabled={log.status === 'ACKNOWLEDGED'}>ACK</Button>
            <Button variant="outline-success" size="sm" onClick={() => handleStatusChange('RESOLVED')} disabled={log.status === 'RESOLVED'}>RESOLVED</Button>
            <Button variant="outline-secondary" size="sm" onClick={() => handleStatusChange('IGNORED')} disabled={log.status === 'IGNORED'}>IGNORE</Button>
        </ButtonGroup>

        <div className="d-flex gap-2">
            <Button variant="purple" style={{backgroundColor: '#6f42c1', color: 'white'}} onClick={handleAiAnalyze} disabled={loadingAi}>
            {loadingAi ? '분석 중...' : '🤖 AI 분석'}
            </Button>
            {/* Incident가 존재할 때만 KB 등록 버튼 노출 */}
            {incident && (
            <Button variant="primary" onClick={handleCreateKb}>📝 KB 등록</Button>
            )}
            <Button variant="secondary" onClick={onClose}>닫기</Button>
        </div>
    </Modal.Footer>
</Modal>
);
};

export default LogDetailModal;