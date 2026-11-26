<template>
  <section class="unavailable-section" v-loading="loading">
    <div class="section-heading">
      <div>
        <h3>临时不可用时段</h3>
        <p>用于登记出差、会议、休假等临时无法联系或服务的日期。命中该时段的检索候选人会被硬门禁排除。</p>
      </div>
      <el-button
        v-if="!readonly"
        type="primary"
        plain
        icon="Plus"
        @click="openCreate"
      >
        登记不可用时段
      </el-button>
    </div>

    <el-alert
      title="日常可联系时段是固定周计划；临时不可用时段优先级更高，不会改动固定周计划。"
      type="info"
      :closable="false"
      show-icon
      class="section-tip"
    />

    <el-empty
      v-if="!periods.length"
      :description="readonly ? '专家暂未登记临时不可用时段' : '暂无临时不可用时段'"
      :image-size="64"
    />
    <el-table v-else :data="periods" size="small" border>
      <el-table-column label="时间范围" min-width="310">
        <template #default="{ row }">
          <div class="period-range">
            <strong>{{ formatDateTime(row.startTime) }}</strong>
            <span>至 {{ formatDateTime(row.endTime) }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="原因" prop="reason" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">{{ row.reason || "未填写" }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="periodStatus(row).type" size="small">
            {{ periodStatus(row).text }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="!readonly" label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogOpen"
      :title="form.unavailableId ? '编辑临时不可用时段' : '登记临时不可用时段'"
      width="620px"
      append-to-body
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="开始时间" prop="startTime">
          <el-date-picker
            v-model="form.startTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            placeholder="选择开始时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker
            v-model="form.endTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            placeholder="选择结束时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="不可用原因">
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="例如：异地出差、评审会议、休假等"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup name="ExpertUnavailablePeriods">
import {
  createExpertUnavailablePeriod,
  deleteExpertUnavailablePeriod,
  getAdminExpertUnavailablePeriods,
  getExpertUnavailablePeriods,
  updateExpertUnavailablePeriod
} from "@/api/expert/platform"

const props = defineProps({
  mode: {
    type: String,
    default: "expert"
  },
  userId: {
    type: [Number, String],
    default: null
  },
  readonly: {
    type: Boolean,
    default: false
  }
})
const emit = defineEmits(["update:periods"])
const { proxy } = getCurrentInstance()
const loading = ref(false)
const saving = ref(false)
const periods = ref([])
const dialogOpen = ref(false)
const formRef = ref()
const form = reactive({
  unavailableId: null,
  version: null,
  startTime: "",
  endTime: "",
  reason: ""
})
const rules = {
  startTime: [{ required: true, message: "请选择开始时间", trigger: "change" }],
  endTime: [
    { required: true, message: "请选择结束时间", trigger: "change" },
    {
      validator: (_rule, value, callback) => {
        if (value && form.startTime && value <= form.startTime) {
          callback(new Error("结束时间必须晚于开始时间"))
          return
        }
        callback()
      },
      trigger: "change"
    }
  ]
}

function load() {
  if (props.mode === "admin" && !props.userId) {
    periods.value = []
    emit("update:periods", [])
    return
  }
  loading.value = true
  const request = props.mode === "admin"
    ? getAdminExpertUnavailablePeriods(props.userId)
    : getExpertUnavailablePeriods()
  request
    .then(res => {
      periods.value = res.data || []
      emit("update:periods", periods.value)
    })
    .finally(() => {
      loading.value = false
    })
}

function openCreate() {
  Object.assign(form, {
    unavailableId: null,
    version: null,
    startTime: "",
    endTime: "",
    reason: ""
  })
  dialogOpen.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function openEdit(row) {
  Object.assign(form, {
    unavailableId: row.unavailableId,
    version: row.version,
    startTime: row.startTime,
    endTime: row.endTime,
    reason: row.reason || ""
  })
  dialogOpen.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function submit() {
  formRef.value.validate(valid => {
    if (!valid) return
    saving.value = true
    const request = form.unavailableId
      ? updateExpertUnavailablePeriod(form.unavailableId, { ...form })
      : createExpertUnavailablePeriod({ ...form })
    request
      .then(() => {
        proxy.$modal.msgSuccess(form.unavailableId ? "临时不可用时段已更新" : "临时不可用时段已登记")
        dialogOpen.value = false
        load()
      })
      .finally(() => {
        saving.value = false
      })
  })
}

function remove(row) {
  proxy.$modal.confirm(
    `确认删除 ${formatDateTime(row.startTime)} 至 ${formatDateTime(row.endTime)} 的临时不可用登记吗？`
  ).then(() => deleteExpertUnavailablePeriod(row.unavailableId))
    .then(() => {
      proxy.$modal.msgSuccess("临时不可用时段已删除")
      load()
    })
    .catch(() => {})
}

function formatDateTime(value) {
  if (!value) return "-"
  return String(value).replace("T", " ").slice(0, 16)
}

function periodStatus(row) {
  const now = new Date()
  const start = new Date(String(row.startTime).replace(" ", "T"))
  const end = new Date(String(row.endTime).replace(" ", "T"))
  if (end <= now) return { type: "info", text: "已结束" }
  if (start <= now && end > now) return { type: "danger", text: "不可用中" }
  return { type: "warning", text: "待生效" }
}

watch(() => [props.mode, props.userId], load, { immediate: true })
</script>

<style scoped lang="scss">
.unavailable-section {
  padding-top: 20px;
  margin-top: 18px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.section-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  h3 {
    margin: 0 0 6px;
    color: #243d55;
  }
  p {
    margin: 0;
    color: var(--el-text-color-secondary);
    line-height: 1.65;
  }
}
.section-tip {
  margin: 14px 0;
}
.period-range {
  display: flex;
  flex-direction: column;
  gap: 3px;
  strong {
    color: #2d495c;
  }
  span {
    color: var(--el-text-color-secondary);
  }
}
@media (max-width: 760px) {
  .section-heading {
    flex-direction: column;
  }
}
</style>
