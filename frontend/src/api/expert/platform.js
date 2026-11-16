import request from '@/utils/request'

// 专家工作台
export function getExpertSummary() {
  return request({ url: '/expert/workspace/summary', method: 'get' })
}

export function getExpertProfile() {
  return request({ url: '/expert/workspace/profile', method: 'get' })
}

export function saveExpertProfile(data) {
  return request({ url: '/expert/workspace/profile', method: 'put', data })
}

export function getExpertSchedules() {
  return request({ url: '/expert/workspace/schedules', method: 'get' })
}

export function saveExpertSchedules(data) {
  return request({ url: '/expert/workspace/schedules', method: 'put', data })
}

// 资料处理
export function listExpertMaterials(query) {
  return request({ url: '/expert/workspace/materials', method: 'get', params: query })
}

export function getExpertCertifications() {
  return request({ url: '/expert/workspace/certifications', method: 'get' })
}

export function submitExpertMaterial(data) {
  return request({ url: '/expert/workspace/materials', method: 'post', data })
}

export function retryExpertMaterial(materialId) {
  return request({
    url: '/expert/workspace/materials/retry',
    method: 'post',
    params: { materialId }
  })
}

export function previewExpertMaterial(materialId, mode = 'raw') {
  return request({
    url: `/expert/workspace/materials/${materialId}/preview`,
    method: 'get',
    params: { mode },
    responseType: 'blob'
  })
}

// 能力画像
export function getExpertGraph() {
  return request({ url: '/expert/workspace/graph', method: 'get' })
}

// 接单状态
export function getExpertStatus() {
  return request({ url: '/expert/workspace/status', method: 'get' })
}

export function changeExpertStatus(data) {
  return request({ url: '/expert/workspace/status', method: 'post', data })
}

// 审核工作台
export function getReviewSummary() {
  return request({ url: '/expert/review/summary', method: 'get' })
}

export function listReviewQueue(query) {
  return request({ url: '/expert/review/queue', method: 'get', params: query })
}

export function getReviewDetail(userId) {
  return request({ url: '/expert/review/' + userId, method: 'get' })
}

export function submitReviewAction(userId, data) {
  return request({ url: '/expert/review/' + userId + '/action', method: 'post', data })
}

export function changeReviewedExpertStatus(userId, data) {
  return request({ url: '/expert/review/' + userId + '/status', method: 'post', data })
}

export function listApprovedExperts(query) {
  return request({ url: '/expert/review/approved/list', method: 'get', params: query })
}

export function retryAgentJob(materialId) {
  return request({ url: '/expert/review/agent/' + materialId + '/retry', method: 'post' })
}

export function previewReviewMaterial(materialId, mode = 'raw') {
  return request({
    url: `/expert/review/materials/${materialId}/preview`,
    method: 'get',
    params: { mode },
    responseType: 'blob'
  })
}

export function listReviewJobs(query) {
  return request({ url: '/expert/review/jobs', method: 'get', params: query })
}

// 管理控制台
export function getAdminSummary() {
  return request({ url: '/expert/admin/summary', method: 'get' })
}

export function listAdminJobs(query) {
  return request({ url: '/expert/admin/jobs', method: 'get', params: query })
}

export function listAdminExperts(query) {
  return request({ url: '/expert/admin/experts', method: 'get', params: query })
}

export function listAdminExpertUsers() {
  return request({ url: '/expert/admin/experts/users', method: 'get' })
}

export function getAdminExpertCertifications(userId) {
  return request({ url: '/expert/admin/experts/' + userId + '/certifications', method: 'get' })
}

export function getAdminExpertGraph(userId) {
  return request({ url: '/expert/admin/experts/' + userId + '/graph', method: 'get' })
}

export function saveAdminExpert(userId, data) {
  return request({ url: '/expert/admin/experts/' + userId, method: 'put', data })
}

export function deleteAdminExpert(userId) {
  return request({ url: '/expert/admin/experts/' + userId, method: 'delete' })
}

export function getKnowledgeGraphOntology() {
  return request({ url: '/expert/admin/knowledge-graph/ontology', method: 'get' })
}

export function projectKnowledgeGraph(userId) {
  return request({
    url: '/expert/admin/knowledge-graph/experts/' + userId + '/project',
    method: 'post'
  })
}

export function getKnowledgeGraphSubgraph(userId, depth = 2) {
  return request({
    url: '/expert/admin/knowledge-graph/experts/' + userId + '/subgraph',
    method: 'get',
    params: { depth }
  })
}

export function runKnowledgeGraphAgent(data) {
  return request({ url: '/expert/admin/agent/runs', method: 'post', data })
}

export function getKnowledgeGraphAgentRun(runId) {
  return request({ url: '/expert/admin/agent/runs/' + runId, method: 'get' })
}

export function rebuildKnowledgeGraph() {
  return request({
    url: '/expert/admin/knowledge-graph/rebuild',
    method: 'post',
    params: { confirm: true }
  })
}

export function getEvaluationDatasets() {
  return request({ url: '/expert/admin/evaluation/datasets', method: 'get' })
}

export function runQualityEvaluation(datasetId) {
  return request({
    url: '/expert/admin/evaluation/datasets/' + datasetId + '/run',
    method: 'post'
  })
}

export function getEvaluationRun(runId) {
  return request({ url: '/expert/admin/evaluation/runs/' + runId, method: 'get' })
}
