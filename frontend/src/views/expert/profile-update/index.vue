<template>
  <div class="app-container profile-update-review">
    <div class="page-head">
      <div>
        <span class="eyebrow">PROFILE UPDATE REVIEW</span>
        <h2>画像更新审核</h2>
        <p>审核由服务报告、评价和信用事实生成的画像更新建议。批准后才生成新的生效画像版本。</p>
      </div>
      <el-tag effect="plain">管理员审核</el-tag>
    </div>

    <el-form :inline="true" class="search-form">
      <el-form-item label="审核状态">
        <el-select v-model="queryParams.status" clearable placeholder="全部" style="width: 160px">
          <el-option label="待审核" value="PENDING" />
          <el-option label="已批准" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-card shadow="never">
      <el-table :data="suggestions" v-loading="loading" border>
        <el-table-column label="专家" width="140">
          <template #default="{ row }">{{ row.realName || ("用户 " + row.userId) }}</template>
        </el-table-column>
        <el-table-column label="证据ID" prop="evidenceId" width="100" />
        <el-table-column label="建议摘要" min-width="360" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.reviewedSummary || row.summary }}
            <el-tag v-if="row.reviewedSummary" type="warning" size="small">审核已修订</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="置信度" width="100" align="center">
          <template #default="{ row }">{{ confidenceText(row.confidence) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生成时间" prop="createTime" width="170" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" icon="View" @click="openDetail(row)">
              {{ row.status === "PENDING" ? "审核" : "查看" }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>

    <el-dialog
      v-model="dialogOpen"
      :title="`画像更新建议 #${current.suggestionId || ''}`"
      width="900px"
      append-to-body
      destroy-on-close
    >
      <div v-loading="detailLoading">
        <el-alert
          :title="displaySummary"
          :type="current.status === 'REJECTED' ? 'error' : current.status === 'APPROVED' ? 'success' : 'warning'"
          :closable="false"
          show-icon
          class="suggestion-summary"
        />
        <el-alert
          v-if="current.reviewedSummary && current.reviewedSummary !== current.summary"
          :title="`AI原始摘要：${current.summary}`"
          type="info"
          :closable="false"
          class="original-summary"
        />
        <el-form
          v-if="current.status === 'PENDING' && editingOpinions"
          label-position="top"
          class="opinion-editor"
        >
          <el-form-item label="审核修订摘要（批准后用于画像发布）">
            <el-input
              v-model="current.reviewedSummary"
              type="textarea"
              :rows="3"
              maxlength="2000"
              show-word-limit
            />
          </el-form-item>
        </el-form>
        <el-descriptions :column="5" border>
          <el-descriptions-item label="专家">{{ current.realName || ("用户 " + current.userId) }}</el-descriptions-item>
          <el-descriptions-item label="来源证据">#{{ current.evidenceId }}</el-descriptions-item>
          <el-descriptions-item label="建议置信度">{{ confidenceText(current.confidence) }}</el-descriptions-item>
          <el-descriptions-item label="生成方式">{{ generationText(current.generationMode) }}</el-descriptions-item>
          <el-descriptions-item label="模型">{{ current.modelName || "—" }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusText(current.status) }}</el-descriptions-item>
        </el-descriptions>

        <el-table :data="current.items || []" border class="item-table">
          <el-table-column label="画像维度" min-width="180">
            <template #default="{ row }">
              <strong>{{ row.dimensionName }}</strong>
              <div class="dimension-code">{{ row.dimensionCode }}</div>
            </template>
          </el-table-column>
          <el-table-column label="当前分" prop="beforeScore" width="90" align="center" />
          <el-table-column label="建议分" prop="suggestedScore" width="90" align="center" />
          <el-table-column label="批准分" width="150" align="center">
            <template #default="{ row }">
              <el-input-number
                v-if="current.status === 'PENDING' && editingOpinions"
                v-model="row.approvedScore"
                :min="0"
                :max="100"
                :step="1"
                :precision="2"
                controls-position="right"
                style="width: 120px"
              />
              <span v-else>{{ row.approvedScore ?? row.suggestedScore ?? "—" }}</span>
            </template>
          </el-table-column>
          <el-table-column label="AI/审核理由" min-width="260">
            <template #default="{ row }">
              <el-input
                v-if="current.status === 'PENDING' && editingOpinions"
                v-model="row.reviewedReason"
                type="textarea"
                :rows="3"
                maxlength="1000"
                show-word-limit
              />
              <template v-else>
                <div>{{ row.reviewedReason || row.reason }}</div>
                <small v-if="row.reviewedReason && row.reviewedReason !== row.reason">
                  AI原意见：{{ row.reason }}
                </small>
              </template>
            </template>
          </el-table-column>
          <el-table-column label="证据" prop="evidence" min-width="260" show-overflow-tooltip />
        </el-table>

        <div v-if="current.status === 'PENDING'" class="review-form">
          <el-form label-position="top">
            <el-form-item label="审核意见">
              <el-input
                v-model="reviewComment"
                type="textarea"
                :rows="3"
                placeholder="驳回时必填；批准时可选"
              />
            </el-form-item>
          </el-form>
        </div>
        <el-descriptions v-else-if="current.reviewerName" :column="3" border class="review-result">
          <el-descriptions-item label="审核人">{{ current.reviewerName }}</el-descriptions-item>
          <el-descriptions-item label="审核意见">{{ current.reviewComment || "无" }}</el-descriptions-item>
          <el-descriptions-item label="生成版本">
            {{ current.releaseId ? "审核版本 #" + current.releaseId : "未生成" }}
          </el-descriptions-item>
        </el-descriptions>
        <el-collapse v-if="current.agentOutputJson" class="agent-output">
          <el-collapse-item title="Agent结构化输出与审计" name="output">
            <pre>{{ prettyAgentOutput }}</pre>
          </el-collapse-item>
        </el-collapse>
      </div>
      <template #footer>
        <el-button @click="dialogOpen = false">关闭</el-button>
        <template v-if="current.status === 'PENDING'">
          <el-button
            v-if="!editingOpinions"
            type="primary"
            plain
            icon="Edit"
            @click="startEditingOpinions"
          >
            编辑AI意见
          </el-button>
          <template v-else>
            <el-button icon="Close" @click="cancelEditingOpinions">取消编辑</el-button>
            <el-button
              type="success"
              plain
              :loading="savingOpinions"
              @click="saveOpinionEdits"
            >
              保存修改
            </el-button>
          </template>
          <el-button type="danger" plain :loading="reviewing" @click="submitReview('REJECT')">
            驳回
          </el-button>
          <el-button type="primary" :loading="reviewing" @click="submitReview('APPROVE')">
            批准并生成画像版本
          </el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="ExpertProfileUpdateReview">
