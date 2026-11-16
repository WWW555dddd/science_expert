<template>
  <div class="knowledge-graph-svg">
    <div class="graph-legend" aria-label="实体类型图例">
      <span v-for="item in legend" :key="item.type">
        <i :style="{ background: item.color }" />
        {{ item.type }}
      </span>
    </div>

    <div class="svg-scroll">
      <svg
        :viewBox="`0 0 ${layout.width} ${layout.height}`"
        role="img"
        aria-labelledby="kg-svg-title kg-svg-desc"
      >
        <title id="kg-svg-title">专家知识图谱</title>
        <desc id="kg-svg-desc">
          展示专家与单位、资格、能力、产业、认证及材料之间的关系，共
          {{ entities.length }} 个实体和 {{ relations.length }} 条关系。
        </desc>
        <defs>
          <marker
            id="kg-arrow"
            viewBox="0 0 10 10"
            refX="9"
            refY="5"
            markerWidth="7"
            markerHeight="7"
            orient="auto-start-reverse"
          >
            <path d="M 0 0 L 10 5 L 0 10 z" fill="#8493a5" />
          </marker>
        </defs>

        <g class="kg-edges">
          <g
            v-for="edge in layout.edges"
            :key="edge.relationId"
            class="kg-edge"
            :class="{
              selected: selectedRelation?.relationId === edge.relationId,
              pending: edge.reviewStatus === 'PENDING',
              rejected: edge.reviewStatus === 'REJECTED'
            }"
            @click="selectRelation(edge)"
          >
            <title>
              {{ edge.sourceEntityName }} → {{ edge.relationType }} → {{ edge.targetEntityName }}
            </title>
            <path
              :d="edge.path"
              fill="none"
              stroke="#8493a5"
              stroke-width="1.8"
              marker-end="url(#kg-arrow)"
            />
            <text :x="edge.labelX" :y="edge.labelY" text-anchor="middle">
              {{ edge.relationType }}
            </text>
          </g>
        </g>

        <g class="kg-nodes">
          <g
            v-for="node in layout.nodes"
            :key="node.entityId"
            class="kg-node"
            :class="{
              selected: selectedNode?.entityId === node.entityId,
              pending: node.reviewStatus === 'PENDING',
              rejected: node.reviewStatus === 'REJECTED'
            }"
            role="button"
            tabindex="0"
            :aria-label="`${node.entityType} ${node.canonicalName}`"
            @click="selectNode(node)"
            @keydown.enter="selectNode(node)"
            @keydown.space.prevent="selectNode(node)"
          >
            <title>{{ node.entityType }} {{ node.canonicalName }}</title>
            <rect
              :x="node.x"
              :y="node.y"
              :width="layout.nodeWidth"
              :height="layout.nodeHeight"
              rx="12"
              :fill="colorFor(node.entityType)"
              fill-opacity="0.12"
              :stroke="colorFor(node.entityType)"
              stroke-width="1.5"
            />
            <rect
              :x="node.x"
              :y="node.y"
              width="5"
              :height="layout.nodeHeight"
              rx="2.5"
              :fill="colorFor(node.entityType)"
            />
            <text
              :x="node.x + 16"
              :y="node.y + 21"
              class="node-name"
              :title="node.canonicalName"
            >
              {{ truncate(node.canonicalName, 18) }}
            </text>
            <text :x="node.x + 16" :y="node.y + 40" class="node-type">
              {{ node.entityType }} · {{ node.evidenceCount || 0 }} 条证据
            </text>
          </g>
        </g>
      </svg>
    </div>

    <div v-if="selectedNode || selectedRelation" class="graph-selection" aria-live="polite">
      <template v-if="selectedNode">
        <strong>{{ selectedNode.canonicalName }}</strong>
        <span>{{ selectedNode.entityType }} · {{ selectedNode.reviewStatus }}</span>
      </template>
      <template v-else>
        <div class="selection-summary">
          <strong>{{ selectedRelation.relationType }}</strong>
          <span>
            {{ selectedRelation.sourceEntityName }} → {{ selectedRelation.targetEntityName }}
            · 证据 {{ selectedRelation.evidence?.length || 0 }} 条
          </span>
        </div>
        <div v-if="selectedRelation.evidence?.length" class="evidence-preview">
          <span v-for="item in selectedRelation.evidence.slice(0, 2)" :key="item.evidenceId">
            {{ item.excerpt || item.sourceRef }} · {{ item.sourceType }}
          </span>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  entities: { type: Array, default: () => [] },
  relations: { type: Array, default: () => [] },
  seedEntityId: { type: [Number, String], default: null }
})

