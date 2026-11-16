<template>
  <div class="app-container">
    <el-form :inline="true" :model="queryParams" class="search-form">
      <el-form-item label="专家姓名">
        <el-input v-model="queryParams.realName" clearable placeholder="请输入姓名" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="专业方向">
        <el-input v-model="queryParams.researchField" clearable placeholder="请输入专业方向" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-card shadow="never" header="已审核专家正式库">
      <el-table :data="experts" v-loading="loading">
        <el-table-column label="专家姓名" prop="realName" width="120" />
        <el-table-column label="所属单位" prop="organization" min-width="220" show-overflow-tooltip />
        <el-table-column label="专业方向" prop="researchField" min-width="180" show-overflow-tooltip />
        <el-table-column label="产业领域" prop="industryField" min-width="160" show-overflow-tooltip />
        <el-table-column label="服务区域" prop="serviceRegion" width="130" />
        <el-table-column label="接单状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.serviceStatus === 'AVAILABLE' ? 'success' : 'warning'">
              {{ statusText(row.serviceStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="画像版本" width="100">
          <template #default="{ row }">V{{ row.graphVersion }}</template>
        </el-table-column>
        <el-table-column label="审核人" prop="approvedBy" width="120" />
        <el-table-column label="入库时间" prop="approvedTime" width="170" />
      </el-table>
      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>
  </div>
</template>

<script setup name="ReviewApproved">
import { listApprovedExperts } from "@/api/expert/platform"

const loading = ref(false)
const experts = ref([])
const total = ref(0)
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  realName: undefined,
  researchField: undefined
})

function getList() {
  loading.value = true
  listApprovedExperts(queryParams)
    .then(res => {
      experts.value = res.rows || []
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
  queryParams.realName = undefined
  queryParams.researchField = undefined
  handleQuery()
}

function statusText(status) {
  return {
    AVAILABLE: "正常接单",
    PAUSED: "暂停",
    PENDING_CERTIFICATION: "待认证",
    EXITED: "退出"
  }[status] || status
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
</style>