import {
  getProfileUpdateSuggestion,
  listProfileUpdateSuggestions,
  reviewProfileUpdateSuggestion
} from "@/api/expert/platform"

const loading = ref(false)
const detailLoading = ref(false)
const reviewing = ref(false)
const savingOpinions = ref(false)
const editingOpinions = ref(false)
const editSnapshot = ref(null)
const suggestions = ref([])
const total = ref(0)
const dialogOpen = ref(false)
const current = ref({})
const reviewComment = ref("")
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  status: "PENDING"
})
const displaySummary = computed(() =>
  current.value.reviewedSummary || current.value.summary || ""
)
const prettyAgentOutput = computed(() => {
  if (!current.value.agentOutputJson) return ""
  try {
    return JSON.stringify(JSON.parse(current.value.agentOutputJson), null, 2)
  }
  catch {
    return current.value.agentOutputJson
  }
})

function getList() {
  loading.value = true
  listProfileUpdateSuggestions(queryParams)
    .then(res => {
      suggestions.value = res.rows || []
      total.value = res.total || 0
    })
    .finally(() => {
      loading.value = false
    })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.status = "PENDING"
  handleQuery()
}

function openDetail(row) {
  dialogOpen.value = true
  detailLoading.value = true
  reviewComment.value = ""
  editingOpinions.value = false
  editSnapshot.value = null
  getProfileUpdateSuggestion(row.suggestionId)
    .then(res => {
      current.value = res.data || {}
      current.value.reviewedSummary = current.value.reviewedSummary || current.value.summary
      current.value.items = (current.value.items || []).map(item => ({
        ...item,
        approvedScore: item.approvedScore ?? item.suggestedScore,
        reviewedReason: item.reviewedReason || item.reason
      }))
    })
    .finally(() => {
      detailLoading.value = false
    })
}

