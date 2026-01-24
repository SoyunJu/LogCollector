import React, { useEffect, useMemo, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { LogCollectorApi } from '../api/logCollectorApi';
import { formatKst } from '../utils/date';
import { Container, Card, Badge, Button, Form, Row, Col, Spinner, ListGroup, InputGroup } from 'react-bootstrap';

const KbDetailPage = () => {
const params = useParams();
const navigate = useNavigate();
const kbArticleId = useMemo(() => params.kbArticleId, [params.kbArticleId]);

const [kb, setKb] = useState(null);
const [loading, setLoading] = useState(false);

// Editor State
const [title, setTitle] = useState('');
const [content, setContent] = useState(''); // Read-only System Log
const [createdBy, setCreatedBy] = useState('user');

// Addendum State
const [addendumList, setAddendumList] = useState([]);
const [newAddendum, setNewAddendum] = useState('');
const [addingComment, setAddingComment] = useState(false);

const load = async () => {
setLoading(true);
try {
const res = await LogCollectorApi.getKbDetail(kbArticleId, { addendumPage: 0, addendumSize: 100 });
setKb(res.data);
setAddendumList(res.data.addendums || []);

// 초기 로딩 시 데이터 세팅
if (!title) setTitle(res.data?.incidentTitle ?? '');
// Content는 업데이트 대상이 아님 (Display Only)
setContent(res.data?.content ?? '');
} finally {
setLoading(false);
}
};

useEffect(() => { load(); }, [kbArticleId]);

// 1. Title만 업데이트
const saveTitle = async () => {
try {
await LogCollectorApi.updateDraft(kbArticleId, { title, createdBy }); // API가 title만 처리하도록 백엔드 수정됨
alert('제목이 저장되었습니다.');
load();
} catch (e) {
alert('저장 실패: ' + e.message);
}
};

// 2. Addendum(댓글) 작성
const postAddendum = async () => {
if(!newAddendum.trim()) return;
setAddingComment(true);
try {
// postKbArticle 혹은 createAddendum API 호출
await LogCollectorApi.postKbArticle(kbArticleId, {
title: title, // 기존 타이틀 유지
content: newAddendum, // 댓글 내용
createdBy: createdBy
});
setNewAddendum('');
await load();
} catch (e) {
alert('댓글 등록 실패: ' + e.message);
} finally {
setAddingComment(false);
}
};

// 3. Publish (Close 요청)
const publishArticle = async () => {
// Validation: 댓글이 없으면 경고
if (addendumList.length === 0) {
alert('발행하려면 최소 하나의 해결 내용(Addendum)이 필요합니다.\n댓글로 해결 방법을 작성해주세요.');
return;
}

if (!window.confirm('이 문서를 발행(PUBLISHED) 하시겠습니까?\n관련 Incident가 해결 처리(Close 후보)됩니다.')) return;

try {
await LogCollectorApi.updateKbStatus(kbArticleId, 'PUBLISHED');
alert('문서가 발행되었습니다.');
load();
} catch (e) {
alert('발행 실패: ' + e.message);
}
};

if (loading && !kb) return <div className="text-center py-5"><Spinner animation="border"/></div>;
if (!kb) return <div className="p-4">KB Not Found</div>;

return (
<Container className="page py-3">
    {/* Header & Actions */}
    <div className="d-flex justify-content-between align-items-center mb-3">
        <div>
            <Button variant="link" className="p-0 text-decoration-none text-muted mb-1" onClick={() => navigate('/kb')}>&larr; KB List</Button>
            <h3 className="m-0">KB Article #{kb.id}</h3>
        </div>
        <div className="d-flex gap-2 align-items-center">
            <Badge bg={kb.status === 'PUBLISHED' ? 'success' : 'secondary'} className="fs-6">{kb.status}</Badge>

            {/* PUBLISH 버튼: PUBLISHED가 아니고 ARCHIVED가 아닐 때 노출 */}
            {kb.status !== 'PUBLISHED' && kb.status !== 'ARCHIVED' && (
            <Button variant="success" size="sm" onClick={publishArticle}>
                ✅ Publish (Close Issue)
            </Button>
            )}
        </div>
    </div>

    <Row>
        <Col lg={8}>
        {/* Title & System Log Section */}
        <Card className="shadow-sm mb-4">
            <Card.Header className="bg-white fw-bold">📄 Incident Context (Metadata)</Card.Header>
            <Card.Body>
                <Form.Group className="mb-3">
                    <Form.Label>Incident Title <small className="text-muted">(Editable)</small></Form.Label>
                    <InputGroup>
                        <Form.Control
                                value={title}
                                onChange={(e) => setTitle(e.target.value)}
                        className="fw-bold"
                        />
                        <Button variant="outline-secondary" onClick={saveTitle}>Save Title</Button>
                    </InputGroup>
                </Form.Group>

                <Form.Group className="mb-3">
                    <Form.Label>System Log / Summary <small className="text-danger">(Read-Only)</small></Form.Label>
                    <Form.Control
                            as="textarea"
                            rows={8}
                            value={content}
                            readOnly
                            className="font-monospace bg-light text-muted"
                    />
                </Form.Group>
            </Card.Body>
        </Card>

        {/* Addendum Section */}
        <div className="mb-4">
            <h5 className="mb-3">💬 Resolution Notes & Updates ({addendumList.length})</h5>

            {/* 댓글 리스트 */}
            <div className="d-flex flex-column gap-3 mb-4">
                {addendumList.length === 0 && (
                <div className="alert alert-warning">
                    아직 등록된 해결 방법이 없습니다. 발행(Publish)하려면 해결 내용을 작성해주세요.
                </div>
                )}
                {addendumList.map((a) => (
                <Card key={a.id} className="border-0 shadow-sm bg-white">
                    <Card.Body className="p-3">
                        <div className="d-flex justify-content-between mb-2">
                            <strong>{a.createdBy}</strong>
                            <small className="text-muted">{formatKst(a.createdAt)}</small>
                        </div>
                        <div style={{whiteSpace: 'pre-wrap'}} className="text-dark">
                        {a.content}
            </div>
            </Card.Body>
            </Card>
            ))}
        </div>

        {/* 댓글 입력 */}
        <Card className="border shadow-sm">
            <Card.Body>
                <Form.Label className="fw-bold">Write Resolution / Comment</Form.Label>
                <Form.Control
                        as="textarea"
                        rows={3}
                        placeholder="어떻게 해결했나요? 또는 추가 분석 내용을 입력하세요."
                        value={newAddendum}
                        onChange={(e) => setNewAddendum(e.target.value)}
                className="mb-2"
                />
                <div className="d-flex justify-content-between align-items-center">
                    <div className="d-flex align-items-center gap-2">
                        <small className="text-muted">Author:</small>
                        <Form.Select size="sm" style={{width:'100px'}} value={createdBy} onChange={(e)=>setCreatedBy(e.target.value)}>
                            <option value="user">User</option>
                            <option value="admin">Admin</option>
                        </Form.Select>
                    </div>
                    <Button variant="dark" onClick={postAddendum} disabled={addingComment || !newAddendum.trim()}>
                        {addingComment ? 'Posting...' : 'Post Resolution Note'}
                    </Button>
                </div>
            </Card.Body>
        </Card>
        </div>
        </Col>

        {/* Sidebar */}
        <Col lg={4}>
        {/* ... Existing Metadata Card ... */}
        <Card className="shadow-sm mb-3">
            <Card.Header className="bg-light fw-bold">Information</Card.Header>
            <ListGroup variant="flush">
                <ListGroup.Item>
                    <small className="text-muted d-block">Service</small>
                    {kb.serviceName || '-'}
                </ListGroup.Item>
                <ListGroup.Item>
                    <small className="text-muted d-block">Error Code</small>
                    <code>{kb.errorCode || '-'}</code>
                </ListGroup.Item>
            </ListGroup>
        </Card>
        </Col>
    </Row>
</Container>
);
};

export default KbDetailPage;