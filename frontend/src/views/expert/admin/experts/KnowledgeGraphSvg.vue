<template>
  <div class="knowledge-graph-svg">
    <div class="graph-toolbar">
      <div class="graph-legend" aria-label="实体类型图例">
        <span v-for="item in legend" :key="item.type">
          <i :style="{ background: item.color }" />
          {{ item.label }}
        </span>
      </div>
      <div class="graph-structure" aria-hidden="true">
        <span>行业中心</span>
        <b>→</b>
        <span>专家</span>
        <b>→</b>
        <span>关键事实</span>
      </div>
    </div>

    <div class="graph-workbench">
      <div class="canvas-shell">
        <div class="canvas-controls" aria-label="图谱视图控制">
          <button type="button" title="放大" @click="zoomGraph(1.2)">＋</button>
          <span>{{ zoomPercent }}%</span>
          <button type="button" title="缩小" @click="zoomGraph(1 / 1.2)">－</button>
          <button type="button" title="重置布局" @click="resetGraphLayout">重置布局</button>
        </div>
        <div class="canvas-tip">悬停查看关系名称 · 点击节点或连线查看详情 · 滚轮缩放 · 拖拽空白处移动</div>
        <div ref="chartRef" class="graph-canvas" role="img" :aria-label="chartAriaLabel"></div>
      </div>

      <aside class="graph-detail" aria-live="polite">
        <template v-if="selectedNode">
          <span class="detail-eyebrow">{{ nodeTypeLabel(selectedNode.entityType) }}</span>
          <h4>{{ selectedNode.canonicalName }}</h4>
          <div class="detail-meta">
            <span>状态：{{ selectedNode.reviewStatus }}</span>
            <span>证据：{{ selectedNode.evidenceCount || 0 }} 条</span>
            <span>ID：{{ selectedNode.entityId }}</span>
          </div>
          <div v-if="nodePropertyEntries.length" class="detail-section">
            <strong>节点属性</strong>
            <dl>
              <template v-for="item in nodePropertyEntries" :key="item.key">
                <dt>{{ item.label }}</dt>
                <dd>{{ item.value }}</dd>
              </template>
            </dl>
          </div>
          <div class="detail-section">
            <strong>关联关系 · {{ relatedEdges.length }}</strong>
            <div v-if="relatedEdges.length" class="related-list">
              <button
                v-for="edge in relatedEdges"
                :key="edge.relationId"
                type="button"
                @click="selectRelation(edge)"
              >
                <b>{{ relationLabel(edge.relationType) }}</b>
                <span>{{ edge.sourceEntityName }} → {{ edge.targetEntityName }}</span>
                <small>{{ edge.evidence?.length || edge.evidenceCount || 0 }} 条证据</small>
              </button>
            </div>
            <p v-else class="detail-empty">暂无关联关系</p>
          </div>
        </template>

        <template v-else-if="selectedRelation">
          <span class="detail-eyebrow">关系详情</span>
          <h4>{{ relationLabel(selectedRelation.relationType) }}</h4>
          <div class="detail-meta">
            <span>{{ selectedRelation.sourceEntityName }}</span>
            <span>→</span>
            <span>{{ selectedRelation.targetEntityName }}</span>
            <span>状态：{{ selectedRelation.reviewStatus }}</span>
            <span>证据：{{ selectedRelation.evidence?.length || selectedRelation.evidenceCount || 0 }} 条</span>
          </div>
          <div v-if="selectedRelation.evidence?.length" class="detail-section">
            <strong>关系证据</strong>
            <div class="evidence-preview">
              <span
                v-for="item in selectedRelation.evidence.slice(0, 8)"
                :key="item.evidenceId"
              >
                {{ item.excerpt || item.sourceRef }} · {{ item.sourceType }}
              </span>
            </div>
          </div>
        </template>

        <div v-else class="detail-placeholder">
          <strong>交互说明</strong>
          <p>滚轮缩放，拖拽画布空白区域平移；直接拖动节点即可重新排布。</p>
          <p>全局图只突出行业和专家名称；其他事实节点悬停或点击即可查看。</p>
          <p>关系名称默认隐藏，鼠标悬停连线时显示，避免大量文字互相重叠。</p>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import * as echarts from "echarts"

const props = defineProps({
  entities: { type: Array, default: () => [] },
  relations: { type: Array, default: () => [] },
  seedEntityId: { type: [Number, String], default: null }
})