const selectedNode = ref(null)
const selectedRelation = ref(null)
const nodeWidth = 190
const nodeHeight = 58
const horizontalGap = 290
const verticalGap = 76

const palette = {
  Expert: "#2563eb",
  Organization: "#7c3aed",
  Credential: "#d97706",
  Capability: "#059669",
  CapabilityAssessment: "#0f766e",
  Industry: "#db2777",
  ServiceRegion: "#0891b2",
  Certification: "#9333ea",
  Material: "#64748b",
  Project: "#ea580c",
  Achievement: "#ca8a04",
  Technology: "#0284c7",
  Task: "#dc2626"
}

const legend = computed(() => {
  const types = [...new Set(props.entities.map(item => item.entityType))]
  return types.sort().map(type => ({ type, color: colorFor(type) }))
})

const layout = computed(() => buildLayout(props.entities, props.relations, props.seedEntityId))

function buildLayout(entities, relations, seedEntityId) {
  const nodeMap = new Map(entities.map(item => [String(item.entityId), item]))
  const adjacency = new Map()
  relations.forEach(relation => {
    const source = String(relation.sourceEntityId)
    const target = String(relation.targetEntityId)
    if (!adjacency.has(source)) adjacency.set(source, [])
    if (!adjacency.has(target)) adjacency.set(target, [])
    adjacency.get(source).push(target)
    adjacency.get(target).push(source)
  })

  const seed = String(seedEntityId
    || entities.find(item => item.entityType === "Expert")?.entityId
    || entities[0]?.entityId
    || "")
  const levels = new Map()
  const visited = new Set()
  const queue = seed ? [{ id: seed, level: 0 }] : []
  while (queue.length)
  {
    const current = queue.shift()
    if (visited.has(current.id)) continue
    visited.add(current.id)
    if (!levels.has(current.level)) levels.set(current.level, [])
    const entity = nodeMap.get(current.id)
    if (entity) levels.get(current.level).push(entity)
    ;(adjacency.get(current.id) || []).forEach(next => {
      if (!visited.has(next)) queue.push({ id: next, level: current.level + 1 })
    })
  }

  entities.forEach(entity => {
    const id = String(entity.entityId)
    if (!visited.has(id))
    {
      const fallbackLevel = entity.entityType === "Expert" ? 0 : 1
      if (!levels.has(fallbackLevel)) levels.set(fallbackLevel, [])
      levels.get(fallbackLevel).push(entity)
    }
  })

  const maxLevelSize = Math.max(
    1,
    ...[...levels.values()].map(items => items.length)
  )
  const width = Math.max(900, levels.size * horizontalGap + nodeWidth + 90)
  const height = Math.max(470, maxLevelSize * verticalGap + 90)
  const positions = new Map()
  const nodes = []

  ;[...levels.keys()].sort((a, b) => a - b).forEach(level => {
    const items = levels.get(level)
    const totalHeight = (items.length - 1) * verticalGap
    const startY = (height - totalHeight - nodeHeight) / 2
    items.forEach((entity, index) => {
      const node = {
        ...entity,
        x: 45 + level * horizontalGap,
        y: startY + index * verticalGap
      }
      positions.set(String(entity.entityId), node)
      nodes.push(node)
    })
  })

  const evidenceCounts = new Map()
  entities.forEach(entity => {
    const id = String(entity.entityId)
    evidenceCounts.set(id, entity.evidence?.length || 0)
  })
  relations.forEach(relation => {
    const source = String(relation.sourceEntityId)
    const target = String(relation.targetEntityId)
    const count = relation.evidence?.length || 0
    evidenceCounts.set(source, (evidenceCounts.get(source) || 0) + count)
    evidenceCounts.set(target, (evidenceCounts.get(target) || 0) + count)
  })
  nodes.forEach(node => {
    node.evidenceCount = evidenceCounts.get(String(node.entityId)) || 0
  })

  const edges = relations.map(relation => {
    const source = positions.get(String(relation.sourceEntityId))
    const target = positions.get(String(relation.targetEntityId))
    if (!source || !target) return null
    const forward = target.x >= source.x
    const x1 = forward ? source.x + nodeWidth : source.x
    const x2 = forward ? target.x : target.x + nodeWidth
    const y1 = source.y + nodeHeight / 2
    const y2 = target.y + nodeHeight / 2
    const bend = Math.max(50, Math.abs(x2 - x1) / 2)
    const direction = forward ? 1 : -1
    return {
      ...relation,
      path: `M ${x1} ${y1} C ${x1 + direction * bend} ${y1},
             ${x2 - direction * bend} ${y2}, ${x2} ${y2}`,
      labelX: (x1 + x2) / 2,
      labelY: (y1 + y2) / 2 - 7
    }
  }).filter(Boolean)

  return { width, height, nodeWidth, nodeHeight, nodes, edges }
}

