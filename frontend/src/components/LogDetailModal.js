import React, { useState, useEffect } from 'react';
import { LogCollectorApi } from '../api/logCollectorApi';
import { Modal, Button, Alert, Badge, ButtonGroup } from 'react-bootstrap';

const LogDetailModal = ({ log, onClose }) => {
const [incident, setIncident] = useState(null);
const [aiResult, setAiResult] = useState(null);
const [loadingAi, setLoadingAi] = useState(false);

// [수정] KB 연동을 위한 Incident 조회 (logHash 사용)
useEffect(() => {
if (log.logHash) {
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
alert('상태 변경 실패: ' + err.message);
}
};

// [수정] AI 분석 요청 시 logId -> logHash 사용
const handleAiAnalyze = async () => {
setLoadingAi(true);
try {
// AnalysisController는 @PathVariable String logHash를 받음
const res = await LogCollectorApi.analyzeAi(log.logHash);
setAiResult(res.data);
} catch (err) {
alert("분석 실패: " + (err.response?.data?.cause || "시스템 오류"));
} finally {
setLoadingAi(false);
}
};

// [추가] KB 등록 페이지로 이동 (Incident가 있을 경우)
const handleCreateKb = () => {
// 실제 라우팅 구현에 따라 navigate('/kb/new', { state: { incident } }) 등을 사용
alert("KB 등록 기능은 KB 페이지에서 진행해주세요.");
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
            <small className="text-muted">{new Date(log.occurredTime).toLocaleString()}</small>
        </div>

        <h6>Message</h6>
        <div className="p-3 bg-light border rounded mb-3">{log.message}</div>

        <h6>Stack Trace</h6>
        <div className="p-3 bg-light border rounded mb-3 font-monospace small" style={{maxHeight: '200px', overflowY: 'auto'}}>
        {log.stackTrace}
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
            {/* 인시던트가 존재하면 KB 등록 버튼 활성화 (정책상 필요 시) */}
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