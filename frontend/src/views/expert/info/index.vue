<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="82px">
      <el-form-item label="专家编号" prop="expertNo">
        <el-input
          v-model="queryParams.expertNo"
          placeholder="请输入专家编号"
          clearable
          style="width: 180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="专家姓名" prop="expertName">
        <el-input
          v-model="queryParams.expertName"
          placeholder="请输入专家姓名"
          clearable
          style="width: 180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="所属单位" prop="organization">
        <el-input
          v-model="queryParams.organization"
          placeholder="请输入所属单位"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="专业方向" prop="researchField">
        <el-input
          v-model="queryParams.researchField"
          placeholder="请输入专业方向"
          clearable
          style="width: 180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 140px">
          <el-option
            v-for="dict in sys_normal_disable"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['expert:info:add']">
          新增
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['expert:info:edit']"
        >
          修改
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['expert:info:remove']"
        >
          删除
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['expert:info:export']">
          导出
        </el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="expertInfoList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="专家编号" align="center" prop="expertNo" width="130" />
      <el-table-column label="专家姓名" align="center" prop="expertName" width="110" />
      <el-table-column label="所属单位" align="center" prop="organization" min-width="170" show-overflow-tooltip />
      <el-table-column label="职称职务" align="center" prop="professionalTitle" width="120" show-overflow-tooltip />
      <el-table-column label="专业方向" align="center" prop="researchField" min-width="150" show-overflow-tooltip />
      <el-table-column label="产业领域" align="center" prop="industryField" min-width="150" show-overflow-tooltip />
      <el-table-column label="服务区域" align="center" prop="serviceRegion" width="120" show-overflow-tooltip />
      <el-table-column label="联系电话" align="center" prop="phone" width="130" />
      <el-table-column label="状态" align="center" prop="status" width="90">
        <template #default="scope">
          <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="170">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210" align="center" fixed="right" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)" v-hasPermi="['expert:info:query']">
            详情
          </el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['expert:info:edit']">
            修改
          </el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['expert:info:remove']">
            删除
          </el-button>
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

    <el-dialog :title="title" v-model="open" width="820px" append-to-body>
      <el-form ref="expertInfoRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="专家编号" prop="expertNo">
              <el-input v-model="form.expertNo" placeholder="请输入专家编号" maxlength="32" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="专家姓名" prop="expertName">
              <el-input v-model="form.expertName" placeholder="请输入专家姓名" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属单位" prop="organization">
              <el-input v-model="form.organization" placeholder="请输入所属单位" maxlength="128" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="职称职务" prop="professionalTitle">
              <el-input v-model="form.professionalTitle" placeholder="请输入职称职务" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="专业方向" prop="researchField">
              <el-input v-model="form.researchField" placeholder="请输入专业方向" maxlength="255" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="产业领域" prop="industryField">
              <el-input v-model="form.industryField" placeholder="请输入产业领域" maxlength="255" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="服务区域" prop="serviceRegion">
              <el-input v-model="form.serviceRegion" placeholder="请输入服务区域" maxlength="128" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="phone">
              <el-input v-model="form.phone" placeholder="请输入联系电话" maxlength="32" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="电子邮箱" prop="email">
              <el-input v-model="form.email" placeholder="请输入电子邮箱" maxlength="128" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">
                  {{ dict.label }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" maxlength="500" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="专家详情" v-model="viewOpen" width="820px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="专家编号">{{ viewForm.expertNo }}</el-descriptions-item>
        <el-descriptions-item label="专家姓名">{{ viewForm.expertName }}</el-descriptions-item>
        <el-descriptions-item label="所属单位">{{ viewForm.organization }}</el-descriptions-item>
        <el-descriptions-item label="职称职务">{{ viewForm.professionalTitle }}</el-descriptions-item>
        <el-descriptions-item label="专业方向">{{ viewForm.researchField }}</el-descriptions-item>
        <el-descriptions-item label="产业领域">{{ viewForm.industryField }}</el-descriptions-item>
        <el-descriptions-item label="服务区域">{{ viewForm.serviceRegion }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ viewForm.phone }}</el-descriptions-item>
        <el-descriptions-item label="电子邮箱">{{ viewForm.email }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <dict-tag :options="sys_normal_disable" :value="viewForm.status" />
        </el-descriptions-item>
        <el-descriptions-item label="创建人">{{ viewForm.createBy }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ parseTime(viewForm.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ viewForm.remark }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="viewOpen = false">关 闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="ExpertInfo">
import { addExpertInfo, delExpertInfo, getExpertInfo, listExpertInfo, updateExpertInfo } from "@/api/expert/info"

const { proxy } = getCurrentInstance()
const { sys_normal_disable } = useDict("sys_normal_disable")

const expertInfoList = ref([])
const open = ref(false)
const viewOpen = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const viewForm = ref({})

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    expertNo: undefined,
    expertName: undefined,
    organization: undefined,
    researchField: undefined,
    status: undefined
  },
  rules: {
    expertNo: [{ required: true, message: "专家编号不能为空", trigger: "blur" }],
    expertName: [{ required: true, message: "专家姓名不能为空", trigger: "blur" }],
    phone: [
      {
        pattern: /^$|^[0-9+()\-\s]{6,32}$/,
        message: "联系电话格式不正确",
        trigger: "blur"
      }
    ],
    email: [{ type: "email", message: "电子邮箱格式不正确", trigger: "blur" }]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询专家信息列表 */
function getList() {
  loading.value = true
  listExpertInfo(queryParams.value)
    .then(response => {
      expertInfoList.value = response.rows
      total.value = response.total
    })
    .finally(() => {
      loading.value = false
    })
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 表单重置 */
function reset() {
  form.value = {
    expertId: undefined,
    expertNo: undefined,
    expertName: undefined,
    organization: undefined,
    professionalTitle: undefined,
    researchField: undefined,
    industryField: undefined,
    serviceRegion: undefined,
    phone: undefined,
    email: undefined,
    status: "0",
    remark: undefined
  }
  proxy.resetForm("expertInfoRef")
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.expertId)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "添加专家信息"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  const expertId = row.expertId || ids.value[0]
  getExpertInfo(expertId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改专家信息"
  })
}

/** 详情按钮操作 */
function handleView(row) {
  getExpertInfo(row.expertId).then(response => {
    viewForm.value = response.data
    viewOpen.value = true
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["expertInfoRef"].validate(valid => {
    if (!valid) {
      return
    }
    const request = form.value.expertId ? updateExpertInfo(form.value) : addExpertInfo(form.value)
    request.then(() => {
      proxy.$modal.msgSuccess(form.value.expertId ? "修改成功" : "新增成功")
      open.value = false
      getList()
    })
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  const expertIds = row.expertId || ids.value
  proxy.$modal
    .confirm('是否确认删除专家编号为"' + expertIds + '"的数据项？')
    .then(() => {
      return delExpertInfo(expertIds)
    })
    .then(() => {
      getList()
      proxy.$modal.msgSuccess("删除成功")
    })
    .catch(() => {})
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download("expert/info/export", { ...queryParams.value }, `expert_info_${new Date().getTime()}.xlsx`)
}

getList()
</script>
