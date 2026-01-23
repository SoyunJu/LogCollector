import React, { useState, useEffect } from 'react';
import { LogCollectorApi } from '../api/logCollectorApi';
import { Form, Button, Card, Row, Col, Badge, ButtonGroup, ProgressBar } from 'react-bootstrap';

const LogGenerator = () => {
const [formData, setFormData] = useState({
serviceName: 'Payment-Service',
logLevel: '',
message: '',
stackTrace: '',
hostName: 'prod-db-01'
});

// [추가] 반복 전송 및 진행률 상태
const [repeatCount, setRepeatCount] = useState(1);
const [progress, setProgress] = useState(0);
const [isSending, setIsSending] = useState(false);
const [logsSent, setLogsSent] = useState(0);

const [previewLevel, setPreviewLevel] = useState('INFO');

// 로그 레벨 추론 (Java 로직과 동일)
const inferLogLevelClient = (message) => {
if (!message || !message.trim()) return "UNKNOWN";
const upperMsg = message.toUpperCase();
if (upperMsg.includes("FATAL")) return "FATAL";
if (upperMsg.includes("CRITICAL")) return "CRITICAL";
if (upperMsg.includes("ERROR") || upperMsg.includes("EXCEPTION")) return "ERROR";
if (upperMsg.includes("WARN")) return "WARN";
return "INFO";
};

useEffect(() => {
setPreviewLevel(inferLogLevelClient(formData.message));
}, [formData.message]);

// [추가] 시나리오 프리셋
const applyPreset = (type) => {
switch (type) {
case 'DB':
setFormData({
serviceName: 'Order-Service',
logLevel: 'ERROR',
message: 'ERROR: ConnectionRefused - Database connection pool exhaustion.',
stackTrace: 'java.sql.SQLException: Connection refused at com.zaxxer.hikari.pool.HikariPool...',
hostName: 'db-master-01'
});
break;
case 'PAYMENT':
setFormData({
serviceName: 'Payment-Gateway',
logLevel: 'FATAL',
message: 'FATAL: Payment Gateway Timeout (504) - Critical failure.',
stackTrace: 'com.payment.gateway.TimeoutException: No response from provider...',
hostName: 'payment-api-02'
});
break;
case 'OOM':
setFormData({
serviceName: 'Analytics-Service',
logLevel: 'CRITICAL',
message: 'CRITICAL: java.lang.OutOfMemoryError: Java heap space',
stackTrace: 'java.lang.OutOfMemoryError: Java heap space at java.util.Arrays.copyOf...',
hostName: 'worker-node-05'
});
break;
default: break;
}
};

const handleSubmit = async (e) => {
e.preventDefault();
setIsSending(true);
setProgress(0);
setLogsSent(0);

try {
for (let i = 0; i < repeatCount; i++) {
await LogCollectorApi.collectLog(formData);
setLogsSent(prev => prev + 1);
setProgress(Math.round(((i + 1) / repeatCount) * 100));
// 너무 빠른 전송 방지 (약간의 딜레이)
await new Promise(r => setTimeout(r, 50));
}
alert(`총 ${repeatCount}건 전송 완료! 대시보드에서 집계를 확인하세요.`);
} catch (err) {
alert('전송 중 오류 발생: ' + err.message);
} finally {
setIsSending(false);
}
};

return (
<Card className="shadow-sm border-0" style={{ maxWidth: '800px', margin: '0 auto' }}>
<Card.Header className="bg-white py-3">
    <h5 className="mb-0 fw-bold">🚀 로그 생성기 (Test Log Generator)</h5>
</Card.Header>
<Card.Body>
    {/* 프리셋 버튼 */}
    <div className="mb-4">
        <small className="text-muted d-block mb-2">⚡ 빠른 테스트 시나리오 (Click to Autofill)</small>
        <ButtonGroup>
            <Button variant="outline-danger" size="sm" onClick={() => applyPreset('DB')}>🗄️ DB Connection Error</Button>
            <Button variant="outline-dark" size="sm" onClick={() => applyPreset('PAYMENT')}>💳 Payment Timeout</Button>
            <Button variant="outline-warning" size="sm" onClick={() => applyPreset('OOM')}>💥 Out Of Memory</Button>
        </ButtonGroup>
    </div>

    <Form onSubmit={handleSubmit}>
        <Row className="mb-3">
            <Col md={6}>
            <Form.Label className="fw-bold">Service Name</Form.Label>
            <Form.Control type="text" value={formData.serviceName} onChange={e => setFormData({...formData, serviceName: e.target.value})} />
            </Col>
            <Col md={6}>
            <Form.Label className="fw-bold">Log Level</Form.Label>
            <div className="d-flex align-items-center gap-2">
                <Form.Select value={formData.logLevel} onChange={e => setFormData({...formData, logLevel: e.target.value})}>
                <option value="">✨ Auto Detect</option>
                <option value="FATAL">FATAL</option>
                <option value="CRITICAL">CRITICAL</option>
                <option value="ERROR">ERROR</option>
                <option value="WARN">WARN</option>
                <option value="INFO">INFO</option>
                </Form.Select>
                {formData.logLevel === '' && <Badge bg="secondary">Predict: {previewLevel}</Badge>}
            </div>
            </Col>
        </Row>

        <Row className="mb-3">
            <Col md={6}>
            <Form.Label>Host Name</Form.Label>
            <Form.Control type="text" value={formData.hostName} onChange={e => setFormData({...formData, hostName: e.target.value})} />
            </Col>
            <Col md={6}>
            {/* [추가] 반복 횟수 입력 */}
            <Form.Label className="fw-bold text-primary">Repeat Count (Load Test)</Form.Label>
            <Form.Control type="number" min="1" max="100" value={repeatCount} onChange={e => setRepeatCount(parseInt(e.target.value))} />
            </Col>
        </Row>

        <Form.Group className="mb-3">
            <Form.Label className="fw-bold">Message</Form.Label>
            <Form.Control as="textarea" rows={2} value={formData.message} onChange={e => setFormData({...formData, message: e.target.value})} />
        </Form.Group>

        <Form.Group className="mb-3">
            <Form.Label>Stack Trace</Form.Label>
            <Form.Control as="textarea" rows={3} className="font-monospace small bg-light" value={formData.stackTrace} onChange={e => setFormData({...formData, stackTrace: e.target.value})} />
        </Form.Group>

        {isSending && (
        <div className="mb-3">
            <ProgressBar now={progress} label={`${progress}%`} animated variant="success" />
            <div className="text-center small mt-1">{logsSent} / {repeatCount} sent</div>
        </div>
        )}

        <Button variant="dark" type="submit" className="w-100" disabled={isSending}>
            {isSending ? 'Sending...' : `Send Log (x${repeatCount})`}
        </Button>
    </Form>
</Card.Body>
</Card>
);
};

export default LogGenerator;