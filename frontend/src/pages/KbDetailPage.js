import React, { useEffect, useMemo, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { LogCollectorApi } from '../api/logCollectorApi';
import { formatKst } from '../utils/date';
import { Container, Card, Badge, Button, Form, Row, Col, Spinner, Alert, ListGroup, InputGroup } from 'react-bootstrap';

const KbDetailPage = () => {
const params = useParams();
const navigate = useNavigate();
const kbArticleId = useMemo(() => params.kbArticleId, [params.kbArticleId]);

const [kb, setKb] = useState(null);
const [loading, setLoading] = useState(false);

// Addendum Pagination
const [addendumPage, setAddendumPage] = useState(0);
const [addendumSize, setAddendumSize] = useState(20);

// Editor State
const [title, setTitle] = useState('');
const [content, setContent] = useState('');
const [createdBy, setCreatedBy] = useState('user');

const load = async () => {
setLoading(true);
try {
const res = await LogCollectorApi.getKbDetail(kbArticleId, { addendumPage, addendumSize });
setKb(res.data);
// 초기 로딩 시 데이터 바인딩 (사용자가 수정 중이 아닐 때만 덮어쓰기 로직 필요 시 고려, 현재는 단순 덮어쓰기)
setTitle(res.data?.incidentTitle ?? '');
setContent(res.data?.content ?? '');
} catch (e) {
console.error(e);
} finally {
setLoading(false);
}
};

useEffect(() => {
load();
}, [kbArticleId, addendumPage, addendumSize]);

const saveAppend = async () => {
if (!content || !content.trim()) {
alert('내용(Content)은 필수입니다.');
return;
}

if (!window.confirm('내용을 저장(Append) 하시겠습니까?')) return;

try {
await LogCollectorApi.createKbAddendum(kbArticleId, {
content,
createdBy,
});

alert('메모가 추가되었습니다.');

// content 입력창만 비워주는 게 UX상 맞음
setContent('');

await load();
} catch (e) {
console.error(e);
alert('저장 실패: ' + (e.response?.data?.message ?? e.message));
}
};


const updateDraft = async () => {
if(!window.confirm('초안(Draft)을 업데이트 하시겠습니까?')) return;
try {
await LogCollectorApi.updateDraft(kbArticleId, { title, content, createdBy });
alert('Draft가 업데이트 되었습니다.');
await load();
} catch (e) {
alert('업데이트 실패: ' + e.message);
}
};

const getStatusBadge = (status) => {
switch(status) {
case 'PUBLISHED': return <Badge bg="success">PUBLISHED</Badge>;
case 'DRAFT': return <Badge bg="secondary">DRAFT</Badge>;
case 'ARCHIVED': return <Badge bg="dark">ARCHIVED</Badge>;
default: return <Badge bg="info">{status}</Badge>;
}
};

if (loading && !kb) {
return (
<Container className="text-center py-5">
    <Spinner animation="border" variant="primary"/>
    <p className="mt-2 text-muted">Loading KB...</p>
</Container>
);
}

if (!kb) {
return (
<Container className="py-4">
    <Alert variant="danger">데이터를 찾을 수 없습니다.</Alert>
    <Button variant="outline-secondary" onClick={() => navigate('/kb')}>목록으로</Button>
</Container>
);
}

return (
<Container className="page py-3">
    {/* 상단 헤더 */}
    <div className="mb-3 d-flex align-items-center justify-content-between">
        <div>
            <Button variant="link" className="text-decoration-none p-0 mb-1 text-muted" onClick={() => navigate('/kb')}>
            &larr; Back to List
            </Button>
            <h3 className="m-0 d-flex align-items-center gap-2">
                KB Detail <span className="text-muted">#{kb.id}</span>
                {getStatusBadge(kb.status)}
            </h3>
        </div>
    </div>

    <Row className="g-4">
        {/* 왼쪽: 메인 에디터 영역 */}
        <Col md={8}>
        <Card className="shadow-sm h-100">
            <Card.Header className="bg-white fw-bold">Article Content</Card.Header>
            <Card.Body>
                <Form.Group className="mb-3">
                    <Form.Label>Title</Form.Label>
                    <Form.Control
                            type="text"
                            placeholder="Incident Title"
                            value={title}
                            onChange={(e) => setTitle(e.target.value)}
                    />
                </Form.Group>

                <Form.Group className="mb-3">
                    <Form.Label>Content (Body)</Form.Label>
                    <Form.Control
                            as="textarea"
                            rows={10}
                            className="font-monospace"
                            value={content}
                            placeholder="Write content here..."
                            onChange={(e) => setContent(e.target.value)}
                    />
                    <Form.Text className="text-muted">
                        * 'Save / Append' adds new history, 'Update Draft' overwrites current draft.
                    </Form.Text>
                </Form.Group>

                <Row className="align-items-center">
                    <Col md={4}>
                    <InputGroup size="sm">
                        <InputGroup.Text>Author</InputGroup.Text>
                        <Form.Select value={createdBy} onChange={(e) => setCreatedBy(e.target.value)}>
                        <option value="user">User</option>
                        <option value="system">System</option>
                        <option value="admin">Admin</option>
                        </Form.Select>
                    </InputGroup>
                    </Col>
                    <Col md={8} className="d-flex justify-content-end gap-2">
                    <Button variant="outline-secondary" onClick={updateDraft} disabled={loading}>
                        Update Draft
                    </Button>
                    <Button variant="primary" onClick={saveAppend} disabled={loading}>
                        Save / Append
                    </Button>
                    </Col>
                </Row>
            </Card.Body>
        </Card>
        </Col>

        {/* 오른쪽: 메타데이터 & Addendums */}
        <Col md={4}>
        {/* 메타데이터 카드 */}
        <Card className="mb-3 shadow-sm bg-light border-0">
            <Card.Body>
                <h6 className="fw-bold text-muted mb-3">Metadata</h6>
                <ul className="list-unstyled small mb-0">
                    <li className="mb-2"><strong>Service:</strong> {kb.serviceName ?? '-'}</li>
                    <li className="mb-2"><strong>Error Code:</strong> <code>{kb.errorCode ?? '-'}</code></li>
                    <li className="mb-2"><strong>Created:</strong> <br/>{formatKst(kb.createdAt)}</li>
                    <li className="mb-2"><strong>Updated:</strong> <br/>{formatKst(kb.updatedAt)}</li>
                    <li className="mb-0"><strong>Last Activity:</strong> <br/>{formatKst(kb.lastActivityAt)}</li>
                </ul>
            </Card.Body>
        </Card>

        {/* Addendums 목록 카드 */}
        <Card className="shadow-sm">
            <Card.Header className="d-flex justify-content-between align-items-center bg-white">
                <strong>Addendums</strong>
                <Badge bg="secondary" pill>{kb.addendumTotal ?? 0}</Badge>
            </Card.Header>

            {/* 페이지네이션 컨트롤 */}
            <div className="p-2 border-bottom bg-light d-flex justify-content-between align-items-center">
                <div className="d-flex gap-1">
                    <Button size="sm" variant="outline-secondary" disabled={addendumPage === 0} onClick={() => setAddendumPage(Math.max(0, addendumPage - 1))}>&lt;</Button>
                    <Button size="sm" variant="outline-secondary" disabled={!(kb.addendumHasNext)} onClick={() => setAddendumPage(addendumPage + 1)}>&gt;</Button>
                </div>
                <Form.Select size="sm" style={{width: '70px'}} value={addendumSize} onChange={(e) => { setAddendumPage(0); setAddendumSize(Number(e.target.value)); }}>
                <option value={10}>10</option>
                <option value={20}>20</option>
                </Form.Select>
            </div>

            <ListGroup variant="flush" style={{maxHeight: '400px', overflowY: 'auto'}}>
            {(kb.addendums && kb.addendums.length > 0) ? (
            kb.addendums.map((a) => (
            <ListGroup.Item key={a.id} className="py-3">
                <div className="d-flex justify-content-between text-muted small mb-1">
                    <span>#{a.id} <strong>{a.createdBy}</strong></span>
                    <span>{formatKst(a.createdAt)}</span>
                </div>
                <div className="text-dark small text-break font-monospace bg-light p-2 rounded">
                    {a.content}
                </div>
            </ListGroup.Item>
            ))
            ) : (
            <div className="text-center py-4 text-muted small">No addendums yet.</div>
            )}
            </ListGroup>
        </Card>
        </Col>
    </Row>
</Container>
);
};

export default KbDetailPage;