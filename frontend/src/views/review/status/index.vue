<template>
  <div class="app-container">
    <el-form :inline="true" :model="queryParams" class="search-form">
      <el-form-item label="专家姓名">
        <el-input
          v-model="queryParams.realName"
          clearable
          placeholder="请输入姓名"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="申请状态">
        <el-select v-model="queryParams.requestStatus" style="width: 140px">
          <el-option label="待审核" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
          <el-option label="已撤销" value="CANCELLED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-card shadow="never" header="专家接单与信誉恢复审核">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="审核退出专家库和信誉恢复申请。正常接单与暂停接单由已认证专家直接切换。"
        class="review-tip"
      />
      <el-table :data="requests" v-loading="loading" empty-text="暂无专家状态申请">
        <el-table-column label="专家" width="130">
          <template #default="{ row }">
            <strong>{{ row.realName || row.userName }}</strong>
            <div class="sub-text">{{ row.userName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态申请" min-width="190">
          <template #default="{ row }">
            <span>{{ statusText(row.sourceStatus) }}</span>
            <el-icon class="change-arrow"><Right /></el-icon>
            <el-tag :type="row.targetStatus === 'AVAILABLE' ? 'success' : 'warning'" size="small">
              {{ statusText(row.targetStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前状态" width="110">
          <template #default="{ row }">{{ statusText(row.currentServiceStatus) }}</template>
        </el-table-column>
        <el-table-column label="信誉分" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="Number(row.creditScore) < 60 ? 'danger' : 'success'" size="small">
              {{ row.creditScore ?? 100 }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请原因" prop="reason" min-width="240" show-overflow-tooltip />
        <el-table-column label="信用相关" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.creditRelated === '1'" type="danger" size="small">是</el-tag>
            <span v-else>否</span>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" prop="createTime" width="170" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="requestTagType(row.requestStatus)" size="small">
              {{ requestStatusText(row.requestStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核信息" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="row.reviewerName">
              {{ row.reviewerName }}
              <span v-if="row.reviewComment" class="sub-text"> · {{ row.reviewComment }}</span>
            </template>
            <span v-else class="sub-text">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <template v-if="row.requestStatus === 'PENDING'">
              <el-button
                link
                type="success"
                @click="openReview(row, 'APPROVE')"
                v-hasPermi="['review:action']"
              >通过</el-button>
              <el-button
                link
                type="danger"
                @click="openReview(row, 'REJECT')"
                v-hasPermi="['review:action']"
              >驳回</el-button>
            </template>
            <span v-else class="sub-text">已处理</span>
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

    <el-dialog v-model="reviewOpen" :title="reviewTitle" width="520px" destroy-on-close>
      <div v-if="currentRequest" class="review-summary">
        <strong>{{ currentRequest.realName || currentRequest.userName }}</strong>
        <p>
          {{ statusText(currentRequest.sourceStatus) }}
          →
          {{ statusText(currentRequest.targetStatus) }}
        </p>
        <p>申请原因：{{ currentRequest.reason }}</p>
      </div>
      <el-form label-position="top">
        <el-form-item :label="reviewForm.action === 'REJECT' ? '驳回意见' : '审核意见'">
          <el-input
            v-model="reviewForm.comment"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewOpen = false">取消</el-button>
        <el-button
          :type="reviewForm.action === 'APPROVE' ? 'success' : 'danger'"
          :loading="submitting"
          @click="submitReview"
        >{{ reviewForm.action === "APPROVE" ? "确认通过" : "确认驳回" }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="ReviewStatus">
import { listExpertStatusRequests, reviewExpertStatusRequest } from "@/api/expert/platform"

const { proxy } = getCurrentInstance()
const loading = ref(false)
const submitting = ref(false)
const requests = ref([])
const total = ref(0)
const reviewOpen = ref(false)
const currentRequest = ref()
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  realName: undefined,
  requestStatus: "PENDING"
})
const reviewForm = reactive({
  action: "APPROVE",
  comment: ""
})
const reviewTitle = computed(() =>
  reviewForm.action === "APPROVE"
    ? "通过专家状态申请"
    : "驳回专家状态申请"
)

function getList() {
  loading.value = true
  listExpertStatusRequests(queryParams)
    .then(res => {
      requests.value = res.rows || []
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
  queryParams.pageNum = 1
  queryParams.realName = undefined
  queryParams.requestStatus = "PENDING"
  getList()
}

function openReview(row, action) {
  currentRequest.value = row
  reviewForm.action = action
  reviewForm.comment = ""
  reviewOpen.value = true
}

function submitReview() {
  if (reviewForm.action === "REJECT" && !reviewForm.comment.trim()) {
    proxy.$modal.msgWarning("驳回时请填写审核意见")
    return
  }
  const text = reviewForm.action === "APPROVE" ? "通过" : "驳回"
  proxy.$modal.confirm(`是否确认${text}该专家状态申请？`).then(() => {
    submitting.value = true
    return reviewExpertStatusRequest(currentRequest.value.requestId, {
      action: reviewForm.action,
      comment: reviewForm.comment,
      version: currentRequest.value.version
    })
  }).then(() => {
    proxy.$modal.msgSuccess("专家状态申请已处理")
    reviewOpen.value = false
    getList()
  }).finally(() => {
    submitting.value = false
  }).catch(() => {})
}

function statusText(status) {
  return {
    AVAILABLE: "正常接单",
    PAUSED: "暂停接单",
    PENDING_CERTIFICATION: "待认证",
    CREDIT_BLOCKED: "禁止接单",
    EXITED: "退出专家库"
  }[status] || status || "-"
}

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

getList()
</script>

<style scoped lang="scss">
.search-form {
  padding: 16px 16px 0;
  margin-bottom: 16px;
  background: #fff;
  border-radius: 8px;
}
.review-tip {
  margin-bottom: 16px;
}
.sub-text {
  color: #8a96a3;
  font-size: 13px;
}
.change-arrow {
  margin: 0 8px;
  color: #9aa5b1;
  vertical-align: middle;
}
.review-summary {
  padding: 14px 16px;
  margin-bottom: 16px;
  border-radius: 8px;
  background: #f6f8fa;
  p {
    margin: 6px 0 0;
    color: #5f6b76;
  }
}
</style>
