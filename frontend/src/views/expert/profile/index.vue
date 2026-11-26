<template>
  <div class="app-container" v-loading="loading">
    <el-row :gutter="16">
      <el-col :xs="24" :lg="15">
        <el-card shadow="never">
          <template #header>
            <div class="profile-card-header">
              <span>专家基本档案</span>
              <div class="word-import-actions">
                <el-button plain icon="Download" @click="downloadWordTemplate">下载Word模板</el-button>
                <el-upload
                  :action="wordImportAction"
                  :headers="uploadHeaders"
                  :show-file-list="false"
                  accept=".docx"
                  :before-upload="beforeWordImport"
                  :on-success="handleWordImportSuccess"
                  :on-error="handleWordImportError"
                  v-hasPermi="['expert:profile:edit']"
                >
                  <el-button type="primary" plain icon="Upload" :loading="wordImporting">
                    导入Word模板
                  </el-button>
                </el-upload>
              </div>
            </div>
          </template>
          <el-form ref="profileRef" :model="profileForm" :rules="rules" label-width="96px">
            <el-alert
              type="info"
              :closable="false"
              show-icon
              title="可下载标准Word模板填写专家基本信息。导入后只会回填当前表单，请核对内容并点击“保存档案”。"
              class="word-import-tip"
            />
            <el-row :gutter="16">
              <el-col :sm="12">
                <el-form-item label="专家姓名" prop="realName">
                  <el-input v-model="profileForm.realName" maxlength="64" />
                </el-form-item>
              </el-col>
              <el-col :sm="12">
                <el-form-item label="所属单位">
                  <el-input v-model="profileForm.organization" maxlength="128" />
                </el-form-item>
              </el-col>
              <el-col :sm="12">
                <el-form-item label="职称职务">
                  <el-input v-model="profileForm.professionalTitle" maxlength="64" />
                </el-form-item>
              </el-col>
              <el-col :sm="12">
                <el-form-item label="服务区域">
                  <el-input v-model="profileForm.serviceRegion" maxlength="128" />
                </el-form-item>
              </el-col>
              <el-col :sm="12">
                <el-form-item label="专业方向">
                  <el-input v-model="profileForm.researchField" maxlength="255" />
                </el-form-item>
              </el-col>
              <el-col :sm="12">
                <el-form-item label="产业领域">
                  <el-input v-model="profileForm.industryField" maxlength="255" />
                </el-form-item>
              </el-col>
              <el-col :sm="12">
                <el-form-item label="联系电话">
                  <el-input v-model="profileForm.phone" maxlength="32" />
                </el-form-item>
              </el-col>
              <el-col :sm="12">
                <el-form-item label="电子邮箱" prop="email">
                  <el-input v-model="profileForm.email" maxlength="128" />
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item label="专家简介">
                  <el-input v-model="profileForm.careerSummary" type="textarea" :rows="5" maxlength="2000" show-word-limit />
                </el-form-item>
              </el-col>
            </el-row>
            <div class="form-actions">
              <el-button type="primary" v-hasPermi="['expert:profile:edit']" @click="saveProfile">保存档案</el-button>
            </div>
          </el-form>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="9">
        <el-card shadow="never" header="档案状态">
          <div class="completion">
            <el-progress type="dashboard" :percentage="Math.min(100, Number(profileForm.profileCompletion || 0))" />
            <div>
              <h3>{{ reviewStatusText }}</h3>
              <p>档案和重要资料变更后，需要重新进入审核流程。</p>
            </div>
          </div>
          <el-divider />
          <el-descriptions :column="1">
            <el-descriptions-item label="画像版本">V{{ profileForm.graphVersion || 0 }}</el-descriptions-item>
            <el-descriptions-item label="最近提交">{{ parseTime(profileForm.latestSubmitTime) || "尚未提交" }}</el-descriptions-item>
            <el-descriptions-item label="接单状态">{{ serviceStatusText }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="schedule-card" header="我的时间安排">
      <ExpertScheduleGantt
        v-model="scheduleForm"
        date-mode
        :unavailable-periods="unavailablePeriods"
        :occupancy-periods="occupancyPeriods"
        @refresh-occupancy="loadOccupancy"
      />
      <div class="form-actions">
        <el-button type="success" v-hasPermi="['expert:profile:edit']" @click="saveSchedule">保存日常可联系时段</el-button>
      </div>
      <ExpertUnavailablePeriods @update:periods="handleUnavailablePeriods" />
    </el-card>

    <el-card shadow="never" class="schedule-card">
      <ExpertVersionHistory />
    </el-card>
  </div>
</template>

<script setup name="ExpertProfile">
import {
  downloadExpertProfileImportTemplate,
  getExpertProfile,
  getExpertSchedules,
  getExpertOccupancyPeriods,
  saveExpertProfile,
  saveExpertSchedules
} from "@/api/expert/platform"
import Download from "@/plugins/download"
import { getToken } from "@/utils/auth"
import ExpertScheduleGantt from "@/components/ExpertScheduleGantt/index.vue"
import ExpertUnavailablePeriods from "@/components/ExpertUnavailablePeriods/index.vue"
import ExpertVersionHistory from "@/components/ExpertVersionHistory/index.vue"

const { proxy } = getCurrentInstance()
const loading = ref(false)
const wordImporting = ref(false)
const profileRef = ref()
const profileForm = ref({})
const scheduleForm = ref([])
const unavailablePeriods = ref([])
const occupancyPeriods = ref([])
const wordImportAction = import.meta.env.VITE_APP_BASE_API + "/expert/workspace/profile/import"
const uploadHeaders = computed(() => ({ Authorization: "Bearer " + getToken() }))
const rules = {
  realName: [{ required: true, message: "专家姓名不能为空", trigger: "blur" }],
  email: [{ type: "email", message: "电子邮箱格式不正确", trigger: "blur" }]
}
const reviewStatusText = computed(() => ({
  DRAFT: "档案草稿",
  PENDING_REVIEW: "审核中",
  RETURNED: "退回补正",
  REJECTED: "未通过",
  APPROVED: "已审核入库"
})[profileForm.value.reviewStatus] || "档案草稿")
const serviceStatusText = computed(() => ({
  AVAILABLE: "正常接单",
  PAUSED: "暂停接单",
  PENDING_CERTIFICATION: "待认证，暂不能接单",
    CREDIT_BLOCKED: "信誉不足，禁止接单",
  LIMITED: "暂停接单（历史状态）",
  EXITED: "已退出"
})[profileForm.value.serviceStatus] || "待认证，暂不能接单")

function loadData() {
  loading.value = true
  Promise.all([getExpertProfile(), getExpertSchedules(), loadOccupancy()])
    .then(([profileRes, scheduleRes]) => {
      profileForm.value = profileRes.data || {}
      scheduleForm.value = scheduleRes.data || []
    })
    .finally(() => {
      loading.value = false
    })
}

function loadOccupancy() {
  const start = new Date()
  const end = new Date()
  start.setDate(start.getDate() - 365)
  end.setDate(end.getDate() + 365)
  return getExpertOccupancyPeriods(
    `${formatDate(start)} 00:00:00`,
    `${formatDate(end)} 23:59:59`
  ).then(res => {
    occupancyPeriods.value = res.data || []
  })
}

function handleUnavailablePeriods(periods) {
  unavailablePeriods.value = periods || []
}

function formatDate(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, "0")
  const day = String(date.getDate()).padStart(2, "0")
  return `${year}-${month}-${day}`
}

