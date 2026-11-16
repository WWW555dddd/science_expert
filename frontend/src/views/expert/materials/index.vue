<template>
  <div class="app-container">
    <el-card shadow="never" class="upload-card">
      <div class="upload-header">
        <div>
          <h3>专家认证进度</h3>
          <p>每个必选认证角度都需要审核通过，专家才会整体认证成功。材料更新后，该角度需要重新审核。</p>
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
                <small>V{{ angle.versionNo }} · {{ parseTime(angle.submitTime) }}</small>
                <small v-if="angle.reviewComment" class="review-comment">
                  退回意见：{{ angle.reviewComment }}
                </small>
                <small v-else-if="angle.latestReturnComment" class="review-comment">
                  上次退回意见：{{ angle.latestReturnComment }}
                </small>
                <el-button link type="primary" @click="handlePreview(angle)">预览最新文件</el-button>
              </template>
              <span v-else>尚未上传该角度材料</span>
            </div>
            <el-upload
              :action="uploadAction"
              :headers="uploadHeaders"
              :show-file-list="false"
              :before-upload="beforeUpload"
              :on-success="(response, file) => handleUploadSuccess(response, file, angle.categoryCode)"
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
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="never" class="material-list" header="资料处理记录">
      <el-table :data="materials" v-loading="loading">
        <el-table-column label="文件名称" prop="fileName" min-width="210" show-overflow-tooltip />
        <el-table-column label="材料类别" width="120">
          <template #default="{ row }">{{ categoryText(row.materialCategory) }}</template>
        </el-table-column>
        <el-table-column label="版本" width="80" align="center">
          <template #default="{ row }">V{{ row.versionNo }}</template>
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
        <el-table-column label="上传时间" prop="createTime" width="170">
          <template #default="{ row }">{{ parseTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handlePreview(row)">预览</el-button>
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
        <el-step title="字段映射" />
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
  </div>
</template>

<script setup name="ExpertMaterials">
import { getToken } from "@/utils/auth"
import {
  getExpertCertifications,
  listExpertMaterials,
  previewExpertMaterial,
  retryExpertMaterial,
  submitExpertMaterial
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
let timer
const uploadAction = import.meta.env.VITE_APP_BASE_API + "/expert/workspace/materials/upload"
const uploadHeaders = computed(() => ({ Authorization: "Bearer " + getToken() }))
const queryParams = reactive({ pageNum: 1, pageSize: 10 })
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

function beforeUpload(file) {
  const allowed = ["pdf", "docx", "txt", "csv", "md", "png", "jpg", "jpeg"]
  const ext = file.name.split(".").pop().toLowerCase()
  if (!allowed.includes(ext)) {
    proxy.$modal.msgError("仅支持 PDF、Word 和图片文件")
    return false
  }
  if (file.size > 10 * 1024 * 1024) {
    proxy.$modal.msgError("单个文件不能超过10MB")
    return false
  }
  return true
}

function handleUploadSuccess(response, file, categoryCode) {
  if (response.code !== 200) {
    proxy.$modal.msgError(response.msg || "文件上传失败")
    return
  }
  submitExpertMaterial({
    fileName: response.fileName || file.name,
    filePath: response.filePath,
    fileSize: file.size,
    materialCategory: categoryCode
  }).then(() => {
    proxy.$modal.msgSuccess("资料已提交，正在进入处理队列")
    getList()
    loadCertifications()
  })
}

function loadCertifications() {
  getExpertCertifications().then(res => {
    certifications.value = res.data || { angles: [] }
  })
}

function angleTagType(status) {
  return {
    APPROVED: "success",
    IN_REVIEW: "warning",
    NEEDS_UPDATE: "danger"
  }[status] || "info"
}

function certificationClass(status) {
  return {
    APPROVED: "angle-approved",
    IN_REVIEW: "angle-reviewing",
    NEEDS_UPDATE: "angle-rejected"
  }[status] || "angle-empty"
}

function angleButtonText(angle) {
  if (!angle.materialId) return "上传材料"
  if (angle.certificationStatus === "APPROVED") return "更新材料"
  if (angle.certificationStatus === "NEEDS_UPDATE") return "重新上传"
  return "替换材料"
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

async function handlePreview(row) {
  clearPreview()
  previewLoading.value = true
  previewOpen.value = true
  previewTitle.value = row.fileName || "材料预览"
  const fileType = String(row.fileType || "").toLowerCase()
  const textMode = ["docx", "txt", "md", "csv"].includes(fileType)
  try {
    const blob = await previewExpertMaterial(row.materialId, textMode ? "text" : "raw")
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

function categoryText(value) {
  return {
    IDENTITY: "身份证明",
    QUALIFICATION: "学历职称",
    CERTIFICATE: "职业资格",
    ACHIEVEMENT: "代表成果",
    OTHER: "其他材料"
  }[value] || value
}

function jobStatusText(value) {
  return {
    QUEUED: "排队中",
    EXTRACTING: "提取中",
    MASKING: "脱敏中",
    MAPPING: "映射中",
    PRE_REVIEWING: "初审中",
    REVIEW_PENDING: "等待人工审核",
    PRE_REVIEW_PASSED: "等待人工审核",
    REVIEWED: "处理已完成",
    FAILED: "处理失败"
  }[value] || value
}

function jobTagType(value) {
  if (value === "FAILED") return "danger"
  if (value === "REVIEW_PENDING" || value === "PRE_REVIEW_PASSED") return "success"
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
.material-list {
  margin-top: 16px;
}
.review-comment {
  color: #b25b5b;
  line-height: 1.6;
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
</style>
