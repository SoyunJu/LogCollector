import React, { useEffect, useState } from 'react';
import { LogCollectorApi } from '../api/logCollectorApi';
import { Table, Badge, Button, Card, Form } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';

const KbDashboard = () => {
const [articles, setArticles] = useState([]);
// 검색 필터 상태
const [search, setSearch] = useState({ status: '', keyword: '' });
const navigate = useNavigate();

// [추가] Date 헬퍼 함수
const formatKst = (v) => {
if (!v) return '-';
if (v instanceof Date) {
return Number.isNaN(v.getTime()) ? '-' : v.toLocaleString('ko-KR');
}
const s = String(v).trim();
const isoLike = s.includes(' ') && !s.includes('T') ? s.replace(' ', 'T') + '+09:00' : s;
const d = new Date(isoLike);
return Number.isNaN(d.getTime()) ? '-' : d.toLocaleString('ko-KR');
};

// 목록 조회 함수
const fetchArticles = () => {
const params = {};
if (search.status) params.status = search.status;
if (search.keyword) params.keyword = search.keyword;

LogCollectorApi.getKbArticles(params).then(res => setArticles(res.data.content));
};

useEffect(() => {
fetchArticles();
// eslint-disable-next-line
}, []);

const handleSearch = (e) => {
e.preventDefault();
fetchArticles();
};

return (
<Card className="shadow-sm border-0">
    <Card.Header className="bg-white py-3 d-flex justify-content-between align-items-center">
        <h5 className="mb-0 fw-bold">📚 지식 베이스 (Knowledge Base)</h5>

        <Form onSubmit={handleSearch} className="d-flex gap-2">
            <Form.Select size="sm" value={search.status} onChange={e => setSearch(prev => ({ ...prev, status: e.target.value }))}>
            <option value="">전체 상태</option>
            <option value="OPEN">OPEN (초안)</option>
            <option value="UNDERWAY">UNDERWAY (작성중)</option>
            <option value="RESPONDED">RESPONDED (완료)</option>
            </Form.Select>
            <Form.Control
                    size="sm"
                    placeholder="제목 검색..."
                    value={search.keyword}
                    onChange={e => setSearch(prev => ({ ...prev, keyword: e.target.value }))}
            />
            <Button size="sm" variant="dark" type="submit">검색</Button>
        </Form>
    </Card.Header>
    <Table hover responsive className="mb-0 align-middle">
        <thead className="table-light">
        <tr>
            <th>ID</th>
            <th>장애 현상 (제목)</th>
            <th>상태</th>
            <th>발생일</th> {/* [추가] 발생일 컬럼 */}
            <th>신뢰도</th>
            <th>작성자</th>
            <th>작업</th>
        </tr>
        </thead>
        <tbody>
        {articles.length === 0 ? (
        <tr><td colSpan="7" className="text-center py-5 text-muted">등록된 기술 문서가 없습니다. 로그 상세에서 'KB 등록'을 진행해주세요.</td></tr>
        ) : articles.map(a => (
        <tr key={a.id}>
            <td>{a.id}</td>
            <td className="fw-bold"> {a.incidentTitle || a.title || <span className="text-muted">(제목 없음 - 초안)</span>}</td>
            <td>
                <Badge bg={a.status === 'DEFINITE' || a.status === 'RESPONDED' ? 'success' : 'info'}>{a.status}</Badge>
            </td>
            {/* [추가] 날짜 표시 (인시던트 발생일 우선, 없으면 생성일) */}
            <td>{formatKst(a.firstOccurredAt || a.createdAt)}</td>
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