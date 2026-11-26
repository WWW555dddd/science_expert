<template>
  <div class="app-container review-home" v-loading="loading">
    <div class="hero">
      <div>
        <span class="eyebrow">REVIEW WORKSPACE</span>
        <h2>专家审核工作台</h2>
        <p>查看模型或规则初审结果，逐份核验认证材料并完成审核决定。</p>
      </div>
      <div class="hero-actions">
        <el-button type="primary" size="large" icon="List" @click="go('/review-center/queue')">
          进入审核队列
        </el-button>
        <el-button size="large" icon="Switch" @click="go('/review-center/status-review')">
          专家状态与信誉申请 {{ summary.pendingStatusRequestCount || 0 }}
        </el-button>
      </div>
    </div>
    <el-row :gutter="16">
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat">
          <span>待完成认证专家</span>
          <strong>{{ summary.pendingCount || 0 }}</strong>
          <small>等待领取或处理</small>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat">
          <span>处理异常</span>
          <strong class="danger">{{ summary.agentFailureCount || 0 }}</strong>
          <small>需要重试或人工介入</small>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat">
          <span>正式专家库</span>
          <strong>{{ summary.approvedCount || 0 }}</strong>
          <small>已审核入库</small>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat">
          <span>暂停/限制</span>
          <strong>{{ summary.pausedCount || 0 }}</strong>
          <small>当前不可正常接单</small>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" header="最近的资料处理任务">
      <el-table :data="jobs" empty-text="暂无处理任务">
        <el-table-column label="材料编号" prop="materialId" width="100" />
        <el-table-column label="文件名称" prop="fileName" min-width="220" show-overflow-tooltip />
        <el-table-column label="最新结果" width="150">
          <template #default="{ row }">
            <el-tag :type="taskTagType(row)">
              {{ taskText(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提取字段" prop="extractedFields" width="110" align="center" />
        <el-table-column label="预审问题" prop="issueCount" width="110" align="center" />
        <el-table-column label="处理摘要" prop="agentMessage" min-width="280" show-overflow-tooltip />
        <el-table-column label="更新时间" prop="updateTime" width="170" />
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button v-if="row.jobStatus === 'FAILED'" link type="warning" @click="retry(row)">重试</el-button>
            <el-button
              v-else-if="['REVIEW_PENDING', 'PRE_REVIEW_PASSED'].includes(row.jobStatus)"
              link
              type="primary"
              @click="review(row)"
            >
              去审核
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup name="ReviewWorkspace">
import { getReviewSummary, listReviewJobs, retryAgentJob } from "@/api/expert/platform"

const router = useRouter()
const { proxy } = getCurrentInstance()
const loading = ref(false)
const summary = ref({})
const jobs = ref([])

function go(path) {
  router.push(path)
}

function loadData() {
  loading.value = true
  Promise.all([getReviewSummary(), listReviewJobs({ pageNum: 1, pageSize: 8 })])
    .then(([summaryRes, jobsRes]) => {
      summary.value = summaryRes.data || {}
      jobs.value = jobsRes.rows || []
    })
    .finally(() => {
      loading.value = false
    })
}

function retry(row) {
  retryAgentJob(row.materialId).then(() => {
    proxy.$modal.msgSuccess("处理任务已重新排队")
    loadData()
  })
}

function review(row) {
  router.push({
    path: "/review-center/queue",
    query: { userId: row.userId, materialId: row.materialId }
  })
}

function jobText(status) {
  return {
    QUEUED: "排队中",
    EXTRACTING: "提取中",
    MASKING: "脱敏中",
    MAPPING: "映射中",
    PRE_REVIEWING: "初审中",
    REVIEW_PENDING: "等待人工审核",
    PRE_REVIEW_PASSED: "等待人工审核",
    REVIEWED: "人工审核已完成",
    FAILED: "处理失败"
  }[status] || status
}

function taskText(row) {
  if (row.reviewStatus === "APPROVED") return "审核通过";
  if (row.reviewStatus === "RETURNED") return "退回补正";
  if (row.reviewStatus === "REJECTED") return "退回补正";
  return jobText(row.jobStatus);
}

function taskTagType(row) {
  if (row.reviewStatus === "APPROVED") return "success";
  if (row.reviewStatus === "RETURNED") return "warning";
  if (row.reviewStatus === "REJECTED") return "warning";
  if (row.jobStatus === "FAILED") return "danger";
  if (row.jobStatus === "REVIEW_PENDING" || row.jobStatus === "PRE_REVIEW_PASSED") return "primary";
  return "warning";
}

loadData()
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
  background: linear-gradient(125deg, #273c75, #487eb0 70%, #5fa8a4);
  h2 {
    margin: 6px 0 8px;
  }
  p {
    margin: 0;
    opacity: .82;
  }
  .eyebrow {
    font-size: 12px;
    letter-spacing: 2px;
    opacity: .7;
  }
  .hero-actions {
    display: flex;
    gap: 10px;
  }
}
.stat {
  margin-bottom: 16px;
  :deep(.el-card__body) {
    min-height: 126px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  span, small {
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
</style>
