<template>
  <div class="app-container expert-home" v-loading="loading">
    <div class="hero">
      <div>
        <span class="eyebrow">EXPERT WORKSPACE</span>
        <h2>{{ profile.realName || nickName }}，欢迎进入专家工作台</h2>
        <p>维护专家档案与认证材料，查看能力画像、认证进度和接单状态。</p>
      </div>
      <el-tag :type="reviewTagType" effect="dark" size="large">
        {{ reviewStatusText }}
      </el-tag>
    </div>

    <el-row :gutter="16" class="metric-row">
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="metric-card">
          <span>档案完成度</span>
          <strong>{{ completionPercentage }}%</strong>
          <el-progress :percentage="completionPercentage" :show-text="false" />
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="metric-card">
          <span>可服务时间段</span>
          <strong>{{ summary.scheduleCount || 0 }}</strong>
          <small>每周可安排时间</small>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="metric-card">
          <span>已上传资料</span>
          <strong>{{ summary.materialCount || 0 }}</strong>
          <small>包含历史版本</small>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="metric-card">
          <span>认证角度</span>
          <strong>{{ certification.passedRequired || 0 }}/{{ certification.requiredTotal || 0 }}</strong>
          <small>{{ certification.overallStatusText || "尚未开始认证" }}</small>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="15">
        <el-card shadow="never" class="ai-analysis-card">
          <template #header>
            <div class="ai-card-title">
              <div>
                <span class="ai-badge">PROFILE INSIGHT</span>
                <strong>专家能力画像分析</strong>
              </div>
              <el-tag :type="graphTagType">
                {{ graphStatusText }}
              </el-tag>
              <el-tag v-if="graph.profileKind === 'LEGACY_PROFILE'" type="warning" effect="plain">
                旧版评分画像
              </el-tag>
              <el-tag v-else-if="graph.profileKind === 'GRAPH_PROFILE'" type="success" effect="plain">
                知识图谱驱动
              </el-tag>
            </div>
          </template>

          <el-empty
            v-if="!validDimensions.length"
            :description="graph.message || '正式认证通过后，才可查看专家画像'"
          >
            <el-button type="primary" @click="go('/expert-center/materials')">完善认证材料</el-button>
          </el-empty>
          <template v-else>
            <div class="ai-overview">
              <div class="score-ring">
                <strong>{{ averageScore }}</strong>
                <span>综合画像分</span>
              </div>
              <div class="ai-summary">
                <h3>{{ analysisTitle }}</h3>
                <p>{{ analysisSummary }}</p>
                <div class="ai-facts">
                  <span>分析维度 <b>30</b></span>
                  <span>认证进度 <b>{{ certification.passedRequired || 0 }}/{{ certification.requiredTotal || 0 }}</b></span>
                  <span>画像置信度 <b>{{ averageConfidence }}%</b></span>
                  <span>冲突维度 <b>{{ conflictCount }}</b></span>
                </div>
              </div>
            </div>

            <el-row :gutter="16" class="insight-row">
              <el-col :xs="24" :sm="12">
                <div class="insight-panel strength-panel">
                  <span class="panel-label">主要能力优势</span>
                  <div v-for="item in strengths" :key="item.dimensionCode" class="insight-item">
                    <span>{{ item.dimensionName }}</span>
                    <el-progress :percentage="Number(item.score || 0)" :show-text="false" />
                    <b>{{ Math.round(Number(item.score || 0)) }}</b>
                  </div>
                </div>
              </el-col>
              <el-col :xs="24" :sm="12">
                <div class="insight-panel improvement-panel">
                  <span class="panel-label">建议重点提升</span>
                  <div v-for="item in improvements" :key="item.dimensionCode" class="insight-item">
                    <span>{{ item.dimensionName }}</span>
                    <el-progress :percentage="Number(item.score || 0)" :show-text="false" />
                    <b>{{ Math.round(Number(item.score || 0)) }}</b>
                  </div>
                </div>
              </el-col>
            </el-row>
            <div class="ai-card-footer">
              <span>分析依据：专家档案、认证材料、审核结果与30维能力标签</span>
              <el-button type="primary" plain @click="go('/expert-center/graph')">查看完整30维画像</el-button>
            </div>
          </template>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="9">
        <el-card shadow="never" header="快捷操作">
          <div class="quick-actions">
            <el-button type="primary" plain icon="User" @click="go('/expert-center/profile')">完善档案与时间表</el-button>
            <el-button type="success" plain icon="Upload" @click="go('/expert-center/materials')">上传证明材料</el-button>
            <el-button
              v-if="graph.status !== 'UNAVAILABLE'"
              type="warning"
              plain
              icon="DataAnalysis"
              @click="go('/expert-center/graph')"
            >
              查看能力画像
            </el-button>
            <el-button v-else plain disabled icon="Lock">正式认证后查看画像</el-button>
            <el-button plain icon="Switch" @click="go('/expert-center/status')">管理接单状态</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="ExpertWorkspace">
