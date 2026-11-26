<template>
  <div class="app-container risk-review-page">
    <div class="page-head">
      <div>
        <span class="eyebrow">RISK REVIEW</span>
        <h2>风险预警审核</h2>
        <p>处理投诉、低评价和信用风险事件。确认或忽略预警不会自动删除档案、退出专家或更改接单状态。</p>
      </div>
      <el-tag type="danger" effect="plain">人工审核</el-tag>
    </div>

    <el-form :inline="true" class="search-form">
      <el-form-item label="预警状态">
        <el-select v-model="queryParams.status" clearable placeholder="全部" style="width: 150px">
          <el-option label="待处理" value="OPEN" />
          <el-option label="已确认" value="ACKNOWLEDGED" />
          <el-option label="已忽略" value="DISMISSED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="loadList">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-card shadow="never">
      <el-table :data="risks" v-loading="loading" border>
        <el-table-column label="专家" width="130">
          <template #default="{ row }">{{ row.realName || ("用户 " + row.userId) }}</template>
        </el-table-column>
        <el-table-column label="信誉分" width="90" align="center">
          <template #default="{ row }">
            <el-tag
              :type="Number(row.creditScore ?? 100) < 60 ? 'danger' : 'success'"
              size="small"
            >
              {{ row.creditScore ?? 100 }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="风险类型" width="130">
          <template #default="{ row }">{{ riskTypeText(row.riskType) }}</template>
        </el-table-column>
        <el-table-column label="等级" width="90">
          <template #default="{ row }">
            <el-tag :type="severityTag(row.severity)" size="small">{{ row.severity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="风险标题" prop="title" min-width="220" show-overflow-tooltip />
        <el-table-column label="摘要" prop="summary" min-width="320" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="130" prop="sourceSystem" />
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button link type="primary" icon="View" @click="openDetail(row)">
              {{ row.status === "OPEN" ? "审核" : "查看" }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="loadList"
      />
    </el-card>

    <el-dialog v-model="dialogOpen" title="风险预警审核" width="720px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="专家">{{ current.realName || ("用户 " + current.userId) }}</el-descriptions-item>
        <el-descriptions-item label="当前信誉分">{{ current.creditScore ?? 100 }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ riskTypeText(current.riskType) }}</el-descriptions-item>
        <el-descriptions-item label="等级">{{ current.severity }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ current.sourceSystem }}</el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ current.title }}</el-descriptions-item>
        <el-descriptions-item label="摘要" :span="2">{{ current.summary }}</el-descriptions-item>
      </el-descriptions>
      <el-alert
        title="确认预警会按风险等级扣减信誉分；低于60分将自动禁止接单。忽略预警不扣分。"
        type="warning"
        :closable="false"
        show-icon
        class="risk-tip"
      />
      <el-form v-if="current.status === 'OPEN'" label-position="top">
        <el-form-item label="审核意见">
          <el-input v-model="reviewComment" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">关闭</el-button>
        <template v-if="current.status === 'OPEN'">
          <el-button type="info" plain :loading="reviewing" @click="submit('DISMISS')">忽略预警</el-button>
          <el-button type="primary" :loading="reviewing" @click="submit('ACKNOWLEDGE')">确认并记录</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="ExpertRiskReview">
import {
  getRiskReview,
  listRiskReviews,
  reviewRisk
} from "@/api/expert/platform"

const loading = ref(false)
const reviewing = ref(false)
const risks = ref([])
const total = ref(0)
const dialogOpen = ref(false)
const current = ref({})
const reviewComment = ref("")
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  status: "OPEN"
})

function loadList() {
  loading.value = true
  listRiskReviews(queryParams)
    .then(res => {
      risks.value = res.rows || []
      total.value = res.total || 0
    })
    .finally(() => {
      loading.value = false
    })
}

function resetQuery() {
  queryParams.status = "OPEN"
  queryParams.pageNum = 1
  loadList()
}

function openDetail(row) {
  dialogOpen.value = true
  reviewComment.value = ""
  getRiskReview(row.riskId).then(res => {
    current.value = res.data || {}
  })
}

function submit(action) {
  if (action === "DISMISS" && !reviewComment.value.trim()) {
    ElMessage.warning("忽略预警时必须填写审核意见")
    return
  }
  reviewing.value = true
  reviewRisk(current.value.riskId, {
    action,
    version: current.value.version,
    comment: reviewComment.value || undefined
  })
    .then(res => {
      const change = res.data?.creditChange
      if (change)
      {
        const blockedText = change.blocked ? "，专家已自动进入禁止接单状态" : ""
        ElMessage.success(
          `风险预警已确认，信誉分 ${change.beforeScore} → ${change.afterScore}${blockedText}`
        )
      }
      else
      {
        ElMessage.success("风险预警已忽略")
      }
      dialogOpen.value = false
      loadList()
    })
    .finally(() => {
      reviewing.value = false
    })
}

function riskTypeText(value) {
  return {
    COMPLAINT: "投诉",
    LOW_EVALUATION: "低评价",
    CREDIT_WARNING: "信用预警"
  }[value] || value
}

function severityTag(value) {
  return { HIGH: "danger", MEDIUM: "warning", LOW: "info" }[value] || "info"
}

function statusText(value) {
  return { OPEN: "待处理", ACKNOWLEDGED: "已确认", DISMISSED: "已忽略" }[value] || value
}

function statusTag(value) {
  return { OPEN: "danger", ACKNOWLEDGED: "success", DISMISSED: "info" }[value] || "info"
}

loadList()
</script>

<style scoped lang="scss">
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
  padding: 18px 22px;
  margin-bottom: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: linear-gradient(135deg, color-mix(in srgb, var(--el-color-danger) 7%, #fff), #fff 55%);
  h2 {
    margin: 5px 0 8px;
    color: #20384b;
  }
  p {
    margin: 0;
    color: var(--el-text-color-secondary);
    line-height: 1.65;
  }
}
.eyebrow {
  color: var(--el-color-danger);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .12em;
}
.search-form {
  padding: 16px 16px 0;
  margin-bottom: 16px;
  border-radius: 10px;
  background: #fff;
}
.risk-tip {
  margin-top: 16px;
}
</style>
