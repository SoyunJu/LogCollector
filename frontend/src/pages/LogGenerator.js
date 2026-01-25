import React, { useEffect, useState } from 'react';
import { LogCollectorApi } from '../api/logCollectorApi';
import {
Card,
Button,
Form,
Row,
Col,
ProgressBar,
Badge,
ListGroup,
Container,
Tabs,
Tab,
} from 'react-bootstrap';

const inferLogLevelClient = (message) => {
if (!message || !message.trim()) return 'WARN'; // 기본값 WARN
const u = message.toUpperCase();
if (u.includes('FATAL')) return 'FATAL';
if (u.includes('CRITICAL')) return 'CRITICAL';
if (u.includes('ERROR') || u.includes('EXCEPTION')) return 'ERROR';
return 'WARN';
};

const presets = {
DB: {
serviceName: 'Order-Service',
logLevel: 'ERROR',
message: 'ERROR: ConnectionRefused - Database connection pool exhaustion.',
stackTrace: 'java.sql.SQLException: Connection refused at com.zaxxer.hikari.pool.HikariPool...',
hostName: 'db-master-01',
},
PAYMENT: {
serviceName: 'Payment-Gateway',
logLevel: 'FATAL',
message: 'FATAL: Payment Gateway Timeout (504) - Critical failure.',
stackTrace: 'com.payment.gateway.TimeoutException: No response from provider...',
hostName: 'payment-api-02',
},
OOM: {
serviceName: 'Analytics-Service',
logLevel: 'CRITICAL',
message: 'CRITICAL: java.lang.OutOfMemoryError: Java heap space',
stackTrace: 'java.lang.OutOfMemoryError: Java heap space at java.util.Arrays.copyOf...',
hostName: 'worker-node-05',
},
};