import { getExpertGraph, getExpertSummary } from "@/api/expert/platform"
import useUserStore from "@/store/modules/user"

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const summary = ref({})
const graph = ref({ dimensions: [] })
const nickName = computed(() => userStore.nickName || userStore.name)
const profile = computed(() => summary.value.profile || {})
const completionPercentage = computed(() =>
  Math.min(100, Number(profile.value.profileCompletion || 0))
)
const certification = computed(() => summary.value.certification || {})
const graphTagType = computed(() => ({
  APPROVED: "success",
  PARTIAL: "warning",
  PENDING: "info",
  UNAVAILABLE: "info"
})[graph.value.status] || "info")
const graphStatusText = computed(() => ({
  APPROVED: `正式画像 · V${graph.value.graphVersion || 0}`,
  PARTIAL: "部分能力画像",
  PENDING: "画像待生成",
  UNAVAILABLE: "画像未开放"
})[graph.value.status] || "画像未开放")
const validDimensions = computed(() =>
  (graph.value.dimensions || []).filter(item => item.dataStatus !== "INSUFFICIENT_EVIDENCE")
)
const conflictCount = computed(() =>
  validDimensions.value.filter(item => item.dataStatus === "CONFLICT").length
)
const averageScore = computed(() => {
  if (!validDimensions.value.length) return 0
  return Math.round(validDimensions.value.reduce((sum, item) => sum + Number(item.score || 0), 0)
    / validDimensions.value.length)
})
const averageConfidence = computed(() => {
  if (!validDimensions.value.length) return 0
  return Math.round(validDimensions.value.reduce((sum, item) => sum + Number(item.confidence || 0), 0)
    * 100 / validDimensions.value.length)
})
const strengths = computed(() =>
  [...validDimensions.value].sort((a, b) => Number(b.score || 0) - Number(a.score || 0)).slice(0, 3)
)
const improvements = computed(() =>
  [...validDimensions.value].sort((a, b) => Number(a.score || 0) - Number(b.score || 0)).slice(0, 3)
)
const analysisTitle = computed(() => {
  if (averageScore.value >= 80) return "能力结构均衡，核心优势突出"
  if (averageScore.value >= 60) return "能力基础良好，仍具备提升空间"
  return "画像信息仍在完善，建议补充认证材料"
})
const analysisSummary = computed(() => {
  const strengthText = strengths.value.map(item => item.dimensionName).join("、")
  const improvementText = improvements.value.map(item => item.dimensionName).join("、")
  return `当前能力画像综合评分为${averageScore.value}分。优势集中在${strengthText || "待分析"}；`
    + `建议优先补充${improvementText || "待分析"}相关材料与服务经历，以提高画像证据完整度。`
})

const reviewStatusText = computed(() => ({
  DRAFT: "档案草稿",
  PENDING_REVIEW: "审核中",
  RETURNED: "待补正",
  REJECTED: "未通过",
  APPROVED: "已入库"
})[profile.value.reviewStatus] || "档案草稿")

