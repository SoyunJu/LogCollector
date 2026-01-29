import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { LogCollectorApi } from '../api/logCollectorApi';
import { formatKst } from '../utils/date';
import LogDetailModal from '../components/LogDetailModal';
import {
Container,
Table,
Badge,
Button,
ButtonGroup
} from 'react-bootstrap';

const LogDashboard = () => {
const navigate = useNavigate();
const [page, setPage] = useState(0);
const [rows, setRows] = useState([]);
const [selectedLog, setSelectedLog] = useState(null);
const PAGE_SIZE = 20;

const loadLogs = async () => {
try {
const res = await LogCollectorApi.searchLogs({
page: page,
size: PAGE_SIZE,
serviceName: '',
keyword: '',
status: '',
isToday: false
});
setRows(res.data?.content ?? []);
} catch (e) {
console.error(e);
}
};

useEffect(() => {
loadLogs();
// eslint-disable-next-line react-hooks/exhaustive-deps
}, [page]);

return (
<Container className="page py-4">
    <div className="mb-4">
        <h3 className="fw-bold mb-2">Log Dashboard</h3>
        <p className="text-muted small mb-0">
            * 이 페이지는 데이터 검증 편의상 제공되는 화면입니다. 실제 서비스는 View가 없는 수집 모듈(Backend Service)로 동작합니다.
        </p>
    </div>

    <Table hover responsive className="shadow-sm bg-white rounded align-middle">
        <thead className="bg-light table-light">
        <tr>
            <th style={{ width: '120px' }}>Service</th>
            <th>Summary</th>
            <th style={{ width: '100px' }}>Level</th>
            <th style={{ width: '180px' }}>Time</th>
            <th style={{ width: '80px' }}>Count</th>
            <th style={{ width: '160px' }}>Action</th>
        </tr>
        </thead>
        <tbody>
        {rows.length === 0 ? (
        <tr>
            <td colSpan="6" className="text-center py-4 text-muted">
                No logs found.
            </td>
        </tr>
        ) : (
        rows.map((log) => (
        <tr key={log.logId ?? log.id}>
            <td className="fw-semibold text-secondary">{log.serviceName}</td>
            <td className="text-truncate" style={{ maxWidth: '360px' }} title={log.summary}>
            {log.summary || log.message || '(No summary)'}
            </td>
            <td>
                <Badge bg={log.logLevel === 'ERROR' ? 'danger' : 'info'}>
                {log.logLevel}
                </Badge>
            </td>
            <td className="small text-muted">{formatKst(log.occurredTime)}</td>
            <td>{log.repeatCount}</td>
            <td>
                <ButtonGroup size="sm">
                    <Button
                            variant="outline-primary"
                            onClick={() => setSelectedLog(log)}
                    >
                    View
                    </Button>
                    {/* LogDashboard에서도 Incident로 바로 이동하는 버튼 추가 */}
                    <Button
                            variant="outline-secondary"
                            onClick={() => navigate(`/incidents/${log.logHash}`)}
                    title="Go to Linked Incident"
                    >
                    Incident
                    </Button>
                </ButtonGroup>
            </td>
        </tr>
        ))
        )}
        </tbody>
    </Table>

    {/* 페이지네이션 */}
    <div className="d-flex justify-content-center gap-2 mt-3 mb-3">
        <Button
                size="sm"
                variant="outline-primary"
                disabled={page === 0}
        onClick={() => setPage(page - 1)}
        >
        Prev
        </Button>
        <span className="align-self-center text-muted small">Page {page + 1}</span>
        <Button
                size="sm"
                variant="outline-primary"
                disabled={rows.length < PAGE_SIZE}
        onClick={() => setPage(page + 1)}
        >
        Next
        </Button>
    </div>

    {selectedLog && (
    <LogDetailModal
            log={selectedLog}
            onClose={() => setSelectedLog(null)}
    />
    )}
</Container>
);
};

export default LogDashboard;