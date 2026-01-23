import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { LogCollectorApi } from '../api/logCollectorApi';
import { Form, Button, Card, Spinner, Row, Col, Badge } from 'react-bootstrap';

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
createdBy: res.data.createdBy || 'user' // 기본값 소문자 주의 (백엔드 Enum 매핑 확인 필요)
});
setStatus(res.data.status);
setLoading(false);
});
}, [id]);

const handleSave = async (isPublish) => {
try {
const payload = { ...data };
if (isPublish) {
// 제목/내용 검증 등 추가 가능
await LogCollectorApi.postKbArticle(id, payload);
alert("게시 완료! (Incident 정보도 동기화되었습니다)");
} else {
await LogCollectorApi.updateKbDraft(id, payload);
alert("임시 저장 완료");
}
navigate('/kb');
} catch (err) {
alert("오류 발생: " + (err.response?.data?.message || err.message));
}
};

if (loading) return <div className="text-center p-5"><Spinner animation="border" /></div>;

return (
<Card className="shadow-sm border-0">
    <Card.Header className="bg-white d-flex justify-content-between align-items-center py-3">
        <h5 className="mb-0 fw-bold">📝 KB 상세 / 수정 (ID: {id})</h5>
        <Badge bg={status === 'DEFINITE' || status === 'RESPONDED' ? 'success' : 'warning'}>{status}</Badge>
    </Card.Header>
    <Card.Body>
        <Form>
            <Row className="mb-3">
                <Col md={8}>
                <Form.Group>
                    <Form.Label className="fw-bold">Title (Incident)</Form.Label>
                    <Form.Control
                            value={data.title}
                            onChange={e => setData({...data, title: e.target.value})}
                    placeholder="장애 현상 요약"
                    />
                </Form.Group>
                </Col>
                <Col md={4}>
                {/* [수정] 작성자(CreatedBy)를 Select Box로 변경하여 Enum 오류 방지 */}
                <Form.Group>
                    <Form.Label className="fw-bold">Author</Form.Label>
                    <Form.Select
                            value={data.createdBy}
                            onChange={e => setData({...data, createdBy: e.target.value})}
                    >
                    <option value="user">User (운영자)</option>
                    <option value="system">System (자동 생성)</option>
                    <option value="admin">Admin (관리자)</option>
                    </Form.Select>
                </Form.Group>
                </Col>
            </Row>

            <Form.Group className="mb-4">
                <Form.Label className="fw-bold">Content (Analysis & Solution)</Form.Label>
                <Form.Control
                        as="textarea"
                        rows={12}
                        value={data.content}
                        onChange={e => setData({...data, content: e.target.value})}
                className="font-monospace"
                />
            </Form.Group>

            <div className="d-flex justify-content-end gap-2">
                <Button variant="secondary" onClick={() => navigate('/kb')}>취소</Button>
                <Button variant="outline-primary" onClick={() => handleSave(false)}>임시 저장 (Draft)</Button>
                <Button variant="primary" onClick={() => handleSave(true)}>게시 (Publish)</Button>
            </div>
        </Form>
    </Card.Body>
</Card>
);
};

export default KbDetailPage;