const scenarios = {
DB_FAILOVER: [
{ ...presets.DB, logLevel: 'WARN', message: 'Health check: DB Latency 10ms (Normal)', count: 2, delay: 2000 },
{ ...presets.DB, logLevel: 'WARN', message: 'WARN: DB Latency spiked to 2000ms', count: 3, delay: 2000 },
{ ...presets.DB, logLevel: 'ERROR', message: 'ERROR: ConnectionRefused - Pool Exhausted', count: 10, delay: 2000 },
{ ...presets.DB, logLevel: 'WARN', message: 'System: Switchover to Secondary DB initiated.', count: 1, delay: 20000 },
{ ...presets.DB, logLevel: 'WARN', message: 'System: DB Connected (Secondary).', count: 1, delay: 2000 },
],
PAYMENT_TIMEOUT: [
{ ...presets.PAYMENT, logLevel: 'WARN', message: 'Payment Request: Order #1234 initiated', count: 1, delay: 2000 },
{ ...presets.PAYMENT, logLevel: 'WARN', message: 'WARN: PG Provider response slow (5s)', count: 2, delay: 20000 },
{ ...presets.PAYMENT, logLevel: 'FATAL', count: 5, delay: 2000 },
],
};

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const LogGenerator = () => {
const [key, setKey] = useState('basic');

const [formData, setFormData] = useState({
serviceName: 'Order-Service',
hostName: 'web-01',
logLevel: 'WARN',
message: 'Operation completed successfully.',
stackTrace: '',
});

const [repeatCount, setRepeatCount] = useState(1);
const [delayMs, setDelayMs] = useState(0);
const [isSending, setIsSending] = useState(false);
const [progress, setProgress] = useState({ sent: 0, total: 0 });

useEffect(() => {
const lvl = inferLogLevelClient(formData.message);
setFormData((prev) => ({ ...prev, logLevel: lvl }));
}, [formData.message]);

const handleBasicSubmit = async (e) => {
e.preventDefault();
setIsSending(true);
setProgress({ sent: 0, total: repeatCount });

try {
for (let i = 0; i < repeatCount; i++) {
await LogCollectorApi.collectLog(formData);
setProgress((prev) => ({ ...prev, sent: prev.sent + 1 }));
if (delayMs > 0) await sleep(delayMs);
}
alert(`✅ ${repeatCount} logs sent.`);
} catch (err) {
alert('Failed: ' + (err?.response?.data?.message || err.message));
} finally {
setIsSending(false);
}
};

const runScenario = async (scenarioName) => {
if (!window.confirm(`Run scenario '${scenarioName}'?`)) return;

const steps = scenarios[scenarioName];
const totalCount = steps.reduce((acc, cur) => acc + (cur.count || 1), 0);

const fixedDelayMs = 1000; // 시나리오 전송 간격: 1초 고정

setIsSending(true);
setProgress({ sent: 0, total: totalCount });

try {
for (const step of steps) {
const count = step.count || 1;

const logData = {
serviceName: step.serviceName,
hostName: step.hostName,
logLevel: step.logLevel,
message: step.message,
stackTrace: step.stackTrace || '',
};

for (let i = 0; i < count; i++) {
try {
await LogCollectorApi.collectLog(logData);
} catch (e) {
// 전송 실패해도 시나리오는 계속 진행
console.error('[SCENARIO] Log send failed', e);
} finally {
setProgress((prev) => ({ ...prev, sent: prev.sent + 1 }));
}

// 마지막 전송 뒤에도 1초 대기(단순화)
await sleep(fixedDelayMs);
}
}

console.log(`Scenario '${scenarioName}' completed.`);
alert(`✅ Scenario '${scenarioName}' completed.`);
} catch (err) {
console.error(err);
alert('Failed: ' + (err?.response?.data?.message || err.message));
} finally {
setIsSending(false);
}
};

const applyPreset = (presetKey) => {
setFormData((prev) => ({ ...prev, ...presets[presetKey] }));
};

return (
<Container className="page py-3">
    <h3 className="mb-4">🛠️ Log Generator</h3>

    <Tabs activeKey={key} onSelect={(k) => k && setKey(k)} className="mb-3">
    {/* Tab 1: Basic */}
    <Tab eventKey="basic" title="Basic Generator">
        <Row>
            <Col md={4} className="mb-3">
            <Card className="h-100">
                <Card.Header>Quick Presets</Card.Header>
                <ListGroup variant="flush">
                    {Object.keys(presets).map((k) => (
                    <ListGroup.Item action key={k} onClick={() => applyPreset(k)}>
                    <div className="d-flex justify-content-between align-items-center">
                        <strong>{k}</strong>
                        <Badge bg="secondary">{presets[k].logLevel}</Badge>
                    </div>
                    <small className="text-muted text-truncate d-block">{presets[k].message}</small>
                    </ListGroup.Item>
                    ))}
                </ListGroup>
            </Card>
            </Col>

            <Col md={8}>
            <Card>
                <Card.Body>
                    <Form onSubmit={handleBasicSubmit}>
                        <Row className="mb-3">
                            <Col md={6}>
                            <Form.Label>Service Name</Form.Label>
                            <Form.Control
                                    type="text"
                                    value={formData.serviceName}
                                    onChange={(e) => setFormData({ ...formData, serviceName: e.target.value })}
                            />
                            </Col>
                            <Col md={6}>
                            <Form.Label>Host Name</Form.Label>
                            <Form.Control
                                    type="text"
                                    value={formData.hostName}
                                    onChange={(e) => setFormData({ ...formData, hostName: e.target.value })}
                            />
                            </Col>
                        </Row>

                        <Form.Group className="mb-3">
                            <Form.Label>Level</Form.Label>
                            <Form.Control type="text" readOnly value={formData.logLevel} className="bg-light fw-bold" />
                            <Form.Text className="text-muted">Auto-inferred (WARN, ERROR, FATAL...)</Form.Text>
                        </Form.Group>

                        <Form.Group className="mb-3">
                            <Form.Label>Message (min 10 chars)</Form.Label>
                            <Form.Control
                                    as="textarea"
                                    rows={2}
                                    value={formData.message}
                                    onChange={(e) => setFormData({ ...formData, message: e.target.value })}
                            />
                        </Form.Group>

                        <Form.Group className="mb-3">
                            <Form.Label>Stack Trace</Form.Label>
                            <Form.Control
                                    as="textarea"
                                    rows={4}
                                    value={formData.stackTrace}
                                    onChange={(e) => setFormData({ ...formData, stackTrace: e.target.value })}
                            className="font-monospace small bg-light"
                            />
                        </Form.Group>

                        <hr />

                        <div className="d-flex align-items-end gap-3 mb-3">
                            <div style={{ width: 200 }}>
                                <Form.Label>Repeat</Form.Label>
                                <Form.Control
                                        type="number"
                                        min="1"
                                        value={repeatCount}
                                        onChange={(e) => setRepeatCount(Number(e.target.value))}
                                />
                            </div>

                            <div style={{ width: 120 }}>
                                <Form.Label>Delay(ms)</Form.Label>
                                <Form.Control
                                        type="number"
                                        min="0"
                                        value={delayMs}
                                        onChange={(e) => setDelayMs(Number(e.target.value))}
                                />
                            </div>

                            <Button variant="primary" type="submit" className="flex-grow-1" disabled={isSending}>
                                {isSending ? 'Sending...' : '🚀 Send'}
                            </Button>
                        </div>
                    </Form>
                </Card.Body>
            </Card>
            </Col>
        </Row>
    </Tab>

    {/* Tab 2: Scenario */}
    <Tab eventKey="scenario" title="🔥 Scenario Mode">
        <Card className="border-danger">
            <Card.Header className="bg-danger text-white">Simulation Scenarios</Card.Header>
            <Card.Body>
                <p>Generate a sequence of logs to simulate real-world incidents.</p>
                <Row className="g-3">
                    {Object.keys(scenarios).map((k) => (
                    <Col md={6} key={k}>
                    <Card className="h-100 shadow-sm">
                        <Card.Body>
                            <div className="d-flex justify-content-between align-items-start mb-2">
                                <h5>{k}</h5>
                                <Badge bg="dark">{scenarios[k].length} Steps</Badge>
                            </div>

                            <ListGroup variant="flush" className="small mb-3">
                                {scenarios[k].map((step, idx) => (
                                <ListGroup.Item key={idx} className="px-0 py-1 border-0">
                                    <Badge
                                            bg={step.logLevel === 'ERROR' || step.logLevel === 'FATAL' ? 'danger' : 'secondary'}
                                    className="me-2"
                                    >
                                    {step.logLevel}
                                    </Badge>
                                    x{step.count || 1}
                                </ListGroup.Item>
                                ))}
                            </ListGroup>

                            <Button
                                    variant="outline-danger"
                                    className="w-100"
                                    onClick={() => runScenario(k)}
                            disabled={isSending}
                            >
                            ▶ Run Simulation
                            </Button>
                        </Card.Body>
                    </Card>
                    </Col>
                    ))}
                </Row>
            </Card.Body>
        </Card>
    </Tab>
    </Tabs>

    {/* Progress Bar */}
    {isSending && (
    <div className="fixed-bottom p-3 bg-white border-top shadow-lg" style={{ zIndex: 1050 }}>
        <Container>
            <div className="d-flex justify-content-between mb-1">
                <strong>Sending Logs...</strong>
                <span>
                {progress.sent} / {progress.total}
              </span>
            </div>
            <ProgressBar
                    now={progress.total ? (progress.sent / progress.total) * 100 : 0}
            animated
            variant={key === 'scenario' ? 'danger' : 'primary'}
            />
        </Container>
    </div>
    )}
</Container>
);
};

export default LogGenerator;
