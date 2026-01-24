import React from 'react';
import { BrowserRouter, Routes, Route, Link } from 'react-router-dom';
import { Navbar, Container, Nav } from 'react-bootstrap';
import './App.css';

// Pages
import LogGenerator from './pages/LogGenerator';
import LogDashboard from './pages/LogDashboard';
import IncidentDashboard from './pages/IncidentDashboard'; // [추가됨]
import IncidentDetailPage from './pages/IncidentDetailPage';
import KbDashboard from './pages/KbDashboard'; // [추가됨]
import KbDetailPage from './pages/KbDetailPage';

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
                    <Nav.Link as={Link} to="/generator">Log Generator</Nav.Link>
                    <Nav.Link as={Link} to="/logs">LC Logs</Nav.Link>
                    <Nav.Link as={Link} to="/incidents">Incidents</Nav.Link>
                    <Nav.Link as={Link} to="/kb">Knowledge Base</Nav.Link>
                </Nav>
            </Navbar.Collapse>
        </Container>
    </Navbar>

    {/* 메인 콘텐츠 영역 */}
    <Container className="py-3">
        <Routes>
            <Route path="/generator" element={<LogGenerator />} />
            <Route path="/logs" element={<LogDashboard />} />

            <Route path="/incidents" element={<IncidentDashboard />} />
            <Route path="/incidents/:logHash" element={<IncidentDetailPage />} />

            <Route path="/kb" element={<KbDashboard />} />
            <Route path="/kb/:kbArticleId" element={<KbDetailPage />} />

            {/* 기본 경로는 Incidents로 리다이렉트 처리와 유사하게 동작 */}
            <Route path="*" element={<IncidentDashboard />} />
        </Routes>
    </Container>
</BrowserRouter>
);
};

export default App;