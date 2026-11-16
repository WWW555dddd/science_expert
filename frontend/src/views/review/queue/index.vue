<template>
  <div class="app-container">
    <el-form :inline="true" :model="queryParams" class="search-form">
      <el-form-item label="专家姓名">
        <el-input v-model="queryParams.realName" clearable placeholder="请输入姓名" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="专业方向">
        <el-input v-model="queryParams.researchField" clearable placeholder="请输入专业方向" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="审核状态">
        <el-select v-model="queryParams.reviewStatus" clearable placeholder="全部状态" style="width: 150px">
          <el-option label="待审核" value="PENDING_REVIEW" />
          <el-option label="审核中" value="REVIEWING" />
          <el-option label="退回补正" value="RETURNED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-card shadow="never">
      <el-table :data="queue" v-loading="loading">
        <el-table-column label="专家" width="130">
          <template #default="{ row }">
            <strong>{{ row.realName }}</strong>
            <div class="sub-text">{{ row.userName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="单位/职称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.organization || "未填写单位" }}</div>
            <div class="sub-text">{{ row.professionalTitle || "未填写职称" }}</div>
          </template>
        </el-table-column>
        <el-table-column label="专业/产业" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.researchField || "未填写专业方向" }}</div>
            <div class="sub-text">{{ row.industryField || "未填写产业领域" }}</div>
          </template>
        </el-table-column>
        <el-table-column label="初审状态" width="130">
          <template #default="{ row }">
            <el-tag :type="row.jobStatus === 'FAILED' ? 'danger' : row.issueCount ? 'warning' : 'success'">
              {{ row.jobStatus === "FAILED" ? "处理失败" : row.issueCount ? `${row.issueCount}个问题` : "未发现缺项" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="认证进度" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.certification?.fullyCertified ? 'success' : 'warning'">
              {{ row.certification?.passedRequired || 0 }}/{{ row.certification?.requiredTotal || 0 }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="120">
          <template #default="{ row }">
            <el-tag :type="reviewTag(row.reviewStatus)">{{ reviewText(row.reviewStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" prop="latestSubmitTime" width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" icon="View" @click="openDetail(row.userId)">审核</el-button>
            <el-button
              v-if="row.jobStatus === 'FAILED' && row.materialId"
              link
              type="warning"
              @click="retry(row.materialId)"
            >重试</el-button>
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

    <el-drawer v-model="detailOpen" title="专家审核详情" size="88%" destroy-on-close>
      <div v-loading="detailLoading" class="review-detail">
        <el-row :gutter="16">
          <el-col :xs="24" :lg="16">
            <el-card shadow="never" header="专家档案">
              <el-descriptions :column="3" border>
                <el-descriptions-item label="姓名">{{ detail.profile?.realName }}</el-descriptions-item>
                <el-descriptions-item label="账号">{{ detail.profile?.userName }}</el-descriptions-item>
                <el-descriptions-item label="档案完成度">{{ detail.profile?.profileCompletion || 0 }}%</el-descriptions-item>
                <el-descriptions-item label="所属单位" :span="2">{{ detail.profile?.organization || "未填写" }}</el-descriptions-item>
                <el-descriptions-item label="职称">{{ detail.profile?.professionalTitle || "未填写" }}</el-descriptions-item>
                <el-descriptions-item label="专业方向">{{ detail.profile?.researchField || "未填写" }}</el-descriptions-item>
                <el-descriptions-item label="产业领域">{{ detail.profile?.industryField || "未填写" }}</el-descriptions-item>
                <el-descriptions-item label="服务区域">{{ detail.profile?.serviceRegion || "未填写" }}</el-descriptions-item>
                <el-descriptions-item label="联系电话">{{ detail.profile?.phone || "未填写" }}</el-descriptions-item>
                <el-descriptions-item label="电子邮箱">{{ detail.profile?.email || "未填写" }}</el-descriptions-item>
                <el-descriptions-item label="专家简介" :span="3">{{ detail.profile?.careerSummary || "未填写" }}</el-descriptions-item>
              </el-descriptions>
            </el-card>

            <el-card shadow="never" header="认证角度进度" class="detail-block">
              <div class="cert-summary">
                <strong>{{ detail.certification?.overallStatusText }}</strong>
                <span>必选角度通过 {{ detail.certification?.passedRequired || 0 }}/{{ detail.certification?.requiredTotal || 0 }}</span>
              </div>
              <div class="cert-angle-list">
                <div v-for="angle in detail.certification?.angles || []" :key="angle.categoryCode" class="cert-angle">
                  <span>{{ angle.angleName }}</span>
                  <el-tag :type="angleTagType(angle.certificationStatus)" size="small">
                    {{ angle.certificationStatusText }}
                  </el-tag>
                  <small v-if="angle.materialId">V{{ angle.versionNo }}</small>
                </div>
              </div>
            </el-card>

            <el-card shadow="never" header="认证材料审核" class="detail-block">
              <el-table
                :data="detail.materials || []"
                border
                highlight-current-row
                :current-row-key="selectedMaterialId"
                row-key="materialId"
                @row-click="selectMaterial"
              >
                <el-table-column label="认证角度" width="120">
                  <template #default="{ row }">{{ categoryText(row.materialCategory) }}</template>
                </el-table-column>
                <el-table-column label="文件" prop="fileName" min-width="180" show-overflow-tooltip />
                <el-table-column label="版本" width="70">
                  <template #default="{ row }">V{{ row.versionNo }}</template>
                </el-table-column>
                <el-table-column label="初审" width="120">
                  <template #default="{ row }">{{ jobText(row.jobStatus) }}</template>
                </el-table-column>
                <el-table-column label="审核结果" width="110">
                  <template #default="{ row }">
                    <el-tag :type="materialReviewTag(row.reviewStatus)">{{ materialReviewText(row.reviewStatus) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="90" align="center">
                  <template #default="{ row }">
                    <el-button link type="primary" @click.stop="handleReviewPreview(row)">预览</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-card>

            <el-card shadow="never" header="字段映射" class="detail-block">
              <el-table :data="selectedMappings" border size="small" empty-text="当前材料暂无字段映射">
                <el-table-column label="标准字段" prop="dbField" width="150" />
                <el-table-column label="文件字段" prop="fileField" width="130" />
                <el-table-column label="提取值" prop="extractedValue" min-width="180" show-overflow-tooltip />
                <el-table-column label="置信度" width="100">
                  <template #default="{ row }">{{ Math.round(Number(row.confidence) * 100) }}%</template>
                </el-table-column>
                <el-table-column label="证据位置" prop="evidence" min-width="150" />
              </el-table>
            </el-card>

            <el-card shadow="never" header="可服务时间表" class="detail-block">
              <el-table :data="detail.schedules || []" border size="small">
                <el-table-column label="星期" width="100">
                  <template #default="{ row }">{{ weekdayText(row.weekday) }}</template>
                </el-table-column>
                <el-table-column label="可服务时间" width="150">
                  <template #default="{ row }">{{ row.startTime }} - {{ row.endTime }}</template>
                </el-table-column>
                <el-table-column label="服务方式" width="120">
                  <template #default="{ row }">{{ serviceModeText(row.serviceMode) }}</template>
                </el-table-column>
                <el-table-column label="每周上限" prop="weeklyCapacity" width="110" />
                <el-table-column label="生效日期" prop="effectiveFrom" />
              </el-table>
            </el-card>

            <el-card shadow="never" header="能力画像（临时）" class="detail-block">
              <el-tag
                v-for="item in detail.graph || []"
                :key="item.dimensionCode"
                :type="item.dataStatus === 'AVAILABLE' ? 'success'
                  : item.dataStatus === 'CONFLICT' ? 'danger' : 'info'"
                class="dimension-tag"
                effect="plain"
              >
                {{ item.dimensionName }}
                {{ item.dataStatus === "AVAILABLE" ? Math.round(Number(item.score || 0))
                  : item.dataStatus === "CONFLICT" ? `冲突 ${Math.round(Number(item.score || 0))}`
                    : "证据不足" }}
              </el-tag>
            </el-card>
          </el-col>

          <el-col :xs="24" :lg="8">
            <el-card shadow="never" header="初审结果">
              <el-alert
                :type="selectedMaterial?.issueCount ? 'warning' : 'success'"
                :title="selectedMaterial?.issueCount ? `发现 ${selectedMaterial.issueCount} 个问题` : '未发现明显缺项'"
                :description="selectedMaterial?.agentMessage || '暂无处理结果'"
                :closable="false"
                show-icon
              />
              <div class="material-link">
                <span>当前审核材料</span>
                <span v-if="selectedMaterial?.fileName">
                  {{ categoryText(selectedMaterial.materialCategory) }} · {{ selectedMaterial.fileName }}
                </span>
                <span v-else>无</span>
                <span>原文受控存储，当前展示脱敏字段</span>
              </div>
            </el-card>

            <el-card shadow="never" header="审核决定" class="detail-block">
              <el-alert
                v-if="selectedMaterial && !selectedMaterialReviewable"
                :title="reviewBlockedText"
                type="info"
                :closable="false"
                show-icon
                class="review-lock-tip"
              />
              <el-form label-position="top">
                <el-form-item label="审核意见">
                  <el-input v-model="reviewForm.comment" type="textarea" :rows="5" maxlength="1000" show-word-limit />
                </el-form-item>
                <div class="review-buttons">
                  <el-button
                    type="success"
                    :loading="submitting"
                    :disabled="!selectedMaterialReviewable"
                    @click="submitAction('PASS')"
                    v-hasPermi="['review:action']"
                  >
                    通过当前认证角度
                  </el-button>
                  <el-button
                    type="warning"
                    :loading="submitting"
                    :disabled="!selectedMaterialReviewable"
                    @click="submitAction('RETURN')"
                    v-hasPermi="['review:action']"
                  >
                    退回补正
                  </el-button>
                </div>
              </el-form>
            </el-card>

            <el-card shadow="never" header="接单状态处理" class="detail-block">
              <el-alert
                v-if="!canAcceptOrders"
                title="专家尚未正式认证通过，接单状态锁定为待认证，不能选择其他状态。"
                type="warning"
                :closable="false"
                show-icon
                class="review-lock-tip"
              />
              <el-form label-position="top">
                <el-form-item label="目标状态">
                  <el-select
                    v-model="statusForm.serviceStatus"
                    :disabled="!canAcceptOrders"
                    style="width: 100%"
                  >
                    <el-option label="正常接单" value="AVAILABLE" :disabled="!canAcceptOrders" />
                    <el-option label="暂停接单" value="PAUSED" />
                    <el-option label="待认证" value="PENDING_CERTIFICATION" disabled />
                    <el-option label="退出专家库" value="EXITED" />
                  </el-select>
                </el-form-item>
                <el-form-item label="处理原因">
                  <el-input v-model="statusForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit />
                </el-form-item>
                <el-checkbox v-model="statusForm.creditRelated">信用相关问题</el-checkbox>
                <el-button
                  class="status-submit"
                  type="warning"
                  @click="changeStatus"
                  v-hasPermi="['review:action']"
                >调整接单状态</el-button>
              </el-form>
            </el-card>

            <el-card shadow="never" header="审核记录" class="detail-block">
              <el-timeline v-if="detail.reviewLogs?.length">
                <el-timeline-item
                  v-for="log in detail.reviewLogs"
                  :key="log.logId"
                  :timestamp="log.createTime"
                >
                  <strong>{{ reviewActionText(log.action) }} · {{ log.reviewerName }}</strong>
                  <p>{{ log.comment || "无审核意见" }}</p>
                </el-timeline-item>
              </el-timeline>
              <el-empty v-else description="暂无审核记录" :image-size="60" />
            </el-card>
          </el-col>
        </el-row>
      </div>
    </el-drawer>

    <el-dialog
      v-model="previewOpen"
      :title="previewTitle"
      width="86%"
      top="4vh"
      append-to-body
      destroy-on-close
      @closed="clearPreview"
    >
      <div v-loading="previewLoading" class="preview-body">
        <img v-if="previewKind === 'image'" :src="previewUrl" class="preview-image" alt="审核材料预览" />
        <iframe v-else-if="previewKind === 'pdf'" :src="previewUrl" class="preview-pdf" title="PDF审核预览" />
        <pre v-else-if="previewKind === 'text'" class="preview-text">{{ previewText }}</pre>
        <el-empty v-else description="当前文件格式暂不支持浏览器内预览" />
      </div>
      <template #footer>
        <el-button @click="previewOpen = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="ReviewQueue">
import {
  changeReviewedExpertStatus,
  getReviewDetail,
  listReviewQueue,
  previewReviewMaterial,
  retryAgentJob,
  submitReviewAction
} from "@/api/expert/platform"

const { proxy } = getCurrentInstance()
const loading = ref(false)
const detailLoading = ref(false)
const submitting = ref(false)
const queue = ref([])
const total = ref(0)
const detailOpen = ref(false)
const detail = ref({})
const currentUserId = ref()
const selectedMaterialId = ref()
const previewOpen = ref(false)
const previewLoading = ref(false)
const previewKind = ref("")
const previewUrl = ref("")
const previewText = ref("")
const previewTitle = ref("审核材料预览")
const reviewForm = reactive({ comment: "" })
const statusForm = reactive({
  serviceStatus: "PAUSED",
  reason: "",
  creditRelated: true
})
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  realName: undefined,
  researchField: undefined,
  reviewStatus: undefined
})
const selectedMaterial = computed(() =>
  (detail.value.materials || []).find(item => item.materialId === selectedMaterialId.value)
)
const selectedMappings = computed(() =>
  detail.value.mappingsByMaterial?.[selectedMaterialId.value] || []
)
const canAcceptOrders = computed(() =>
  detail.value.profile?.reviewStatus === "APPROVED"
  && Boolean(detail.value.certification?.fullyCertified)
)
const selectedMaterialReviewable = computed(() => {
  const material = selectedMaterial.value
  if (!material || !["PENDING", "PENDING_REVIEW"].includes(material.reviewStatus)) return false
  if (!["REVIEW_PENDING", "PRE_REVIEW_PASSED"].includes(material.jobStatus)) return false
  const latest = (detail.value.materials || []).find(item =>
    item.materialCategory === material.materialCategory
  )
  return latest?.materialId === material.materialId
})
const reviewBlockedText = computed(() => {
  const material = selectedMaterial.value
  if (!material) return "请先选择需要审核的材料"
  if (!["PENDING", "PENDING_REVIEW"].includes(material.reviewStatus)) {
    return "该材料已经处理，审核结果不可重复修改"
  }
  if (!["REVIEW_PENDING", "PRE_REVIEW_PASSED"].includes(material.jobStatus)) {
    return "该材料仍在处理中，暂时不能审核"
  }
  return "这是历史版本，仅支持查看，不可审核"
})
function getList() {
  loading.value = true
  listReviewQueue(queryParams)
    .then(res => {
      queue.value = res.rows || []
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
  Object.assign(queryParams, {
    pageNum: 1,
    realName: undefined,
    researchField: undefined,
    reviewStatus: undefined
  })
  getList()
}

function openDetail(userId) {
  currentUserId.value = userId
  detailOpen.value = true
  reviewForm.comment = ""
  selectedMaterialId.value = undefined
  statusForm.reason = ""
  statusForm.serviceStatus = "PAUSED"
  statusForm.creditRelated = true
  loadDetail()
}

function loadDetail() {
  detailLoading.value = true
  getReviewDetail(currentUserId.value)
    .then(res => {
      detail.value = res.data || {}
      const materials = detail.value.materials || []
      const nextMaterial = materials.find(item =>
        item.reviewStatus !== "APPROVED"
        && ["REVIEW_PENDING", "PRE_REVIEW_PASSED"].includes(item.jobStatus)
      ) || materials.find(item => item.reviewStatus !== "APPROVED") || materials[0]
      selectedMaterialId.value = nextMaterial?.materialId
      statusForm.serviceStatus = canAcceptOrders.value
        ? (detail.value.profile?.serviceStatus || "PAUSED")
        : "PENDING_CERTIFICATION"
    })
    .finally(() => {
      detailLoading.value = false
    })
}

function submitAction(action) {
  if (!selectedMaterial.value) {
    proxy.$modal.msgWarning("请先选择需要审核的材料")
    return
  }
  if (action !== "PASS" && !reviewForm.comment.trim()) {
    proxy.$modal.msgWarning("退回补正时请填写审核意见")
    return
  }
  const text = { PASS: "通过当前认证角度", RETURN: "退回补正" }[action]
  proxy.$modal.confirm(`是否确认${text}？`).then(() => {
    submitting.value = true
    return submitReviewAction(currentUserId.value, {
      materialId: selectedMaterial.value.materialId,
      action,
      comment: reviewForm.comment
    })
  }).then(() => {
    proxy.$modal.msgSuccess("审核操作已完成")
    getList()
    loadDetail()
  }).finally(() => {
    submitting.value = false
  }).catch(() => {})
}

function selectMaterial(row) {
  selectedMaterialId.value = row.materialId
  reviewForm.comment = ""
}

async function handleReviewPreview(row) {
  clearPreview()
  previewLoading.value = true
  previewOpen.value = true
  previewTitle.value = row.fileName || "审核材料预览"
  const fileType = String(row.fileType || "").toLowerCase()
  const textMode = ["docx", "txt", "md", "csv"].includes(fileType)
  try {
    const blob = await previewReviewMaterial(row.materialId, textMode ? "text" : "raw")
    if (textMode) {
      previewKind.value = "text"
      previewText.value = await blob.text()
    }
    else if (["png", "jpg", "jpeg", "gif", "webp"].includes(fileType)) {
      previewKind.value = "image"
      previewUrl.value = URL.createObjectURL(blob)
    }
    else if (fileType === "pdf") {
      previewKind.value = "pdf"
      previewUrl.value = URL.createObjectURL(blob)
    }
    else {
      previewKind.value = "unsupported"
    }
  }
  catch {
    previewKind.value = "unsupported"
  }
  finally {
    previewLoading.value = false
  }
}

function clearPreview() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
  previewUrl.value = ""
  previewText.value = ""
  previewKind.value = ""
}

function retry(materialId) {
  retryAgentJob(materialId).then(() => {
    proxy.$modal.msgSuccess("处理任务已重新排队")
    getList()
  })
}

function changeStatus() {
  if (!canAcceptOrders.value) {
    proxy.$modal.msgWarning("专家尚未正式认证通过，接单状态锁定为待认证")
    return
  }
  if (!statusForm.reason.trim()) {
    proxy.$modal.msgWarning("请填写接单状态处理原因")
    return
  }
  changeReviewedExpertStatus(currentUserId.value, statusForm).then(() => {
    proxy.$modal.msgSuccess("专家接单状态已更新")
    loadDetail()
    getList()
  })
}

function reviewText(status) {
  return { PENDING_REVIEW: "待审核", REVIEWING: "审核中", RETURNED: "退回补正", APPROVED: "已通过", REJECTED: "退回补正" }[status] || status
}

function reviewTag(status) {
  return { PENDING_REVIEW: "warning", REVIEWING: "primary", RETURNED: "warning", APPROVED: "success", REJECTED: "warning" }[status] || "info"
}

function reviewActionText(action) {
  return { PASS: "审核通过", RETURN: "退回补正", REJECT: "退回补正", CLAIM: "领取审核" }[action] || action
}

function angleTagType(status) {
  return {
    APPROVED: "success",
    IN_REVIEW: "warning",
    NEEDS_UPDATE: "danger"
  }[status] || "info"
}

function materialReviewText(status) {
  return {
    PENDING: "待审核",
    PENDING_REVIEW: "待审核",
    APPROVED: "已通过",
    RETURNED: "需补正",
    REJECTED: "退回补正"
  }[status] || status || "待审核"
}

function materialReviewTag(status) {
  return {
    APPROVED: "success",
    PENDING_REVIEW: "info",
    RETURNED: "warning",
    REJECTED: "warning"
  }[status] || "info"
}

function categoryText(value) {
  return {
    IDENTITY: "身份证明",
    QUALIFICATION: "学历职称",
    CERTIFICATE: "职业资格",
    ACHIEVEMENT: "代表成果",
    OTHER: "其他材料"
  }[value] || value
}

function jobText(status) {
  return {
    QUEUED: "排队中",
    EXTRACTING: "提取中",
    MASKING: "脱敏中",
    MAPPING: "映射中",
    PRE_REVIEWING: "初审中",
    REVIEW_PENDING: "待审核",
    PRE_REVIEW_PASSED: "待审核",
    REVIEWED: "审核已完成",
    FAILED: "处理失败"
  }[status] || status
}

function weekdayText(day) {
  return ["", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"][day] || day
}

function serviceModeText(mode) {
  return { REMOTE: "远程", ONSITE: "现场", BOTH: "均可" }[mode] || mode
}

getList()
onUnmounted(clearPreview)
</script>

<style scoped lang="scss">
.search-form {
  padding: 16px 16px 0;
  margin-bottom: 16px;
  background: #fff;
  border-radius: 8px;
}
.sub-text {
  margin-top: 4px;
  color: #8a97a3;
  font-size: 12px;
}
.review-detail {
  padding: 0 8px 24px;
}
.detail-block {
  margin-top: 16px;
}
.dimension-tag {
  margin: 0 8px 8px 0;
}
.material-link {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 18px;
  color: #7b8794;
}
.cert-summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  strong {
    color: #243d55;
  }
  span {
    color: #7b8794;
  }
}
.cert-angle-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.cert-angle {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #f6f8fa;
  span {
    flex: 1;
    color: #465665;
  }
  small {
    color: #8a97a3;
  }
}
.review-buttons {
  display: flex;
  flex-direction: column;
  gap: 10px;
  .el-button {
    width: 100%;
    margin-left: 0;
  }
}
.status-submit {
  width: 100%;
  margin-top: 14px;
}
.review-lock-tip {
  margin-bottom: 16px;
}
.preview-body {
  min-height: 420px;
  background: #f5f7f9;
  border-radius: 8px;
  overflow: hidden;
}
.preview-image {
  display: block;
  max-width: 100%;
  max-height: 75vh;
  margin: 0 auto;
  object-fit: contain;
}
.preview-pdf {
  width: 100%;
  height: 75vh;
  border: 0;
  background: #fff;
}
.preview-text {
  min-height: 420px;
  max-height: 75vh;
  margin: 0;
  padding: 24px;
  overflow: auto;
  color: #26384a;
  font-family: "Microsoft YaHei", monospace;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
  background: #fff;
}
</style>
