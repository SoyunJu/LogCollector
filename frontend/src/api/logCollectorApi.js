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
  analyzeAi: (logId) => api.post(`/logs/ai/${logId}/analyze`),
  collectLog: (data) => api.post('/logs', data),

  // --- 2. 인시던트 (Incidents) ---
  getIncidents: (params) => api.get('/incidents', { params }),
  getIncidentTop: () => api.get('/incidents/top', { params: { limit: 10 } }),
  // [추가] 인시던트 상태 변경
  updateIncidentStatus: (id, status) => api.patch(`/incidents/${id}/status`, null, { params: { status } }),

  // --- 3. KB (Knowledge Base) ---
  getKbArticles: (params) => api.get('/kb', { params }),
  getKbDetail: (id) => api.get(`/kb/${id}`),
  createKbDraft: (incidentId) => api.post('/kb/draft', null, { params: { incidentId } }),
  updateKbDraft: (id, data) => api.post(`/kb/draft/${id}`, data),
  // [수정] createdBy 파라미터 추가 전송
  publishKbArticle: (id, data) => api.post(`/kb/articles/${id}`, data)
};