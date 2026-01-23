import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { LogCollectorApi } from '../api/logCollectorApi';
import { Form, Button, Card, Spinner, Row, Col, Badge } from 'react-bootstrap';

const KbDetailPage = () => {
const { id } = useParams();
const navigate = useNavigate();

const [data, setData] = useState({ title: '', content: '', createdBy: 'user' });
const [timeInfo, setTimeInfo] = useState(''); // [추가] 시간 정보 상태
const [status, setStatus] = useState('');
const [loading, setLoading] = useState(true);
const [contentPlaceholder, setContentPlaceholder] = useState('');

// Date 헬퍼 함수
const formatKst = (v) => {
if (!v) return '';
if (v instanceof Date) return Number.isNaN(v.getTime()) ? '' : v.toLocaleString('ko-KR');
const s = String(v).trim();
const isoLike = s.includes(' ') && !s.includes('T') ? s.replace(' ', 'T') + '+09:00' : s;
const d = new Date(isoLike);
return Number.isNaN(d.getTime()) ? '' : d.toLocaleString('ko-KR');
};

useEffect(() => {
let mounted = true;

const fetchDetail = async () => {
try {
const res = await LogCollectorApi.getKbDetail(id);
if (!mounted) return;

const serverTitle = res.data?.incidentTitle || res.data?.title || '';
const serverContent = res.data?.content || '';
const serverCreatedBy = res.data?.createdBy || 'user';
const serverStatus = res.data?.status || '';

// [추가] 시간 정보 추출 (Incident 발생시간 우선, 없으면 KB 생성시간)
const serverTime = res.data?.incidentFirstOccurredAt || res.data?.firstOccurredAt || res.data?.createdAt || '';

setStatus(serverStatus);
setTimeInfo(serverTime);

// system 템플릿이면 placeholder로만 보여주기
if (
serverCreatedBy === 'system' &&
(serverStatus === 'OPEN' || serverStatus === 'UNDERWAY') &&
serverContent
) {
setContentPlaceholder(serverContent);
setData({ title: serverTitle, content: '', createdBy: serverCreatedBy });
} else {
setContentPlaceholder('');
setData({ title: serverTitle, content: serverContent, createdBy: serverCreatedBy });
}
} catch (err) {
alert('KB 상세 조회 실패: ' + (err.response?.data?.message || err.message));
} finally {
if (mounted) setLoading(false);
}
};

fetchDetail();
return () => {
mounted = false;
};
}, [id]);

const handleSaveDraft = async () => {
try {
const payload = { ...data };
await LogCollectorApi.updateKbDraft(id, payload);
alert('임시 저장 완료');
navigate('/kb');
} catch (err) {
alert('오류 발생: ' + (err.response?.data?.message || err.message));
}
};

const handleSaveResponded = async () => {
if (!data.title?.trim() || !data.content?.trim()) {
alert('RESPONDED로 전환하려면 Title과 Content가 모두 필요합니다.');
return;
}

try {
const payload = { ...data };
await LogCollectorApi.updateKbDraft(id, payload);
alert('저장 완료 (Title+Content 조건 충족 시 RESPONDED)');
navigate('/kb');
} catch (err) {
alert('오류 발생: ' + (err.response?.data?.message || err.message));
}
};

if (loading) return <div className="text-center p-5"><Spinner animation="border" /></div>;

return (
<Card className="shadow-sm border-0">
    <Card.Header className="bg-white d-flex justify-content-between align-items-center py-3">
        <h5 className="mb-0 fw-bold">📝 KB 상세 / 수정 (ID: {id})</h5>
        <Badge bg={(status === 'DEFINITE' || status === 'RESPONDED') ? 'success' : 'warning'}>
        {status}
        </Badge>
    </Card.Header>

    <Card.Body>
        <Form>
            <Row className="mb-3">
                <Col md={6}>
                <Form.Group>
                    <Form.Label className="fw-bold">Title (Incident)</Form.Label>
                    <Form.Control
                            value={data.title}
                            onChange={e => setData(prev => ({ ...prev, title: e.target.value }))}
                    placeholder="장애 현상 요약"
                    />
                </Form.Group>
                </Col>

                {/* [추가] 발생 시간 필드 */}
                <Col md={3}>
                <Form.Group>
                    <Form.Label className="fw-bold">Time (Occurred)</Form.Label>
                    <Form.Control
                            value={formatKst(timeInfo)}
                            readOnly
                            disabled
                            className="bg-light"
                    />
                </Form.Group>
                </Col>

                <Col md={3}>
                <Form.Group>
                    <Form.Label className="fw-bold">Author</Form.Label>
                    <Form.Select
                            value={data.createdBy}
                            onChange={e => setData(prev => ({ ...prev, createdBy: e.target.value }))}
                    >
                    <option value="user">user</option>
                    <option value="system">system</option>
                    <option value="admin">admin</option>
                    </Form.Select>
                </Form.Group>
                </Col>
            </Row>

            <Form.Group className="mb-4">
                <Form.Label className="fw-bold">Content</Form.Label>
                <Form.Control
                        as="textarea"
                        rows={12}
                        value={data.content}
                        placeholder={contentPlaceholder || '분석/조치 내용을 입력하세요'}
                onChange={e => setData(prev => ({ ...prev, content: e.target.value }))}
                className="font-monospace"
                />
            </Form.Group>

            <div className="d-flex justify-content-end gap-2">
                <Button variant="secondary" onClick={() => navigate('/kb')}>취소</Button>
                <Button variant="outline-primary" onClick={handleSaveDraft}>임시 저장 (UNDERWAY)</Button>
                <Button variant="primary" onClick={handleSaveResponded}>저장 후 RESPONDED</Button>
            </div>
        </Form>
    </Card.Body>
</Card>
);
};

export default KbDetailPage;