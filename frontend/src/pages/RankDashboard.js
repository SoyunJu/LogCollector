import React, { useEffect, useState } from 'react';
import { LogCollectorApi } from '../api/logCollectorApi';
import { Container, Card, Row, Col, ProgressBar, Spinner, Alert, Badge, Form } from 'react-bootstrap';
// [수정] date.js 유틸 활용 import (경로 확인 필요)
import { toIsoKst } from '../utils/date';

const RankDashboard = () => {
const [loading, setLoading] = useState(false);

// 3가지 뷰를 위한 데이터 상태
const [openTop, setOpenTop] = useState([]);
const [recentTop, setRecentTop] = useState([]);
const [totalTop, setTotalTop] = useState([]);

const [serviceName, setServiceName] = useState('');

// [수정] date.js의 toIsoKst를 활용한 Date 객체 포맷팅
const formatDateForApi = (date) => {
if (!date) return null;
// date.js의 toIsoKst(y, mo, d, h, mi, s, ms) 활용
// 주의: getMonth()는 0부터 시작하므로 +1 필요
return toIsoKst(
date.getFullYear(),
date.getMonth() + 1,
date.getDate(),
date.getHours(),
date.getMinutes(),
date.getSeconds(),
0 // ms는 0 처리
);
};

const loadAll = async () => {
setLoading(true);
try {
// 1. Critical Issues (Status=OPEN)
const resOpen = await LogCollectorApi.getIncidentTop({
metric: 'repeatCount',
limit: 5,
status: 'OPEN',
serviceName: serviceName || null
});
setOpenTop(resOpen.data || []);

// 2. Recent Trends (최근 7일)
const sevenDaysAgo = new Date();
sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);

// [수정] 날짜 포맷팅 적용
const resRecent = await LogCollectorApi.getIncidentTop({
metric: 'repeatCount',
limit: 5,
from: formatDateForApi(sevenDaysAgo),
serviceName: serviceName || null
});
setRecentTop(resRecent.data || []);

// 3. All Time High (전체)
const resTotal = await LogCollectorApi.getIncidentTop({
metric: 'repeatCount',
limit: 5,
serviceName: serviceName || null
});
setTotalTop(resTotal.data || []);

} catch (e) {
console.error("Rank Load Error:", e);
} finally {
setLoading(false);
}
};

useEffect(() => { loadAll(); }, [serviceName]);

// 카드 렌더링 헬퍼 (기존 디자인 유지)
const renderRankCard = (title, data, variant, icon) => {
const maxCount = data.length > 0 ? Math.max(...data.map(i => i.repeatCount || i.count || 0)) : 1;

return (
<Card className="shadow-sm h-100 border-0 bg-white">
    <Card.Header className="bg-white fw-bold border-bottom-0 pt-3">
        <span className="me-2">{icon}</span> {title}
    </Card.Header>
    <Card.Body>
        {data.length === 0 ? <Alert variant="light" className="text-center text-muted small">No Data</Alert> : (
        <div className="d-flex flex-column gap-3">
            {data.map((item, idx) => {
            const count = item.repeatCount || item.count || 0;
            const percent = (count / maxCount) * 100;
            return (
            <div key={idx}>
                <div className="d-flex justify-content-between mb-1 small">
                    <div className="text-truncate" style={{maxWidth: '75%'}}>
                    <Badge bg={variant} className="me-2 rounded-pill">#{idx + 1}</Badge>
                    <span className="fw-bold text-dark me-1">{item.serviceName}</span>
                    <span className="text-muted text-truncate">{item.title || item.incidentTitle || item.logSummary}</span>
                </div>
                <strong className="text-dark">{count.toLocaleString()}</strong>
            </div>
            <ProgressBar
                    now={percent}
                    variant={variant}
                    style={{ height: '6px' }}
            className="opacity-75"
            />
        </div>
        );
        })}
        </div>
        )}
    </Card.Body>
</Card>
);
};

return (
<Container className="page py-4">
    <div className="d-flex justify-content-between align-items-center mb-4">
        <h3 className="m-0 fw-bold">📊 Analytics Dashboard</h3>
        <Form.Control
                size="sm"
                type="text"
                placeholder="Filter by Service..."
                style={{width: '200px'}}
        value={serviceName}
        onChange={(e) => setServiceName(e.target.value)}
        />
    </div>

    {loading && <div className="text-center py-5"><Spinner animation="border" variant="primary"/></div>}

    {!loading && (
    <Row className="g-4">
        <Col lg={4} md={12}>{renderRankCard("Critical Issues (OPEN)", openTop, "danger", "🔥")}</Col>
        <Col lg={4} md={12}>{renderRankCard("Recent Trends (7d)", recentTop, "primary", "📈")}</Col>
        <Col lg={4} md={12}>{renderRankCard("All-Time Frequent", totalTop, "secondary", "🏆")}</Col>
    </Row>
    )}
</Container>
);
};

export default RankDashboard;