const reviewTagType = computed(() => ({
  DRAFT: "info",
  PENDING_REVIEW: "warning",
  RETURNED: "danger",
  REJECTED: "danger",
  APPROVED: "success"
})[profile.value.reviewStatus] || "info")

function go(path) {
  router.push(path)
}

function loadData() {
  loading.value = true
  Promise.all([getExpertSummary(), getExpertGraph()])
    .then(([summaryRes, graphRes]) => {
      summary.value = summaryRes.data || {}
      graph.value = graphRes.data || { dimensions: [] }
    })
    .finally(() => {
      loading.value = false
    })
}

loadData()
</script>

<style scoped lang="scss">
.expert-home {
  .hero {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 24px;
    padding: 28px 30px;
    margin-bottom: 18px;
    border-radius: 12px;
    color: #fff;
    background: linear-gradient(125deg, #123a63 0%, #176b87 65%, #3b9c9c 100%);
  }
  .hero h2 {
    margin: 6px 0 10px;
    font-size: 25px;
  }
  .hero p {
    margin: 0;
    opacity: .82;
    line-height: 1.8;
  }
  .eyebrow {
    font-size: 12px;
    letter-spacing: 2px;
    opacity: .7;
  }
  .metric-row {
    margin-bottom: 2px;
  }
  .metric-card {
    margin-bottom: 16px;
    :deep(.el-card__body) {
      min-height: 126px;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    span, small {
      color: #7b8794;
    }
    strong {
      font-size: 30px;
      color: #17324d;
    }
  }
  .ai-analysis-card {
    border: 0;
    background: linear-gradient(145deg, #f8fbfd, #eef7f3);
  }
  .ai-card-title {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 16px;
    > div {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    strong {
      color: #17324d;
      font-size: 17px;
    }
  }
  .ai-badge {
    padding: 4px 8px;
    border-radius: 4px;
    color: #176b87;
    font-size: 11px;
    letter-spacing: 1px;
    background: #dff2f5;
  }
  .ai-overview {
    display: flex;
    align-items: center;
    gap: 28px;
    padding: 16px 4px 22px;
  }
  .score-ring {
    display: flex;
    flex: 0 0 126px;
    height: 126px;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    border: 8px solid #cce8dd;
    border-top-color: #319479;
    border-radius: 50%;
    background: #fff;
    strong {
      color: #1f6d5b;
      font-size: 36px;
    }
    span {
      color: #7b8794;
      font-size: 12px;
    }
  }
  .ai-summary {
    flex: 1;
    h3 {
      margin: 0 0 10px;
      color: #17324d;
    }
    p {
      margin: 0;
      color: #60707d;
      line-height: 1.85;
    }
  }
  .ai-facts {
    display: flex;
    flex-wrap: wrap;
    gap: 18px;
    margin-top: 14px;
    color: #81909b;
    font-size: 13px;
    b {
      color: #24586c;
    }
  }
  .insight-row {
    margin-bottom: 18px;
  }
  .insight-panel {
    padding: 16px;
    border-radius: 10px;
    background: rgba(255, 255, 255, .88);
  }
  .panel-label {
    display: block;
    margin-bottom: 12px;
    color: #405666;
    font-weight: 600;
  }
  .insight-item {
    display: grid;
    grid-template-columns: 100px 1fr 28px;
    align-items: center;
    gap: 10px;
    margin: 10px 0;
    span {
      color: #61717e;
      font-size: 13px;
    }
    b {
      color: #294d61;
      text-align: right;
    }
  }
  .ai-card-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 16px;
    padding-top: 12px;
    border-top: 1px solid #dce8e3;
    color: #7b8794;
    font-size: 12px;
  }
  .quick-actions {
    display: grid;
    grid-template-columns: 1fr;
    gap: 10px;
    .el-button {
      width: 100%;
      height: 44px;
      margin-left: 0;
    }
  }
}
</style>
