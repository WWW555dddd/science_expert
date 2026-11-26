<template>
  <div class="app-container business-workspace">
    <div class="page-head">
      <div>
        <span class="eyebrow">BUSINESS WORKSPACE</span>
        <h2>业务专家查询</h2>
        <p>按专家业务方向查询有效档案、画像和时间信息，并处理本次任务接单与正式安排。</p>
      </div>
      <el-tag type="warning" effect="plain">业务核验与安排</el-tag>
    </div>

    <el-alert
      title="专家档案、长期时间安排、画像和长期利益冲突声明仍为只读；本页面只处理本次任务的接单请求与正式安排。"
      type="info"
      :closable="false"
      show-icon
      class="read-only-tip"
    />

    <el-form :inline="true" :model="queryParams" class="search-form">
      <el-form-item label="业务关键词">
        <el-input
          v-model="queryParams.keyword"
          clearable
          placeholder="姓名、单位、专业、产业或区域"
          style="width: 260px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="专家姓名">
        <el-input v-model="queryParams.realName" clearable placeholder="姓名" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="所属单位">
        <el-input v-model="queryParams.organization" clearable placeholder="单位" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="接单状态">
        <el-select v-model="queryParams.serviceStatus" clearable placeholder="全部" style="width: 130px">
          <el-option label="正常接单" value="AVAILABLE" />
          <el-option label="暂停接单" value="PAUSED" />
          <el-option label="信誉禁止接单" value="CREDIT_BLOCKED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-card shadow="never">
      <el-table :data="experts" v-loading="loading" row-key="userId" border>
        <el-table-column label="专家" width="160" fixed="left">
          <template #default="{ row }">
            <strong>{{ row.realName }}</strong>
            <div class="sub-text">{{ row.professionalTitle || "未填写职称" }}</div>
          </template>
        </el-table-column>
        <el-table-column label="单位 / 区域" min-width="210" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.organization || "未填写单位" }}</div>
            <div class="sub-text">{{ row.serviceRegion || "未填写服务区域" }}</div>
          </template>
        </el-table-column>
        <el-table-column label="专业方向" prop="researchField" min-width="180" show-overflow-tooltip />
        <el-table-column label="产业领域" prop="industryField" min-width="160" show-overflow-tooltip />
        <el-table-column label="有效档案" width="120" align="center">
          <template #default="{ row }">
            <el-tag type="success" size="small">已认证</el-tag>
            <div class="sub-text">档案 V{{ row.profileVersion || 0 }}</div>
          </template>
        </el-table-column>
        <el-table-column label="接单状态" width="105" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.serviceStatus)" size="small">
              {{ statusText(row.serviceStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="画像版本" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="Number(row.latestGraphVersion ?? row.graphVersion ?? 0) > 0" type="success" size="small">
              画像 V{{ row.latestGraphVersion ?? row.graphVersion }}
            </el-tag>
            <span v-else class="sub-text">暂无画像</span>
            <div class="sub-text">{{ row.scheduleCount || 0 }} 个常规时段</div>
          </template>
        </el-table-column>
        <el-table-column label="入库时间" prop="approvedTime" width="165" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" icon="View" @click="openDetail(row)">
              查看有效档案
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
    </el-card>

    <el-drawer
      v-model="detailOpen"
      :title="`${detail.expert?.realName || '专家'} · 业务只读视图`"
      size="92%"
      destroy-on-close
    >
      <div v-loading="detailLoading" class="detail-shell">
        <template v-if="detail.expert">
          <div class="expert-hero">
            <div>
              <div class="hero-title">
                <h3>{{ detail.expert.realName }}</h3>
                <el-tag :type="statusTag(detail.expert.serviceStatus)" size="small">
                  {{ statusText(detail.expert.serviceStatus) }}
                </el-tag>
                <el-tag type="success" size="small">有效档案</el-tag>
              </div>
              <p>
                {{ detail.expert.organization || "未填写单位" }}
                · {{ detail.expert.professionalTitle || "未填写职称" }}
                · {{ detail.expert.serviceRegion || "未填写服务区域" }}
              </p>
            </div>
            <div class="hero-version">
              <span>档案版本</span>
              <strong>V{{ detail.expert.profileVersion || 0 }}</strong>
              <span>当前画像版本</span>
              <strong v-if="Number(detail.expert.graphVersion || 0) > 0">
                V{{ detail.expert.graphVersion }}
              </strong>
              <strong v-else class="empty-version">暂无</strong>
            </div>
          </div>

          <el-tabs v-model="activeTab" class="business-tabs">
            <el-tab-pane label="有效档案" name="overview">
              <el-card shadow="never">
                <el-descriptions :column="3" border>
                  <el-descriptions-item label="专业方向">{{ detail.expert.researchField || "-" }}</el-descriptions-item>
                  <el-descriptions-item label="产业领域">{{ detail.expert.industryField || "-" }}</el-descriptions-item>
                  <el-descriptions-item label="服务区域">{{ detail.expert.serviceRegion || "-" }}</el-descriptions-item>
                  <el-descriptions-item label="认证状态">
                    <el-tag type="success" size="small">已认证</el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="接单状态">
                    {{ statusText(detail.expert.serviceStatus) }}
                  </el-descriptions-item>
                  <el-descriptions-item label="入库时间">{{ detail.expert.approvedTime || "-" }}</el-descriptions-item>
                  <el-descriptions-item label="专家简介" :span="3">
                    {{ detail.expert.careerSummary || "暂无简介" }}
                  </el-descriptions-item>
                </el-descriptions>
              </el-card>

              <el-card shadow="never" class="detail-block" header="业务使用中的画像信息">
                <el-alert
                  v-if="releaseVersionBehind"
                  title="专家当前画像已更新，业务展示的最近审核快照尚未同步。"
                  :description="`当前画像 V${detail.expert.graphVersion || 0}，最近审核快照中的画像为 V${detail.latestRelease.graphVersion || 0}。`"
                  type="warning"
                  :closable="false"
                  show-icon
                  class="version-sync-alert"
                />
                <el-descriptions
                  v-if="detail.latestRelease?.releaseId
                    && Number(detail.latestRelease.graphVersion || 0) > 0"
                  :column="3"
                  border
                >
                  <el-descriptions-item label="画像版本">V{{ detail.latestRelease.graphVersion }}</el-descriptions-item>
                  <el-descriptions-item label="审核人">{{ detail.latestRelease.approvedBy || "系统" }}</el-descriptions-item>
                  <el-descriptions-item label="生效时间">{{ detail.latestRelease.createTime }}</el-descriptions-item>
                </el-descriptions>
                <el-empty v-else description="暂无业务可用画像，不向业务展示草稿内容" :image-size="72" />
              </el-card>
            </el-tab-pane>

            <el-tab-pane label="能力画像" name="profile">
              <div v-if="detail.dimensions?.length" class="profile-summary">
                <div>
                  <span>已发布维度</span>
                  <strong>{{ scoredDimensions.length }}</strong>
                </div>
                <div>
                  <span>证据冲突维度</span>
                  <strong>{{ conflictDimensionCount }}</strong>
                </div>
                <div>
                  <span>画像覆盖率</span>
                  <strong>{{ profileCoverage }}%</strong>
                </div>
              </div>
              <el-table v-if="detail.dimensions?.length" :data="detail.dimensions" border max-height="600">
                <el-table-column label="维度组" prop="groupName" width="170" fixed="left" />
                <el-table-column label="能力维度" prop="dimensionName" min-width="180" fixed="left" />
                <el-table-column label="评分" width="90" align="center">
                  <template #default="{ row }">{{ scoreText(row) }}</template>
                </el-table-column>
                <el-table-column label="状态" width="110">
                  <template #default="{ row }">
                    <el-tag :type="dimensionTag(row.dataStatus)" size="small">
                      {{ dimensionStatusText(row.dataStatus) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="证据摘要" prop="evidence" min-width="360" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.evidence || "证据不足" }}</template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="暂无已发布能力画像" />
            </el-tab-pane>

            <el-tab-pane label="常规服务时间" name="schedule">
              <el-alert
                title="常规服务时间表示专家长期声明的可服务时段，不代表该时段实际未被派单。"
                type="info"
                :closable="false"
                show-icon
                class="tab-tip"
              />
              <el-table :data="detail.schedules || []" border>
                <el-table-column label="星期" width="110">
                  <template #default="{ row }">{{ weekdayText(row.weekday) }}</template>
                </el-table-column>
                <el-table-column label="服务时间" width="150">
                  <template #default="{ row }">{{ row.startTime }}–{{ row.endTime }}</template>
                </el-table-column>
                <el-table-column label="服务方式" width="120">
                  <template #default="{ row }">{{ modeText(row.serviceMode) }}</template>
                </el-table-column>
                <el-table-column label="每周上限" prop="weeklyCapacity" width="110" align="center" />
                <el-table-column label="生效日期" prop="effectiveFrom" width="150" />
              </el-table>
            </el-tab-pane>

            <el-tab-pane label="任务占用" name="occupancy">
              <el-alert
                title="这里显示专家已经安排任务的时间。同一个任务只显示最新状态，绿色表示可以安排，红色表示已有任务、不能重复安排。"
                type="info"
                :closable="false"
                show-icon
                class="tab-tip"
              />
              <el-table :data="occupancyRecords" v-loading="occupancyLoading" border>
                <el-table-column label="任务编号" prop="externalTaskId" min-width="180">
                  <template #default="{ row }">{{ row.externalTaskId }}</template>
                </el-table-column>
                <el-table-column label="任务时间" min-width="230">
                  <template #default="{ row }">{{ row.serviceStart }} 至 {{ row.serviceEnd }}</template>
                </el-table-column>
                <el-table-column label="是否可安排" width="120">
                  <template #default="{ row }">
                    <el-tag :type="occupancyTag(row.occupancyStatus)" size="small">
                      {{ occupancyText(row.occupancyStatus) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="冲突情况" width="120">
                  <template #default="{ row }">{{ conflictText(row.conflictStatus) }}</template>
                </el-table-column>
                <el-table-column label="服务方式" width="110">
                  <template #default="{ row }">{{ modeText(row.serviceMode) }}</template>
                </el-table-column>
                <el-table-column label="记录来源" min-width="180">
                  <template #default="{ row }">
                    {{ sourceKindText(row.sourceKind) }}
                    <div class="sub-text">{{ row.sourceSystem }}</div>
                  </template>
                </el-table-column>
                <el-table-column label="最后确认时间" prop="verifiedAt" width="170" />
              </el-table>
              <el-empty
                v-if="!occupancyLoading && !occupancyRecords.length"
                description="这段时间没有任务安排"
              />
            </el-tab-pane>

            <el-tab-pane label="利益冲突核验" name="conflict">
              <el-alert
                :title="detail.conflict?.message"
                type="warning"
                :closable="false"
                show-icon
                class="tab-tip"
              />

              <el-card shadow="never" header="具体任务核验">
                <el-form :model="taskForm" label-position="top">
                  <div class="task-form-grid">
                    <el-form-item label="选择业务任务" class="task-select-item">
                      <el-select
                        v-model="selectedTaskKey"
                        placeholder="请选择业务任务或接单记录"
                        style="width: 100%"
                        @change="applyTaskOption"
                      >
                        <el-option
                          v-for="item in taskOptions"
                          :key="item.optionKey"
                          :label="taskOptionLabel(item)"
                          :value="item.optionKey"
                        />
                      </el-select>
                    </el-form-item>
                  </div>
                  <el-descriptions v-if="selectedTaskOption" :column="3" border class="selected-task">
                    <el-descriptions-item label="任务编号">
                      {{ selectedTaskOption.externalTaskId }}
                    </el-descriptions-item>
                    <el-descriptions-item label="任务时间">
                      {{ selectedTaskOption.serviceStart }} 至 {{ selectedTaskOption.serviceEnd }}
                    </el-descriptions-item>
                    <el-descriptions-item label="接单状态">
                      {{ selectedTaskOption.status ? acceptanceText(selectedTaskOption.status) : "尚未发起" }}
                    </el-descriptions-item>
                    <el-descriptions-item label="正式安排">
                      {{ arrangementText(selectedTaskOption.arrangementStatus) }}
                    </el-descriptions-item>
                    <el-descriptions-item label="服务方式">
                      {{ modeText(selectedTaskOption.serviceMode) }}
                    </el-descriptions-item>
                    <el-descriptions-item label="服务区域">
                      {{ selectedTaskOption.serviceRegion || "-" }}
                    </el-descriptions-item>
                    <el-descriptions-item label="产业方向">
                      {{ selectedTaskOption.industry || "-" }}
                    </el-descriptions-item>
                  </el-descriptions>
                  <el-empty
                    v-else
                    description="暂无可选业务任务，请先准备专家业务任务快照"
                    :image-size="60"
                  />
                  <el-button type="primary" icon="CircleCheck" :loading="eligibilityLoading" @click="runEligibility">
                    核验当前任务
                  </el-button>
                  <el-button
                    plain
                    icon="Warning"
                    :loading="taskRequestLoading"
                    :disabled="requestButtonDisabled"
                    :title="requestButtonHint || undefined"
                    @click="requestTaskConfirmation"
                  >
                    {{ requestButtonText }}
                  </el-button>
                  <el-button
                    v-if="currentTaskRequest?.status === 'ACCEPTED'
                      && currentTaskRequest?.arrangementStatus !== 'CONFIRMED'
                      && currentTaskRequest?.arrangementStatus !== 'CANCELLED'"
                    type="success"
                    icon="Select"
                    :loading="arrangementLoading"
                    @click="confirmArrangement"
                  >
                    确认正式安排
                  </el-button>
                  <el-button
                    v-if="currentTaskRequest?.arrangementStatus === 'CONFIRMED'"
                    type="warning"
                    plain
                    icon="CloseBold"
                    :loading="arrangementLoading"
                    @click="cancelArrangement"
                  >
                    取消正式安排
                  </el-button>
                </el-form>

                <div v-if="eligibilityResult" class="eligibility-result">
                  <el-alert
                    :title="decisionTitle(eligibilityResult.decision)"
                    :description="eligibilityDescription"
                    :type="decisionTag(eligibilityResult.decision)"
                    :closable="false"
                    show-icon
                  />
                  <el-table :data="eligibilityResult.checks || []" size="small" border class="result-table">
                    <el-table-column label="检查项" prop="code" width="210" />
                    <el-table-column label="结果" width="110">
                      <template #default="{ row }">
                        <el-tag :type="checkTag(row.status)" size="small">{{ checkText(row.status) }}</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="说明" prop="message" min-width="360" />
                  </el-table>
                  <el-descriptions
                    v-if="eligibilityResult.taskConflictConfirmation"
                    :column="3"
                    border
                    class="result-table"
                  >
                    <el-descriptions-item label="核验任务号">
                      {{ eligibilityResult.taskContext?.externalTaskId || "未填写" }}
                    </el-descriptions-item>
                    <el-descriptions-item label="专家接单">
                      {{ acceptanceText(eligibilityResult.taskConflictConfirmation.acceptanceStatus) }}
                    </el-descriptions-item>
                    <el-descriptions-item label="冲突确认">
                      {{ conflictText(eligibilityResult.taskConflictConfirmation.conflictStatus) }}
                    </el-descriptions-item>
                    <el-descriptions-item label="接单记录">
                      {{ eligibilityResult.taskConflictConfirmation.confirmationId || "未发起" }}
                    </el-descriptions-item>
                  </el-descriptions>
                </div>
              </el-card>

              <el-card shadow="never" header="专家利益冲突声明" class="detail-block">
                <el-alert
                  v-if="detail.conflict?.declaration?.validityStatus === 'EXPIRED'"
                  title="最近一份利益冲突说明已过期，具体任务必须重新核验。"
                  type="error"
                  :closable="false"
                  show-icon
                  class="declaration-warning"
                />
                <el-descriptions v-if="detail.conflict?.declaration?.materialId" :column="3" border>
                  <el-descriptions-item label="声明文件">{{ detail.conflict.declaration.fileName }}</el-descriptions-item>
                  <el-descriptions-item label="材料版本">V{{ detail.conflict.declaration.versionNo }}</el-descriptions-item>
                  <el-descriptions-item label="审核状态">
                    <el-tag type="success" size="small">已审核通过</el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="当前有效性">
                    <el-tag
                      :type="detail.conflict.declaration.validityStatus === 'EXPIRED' ? 'danger' : 'success'"
                      size="small"
                    >
                      {{ detail.conflict.declaration.validityStatus === "EXPIRED" ? "已过期" : "当前有效" }}
                    </el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="有效期">{{ validityText(detail.conflict.declaration) }}</el-descriptions-item>
                  <el-descriptions-item label="审核意见" :span="2">
                    {{ detail.conflict.declaration.reviewComment || "无补充意见" }}
                  </el-descriptions-item>
                </el-descriptions>
                <el-empty v-else description="未找到已审核通过的利益冲突说明" :image-size="72" />
              </el-card>

              <el-card shadow="never" header="利益冲突与独立性画像" class="detail-block">
                <el-table :data="detail.conflict?.dimensions || []" border>
                  <el-table-column label="维度" prop="dimensionName" min-width="180" />
                  <el-table-column label="评分" width="90" align="center">
                    <template #default="{ row }">{{ scoreText(row) }}</template>
                  </el-table-column>
                  <el-table-column label="状态" width="110">
                    <template #default="{ row }">
                      <el-tag :type="dimensionTag(row.dataStatus)" size="small">
                        {{ dimensionStatusText(row.dataStatus) }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="证据摘要" prop="evidence" min-width="360" show-overflow-tooltip />
                </el-table>
              </el-card>
            </el-tab-pane>
          </el-tabs>
        </template>
        <el-empty v-else description="请选择专家查看有效业务档案" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup name="BusinessExpert">
import {
  checkBusinessEligibility,
  cancelBusinessArrangement,
  confirmBusinessArrangement,
  getBusinessExpertDetail,
  listBusinessExperts,
  listBusinessOccupancy,
  listBusinessTaskAcceptances,
  requestBusinessTaskAcceptance
} from "@/api/expert/platform"

const loading = ref(false)
const experts = ref([])
const total = ref(0)
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref({})
const activeTab = ref("overview")
const eligibilityLoading = ref(false)
const eligibilityResult = ref(null)
const eligibilityResultTaskKey = ref("")
const occupancyLoading = ref(false)
const occupancyRecords = ref([])
const taskRequestLoading = ref(false)
const arrangementLoading = ref(false)
const acceptedTasks = ref([])
const selectedTaskKey = ref("")
const currentTaskRequest = computed(() =>
  acceptedTasks.value.find(item =>
    item.externalTaskId === taskForm.externalTaskId
      && normalizeFormTime(item.serviceStart) === normalizeFormTime(taskForm.serviceStart)
      && normalizeFormTime(item.serviceEnd) === normalizeFormTime(taskForm.serviceEnd)
  ) || null
)
const requestBlockedReason = computed(() => {
  if (eligibilityResult.value?.decision !== "INELIGIBLE") return ""
  return "本次核验未通过，不能发起专家接单。请先处理不通过的检查项。"
})
const currentTaskSignature = computed(() => {
  if (!taskForm.externalTaskId || !taskForm.serviceStart || !taskForm.serviceEnd) return ""
  return taskOptionKey(taskForm)
})
const eligibilityRequired = computed(() =>
  !eligibilityResult.value
    || eligibilityResultTaskKey.value !== currentTaskSignature.value
)
const requestButtonDisabled = computed(() =>
  Boolean(currentTaskRequest.value)
    || eligibilityRequired.value
    || Boolean(requestBlockedReason.value)
)
const requestButtonHint = computed(() =>
  requestBlockedReason.value
    || (eligibilityRequired.value ? "请先核验当前任务，核验后才能发起专家接单。" : "")
)
const requestButtonText = computed(() => {
  if (currentTaskRequest.value) {
    return `已发起（${acceptanceText(currentTaskRequest.value.status)}）`
  }
  if (requestBlockedReason.value) return "不能发起专家接单"
  return eligibilityRequired.value ? "请先核验业务" : "发起专家接单"
})
const eligibilityDescription = computed(() => {
  if (!eligibilityResult.value) return ""
  const reasons = eligibilityResult.value.reasons?.join("；")
  if (eligibilityResult.value.decision === "INELIGIBLE") {
    return `不能发起专家接单。${reasons || "本次核验未通过，请检查各项业务条件。"}`
  }
  return reasons || "各项条件已核验"
})
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  realName: undefined,
  organization: undefined,
  serviceStatus: undefined
})
const taskForm = reactive({
  externalTaskId: "",
  serviceStart: "",
  serviceEnd: "",
  serviceMode: "REMOTE",
  serviceRegion: "",
  industry: ""
})
const taskOptions = computed(() => {
  const options = new Map()
  for (const item of occupancyRecords.value)
  {
    if (!item.externalTaskId) continue
    const key = taskOptionKey(item)
    options.set(key, {
      ...item,
      optionKey: key,
      status: null,
      contextSource: "BUSINESS_CONTEXT"
    })
  }
  for (const item of acceptedTasks.value)
  {
    const key = taskOptionKey(item)
    options.set(key, {
      ...(options.get(key) || {}),
      ...item,
      optionKey: key,
      contextSource: "ACCEPTANCE_REQUEST"
    })
  }
  return [...options.values()]
})
const selectedTaskOption = computed(() =>
  taskOptions.value.find(item => item.optionKey === selectedTaskKey.value) || null
)
const releaseVersionBehind = computed(() => {
  const latestRelease = detail.value.latestRelease
  if (!latestRelease?.releaseId) return false
  return Number(detail.value.expert?.graphVersion || 0)
    > Number(latestRelease.graphVersion || 0)
})

const scoredDimensions = computed(() =>
  (detail.value.dimensions || []).filter(item =>
    item.dataStatus === "AVAILABLE" || item.dataStatus === "CONFLICT"
  )
)
const conflictDimensionCount = computed(() =>
  (detail.value.dimensions || []).filter(item => item.dataStatus === "CONFLICT").length
)
const profileCoverage = computed(() => {
  const totalCount = detail.value.dimensions?.length || 0
  return totalCount ? Math.round(scoredDimensions.value.length * 100 / totalCount) : 0
})

function getList() {
  loading.value = true
  listBusinessExperts(queryParams)
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
  Object.assign(queryParams, {
    pageNum: 1,
    keyword: undefined,
    realName: undefined,
    organization: undefined,
    serviceStatus: undefined
  })
  getList()
}

function openDetail(row) {
  detail.value = {}
  activeTab.value = "overview"
  eligibilityResult.value = null
  eligibilityResultTaskKey.value = ""
  occupancyRecords.value = []
  acceptedTasks.value = []
  selectedTaskKey.value = ""
  Object.assign(taskForm, {
    externalTaskId: "",
    serviceStart: "",
    serviceEnd: "",
    serviceMode: "REMOTE",
    serviceRegion: "",
    industry: ""
  })
  detailOpen.value = true
  detailLoading.value = true
  getBusinessExpertDetail(row.userId)
    .then(res => {
      detail.value = res.data || {}
      loadOccupancy()
      loadAcceptedTasks()
    })
    .finally(() => {
      detailLoading.value = false
    })
}

function loadAcceptedTasks() {
  if (!detail.value.expert?.userId) return
  return listBusinessTaskAcceptances(detail.value.expert.userId)
    .then(res => {
      acceptedTasks.value = res.data || []
      syncSelectedTask()
    })
}

function applyTaskOption(optionKey) {
  const task = taskOptions.value.find(item => item.optionKey === optionKey)
  if (task) {
    eligibilityResult.value = null
    eligibilityResultTaskKey.value = ""
    applyTaskOptionValue(task)
  }
}

function applyTaskOptionValue(task) {
  taskForm.externalTaskId = task.externalTaskId
  taskForm.serviceStart = task.serviceStart
  taskForm.serviceEnd = task.serviceEnd
  taskForm.serviceMode = task.serviceMode || "REMOTE"
  taskForm.serviceRegion = task.serviceRegion || detail.value.expert?.serviceRegion || ""
  taskForm.industry = task.industry || detail.value.expert?.industryField || ""
}

function syncSelectedTask() {
  if (!selectedTaskKey.value) return
  const task = taskOptions.value.find(item =>
    item.optionKey === selectedTaskKey.value
  )
  if (task) applyTaskOptionValue(task)
  else selectedTaskKey.value = ""
}

function taskOptionKey(item) {
  return `${item.externalTaskId}|${normalizeFormTime(item.serviceStart)}|${normalizeFormTime(item.serviceEnd)}`
}

function taskOptionLabel(item) {
  const state = item.status ? acceptanceText(item.status)
    : item.occupancyStatus ? occupancyText(item.occupancyStatus) : "可发起"
  return `${item.externalTaskId} · ${item.serviceStart} · ${state}`
}

function loadOccupancy() {
  if (!detail.value.expert?.userId) return
  occupancyLoading.value = true
  const start = new Date()
  const end = new Date()
  end.setDate(end.getDate() + 90)
  return listBusinessOccupancy(
    detail.value.expert.userId,
    `${formatDate(start)} 00:00:00`,
    `${formatDate(end)} 23:59:59`
  )
    .then(res => {
      occupancyRecords.value = (res.data || []).filter(item =>
        String(item.externalTaskId || "").trim()
      )
    })
    .finally(() => {
      occupancyLoading.value = false
    })
}

function runEligibility() {
  if (!detail.value.expert?.userId) return
  if (!taskForm.externalTaskId || !taskForm.serviceStart || !taskForm.serviceEnd) {
    ElMessage.warning("请先选择一个业务任务")
    return
  }
  eligibilityLoading.value = true
  eligibilityResult.value = null
  eligibilityResultTaskKey.value = ""
  const requestedTaskKey = currentTaskSignature.value
  checkBusinessEligibility({
    userId: detail.value.expert.userId,
    externalTaskId: taskForm.externalTaskId,
    serviceStart: taskForm.serviceStart,
    serviceEnd: taskForm.serviceEnd,
    serviceMode: taskForm.serviceMode,
    serviceRegion: taskForm.serviceRegion || undefined,
    industry: taskForm.industry || undefined
  })
    .then(res => {
      if (currentTaskSignature.value === requestedTaskKey) {
        eligibilityResult.value = res.data || null
        eligibilityResultTaskKey.value = requestedTaskKey
      }
    })
    .finally(() => {
      eligibilityLoading.value = false
    })
}

function requestTaskConfirmation() {
  if (eligibilityRequired.value) {
    ElMessage.warning("请先核验当前任务，核验后才能发起专家接单")
    return
  }
  if (requestBlockedReason.value) {
    ElMessage.warning(requestBlockedReason.value)
    return
  }
  if (currentTaskRequest.value) {
    ElMessage.info(`该任务已经发起，当前状态：${acceptanceText(currentTaskRequest.value.status)}`)
    return
  }
  if (!detail.value.expert?.userId || !taskForm.externalTaskId) {
    ElMessage.warning("请先填写外部任务号")
    return
  }
  taskRequestLoading.value = true
  requestBusinessTaskAcceptance({
    externalTaskId: taskForm.externalTaskId,
    userId: detail.value.expert.userId,
    serviceStart: taskForm.serviceStart,
    serviceEnd: taskForm.serviceEnd,
    serviceMode: taskForm.serviceMode,
    serviceRegion: taskForm.serviceRegion || undefined,
    industry: taskForm.industry || undefined,
    sourceSystem: "business-workspace"
  })
    .then(res => {
      const status = res.data?.status
      if (res.data?.externalTaskId) {
        taskForm.externalTaskId = res.data.externalTaskId
      }
      ElMessage.success(
        status === "PENDING"
          ? "专家接单请求已发起，等待专家确认"
          : `该任务已经发起，当前状态：${acceptanceText(status)}`
      )
      loadAcceptedTasks()
      runEligibility()
    })
    .finally(() => {
      taskRequestLoading.value = false
    })
}

function confirmArrangement() {
  if (!currentTaskRequest.value) return
  arrangementLoading.value = true
  confirmBusinessArrangement(currentTaskRequest.value.confirmationId)
    .then(() => {
      ElMessage.success("正式安排已确认，任务占用已生成")
      return Promise.all([loadAcceptedTasks(), loadOccupancy()])
    })
    .finally(() => {
      arrangementLoading.value = false
    })
}

function cancelArrangement() {
  if (!currentTaskRequest.value) return
  arrangementLoading.value = true
  cancelBusinessArrangement(currentTaskRequest.value.confirmationId)
    .then(() => {
      ElMessage.success("正式安排已取消，任务占用已释放")
      return Promise.all([loadAcceptedTasks(), loadOccupancy()])
    })
    .finally(() => {
      arrangementLoading.value = false
    })
}

function normalizeFormTime(value) {
  if (!value) return ""
  const text = String(value).replace("T", " ").slice(0, 19)
  return text.length === 16 ? `${text}:00` : text
}

function formatDate(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, "0")
  const day = String(date.getDate()).padStart(2, "0")
  return `${year}-${month}-${day}`
}

function statusText(value) {
  return {
    AVAILABLE: "正常接单",
    PAUSED: "暂停接单",
    CREDIT_BLOCKED: "信誉禁止接单",
    PENDING_CERTIFICATION: "待认证",
    EXITED: "已退出"
  }[value] || value || "未知"
}

function statusTag(value) {
  return value === "AVAILABLE" ? "success"
    : value === "CREDIT_BLOCKED" || value === "EXITED" ? "danger" : "warning"
}

function weekdayText(value) {
  return ["", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"][Number(value)] || "-"
}

function modeText(value) {
  return { REMOTE: "远程", ONSITE: "现场", BOTH: "远程或现场" }[value] || value || "-"
}

function occupancyText(value) {
  return {
    FREE: "可以安排",
    OCCUPIED: "已有任务",
    BLOCKED: "暂不可安排",
    UNKNOWN: "需要核实"
  }[value] || "需要核实"
}

function occupancyTag(value) {
  return {
    FREE: "success",
    OCCUPIED: "danger",
    BLOCKED: "warning",
    UNKNOWN: "info"
  }[value] || "info"
}

function sourceKindText(value) {
  return {
    EXTERNAL: "业务系统",
    MANUAL_IMPORT: "人工导入",
    MOCK: "演示数据"
  }[value] || "系统记录"
}

function conflictText(value) {
  return {
    NONE: "未发现冲突",
    POTENTIAL: "需要核实冲突",
    CONFIRMED: "存在明确冲突",
    UNKNOWN: "冲突待核实"
  }[value] || "冲突待核实"
}

function acceptanceText(value) {
  return {
    PENDING: "待专家确认",
    ACCEPTED: "专家已接受",
    REJECTED: "专家已拒绝",
    NOT_REQUESTED: "尚未发起",
    NO_TASK_CONTEXT: "缺少任务号"
  }[value] || value || "未知"
}

function arrangementText(value) {
  return {
    NOT_CONFIRMED: "未确认",
    CONFIRMED: "已确认",
    CANCELLED: "已取消"
  }[value] || "未确认"
}

function dimensionStatusText(value) {
  return {
    AVAILABLE: "有证据",
    CONFLICT: "证据冲突",
    INSUFFICIENT_EVIDENCE: "证据不足"
  }[value] || "未知"
}

function dimensionTag(value) {
  return {
    AVAILABLE: "success",
    CONFLICT: "danger",
    INSUFFICIENT_EVIDENCE: "info"
  }[value] || "info"
}

function scoreText(row) {
  return row.dataStatus === "INSUFFICIENT_EVIDENCE" ? "—" : Math.round(Number(row.score || 0))
}

function decisionTitle(value) {
  return {
    ELIGIBLE: "核验通过，可进入后续业务",
    INELIGIBLE: "不满足业务条件",
    REQUIRES_MANUAL_CONFIRMATION: "需要人工确认"
  }[value] || value
}

function decisionTag(value) {
  return {
    ELIGIBLE: "success",
    INELIGIBLE: "error",
    REQUIRES_MANUAL_CONFIRMATION: "warning"
  }[value] || "info"
}

function checkText(value) {
  return { PASS: "通过", WARN: "待确认", FAIL: "不通过" }[value] || value
}

function checkTag(value) {
  return { PASS: "success", WARN: "warning", FAIL: "danger" }[value] || "info"
}

function validityText(declaration) {
  if (!declaration.validFrom && !declaration.validUntil) return "长期有效"
  return `${declaration.validFrom || "不限"} 至 ${declaration.validUntil || "长期"}`
}

getList()
</script>

<style scoped lang="scss">
.business-workspace {
  .page-head {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 20px;
    padding: 18px 22px;
    margin-bottom: 16px;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 10px;
    background:
      linear-gradient(135deg, color-mix(in srgb, var(--el-color-primary) 8%, #fff), #fff 55%);
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
    color: var(--el-color-primary);
    font-size: 11px;
    font-weight: 700;
    letter-spacing: .12em;
  }
}
.read-only-tip {
  margin-bottom: 16px;
}
.search-form {
  padding: 16px 16px 0;
  margin-bottom: 16px;
  border-radius: 10px;
  background: #fff;
}
.sub-text {
  margin-top: 3px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.detail-shell {
  min-height: 360px;
}
.version-sync-alert {
  margin-bottom: 14px;
}
.expert-hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  padding: 18px 20px;
  margin-bottom: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-fill-color-extra-light);
  h3 {
    margin: 0;
    color: #20384b;
  }
  p {
    margin: 8px 0 0;
    color: var(--el-text-color-secondary);
  }
}
.hero-title {
  display: flex;
  align-items: center;
  gap: 9px;
}
.hero-version {
  display: grid;
  grid-template-columns: auto auto;
  gap: 5px 10px;
  text-align: right;
  span {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
  strong {
    color: var(--el-color-primary);
  }
  .empty-version {
    color: var(--el-text-color-secondary);
    font-weight: 400;
  }
}
.business-tabs {
  margin-top: 4px;
}
.detail-block,
.tab-tip {
  margin-top: 16px;
}
.profile-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
  > div {
    padding: 14px 16px;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 9px;
    background: var(--el-fill-color-extra-light);
  }
  span,
  strong {
    display: block;
  }
  span {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
  strong {
    margin-top: 6px;
    color: #27475d;
    font-size: 24px;
  }
}
.task-form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 14px;
}
.task-select-item {
  grid-column: 1 / -1;
}
.selected-task {
  margin-bottom: 16px;
}
.eligibility-result {
  margin-top: 16px;
}
.declaration-warning {
  margin-bottom: 14px;
}
.result-table {
  margin-top: 12px;
}
@media (max-width: 900px) {
  .task-form-grid,
  .profile-summary {
    grid-template-columns: 1fr;
  }
  .expert-hero {
    flex-direction: column;
    align-items: flex-start;
  }
  .hero-version {
    text-align: left;
  }
}
</style>
