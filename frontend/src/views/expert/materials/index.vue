<template>
  <div class="app-container">
    <el-card shadow="never" class="upload-card">
      <el-alert
        v-for="reminder in qualificationReminders"
        :key="reminder.reminderId"
        class="qualification-alert"
        :type="reminder.reminderType === 'EXPIRED' ? 'error' : 'warning'"
        :closable="false"
        show-icon
        :title="reminder.reminderType === 'EXPIRED'
          ? `${reminder.angleName}资质已过期，已暂停接单资格`
          : `${reminder.angleName}资质将在 ${Math.max(0, reminder.daysRemaining)} 天后到期`"
        :description="`${reminder.fileName} · 到期日 ${reminder.expiryDate}`"
      />
      <div class="upload-header">
        <div>
          <h3>专家认证进度</h3>
          <p>每个必选认证角度都需要审核通过。已有认证角度可维护版本内文件，逐份增加或删除，保存后整组重新审核。</p>
        </div>
        <el-tag :type="certTagType" effect="dark" size="large">{{ certifications.overallStatusText }}</el-tag>
      </div>
      <div class="cert-progress">
        <el-progress
          :percentage="certPercentage"
          :stroke-width="12"
          :status="certifications.fullyCertified ? 'success' : undefined"
        />
        <span>已通过 {{ certifications.passedRequired || 0 }} / {{ certifications.requiredTotal || 0 }} 个必选角度</span>
      </div>
      <el-row :gutter="14" class="angle-grid">
        <el-col v-for="angle in certifications.angles || []" :key="angle.categoryCode" :xs="24" :sm="12" :lg="8">
          <el-card shadow="never" class="angle-card" :class="certificationClass(angle.certificationStatus)">
            <div class="angle-heading">
              <div>
                <strong>{{ angle.angleName }}</strong>
                <el-tag v-if="angle.requiredFlag === '1'" size="small" type="danger" effect="plain">必选</el-tag>
              </div>
              <el-tag :type="angleTagType(angle.certificationStatus)">
                {{ angle.certificationStatusText }}
              </el-tag>
            </div>
            <div class="angle-file">
              <template v-if="angle.materialId">
                <span>{{ angle.fileName }}</span>
                <small>V{{ angle.versionNo }} · {{ angle.fileCount || 1 }} 份文件 · {{ parseTime(angle.submitTime) }}</small>
                <small v-if="angle.effectiveMaterialId && angle.effectiveMaterialId !== angle.materialId">
                  当前生效：V{{ angle.effectiveVersionNo }} · {{ angle.effectiveFileName }}
                </small>
                <small v-else-if="angle.reviewStatus === 'APPROVED'">
                  当前生效版本
                </small>
                <small v-if="angle.validityRequired === '1'" class="validity-summary">
                  审核有效期：{{ validityRemainingText(angle) }}
                </small>
                <small v-if="angle.reviewComment" class="review-comment">
                  退回意见：{{ angle.reviewComment }}
                </small>
                <small v-else-if="angle.latestReturnComment" class="review-comment">
                  上次退回意见：{{ angle.latestReturnComment }}
                </small>
              </template>
              <span v-else>尚未上传该角度材料</span>
            </div>
            <el-upload
              v-if="!angle.materialId"
              :action="uploadAction"
              :headers="uploadHeaders"
              :show-file-list="false"
              :before-upload="file => beforeUpload(file, angle)"
              :on-success="(response, file) => handleUploadSuccess(response, file, angle)"
              :on-error="handleUploadError"
              accept=".pdf,.docx,.txt,.csv,.md,.png,.jpg,.jpeg"
            >
              <el-button
                :type="angle.certificationStatus === 'NEEDS_UPDATE' ? 'warning' : 'primary'"
                plain
                icon="Upload"
                v-hasPermi="['expert:material:add']"
              >
                {{ angleButtonText(angle) }}
              </el-button>
            </el-upload>
            <el-button
              v-else
              type="primary"
              plain
              icon="FolderOpened"
              v-hasPermi="['expert:material:add']"
              @click="openFileDrawer(angle)"
            >
              维护版本文件
            </el-button>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="never" class="material-list" header="资料处理记录">
      <el-table :data="materials" v-loading="loading">
        <el-table-column label="文件名称" prop="fileName" min-width="210" show-overflow-tooltip />
        <el-table-column label="文件数" width="80" align="center">
          <template #default="{ row }">{{ row.fileCount || 1 }}</template>
        </el-table-column>
        <el-table-column label="审核有效期" width="210" align="center">
          <template #default="{ row }">
            {{ validityRemainingText(row) }}
          </template>
        </el-table-column>
        <el-table-column label="材料类别" width="120">
          <template #default="{ row }">{{ categoryText(row.materialCategory) }}</template>
        </el-table-column>
        <el-table-column label="版本" width="80" align="center">
          <template #default="{ row }">
            <div class="version-cell">
              <span>V{{ row.versionNo }}</span>
              <el-tag v-if="row.effectiveFlag === '1'" type="success" size="small" effect="plain">生效</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="处理进度" width="170">
          <template #default="{ row }">
            <el-tag :type="jobTagType(row.jobStatus)">{{ jobStatusText(row.jobStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="130">
          <template #default="{ row }">
            <el-tag :type="reviewTagType(row.reviewStatus)">{{ reviewStatusText(row.reviewStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核/退回意见" prop="reviewComment" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'review-comment': row.reviewStatus === 'RETURNED' || row.reviewStatus === 'REJECTED' }">
              {{ row.reviewComment || "暂无意见" }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="提取字段" prop="extractedFields" width="100" align="center" />
        <el-table-column label="预审问题" width="100" align="center">
          <template #default="{ row }">
            <el-badge :value="row.issueCount || 0" :hidden="!row.issueCount" type="warning">
              <span class="issue-count">{{ row.issueCount || 0 }}</span>
            </el-badge>
          </template>
        </el-table-column>
        <el-table-column label="处理摘要" prop="agentMessage" min-width="260" show-overflow-tooltip />
        <el-table-column label="最近更新" prop="updateTime" width="170">
          <template #default="{ row }">{{ parseTime(row.updateTime || row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">进度</el-button>
            <el-button
              v-if="row.jobStatus === 'FAILED'"
              link
              type="warning"
              @click="retry(row)"
              v-hasPermi="['expert:material:retry']"
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

    <el-dialog v-model="detailOpen" title="资料处理进度" width="700px">
      <el-steps :active="jobStep(currentMaterial.jobStatus)" finish-status="success" align-center>
        <el-step title="排队" />
        <el-step title="提取脱敏" />
        <el-step title="基本信息提取" />
        <el-step title="模型/规则初审" />
        <el-step title="人工审核" />
      </el-steps>
      <el-descriptions :column="2" border class="detail-info">
        <el-descriptions-item label="文件">{{ currentMaterial.fileName }}</el-descriptions-item>
        <el-descriptions-item label="类别">{{ categoryText(currentMaterial.materialCategory) }}</el-descriptions-item>
        <el-descriptions-item label="处理状态">{{ jobStatusText(currentMaterial.jobStatus) }}</el-descriptions-item>
        <el-descriptions-item label="审核状态">{{ reviewStatusText(currentMaterial.reviewStatus) }}</el-descriptions-item>
        <el-descriptions-item label="处理摘要" :span="2">{{ currentMaterial.agentMessage }}</el-descriptions-item>
        <el-descriptions-item label="审核意见" :span="2">{{ currentMaterial.reviewComment || "暂无" }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

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
        <img v-if="previewKind === 'image'" :src="previewUrl" class="preview-image" alt="材料预览" />
        <iframe v-else-if="previewKind === 'pdf'" :src="previewUrl" class="preview-pdf" title="PDF预览" />
        <pre v-else-if="previewKind === 'text'" class="preview-text">{{ previewText }}</pre>
        <el-empty v-else description="当前文件格式暂不支持浏览器内预览" />
      </div>
      <template #footer>
        <el-button @click="previewOpen = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="fileDrawerOpen"
      :title="`维护 ${fileAngle.angleName || '认证材料'} · V${fileAngle.versionNo || 1}`"
      size="720px"
      destroy-on-close
    >
      <div v-loading="fileLoading" class="file-maintainer">
        <el-alert
          title="新增或删除只作用于当前版本，不会覆盖已有文件。保存后整组文件会重新进入审核。"
          type="info"
          :closable="false"
          show-icon
        />
        <div class="file-toolbar">
          <span>当前版本共 {{ versionFiles.length }} 份文件</span>
          <el-upload
            :action="uploadAction"
            :headers="uploadHeaders"
            :show-file-list="false"
            :before-upload="file => beforeUpload(file, fileAngle)"
            :on-success="handleFileAddSuccess"
            :on-error="handleUploadError"
            accept=".pdf,.docx,.txt,.csv,.md,.png,.jpg,.jpeg"
          >
            <el-button type="primary" icon="Plus" v-hasPermi="['expert:material:add']">
              增加文件
            </el-button>
          </el-upload>
        </div>
        <el-table :data="versionFiles" border empty-text="当前版本暂无文件">
          <el-table-column type="index" label="序号" width="60" align="center" />
          <el-table-column label="文件名称" prop="fileName" min-width="260" show-overflow-tooltip />
          <el-table-column label="类型" prop="fileType" width="80" align="center" />
          <el-table-column label="大小" width="110">
            <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" align="center">
            <template #default="{ row }">
              <el-button link type="primary" @click="handleVersionFilePreview(row)">预览</el-button>
              <el-button
                link
                type="danger"
                :disabled="versionFiles.length <= 1"
                @click="handleDeleteVersionFile(row)"
                v-hasPermi="['expert:material:add']"
              >删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="fileDrawerOpen = false">关闭</el-button>
        <el-button
          type="primary"
          :loading="fileSubmitting"
          :disabled="!fileDirty || versionFiles.length === 0"
          @click="saveVersionFiles"
        >
          保存并提交审核
        </el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup name="ExpertMaterials">
import { getToken } from "@/utils/auth"
import {
  addExpertMaterialFile,
  deleteExpertMaterialFile,
  getExpertCertifications,
  getQualificationReminders,
  listExpertMaterials,
  listExpertMaterialFiles,
  previewExpertMaterialFile,
  retryExpertMaterial,
  submitExpertMaterial,
  submitExpertMaterialRevision
} from "@/api/expert/platform"

const { proxy } = getCurrentInstance()
const loading = ref(false)
const materials = ref([])
const total = ref(0)
const certifications = ref({ angles: [], requiredTotal: 0, passedRequired: 0, overallStatusText: "尚未开始认证" })
const detailOpen = ref(false)
const currentMaterial = ref({})
const previewOpen = ref(false)
const previewLoading = ref(false)
const previewKind = ref("")
const previewUrl = ref("")
const previewText = ref("")
const previewTitle = ref("材料预览")
const fileDrawerOpen = ref(false)
const fileLoading = ref(false)
const fileSubmitting = ref(false)
const fileAngle = ref({})
const baseVersionFiles = ref([])
const versionFiles = ref([])
const pendingAdditions = ref([])
const pendingDeletions = ref([])
const submitRetryNeeded = ref(false)
const qualificationReminders = ref([])
let timer
const uploadAction = import.meta.env.VITE_APP_BASE_API + "/expert/workspace/materials/upload"
const uploadHeaders = computed(() => ({ Authorization: "Bearer " + getToken() }))
const queryParams = reactive({ pageNum: 1, pageSize: 10 })
const fileDirty = computed(() => pendingAdditions.value.length > 0
  || pendingDeletions.value.length > 0
  || submitRetryNeeded.value)
const certPercentage = computed(() => {
  const totalRequired = Number(certifications.value.requiredTotal || 0)
  return totalRequired ? Math.round(Number(certifications.value.passedRequired || 0) * 100 / totalRequired) : 0
})
const certTagType = computed(() => ({
  CERTIFIED: "success",
  NEEDS_UPDATE: "danger",
  IN_PROGRESS: "warning",
  NOT_STARTED: "info"
})[certifications.value.overallStatus] || "info")

const terminalStatuses = ["REVIEW_PENDING", "PRE_REVIEW_PASSED", "REVIEWED", "FAILED"]

function getList() {
  loading.value = true
  listExpertMaterials(queryParams)
    .then(res => {
      materials.value = res.rows || []
      total.value = res.total || 0
      const running = materials.value.some(item => !terminalStatuses.includes(item.jobStatus))
      clearTimeout(timer)
      if (running) timer = setTimeout(getList, 4000)
      loadCertifications()
    })
    .finally(() => {
      loading.value = false
    })
}

function beforeUpload(file, angle) {
  const allowed = ["pdf", "docx", "txt", "csv", "md", "png", "jpg", "jpeg"]
  const ext = file.name.split(".").pop().toLowerCase()
  if (!allowed.includes(ext)) {
    proxy.$modal.msgError("仅支持 PDF、Word、文本和图片文件")
    return false
  }
  if (file.size > 10 * 1024 * 1024) {
    proxy.$modal.msgError("单个文件不能超过10MB")
    return false
  }
  return true
}

function handleUploadSuccess(response, file, angle) {
  if (response.code !== 200) {
    proxy.$modal.msgError(response.msg || "文件上传失败")
    return
  }
  submitExpertMaterial({
    materialId: angle.materialId || undefined,
    fileName: response.fileName || file.name,
    filePath: response.filePath,
    fileSize: response.fileSize || file.size,
    materialCategory: angle.categoryCode
  }).then(saved => {
    const versionNo = saved.data?.versionNo
    const isFirstUpload = !angle.materialId
    proxy.$modal.msgSuccess(isFirstUpload
      ? `材料已创建为 V${versionNo || 1}，正在进入处理队列`
      : `文件已加入原版本 V${versionNo}，正在重新审核`)
    getList()
    loadCertifications()
  })
}

function openFileDrawer(angle) {
  fileAngle.value = angle
  fileDrawerOpen.value = true
  pendingAdditions.value = []
  pendingDeletions.value = []
  submitRetryNeeded.value = false
  loadVersionFiles()
}

function loadVersionFiles() {
  if (!fileAngle.value.materialId) return
  fileLoading.value = true
  return listExpertMaterialFiles(fileAngle.value.materialId)
    .then(res => {
      baseVersionFiles.value = res.data || []
      rebuildVersionFiles()
    })
    .finally(() => {
      fileLoading.value = false
    })
}

function rebuildVersionFiles() {
  const deleted = new Set(pendingDeletions.value)
  versionFiles.value = [
    ...baseVersionFiles.value.filter(file => !deleted.has(file.fileId)),
    ...pendingAdditions.value
  ]
}

function handleFileAddSuccess(response, file) {
  if (response.code !== 200) {
    proxy.$modal.msgError(response.msg || "文件上传失败")
    return
  }
  pendingAdditions.value.push({
    fileId: -Date.now(),
    pending: true,
    fileName: response.fileName || file.name,
    filePath: response.filePath,
    fileSize: response.fileSize || file.size,
    fileType: String(response.fileName || file.name).split(".").pop().toLowerCase()
  })
  rebuildVersionFiles()
  proxy.$modal.msgSuccess("文件已暂存，点击“保存并提交审核”后生效")
}

function handleDeleteVersionFile(file) {
  proxy.$modal.confirm(`确认从当前版本删除“${file.fileName}”吗？`)
    .then(() => {
      if (file.pending) {
        pendingAdditions.value = pendingAdditions.value
          .filter(item => item.fileId !== file.fileId)
      }
      else if (!pendingDeletions.value.includes(file.fileId)) {
        pendingDeletions.value.push(file.fileId)
      }
      rebuildVersionFiles()
      proxy.$modal.msgSuccess("文件删除已暂存，点击“保存并提交审核”后生效")
    })
    .catch(() => {})
}

async function saveVersionFiles() {
  if (!fileDirty.value || versionFiles.value.length === 0) return
  fileSubmitting.value = true
  try {
    for (const file of [...pendingAdditions.value]) {
      await addExpertMaterialFile(fileAngle.value.materialId, {
        fileName: file.fileName,
        filePath: file.filePath,
        fileSize: file.fileSize
      })
      pendingAdditions.value = pendingAdditions.value
        .filter(item => item.fileId !== file.fileId)
    }
    for (const fileId of [...pendingDeletions.value]) {
      await deleteExpertMaterialFile(fileAngle.value.materialId, fileId)
      pendingDeletions.value = pendingDeletions.value.filter(id => id !== fileId)
    }
    await submitExpertMaterialRevision(fileAngle.value.materialId)
    proxy.$modal.msgSuccess(`当前版本已提交审核，共 ${versionFiles.value.length} 份文件`)
    fileDrawerOpen.value = false
    pendingAdditions.value = []
    pendingDeletions.value = []
    submitRetryNeeded.value = false
    getList()
    loadCertifications()
  }
  catch {
    if (pendingAdditions.value.length === 0 && pendingDeletions.value.length === 0) {
      submitRetryNeeded.value = true
    }
    await loadVersionFiles()
  }
  finally {
    fileSubmitting.value = false
  }
}

function loadCertifications() {
  getExpertCertifications().then(res => {
    certifications.value = res.data || { angles: [] }
  })
  getQualificationReminders().then(res => {
    qualificationReminders.value = res.data || []
  })
}

function angleTagType(status) {
  return {
    APPROVED: "success",
    EXPIRING_SOON: "warning",
    EXPIRED: "danger",
    IN_REVIEW: "warning",
    UPDATE_RETURNED: "warning",
    NEEDS_UPDATE: "danger"
  }[status] || "info"
}

function certificationClass(status) {
  return {
    APPROVED: "angle-approved",
    EXPIRING_SOON: "angle-reviewing",
    EXPIRED: "angle-rejected",
    IN_REVIEW: "angle-reviewing",
    UPDATE_RETURNED: "angle-reviewing",
    NEEDS_UPDATE: "angle-rejected"
  }[status] || "angle-empty"
}

function angleButtonText(angle) {
  if (!angle.materialId) return "上传材料"
  if (["NEEDS_UPDATE", "EXPIRED"].includes(angle.certificationStatus)) return "更新资质材料"
  return "更新原文件版本"
}

function handleUploadError() {
  proxy.$modal.msgError("文件上传失败，请检查后端文件服务")
}

function retry(row) {
  retryExpertMaterial(row.materialId).then(() => {
    proxy.$modal.msgSuccess("任务已重新排队")
    getList()
  })
}

function showDetail(row) {
  currentMaterial.value = row
  detailOpen.value = true
}

async function showPreviewBlob(blob, fileType, textMode) {
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

async function handleVersionFilePreview(file) {
  clearPreview()
  previewLoading.value = true
  previewOpen.value = true
  previewTitle.value = file.fileName || "版本文件预览"
  const fileType = String(file.fileType || "").toLowerCase()
  const textMode = ["docx", "txt", "md", "csv"].includes(fileType)
  try {
    const blob = await previewExpertMaterialFile(fileAngle.value.materialId, file.fileId,
      textMode ? "text" : "raw")
    await showPreviewBlob(blob, fileType, textMode)
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

function formatFileSize(size) {
  const value = Number(size || 0)
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

function validityRemainingText(item) {
  if (!item?.validUntil) return "审核通过后自动生成，有效期3个月"
  const expiryText = String(item.validUntil).slice(0, 10)
  const expiry = new Date(`${expiryText}T00:00:00`)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const remainingDays = Math.ceil((expiry.getTime() - today.getTime()) / 86400000)
  if (remainingDays < 0) {
    return `有效期至 ${expiryText} · 已过期 ${Math.abs(remainingDays)} 天`
  }
  if (remainingDays === 0) return `有效期至 ${expiryText} · 今天到期`
  return `有效期至 ${expiryText} · 剩余 ${remainingDays} 天`
}

function categoryText(value) {
  return {
    IDENTITY: "身份证明",
    QUALIFICATION: "学历职称",
    CERTIFICATE: "职业资格",
    ACHIEVEMENT: "代表成果",
    SERVICE_EXPERIENCE: "服务经历",
    OTHER: "利益冲突说明"
  }[value] || value
}

function jobStatusText(value) {
  if (["QUEUED", "EXTRACTING", "MASKING", "MAPPING", "PRE_REVIEWING",
    "REVIEW_PENDING", "PRE_REVIEW_PASSED"].includes(value)) {
    return "已提交审核"
  }
  return {
    REVIEWED: "审核完毕",
    FAILED: "处理失败"
  }[value] || value
}

function jobTagType(value) {
  if (value === "FAILED") return "danger"
  if (value === "REVIEWED") return "success"
  return "warning"
}

function reviewStatusText(value) {
  return {
    PENDING: "待处理",
    PENDING_REVIEW: "待审核",
    APPROVED: "已通过",
    RETURNED: "退回补正",
    REJECTED: "退回补正"
  }[value] || value || "待处理"
}

function reviewTagType(value) {
  return { APPROVED: "success", RETURNED: "warning", REJECTED: "warning" }[value] || "info"
}

function jobStep(value) {
  return {
    QUEUED: 1,
    EXTRACTING: 1,
    MASKING: 2,
    MAPPING: 2,
    PRE_REVIEWING: 3,
    REVIEW_PENDING: 4,
    PRE_REVIEW_PASSED: 4
  }[value] || 0
}

onUnmounted(() => {
  clearTimeout(timer)
  clearPreview()
})
getList()
loadCertifications()
</script>

<style scoped lang="scss">
.upload-header {
  display: flex;
  align-items: center;
  gap: 16px;
  h3 {
    margin: 0 0 8px;
  }
  p {
    margin: 0;
    color: #7b8794;
  }
  > div:first-child {
    flex: 1;
  }
}
.qualification-alert {
  margin-bottom: 12px;
}
.cert-progress {
  display: flex;
  align-items: center;
  gap: 18px;
  margin: 8px 0 20px;
  color: #6f7d89;
  .el-progress {
    flex: 1;
  }
}
.angle-grid {
  .el-col {
    margin-bottom: 14px;
  }
}
.angle-card {
  min-height: 220px;
  border-top: 4px solid #b7c1ca;
  &.angle-approved {
    border-top-color: #36a77c;
  }
  &.angle-reviewing {
    border-top-color: #d59a32;
  }
  &.angle-rejected {
    border-top-color: #cf5b5b;
  }
  :deep(.el-card__body) {
    display: flex;
    min-height: 216px;
    flex-direction: column;
  }
}
.angle-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  strong {
    margin-right: 6px;
    color: #213a57;
  }
}
.angle-file {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 7px;
  padding: 18px 0;
  color: #5f6f7c;
  small {
    color: #8b97a1;
  }
  .review-comment {
    color: #b25b5b;
    line-height: 1.6;
  }
}
.validity-field {
  margin-bottom: 12px;
}
.validity-summary {
  color: #b57916 !important;
}
.material-list {
  margin-top: 16px;
}
.review-comment {
  color: #b25b5b;
  line-height: 1.6;
}
.version-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}
.issue-count {
  display: inline-block;
  min-width: 24px;
  text-align: center;
}
.detail-info {
  margin-top: 28px;
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
.file-maintainer {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.file-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #5f6f7c;
}
</style>
