import axios from 'axios';

const API_BASE_URL = 'http://localhost:8080/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
});

export const LogCollectorApi = {
  // --- 1. 로그 (Logs) ---
  // [수정] LogDashboard.js에서 searchLogs로 호출하므로 별칭 추가
  searchLogs: (params) => api.get('/logs', { params }),
  getLogs: (params) => api.get('/logs', { params }),

  updateLogStatus: (logId, status) => api.patch(`/logs/${logId}/status`, null, { params: { newStatus: status } }),

  // 백엔드 AnalysisController 경로 일치
  analyzeAi: (logHash) => api.post(`/logs/analyze/${logHash}`),

  collectLog: (data) => api.post('/logs', data),

  // --- 2. 인시던트 (Incidents) ---
  // [수정] IncidentDashboard.js에서 searchIncidents로 호출함
  searchIncidents: (params) => api.get('/incidents/search', { params }),
  getIncidents: (params) => api.get('/incidents/search', { params }),

  // 랭킹 조회
  getIncidentTop: (metric) => api.get('/incidents/top', { params: { metric, limit: 5 } }),

  // [수정] 식별자를 id -> logHash로 변경 (프론트엔드 호출 규격에 맞춤)
  updateIncidentStatus: (logHash, status) => api.patch(`/incidents/${logHash}/status`, null, { params: { newStatus: status } }),

  // 상세 정보 수정
  updateIncidentDetails: (logHash, params) => api.patch(`/incidents/${logHash}/details`, null, { params }),




  // [추가] IncidentDetailPage.js에서 호출하는 메서드 (이름 일치화)
  getIncidentByLogHash: (logHash) => api.get(`/incidents/${logHash}`),
  getIncidentByHash: (logHash) => api.get(`/incidents/${logHash}`),

  // [누락된 기능 추가] IncidentDashboard에서 "+KB" 버튼 클릭 시 호출
  createDraft: (incidentId) => api.post(`/kb/draft/${incidentId}`),

  // --- 3. KB (Knowledge Base) ---
  // [수정] KbDashboard.js에서 listKb로 호출함
  listKb: (params) => api.get('/kb', { params }),
  getKbArticles: (params) => api.get('/kb', { params }),

  getKbDetail: (id) => api.get(`/kb/${id}`),

  // 게시(Publish) 및 내용 저장 (Append)
  postKbArticle: (kbArticleId, data) => api.post(`/kb/articles/${kbArticleId}`, data),

  // [수정] 프론트엔드에서 updateDraft로 호출하는 경우 대응
  updateDraft: (kbArticleId, data) => api.post(`/kb/draft/${kbArticleId}`, data),
  updateKbDraft: (kbArticleId, data) => api.post(`/kb/draft/${kbArticleId}`, data),

    // Addendum 생성 (메모 추가)
    createKbAddendum: (kbArticleId, data) =>
      api.post(`/kb/${kbArticleId}/addendums`, data),
};