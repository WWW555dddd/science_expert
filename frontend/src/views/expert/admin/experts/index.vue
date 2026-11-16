<template>
  <div class="app-container expert-admin">
    <div class="page-head">
      <div>
        <span class="eyebrow">EXPERT OPERATIONS</span>
        <h2>专家总体情况</h2>
        <p>统一查看和维护全部专家账号的档案、认证进度、材料、画像与接单状态。</p>
      </div>
      <div class="head-actions">
        <el-tag effect="plain">共 {{ total }} 个专家账号</el-tag>
        <el-button type="primary" icon="Plus" v-hasPermi="['expert:admin:expert:edit']" @click="openCreate">
          新增专家档案
        </el-button>
      </div>
    </div>

    <el-alert
      title="管理员清空专家后会删除档案、认证材料、审核记录、时间表、画像和MinIO文件；账号与专家角色保留，并退回未认证状态。"
      type="info"
      :closable="false"
      show-icon
      class="operation-tip"
    />

    <el-form :inline="true" :model="queryParams" class="search-form">
      <el-form-item label="登录账号">
        <el-input v-model="queryParams.userName" clearable placeholder="账号" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="专家姓名">
        <el-input v-model="queryParams.realName" clearable placeholder="姓名" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="所属单位">
        <el-input v-model="queryParams.organization" clearable placeholder="单位名称" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="档案状态">
        <el-select v-model="queryParams.reviewStatus" clearable placeholder="全部" style="width: 132px">
          <el-option label="未建档" value="NOT_REGISTERED" />
          <el-option label="草稿" value="DRAFT" />
          <el-option label="待审核" value="PENDING_REVIEW" />
          <el-option label="审核中" value="REVIEWING" />
          <el-option label="退回补正" value="RETURNED" />
          <el-option label="已认证" value="APPROVED" />
        </el-select>
      </el-form-item>
      <el-form-item label="接单状态">
        <el-select v-model="queryParams.serviceStatus" clearable placeholder="全部" style="width: 120px">
          <el-option label="正常接单" value="AVAILABLE" />
          <el-option label="暂停接单" value="PAUSED" />
          <el-option label="待认证" value="PENDING_CERTIFICATION" />
          <el-option label="已退出" value="EXITED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-card shadow="never">
      <el-table :data="experts" v-loading="loading" row-key="userId" border>
        <el-table-column label="专家账号" width="150" fixed="left">
          <template #default="{ row }">
            <strong>{{ row.realName || row.nickName || "未建档" }}</strong>
            <div class="sub-text">{{ row.userName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="单位 / 职称" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.organization || "尚未填写单位" }}</div>
            <div class="sub-text">{{ row.professionalTitle || "尚未填写职称" }}</div>
          </template>
        </el-table-column>
        <el-table-column label="专业方向" prop="researchField" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.researchField || "尚未填写" }}</template>
        </el-table-column>
        <el-table-column label="产业领域" prop="industryField" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.industryField || "尚未填写" }}</template>
        </el-table-column>
        <el-table-column label="服务区域 / 联系方式" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.serviceRegion || "尚未填写区域" }}</div>
            <div class="sub-text">{{ row.phone || row.email || "尚未填写联系方式" }}</div>
          </template>
        </el-table-column>
        <el-table-column label="认证进度" width="135" align="center">
          <template #default="{ row }">
            <div class="cert-cell">
              <strong>{{ row.passedRequired || 0 }}/{{ row.requiredTotal || 0 }}</strong>
              <el-progress
                :percentage="certificationPercent(row)"
                :show-text="false"
                :stroke-width="7"
                :color="certificationColor(row)"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="总体情况" width="145">
          <template #default="{ row }">
            <el-tag :type="reviewTagType(row.reviewStatus)" size="small">
              {{ reviewStatusText(row.reviewStatus) }}
            </el-tag>
            <div class="sub-text">完成度 {{ Math.min(100, Number(row.profileCompletion || 0)) }}%</div>
          </template>
        </el-table-column>
        <el-table-column label="接单状态" width="110">
          <template #default="{ row }">
            <el-tag :type="serviceTagType(row.serviceStatus)" size="small">
              {{ serviceStatusText(row.serviceStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="资料 / 时间表" width="120" align="center">
          <template #default="{ row }">
            <span>{{ row.materialCount || 0 }} 份</span>
            <div class="sub-text">{{ row.scheduleCount || 0 }} 个时段</div>
          </template>
        </el-table-column>
        <el-table-column label="画像" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.graphVersion > 0" type="success" size="small">
              V{{ row.graphVersion }}
            </el-tag>
            <el-tag v-else-if="row.draftGraphDimensionCount > 0" type="warning" size="small">
              部分
            </el-tag>
            <span v-else class="sub-text">待生成</span>
          </template>
        </el-table-column>
        <el-table-column label="最近提交" prop="latestSubmitTime" width="170">
          <template #default="{ row }">{{ row.latestSubmitTime || "尚未提交" }}</template>
        </el-table-column>
        <el-table-column label="操作" width="285" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.profileId"
              link
              type="success"
              icon="DataAnalysis"
              @click="openGraph(row)"
            >
              画像
            </el-button>
            <el-button
              v-if="row.profileId"
              link
              type="warning"
              icon="Share"
              v-hasPermi="['expert:admin:expert:edit']"
              @click="openKnowledgeGraph(row)"
            >
              图谱
            </el-button>
            <el-button
              link
              type="primary"
              icon="Edit"
              v-hasPermi="['expert:admin:expert:edit']"
              @click="openEdit(row)"
            >
              {{ row.profileId ? "编辑" : "建档" }}
            </el-button>
            <el-button
              v-if="row.profileId"
              link
              type="danger"
              icon="Delete"
              v-hasPermi="['expert:admin:expert:remove']"
              @click="removeExpert(row)"
            >
              清空
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

    <el-dialog
      v-model="dialogOpen"
      :title="dialogMode === 'create' ? '新增专家档案' : '维护专家总体情况'"
      width="960px"
      append-to-body
      destroy-on-close
    >
      <el-alert
        v-if="restoring"
        title="检测到旧版软删除档案；保存后将按管理员认证流程重新启用。"
        type="warning"
        :closable="false"
        show-icon
        class="dialog-alert"
      />
      <el-form ref="profileRef" :model="profileForm" :rules="rules" label-width="92px">
        <el-form-item v-if="dialogMode === 'create'" label="专家账号" prop="userId">
          <el-select
            v-model="profileForm.userId"
            filterable
            placeholder="请选择已分配专家角色的账号"
            style="width: 100%"
            @change="handleUserChange"
          >
            <el-option
              v-for="item in availableUsers"
              :key="item.userId"
              :label="userOptionLabel(item)"
              :value="item.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="专家账号">
          <el-input :model-value="editingAccount" disabled />
        </el-form-item>
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
          <el-col :sm="12">
            <el-form-item label="接单状态">
              <el-select
                v-model="profileForm.serviceStatus"
                :disabled="serviceStatusLocked"
                style="width: 100%"
              >
                <el-option label="正常接单" value="AVAILABLE" />
                <el-option label="暂停接单" value="PAUSED" />
                <el-option label="待认证（通过后自动开放）" value="PENDING_CERTIFICATION" disabled />
                <el-option label="退出专家库" value="EXITED" />
              </el-select>
              <div v-if="serviceStatusLocked" class="status-lock-tip">
                未正式认证，接单状态已锁定为“待认证”；认证通过后自动开放，之后才可切换其他状态。
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="专家简介">
              <el-input
                v-model="profileForm.careerSummary"
                type="textarea"
                :rows="4"
                maxlength="2000"
                show-word-limit
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <div class="admin-material-section" v-loading="certificationLoading">
        <div class="material-heading">
          <div>
            <h3>认证材料</h3>
            <p>管理员上传的认证材料免人工审核。四个必选角度全部具备后，专家会直接认证通过。</p>
          </div>
          <el-tag :type="requiredMaterialProgress >= requiredAngles.length ? 'success' : 'warning'">
            必选材料 {{ requiredMaterialProgress }}/{{ requiredAngles.length }}
          </el-tag>
        </div>
        <el-alert
          title="已有“已认证”材料可以沿用；未认证角度需要由管理员重新上传。保存后自动进入字段提取、脱敏和画像分析。"
          type="info"
          :closable="false"
          show-icon
          class="material-tip"
        />
        <el-empty v-if="!profileForm.userId" description="请先选择专家账号" :image-size="70" />
        <div v-else class="admin-angle-grid">
          <div
            v-for="angle in certificationAngles"
            :key="angle.categoryCode"
            class="admin-angle-card"
            :class="{ 'angle-ready': isAngleReady(angle) }"
          >
            <div class="angle-title">
              <div>
                <strong>{{ angle.angleName }}</strong>
                <el-tag v-if="angle.requiredFlag === '1'" type="danger" size="small" effect="plain">必选</el-tag>
                <el-tag v-else type="info" size="small" effect="plain">可选</el-tag>
              </div>
              <el-tag :type="isAngleReady(angle) ? 'success' : uploadedMaterials[angle.categoryCode] ? 'warning' : 'info'">
                {{ angleStateText(angle) }}
              </el-tag>
            </div>
            <div class="angle-current">
              <template v-if="uploadedMaterials[angle.categoryCode]">
                <span>{{ uploadedMaterials[angle.categoryCode].fileName }}</span>
                <small>待保存 · 保存后免审核直接通过</small>
              </template>
              <template v-else-if="angle.materialId">
                <span>{{ angle.fileName }}</span>
                <small>V{{ angle.versionNo }} · {{ materialReviewStatusText(angle.reviewStatus) }}</small>
              </template>
              <span v-else>尚未上传该角度材料</span>
            </div>
            <el-upload
              :action="adminUploadAction"
              :headers="uploadHeaders"
              :show-file-list="false"
              :disabled="!profileForm.userId || uploadingCategory === angle.categoryCode"
              :before-upload="file => beforeAdminUpload(file, angle)"
              :on-success="(response, file) => handleAdminUploadSuccess(response, file, angle.categoryCode)"
              :on-error="handleAdminUploadError"
              accept=".pdf,.docx,.txt,.csv,.md,.png,.jpg,.jpeg"
            >
              <el-button
                plain
                :type="isAngleReady(angle) ? 'success' : 'primary'"
                :loading="uploadingCategory === angle.categoryCode"
                icon="Upload"
              >
                {{ isAngleReady(angle) ? "更新材料" : "上传材料" }}
              </el-button>
            </el-upload>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="certificationLoading || Boolean(uploadingCategory)"
          @click="submitProfile"
        >
          保存并认证
        </el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="graphDrawerOpen"
      :title="`${graphExpert.realName || graphExpert.userName || '专家'}的30维画像`"
      size="88%"
      destroy-on-close
    >
      <div v-loading="graphLoading" class="admin-graph-view">
        <div class="graph-summary">
          <div>
            <span class="eyebrow">EXPERT PROFILE</span>
            <h2>{{ graphExpert.realName || graphExpert.nickName || "未命名专家" }}</h2>
            <p>{{ graphExpert.userName }} · {{ graphExpert.organization || "未填写单位" }}</p>
          </div>
          <div class="graph-stats">
            <el-tag :type="graphTagType" effect="dark">
              {{ adminGraph.statusText || graphStatusFallback }}
            </el-tag>
            <el-tag v-if="adminGraph.profileKind === 'LEGACY_PROFILE'" type="warning" effect="plain">
              旧版评分画像
            </el-tag>
            <el-tag v-else-if="adminGraph.profileKind === 'GRAPH_PROFILE'" type="success" effect="plain">
              知识图谱驱动
            </el-tag>
            <strong>V{{ adminGraph.graphVersion || 0 }}</strong>
            <span>平均分 {{ graphAverage }}</span>
            <span>证据覆盖 {{ adminGraph.coverage || 0 }}%</span>
            <span>{{ graphDimensions.length }}/{{ adminGraph.totalDimensionCount || 0 }} 个维度</span>
          </div>
        </div>
        <el-alert
          v-if="adminGraph.message"
          :title="adminGraph.message"
          type="info"
          :closable="false"
          show-icon
          class="graph-status-tip"
        />

        <el-empty
          v-if="!graphDimensions.length"
          :description="adminGraph.message || '该专家尚未生成画像，请等待档案材料处理完成'"
        />
        <div v-else class="admin-graph-groups">
          <el-card v-for="group in graphGroups" :key="group.groupCode" shadow="never" class="graph-group-card">
            <template #header>
              <div class="graph-group-title">
                <span>{{ group.groupName }}</span>
                <strong>{{ group.average }}</strong>
              </div>
            </template>
            <div v-for="dimension in group.items" :key="dimension.dimensionCode" class="graph-dimension-row">
              <div class="graph-dimension-label">
                <span>{{ dimension.dimensionName }}</span>
                <el-tooltip :content="dimensionTooltip(dimension)" placement="top">
                  <el-button link icon="InfoFilled" />
                </el-tooltip>
              </div>
              <el-tag v-if="dimension.dataStatus === 'INSUFFICIENT_EVIDENCE'" type="info" size="small">
                证据不足
              </el-tag>
              <el-tag v-else-if="dimension.dataStatus === 'CONFLICT'" type="danger" size="small">
                冲突 · {{ Math.round(Number(dimension.score || 0)) }}
              </el-tag>
              <el-progress
                v-else
                :percentage="Number(dimension.score || 0)"
                :stroke-width="9"
                :color="graphScoreColor(dimension.score)"
              />
              <small v-if="dimension.dataStatus !== 'INSUFFICIENT_EVIDENCE'">
                置信度 {{ Math.round(Number(dimension.confidence || 0) * 100) }}%
                · {{ dimension.sourceCount || 0 }} 份证据
                · {{ dimension.graphPathCount || 0 }} 条图谱路径
              </small>
              <small v-else>不生成占位分</small>
            </div>
          </el-card>
        </div>
      </div>
      <template #footer>
        <el-button @click="graphDrawerOpen = false">关闭</el-button>
      </template>
    </el-drawer>

    <el-drawer
      v-model="knowledgeGraphOpen"
      :title="`${knowledgeGraphExpert.realName || knowledgeGraphExpert.userName || '专家'}的知识图谱`"
      size="76%"
      destroy-on-close
    >
      <div v-loading="knowledgeGraphLoading">
        <el-descriptions :column="4" border>
          <el-descriptions-item label="状态">{{ knowledgeGraph.status || "UNKNOWN" }}</el-descriptions-item>
          <el-descriptions-item label="本体版本">{{ knowledgeGraph.ontologyVersion || "-" }}</el-descriptions-item>
          <el-descriptions-item label="实体数">{{ knowledgeGraph.entityCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="关系数">{{ knowledgeGraph.relationCount || 0 }}</el-descriptions-item>
        </el-descriptions>

        <el-card shadow="never" header="专家知识图谱" class="detail-block">
          <KnowledgeGraphSvg
            :entities="knowledgeGraph.entities || []"
            :relations="knowledgeGraph.relations || []"
            :seed-entity-id="knowledgeGraph.seedEntityId"
          />
        </el-card>

        <el-card shadow="never" header="实体" class="detail-block">
          <el-table :data="knowledgeGraph.entities || []" size="small">
            <el-table-column label="类型" prop="entityType" width="150" />
            <el-table-column label="标准名称" prop="canonicalName" min-width="200" />
            <el-table-column label="审核状态" prop="reviewStatus" width="110" />
            <el-table-column label="实体ID" prop="entityId" width="100" />
          </el-table>
        </el-card>

        <el-card shadow="never" header="关系与证据" class="detail-block">
          <el-table :data="knowledgeGraph.relations || []" size="small">
            <el-table-column label="起点" prop="sourceEntityName" min-width="150" />
            <el-table-column label="关系" prop="relationType" min-width="170" />
            <el-table-column label="终点" prop="targetEntityName" min-width="150" />
            <el-table-column label="状态" prop="reviewStatus" width="100" />
            <el-table-column label="证据数" width="90">
              <template #default="{ row }">{{ row.evidence?.length || 0 }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>
      <template #footer>
        <el-button @click="knowledgeGraphOpen = false">关闭</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup name="ExpertManagementList">
import {
  deleteAdminExpert,
  getAdminExpertCertifications,
  getAdminExpertGraph,
  getKnowledgeGraphSubgraph,
  listAdminExperts,
  listAdminExpertUsers,
  projectKnowledgeGraph,
  saveAdminExpert
} from "@/api/expert/platform"
import { getToken } from "@/utils/auth"
import KnowledgeGraphSvg from "./KnowledgeGraphSvg.vue"

const { proxy } = getCurrentInstance()
const loading = ref(false)
const saving = ref(false)
const experts = ref([])
const expertUsers = ref([])
const total = ref(0)
const dialogOpen = ref(false)
const dialogMode = ref("create")
const profileRef = ref()
const editingAccount = ref("")
const editingReviewStatus = ref("DRAFT")
const profileForm = reactive({})
const certificationAngles = ref([])
const uploadedMaterials = ref({})
const certificationLoading = ref(false)
const uploadingCategory = ref("")
const graphDrawerOpen = ref(false)
const graphLoading = ref(false)
const graphExpert = ref({})
const adminGraph = ref({ dimensions: [] })
const knowledgeGraphOpen = ref(false)
const knowledgeGraphLoading = ref(false)
const knowledgeGraphExpert = ref({})
const knowledgeGraph = ref({ entities: [], relations: [] })
const uploadActionBase = import.meta.env.VITE_APP_BASE_API + "/expert/admin/experts/"
const uploadHeaders = computed(() => ({ Authorization: "Bearer " + getToken() }))
const adminUploadAction = computed(() =>
  profileForm.userId ? `${uploadActionBase}${profileForm.userId}/materials/upload` : ""
)

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  userName: undefined,
  realName: undefined,
  organization: undefined,
  reviewStatus: undefined,
  serviceStatus: undefined
})

const rules = {
  userId: [{ required: true, message: "请选择专家账号", trigger: "change" }],
  realName: [{ required: true, message: "专家姓名不能为空", trigger: "blur" }],
  email: [{ type: "email", message: "电子邮箱格式不正确", trigger: "blur" }]
}

const availableUsers = computed(() =>
  expertUsers.value.filter(item => !item.profileId || item.delFlag === "2")
)
const restoring = computed(() =>
  dialogMode.value === "create" && expertUsers.value.some(item =>
    item.userId === profileForm.userId && item.delFlag === "2"
  )
)
const requiredAngles = computed(() =>
  certificationAngles.value.filter(angle => angle.requiredFlag === "1")
)
const requiredMaterialProgress = computed(() =>
  requiredAngles.value.filter(angle => isAngleReady(angle)).length
)
const serviceStatusLocked = computed(() => {
  if (editingReviewStatus.value !== "APPROVED") return true
  if (certificationLoading.value || !requiredAngles.value.length) return true
  return requiredAngles.value.some(angle => !isAngleReady(angle))
})
const graphDimensions = computed(() => adminGraph.value.dimensions || [])
const graphAverage = computed(() => {
  const scored = graphDimensions.value.filter(item =>
    item.dataStatus === "AVAILABLE" || item.dataStatus === "CONFLICT"
  )
  if (!scored.length) return 0
  const total = scored.reduce((sum, item) => sum + Number(item.score || 0), 0)
  return Math.round(total / scored.length)
})
const graphGroups = computed(() => {
  const groups = new Map()
  graphDimensions.value.forEach(item => {
    if (!groups.has(item.groupCode)) {
      groups.set(item.groupCode, {
        groupCode: item.groupCode,
        groupName: item.groupName,
        items: []
      })
    }
    groups.get(item.groupCode).items.push(item)
  })
  return [...groups.values()].map(group => ({
    ...group,
    average: group.items.some(item => item.dataStatus !== "INSUFFICIENT_EVIDENCE")
      ? Math.round(
          group.items
            .filter(item => item.dataStatus !== "INSUFFICIENT_EVIDENCE")
            .reduce((sum, item) => sum + Number(item.score || 0), 0)
          / group.items.filter(item => item.dataStatus !== "INSUFFICIENT_EVIDENCE").length
        )
      : "—"
  }))
})
const graphTagType = computed(() => ({
  APPROVED: "success",
  PARTIAL: "warning",
  PENDING: "info",
  UNAVAILABLE: "info"
})[adminGraph.value.status] || "info")
const graphStatusFallback = computed(() => ({
  APPROVED: "正式画像",
  PARTIAL: "部分画像",
  PENDING: "待生成",
  UNAVAILABLE: "暂无画像"
})[adminGraph.value.status] || "画像状态未知")

function getList() {
  loading.value = true
  listAdminExperts(queryParams)
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
    userName: undefined,
    realName: undefined,
    organization: undefined,
    reviewStatus: undefined,
    serviceStatus: undefined
  })
  getList()
}

function resetProfileForm() {
  for (const key of Object.keys(profileForm)) {
    delete profileForm[key]
  }
  Object.assign(profileForm, {
    userId: undefined,
    realName: "",
    organization: "",
    professionalTitle: "",
    researchField: "",
    industryField: "",
    serviceRegion: "",
    phone: "",
    email: "",
    careerSummary: "",
    version: 0,
    serviceStatus: "PENDING_CERTIFICATION"
  })
  certificationAngles.value = []
  uploadedMaterials.value = {}
  uploadingCategory.value = ""
  editingReviewStatus.value = "DRAFT"
  profileRef.value?.clearValidate()
}

function openCreate() {
  listAdminExpertUsers().then(res => {
    expertUsers.value = res.data || []
    resetProfileForm()
    dialogMode.value = "create"
    editingAccount.value = ""
    if (!availableUsers.value.length) {
      proxy.$modal.msgWarning("所有专家账号均已建立档案，可在用户管理中先分配专家角色")
      return
    }
    dialogOpen.value = true
  })
}

function openEdit(row) {
  resetProfileForm()
  dialogMode.value = "edit"
  editingReviewStatus.value = row.reviewStatus || "DRAFT"
  editingAccount.value = `${row.realName || row.nickName || "专家"}（${row.userName}）`
  Object.assign(profileForm, {
    userId: row.userId,
    realName: row.realName || row.nickName || "",
    organization: row.organization || "",
    professionalTitle: row.professionalTitle || "",
    researchField: row.researchField || "",
    industryField: row.industryField || "",
    serviceRegion: row.serviceRegion || "",
    phone: row.phone || "",
    email: row.email || "",
    careerSummary: row.careerSummary || "",
    version: row.version == null ? 0 : row.version,
    serviceStatus: row.reviewStatus === "APPROVED"
      ? row.serviceStatus || "AVAILABLE"
      : "PENDING_CERTIFICATION"
  })
  dialogOpen.value = true
  loadCertificationAngles(row.userId)
}

function handleUserChange(userId) {
  uploadedMaterials.value = {}
  certificationAngles.value = []
  if (userId) {
    loadCertificationAngles(userId)
  }
}

function loadCertificationAngles(userId) {
  certificationLoading.value = true
  getAdminExpertCertifications(userId)
    .then(res => {
      certificationAngles.value = res.data?.angles || []
      if (editingReviewStatus.value !== "APPROVED"
          || requiredAngles.value.some(angle => angle.reviewStatus !== "APPROVED")) {
        profileForm.serviceStatus = "PENDING_CERTIFICATION"
      }
    })
    .finally(() => {
      certificationLoading.value = false
    })
}

function beforeAdminUpload(file, angle) {
  const extension = file.name.split(".").pop().toLowerCase()
  const allowed = ["pdf", "docx", "txt", "csv", "md", "png", "jpg", "jpeg"]
  if (!allowed.includes(extension)) {
    proxy.$modal.msgError("仅支持 PDF、DOCX、TXT、CSV、MD 和图片文件")
    return false
  }
  if (file.size > 10 * 1024 * 1024) {
    proxy.$modal.msgError("单个文件不能超过10MB")
    return false
  }
  uploadingCategory.value = angle.categoryCode
  return true
}

function handleAdminUploadSuccess(response, file, categoryCode) {
  uploadingCategory.value = ""
  if (response.code !== 200) {
    proxy.$modal.msgError(response.msg || "认证材料上传失败")
    return
  }
  uploadedMaterials.value = {
    ...uploadedMaterials.value,
    [categoryCode]: {
      materialCategory: categoryCode,
      fileName: response.fileName || file.name,
      filePath: response.filePath,
      fileSize: file.size,
      fileType: response.fileType
    }
  }
  proxy.$modal.msgSuccess("材料已上传，保存专家档案后直接认证")
}

function handleAdminUploadError() {
  uploadingCategory.value = ""
  proxy.$modal.msgError("认证材料上传失败，请检查 MinIO 服务")
}

function submitProfile() {
  profileRef.value.validate(valid => {
    if (!valid) return
    const missing = requiredAngles.value.filter(angle => !isAngleReady(angle))
    if (missing.length) {
      proxy.$modal.msgWarning(`请上传必选认证材料：${missing.map(item => item.angleName).join("、")}`)
      return
    }
    saving.value = true
    saveAdminExpert(profileForm.userId, {
      profile: {
        ...profileForm,
        serviceStatus: serviceStatusLocked.value
          ? "PENDING_CERTIFICATION"
          : profileForm.serviceStatus
      },
      materials: Object.values(uploadedMaterials.value)
    })
      .then(() => {
        proxy.$modal.msgSuccess(dialogMode.value === "create"
          ? "专家建档完成，必选材料已直接认证通过"
          : "专家总体情况与认证材料已更新")
        dialogOpen.value = false
        getList()
      })
      .finally(() => {
        saving.value = false
      })
  })
}

function isAngleReady(angle) {
  return Boolean(
    uploadedMaterials.value[angle.categoryCode]
    || angle.reviewStatus === "APPROVED"
  )
}

function angleStateText(angle) {
  if (uploadedMaterials.value[angle.categoryCode]) return "待保存"
  if (angle.reviewStatus === "APPROVED") return "已认证"
  if (angle.materialId) return "未认证"
  return "待上传"
}

function materialReviewStatusText(status) {
  return {
    APPROVED: "已认证",
    PENDING: "待处理",
    PENDING_REVIEW: "原待审核",
    RETURNED: "退回补正",
    REJECTED: "退回补正"
  }[status] || "待认证"
}

function openGraph(row) {
  graphExpert.value = row
  graphDrawerOpen.value = true
  graphLoading.value = true
  adminGraph.value = { dimensions: [] }
  getAdminExpertGraph(row.userId)
    .then(res => {
      adminGraph.value = res.data || { dimensions: [] }
    })
    .finally(() => {
      graphLoading.value = false
    })
}

async function openKnowledgeGraph(row) {
  knowledgeGraphExpert.value = row
  knowledgeGraphOpen.value = true
  knowledgeGraphLoading.value = true
  knowledgeGraph.value = { entities: [], relations: [] }
  try
  {
    await projectKnowledgeGraph(row.userId)
    const response = await getKnowledgeGraphSubgraph(row.userId, 2)
    knowledgeGraph.value = response.data || { entities: [], relations: [] }
  }
  catch (error)
  {
    proxy.$modal.msgError(error?.message || "知识图谱投影失败")
  }
  finally
  {
    knowledgeGraphLoading.value = false
  }
}

function graphScoreColor(score) {
  if (Number(score) >= 80) return "#2f9e76"
  if (Number(score) >= 60) return "#3c82c4"
  if (Number(score) >= 40) return "#d89c35"
  return "#c75b5b"
}

function dimensionTooltip(dimension) {
  if (dimension.evidenceItems?.length) {
    return dimension.evidenceItems
      .slice(0, 3)
      .map(item => `材料#${item.materialId}：${item.evidence}`)
      .join("\n")
  }
  return dimension.evidence || "暂无可追溯证据"
}

function removeExpert(row) {
  proxy.$modal.confirm(
    `确认清空专家“${row.realName || row.userName}”的全部专家业务数据吗？账号和专家角色会保留，但档案、认证材料、审核记录、时间表和画像将被永久删除，并退回未认证状态。`
  ).then(() => deleteAdminExpert(row.userId))
    .then(() => {
      proxy.$modal.msgSuccess("专家业务数据已清空，账号已退回未认证状态")
      getList()
    })
    .catch(() => {})
}

function userOptionLabel(item) {
  const name = item.realName || item.nickName || item.userName
  if (item.delFlag === "2") {
    return `${name}（${item.userName}）· 已删除，可恢复`
  }
  return `${name}（${item.userName}）`
}

function reviewStatusText(status) {
  return {
    NOT_REGISTERED: "未建档",
    DRAFT: "草稿",
    PENDING_REVIEW: "待审核",
    REVIEWING: "审核中",
    RETURNED: "退回补正",
    REJECTED: "退回补正",
    APPROVED: "已认证"
  }[status] || status || "未建档"
}

function reviewTagType(status) {
  return {
    APPROVED: "success",
    PENDING_REVIEW: "warning",
    REVIEWING: "primary",
    RETURNED: "warning",
    REJECTED: "warning",
    NOT_REGISTERED: "info"
  }[status] || "info"
}

function serviceStatusText(status) {
  return {
    AVAILABLE: "正常接单",
    PAUSED: "暂停接单",
    PENDING_CERTIFICATION: "待认证",
    EXITED: "已退出"
  }[status] || status || "待认证"
}

function serviceTagType(status) {
  return {
    AVAILABLE: "success",
    PAUSED: "warning",
    PENDING_CERTIFICATION: "warning",
    EXITED: "info"
  }[status] || "info"
}

function certificationPercent(row) {
  const required = Number(row.requiredTotal || 0)
  if (!required) return 0
  return Math.round(Number(row.passedRequired || 0) * 100 / required)
}

function certificationColor(row) {
  return certificationPercent(row) >= 100 ? "#48a868" : "#d99b35"
}

getList()
</script>

<style scoped lang="scss">
.expert-admin {
  .page-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 20px;
    padding: 24px 28px;
    margin-bottom: 16px;
    border-radius: 12px;
    color: #fff;
    background: linear-gradient(125deg, #203b53, #376c68 68%, #62a093);
    h2 {
      margin: 5px 0 8px;
    }
    p {
      margin: 0;
      opacity: .82;
    }
  }
  .eyebrow {
    font-size: 12px;
    letter-spacing: 2px;
    opacity: .68;
  }
  .head-actions {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-shrink: 0;
  }
  .operation-tip {
    margin-bottom: 16px;
  }
  .search-form {
    padding: 16px 16px 0;
    margin-bottom: 16px;
    background: #fff;
    border-radius: 8px;
  }
  .sub-text {
    margin-top: 4px;
    color: #8794a1;
    font-size: 12px;
    line-height: 1.5;
  }
  .cert-cell {
    display: flex;
    flex-direction: column;
    gap: 6px;
    strong {
      color: #2d495c;
    }
  }
  .dialog-alert {
    margin-bottom: 18px;
  }
  .status-lock-tip {
    margin-top: 7px;
    color: #a06b22;
    font-size: 12px;
    line-height: 1.55;
  }
  .admin-material-section {
    margin-top: 6px;
    padding-top: 20px;
    border-top: 1px solid #edf0f3;
  }
  .material-heading {
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
      color: #7d8a96;
    }
  }
  .material-tip {
    margin: 14px 0;
  }
  .admin-angle-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }
  .admin-angle-card {
    display: flex;
    min-height: 150px;
    flex-direction: column;
    gap: 12px;
    padding: 14px;
    border: 1px solid #e3e8ed;
    border-left: 4px solid #aeb9c3;
    border-radius: 8px;
    background: #fbfcfd;
    &.angle-ready {
      border-left-color: #45a678;
      background: #f7fcf9;
    }
    .el-upload {
      margin-top: auto;
    }
  }
  .angle-title {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 8px;
    strong {
      margin-right: 5px;
      color: #2c4255;
    }
  }
  .angle-current {
    display: flex;
    flex-direction: column;
    gap: 5px;
    color: #566775;
    small {
      color: #8794a0;
    }
  }
  .admin-graph-view {
    padding: 0 6px 18px;
  }
  .graph-summary {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 24px;
    padding: 24px 26px;
    margin-bottom: 16px;
    border-radius: 12px;
    background: linear-gradient(120deg, #eef6fb, #edf8f2);
    h2 {
      margin: 6px 0 6px;
      color: #17324d;
    }
    p {
      margin: 0;
      color: #71808c;
    }
  }
  .graph-stats {
    display: flex;
    align-items: center;
    gap: 14px;
    color: #647481;
    strong {
      color: #17324d;
      font-size: 26px;
    }
  }
  .graph-status-tip {
    margin-bottom: 14px;
  }
  .admin-graph-groups {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
  }
  .graph-group-card {
    :deep(.el-card__body) {
      padding-top: 8px;
      padding-bottom: 8px;
    }
  }
  .graph-group-title {
    display: flex;
    justify-content: space-between;
    align-items: center;
    color: #17324d;
    font-weight: 600;
    strong {
      font-size: 22px;
    }
  }
  .graph-dimension-row {
    display: grid;
    grid-template-columns: minmax(120px, 1fr) minmax(140px, 1.8fr) 88px;
    align-items: center;
    gap: 10px;
    padding: 7px 0;
    small {
      color: #8a97a3;
      text-align: right;
    }
  }
  .graph-dimension-label {
    display: flex;
    align-items: center;
    gap: 3px;
    color: #465665;
  }
}

@media (max-width: 900px) {
  .expert-admin .page-head {
    align-items: flex-start;
    flex-direction: column;
  }
  .expert-admin .admin-angle-grid {
    grid-template-columns: 1fr;
  }
  .expert-admin .admin-graph-groups {
    grid-template-columns: 1fr;
  }
  .expert-admin .graph-summary {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
