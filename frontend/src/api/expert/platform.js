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

export function downloadExpertProfileImportTemplate() {
  return request({
    url: '/expert/workspace/profile/import-template',
    method: 'get',
    responseType: 'blob'
  })
}

export function getExpertSchedules() {
  return request({ url: '/expert/workspace/schedules', method: 'get' })
}

export function saveExpertSchedules(data) {
  return request({ url: '/expert/workspace/schedules', method: 'put', data })
}

export function getExpertUnavailablePeriods() {
  return request({ url: '/expert/workspace/unavailable-periods', method: 'get' })
}

export function getExpertOccupancyPeriods(serviceStart, serviceEnd) {
  return request({
    url: '/expert/workspace/occupancy-periods',
    method: 'get',
    params: { serviceStart, serviceEnd }
  })
}

export function createExpertUnavailablePeriod(data) {
  return request({ url: '/expert/workspace/unavailable-periods', method: 'post', data })
}

export function updateExpertUnavailablePeriod(unavailableId, data) {
  return request({
    url: `/expert/workspace/unavailable-periods/${unavailableId}`,
    method: 'put',
    data
  })
}

export function deleteExpertUnavailablePeriod(unavailableId) {
  return request({
    url: `/expert/workspace/unavailable-periods/${unavailableId}`,
    method: 'delete'
  })
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

export function listExpertMaterialFiles(materialId) {
  return request({
    url: `/expert/workspace/materials/${materialId}/files`,
    method: 'get'
  })
}

export function getQualificationReminders() {
  return request({ url: '/expert/workspace/qualification-reminders', method: 'get' })
}

export function addExpertMaterialFile(materialId, data) {
  return request({
    url: `/expert/workspace/materials/${materialId}/files`,
    method: 'post',
    data
  })
}

export function deleteExpertMaterialFile(materialId, fileId) {
  return request({
    url: `/expert/workspace/materials/${materialId}/files/${fileId}`,
    method: 'delete'
  })
}

export function submitExpertMaterialRevision(materialId) {
  return request({
    url: `/expert/workspace/materials/${materialId}/submit`,
    method: 'post'
  })
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

export function previewExpertMaterialFile(materialId, fileId, mode = 'raw') {
  return request({
    url: `/expert/workspace/materials/${materialId}/files/${fileId}/preview`,
    method: 'get',
    params: { mode },
    responseType: 'blob'
  })
}

// 能力画像
export function getExpertGraph() {
  return request({ url: '/expert/workspace/graph', method: 'get' })
}

export function getMyProfileReviews() {
  return request({ url: '/expert/workspace/profile-reviews', method: 'get' })
}

export function getMyRiskAlerts() {
  return request({ url: '/expert/workspace/risk-alerts', method: 'get' })
}

export function getExpertProfileVersions() {
  return request({ url: '/expert/workspace/profile/versions', method: 'get' })
}

export function getExpertProfileVersion(releaseId) {
  return request({ url: `/expert/workspace/profile/versions/${releaseId}`, method: 'get' })
}

// 接单状态
export function getExpertStatus() {
  return request({ url: '/expert/workspace/status', method: 'get' })
}

export function changeExpertStatus(data) {
  return request({ url: '/expert/workspace/status', method: 'post', data })
}

export function cancelExpertStatusRequest(requestId, version) {
  return request({
    url: `/expert/workspace/status/requests/${requestId}/cancel`,
    method: 'post',
    params: { version }
  })
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

export function reviewAiResult(materialId, resultType, resultId, status) {
  return request({
    url: `/expert/review/materials/${materialId}/ai-results/${resultType}/${resultId}`,
    method: 'post',
    params: { status }
  })
}

export function editReviewAiResult(materialId, resultType, resultId, data) {
  return request({
    url: `/expert/review/materials/${materialId}/ai-results/${resultType}/${resultId}/edit`,
    method: 'post',
    data
  })
}

export function listExpertStatusRequests(query) {
  return request({ url: '/expert/review/status-requests', method: 'get', params: query })
}

export function reviewExpertStatusRequest(requestId, data) {
  return request({
    url: `/expert/review/status-requests/${requestId}/action`,
    method: 'post',
    data
  })
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

export function previewReviewMaterialFile(materialId, fileId, mode = 'raw') {
  return request({
    url: `/expert/review/materials/${materialId}/files/${fileId}/preview`,
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

export function downloadAdminExpertProfileImportTemplate() {
  return request({
    url: '/expert/admin/experts/profile-import-template',
    method: 'get',
    responseType: 'blob'
  })
}

export function getAdminExpertCertifications(userId) {
  return request({ url: '/expert/admin/experts/' + userId + '/certifications', method: 'get' })
}

export function getAdminExpertSchedules(userId) {
  return request({ url: '/expert/admin/experts/' + userId + '/schedules', method: 'get' })
}

export function getAdminExpertUnavailablePeriods(userId) {
  return request({
    url: `/expert/admin/experts/${userId}/unavailable-periods`,
    method: 'get'
  })
}

export function saveAdminExpertSchedules(userId, data) {
  return request({ url: '/expert/admin/experts/' + userId + '/schedules', method: 'put', data })
}

export function getAdminExpertGraph(userId) {
  return request({ url: '/expert/admin/experts/' + userId + '/graph', method: 'get' })
}

export function getAdminExpertProfileVersions(userId) {
  return request({
    url: `/expert/admin/experts/${userId}/profile-versions`,
    method: 'get'
  })
}

export function getAdminExpertProfileVersion(userId, releaseId) {
  return request({
    url: `/expert/admin/experts/${userId}/profile-versions/${releaseId}`,
    method: 'get'
  })
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

export function getKnowledgeGraphOverview(entityLimit = 400, relationLimit = 800) {
  return request({
    url: '/expert/admin/knowledge-graph/overview',
    method: 'get',
    params: { entityLimit, relationLimit }
  })
}

export function runKnowledgeGraphAgent(data) {
  return request({
    url: '/expert/admin/agent/runs',
    method: 'post',
    data,
    timeout: 180000
  })
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

// 业务经办人员只读查询
export function listBusinessExperts(query) {
  return request({ url: '/expert/business/experts', method: 'get', params: query })
}

export function getBusinessExpertDetail(userId) {
  return request({ url: `/expert/business/experts/${userId}`, method: 'get' })
}

export function checkBusinessEligibility(data) {
  return request({ url: '/expert/business/eligibility', method: 'post', data })
}

export function listBusinessOccupancy(userId, serviceStart, serviceEnd) {
  return request({
    url: `/expert/business/experts/${userId}/occupancy`,
    method: 'get',
    params: { serviceStart, serviceEnd }
  })
}

export function requestBusinessTaskAcceptance(data) {
  return request({
    url: '/expert/business/task-acceptance-requests',
    method: 'post',
    data
  })
}

export function listBusinessTaskAcceptances(userId, status) {
  return request({
    url: `/expert/business/experts/${userId}/task-acceptances`,
    method: 'get',
    params: { status }
  })
}

export function confirmBusinessArrangement(confirmationId) {
  return request({
    url: `/expert/business/task-acceptances/${confirmationId}/confirm-arrangement`,
    method: 'post'
  })
}

export function cancelBusinessArrangement(confirmationId) {
  return request({
    url: `/expert/business/task-acceptances/${confirmationId}/cancel-arrangement`,
    method: 'post'
  })
}

export function listMyTaskAcceptances(status) {
  return request({
    url: '/expert/workspace/task-acceptances',
    method: 'get',
    params: { status }
  })
}

export function respondMyTaskAcceptance(confirmationId, data) {
  return request({
    url: `/expert/workspace/task-acceptances/${confirmationId}/respond`,
    method: 'post',
    data
  })
}

// 投诉、低评价和信用预警
export function listRiskReviews(query) {
  return request({ url: '/expert/admin/risk-reviews', method: 'get', params: query })
}

export function getRiskReview(riskId) {
  return request({ url: `/expert/admin/risk-reviews/${riskId}`, method: 'get' })
}

export function reviewRisk(riskId, data) {
  return request({
    url: `/expert/admin/risk-reviews/${riskId}/review`,
    method: 'post',
    data
  })
}

// 画像更新建议审核
export function listProfileUpdateSuggestions(query) {
  return request({ url: '/expert/admin/profile-updates', method: 'get', params: query })
}

export function getProfileUpdateSuggestion(suggestionId) {
  return request({
    url: `/expert/admin/profile-updates/${suggestionId}`,
    method: 'get'
  })
}

export function reviewProfileUpdateSuggestion(suggestionId, data) {
  return request({
    url: `/expert/admin/profile-updates/${suggestionId}/review`,
    method: 'post',
    data
  })
}
