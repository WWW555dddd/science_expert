<template>
  <div class="app-container task-conflict-page">
    <div class="page-head">
      <div>
        <span class="eyebrow">EXPERT ACCEPTANCE</span>
        <h2>专家接单</h2>
        <p>确认是否接受本次任务，并同时确认当前任务是否存在利益冲突。接单结果不修改长期时间表和利益冲突声明。</p>
      </div>
      <el-tag type="warning" effect="plain">接单并确认冲突</el-tag>
    </div>

    <el-form :inline="true" class="search-form">
      <el-form-item label="确认状态">
        <el-select v-model="queryStatus" clearable placeholder="全部" style="width: 150px">
          <el-option label="待确认" value="PENDING" />
            <el-option label="已接受" value="ACCEPTED" />
            <el-option label="已拒绝" value="REJECTED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="loadList">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-card shadow="never">
      <el-table :data="confirmations" v-loading="loading" border>
        <el-table-column label="外部任务号" prop="externalTaskId" min-width="190" />
        <el-table-column label="任务时间" min-width="230">
          <template #default="{ row }">{{ row.serviceStart }} 至 {{ row.serviceEnd }}</template>
        </el-table-column>
        <el-table-column label="服务方式" width="110">
          <template #default="{ row }">{{ modeText(row.serviceMode) }}</template>
        </el-table-column>
        <el-table-column label="服务区域" prop="serviceRegion" min-width="140" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="acceptanceTag(row.status)" size="small">
              {{ acceptanceText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="冲突结果" width="120">
          <template #default="{ row }">{{ conflictText(row.conflictStatus) }}</template>
        </el-table-column>
        <el-table-column label="正式安排" width="110">
          <template #default="{ row }">{{ arrangementText(row.arrangementStatus) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING'"
              link
              type="primary"
              icon="Edit"
              @click="openConfirm(row)"
            >
              确认
            </el-button>
            <el-button v-else link type="info" @click="showStatement(row)">查看说明</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogOpen" title="确认接单与利益冲突" width="640px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="任务号">{{ current.externalTaskId }}</el-descriptions-item>
        <el-descriptions-item label="时间">
          {{ current.serviceStart }} 至 {{ current.serviceEnd }}
        </el-descriptions-item>
      </el-descriptions>
      <el-alert
        v-if="validationMessage"
        :title="validationMessage"
        type="warning"
        :closable="false"
        show-icon
        class="validation-tip"
      />
      <el-form :model="form" label-position="top" class="confirm-form">
        <el-form-item label="是否接受本次任务">
          <el-radio-group v-model="form.acceptanceStatus">
            <el-radio-button value="ACCEPTED">接受接单</el-radio-button>
            <el-radio-button value="REJECTED">不接受</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="本次任务是否存在利益冲突">
          <el-radio-group v-model="form.conflictStatus">
            <el-radio-button value="NONE">无冲突</el-radio-button>
            <el-radio-button value="POTENTIAL">存在潜在冲突</el-radio-button>
            <el-radio-button value="CONFIRMED">存在明确冲突</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="确认说明">
          <el-input
            v-model="form.statement"
            type="textarea"
            :rows="4"
            placeholder="请说明与客户、项目参与方、保险标的等是否存在关系及回避安排"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitConfirm">
          提交接单结果
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="ExpertTaskAcceptance">
import {
  listMyTaskAcceptances,
  respondMyTaskAcceptance
} from "@/api/expert/platform"

const loading = ref(false)
const submitting = ref(false)
const confirmations = ref([])
const queryStatus = ref("PENDING")
const dialogOpen = ref(false)
const current = ref({})
const form = reactive({
  acceptanceStatus: "ACCEPTED",
  conflictStatus: "NONE",
  statement: ""
})
const validationMessage = computed(() => {
  if (!current.value.confirmationId) return "接单请求信息缺失，无法提交"
  if (!current.value.externalTaskId) return "外部任务号缺失，无法提交"
  if (!current.value.serviceStart || !current.value.serviceEnd) return "任务服务时间缺失，无法提交"
  if (!form.acceptanceStatus) return "请选择是否接受本次任务"
  if (!form.conflictStatus) return "请选择本次任务的利益冲突结果"
  if (!form.statement.trim()) return "请填写接单及利益冲突确认说明"
  if (form.acceptanceStatus === "ACCEPTED" && form.conflictStatus === "CONFIRMED") {
    return "存在明确利益冲突时不能直接接受任务"
  }
  return ""
})

function loadList() {
  loading.value = true
  listMyTaskAcceptances(queryStatus.value)
    .then(res => {
      confirmations.value = res.data || []
    })
    .finally(() => {
      loading.value = false
    })
}

function resetQuery() {
  queryStatus.value = "PENDING"
  loadList()
}

function openConfirm(row) {
  current.value = row
  form.acceptanceStatus = "ACCEPTED"
  form.conflictStatus = "NONE"
  form.statement = ""
  dialogOpen.value = true
}

function submitConfirm() {
  if (validationMessage.value) {
    ElMessage.warning(validationMessage.value)
    return
  }
  submitting.value = true
  respondMyTaskAcceptance(current.value.confirmationId, {
    acceptanceStatus: form.acceptanceStatus,
    conflictStatus: form.conflictStatus,
    statement: form.statement,
    version: current.value.version
  })
    .then(() => {
      ElMessage.success("接单结果已提交")
      dialogOpen.value = false
      loadList()
    })
    .finally(() => {
      submitting.value = false
    })
}

function showStatement(row) {
  ElMessageBox.alert(row.statement || "未填写说明", "冲突确认说明", {
    confirmButtonText: "关闭"
  })
}

function modeText(value) {
  return { REMOTE: "远程", ONSITE: "现场", BOTH: "远程或现场" }[value] || value || "-"
}

function conflictText(value) {
  return {
    PENDING: "待确认",
    NONE: "无冲突",
    POTENTIAL: "潜在冲突",
    CONFIRMED: "明确冲突"
  }[value] || value || "-"
}

function acceptanceText(value) {
  return {
    PENDING: "待接单",
    ACCEPTED: "已接受",
    REJECTED: "已拒绝"
  }[value] || value || "-"
}

function acceptanceTag(value) {
  return {
    PENDING: "warning",
    ACCEPTED: "success",
    REJECTED: "danger"
  }[value] || "info"
}

function arrangementText(value) {
  return {
    NOT_CONFIRMED: "未确认",
    CONFIRMED: "已确认",
    CANCELLED: "已取消"
  }[value] || "未确认"
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
  background: linear-gradient(135deg, color-mix(in srgb, var(--el-color-warning) 8%, #fff), #fff 55%);
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
  color: var(--el-color-warning);
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
.confirm-form {
  margin-top: 18px;
}
.validation-tip {
  margin-top: 16px;
}
</style>
