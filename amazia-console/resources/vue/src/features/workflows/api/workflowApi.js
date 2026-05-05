import api from '../../auth/api/authApi.js';

export function listWorkflows(status) {
  const config = status ? { params: { status } } : {};
  return api.get('/workflows', config);
}

export function getWorkflow(id) {
  return api.get(`/workflows/${id}`);
}

export function createWorkflow(payload) {
  return api.post('/workflows', payload);
}

export function approveWorkflowStep(id, stepNumber) {
  return api.post(`/workflows/${id}/steps/${stepNumber}/approve`);
}

export function rejectWorkflowStep(id, stepNumber) {
  return api.post(`/workflows/${id}/steps/${stepNumber}/reject`);
}

export function cancelWorkflow(id) {
  return api.post(`/workflows/${id}/cancel`);
}

export function immediateApply(payload) {
  return api.post('/workflows/immediate-apply', payload);
}