const chartRef = ref(null)
const selectedNode = ref(null)
const selectedRelation = ref(null)
const zoomPercent = ref(100)
let chart = null
let resizeObserver = null
let graphZoom = 1

const palette = {
  Industry: "#e96b78",
  Expert: "#3d8fe8",
  Organization: "#e7b23e",
  Credential: "#d99a2b",
  Capability: "#55b984",
  ServiceRegion: "#4db7c8",
  Project: "#dc7467",
  Achievement: "#e8bc45",
  Technology: "#61b4df",
  Task: "#ef5b4c"
}

const typeLabels = {
  Industry: "行业",
  Expert: "专家",
  Organization: "单位",
  Credential: "资质",
  Capability: "能力",
  ServiceRegion: "区域",
  Project: "项目",
  Achievement: "成果",
  Technology: "技术",
  Task: "任务"
}

const typeGlyphs = {
  Industry: "行",
  Expert: "专",
  Organization: "单",
  Credential: "资",
  Capability: "能",
  ServiceRegion: "域",
  Project: "项",
  Achievement: "果",
  Technology: "技",
  Task: "任"
}

const relationLabels = {
  HAS_EXPERT: "服务行业",
  BELONGS_TO: "所属单位",
  HAS_CREDENTIAL: "具备资质",
  EXPERTISE_IN: "专业能力",
  SERVES_REGION: "服务区域",
  PARTICIPATED_IN: "参与项目",
  PRODUCED: "产出成果",
  PROJECT_PRODUCED: "项目产出",
  USES_TECHNOLOGY: "使用技术",
  APPLIES_TO: "应用于",
  TARGETS_INDUSTRY: "目标行业",
  REQUIRES_CAPABILITY: "需求能力"
}

const propertyLabels = {
  userId: "用户ID",
  realName: "姓名",
  organization: "单位",
  professionalTitle: "职称",
  researchField: "专业方向",
  industryField: "产业领域",
  serviceRegion: "服务区域",
  materialId: "材料ID",
  extractionSource: "提取来源",
  categoryCode: "认证类型",
  reviewStatus: "审核状态",
  serviceStatus: "接单状态",
  graphVersion: "画像版本"
}

const typeOrder = [
  "Industry", "Expert", "Project", "Achievement", "Technology",
  "Capability", "ServiceRegion", "Organization", "Credential", "Task"
]

const legend = computed(() => {
  const types = [...new Set(props.entities.map(item => item.entityType))]
  return types.sort((left, right) => typeOrder.indexOf(left) - typeOrder.indexOf(right))
    .map(type => ({ type, color: colorFor(type), label: nodeTypeLabel(type) }))
})

const chartAriaLabel = computed(() =>
  `知识图谱，共${props.entities.length}个实体和${props.relations.length}条关系`)

const relatedEdges = computed(() => {
  if (!selectedNode.value)
  {
    return []
  }
  const selectedId = String(selectedNode.value.entityId)
  return props.relations.filter(edge =>
    String(edge.sourceEntityId) === selectedId
    || String(edge.targetEntityId) === selectedId)
})

const nodePropertyEntries = computed(() => {
  const properties = selectedNode.value?.properties
  if (!properties || typeof properties !== "object")
  {
    return []
  }
  return Object.entries(properties)
    .filter(([, value]) => value !== null && value !== undefined && value !== "")
    .slice(0, 12)
    .map(([key, value]) => ({
      key,
      label: propertyLabels[key] || key,
      value: typeof value === "object" ? JSON.stringify(value) : String(value)
    }))
})

watch(
  () => [props.entities, props.relations, props.seedEntityId],
  () => {
    selectedNode.value = null
    selectedRelation.value = null
    graphZoom = 1
    zoomPercent.value = 100
    nextTick(() => renderGraph(true))
  },
  { deep: true }
)

onMounted(() => {
  chart = echarts.init(chartRef.value)
  chart.on("click", handleChartClick)
  chart.on("graphRoam", params => {
    if (typeof params.zoom === "number")
    {
      graphZoom = Math.max(0.45, Math.min(2.8, graphZoom * params.zoom))
      zoomPercent.value = Math.round(graphZoom * 100)
    }
  })
  resizeObserver = new ResizeObserver(() => chart?.resize())
  resizeObserver.observe(chartRef.value)
  renderGraph(true)
})

onUnmounted(() => {
  resizeObserver?.disconnect()
  chart?.dispose()
  chart = null
})

