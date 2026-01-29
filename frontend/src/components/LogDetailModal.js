import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { LogCollectorApi } from '../api/logCollectorApi';
import { Modal, Button, Badge, Table } from 'react-bootstrap';
import { formatKst } from '../utils/date';

const LogDetailModal = ({ log, onClose }) => {
const navigate = useNavigate();
const [incident, setIncident] = useState(null);

useEffect(() => {
if (log?.logHash) {
LogCollectorApi.getIncidentByLogHash(log.logHash)
.then((res) => setIncident(res.data))
.catch(() => setIncident(null));
}
}, [log]);

const goToIncident = () => {
onClose(); // 모달 닫기
if (log.logHash) {
navigate(`/incidents/${log.logHash}`);
}
};

return (
<Modal show={true} onHide={onClose} size="lg" centered>
    <Modal.Header closeButton>
        <Modal.Title>Log Details</Modal.Title>
    </Modal.Header>

    <Modal.Body>
        {/* 기본 정보 테이블 */}
        <Table borderless size="sm" className="mb-3">
            <tbody>
            <tr>
                <td className="text-muted" style={{ width: '100px' }}>Service</td>
                <td className="fw-bold text-primary">{log.serviceName}</td>
            </tr>
            <tr>
                <td className="text-muted">Level</td>
                <td>
                    <Badge bg={log.logLevel === 'ERROR' ? 'danger' : 'info'}>
                    {log.logLevel}
                    </Badge>
                </td>
            </tr>
            <tr>
                <td className="text-muted">Occurred</td>
                <td>{formatKst(log.occurredTime ?? log.createdAt)}</td>
            </tr>
            <tr>
                <td className="text-muted">Log Hash</td>
                <td><code>{log.logHash}</code></td>
            </tr>
            {/* Incident 연결 정보가 있을 때만 표시 */}
            {incident && (
            <tr className="table-warning">
                <td className="text-muted align-middle">Incident</td>
                <td className="align-middle">
                  <span className="me-2 fw-semibold">
                    {incident.incidentTitle ?? incident.summary}
                  </span>
                    <Badge bg="warning" text="dark" className="small">Linked</Badge>
                </td>
            </tr>
            )}
            </tbody>
        </Table>

        <hr />

        <h6 className="fw-bold text-secondary">Message</h6>
        <div className="p-3 bg-light border rounded mb-3 text-break" style={{ maxHeight: '150px', overflowY: 'auto' }}>
        {log.summary || log.message || "(No message)"}
        </div>

        {log.stackTrace && (
        <>
        <h6 className="fw-bold text-secondary">Stack Trace</h6>
        <div
                className="p-3 bg-dark text-light border rounded mb-0 font-monospace small"
                style={{ maxHeight: '350px', overflowY: 'auto', whiteSpace: 'pre-wrap' }}
        >
        {log.stackTrace}
        </div>
    </>
    )}
    </Modal.Body>

    <Modal.Footer>
        <Button variant="secondary" onClick={onClose}>
            Close
        </Button>
        {/* Incident가 존재할 경우 이동 버튼 활성화 */}
        {incident && (
        <Button variant="primary" onClick={goToIncident}>
            Go to Incident &rarr;
        </Button>
        )}
    </Modal.Footer>
</Modal>
);
};

export default LogDetailModal;