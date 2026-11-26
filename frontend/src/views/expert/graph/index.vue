<template>
  <div class="app-container" v-loading="loading">
    <el-card shadow="never" class="graph-header">
      <div>
        <span class="eyebrow">PROFILE SNAPSHOT</span>
        <h2>专家能力画像</h2>
        <p>系统基于已审核认证材料独立派生30维画像。每个分数均可回溯到材料证据和画像规则版本。</p>
      </div>
      <div class="graph-meta">
        <el-tag :type="graphTagType" effect="dark">
          {{ graphStatusText }}
        </el-tag>
        <el-tag v-if="profileKindLabel" type="warning" effect="plain">{{ profileKindLabel }}</el-tag>
        <strong v-if="graph.status === 'APPROVED'">V{{ graph.graphVersion }}</strong>
        <span>证据覆盖 {{ graph.coverage || 0 }}%</span>
        <span>平均能力分 {{ averageScore }}</span>
      </div>
    </el-card>

    <el-empty
      v-if="!dimensions.length"
      :description="graph.message || '正式认证通过并生成正式画像后，才可查看能力画像'"
    />
    <div v-else class="dimension-groups">
      <el-card v-for="group in groups" :key="group.groupCode" shadow="never" class="dimension-card">
        <template #header>
          <div class="group-title">
            <span>{{ group.groupName }}</span>
            <strong>{{ group.average }}</strong>
          </div>
        </template>
        <div v-for="dimension in group.items" :key="dimension.dimensionCode" class="dimension-row">
          <div class="dimension-label">
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
            :color="scoreColor(dimension.score)"
          />
          <small v-if="dimension.dataStatus !== 'INSUFFICIENT_EVIDENCE'">
            置信度 {{ Math.round(Number(dimension.confidence || 0) * 100) }}%
            · {{ dimension.sourceCount || 0 }} 份材料证据
          </small>
          <small v-else>不生成占位分</small>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup name="ExpertGraph">
import { getExpertGraph } from "@/api/expert/platform"

const loading = ref(false)
const graph = ref({ dimensions: [] })
const dimensions = computed(() => graph.value.dimensions || [])
const graphTagType = computed(() => ({
  APPROVED: "success",
  PARTIAL: "warning",
  PENDING: "info",
  UNAVAILABLE: "info"
})[graph.value.status] || "info")
const graphStatusText = computed(() => ({
  APPROVED: "正式能力画像",
  PARTIAL: "部分能力画像",
  PENDING: "画像待生成",
  UNAVAILABLE: "暂无画像"
})[graph.value.status] || "暂无画像")
const profileKindLabel = computed(() => ({
  LEGACY_PROFILE: "旧版评分画像",
  EVIDENCE_SCORE: "独立30维画像",
  GRAPH_PROFILE: "历史知识图谱画像",
  MIXED: "混合来源画像"
})[graph.value.profileKind] || "")
const averageScore = computed(() => {
  const scored = dimensions.value.filter(item =>
    item.dataStatus === "AVAILABLE" || item.dataStatus === "CONFLICT"
  )
  if (!scored.length) return 0
  const total = scored.reduce((sum, item) => sum + Number(item.score || 0), 0)
  return Math.round(total / scored.length)
})
const groups = computed(() => {
  const result = new Map()
  dimensions.value.forEach(item => {
    if (!result.has(item.groupCode)) {
      result.set(item.groupCode, {
        groupCode: item.groupCode,
        groupName: item.groupName,
        items: []
      })
    }
    result.get(item.groupCode).items.push(item)
  })
  return [...result.values()].map(group => ({
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

function scoreColor(score) {
  if (Number(score) >= 80) return "#2f9e76"
  if (Number(score) >= 60) return "#3c82c4"
  if (Number(score) >= 40) return "#d89c35"
  return "#c75b5b"
}

function dimensionTooltip(dimension) {
  const definition = dimension.description
    ? `维度说明：${dimension.description}\n`
    : ""
  if (dimension.evidenceItems?.length) {
    return definition + dimension.evidenceItems
      .slice(0, 3)
      .map(item => `材料#${item.materialId}：${item.evidence}`)
      .join("\n")
  }
  return definition + (dimension.evidence || "暂无可追溯证据")
}

function loadGraph() {
  loading.value = true
  getExpertGraph()
    .then(res => {
      graph.value = res.data || { dimensions: [] }
    })
    .finally(() => {
      loading.value = false
    })
}

loadGraph()
</script>

<style scoped lang="scss">
.graph-header {
  margin-bottom: 16px;
  :deep(.el-card__body) {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 24px;
    padding: 28px;
    background: linear-gradient(120deg, #f3f8fc, #edf7f2);
  }
  h2 {
    margin: 6px 0 8px;
    color: #17324d;
  }
  p {
    max-width: 720px;
    margin: 0;
    color: #6e7c88;
    line-height: 1.8;
  }
  .eyebrow {
    color: #4a7c9e;
    font-size: 12px;
    letter-spacing: 2px;
  }
}
.graph-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  strong {
    color: #17324d;
    font-size: 28px;
  }
  span {
    color: #6e7c88;
  }
}
.dimension-groups {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.group-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  color: #17324d;
  strong {
    font-size: 22px;
  }
}
.dimension-row {
  display: grid;
  grid-template-columns: minmax(130px, 1fr) minmax(150px, 2fr) 92px;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  small {
    color: #8a97a3;
    text-align: right;
  }
}
.dimension-label {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #465665;
}
@media (max-width: 900px) {
  .dimension-groups {
    grid-template-columns: 1fr;
  }
}
</style>
