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

          <div class="credit-summary" :class="{ blocked: isCreditBlocked }">
            <div>
              <span>信誉积分</span>
              <strong>{{ profile.creditScore ?? 100 }}</strong>
            </div>
            <div class="credit-meter">
              <el-progress
                :percentage="Number(profile.creditScore ?? 100)"
                :show-text="false"
                :stroke-width="9"
                :status="isCreditBlocked ? 'exception' : undefined"
              />
              <small>低于 {{ creditThreshold }} 分自动禁止接单</small>
            </div>
          </div>

          <el-alert
            v-if="isCreditBlocked && canSubmitStatusChange"
            type="error"
            :closable="false"
            show-icon
            :title="`信誉分低于阈值 ${creditThreshold}，当前禁止接单。可在下方提交信誉恢复申请。`"
          />
          <el-alert
            v-else-if="isCreditBlocked"
            type="warning"
            :closable="false"
            show-icon
            title="专家已因信誉分低于阈值被禁止接单；当前还需恢复认证，认证完成后才能提交信誉恢复申请。"
          />
          <el-alert
            v-else-if="canSubmitStatusChange"
            type="success"
            :closable="false"
            show-icon
            :title="`专家已正式认证，可通过 ${certification.passedRequired || 0}/${certification.requiredTotal || 0} 个必选角度。正常接单和暂停接单可立即切换，退出专家库需要审核。`"
          />
          <el-alert
            v-else
            type="warning"
            :closable="false"
            show-icon
            title="专家尚未正式认证通过，当前不能开放接单。完成全部必选角度认证后将自动开放。"
          />

          <div v-if="pendingRequest" class="pending-request">
            <div>
            <strong>{{ pendingRequestTitle }}</strong>
              <p>
                {{ statusText(pendingRequest.sourceStatus) }} → {{ statusText(pendingRequest.targetStatus) }}
              </p>
              <small>审核通过前，当前接单状态保持不变；可撤销本次申请。</small>
            </div>
            <el-button
              type="danger"
              plain
              :loading="cancelling"
              @click="cancelRequest(pendingRequest)"
            >撤销申请</el-button>
          </div>

          <el-form :model="changeForm" label-position="top" class="status-form">
            <el-form-item label="目标状态">
              <el-radio-group
                v-model="changeForm.serviceStatus"
                :disabled="!canSubmitStatusChange"
              >
                <el-radio-button value="AVAILABLE" :disabled="!canSubmitStatusChange">
                  {{ isCreditBlocked ? "申请恢复接单" : "正常接单" }}
                </el-radio-button>
                <el-radio-button value="PAUSED" :disabled="!canSubmitStatusChange || isCreditBlocked">暂停接单</el-radio-button>
                <el-radio-button
                  value="EXITED"
                  :disabled="!canSubmitStatusChange || isCreditBlocked || Boolean(pendingRequest)"
                >退出专家库</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item :label="statusReasonLabel">
              <el-input
                v-model="changeForm.reason"
                type="textarea"
                :rows="4"
                maxlength="500"
                show-word-limit
              />
            </el-form-item>
            <el-form-item v-if="!isCreditBlocked">
              <el-checkbox v-model="changeForm.creditRelated">
                该变更为信用相关处理
              </el-checkbox>
            </el-form-item>
            <el-button
              :type="needsStatusReview ? 'warning' : 'primary'"
              :loading="submitting"
              :disabled="!canSubmitStatusChange || Boolean(pendingRequest)
                || changeForm.serviceStatus === profile.serviceStatus"
              @click="submitStatus"
            >{{ statusActionText }}</el-button>
          </el-form>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="14">
        <el-card shadow="never" header="接单状态申请">
          <el-timeline v-if="requests.length">
            <el-timeline-item
              v-for="request in requests"
              :key="request.requestId"
              :timestamp="parseTime(request.createTime)"
              :type="requestTimelineType(request.requestStatus)"
            >
              <div class="request-heading">
                <strong>
                  {{ statusText(request.sourceStatus) }} → {{ statusText(request.targetStatus) }}
                </strong>
                <el-tag :type="requestTagType(request.requestStatus)" size="small">
                  {{ requestStatusText(request.requestStatus) }}
                </el-tag>
              </div>
              <p>申请原因：{{ request.reason || "未填写" }}</p>
              <span v-if="request.reviewerName">
                审核人：{{ request.reviewerName }}
                <template v-if="request.reviewComment"> · {{ request.reviewComment }}</template>
              </span>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无接单状态申请" />
        </el-card>

        <el-card shadow="never" header="已生效状态变更" class="detail-card">
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
          <el-empty v-else description="暂无已生效状态变更记录" />
        </el-card>

        <el-card shadow="never" header="信誉积分变化" class="detail-card">
          <el-timeline v-if="creditLogs.length">
            <el-timeline-item
              v-for="log in creditLogs"
              :key="log.creditLogId"
              :timestamp="parseTime(log.createTime)"
              :type="Number(log.delta) < 0 ? 'danger' : 'success'"
            >
              <strong>
                信誉分 {{ log.beforeScore }} → {{ log.afterScore }}
                （{{ Number(log.delta) > 0 ? "+" : "" }}{{ log.delta }}）
              </strong>
              <p>{{ log.reason || "信誉积分调整" }}</p>
              <span>操作人：{{ log.operatorName || "系统" }}</span>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无信誉积分变化" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="ExpertStatus">