function saveProfile() {
  profileRef.value.validate(valid => {
    if (!valid) return
    saveExpertProfile(profileForm.value).then(res => {
      profileForm.value = res.data
      proxy.$modal.msgSuccess("专家档案已保存")
    })
  })
}

async function downloadWordTemplate() {
  try {
    const blob = await downloadExpertProfileImportTemplate()
    Download.saveAs(blob, "专家信息导入模板.docx")
  }
  catch {
    proxy.$modal.msgError("Word模板下载失败")
  }
}

function beforeWordImport(file) {
  if (!file.name.toLowerCase().endsWith(".docx")) {
    proxy.$modal.msgError("仅支持.docx格式的Word模板")
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    proxy.$modal.msgError("Word模板不能超过5MB")
    return false
  }
  wordImporting.value = true
  return true
}

function handleWordImportSuccess(response) {
  wordImporting.value = false
  if (response.code !== 200) {
    proxy.$modal.msgError(response.msg || "Word模板导入失败")
    return
  }
  const imported = response.data?.profile || {}
  const fields = response.data?.recognizedFields || []
  const updates = Object.fromEntries(
    fields
      .filter(field => imported[field] != null)
      .map(field => [field, imported[field]])
  )
  profileForm.value = { ...profileForm.value, ...updates }
  profileRef.value?.clearValidate()
  const count = fields.length
  proxy.$modal.msgSuccess(`已识别 ${count} 个字段，请核对后保存档案`)
}

function handleWordImportError() {
  wordImporting.value = false
  proxy.$modal.msgError("Word模板导入失败，请确认文件格式和内容")
}

function saveSchedule() {
  if (!scheduleForm.value.length) {
    proxy.$modal.msgWarning("请至少设置一个日常可联系时段")
    return
  }
  saveExpertSchedules(scheduleForm.value).then(() => {
    proxy.$modal.msgSuccess("日常可联系时段已保存")
    loadData()
  })
}

loadData()
</script>

<style scoped lang="scss">
.schedule-card {
  margin-top: 16px;
}
.profile-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.word-import-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.word-import-tip {
  margin-bottom: 18px;
}
.completion {
  display: flex;
  align-items: center;
  gap: 22px;
  h3 {
    margin: 0 0 8px;
  }
  p {
    margin: 0;
    color: #7b8794;
    line-height: 1.7;
  }
}
.form-actions {
  margin-top: 18px;
  text-align: right;
}
</style>