function startEditingOpinions() {
  editSnapshot.value = {
    summary: current.value.reviewedSummary,
    reasons: Object.fromEntries(
      (current.value.items || []).map(item => [item.itemId, item.reviewedReason])
    )
  }
  editingOpinions.value = true
}

function cancelEditingOpinions() {
  if (editSnapshot.value) {
    current.value.reviewedSummary = editSnapshot.value.summary
    const items = current.value.items || []
    items.forEach(item => {
      item.reviewedReason = editSnapshot.value.reasons[item.itemId]
    })
  }
  editingOpinions.value = false
  editSnapshot.value = null
}

function reviewPayload(action) {
  return {
    action,
    version: current.value.version,
    comment: reviewComment.value || undefined,
    reviewedSummary: current.value.reviewedSummary || undefined,
    items: (current.value.items || []).map(item => ({
      itemId: item.itemId,
      approvedScore: item.approvedScore,
      reviewedReason: item.reviewedReason || undefined
    }))
  }
}

function saveOpinionEdits() {
  if (!current.value.reviewedSummary?.trim()) {
    ElMessage.warning("审核修订摘要不能为空")
    return
  }
  if ((current.value.items || []).some(item => !item.reviewedReason?.trim())) {
    ElMessage.warning("审核修订理由不能为空")
    return
  }
  savingOpinions.value = true
  reviewProfileUpdateSuggestion(
    current.value.suggestionId,
    reviewPayload("SAVE")
  )
    .then(res => {
      current.value = res.data || current.value
      current.value.reviewedSummary = current.value.reviewedSummary || current.value.summary
      current.value.items = (current.value.items || []).map(item => ({
        ...item,
        approvedScore: item.approvedScore ?? item.suggestedScore,
        reviewedReason: item.reviewedReason || item.reason
      }))
      editingOpinions.value = false
      editSnapshot.value = null
      ElMessage.success("AI意见修改已保存")
      getList()
    })
    .finally(() => {
      savingOpinions.value = false
    })
}

function submitReview(action) {
  if (action === "REJECT" && !reviewComment.value.trim()) {
    ElMessage.warning("驳回时必须填写审核意见")
    return
  }
  reviewing.value = true
  reviewProfileUpdateSuggestion(
    current.value.suggestionId,
    reviewPayload(action)
  )
    .then(res => {
      current.value = res.data || current.value
      editingOpinions.value = false
      ElMessage.success(action === "APPROVE" ? "已批准并生成新画像版本" : "已驳回")
      getList()
    })
    .finally(() => {
      reviewing.value = false
    })
}

function confidenceText(value) {
  return `${Math.round(Number(value || 0) * 100)}%`
}

function statusText(value) {
  return { PENDING: "待审核", APPROVED: "已批准", REJECTED: "已驳回" }[value] || value
}

function statusTag(value) {
  return { PENDING: "warning", APPROVED: "success", REJECTED: "danger" }[value] || "info"
}

function generationText(value) {
  return {
    AGENT: "Agent实时分析",
    RULE_FALLBACK: "规则降级",
    SEEDED: "模拟导入"
  }[value] || value || "未知"
}

getList()
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
  background: linear-gradient(135deg, color-mix(in srgb, var(--el-color-primary) 8%, #fff), #fff 55%);
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
  color: var(--el-color-primary);
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
.suggestion-summary {
  margin-bottom: 16px;
}
.original-summary,
.opinion-editor {
  margin-bottom: 16px;
}
.item-table {
  margin-top: 16px;
}
.dimension-code {
  margin-top: 3px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.item-table small {
  display: block;
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}
.review-form,
.review-result {
  margin-top: 18px;
}
.agent-output {
  margin-top: 18px;
  pre {
    max-height: 360px;
    overflow: auto;
    padding: 12px;
    border-radius: 8px;
    background: var(--el-fill-color-light);
    font-size: 12px;
    white-space: pre-wrap;
  }
}
</style>
