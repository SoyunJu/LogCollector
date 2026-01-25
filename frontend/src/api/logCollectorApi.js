import axios from 'axios';

 const API_BASE_URL = 'http://localhost:8080/api';

 const api = axios.create({
   baseURL: API_BASE_URL,
   headers: { 'Content-Type': 'application/json' },
 });

 export const LogCollectorApi = {
   // --- Logs ---
   searchLogs: (params) => api.get('/logs', { params }),
   updateLogStatus: (logId, status) => api.patch(`/logs/${logId}/status`, null, { params: { newStatus: status } }),
   analyzeAi: (logHash, force = false) =>
       api.post(`/logs/analyze/${logHash}`, null, { params: { force } }),
   collectLog: (data) => api.post('/logs', data),

   // --- Incidents ---
   searchIncidents: (params) => api.get('/incidents/search', { params }),
   getIncidentByHash: (logHash) => api.get(`/incidents/${logHash}`),
   getIncidentByLogHash: (logHash) => api.get(`/incidents/${logHash}`),
   updateIncidentStatus: (logHash, status) => api.patch(`/incidents/${logHash}/status`, null, { params: { newStatus: status } }),
   updateIncidentDetails: (logHash, data) => api.patch(`/incidents/${logHash}/details`, null, { params: data }),
   createDraft: (incidentId) => api.post('/kb/draft', null, { params: { incidentId } }),

   // [신규] 랭킹 조회
   getIncidentTop: (params = {}) => {
       // params 예시: { metric: 'repeatCount', status: 'OPEN', from: '...', serviceName: '...' }
      return api.get('/incidents/top', { params });
   },




   // --- KB ---
   listKb: (params) => api.get('/kb', { params }),
  getKbDetail: (id, params) => api.get(`/kb/${id}`, { params }),
   postKbArticle: (id, data) => api.post(`/kb/articles/${id}`, data),
   updateDraft: (id, data) => api.post(`/kb/draft/${id}/update`, data),
   updateKbStatus: (id, status) => api.patch(`/kb/articles/${id}/status`, null, { params: { status } }),

   // [신규] 스케줄러 수동 실행
   runScheduler: () => api.post('/test/scheduler/run'),

};
