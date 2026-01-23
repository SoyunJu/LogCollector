import axios from 'axios';

const API_BASE_URL = 'http://localhost:8080/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
});

export const LogCollectorApi = {
  // --- 1. 로그 (Logs) ---
  getLogs: (params) => api.get('/logs', { params }),
  updateLogStatus: (logId, status) => api.patch(`/logs/${logId}/status`, null, { params: { newStatus: status } }),

  // [수정] 백엔드 AnalysisController와 경로 및 파라미터(logHash) 일치화
  analyzeAi: (logHash) => api.post(`/logs/analyze/${logHash}`),

  collectLog: (data) => api.post('/logs', data),

  // --- 2. 인시던트 (Incidents) ---
  // [수정] 검색 컨트롤러(/search) 연결
  getIncidents: (params) => api.get('/incidents/search', { params }),

  // [추가] 랭킹 컨트롤러(/top) 연결
  getIncidentTop: (metric) => api.get('/incidents/top', { params: { metric, limit: 5 } }),

  // [수정] 식별자를 id -> logHash로 변경
  updateIncidentStatus: (logHash, status) => api.patch(`/incidents/${logHash}/status`, null, { params: { newStatus: status } }),

  // [추가] 상세 정보 수정 (제목, 작성자 등)
  updateIncidentDetails: (logHash, params) => api.patch(`/incidents/${logHash}/details`, null, { params }),

  // --- 3. KB (Knowledge Base) ---
  getKbArticles: (params) => api.get('/kb', { params }),
  getKbDetail: (id) => api.get(`/kb/${id}`),

  // [수정] 게시(Publish) 및 초안(Draft) 업데이트 경로 확인
  postKbArticle: (kbArticleId, data) => api.post(`/kb/articles/${kbArticleId}`, data),
  updateKbDraft: (kbArticleId, data) => api.post(`/kb/draft/${kbArticleId}`, data),

  // [추가] 해시로 인시던트 조회 (KB 등록 전 확인용)
  getIncidentByHash: (logHash) => api.get(`/incidents/${logHash}`),
};