function renderGraph(forceLayout = false) {
  if (!chart)
  {
    return
  }
  chart.setOption(buildOption(forceLayout), true)
}

function resetGraphLayout() {
  graphZoom = 1
  zoomPercent.value = 100
  selectedNode.value = null
  selectedRelation.value = null
  renderGraph(true)
}

function buildOption(forceLayout) {
  const css = getComputedStyle(chartRef.value)
  const textColor = css.getPropertyValue("--el-text-color-primary").trim() || "#1f2937"
  const secondaryColor = css.getPropertyValue("--el-text-color-secondary").trim() || "#64748b"
  const backgroundColor = css.getPropertyValue("--el-bg-color").trim() || "#ffffff"
  const borderColor = css.getPropertyValue("--el-border-color-lighter").trim() || "#e5e7eb"
  const centerEntity = chooseCenterEntity()
  const expertCount = props.entities.filter(item => item.entityType === "Expert").length
  const nodeCount = props.entities.length
  const showAllFactLabels = expertCount <= 1

  const nodes = props.entities.map(entity => {
    const center = String(entity.entityId) === String(centerEntity?.entityId)
    const expert = entity.entityType === "Expert"
    const showLabel = showAllFactLabels
      || expert
      || entity.entityType === "Industry"
    const size = showLabel
      ? nodeDimensions(entity, center, expert)
      : [20, 20]
    return {
      id: String(entity.entityId),
      name: entity.canonicalName,
      category: Math.max(0, typeOrder.indexOf(entity.entityType)),
      symbol: showLabel ? "roundRect" : "circle",
      symbolSize: size,
      fixed: forceLayout && entity.entityType === "Industry" && center,
      x: center ? 0 : undefined,
      y: center ? 0 : undefined,
      draggable: true,
      entity,
      itemStyle: {
        color: withAlpha(colorFor(entity.entityType), showLabel ? 0.18 : 0.78),
        borderColor: colorFor(entity.entityType),
        borderWidth: showLabel ? 2 : 1.2,
        borderRadius: 12
      },
      label: {
        show: showLabel,
        position: "inside",
        formatter: nodeCircleLabel(entity, center, expert),
        color: "#000000",
        fontSize: center ? 13 : expert ? 12 : 11,
        lineHeight: center ? 18 : 16,
        fontWeight: 600,
        align: "center",
        verticalAlign: "middle",
        overflow: "truncate",
        ellipsis: "…",
        width: Math.max(58, size[0] - 14)
      },
      emphasis: {
        focus: "adjacency",
        scale: 1.08,
        itemStyle: {
          color: withAlpha(colorFor(entity.entityType), 0.32),
          borderWidth: 2.5
        }
      }
    }
  })

  const edges = props.relations.map(relation => ({
    source: String(relation.sourceEntityId),
    target: String(relation.targetEntityId),
    relation,
    value: relationLabel(relation.relationType),
    lineStyle: {
      color: withAlpha(secondaryColor, 0.28),
      width: 1,
      curveness: 0.02,
      opacity: relation.reviewStatus === "REJECTED" ? 0.35 : 1
    },
    emphasis: {
      focus: "adjacency",
      lineStyle: {
        color: css.getPropertyValue("--el-color-primary").trim() || "#409eff",
        width: 2.6
      },
      label: { show: true }
    }
  }))

  return {
    animationDurationUpdate: 320,
    tooltip: {
      trigger: "item",
      confine: true,
      borderColor,
      backgroundColor,
      textStyle: { color: textColor, fontSize: 12 },
      formatter: tooltipFormatter
    },
    series: [
      {
        type: "graph",
        layout: forceLayout && nodeCount > 1 ? "force" : "none",
        roam: true,
        draggable: true,
        nodeScaleRatio: 1,
        zoom: graphZoom,
        scaleLimit: { min: 0.45, max: 2.8 },
        edgeSymbol: ["none", "none"],
        data: nodes,
        links: edges,
        categories: typeOrder.map(type => ({ name: nodeTypeLabel(type) })),
        edgeLabel: {
          show: false,
          formatter: params => params.value,
          color: "#000000",
          fontSize: 10,
          backgroundColor: withAlpha(backgroundColor, 0.88),
          borderRadius: 3,
          padding: [1, 3]
        },
        force: {
          initLayout: "circular",
          repulsion: Math.max(520, Math.min(1250, nodeCount * 34)),
          edgeLength: expertCount > 1 ? [105, 185] : [145, 235],
          gravity: expertCount > 1 ? 0.08 : 0.045,
          layoutAnimation: true,
          friction: 0.56
        },
        labelLayout: {
          hideOverlap: true
        },
        emphasis: {
          focus: "adjacency",
          lineStyle: { opacity: 1 }
        },
        selectedMode: "single"
      }
    ]
  }
}

