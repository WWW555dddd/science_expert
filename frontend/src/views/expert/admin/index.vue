<template>
  <div class="app-container admin-home" v-loading="loading">
    <div class="hero">
      <div>
        <span class="eyebrow">PLATFORM OPERATIONS</span>
        <h2>平台管理控制台</h2>
        <p>管理平台用户、角色权限、专家正式库、资料处理任务和安全审计。</p>
      </div>
      <el-tag effect="dark" size="large">管理员 {{ summary.currentUser }}</el-tag>
    </div>
    <el-row :gutter="16">
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat">
          <span>待审核</span><strong>{{ summary.pendingCount || 0 }}</strong>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat">
          <span>正式专家</span><strong>{{ summary.approvedCount || 0 }}</strong>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat">
          <span>处理异常</span><strong class="danger">{{ summary.agentFailureCount || 0 }}</strong>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat">
          <span>暂停接单</span><strong>{{ summary.pausedCount || 0 }}</strong>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" header="平台管理入口">
      <div class="admin-links">
        <el-button type="warning" plain icon="UserFilled" @click="go('/expert-management/list')">专家管理</el-button>
        <el-button type="primary" plain icon="User" @click="go('/system/user')">用户管理</el-button>
        <el-button type="success" plain icon="Avatar" @click="go('/system/role')">角色权限</el-button>
        <el-button plain icon="Tickets" @click="go('/monitor/operlog')">审计日志</el-button>
        <el-button plain icon="DataAnalysis" @click="go('/review-center/approved')">正式专家库</el-button>
      </div>
    </el-card>

    <el-card shadow="never" header="GraphRAG 专家匹配 Agent" class="agent-card">
      <el-row :gutter="12" class="agent-demand">
        <el-col :xs="24" :sm="6">
          <el-date-picker
            v-model="agentDemand.serviceDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="服务日期"
            style="width: 100%"
          />
        </el-col>
        <el-col :xs="24" :sm="6">
          <el-input v-model="agentDemand.serviceRegion" placeholder="服务区域，如 南昌" />
        </el-col>
        <el-col :xs="24" :sm="6">
          <el-select v-model="agentDemand.serviceMode" clearable placeholder="服务方式" style="width: 100%">
            <el-option label="远程" value="REMOTE" />
            <el-option label="现场" value="ONSITE" />
            <el-option label="均可" value="BOTH" />
          </el-select>
        </el-col>
        <el-col :xs="24" :sm="6">
          <el-input v-model="agentDemand.industry" placeholder="目标产业，如 新能源" />
        </el-col>
      </el-row>
      <el-input
        v-model="agentDemand.requiredCapabilities"
        class="agent-capabilities"
        maxlength="1000"
        placeholder="能力要求，用逗号分隔，如：储能安全, 技术鉴定, 现场取证"
      />
      <el-input
        v-model="agentObjective"
        type="textarea"
        :rows="3"
        maxlength="4000"
        show-word-limit
        placeholder="例如：为上海地区的新材料项目寻找具有相关项目经历、已认证且当前可以接单的专家"
      />
      <div class="agent-actions">
        <el-button
          type="primary"
          icon="Promotion"
          :loading="agentRunning"
          :disabled="!agentObjective.trim()"
          @click="runAgent"
        >
          运行 Agent
        </el-button>
        <el-button
          type="warning"
          plain
          icon="Refresh"
          :loading="graphRebuilding"
          @click="rebuildGraph"
        >
          重建 Neo4j 图谱
        </el-button>
        <span v-if="agentResult?.runId">运行编号：{{ agentResult.runId }}</span>
        <span v-if="agentResult?.taskEntityId">任务图谱：#{{ agentResult.taskEntityId }}</span>
      </div>
      <el-alert
        v-if="agentResult?.warning"
        :title="agentResult.warning"
        type="warning"
        :closable="false"
        show-icon
      />
      <div v-if="agentResult?.finalAnswer" class="agent-answer">
        <strong>Agent 结论</strong>
        <p>{{ agentResult.finalAnswer }}</p>
      </div>
      <el-table
        v-if="agentSteps.length"
        :data="agentSteps"
        size="small"
        class="agent-steps"
      >
        <el-table-column label="步骤" prop="stepNo" width="70" />
        <el-table-column label="工具" prop="toolName" min-width="180" />
        <el-table-column label="状态" prop="stepStatus" width="100" />
        <el-table-column label="结果摘要" min-width="220">
          <template #default="{ row }">{{ stepSummary(row) }}</template>
        </el-table-column>
        <el-table-column label="耗时(ms)" prop="latencyMs" width="100" />
      </el-table>
    </el-card>

    <el-card shadow="never" header="抽取质量与画像一致性评估" class="evaluation-card">
      <div class="evaluation-controls">
        <el-select v-model="selectedDatasetId" placeholder="选择评估数据集" style="width: 280px">
          <el-option
            v-for="dataset in evaluationDatasets"
            :key="dataset.datasetId"
            :label="`${dataset.datasetName} · ${dataset.datasetVersion}`"
            :value="dataset.datasetId"
          />
        </el-select>
        <el-button
          type="primary"
          plain
          icon="DataLine"
          :loading="evaluationRunning"
          :disabled="!selectedDatasetId"
          @click="runEvaluation"
        >
          运行质量评估
        </el-button>
        <span v-if="evaluationResult?.runId">评估编号：{{ evaluationResult.runId }}</span>
      </div>
      <el-alert
        v-if="selectedDataset?.description"
        :title="selectedDataset.description"
        type="info"
        :closable="false"
        show-icon
      />
      <div v-if="evaluationMetrics.length" class="evaluation-metrics">
        <div v-for="metric in evaluationMetrics" :key="metric.key" class="evaluation-metric">
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value }}</strong>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" header="资料处理任务" class="jobs-card">
      <el-table :data="jobs">
        <el-table-column label="材料编号" prop="materialId" width="100" />
        <el-table-column label="专家用户" prop="userId" width="110" />
        <el-table-column label="文件名称" prop="fileName" min-width="220" show-overflow-tooltip />
        <el-table-column label="处理状态" width="150">
          <template #default="{ row }">
            <el-tag :type="jobTagType(row)">{{ adminJobText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="问题数" prop="issueCount" width="90" align="center" />
        <el-table-column label="摘要" prop="agentMessage" min-width="260" show-overflow-tooltip />
        <el-table-column label="更新时间" prop="updateTime" width="170" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button v-if="row.jobStatus === 'FAILED'" link type="warning" @click="retry(row.materialId)">重试</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getJobs"
      />
    </el-card>
  </div>
</template>

<script setup name="ExpertAdmin">
import {
  getAdminSummary,
  getEvaluationDatasets,
  getKnowledgeGraphAgentRun,
  listAdminJobs,
  rebuildKnowledgeGraph,
  retryAgentJob,
  runQualityEvaluation,
  runKnowledgeGraphAgent
} from "@/api/expert/platform"

const router = useRouter()
const { proxy } = getCurrentInstance()
const loading = ref(false)
const summary = ref({})
const jobs = ref([])
const total = ref(0)
const queryParams = reactive({ pageNum: 1, pageSize: 10 })
const agentObjective = ref("")
const agentDemand = reactive({
  serviceDate: "",
  serviceRegion: "",
  serviceMode: "",
  industry: "",
  requiredCapabilities: ""
})
const agentRunning = ref(false)
const agentResult = ref(null)
const agentSteps = ref([])
const graphRebuilding = ref(false)
const evaluationDatasets = ref([])
const selectedDatasetId = ref(null)
const evaluationRunning = ref(false)
const evaluationResult = ref(null)

const selectedDataset = computed(() =>
  evaluationDatasets.value.find(item => item.datasetId === selectedDatasetId.value)
)
const evaluationMetrics = computed(() => {
  const summary = evaluationResult.value?.summary || {}
  return [
    ["fieldF1", "字段F1", "%"],
    ["entityF1", "实体F1", "%"],
    ["relationF1", "关系F1", "%"],
    ["conflictF1", "冲突F1", "%"],
    ["portraitCompleteness", "画像完整度", "%"],
    ["portraitEvidenceCoverage", "画像证据覆盖", "%"],
    ["relationEvidenceCoverage", "关系证据覆盖", "%"],
    ["evidenceLocationCoverage", "证据定位覆盖", "%"],
    ["crossMaterialConsistency", "跨材料一致性", "%"],
    ["crossMaterialComparableDimensions", "可比较维度", ""],
    ["overallQuality", "综合质量", "%"]
  ]
    .map(([key, label, suffix]) => {
      const value = summary[key]
      return {
        key,
        label,
        value: value == null ? "不可计算" : `${value}${suffix}`
      }
    })
})

function go(path) {
  router.push(path)
}

function loadData() {
  loading.value = true
  Promise.all([getAdminSummary(), listAdminJobs(queryParams)])
    .then(([summaryRes, jobsRes]) => {
      summary.value = summaryRes.data || {}
      jobs.value = jobsRes.rows || []
      total.value = jobsRes.total || 0
    })
    .finally(() => {
      loading.value = false
    })
}

function loadEvaluationDatasets() {
  getEvaluationDatasets().then(res => {
    evaluationDatasets.value = res.data || []
    if (!selectedDatasetId.value && evaluationDatasets.value.length) {
      selectedDatasetId.value = evaluationDatasets.value[0].datasetId
    }
  })
}

function getJobs() {
  listAdminJobs(queryParams).then(res => {
    jobs.value = res.rows || []
    total.value = res.total || 0
  })
}

function retry(materialId) {
  retryAgentJob(materialId).then(() => {
    proxy.$modal.msgSuccess("任务已重新排队")
    loadData()
  })
}

function runAgent() {
  agentRunning.value = true
  agentResult.value = null
  agentSteps.value = []
  runKnowledgeGraphAgent({
    objective: agentObjective.value.trim(),
    maxSteps: 6,
    serviceDate: agentDemand.serviceDate || null,
    serviceRegion: agentDemand.serviceRegion.trim() || null,
    serviceMode: agentDemand.serviceMode || null,
    industry: agentDemand.industry.trim() || null,
    requiredCapabilities: agentDemand.requiredCapabilities.trim() || null
  })
    .then(res => {
      agentResult.value = res.data || {}
      if (agentResult.value.runId) {
        return getKnowledgeGraphAgentRun(agentResult.value.runId)
      }
      return null
    })
    .then(res => {
      agentSteps.value = res?.data?.steps || []
    })
    .catch(error => {
      proxy.$modal.msgError(error?.message || "Agent 运行失败")
    })
    .finally(() => {
      agentRunning.value = false
    })
}

function stepSummary(row) {
  try {
    const observation = row.observationJson
      ? JSON.parse(row.observationJson)
      : row.observation || {}
    if (row.toolName === "expert_filter_available") {
      return `硬门禁通过 ${observation.eligibleCount || 0}/${observation.candidateCount || 0}`
    }
    if (row.toolName === "kg_search_experts") {
      return `图谱候选 ${observation.candidateCount || 0} 人`
    }
    if (row.toolName === "kg_find_paths") {
      return `匹配路径 ${observation.pathCount || 0} 条`
    }
    if (row.toolName === "kg_expand_evidence") {
      const relationEvidence = observation.relationEvidence?.length || 0
      const entityEvidence = observation.entityEvidence?.length || 0
      return `关系证据 ${relationEvidence} 条，实体证据 ${entityEvidence} 条`
    }
  } catch {
    return "-"
  }
  return "-"
}

function rebuildGraph() {
  proxy.$modal.confirm(
    "确认从 MySQL 源数据清空并重建 Neo4j 只读投影吗？该操作不会删除 MySQL 专家和证据数据。"
  ).then(() => {
    graphRebuilding.value = true
    return rebuildKnowledgeGraph()
  }).then(res => {
    if (res?.data?.status === "COMPLETED") {
      proxy.$modal.msgSuccess(`Neo4j 图谱重建完成，共 ${res.data.rebuiltExperts} 位专家`)
    } else {
      proxy.$modal.msgWarning(`Neo4j 图谱部分重建，失败 ${res?.data?.failedCount || 0} 位专家`)
    }
  }).catch(() => {}).finally(() => {
    graphRebuilding.value = false
  })
}

function runEvaluation() {
  evaluationRunning.value = true
  evaluationResult.value = null
  runQualityEvaluation(selectedDatasetId.value)
    .then(res => {
      evaluationResult.value = res.data || {}
      proxy.$modal.msgSuccess("质量评估完成")
    })
    .catch(error => {
      proxy.$modal.msgError(error?.message || "质量评估失败")
    })
    .finally(() => {
      evaluationRunning.value = false
    })
}

function jobText(status) {
  return {
    QUEUED: "排队中",
    EXTRACTING: "提取中",
    MASKING: "脱敏中",
    MAPPING: "映射中",
    PRE_REVIEWING: "初审中",
    REVIEW_PENDING: "待人工审核",
    PRE_REVIEW_PASSED: "待人工审核",
    REVIEWED: "处理已完成",
    FAILED: "处理失败"
  }[status] || status
}

function adminJobText(row) {
  if (row.reviewStatus === "APPROVED" && row.jobStatus === "REVIEWED") return "认证及处理完成";
  if (row.reviewStatus === "APPROVED") return "已认证 · " + jobText(row.jobStatus);
  if (row.reviewStatus === "RETURNED") return "退回补正";
  return jobText(row.jobStatus);
}

function jobTagType(row) {
  if (row.reviewStatus === "APPROVED") return "success";
  if (row.reviewStatus === "RETURNED") return "warning";
  if (row.jobStatus === "FAILED") return "danger";
  return "warning";
}

loadData()
loadEvaluationDatasets()
</script>

<style scoped lang="scss">
.hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 28px 30px;
  margin-bottom: 18px;
  border-radius: 12px;
  color: #fff;
  background: linear-gradient(125deg, #25344a, #3f596f 66%, #557d75);
  h2 {
    margin: 6px 0 8px;
  }
  p {
    max-width: 760px;
    margin: 0;
    opacity: .8;
    line-height: 1.7;
  }
  .eyebrow {
    font-size: 12px;
    letter-spacing: 2px;
    opacity: .68;
  }
}
.stat {
  margin-bottom: 16px;
  :deep(.el-card__body) {
    min-height: 110px;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  span {
    color: #7b8794;
  }
  strong {
    color: #213a57;
    font-size: 30px;
  }
  .danger {
    color: #c25353;
  }
}
.admin-links {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  .el-button {
    margin-left: 0;
  }
}
.jobs-card {
  margin-top: 16px;
}
.agent-card {
  margin-top: 16px;
}
.agent-demand {
  margin-bottom: 12px;
  row-gap: 10px;
}
.agent-capabilities {
  margin-bottom: 12px;
}
.agent-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 12px 0;
  span {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
}
.agent-answer {
  margin-top: 12px;
  padding: 14px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  strong {
    color: var(--el-text-color-primary);
  }
  p {
    margin: 8px 0 0;
    color: var(--el-text-color-regular);
    line-height: 1.8;
    white-space: pre-wrap;
  }
}
.agent-steps {
  margin-top: 12px;
}
.evaluation-card {
  margin-top: 16px;
}
.evaluation-controls {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;
  span {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
}
.evaluation-metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: 10px;
  margin-top: 14px;
}
.evaluation-metric {
  padding: 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  span {
    display: block;
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
  strong {
    display: block;
    margin-top: 6px;
    color: var(--el-color-primary);
    font-size: 22px;
  }
}
</style>
