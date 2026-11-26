<template>
  <section class="version-history" v-loading="loading">
    <div class="version-heading">
      <div>
        <h3>画像与知识图谱版本</h3>
        <p>每次审核材料形成新的生效版本时保存快照。待审核的新文件不会覆盖当前已通过版本。</p>
      </div>
      <el-tag effect="plain">共 {{ versions.length }} 个审核版本</el-tag>
    </div>

    <el-empty
      v-if="!versions.length"
      description="暂无已审核版本；首份必选材料审核通过后会生成版本快照"
    />
    <template v-else>
      <el-timeline class="version-timeline">
        <el-timeline-item
          v-for="version in versions"
          :key="version.releaseId"
          :timestamp="formatTime(version.createTime)"
          placement="top"
          :color="version.releaseId === versions[0].releaseId ? '#2f9e76' : '#8aa0b3'"
        >
          <div class="timeline-card">
            <strong>审核版本 V{{ version.versionNo }}</strong>
            <span>画像 V{{ version.graphVersion || 0 }}</span>
            <span>{{ triggerText(version.triggerType) }}</span>
            <span>审核人：{{ version.approvedBy || "系统" }}</span>
          </div>
        </el-timeline-item>
      </el-timeline>

      <el-card shadow="never" class="compare-card">
        <div class="compare-toolbar">
          <div class="version-select">
            <span>对比基准</span>
            <el-select v-model="leftId" placeholder="选择旧版本" @change="loadComparison">
              <el-option
                v-for="version in versions"
                :key="version.releaseId"
                :label="`V${version.versionNo} · ${formatTime(version.createTime)}`"
                :value="version.releaseId"
              />
            </el-select>
          </div>
          <span class="compare-arrow">→</span>
          <div class="version-select">
            <span>目标版本</span>
            <el-select v-model="rightId" placeholder="选择新版本" @change="loadComparison">
              <el-option
                v-for="version in versions"
                :key="version.releaseId"
                :label="`V${version.versionNo} · ${formatTime(version.createTime)}`"
                :value="version.releaseId"
              />
            </el-select>
          </div>
          <el-button type="primary" plain :loading="comparing" @click="loadComparison">
            加载对比
          </el-button>
        </div>

        <div v-if="leftVersion && rightVersion" class="material-basis">
          <span>基准材料版本</span>
          <el-tag
            v-for="item in leftVersion.sourceMaterialVersions"
            :key="`${item.categoryCode}-${item.materialId}`"
            effect="plain"
            size="small"
          >
            {{ categoryText(item.categoryCode) }} V{{ item.versionNo }}
          </el-tag>
          <b>→</b>
          <el-tag
            v-for="item in rightVersion.sourceMaterialVersions"
            :key="`${item.categoryCode}-${item.materialId}`"
            type="success"
            effect="plain"
            size="small"
          >
            {{ categoryText(item.categoryCode) }} V{{ item.versionNo }}
          </el-tag>
        </div>

        <el-tabs v-model="activeTab" class="version-tabs">
          <el-tab-pane label="30维画像变化" name="profile">
            <div class="change-summary">
              <span>评分变化 {{ changedProfileCount }} 个维度</span>
              <span>新增证据维度 {{ addedProfileCount }}</span>
              <span>失去证据维度 {{ removedProfileCount }}</span>
            </div>
            <el-table :data="profileRows" size="small" max-height="560" border>
              <el-table-column label="维度" prop="dimensionName" min-width="180" fixed="left" />
              <el-table-column label="基准分" width="100" align="center">
                <template #default="{ row }">{{ scoreText(row.before) }}</template>
              </el-table-column>
              <el-table-column label="目标分" width="100" align="center">
                <template #default="{ row }">{{ scoreText(row.after) }}</template>
              </el-table-column>
              <el-table-column label="变化" width="100" align="center">
                <template #default="{ row }">
                  <span :class="deltaClass(row.delta)">
                    {{ row.delta == null ? "—" : (row.delta > 0 ? "+" : "") + row.delta }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="状态变化" min-width="210">
                <template #default="{ row }">
                  {{ statusText(row.before?.dataStatus) }} → {{ statusText(row.after?.dataStatus) }}
                </template>
              </el-table-column>
              <el-table-column label="目标证据" min-width="320" show-overflow-tooltip>
                <template #default="{ row }">{{ row.after?.evidence || "证据不足" }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="知识图谱变化" name="graph">
            <div class="graph-change-grid">
              <el-card shadow="never">
                <template #header>节点变化 · 新增 {{ entityDiff.added.length }} / 删除 {{ entityDiff.removed.length }}</template>
                <el-table :data="entityDiff.rows" size="small" max-height="380">
                  <el-table-column label="变化" width="76">
                    <template #default="{ row }">
                      <el-tag :type="row.change === 'ADDED' ? 'success' : 'danger'" size="small">
                        {{ row.change === "ADDED" ? "新增" : "删除" }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="类型" prop="entityType" width="120" />
                  <el-table-column label="节点" prop="canonicalName" min-width="180" />
                </el-table>
              </el-card>
              <el-card shadow="never">
                <template #header>关系变化 · 新增 {{ relationDiff.added.length }} / 删除 {{ relationDiff.removed.length }}</template>
                <el-table :data="relationDiff.rows" size="small" max-height="380">
                  <el-table-column label="变化" width="76">
                    <template #default="{ row }">
                      <el-tag :type="row.change === 'ADDED' ? 'success' : 'danger'" size="small">
                        {{ row.change === "ADDED" ? "新增" : "删除" }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="关系" prop="label" min-width="280" show-overflow-tooltip />
                </el-table>
              </el-card>
            </div>
          </el-tab-pane>

          <el-tab-pane label="图谱快照" name="snapshot">
            <div class="snapshot-switch">
              <el-radio-group v-model="snapshotSide">
                <el-radio-button value="left">
                  V{{ leftVersion.versionNo }} · {{ snapshotCount(leftVersion) }} 个节点
                </el-radio-button>
                <el-radio-button value="right">
                  V{{ rightVersion.versionNo }} · {{ snapshotCount(rightVersion) }} 个节点
                </el-radio-button>
              </el-radio-group>
            </div>
            <KnowledgeGraphSvg
              v-if="snapshotGraph.entities?.length"
              :entities="snapshotGraph.entities"
              :relations="snapshotGraph.relations || []"
              :seed-entity-id="snapshotGraph.seedEntityId"
            />
            <el-empty v-else description="该版本尚未生成知识图谱快照" />
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </template>
  </section>
</template>

<script setup name="ExpertVersionHistory">
import {
  getAdminExpertProfileVersion,
  getAdminExpertProfileVersions,
  getExpertProfileVersion,
  getExpertProfileVersions
} from "@/api/expert/platform"
import KnowledgeGraphSvg from "@/views/expert/admin/experts/KnowledgeGraphSvg.vue"

const props = defineProps({
  mode: {
    type: String,
    default: "expert"
  },
  userId: {
    type: [Number, String],
    default: null
  }
})
const loading = ref(false)
const comparing = ref(false)
const versions = ref([])
const leftId = ref(null)
const rightId = ref(null)
const leftVersion = ref(null)
const rightVersion = ref(null)
const activeTab = ref("profile")
const snapshotSide = ref("right")
const detailCache = new Map()

const profileRows = computed(() => {
  const before = new Map((leftVersion.value?.profileSnapshot?.dimensions || [])
    .map(item => [item.dimensionCode, item]))
  const afterItems = rightVersion.value?.profileSnapshot?.dimensions || []
  const codes = new Set([...before.keys(), ...afterItems.map(item => item.dimensionCode)])
  return [...codes].map(code => {
    const beforeItem = before.get(code) || null
    const afterItem = afterItems.find(item => item.dimensionCode === code) || null
    const beforeScore = scored(beforeItem) ? Number(beforeItem.score) : null
    const afterScore = scored(afterItem) ? Number(afterItem.score) : null
    const delta = beforeScore == null || afterScore == null
      ? null : Math.round(afterScore - beforeScore)
    return {
      dimensionCode: code,
      dimensionName: afterItem?.dimensionName || beforeItem?.dimensionName || code,
      before: beforeItem,
      after: afterItem,
      delta
    }
  }).sort((left, right) => Math.abs(right.delta || 0) - Math.abs(left.delta || 0))
})
const changedProfileCount = computed(() =>
  profileRows.value.filter(item => item.delta != null && item.delta !== 0).length)
const addedProfileCount = computed(() =>
  profileRows.value.filter(item => !scored(item.before) && scored(item.after)).length)
const removedProfileCount = computed(() =>
  profileRows.value.filter(item => scored(item.before) && !scored(item.after)).length)
const entityDiff = computed(() =>
  diffByKey(
    leftVersion.value?.graphSnapshot?.entities || [],
    rightVersion.value?.graphSnapshot?.entities || [],
    item => `${item.entityType}|${item.externalKey || item.canonicalName}`,
    item => ({
      entityType: item.entityType,
      canonicalName: item.canonicalName
    })
  ))
const relationDiff = computed(() =>
  diffByKey(
    leftVersion.value?.graphSnapshot?.relations || [],
    rightVersion.value?.graphSnapshot?.relations || [],
    item => `${item.sourceEntityId}|${item.relationType}|${item.targetEntityId}`,
    item => ({
      label: `${item.sourceEntityName || item.sourceEntityId} → ${relationText(item.relationType)} → ${item.targetEntityName || item.targetEntityId}`
    })
  ))
const snapshotGraph = computed(() => {
  const source = snapshotSide.value === "left" ? leftVersion.value : rightVersion.value
  return source?.graphSnapshot || { entities: [], relations: [] }
})

function loadTimeline() {
  if (props.mode === "admin" && !props.userId) {
    versions.value = []
    return
  }
  loading.value = true
  const request = props.mode === "admin"
    ? getAdminExpertProfileVersions(props.userId)
    : getExpertProfileVersions()
  request
    .then(res => {
      versions.value = res.data || []
      detailCache.clear()
      if (versions.value.length) {
        rightId.value = versions.value[0].releaseId
        leftId.value = versions.value[1]?.releaseId || versions.value[0].releaseId
        return loadComparison()
      }
      leftVersion.value = null
      rightVersion.value = null
    })
    .finally(() => {
      loading.value = false
    })
}

function versionRequest(releaseId) {
  return props.mode === "admin"
    ? getAdminExpertProfileVersion(props.userId, releaseId)
    : getExpertProfileVersion(releaseId)
}

async function getVersion(releaseId) {
  if (detailCache.has(releaseId)) return detailCache.get(releaseId)
  const response = await versionRequest(releaseId)
  detailCache.set(releaseId, response.data)
  return response.data
}

async function loadComparison() {
  if (!leftId.value || !rightId.value) return
  comparing.value = true
  try {
    const [left, right] = await Promise.all([
      getVersion(leftId.value),
      getVersion(rightId.value)
    ])
    leftVersion.value = left
    rightVersion.value = right
  }
  finally {
    comparing.value = false
  }
}

function diffByKey(leftItems, rightItems, keyOf, viewOf) {
  const left = new Map(leftItems.map(item => [keyOf(item), item]))
  const right = new Map(rightItems.map(item => [keyOf(item), item]))
  const added = [...right.values()].filter(item => !left.has(keyOf(item)))
  const removed = [...left.values()].filter(item => !right.has(keyOf(item)))
  const rows = [
    ...added.map(item => ({ change: "ADDED", ...viewOf(item) })),
    ...removed.map(item => ({ change: "REMOVED", ...viewOf(item) }))
  ]
  return { added, removed, rows }
}

function scored(item) {
  return item && item.dataStatus !== "INSUFFICIENT_EVIDENCE"
}

function scoreText(item) {
  return scored(item) ? Math.round(Number(item.score || 0)) : "—"
}

function statusText(status) {
  return {
    AVAILABLE: "有证据",
    CONFLICT: "证据冲突",
    INSUFFICIENT_EVIDENCE: "证据不足"
  }[status] || "无"
}

function deltaClass(delta) {
  if (delta == null || delta === 0) return ""
  return delta > 0 ? "delta-up" : "delta-down"
}

function snapshotCount(version) {
  return version?.graphSnapshot?.entities?.length || 0
}

function categoryText(value) {
  return {
    IDENTITY: "身份证明",
    QUALIFICATION: "学历职称",
    CERTIFICATE: "职业资格",
    ACHIEVEMENT: "代表成果",
    SERVICE_EXPERIENCE: "服务经历",
    OTHER: "利益冲突说明"
  }[value] || value
}

function triggerText(value) {
  return {
    MATERIAL_APPROVED: "审核通过",
    ADMIN_MATERIAL_APPROVED: "管理员材料审核通过",
    CURRENT_STATE_BACKFILL: "当前状态补齐"
  }[value] || value
}

function relationText(value) {
  return {
    HAS_EXPERT: "行业专家",
    BELONGS_TO: "所属单位",
    HAS_CREDENTIAL: "具有资质",
    EXPERTISE_IN: "专业能力",
    SERVES_REGION: "服务区域",
    PARTICIPATED_IN: "参与项目",
    PRODUCED: "产出成果",
    USES_TECHNOLOGY: "使用技术"
  }[value] || value
}

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "-"
}

watch(() => [props.mode, props.userId], loadTimeline, { immediate: true })
</script>

<style scoped lang="scss">
.version-history {
  padding-top: 20px;
  margin-top: 18px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.version-heading {
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
.version-timeline {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 8px 20px;
  padding: 18px 4px 0;
}
.timeline-card {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-extra-light);
  strong {
    color: #2d495c;
  }
  span {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
}
.compare-card {
  margin-top: 12px;
}
.compare-toolbar {
  display: flex;
  align-items: flex-end;
  gap: 14px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.version-select {
  display: flex;
  min-width: 240px;
  flex-direction: column;
  gap: 7px;
  span {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
}
.compare-arrow {
  padding-bottom: 9px;
  color: #71818e;
  font-size: 22px;
}
.material-basis {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
  padding: 14px 0 2px;
  color: var(--el-text-color-secondary);
}
.version-tabs {
  margin-top: 8px;
}
.change-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 24px;
  padding: 0 0 12px;
  color: #5f7382;
}
.graph-change-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}
.snapshot-switch {
  margin-bottom: 14px;
}
.delta-up {
  color: #2f9e76;
  font-weight: 600;
}
.delta-down {
  color: #c75b5b;
  font-weight: 600;
}
@media (max-width: 900px) {
  .compare-toolbar,
  .version-heading {
    flex-direction: column;
    align-items: stretch;
  }
  .compare-arrow {
    display: none;
  }
  .graph-change-grid {
    grid-template-columns: 1fr;
  }
}
</style>
