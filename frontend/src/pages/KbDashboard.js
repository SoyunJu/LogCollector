import React, { useEffect, useState } from 'react';
import { LogCollectorApi } from '../api/logCollectorApi';
import { Table, Badge, Button, Card } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';

const KbDashboard = () => {
const [articles, setArticles] = useState([]);
const navigate = useNavigate();

useEffect(() => {
// KB 목록 조회
LogCollectorApi.getKbArticles({}).then(res => setArticles(res.data.content));
}, []);

return (
<Card className="shadow-sm border-0">
    <Card.Header className="bg-white py-3">
        <h5 className="mb-0 fw-bold">📚 지식 베이스 (Knowledge Base)</h5>
    </Card.Header>
    <Table hover responsive className="mb-0 align-middle">
        <thead className="table-light">
        <tr>
            <th>ID</th>
            <th>장애 현상 (제목)</th>
            <th>상태</th>
            <th>신뢰도</th>
            <th>작성자</th>
            <th>작업</th>
        </tr>
        </thead>
        <tbody>
        {articles.length === 0 ? (
        <tr><td colSpan="6" className="text-center py-5 text-muted">등록된 기술 문서가 없습니다. 로그 상세에서 'KB 등록'을 진행해주세요.</td></tr>
        ) : articles.map(a => (
        <tr key={a.id}>
            <td>{a.id}</td>
            <td className="fw-bold">{a.incidentTitle || <span className="text-muted">(제목 없음 - 초안)</span>}</td>
            <td>
                <Badge bg={a.status === 'DEFINITE' ? 'success' : 'info'}>{a.status}</Badge>
            </td>
            <td>⭐ {a.confidenceLevel}</td>
            <td>{a.createdBy}</td>
            <td>
                <Button size="sm" variant="outline-primary" onClick={() => navigate(`/kb/${a.id}`)}>
                상세 / 수정
                </Button>
            </td>
        </tr>
        ))}
        </tbody>
    </Table>
</Card>
);
};

export default KbDashboard;