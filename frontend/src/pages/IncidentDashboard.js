import React, { useEffect, useState } from 'react';
import { LogCollectorApi } from '../api/logCollectorApi';
import { Table, Badge, Card, Row, Col, Modal, Button, Form, Spinner } from 'react-bootstrap';

const IncidentDashboard = () => {
const [incidents, setIncidents] = useState([]);
const [topRank, setTopRank] = useState([]);
const [selectedIncident, setSelectedIncident] = useState(null);
const [loading, setLoading] = useState(true);

const fetchData = async () => {
try {
const [listRes, rankRes] = await Promise.all([
LogCollectorApi.getIncidents({ size: 20 }),
LogCollectorApi.getIncidentTop()
]);
setIncidents(listRes.data.content);
setTopRank(rankRes.data);
} catch (err) { console.error(err); }
finally { setLoading(false); }
};

useEffect(() => { fetchData(); }, []);

// 인시던트 상태 변경 핸들러
const handleStatusChange = async (newStatus) => {
if (!selectedIncident) return;
try {
await LogCollectorApi.updateIncidentStatus(selectedIncident.id, newStatus);
alert(`상태가 ${newStatus}로 변경되었습니다.`);
setSelectedIncident(null);
fetchData();
} catch (err) {
alert("상태 변경 실패 (백엔드 Enum 확인 필요)");
}
};

if (loading) return <div className="text-center p-5"><Spinner animation="border"/></div>;

return (
<>
<Row>
    <Col md={8}>
    <Card className="shadow-sm border-0">
        <Card.Header className="bg-white fw-bold">🚩 Incident List</Card.Header>
        <Table hover responsive className="mb-0 align-middle">
            <thead className="table-light">
            <tr><th>Service</th><th>Summary</th><th>Status</th><th className="text-center">Count</th></tr>
            </thead>
            <tbody>
            {incidents.map(inc => (
            <tr key={inc.id} onClick={() => setSelectedIncident(inc)} style={{cursor: 'pointer'}}>
            <td>{inc.serviceName}</td>
            <td className="text-truncate" style={{maxWidth: '200px'}}>{inc.summary}</td>
            <td><Badge bg={inc.status === 'OPEN' ? 'danger' : 'success'}>{inc.status}</Badge></td>
            <td className="text-center">{inc.repeatCount}</td>
            </tr>
            ))}
            </tbody>
        </Table>
    </Card>
    </Col>
    <Col md={4}>
    <Card className="bg-dark text-white border-0">
        <Card.Header className="bg-transparent fw-bold">🔥 Hot Issues</Card.Header>
        <Card.Body>
            {topRank.map((t, idx) => (
            <div key={idx} className="mb-2 border-bottom border-secondary pb-1 small">
                <span className="text-warning me-2">#{idx+1}</span>
                {t.summary} ({t.repeatCount})
            </div>
            ))}
        </Card.Body>
    </Card>
    </Col>
</Row>

{/* 상세 및 상태 변경 모달 */}
{selectedIncident && (
<Modal show={true} onHide={() => setSelectedIncident(null)} centered>
<Modal.Header closeButton>
    <Modal.Title>Incident Detail #{selectedIncident.id}</Modal.Title>
</Modal.Header>
<Modal.Body>
    <p><strong>Service:</strong> {selectedIncident.serviceName}</p>
    <p><strong>Status:</strong> <Badge bg="dark">{selectedIncident.status}</Badge></p>
    <div className="p-3 bg-light border rounded mb-3">{selectedIncident.summary}</div>
    <Form.Group className="mt-3">
        <Form.Label className="fw-bold">Change Status</Form.Label>
        <div className="d-flex gap-2">
            <Button size="sm" variant="outline-danger" onClick={() => handleStatusChange('OPEN')}>OPEN</Button>
            <Button size="sm" variant="outline-warning" onClick={() => handleStatusChange('UNDERWAY')}>UNDERWAY</Button>
            <Button size="sm" variant="outline-success" onClick={() => handleStatusChange('RESOLVED')}>RESOLVED</Button>
            <Button size="sm" variant="outline-dark" onClick={() => handleStatusChange('PUBLISHED')}>PUBLISHED</Button>
        </div>
    </Form.Group>
</Modal.Body>
</Modal>
)}
</>
);
};
export default IncidentDashboard;