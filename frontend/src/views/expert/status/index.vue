<template>
  <div class="app-container" v-loading="loading">
    <el-row :gutter="16">
      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="status-card">
          <div class="status-hero" :class="profile.serviceStatus?.toLowerCase()">
            <el-icon size="38"><Switch /></el-icon>
            <div>
              <span>当前接单状态</span>
              <h2>{{ statusText(profile.serviceStatus) }}</h2>
            </div>
          </div>
          <el-alert
            v-if="canAcceptOrders"
            type="success"
            :closable="false"
            show-icon
            :title="`专家已正式认证，可通过 ${certification.passedRequired || 0}/${certification.requiredTotal || 0} 个必选角度。可自行暂停或恢复正常接单。`"
          />
          <el-alert
            v-else
            type="warning"
            :closable="false"
            show-icon
            title="专家尚未正式认证通过，当前不能开放接单。完成全部必选角度认证后将自动开放。"
          />
          <el-form :model="changeForm" label-position="top" class="status-form">
            <el-form-item label="目标状态">
              <el-radio-group v-model="changeForm.serviceStatus" :disabled="!canAcceptOrders">
                <el-radio-button value="AVAILABLE" :disabled="!canAcceptOrders">正常接单</el-radio-button>
                <el-radio-button value="PAUSED">暂停接单</el-radio-button>
                <el-radio-button value="EXITED">退出专家库</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="变更原因">
              <el-input v-model="changeForm.reason" type="textarea" :rows="4" maxlength="500" show-word-limit />
            </el-form-item>
            <el-form-item>
              <el-checkbox v-model="changeForm.creditRelated">该变更为信用相关处理</el-checkbox>
            </el-form-item>
            <el-button
              type="primary"
              :disabled="!canAcceptOrders || changeForm.serviceStatus === profile.serviceStatus"
              v-hasPermi="['expert:status:change']"
              @click="submitStatus"
            >提交状态变更</el-button>
          </el-form>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" header="状态变更记录">
          <el-timeline v-if="logs.length">
            <el-timeline-item
              v-for="log in logs"
              :key="log.logId"
              :timestamp="parseTime(log.createTime)"
              :type="log.currentStatus === 'AVAILABLE' ? 'success' : 'warning'"
            >
              <strong>{{ statusText(log.previousStatus) }} → {{ statusText(log.currentStatus) }}</strong>
              <p>{{ log.reason || "未填写原因" }}</p>
              <span>操作人：{{ log.operatorName }}<em v-if="log.creditRelated === '1'"> · 信用相关</em></span>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无状态变更记录" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="ExpertStatus">
import { changeExpertStatus, getExpertStatus } from "@/api/expert/platform"

const { proxy } = getCurrentInstance()
const loading = ref(false)
const profile = ref({})
const logs = ref([])
const certification = ref({})
const canAcceptOrders = ref(false)
const changeForm = reactive({
  serviceStatus: "PAUSED",
  reason: "",
  creditRelated: false
})

function loadData() {
  loading.value = true
  getExpertStatus()
    .then(res => {
      profile.value = res.data.profile || {}
      logs.value = res.data.logs || []
      certification.value = res.data.certification || {}
      canAcceptOrders.value = Boolean(res.data.canAcceptOrders)
      changeForm.serviceStatus = profile.value.serviceStatus === "PENDING_CERTIFICATION"
        ? "PAUSED"
        : profile.value.serviceStatus || "PAUSED"
    })
    .finally(() => {
      loading.value = false
    })
}

function submitStatus() {
  if (changeForm.serviceStatus === "AVAILABLE" && !canAcceptOrders.value) {
    proxy.$modal.msgWarning("专家尚未正式认证通过，不能开放接单")
    return
  }
  if (!changeForm.reason.trim()) {
    proxy.$modal.msgWarning("请填写状态变更原因")
    return
  }
  changeExpertStatus(changeForm).then(() => {
    proxy.$modal.msgSuccess("接单状态已更新")
    loadData()
  })
}

function statusText(status) {
  return {
    AVAILABLE: "正常接单",
    PAUSED: "暂停接单",
    PENDING_CERTIFICATION: "待认证",
    LIMITED: "暂停接单（历史状态）",
    EXITED: "已退出"
  }[status] || status || "未设置"
}

loadData()
</script>

<style scoped lang="scss">
.status-hero {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 24px;
  margin-bottom: 20px;
  border-radius: 10px;
  color: #fff;
  background: linear-gradient(120deg, #1d6d59, #37997b);
  &.paused {
    background: linear-gradient(120deg, #a2731f, #d29d36);
  }
  &.exited {
    background: linear-gradient(120deg, #8f3f3f, #c55f5f);
  }
  &.pending_certification {
    background: linear-gradient(120deg, #596b7f, #8497a9);
  }
  span {
    opacity: .78;
  }
  h2 {
    margin: 4px 0 0;
  }
}
.status-form {
  margin-top: 22px;
}
.el-timeline {
  padding-left: 6px;
  p {
    margin: 6px 0;
    color: #63717e;
  }
  span {
    color: #8c98a2;
    font-size: 13px;
  }
  em {
    color: #c15b5b;
    font-style: normal;
  }
}
</style>
