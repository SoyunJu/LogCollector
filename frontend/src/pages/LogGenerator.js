import React, { useEffect, useState } from 'react';
import { LogCollectorApi } from '../api/logCollectorApi';

const inferLogLevelClient = (message) => {
if (!message || !message.trim()) return 'UNKNOWN';
const u = message.toUpperCase();
if (u.includes('FATAL')) return 'FATAL';
if (u.includes('CRITICAL')) return 'CRITICAL';
if (u.includes('ERROR') || u.includes('EXCEPTION')) return 'ERROR';
if (u.includes('WARN')) return 'WARN';
return 'INFO';
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

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const LogGenerator = () => {
const [formData, setFormData] = useState({
serviceName: 'Payment-Service',
logLevel: '', // empty => server infer
message: '',
stackTrace: '',
hostName: 'prod-db-01',
});

const [repeatCount, setRepeatCount] = useState(1);
const [isSending, setIsSending] = useState(false);
const [logsSent, setLogsSent] = useState(0);
const [progress, setProgress] = useState(0);
const [previewLevel, setPreviewLevel] = useState('INFO');
const [delayMs, setDelayMs] = useState(50);

useEffect(() => {
setPreviewLevel(inferLogLevelClient(formData.message));
}, [formData.message]);

const applyPreset = (key) => {
setFormData({ ...presets[key] });
};

const handleSubmit = async (e) => {
e.preventDefault();
setIsSending(true);
setLogsSent(0);
setProgress(0);

try {
for (let i = 0; i < repeatCount; i++) {
await LogCollectorApi.collectLog(formData);
const sent = i + 1;
setLogsSent(sent);
setProgress(Math.round((sent / repeatCount) * 100));
if (delayMs > 0) await sleep(delayMs);
}
alert(`총 ${repeatCount}건 전송 완료`);
} catch (err) {
alert('전송 오류: ' + (err?.response?.data?.message ?? err?.message ?? String(err)));
} finally {
setIsSending(false);
}
};

return (
<div className="page">
    <div className="card">
        <div className="spread">
            <h3>Log Generator</h3>
            <div className="small">POST /api/logs</div>
        </div>

        <div className="row">
            <button className="btn" type="button" onClick={() => applyPreset('DB')}>DB</button>
            <button className="btn" type="button" onClick={() => applyPreset('PAYMENT')}>PAYMENT</button>
            <button className="btn" type="button" onClick={() => applyPreset('OOM')}>OOM</button>
        </div>

        <form onSubmit={handleSubmit}>
            <div className="row">
                <input
                        className="input"
                        value={formData.serviceName}
                        placeholder="serviceName"
                        onChange={(e) => setFormData({ ...formData, serviceName: e.target.value })}
                />
                <input
                        className="input"
                        value={formData.hostName}
                        placeholder="hostName"
                        onChange={(e) => setFormData({ ...formData, hostName: e.target.value })}
                />
            </div>

            <div className="row">
                <select
                        className="select"
                        value={formData.logLevel}
                        onChange={(e) => setFormData({ ...formData, logLevel: e.target.value })}
                >
                <option value="">(Auto Detect)</option>
                <option value="FATAL">FATAL</option>
                <option value="CRITICAL">CRITICAL</option>
                <option value="ERROR">ERROR</option>
                <option value="WARN">WARN</option>
                <option value="INFO">INFO</option>
                </select>

                <div className="small">
                    Predict: {formData.logLevel ? formData.logLevel : previewLevel}
                </div>

                <input
                        className="input"
                        type="number"
                        min="1"
                        max="200"
                        value={repeatCount}
                        onChange={(e) => setRepeatCount(Number(e.target.value || 1))}
                />

                <input
                        className="input"
                        type="number"
                        min="0"
                        max="2000"
                        value={delayMs}
                        onChange={(e) => setDelayMs(Number(e.target.value || 0))}
                placeholder="delay(ms)"
                />
            </div>

            <div className="row">
            <textarea
                    className="textarea"
                    value={formData.message}
                    placeholder="message"
                    onChange={(e) => setFormData({ ...formData, message: e.target.value })}
                />
                </div>

                <div className="row">
                <textarea
                className="textarea mono"
                value={formData.stackTrace}
                placeholder="stackTrace"
                onChange={(e) => setFormData({ ...formData, stackTrace: e.target.value })}
                />
                </div>

                {isSending && (
                <div className="card">
                <div className="small">progress: {progress}%</div>
                <div className="small">{logsSent} / {repeatCount} sent</div>
                </div>
                )}

                <button className="btn primary" type="submit" disabled={isSending}>
                {isSending ? 'Sending...' : `Send x${repeatCount}`}
                </button>
                </form>
                </div>
                </div>
                );
                };

                export default LogGenerator;