function chooseCenterEntity() {
  const seed = props.seedEntityId == null ? "" : String(props.seedEntityId)
  const industries = props.entities.filter(item => item.entityType === "Industry")
  return industries.find(item => String(item.entityId) === seed)
    || industries[0]
    || props.entities.find(item => item.entityType === "Expert" && String(item.entityId) === seed)
    || props.entities.find(item => item.entityType === "Expert")
    || props.entities[0]
}

function handleChartClick(params) {
  if (params.dataType === "node")
  {
    selectedNode.value = params.data.entity
    selectedRelation.value = null
    chart.dispatchAction({ type: "focusNodeAdjacency", seriesIndex: 0, dataIndex: params.dataIndex })
    return
  }
  if (params.dataType === "edge")
  {
    selectedRelation.value = params.data.relation
    selectedNode.value = null
  }
}

function tooltipFormatter(params) {
  if (params.dataType === "node")
  {
    const entity = params.data.entity
    if (!entity)
    {
      return `<strong>${escapeHtml(params.name || "分组")}</strong>`
    }
    return [
      `<strong>${escapeHtml(entity.canonicalName)}</strong>`,
      `${escapeHtml(nodeTypeLabel(entity.entityType))} · ${escapeHtml(entity.reviewStatus || "")}`,
      `证据：${entity.evidenceCount || entity.evidence?.length || 0} 条`
    ].join("<br/>")
  }
  if (params.dataType === "edge")
  {
    const relation = params.data.relation
    if (!relation)
    {
      return ""
    }
    return [
      `<strong>${escapeHtml(relationLabel(relation.relationType))}</strong>`,
      `${escapeHtml(relation.sourceEntityName)} → ${escapeHtml(relation.targetEntityName)}`,
      `${escapeHtml(relation.reviewStatus || "")} · 证据 ${relation.evidenceCount || relation.evidence?.length || 0} 条`
    ].join("<br/>")
  }
  return ""
}

function zoomGraph(factor) {
  if (!chart)
  {
    return
  }
  chart.dispatchAction({
    type: "graphRoam",
    seriesIndex: 0,
    zoom: factor
  })
}

function selectRelation(relation) {
  selectedRelation.value = relation
  selectedNode.value = null
}

function nodeTypeLabel(type) {
  return typeLabels[type] || type
}

function relationLabel(type) {
  return relationLabels[type] || type
}

function colorFor(type) {
  return palette[type] || "#475569"
}

function truncateLabel(value, maxLength) {
  const text = String(value || "未命名")
  return text.length > maxLength ? `${text.slice(0, maxLength - 1)}…` : text
}

function nodeDimensions(entity, center, expert) {
  const label = nodeCircleLabel(entity, center, expert)
  const lines = label.split("\n")
  const textWidth = Math.max(...lines.map(measureLabelLine))
  const minWidth = center ? 124 : expert ? 112 : 76
  const maxWidth = center ? 164 : expert ? 148 : 136
  return [
    Math.max(minWidth, Math.min(maxWidth, textWidth + 24)),
    lines.length > 1 ? 50 : 38
  ]
}

function measureLabelLine(value) {
  return Array.from(String(value || "")).reduce((width, character) => {
    return width + (/[\u3400-\u9fff]/.test(character) ? 12 : 7)
  }, 0)
}

function nodeCircleLabel(entity, center, expert) {
  const text = String(entity.canonicalName || "未命名")
  const maxLength = center ? 8 : expert ? 7 : 6
  if (text.length <= maxLength)
  {
    return text
  }
  return `${text.slice(0, maxLength)}\n`
    + (text.length > maxLength * 2
      ? `${text.slice(maxLength, maxLength * 2 - 1)}…`
      : text.slice(maxLength))
}

function withAlpha(color, opacity) {
  if (String(color).startsWith("#") && String(color).length === 7)
  {
    return `${color}${Math.round(opacity * 255).toString(16).padStart(2, "0")}`
  }
  return color
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;")
}
</script>

