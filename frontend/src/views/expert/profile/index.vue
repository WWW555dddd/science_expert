<template>
  <div class="app-container" v-loading="loading">
    <el-row :gutter="16">
      <el-col :xs="24" :lg="15">
        <el-card shadow="never" header="专家基本档案">
          <el-form ref="profileRef" :model="profileForm" :rules="rules" label-width="96px">
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

    <el-card shadow="never" class="schedule-card" header="可服务时间表">
      <div class="schedule-toolbar">
        <div>
          <p>时间表是专家接单匹配的必要字段。请按周设置可服务时段和每周承接上限。</p>
        </div>
        <el-button type="primary" plain icon="Plus" @click="addSchedule">添加时段</el-button>
      </div>
      <el-table :data="scheduleForm" border>
        <el-table-column label="星期" width="130">
          <template #default="{ row }">
            <el-select v-model="row.weekday" placeholder="星期">
              <el-option v-for="day in weekdays" :key="day.value" :label="day.label" :value="day.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" width="150">
          <template #default="{ row }">
            <el-time-picker v-model="row.startTime" value-format="HH:mm" format="HH:mm" placeholder="开始" />
          </template>
        </el-table-column>
        <el-table-column label="结束时间" width="150">
          <template #default="{ row }">
            <el-time-picker v-model="row.endTime" value-format="HH:mm" format="HH:mm" placeholder="结束" />
          </template>
        </el-table-column>
        <el-table-column label="服务方式" width="140">
          <template #default="{ row }">
            <el-select v-model="row.serviceMode">
              <el-option label="远程" value="REMOTE" />
              <el-option label="现场" value="ONSITE" />
              <el-option label="均可" value="BOTH" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="每周承接上限" width="140">
          <template #default="{ row }">
            <el-input-number v-model="row.weeklyCapacity" :min="1" :max="20" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="生效日期" width="170">
          <template #default="{ row }">
            <el-date-picker v-model="row.effectiveFrom" type="date" value-format="YYYY-MM-DD" placeholder="生效日期" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" icon="Delete" @click="removeSchedule($index)" />
          </template>
        </el-table-column>
      </el-table>
      <div class="form-actions">
        <el-button type="success" v-hasPermi="['expert:profile:edit']" @click="saveSchedule">保存时间表</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup name="ExpertProfile">
import { getExpertProfile, getExpertSchedules, saveExpertProfile, saveExpertSchedules } from "@/api/expert/platform"

const { proxy } = getCurrentInstance()
const loading = ref(false)
const profileRef = ref()
const profileForm = ref({})
const scheduleForm = ref([])
const weekdays = [
  { label: "星期一", value: 1 },
  { label: "星期二", value: 2 },
  { label: "星期三", value: 3 },
  { label: "星期四", value: 4 },
  { label: "星期五", value: 5 },
  { label: "星期六", value: 6 },
  { label: "星期日", value: 7 }
]
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
  LIMITED: "暂停接单（历史状态）",
  EXITED: "已退出"
})[profileForm.value.serviceStatus] || "待认证，暂不能接单")

function loadData() {
  loading.value = true
  Promise.all([getExpertProfile(), getExpertSchedules()])
    .then(([profileRes, scheduleRes]) => {
      profileForm.value = profileRes.data || {}
      scheduleForm.value = scheduleRes.data || []
      if (!scheduleForm.value.length) {
        addSchedule()
      }
    })
    .finally(() => {
      loading.value = false
    })
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

function addSchedule() {
  scheduleForm.value.push({
    weekday: 1,
    startTime: "09:00",
    endTime: "17:00",
    serviceMode: "REMOTE",
    weeklyCapacity: 1,
    effectiveFrom: new Date().toISOString().slice(0, 10)
  })
}

function removeSchedule(index) {
  scheduleForm.value.splice(index, 1)
}

function saveSchedule() {
  saveExpertSchedules(scheduleForm.value).then(() => {
    proxy.$modal.msgSuccess("可服务时间表已保存")
    loadData()
  })
}

loadData()
</script>

<style scoped lang="scss">
.schedule-card {
  margin-top: 16px;
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
.schedule-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  p {
    margin: 0;
    color: #7b8794;
  }
}
.form-actions {
  margin-top: 18px;
  text-align: right;
}
</style>
