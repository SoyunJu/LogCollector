import React from 'react';
import { BrowserRouter as Router, Routes, Route, Link } from 'react-router-dom';
import LogDashboard from './pages/LogDashboard';
import IncidentDashboard from './pages/IncidentDashboard'; // 파일 생성 필요
import KbDashboard from './pages/KbDashboard';         // 파일 생성 필요
import KbDetailPage from './pages/KbDetailPage';       // 파일 생성 필요
import LogGenerator from './pages/LogGenerator';
import { Container, Nav, Navbar } from 'react-bootstrap';

function App() {
return (
<Router>
    <Navbar bg="dark" variant="dark" expand="lg" className="mb-4 sticky-top shadow">
        <Container>
            <Navbar.Brand as={Link} to="/" className="fw-bold">🛡️ LogCollector</Navbar.Brand>
            <Navbar.Toggle aria-controls="basic-navbar-nav" />
            <Navbar.Collapse id="basic-navbar-nav">
                <Nav className="ms-auto">
                    {/* [수정] 모든 탭 활성화 */}
                    <Nav.Link as={Link} to="/">Logs</Nav.Link>
                    <Nav.Link as={Link} to="/incidents">Incidents</Nav.Link>
                    <Nav.Link as={Link} to="/kb">Knowledge Base</Nav.Link>
                    <Nav.Link as={Link} to="/test" className="text-warning">Test Generator</Nav.Link>
                </Nav>
            </Navbar.Collapse>
        </Container>
    </Navbar>

    <Container className="pb-5">
        <Routes>
            <Route path="/" element={<LogDashboard />} />
            <Route path="/incidents" element={<IncidentDashboard />} />
            <Route path="/kb" element={<KbDashboard />} />
            <Route path="/kb/:id" element={<KbDetailPage />} />
            <Route path="/test" element={<LogGenerator />} />
        </Routes>
    </Container>
</Router>
);
}

export default App;