function colorFor(type) {
  return palette[type] || "#475569"
}

function truncate(value, maxLength) {
  const text = String(value || "")
  return text.length > maxLength ? `${text.slice(0, maxLength - 1)}…` : text
}

function selectNode(node) {
  selectedNode.value = node
  selectedRelation.value = null
}

function selectRelation(relation) {
  selectedRelation.value = relation
  selectedNode.value = null
}
</script>

<style scoped lang="scss">
.knowledge-graph-svg {
  display: grid;
  gap: 12px;
}
.graph-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
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
.svg-scroll {
  overflow-x: auto;
  border-radius: 12px;
  background:
    radial-gradient(circle at 1px 1px, rgba(120, 135, 150, .2) 1px, transparent 0);
  background-size: 24px 24px;
}
svg {
  display: block;
  width: 100%;
  min-width: 900px;
  height: auto;
}
.kg-edge {
  cursor: pointer;
  path {
    transition: stroke .18s ease, stroke-width .18s ease;
  }
  text {
    fill: var(--el-text-color-secondary);
    font-size: 11px;
    paint-order: stroke;
    stroke: var(--el-bg-color);
    stroke-width: 5px;
    stroke-linejoin: round;
  }
  &.selected path {
    stroke: var(--el-color-primary);
    stroke-width: 3px;
  }
  &.pending path {
    stroke: var(--el-color-warning);
    stroke-dasharray: 6 4;
  }
  &.rejected path {
    stroke: var(--el-color-danger);
    opacity: .55;
  }
}
.kg-node {
  cursor: pointer;
  outline: none;
  rect {
    transition: filter .18s ease, stroke-width .18s ease;
  }
  &:hover rect,
  &:focus rect {
    stroke-width: 2.5px;
    filter: drop-shadow(0 3px 5px rgba(30, 45, 60, .18));
  }
  &.selected rect {
    stroke-width: 3px;
    filter: drop-shadow(0 3px 6px rgba(30, 45, 60, .25));
  }
  &.pending rect {
    stroke-dasharray: 6 4;
  }
  &.rejected {
    opacity: .55;
  }
  .node-name {
    fill: var(--el-text-color-primary);
    font-size: 13px;
    font-weight: 500;
  }
  .node-type {
    fill: var(--el-text-color-secondary);
    font-size: 11px;
  }
}
.graph-selection {
  display: grid;
  gap: 6px;
  min-height: 34px;
  padding: 6px 10px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  span {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
}
.selection-summary {
  display: flex;
  align-items: center;
  gap: 10px;
}
.evidence-preview {
  display: grid;
  gap: 4px;
  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>
