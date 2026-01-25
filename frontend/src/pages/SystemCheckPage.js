import React, { useEffect, useMemo, useState } from 'react';
import { LogCollectorApi } from '../api/logCollectorApi';
import { formatKst } from '../utils/date';
import { Badge, Button, Card, Container, Table } from 'react-bootstrap';

const SystemCheckPage = () => {
const [running, setRunning] = useState(false);
const [lastRunAt, setLastRunAt] = useState(null);
const [results, setResults] = useState({});

const checks = useMemo(() => ([
{ id: 'logs', label: 'Logs 조회 (/logs)' },
{ id: 'incidents', label: 'Incident 검색 (/incidents/search)' },
{ id: 'incidentDetail', label: 'Incident 상세 (/incidents/{logHash})' },
{ id: 'incidentTop', label: 'Incident 랭킹 (/incidents/top)' },
{ id: 'kbList', label: 'KB 목록 (/kb)' },
{ id: 'kbDetail', label: 'KB 상세 (/kb/{id})' }
]), []);

const getErrorMessage = (error) => {
if (!error) return 'Unknown error';
return error.response?.data?.message || error.message || 'Unknown error';
};

const runChecks = async () => {
setRunning(true);
const nextResults = {};
const startedAt = new Date();

try {
const logsRes = await LogCollectorApi.searchLogs({ page: 0, size: 5 });
const logs = logsRes.data?.content ?? [];
nextResults.logs = {
status: 'PASS',
message: `${logs.length}건 로드됨`
};
} catch (error) {
nextResults.logs = { status: 'FAIL', message: getErrorMessage(error) };
}

let incidents = [];
try {
const incidentRes = await LogCollectorApi.searchIncidents({ page: 0, size: 5 });
incidents = incidentRes.data?.content ?? [];
nextResults.incidents = {
status: 'PASS',
message: `${incidents.length}건 로드됨`
};
} catch (error) {
nextResults.incidents = { status: 'FAIL', message: getErrorMessage(error) };
}

try {
const target = incidents[0];
if (!target?.logHash) {
nextResults.incidentDetail = { status: 'SKIP', message: 'Incident 데이터 없음' };
} else {
await LogCollectorApi.getIncidentByHash(target.logHash);
nextResults.incidentDetail = { status: 'PASS', message: `logHash=${target.logHash}` };
}
} catch (error) {
nextResults.incidentDetail = { status: 'FAIL', message: getErrorMessage(error) };
}

try {
const topRes = await LogCollectorApi.getIncidentTop({ metric: 'repeatCount', limit: 5 });
const count = topRes.data?.length ?? 0;
nextResults.incidentTop = { status: 'PASS', message: `${count}건 로드됨` };
} catch (error) {
nextResults.incidentTop = { status: 'FAIL', message: getErrorMessage(error) };
}

let kbArticles = [];
try {
const kbRes = await LogCollectorApi.listKb({ page: 0, size: 5 });
kbArticles = kbRes.data?.content ?? kbRes.data ?? [];
nextResults.kbList = { status: 'PASS', message: `${kbArticles.length}건 로드됨` };
} catch (error) {
nextResults.kbList = { status: 'FAIL', message: getErrorMessage(error) };
}

try {
const target = kbArticles[0];
if (!target?.id) {
nextResults.kbDetail = { status: 'SKIP', message: 'KB 데이터 없음' };
} else {
await LogCollectorApi.getKbDetail(target.id, { addendumPage: 0, addendumSize: 5 });
nextResults.kbDetail = { status: 'PASS', message: `id=${target.id}` };
}
} catch (error) {
nextResults.kbDetail = { status: 'FAIL', message: getErrorMessage(error) };
}

setResults(nextResults);
setLastRunAt(startedAt);
setRunning(false);
};

const renderStatusBadge = (status) => {
if (status === 'PASS') return <Badge bg="success">PASS</Badge>;
if (status === 'FAIL') return <Badge bg="danger">FAIL</Badge>;
if (status === 'SKIP') return <Badge bg="secondary">SKIP</Badge>;
return <Badge bg="light" text="dark">PENDING</Badge>;
};

useEffect(() => { runChecks(); }, []);

return (
<Container className="page py-3">
    <Card className="shadow-sm mb-3">
        <Card.Body className="d-flex justify-content-between align-items-center">
            <div>
                <h3 className="m-0">✅ System Check</h3>
                <div className="text-muted small">프론트에서 주요 API 동작 여부를 빠르게 확인합니다.</div>
                {lastRunAt && (
                <div className="text-muted small mt-1">Last run: {formatKst(lastRunAt)}</div>
                )}
            </div>
            <Button variant="primary" onClick={runChecks} disabled={running}>
                {running ? 'Checking...' : 'Run Checks'}
            </Button>
        </Card.Body>
    </Card>

    <Card className="shadow-sm">
        <Table hover responsive className="mb-0">
            <thead className="table-light">
            <tr>
                <th style={{width: '220px'}}>Check</th>
                <th style={{width: '120px'}}>Status</th>
                <th>Detail</th>
            </tr>
            </thead>
            <tbody>
            {checks.map((check) => {
            const result = results[check.id];
            return (
            <tr key={check.id}>
                <td>{check.label}</td>
                <td>{renderStatusBadge(result?.status)}</td>
                <td className="text-muted small">{result?.message || '대기 중'}</td>
            </tr>
            );
            })}
            </tbody>
        </Table>
    </Card>
</Container>
);
};

export default SystemCheckPage;