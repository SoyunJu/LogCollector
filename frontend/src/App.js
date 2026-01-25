import React from 'react';
import { BrowserRouter, Routes, Route, Link } from 'react-router-dom';
import { Navbar, Container, Nav } from 'react-bootstrap';
import './App.css';

// Pages
import LogGenerator from './pages/LogGenerator';
import LogDashboard from './pages/LogDashboard';
import IncidentDashboard from './pages/IncidentDashboard';
import IncidentDetailPage from './pages/IncidentDetailPage';
import KbDashboard from './pages/KbDashboard';
import KbDetailPage from './pages/KbDetailPage';
import RankDashboard from './pages/RankDashboard';
import SystemCheckPage from './pages/SystemCheckPage';

const App = () => {
return (
<BrowserRouter>
    {/* 상단 네비게이션 바 */}
    <Navbar bg="dark" variant="dark" expand="lg" className="mb-4 shadow-sm">
        <Container>
            <Navbar.Brand as={Link} to="/">🛡️ LogCollector</Navbar.Brand>
            <Navbar.Toggle aria-controls="basic-navbar-nav" />
            <Navbar.Collapse id="basic-navbar-nav">
                <Nav className="me-auto">
                    <Nav.Link as={Link} to="/generator">Generator</Nav.Link>
                    <Nav.Link as={Link} to="/logs">Logs</Nav.Link>
                    <Nav.Link as={Link} to="/incidents">Incidents</Nav.Link>
                    <Nav.Link as={Link} to="/rank">🏆 Rank</Nav.Link>
                    {/* 수정됨: 여기에 있던 <Route> 제거함 */}
                    <Nav.Link as={Link} to="/kb">KB</Nav.Link>
                    <Nav.Link as={Link} to="/diagnostics">Diagnostics</Nav.Link>
                </Nav>
            </Navbar.Collapse>
        </Container>
    </Navbar>

    {/* 메인 콘텐츠 영역 */}
    <Container className="py-3">
        <Routes>
            {/* 각 페이지별 경로 설정 */}
            <Route path="/generator" element={<LogGenerator />} />
            <Route path="/logs" element={<LogDashboard />} />

            <Route path="/incidents" element={<IncidentDashboard />} />
            <Route path="/incidents/:logHash" element={<IncidentDetailPage />} />

            <Route path="/kb" element={<KbDashboard />} />
            <Route path="/kb/:kbArticleId" element={<KbDetailPage />} />

            <Route path="/rank" element={<RankDashboard />} />

            {/* 수정됨: SystemCheckPage 라우트 추가 */}
            <Route path="/diagnostics" element={<SystemCheckPage />} />

            {/* 기본 경로(/)나 없는 경로 접근 시 Incidents 페이지로 이동 */}
            <Route path="*" element={<IncidentDashboard />} />
        </Routes>
    </Container>
</BrowserRouter>
);
};

export default App;