import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { LogCollectorApi } from '../api/logCollectorApi';
import { Form, Button, Card, Badge, Spinner, Row, Col } from 'react-bootstrap';

const KbDetailPage = () => {
const { id } = useParams();
const navigate = useNavigate();
const [data, setData] = useState({ title: '', content: '', createdBy: '' });
const [status, setStatus] = useState('');
const [loading, setLoading] = useState(true);

useEffect(() => {
LogCollectorApi.getKbDetail(id).then(res => {
setData({
title: res.data.incidentTitle || '',
content: res.data.content || '',
createdBy: res.data.createdBy || 'Unknown'
});
setStatus(res.data.status);
setLoading(false);
});
}, [id]);

const handleSave = async (isPublish) => {
try {
const payload = { ...data };
if (isPublish) {
await LogCollectorApi.publishKbArticle(id, payload);
alert("게시 완료!");
} else {
await LogCollectorApi.updateKbDraft(id, payload);
alert("임시 저장 완료");
}
navigate('/kb');
} catch (err) {
alert("오류 발생: " + err.message);
}
};

if (loading) return <Spinner animation="border" />;

return (
<Card className="shadow-sm border-0">
    <Card.Header className="bg-white d-flex justify-content-between align-items-center py-3">
        <h5 className="mb-0">📖 KB Editor <Badge bg="secondary">#{id}</Badge></h5>
        <Badge bg="info">{status}</Badge>
    </Card.Header>
    <Card.Body>
        <Form>
            <Row className="mb-3">
                <Col md={8}>
                <Form.Group>
                    <Form.Label className="fw-bold">Title (Incident)</Form.Label>
                    <Form.Control value={data.title} onChange={e => setData({...data, title: e.target.value})} />
                </Form.Group>
                </Col>
                <Col md={4}>
                {/* [추가] 작성자 수정 필드 */}
                <Form.Group>
                    <Form.Label className="fw-bold">Author</Form.Label>
                    <Form.Control value={data.createdBy} onChange={e => setData({...data, createdBy: e.target.value})} />
                </Form.Group>
                </Col>
            </Row>

            <Form.Group className="mb-4">
                <Form.Label className="fw-bold">Content (Analysis & Solution)</Form.Label>
                <Form.Control as="textarea" rows={10} value={data.content} onChange={e => setData({...data, content: e.target.value})} />
            </Form.Group>

            <div className="d-flex justify-content-end gap-2">
                <Button variant="secondary" onClick={() => navigate('/kb')}>Cancel</Button>
                <Button variant="primary" onClick={() => handleSave(false)}>Save</Button>
               {/* <Button variant="success" onClick={() => handleSave(true)}>Publish</Button> */}
            </div>
        </Form>
    </Card.Body>
</Card>
);
};

export default KbDetailPage;