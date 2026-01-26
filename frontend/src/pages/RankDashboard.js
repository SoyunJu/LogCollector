import React, { useEffect, useState } from 'react';
import { LogCollectorApi } from '../api/logCollectorApi';
import { Container, Card, Row, Col, ProgressBar, Spinner, Alert, Badge, Form } from 'react-bootstrap';

const RankDashboard = () => {
const [loading, setLoading] = useState(false);

// 3가지 뷰를 위한 데이터 상태
const [openTop, setOpenTop] = useState([]);
const [recentTop, setRecentTop] = useState([]);
const [totalTop, setTotalTop] = useState([]);

const [serviceName, setServiceName] = useState('');

// [수정] 외부 유틸 의존성 제거 및 직접 구현 (yyyy-MM-ddTHH:mm:ss)
const toIsoStringLocal = (date) => {
if (!date) return null;
const pad = (n) => n.toString().padStart(2, '0');
const yyyy = date.getFullYear();
const MM = pad(date.getMonth() + 1);
const dd = pad(date.getDate());
const hh = pad(date.getHours());
const mm = pad(date.getMinutes());
const ss = pad(date.getSeconds());
return `${yyyy}-${MM}-${dd}T${hh}:${mm}:${ss}`;
};

const loadAll = async () => {
setLoading(true);

// [수정] 하나가 실패해도 나머지는 로딩되도록 개별 try-catch 또는 Promise.allSettled 사용
// 여기서는 가독성을 위해 개별 호출하되, 에러를 로그만 남기고 무시하여 다음 호출 진행
try {
// 1. Critical Issues (Status=OPEN)
try {
const resOpen = await LogCollectorApi.getIncidentTop({
metric: 'repeatCount',
limit: 5,
status: 'OPEN',
serviceName: serviceName || null
});
setOpenTop(resOpen.data || []);
} catch (e) {
console.error("Failed to load Critical Issues:", e);
setOpenTop([]);
}

// 2. Recent Trends (최근 7일)
try {
const sevenDaysAgo = new Date();
sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);

const resRecent = await LogCollectorApi.getIncidentTop({
metric: 'repeatCount', // 또는 'lastOccurredAt' 등 의도에 맞게
limit: 5,
from: toIsoStringLocal(sevenDaysAgo),
serviceName: serviceName || null
});
setRecentTop(resRecent.data || []);
} catch (e) {
console.error("Failed to load Recent Trends:", e);
setRecentTop([]);
}

// 3. All Time High (전체)
try {
const resTotal = await LogCollectorApi.getIncidentTop({
metric: 'repeatCount',
limit: 5,
serviceName: serviceName || null
});
setTotalTop(resTotal.data || []);
} catch (e) {
console.error("Failed to load All-Time High:", e);
setTotalTop([]);
}

} finally {
setLoading(false);
}
};

useEffect(() => { loadAll(); }, [serviceName]);

// 카드 렌더링 헬퍼
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