import {
  cancelExpertStatusRequest,
  changeExpertStatus,
  getExpertStatus
} from "@/api/expert/platform"

const { proxy } = getCurrentInstance()
const loading = ref(false)
const submitting = ref(false)
const cancelling = ref(false)
const profile = ref({})
const logs = ref([])
const requests = ref([])
const certification = ref({})
const canSubmitStatusChange = ref(false)
const creditThreshold = ref(60)
const creditLogs = ref([])
const pendingRequest = ref()
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
      requests.value = res.data.requests || []
      certification.value = res.data.certification || {}
      canSubmitStatusChange.value = Boolean(res.data.canSubmitStatusChange)
      creditThreshold.value = Number(res.data.creditThreshold || 60)
      creditLogs.value = res.data.creditLogs || []
      pendingRequest.value = res.data.pendingRequest
      changeForm.serviceStatus = profile.value.serviceStatus === "CREDIT_BLOCKED"
        ? "AVAILABLE"
        : profile.value.serviceStatus === "PENDING_CERTIFICATION"
          ? "PAUSED"
          : profile.value.serviceStatus || "PAUSED"
    })
    .finally(() => {
      loading.value = false
    })
}

function submitStatus() {
  if (!canSubmitStatusChange.value) {
    proxy.$modal.msgWarning("专家尚未正式认证通过，不能提交接单状态变更")
    return
  }
  if (!changeForm.reason.trim()) {
    proxy.$modal.msgWarning("请填写状态变更原因")
    return
  }
  submitting.value = true
  changeExpertStatus(changeForm)
    .then(() => {
      const message = isRestoreRequest.value
        ? "信誉恢复申请已提交，审核通过后恢复接单"
        : changeForm.serviceStatus === "EXITED"
          ? "退出专家库申请已提交，审核通过后生效"
          : pendingRequest.value ? "接单状态已切换，待审申请已自动撤销" : "接单状态已切换"
      proxy.$modal.msgSuccess(message)
      changeForm.reason = ""
      loadData()
    })
    .finally(() => {
      submitting.value = false
    })
}

function cancelRequest(request) {
  proxy.$modal.confirm("撤销后本次申请不再进入审核，是否继续？").then(() => {
    cancelling.value = true
    return cancelExpertStatusRequest(request.requestId, request.version)
  }).then(() => {
    proxy.$modal.msgSuccess("申请已撤销")
    loadData()
  }).finally(() => {
    cancelling.value = false
  }).catch(() => {})
}

function statusText(status) {
  return {
    AVAILABLE: "正常接单",
    PAUSED: "暂停接单",
    PENDING_CERTIFICATION: "待认证",
    CREDIT_BLOCKED: "禁止接单",
    LIMITED: "暂停接单（历史状态）",
    EXITED: "已退出"
  }[status] || status || "未设置"
}

const isCreditBlocked = computed(() => profile.value.serviceStatus === "CREDIT_BLOCKED")
const isRestoreRequest = computed(() =>
  isCreditBlocked.value && changeForm.serviceStatus === "AVAILABLE"
)
const needsStatusReview = computed(() =>
  changeForm.serviceStatus === "EXITED" || isRestoreRequest.value
)
const statusReasonLabel = computed(() => {
  if (isRestoreRequest.value) return "信誉恢复说明"
  return changeForm.serviceStatus === "EXITED" ? "退出原因" : "变更原因"
})
const statusActionText = computed(() => {
  if (isRestoreRequest.value) return "提交信誉恢复申请"
  if (changeForm.serviceStatus === "EXITED") return "提交退出审核"
  return "立即切换状态"
})
const pendingRequestTitle = computed(() =>
  pendingRequest.value?.sourceStatus === "CREDIT_BLOCKED"
    ? "信誉恢复申请审核中"
    : "退出专家库审核中"
)

function requestStatusText(status) {
  return {
    PENDING: "待审核",
    APPROVED: "已通过",
    REJECTED: "已驳回",
    CANCELLED: "已撤销"
  }[status] || status
}

function requestTagType(status) {
  return {
    PENDING: "warning",
    APPROVED: "success",
    REJECTED: "danger",
    CANCELLED: "info"
  }[status] || "info"
}

function requestTimelineType(status) {
  return status === "APPROVED" ? "success" : status === "PENDING" ? "warning" : "info"
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
  &.credit_blocked {
    background: linear-gradient(120deg, #8f3f3f, #c55f5f);
  }
  span {
    opacity: .78;
  }
  h2 {
    margin: 4px 0 0;
  }
}
.credit-summary {
  display: grid;
  grid-template-columns: auto 1fr;
  align-items: center;
  gap: 20px;
  padding: 16px;
  margin-bottom: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-fill-color-extra-light);
  > div:first-child {
    display: flex;
    flex-direction: column;
    min-width: 92px;
  }
  span,
  small {
    color: var(--el-text-color-secondary);
  }
  strong {
    color: var(--el-color-success);
    font-size: 30px;
  }
  &.blocked strong {
    color: var(--el-color-danger);
  }
}
.credit-meter {
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.pending-request {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 16px;
  margin-top: 16px;
  border: 1px solid #e6d5ae;
  border-radius: 8px;
  background: #fdf8eb;
  strong {
    color: #8c681f;
  }
  p {
    margin: 5px 0;
  }
  small {
    color: #8a8170;
  }
}
.status-form {
  margin-top: 22px;
}
.detail-card {
  margin-top: 16px;
}
.request-heading {
  display: flex;
  align-items: center;
  gap: 10px;
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