<style scoped lang="scss">
.knowledge-graph-svg {
  display: grid;
  gap: 12px;
}
.graph-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px 20px;
}
.graph-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  span {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }
  i {
    width: 9px;
    height: 9px;
    border-radius: 50%;
  }
}
.graph-structure {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-secondary);
  font-size: 11px;
  white-space: nowrap;
  b {
    color: var(--el-color-primary);
    font-weight: 500;
  }
}
.graph-workbench {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 292px;
  gap: 14px;
  align-items: stretch;
}
.canvas-shell {
  position: relative;
  min-width: 0;
  height: 660px;
  min-height: 520px;
  overflow: hidden;
  border-radius: 14px;
  background:
    radial-gradient(circle at 1px 1px, rgba(120, 135, 150, .18) 1px, transparent 0);
  background-size: 24px 24px;
}
.graph-canvas {
  width: 100%;
  height: 100%;
  cursor: grab;
  touch-action: none;
  &:active {
    cursor: grabbing;
  }
}
.canvas-controls {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 3;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 9px;
  background: color-mix(in srgb, var(--el-bg-color) 92%, transparent);
  box-shadow: 0 4px 14px rgba(30, 45, 60, .1);
  button {
    min-width: 30px;
    height: 28px;
    padding: 0 8px;
    border: 0;
    border-radius: 6px;
    background: transparent;
    color: var(--el-text-color-primary);
    cursor: pointer;
    &:hover {
      background: var(--el-fill-color-light);
    }
  }
  span {
    min-width: 42px;
    color: var(--el-text-color-secondary);
    font-size: 11px;
    text-align: center;
  }
}
.canvas-tip {
  position: absolute;
  right: 12px;
  bottom: 10px;
  z-index: 3;
  padding: 4px 9px;
  border-radius: 6px;
  background: color-mix(in srgb, var(--el-bg-color) 88%, transparent);
  color: var(--el-text-color-secondary);
  font-size: 11px;
  pointer-events: none;
}
.graph-detail {
  min-height: 0;
  max-height: 660px;
  padding: 18px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: var(--el-fill-color-extra-light);
  h4 {
    margin: 7px 0 12px;
    color: var(--el-text-color-primary);
    font-size: 16px;
    line-height: 1.45;
  }
  p {
    margin: 8px 0 0;
    color: var(--el-text-color-secondary);
    font-size: 12px;
    line-height: 1.65;
  }
}
.detail-eyebrow {
  color: var(--el-color-primary);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: .08em;
}
.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 5px 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.detail-section {
  display: grid;
  gap: 8px;
  padding-top: 14px;
  margin-top: 14px;
  border-top: 1px solid var(--el-border-color-lighter);
  > strong {
    color: var(--el-text-color-primary);
    font-size: 12px;
  }
  dl {
    display: grid;
    grid-template-columns: 78px minmax(0, 1fr);
    gap: 6px 10px;
    margin: 0;
    font-size: 12px;
  }
  dt {
    color: var(--el-text-color-secondary);
  }
  dd {
    margin: 0;
    color: var(--el-text-color-primary);
    overflow-wrap: anywhere;
  }
}
.related-list {
  display: grid;
  gap: 7px;
  button {
    display: grid;
    gap: 3px;
    width: 100%;
    padding: 8px 9px;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 8px;
    background: var(--el-bg-color);
    color: var(--el-text-color-primary);
    text-align: left;
    cursor: pointer;
    &:hover {
      border-color: var(--el-color-primary-light-5);
      background: var(--el-color-primary-light-9);
    }
    b {
      font-size: 12px;
      font-weight: 500;
    }
    span,
    small {
      color: var(--el-text-color-secondary);
      font-size: 11px;
    }
  }
}
.detail-empty {
  color: var(--el-text-color-secondary);
}
.evidence-preview {
  display: grid;
  gap: 4px;
  span {
    padding: 7px 8px;
    border-radius: 7px;
    background: var(--el-bg-color);
    color: var(--el-text-color-secondary);
    font-size: 11px;
    line-height: 1.55;
  }
}
@media (max-width: 1100px) {
  .graph-workbench {
    grid-template-columns: 1fr;
  }
  .graph-detail {
    max-height: 300px;
  }
}
@media (max-width: 720px) {
  .graph-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
  .canvas-shell {
    height: 520px;
  }
  .canvas-tip {
    display: none;
  }
}
</style>
