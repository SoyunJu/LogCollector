import React, { useState, useEffect } from 'react';
import { LogCollectorApi } from '../api/logCollectorApi';
import { Form, Button, Card, Alert, Row, Col, Badge } from 'react-bootstrap';

const LogGenerator = () => {
const [formData, setFormData] = useState({
serviceName: 'Payment-Service',
logLevel: '', // 빈 값일 경우 Auto Detect
message: 'CRITICAL: Database connection pool exhaustion detected.',
stackTrace: '',
hostName: 'prod-db-01'
});
const [msg, setMsg] = useState(null);
const [previewLevel, setPreviewLevel] = useState('INFO');

// [요청 반영] Java의 inferLogLevel 로직과 100% 동일하게 구현
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

const handleSubmit = async (e) => {
e.preventDefault();
try {
await LogCollectorApi.collectLog(formData);
setMsg({ type: 'success', text: `[${formData.logLevel || 'AUTO'}] 로그 전송 완료!` });
} catch (err) {
setMsg({ type: 'danger', text: '전송 실패: ' + err.message });
}
};

return (
<Card className="shadow-sm border-0 mx-auto" style={{ maxWidth: '800px' }}>
<Card.Header className="bg-white fw-bold py-3">🚀 Log Generator</Card.Header>
<Card.Body>
    {msg && <Alert variant={msg.type} onClose={() => setMsg(null)} dismissible>{msg.text}</Alert>}
    <Form onSubmit={handleSubmit}>
        <Row className="mb-3">
            <Col md={6}>
            <Form.Label className="fw-bold">Service Name</Form.Label>
            <Form.Control value={formData.serviceName} onChange={e => setFormData({...formData, serviceName: e.target.value})} />
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

        <Form.Group className="mb-3">
            <Form.Label className="fw-bold">Message</Form.Label>
            <Form.Control as="textarea" rows={2} value={formData.message} onChange={e => setFormData({...formData, message: e.target.value})} />
        </Form.Group>

        <Form.Group className="mb-3">
            <Form.Label>Stack Trace</Form.Label>
            <Form.Control as="textarea" rows={3} className="font-monospace small bg-light" value={formData.stackTrace} onChange={e => setFormData({...formData, stackTrace: e.target.value})} />
        </Form.Group>

        <Button variant="dark" type="submit" className="w-100">Send Log</Button>
    </Form>
</Card.Body>
</Card>
);
};

export default LogGenerator;