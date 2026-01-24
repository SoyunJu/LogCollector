import React from 'react';
import { BrowserRouter, Routes, Route, Link } from 'react-router-dom';
import './App.css';

import LogGenerator from './pages/LogGenerator';
import LogDashboard from './pages/LogDashboard';
import IncidentDashboard from './pages/IncidentDashboard';
import KbDashboard from './pages/KbDashboard';
import KbDetailPage from './pages/KbDetailPage';
import IncidentDetailPage from './pages/IncidentDetailPage';

const App = () => {
return (
<BrowserRouter>
    <div className="nav">
        <Link to="/generator">LogGenerator</Link>
        <Link to="/logs">LC Logs</Link>
        <Link to="/incidents">Incidents</Link>
        <Link to="/kb">KB</Link>
    </div>

    <Routes>
        <Route path="/generator" element={<LogGenerator />} />
        <Route path="/logs" element={<LogDashboard />} />
        <Route path="/incidents" element={<IncidentDashboard />} />
        <Route path="/incidents/:logHash" element={<IncidentDetailPage />} />
        <Route path="/kb" element={<KbDashboard />} />
        <Route path="/kb/:kbArticleId" element={<KbDetailPage />} />
        <Route path="*" element={<IncidentDashboard />} />
    </Routes>
</BrowserRouter>
);
};

export default App;
