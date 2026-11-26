<template>
  <div class="app-container">
    <el-form :inline="true" :model="queryParams" class="search-form">
      <el-form-item label="专家姓名">
        <el-input v-model="queryParams.realName" clearable placeholder="请输入姓名" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="专业方向">
        <el-input v-model="queryParams.researchField" clearable placeholder="请输入专业方向" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="审核状态">
        <el-select v-model="queryParams.reviewStatus" clearable placeholder="全部状态" style="width: 150px">
          <el-option label="待审核" value="PENDING_REVIEW" />
          <el-option label="审核中" value="REVIEWING" />
          <el-option label="退回补正" value="RETURNED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-card shadow="never">
      <el-table :data="queue" v-loading="loading">
        <el-table-column label="专家" width="130">
          <template #default="{ row }">
            <strong>{{ row.realName }}</strong>
            <div class="sub-text">{{ row.userName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="单位/职称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.organization || "未填写单位" }}</div>
            <div class="sub-text">{{ row.professionalTitle || "未填写职称" }}</div>
          </template>
        </el-table-column>
        <el-table-column label="专业/产业" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.researchField || "未填写专业方向" }}</div>
            <div class="sub-text">{{ row.industryField || "未填写产业领域" }}</div>
          </template>
        </el-table-column>
        <el-table-column label="初审状态" width="130">
          <template #default="{ row }">
            <el-tag :type="row.jobStatus === 'FAILED' ? 'danger' : row.issueCount ? 'warning' : 'success'">
              {{ row.jobStatus === "FAILED" ? "处理失败" : row.issueCount ? `${row.issueCount}个问题` : "未发现缺项" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="认证进度" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.certification?.fullyCertified ? 'success' : 'warning'">
              {{ row.certification?.passedRequired || 0 }}/{{ row.certification?.requiredTotal || 0 }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="120">
          <template #default="{ row }">
            <el-tag :type="reviewTag(row.materialReviewStatus || row.reviewStatus)">
              {{ reviewText(row.materialReviewStatus || row.reviewStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" prop="latestSubmitTime" width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" icon="View" @click="openDetail(row.userId)">审核</el-button>
            <el-button
              v-if="row.jobStatus === 'FAILED' && row.materialId"
              link
              type="warning"
              @click="retry(row.materialId)"
            >重试</el-button>
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

    <el-drawer v-model="detailOpen" title="专家审核详情" size="88%" destroy-on-close>
      <div v-loading="detailLoading" class="review-detail">
        <el-row :gutter="16">
          <el-col :xs="24" :lg="16">
            <el-card shadow="never" header="专家档案">
              <el-descriptions :column="3" border>
                <el-descriptions-item label="姓名">{{ detail.profile?.realName }}</el-descriptions-item>
                <el-descriptions-item label="账号">{{ detail.profile?.userName }}</el-descriptions-item>
                <el-descriptions-item label="档案完成度">{{ detail.profile?.profileCompletion || 0 }}%</el-descriptions-item>
                <el-descriptions-item label="所属单位" :span="2">{{ detail.profile?.organization || "未填写" }}</el-descriptions-item>
                <el-descriptions-item label="职称">{{ detail.profile?.professionalTitle || "未填写" }}</el-descriptions-item>
                <el-descriptions-item label="专业方向">{{ detail.profile?.researchField || "未填写" }}</el-descriptions-item>
                <el-descriptions-item label="产业领域">{{ detail.profile?.industryField || "未填写" }}</el-descriptions-item>
                <el-descriptions-item label="服务区域">{{ detail.profile?.serviceRegion || "未填写" }}</el-descriptions-item>
                <el-descriptions-item label="联系电话">{{ detail.profile?.phone || "未填写" }}</el-descriptions-item>
                <el-descriptions-item label="电子邮箱">{{ detail.profile?.email || "未填写" }}</el-descriptions-item>
                <el-descriptions-item label="专家简介" :span="3">{{ detail.profile?.careerSummary || "未填写" }}</el-descriptions-item>
              </el-descriptions>
            </el-card>

            <el-card shadow="never" header="认证角度进度" class="detail-block">
              <div class="cert-summary">
                <strong>{{ detail.certification?.overallStatusText }}</strong>
                <span>必选角度通过 {{ detail.certification?.passedRequired || 0 }}/{{ detail.certification?.requiredTotal || 0 }}</span>
              </div>
              <div class="cert-angle-list">
                <div v-for="angle in detail.certification?.angles || []" :key="angle.categoryCode" class="cert-angle">
                  <span>{{ angle.angleName }}</span>
                  <el-tag :type="angleTagType(angle.certificationStatus)" size="small">
                    {{ angle.certificationStatusText }}
                  </el-tag>
                  <small v-if="angle.materialId">V{{ angle.versionNo }}</small>
                </div>
              </div>
            </el-card>

            <el-card shadow="never" header="认证材料审核" class="detail-block">
              <el-table
                :data="detail.materials || []"
                border
                highlight-current-row
                :current-row-key="selectedMaterialId"
                row-key="materialId"
                @row-click="selectMaterial"
              >
                <el-table-column label="认证角度" width="120">
                  <template #default="{ row }">{{ categoryText(row.materialCategory) }}</template>
                </el-table-column>
                <el-table-column label="文件" prop="fileName" min-width="180" show-overflow-tooltip />
                <el-table-column label="文件数" width="80" align="center">
                  <template #default="{ row }">{{ row.fileCount || 1 }}</template>
                </el-table-column>
                <el-table-column label="审核有效期" width="210" align="center">
                  <template #default="{ row }">
                    {{ validityRemainingText(row) }}
                  </template>
                </el-table-column>
                <el-table-column label="版本" width="70">
                  <template #default="{ row }">V{{ row.versionNo }}</template>
                </el-table-column>
                <el-table-column label="初审" width="120">
                  <template #default="{ row }">{{ jobText(row.jobStatus) }}</template>
                </el-table-column>
                <el-table-column label="审核结果" width="110">
                  <template #default="{ row }">
                    <el-tag :type="materialReviewTag(row.reviewStatus)">{{ materialReviewText(row.reviewStatus) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="90" align="center">
                  <template #default="{ row }">
                    <el-button link type="primary" @click.stop="handleReviewPreview(row)">预览</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div v-if="selectedMaterial" class="selected-files">
                <div class="selected-files-title">
                  当前版本文件
                  <span>{{ selectedFiles.length }} 份</span>
                </div>
                <el-table :data="selectedFiles" border size="small" empty-text="当前版本暂无文件">
                  <el-table-column type="index" label="序号" width="60" align="center" />
                  <el-table-column label="文件名称" prop="fileName" min-width="240" show-overflow-tooltip />
                  <el-table-column label="类型" prop="fileType" width="80" align="center" />
                  <el-table-column label="操作" width="90" align="center">
                    <template #default="{ row }">
                      <el-button link type="primary" @click="handleReviewFilePreview(row)">预览</el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </el-card>

            <el-card shadow="never" class="detail-block">
              <template #header>
                <div class="review-card-header">
                  <span>基本信息提取</span>
                  <div class="batch-actions">
                    <el-tag v-if="pendingFieldMappings.length" type="warning" size="small">
                      未确认 {{ pendingFieldMappings.length }} 项
                    </el-tag>
                    <el-button
                      type="success"
                      plain
                      size="small"
                      :loading="batchLoadingType === 'FIELD'"
                      :disabled="!selectedMaterialCanConfirm || !pendingFieldMappings.length"
                      @click="batchConfirmMappings('ACCEPTED')"
                    >
                      一键采纳
                    </el-button>
                    <el-button
                      type="danger"
                      plain
                      size="small"
                      :loading="batchLoadingType === 'FIELD'"
                      :disabled="!selectedMaterialCanConfirm || !pendingFieldMappings.length"
                      @click="batchConfirmMappings('REJECTED')"
                    >
                      一键拒绝
                    </el-button>
                  </div>
                </div>
              </template>
              <el-table :data="selectedMappings" border size="small" empty-text="当前材料暂无基本信息提取结果">
                <el-table-column label="标准字段" prop="dbField" width="150" />
                <el-table-column label="文件字段" prop="fileField" width="130" />
                <el-table-column label="提取值" min-width="190">
                  <template #default="{ row }">
                    <div>{{ row.extractedValue }}</div>
                    <small v-if="row.reviewedValue && row.reviewedValue !== row.originalExtractedValue">
                      AI原值：{{ row.originalExtractedValue }}
                    </small>
                  </template>
                </el-table-column>
                <el-table-column label="置信度" width="100">
                  <template #default="{ row }">
                    <strong>{{ Math.round(Number(row.confidence) * 100) }}%</strong>
                    <div
                      v-if="row.reviewedConfidence != null
                        && row.reviewedConfidence !== row.originalConfidence"
                    >
                      <small>AI：{{ Math.round(Number(row.originalConfidence) * 100) }}%</small>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="证据位置/审核意见" min-width="210">
                  <template #default="{ row }">
                    <div>{{ row.evidence }}</div>
                    <small v-if="row.reviewedEvidence && row.reviewedEvidence !== row.originalEvidence">
                      AI原证据：{{ row.originalEvidence }}
                    </small>
                    <small v-if="row.reviewComment" class="review-comment">
                      审核意见：{{ row.reviewComment }}
                    </small>
                  </template>
                </el-table-column>
                <el-table-column label="确认状态" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag :type="aiStatusTag(row.reviewStatus)" size="small">
                      {{ aiStatusText(row.reviewStatus) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="编辑/确认" width="180" align="center">
                  <template #default="{ row }">
                    <el-button
                      link
                      type="primary"
                      :disabled="!selectedMaterialCanConfirm || row.reviewStatus !== 'PENDING'"
                      @click="openAiEdit('FIELD', row)"
                    >编辑</el-button>
                    <el-button
                      link
                      type="success"
                      :disabled="!selectedMaterialCanConfirm"
                      @click="confirmAiItem('FIELD', row.mappingId, 'ACCEPTED')"
                    >采纳</el-button>
                    <el-button
                      link
                      type="danger"
                      :disabled="!selectedMaterialCanConfirm"
                      @click="confirmAiItem('FIELD', row.mappingId, 'REJECTED')"
                    >不采纳</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-card>

            <el-card shadow="never" class="detail-block">
              <template #header>
                <div class="review-card-header">
                  <span>30维AI评分逐条确认</span>
                  <div class="batch-actions">
                    <el-tag v-if="pendingDimensions.length" type="warning" size="small">
                      未确认 {{ pendingDimensions.length }} 项
                    </el-tag>
                    <el-button
                      type="success"
                      plain
                      size="small"
                      :loading="batchLoadingType === 'DIMENSION'"
                      :disabled="!selectedMaterialCanConfirm || !pendingDimensions.length"
                      @click="batchConfirmDimensions('ACCEPTED')"
                    >
                      一键采纳
                    </el-button>
                    <el-button
                      type="danger"
                      plain
                      size="small"
                      :loading="batchLoadingType === 'DIMENSION'"
                      :disabled="!selectedMaterialCanConfirm || !pendingDimensions.length"
                      @click="batchConfirmDimensions('REJECTED')"
                    >
                      一键拒绝
                    </el-button>
                  </div>
                </div>
              </template>
              <el-table :data="selectedDimensions" border size="small" max-height="420"
                empty-text="当前材料暂无AI维度评分">
                <el-table-column label="维度" prop="dimensionName" min-width="170" />
                <el-table-column label="分数" width="95" align="center">
                  <template #default="{ row }">
                    <strong>{{ row.score ?? "—" }}</strong>
                    <div v-if="row.reviewedScore != null && row.reviewedScore !== row.originalScore">
                      <small>AI：{{ row.originalScore }}</small>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="置信度" width="90" align="center">
                  <template #default="{ row }">{{ Math.round(Number(row.confidence) * 100) }}%</template>
                </el-table-column>
                <el-table-column label="证据/审核意见" min-width="270">
                  <template #default="{ row }">
                    <div>{{ row.evidence }}</div>
                    <small v-if="row.reviewedEvidence && row.reviewedEvidence !== row.originalEvidence">
                      AI原证据：{{ row.originalEvidence }}
                    </small>
                    <small v-if="row.reviewComment" class="review-comment">
                      审核意见：{{ row.reviewComment }}
                    </small>
                  </template>
                </el-table-column>
                <el-table-column label="确认状态" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag :type="aiStatusTag(row.reviewStatus)" size="small">
                      {{ aiStatusText(row.reviewStatus) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="编辑/确认" width="180" align="center">
                  <template #default="{ row }">
                    <el-button
                      link
                      type="primary"
                      :disabled="!selectedMaterialCanConfirm || row.reviewStatus !== 'PENDING'"
                      @click="openAiEdit('DIMENSION', row)"
                    >编辑</el-button>
                    <el-button
                      link
                      type="success"
                      :disabled="!selectedMaterialCanConfirm"
                      @click="confirmAiItem('DIMENSION', row.assessmentId, 'ACCEPTED')"
                    >采纳</el-button>
                    <el-button
                      link
                      type="danger"
                      :disabled="!selectedMaterialCanConfirm"
                      @click="confirmAiItem('DIMENSION', row.assessmentId, 'REJECTED')"
                    >不采纳</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-card>

            <el-card shadow="never" class="detail-block">
              <template #header>
                <div class="review-card-header">
                  <span>图谱关系逐条确认</span>
                  <div class="batch-actions">
                    <el-tag v-if="pendingFacts.length" type="warning" size="small">
                      未确认 {{ pendingFacts.length }} 项
                    </el-tag>
                    <el-button
                      type="success"
                      plain
                      size="small"
                      :loading="batchLoadingType === 'RELATION'"
                      :disabled="!selectedMaterialCanConfirm || !pendingFacts.length"
                      @click="batchConfirmRelations('ACCEPTED')"
                    >
                      一键采纳
                    </el-button>
                    <el-button
                      type="danger"
                      plain
                      size="small"
                      :loading="batchLoadingType === 'RELATION'"
                      :disabled="!selectedMaterialCanConfirm || !pendingFacts.length"
                      @click="batchConfirmRelations('REJECTED')"
                    >
                      一键拒绝
                    </el-button>
                  </div>
                </div>
              </template>
              <el-table :data="selectedFacts" border size="small" max-height="380"
                empty-text="当前材料暂无图谱关系">
                <el-table-column label="关系" min-width="300" show-overflow-tooltip>
                  <template #default="{ row }">
                    <div>{{ row.sourceName }} → {{ row.relationType }} → {{ row.targetName }}</div>
                    <small
                      v-if="row.reviewedSourceName || row.reviewedRelationType || row.reviewedTargetName"
                    >
                      AI原关系：{{ row.originalSourceName }} → {{ row.originalRelationType }}
                      → {{ row.originalTargetName }}
                    </small>
                  </template>
                </el-table-column>
                <el-table-column label="证据/审核意见" min-width="280">
                  <template #default="{ row }">
                    <div>{{ row.excerpt }}</div>
                    <small v-if="row.reviewedExcerpt && row.reviewedExcerpt !== row.originalExcerpt">
                      AI原证据：{{ row.originalExcerpt }}
                    </small>
                    <small v-if="row.reviewComment" class="review-comment">
                      审核意见：{{ row.reviewComment }}
                    </small>
                  </template>
                </el-table-column>
                <el-table-column label="确认状态" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag :type="aiStatusTag(row.reviewStatus)" size="small">
                      {{ aiStatusText(row.reviewStatus) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="编辑/确认" width="180" align="center">
                  <template #default="{ row }">
                    <el-button
                      link
                      type="primary"
                      :disabled="!selectedMaterialCanConfirm || row.reviewStatus !== 'PENDING'"
                      @click="openAiEdit('RELATION', row)"
                    >编辑</el-button>
                    <el-button
                      link
                      type="success"
                      :disabled="!selectedMaterialCanConfirm"
                      @click="confirmAiItem('RELATION', row.relationId, 'ACCEPTED')"
                    >采纳</el-button>
                    <el-button
                      link
                      type="danger"
                      :disabled="!selectedMaterialCanConfirm"
                      @click="confirmAiItem('RELATION', row.relationId, 'REJECTED')"
                    >不采纳</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-card>

            <el-card shadow="never" header="可服务时间表" class="detail-block">
              <el-table :data="detail.schedules || []" border size="small">
                <el-table-column label="星期" width="100">
                  <template #default="{ row }">{{ weekdayText(row.weekday) }}</template>
                </el-table-column>
                <el-table-column label="可服务时间" width="150">
                  <template #default="{ row }">{{ row.startTime }} - {{ row.endTime }}</template>
                </el-table-column>
                <el-table-column label="服务方式" width="120">
                  <template #default="{ row }">{{ serviceModeText(row.serviceMode) }}</template>
                </el-table-column>
                <el-table-column label="每周上限" prop="weeklyCapacity" width="110" />
                <el-table-column label="生效日期" prop="effectiveFrom" />
              </el-table>
            </el-card>

            <el-card shadow="never" header="能力画像（临时）" class="detail-block">
              <el-tag
                v-for="item in detail.graph || []"
                :key="item.dimensionCode"
                :type="item.dataStatus === 'AVAILABLE' ? 'success'
                  : item.dataStatus === 'CONFLICT' ? 'danger' : 'info'"
                class="dimension-tag"
                effect="plain"
              >
                {{ item.dimensionName }}
                {{ item.dataStatus === "AVAILABLE" ? Math.round(Number(item.score || 0))
                  : item.dataStatus === "CONFLICT" ? `冲突 ${Math.round(Number(item.score || 0))}`
                    : "证据不足" }}
              </el-tag>
            </el-card>
          </el-col>

          <el-col :xs="24" :lg="8">
            <el-card shadow="never" class="agent-review-card">
              <template #header>
                <div class="agent-review-title">
                  <div>
                    <strong>Agent初审意见</strong>
                    <small>材料提取、缺项与风险提示</small>
                  </div>
                  <el-tag :type="agentReviewTagType" effect="plain">
                    {{ agentReviewStatusText }}
                  </el-tag>
                </div>
              </template>
              <div class="agent-review-summary" :class="agentReviewTone">
                <div class="agent-summary-mark">
                  {{ selectedMaterial?.issueCount ? "!" : "✓" }}
                </div>
                <div>
                  <strong>
                    {{ selectedMaterial?.issueCount ? "需要人工关注" : "预审未发现明显问题" }}
                  </strong>
                  <p>{{ agentReviewSummary }}</p>
                </div>
              </div>

              <div v-if="agentIssueItems.length" class="agent-issue-list">
                <article
                  v-for="(issue, index) in agentIssueItems"
                  :key="`${issue.category}-${index}`"
                  class="agent-issue-item"
                >
                  <el-tag :type="issue.tagType" size="small" effect="plain">
                    {{ issue.category }}
                  </el-tag>
                  <p>{{ issue.text }}</p>
                </article>
              </div>
              <div
                v-else-if="selectedMaterial?.jobStatus && selectedMaterial.jobStatus !== 'FAILED'"
                class="agent-review-empty"
              >
                Agent已完成当前材料初审，未返回需要人工处理的缺项或冲突。
              </div>
              <div class="material-link">
                <span>当前审核材料</span>
                <span v-if="selectedMaterial?.fileName">
                  {{ categoryText(selectedMaterial.materialCategory) }} · {{ selectedMaterial.fileName }}
                </span>
                <span v-else>无</span>
                <span v-if="selectedMaterial?.validUntil">
                  审核有效期：{{ validityRemainingText(selectedMaterial) }}
                </span>
                <span>原文受控存储，当前展示脱敏字段</span>
              </div>
            </el-card>

            <el-card shadow="never" header="审核决定" class="detail-block">
              <el-alert
                v-if="selectedMaterial && !selectedMaterialReviewable"
                :title="reviewBlockedText"
                type="info"
                :closable="false"
                show-icon
                class="review-lock-tip"
              />
              <el-form label-position="top">
                <el-form-item label="审核意见">
                  <el-input v-model="reviewForm.comment" type="textarea" :rows="5" maxlength="1000" show-word-limit />
                </el-form-item>
                <div class="review-buttons">
                  <el-button
                    type="success"
                    :loading="submitting"
                    :disabled="!selectedMaterialReviewable"
                    @click="submitAction('PASS')"
                    v-hasPermi="['review:action']"
                  >
                    通过当前认证角度
                  </el-button>
                  <el-button
                    type="warning"
                    :loading="submitting"
                    :disabled="!selectedMaterialReviewable"
                    @click="submitAction('RETURN')"
                    v-hasPermi="['review:action']"
                  >
                    退回补正
                  </el-button>
                </div>
              </el-form>
            </el-card>

            <el-card shadow="never" header="审核记录" class="detail-block">
              <el-timeline v-if="detail.reviewLogs?.length">
                <el-timeline-item
                  v-for="log in detail.reviewLogs"
                  :key="log.logId"
                  :timestamp="log.createTime"
                >
                  <strong>{{ reviewActionText(log.action) }} · {{ log.reviewerName }}</strong>
                  <p>{{ log.comment || "无审核意见" }}</p>
                </el-timeline-item>
              </el-timeline>
              <el-empty v-else description="暂无审核记录" :image-size="60" />
            </el-card>
          </el-col>
        </el-row>
      </div>
    </el-drawer>

    <el-dialog
      v-model="aiEditOpen"
      :title="aiEditTitle"
      width="720px"
      append-to-body
      destroy-on-close
    >
      <el-form :model="aiEditForm" label-position="top">
        <el-form-item v-if="aiEditType === 'FIELD'" label="审核提取值">
          <el-input v-model="aiEditForm.reviewedValue" maxlength="1000" show-word-limit />
          <small class="original-value">AI原值：{{ aiEditRow.originalExtractedValue || "—" }}</small>
        </el-form-item>
        <el-form-item v-if="aiEditType === 'FIELD'" label="审核置信度">
          <el-input-number
            v-model="aiEditForm.reviewedConfidence"
            :min="0"
            :max="1"
            :step="0.01"
            :precision="2"
            controls-position="right"
          />
          <small class="original-value">
            AI原始置信度：{{ Math.round(Number(aiEditRow.originalConfidence || 0) * 100) }}%
          </small>
        </el-form-item>
        <el-form-item v-if="aiEditType === 'DIMENSION'" label="审核评分">
          <el-input-number
            v-model="aiEditForm.reviewedScore"
            :min="0"
            :max="100"
            :step="1"
            :precision="2"
            controls-position="right"
          />
          <small class="original-value">AI原始分：{{ aiEditRow.originalScore ?? "—" }}</small>
        </el-form-item>
        <el-form-item
          v-if="aiEditType === 'FIELD' || aiEditType === 'DIMENSION'"
          label="审核证据说明"
        >
          <el-input
            v-model="aiEditForm.reviewedEvidence"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
          />
          <small class="original-value">AI原始证据：{{ aiEditRow.originalEvidence || "—" }}</small>
        </el-form-item>
        <el-form-item v-if="aiEditType === 'RELATION'" label="审核关系证据">
          <div class="relation-editor">
            <el-form-item label="源节点名称">
              <el-input
                v-model="aiEditForm.reviewedSourceName"
                maxlength="255"
                show-word-limit
              />
              <small class="original-value">
                AI原节点：{{ aiEditRow.originalSourceName || "—" }}
                · 类型：{{ aiEditRow.sourceType || "—" }}
              </small>
            </el-form-item>
            <el-form-item label="源节点类型">
              <el-select v-model="aiEditForm.reviewedSourceType" style="width: 100%">
                <el-option
                  v-for="type in entityTypeOptions"
                  :key="type"
                  :label="type"
                  :value="type"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="关系类型">
              <el-select v-model="aiEditForm.reviewedRelationType" style="width: 100%">
                <el-option
                  v-for="type in relationTypeOptions"
                  :key="type"
                  :label="type"
                  :value="type"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="目标节点名称">
              <el-input
                v-model="aiEditForm.reviewedTargetName"
                maxlength="255"
                show-word-limit
              />
              <small class="original-value">
                AI原节点：{{ aiEditRow.originalTargetName || "—" }}
                · 类型：{{ aiEditRow.targetType || "—" }}
              </small>
            </el-form-item>
            <el-form-item label="目标节点类型">
              <el-select v-model="aiEditForm.reviewedTargetType" style="width: 100%">
                <el-option
                  v-for="type in entityTypeOptions"
                  :key="type"
                  :label="type"
                  :value="type"
                />
              </el-select>
            </el-form-item>
          </div>
          <el-input
            v-model="aiEditForm.reviewedExcerpt"
            type="textarea"
            :rows="5"
            maxlength="1000"
            show-word-limit
          />
          <small class="original-value">AI原始证据：{{ aiEditRow.originalExcerpt || "—" }}</small>
        </el-form-item>
        <el-form-item label="审核意见">
          <el-input
            v-model="aiEditForm.reviewComment"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="填写为何修改AI结果"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="aiEditOpen = false">取消</el-button>
        <el-button type="primary" :loading="aiEditSaving" @click="saveAiEdit">
          保存修改
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="previewOpen"
      :title="previewTitle"
      width="86%"
      top="4vh"
      append-to-body
      destroy-on-close
      @closed="clearPreview"
    >
      <div v-loading="previewLoading" class="preview-body">
        <img v-if="previewKind === 'image'" :src="previewUrl" class="preview-image" alt="审核材料预览" />
        <iframe v-else-if="previewKind === 'pdf'" :src="previewUrl" class="preview-pdf" title="PDF审核预览" />
        <pre v-else-if="previewKind === 'text'" class="preview-text">{{ previewText }}</pre>
        <el-empty v-else description="当前文件格式暂不支持浏览器内预览" />
      </div>
      <template #footer>
        <el-button @click="previewOpen = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="ReviewQueue">
import {
  getReviewDetail,
  editReviewAiResult,
  listReviewQueue,
  previewReviewMaterial,
  previewReviewMaterialFile,
  reviewAiResult,
  retryAgentJob,
  submitReviewAction
} from "@/api/expert/platform"

const { proxy } = getCurrentInstance()
const route = useRoute()
const loading = ref(false)
const detailLoading = ref(false)
const submitting = ref(false)
const batchLoadingType = ref("")
const aiEditOpen = ref(false)
const aiEditSaving = ref(false)
const aiEditType = ref("")
const aiEditRow = ref({})
const queue = ref([])
const total = ref(0)
const detailOpen = ref(false)
const detail = ref({})
const currentUserId = ref()
const selectedMaterialId = ref()
const preferredMaterialId = ref()
const previewOpen = ref(false)
const previewLoading = ref(false)
const previewKind = ref("")
const previewUrl = ref("")
const previewText = ref("")
const previewTitle = ref("审核材料预览")
const reviewForm = reactive({ comment: "" })
const aiEditForm = reactive({
  reviewedValue: "",
  reviewedConfidence: undefined,
  reviewedScore: undefined,
  reviewedEvidence: "",
  reviewedSourceName: "",
  reviewedSourceType: "",
  reviewedRelationType: "",
  reviewedTargetName: "",
  reviewedTargetType: "",
  reviewedExcerpt: "",
  reviewComment: ""
})
const relationTypeOptions = [
  "HAS_EXPERT",
  "BELONGS_TO",
  "HAS_CREDENTIAL",
  "EXPERTISE_IN",
  "WORKS_IN_INDUSTRY",
  "SERVES_REGION",
  "PARTICIPATED_IN",
  "PRODUCED",
  "PROJECT_PRODUCED",
  "USES_TECHNOLOGY",
  "APPLIES_TO",
  "TARGETS_INDUSTRY"
]
const entityTypeOptions = [
  "Industry",
  "Expert",
  "Organization",
  "Credential",
  "Capability",
  "Project",
  "Achievement",
  "Technology",
  "ServiceRegion"
]
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  realName: undefined,
  researchField: undefined,
  reviewStatus: undefined
})
const selectedMaterial = computed(() =>
  (detail.value.materials || []).find(item => item.materialId === selectedMaterialId.value)
)
const agentIssueItems = computed(() => {
  const message = String(selectedMaterial.value?.agentMessage || "").trim()
  if (!message || message.includes("未发现明显缺项"))
  {
    return []
  }
  const content = message.replace(
    /^.*?(?:发现\s*\d+\s*个问题|存在\s*\d+\s*个问题)[：:]\s*/,
    ""
  )
  return content
    .split(/[；;\n]+/)
    .map(item => item.trim())
    .filter(Boolean)
    .map(text => ({
      text,
      category: agentIssueCategory(text),
      tagType: agentIssueTagType(text)
    }))
})
const agentReviewSummary = computed(() => {
  if (!selectedMaterial.value?.agentMessage)
  {
    return "暂无初审结果"
  }
  if (!selectedMaterial.value.issueCount)
  {
    return "材料可进入逐项确认；仍需审核人员核对原始材料。"
  }
  return `共识别 ${agentIssueItems.value.length || selectedMaterial.value.issueCount} 个问题，建议结合原始材料逐项复核。`
})
const agentReviewTone = computed(() => {
  if (selectedMaterial.value?.jobStatus === "FAILED") return "is-danger"
  return selectedMaterial.value?.issueCount ? "is-warning" : "is-success"
})
const agentReviewTagType = computed(() => {
  if (selectedMaterial.value?.jobStatus === "FAILED") return "danger"
  return selectedMaterial.value?.issueCount ? "warning" : "success"
})
const agentReviewStatusText = computed(() => {
  if (selectedMaterial.value?.jobStatus === "FAILED") return "处理失败"
  return selectedMaterial.value?.issueCount
    ? `${selectedMaterial.value.issueCount} 项待关注`
    : "初审通过"
})
const selectedMappings = computed(() =>
  detail.value.mappingsByMaterial?.[selectedMaterialId.value] || []
)
const pendingFieldMappings = computed(() =>
  selectedMappings.value.filter(item => item.reviewStatus === "PENDING")
)
const selectedDimensions = computed(() =>
  detail.value.dimensionsByMaterial?.[selectedMaterialId.value] || []
)
const pendingDimensions = computed(() =>
  selectedDimensions.value.filter(item => item.reviewStatus === "PENDING")
)
const selectedFacts = computed(() =>
  detail.value.factsByMaterial?.[selectedMaterialId.value] || []
)
const aiEditTitle = computed(() => ({
  FIELD: "编辑基本信息提取结果",
  DIMENSION: "编辑30维AI评分",
  RELATION: "编辑图谱关系证据"
})[aiEditType.value] || "编辑AI结果")
const pendingFacts = computed(() =>
  selectedFacts.value.filter(item => item.reviewStatus === "PENDING")
)
const selectedFiles = computed(() => {
  const files = detail.value.filesByMaterial?.[selectedMaterialId.value] || []
  if (files.length) return files
  const material = selectedMaterial.value
  return material?.filePath ? [{
    fileName: material.fileName,
    fileType: material.fileType,
    filePath: material.filePath
  }] : []
})
const selectedMaterialCanConfirm = computed(() => {
  const material = selectedMaterial.value
  if (!material || !["PENDING", "PENDING_REVIEW"].includes(material.reviewStatus)) return false
  if (!["REVIEW_PENDING", "PRE_REVIEW_PASSED"].includes(material.jobStatus)) return false
  const latest = (detail.value.materials || []).find(item =>
    item.materialCategory === material.materialCategory
  )
  return latest?.materialId === material.materialId
})
const selectedMaterialReviewable = computed(() => {
  if (!selectedMaterialCanConfirm.value) return false
  const aiConfirmed = [...selectedMappings.value, ...selectedDimensions.value,
    ...selectedFacts.value].every(item => item.reviewStatus !== "PENDING")
  return aiConfirmed
})
const reviewBlockedText = computed(() => {
  const material = selectedMaterial.value
  if (!material) return "请先选择需要审核的材料"
  if (!["PENDING", "PENDING_REVIEW"].includes(material.reviewStatus)) {
    return "该材料已经处理，审核结果不可重复修改"
  }
  if (!["REVIEW_PENDING", "PRE_REVIEW_PASSED"].includes(material.jobStatus)) {
    return "该材料仍在处理中，暂时不能审核"
  }
  const pendingCount = [...selectedMappings.value, ...selectedDimensions.value,
    ...selectedFacts.value].filter(item => item.reviewStatus === "PENDING").length
  if (pendingCount > 0) {
    return `仍有 ${pendingCount} 条AI结果未确认，请逐条采纳或拒绝`
  }
  return "这是历史版本，仅支持查看，不可审核"
})
function getList() {
  loading.value = true
  return listReviewQueue(queryParams)
    .then(res => {
      queue.value = res.rows || []
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
    realName: undefined,
    researchField: undefined,
    reviewStatus: undefined
  })
  getList()
}

function openDetail(userId, materialId = null) {
  currentUserId.value = userId
  preferredMaterialId.value = materialId
  detailOpen.value = true
  reviewForm.comment = ""
  selectedMaterialId.value = undefined
  loadDetail()
}

function loadDetail() {
  detailLoading.value = true
  getReviewDetail(currentUserId.value)
    .then(res => {
      detail.value = res.data || {}
      const materials = detail.value.materials || []
      const preferred = materials.find(item =>
        item.materialId === Number(preferredMaterialId.value)
      )
      const nextMaterial = preferred || materials.find(item =>
        item.reviewStatus !== "APPROVED"
        && ["REVIEW_PENDING", "PRE_REVIEW_PASSED"].includes(item.jobStatus)
      ) || materials.find(item => item.reviewStatus !== "APPROVED") || materials[0]
      selectedMaterialId.value = nextMaterial?.materialId
    })
    .finally(() => {
      detailLoading.value = false
    })
}

function submitAction(action) {
  if (!selectedMaterial.value) {
    proxy.$modal.msgWarning("请先选择需要审核的材料")
    return
  }
  if (action !== "PASS" && !reviewForm.comment.trim()) {
    proxy.$modal.msgWarning("退回补正时请填写审核意见")
    return
  }
  const text = { PASS: "通过当前认证角度", RETURN: "退回补正" }[action]
  proxy.$modal.confirm(`是否确认${text}？`).then(() => {
    submitting.value = true
    return submitReviewAction(currentUserId.value, {
      materialId: selectedMaterial.value.materialId,
      action,
      comment: reviewForm.comment
    })
  }).then(() => {
    proxy.$modal.msgSuccess("审核操作已完成")
    getList()
    loadDetail()
  }).finally(() => {
    submitting.value = false
  }).catch(() => {})
}

function selectMaterial(row) {
  selectedMaterialId.value = row.materialId
  reviewForm.comment = ""
}

function confirmAiItem(resultType, resultId, status) {
  if (!selectedMaterial.value || !resultId) return
  reviewAiResult(selectedMaterial.value.materialId, resultType, resultId, status)
    .then(() => {
      proxy.$modal.msgSuccess(status === "ACCEPTED" ? "AI结果已采纳" : "AI结果已拒绝")
      loadDetail()
    })
}

function openAiEdit(type, row) {
  aiEditType.value = type
  aiEditRow.value = row
  Object.assign(aiEditForm, {
    reviewedValue: row.reviewedValue || row.originalExtractedValue || row.extractedValue || "",
    reviewedConfidence: row.reviewedConfidence ?? row.originalConfidence ?? row.confidence,
    reviewedScore: row.reviewedScore ?? row.originalScore ?? row.score,
    reviewedEvidence: row.reviewedEvidence || row.originalEvidence || row.evidence || "",
    reviewedSourceName: row.reviewedSourceName
      || row.originalSourceName || row.sourceName || "",
    reviewedSourceType: row.reviewedSourceType
      || row.originalSourceType || row.sourceType || "",
    reviewedRelationType: row.reviewedRelationType
      || row.originalRelationType || row.relationType || "",
    reviewedTargetName: row.reviewedTargetName
      || row.originalTargetName || row.targetName || "",
    reviewedTargetType: row.reviewedTargetType
      || row.originalTargetType || row.targetType || "",
    reviewedExcerpt: row.reviewedExcerpt || row.originalExcerpt || row.excerpt || "",
    reviewComment: row.reviewComment || ""
  })
  aiEditOpen.value = true
}

function saveAiEdit() {
  if (!selectedMaterial.value) return
  if (aiEditType.value === "FIELD" && (!aiEditForm.reviewedValue.trim()
    || aiEditForm.reviewedConfidence == null
    || !aiEditForm.reviewedEvidence.trim())) {
    ElMessage.warning("提取值、置信度和证据说明不能为空")
    return
  }
  if (aiEditType.value === "DIMENSION" && (aiEditForm.reviewedScore == null
    || !aiEditForm.reviewedEvidence.trim())) {
    ElMessage.warning("评分和证据说明不能为空")
    return
  }
  if (aiEditType.value === "RELATION" && (!aiEditForm.reviewedSourceName.trim()
    || !aiEditForm.reviewedSourceType
    || !aiEditForm.reviewedRelationType
    || !aiEditForm.reviewedTargetName.trim()
    || !aiEditForm.reviewedTargetType
    || !aiEditForm.reviewedExcerpt.trim())) {
    ElMessage.warning("节点名称/类型、关系类型和关系证据不能为空")
    return
  }
  const resultId = aiEditType.value === "FIELD"
    ? aiEditRow.value.mappingId
    : aiEditType.value === "DIMENSION"
      ? aiEditRow.value.assessmentId
      : aiEditRow.value.relationId
  aiEditSaving.value = true
  editReviewAiResult(
    selectedMaterial.value.materialId,
    aiEditType.value,
    resultId,
    {
      reviewedValue: aiEditForm.reviewedValue || undefined,
      reviewedConfidence: aiEditForm.reviewedConfidence,
      reviewedScore: aiEditForm.reviewedScore,
      reviewedEvidence: aiEditForm.reviewedEvidence || undefined,
      reviewedSourceName: aiEditForm.reviewedSourceName || undefined,
      reviewedSourceType: aiEditForm.reviewedSourceType || undefined,
      reviewedRelationType: aiEditForm.reviewedRelationType || undefined,
      reviewedTargetName: aiEditForm.reviewedTargetName || undefined,
      reviewedTargetType: aiEditForm.reviewedTargetType || undefined,
      reviewedExcerpt: aiEditForm.reviewedExcerpt || undefined,
      reviewComment: aiEditForm.reviewComment || undefined
    }
  )
    .then(() => {
      ElMessage.success("AI结果修改已保存")
      aiEditOpen.value = false
      loadDetail()
    })
    .finally(() => {
      aiEditSaving.value = false
    })
}

function batchConfirmMappings(status) {
  return batchConfirmItems(
    "FIELD", pendingFieldMappings.value, "mappingId", status, "基本信息"
  )
}

function batchConfirmDimensions(status) {
  return batchConfirmItems(
    "DIMENSION", pendingDimensions.value, "assessmentId", status, "AI评分"
  )
}

function batchConfirmRelations(status) {
  return batchConfirmItems(
    "RELATION", pendingFacts.value, "relationId", status, "图谱关系"
  )
}

async function batchConfirmItems(resultType, items, idKey, status, typeLabel) {
  if (!selectedMaterial.value || !items.length) return
  const actionText = status === "ACCEPTED" ? "采纳" : "拒绝"
  try {
    await proxy.$modal.confirm(
      `是否一键${actionText}当前材料全部 ${items.length} 个未确认${typeLabel}？`
    )
    batchLoadingType.value = resultType
    for (const item of items) {
      await reviewAiResult(
        selectedMaterial.value.materialId,
        resultType,
        item[idKey],
        status
      )
    }
    proxy.$modal.msgSuccess(`已${actionText}全部未确认${typeLabel}`)
    await loadDetail()
  } catch (error) {
    if (error !== "cancel" && error !== "close") {
      proxy.$modal.msgError(error?.message || `批量${actionText}${typeLabel}失败`)
      await loadDetail()
    }
  } finally {
    batchLoadingType.value = ""
  }
}

function aiStatusText(status) {
  return {
    PENDING: "未确认",
    ACCEPTED: "已采纳",
    REJECTED: "已拒绝"
  }[status] || "未确认"
}

function aiStatusTag(status) {
  return {
    ACCEPTED: "success",
    REJECTED: "danger",
    PENDING: "warning"
  }[status] || "info"
}

async function handleReviewPreview(row) {
  clearPreview()
  previewLoading.value = true
  previewOpen.value = true
  previewTitle.value = row.fileName || "审核材料预览"
  const fileType = String(row.fileType || "").toLowerCase()
  const textMode = ["docx", "txt", "md", "csv"].includes(fileType)
  try {
    const blob = await previewReviewMaterial(row.materialId, textMode ? "text" : "raw")
    if (textMode) {
      previewKind.value = "text"
      previewText.value = await blob.text()
    }
    else if (["png", "jpg", "jpeg", "gif", "webp"].includes(fileType)) {
      previewKind.value = "image"
      previewUrl.value = URL.createObjectURL(blob)
    }
    else if (fileType === "pdf") {
      previewKind.value = "pdf"
      previewUrl.value = URL.createObjectURL(blob)
    }
    else {
      previewKind.value = "unsupported"
    }
  }
  catch {
    previewKind.value = "unsupported"
  }
  finally {
    previewLoading.value = false
  }
}

async function handleReviewFilePreview(file) {
  clearPreview()
  previewLoading.value = true
  previewOpen.value = true
  previewTitle.value = file.fileName || "审核材料预览"
  const fileType = String(file.fileType || "").toLowerCase()
  const textMode = ["docx", "txt", "md", "csv"].includes(fileType)
  try {
    const blob = file.fileId
      ? await previewReviewMaterialFile(selectedMaterial.value.materialId, file.fileId,
        textMode ? "text" : "raw")
      : await previewReviewMaterial(selectedMaterial.value.materialId,
        textMode ? "text" : "raw")
    if (textMode) {
      previewKind.value = "text"
      previewText.value = await blob.text()
    }
    else if (["png", "jpg", "jpeg", "gif", "webp"].includes(fileType)) {
      previewKind.value = "image"
      previewUrl.value = URL.createObjectURL(blob)
    }
    else if (fileType === "pdf") {
      previewKind.value = "pdf"
      previewUrl.value = URL.createObjectURL(blob)
    }
    else {
      previewKind.value = "unsupported"
    }
  }
  catch {
    previewKind.value = "unsupported"
  }
  finally {
    previewLoading.value = false
  }
}

function clearPreview() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
  previewUrl.value = ""
  previewText.value = ""
  previewKind.value = ""
}

function retry(materialId) {
  retryAgentJob(materialId).then(() => {
    proxy.$modal.msgSuccess("处理任务已重新排队")
    getList()
  })
}

function reviewText(status) {
  return {
    PENDING: "待审核",
    PENDING_REVIEW: "待审核",
    REVIEWING: "审核中",
    RETURNED: "退回补正",
    APPROVED: "已通过",
    REJECTED: "退回补正"
  }[status] || status
}

function reviewTag(status) {
  return {
    PENDING: "warning",
    PENDING_REVIEW: "warning",
    REVIEWING: "primary",
    RETURNED: "warning",
    APPROVED: "success",
    REJECTED: "warning"
  }[status] || "info"
}

function reviewActionText(action) {
  return {
    PASS: "审核通过",
    RETURN: "退回补正",
    REJECT: "退回补正",
    CLAIM: "领取审核",
    AI_RESULT_EDITED: "编辑AI结果",
    AI_RESULT_ACCEPTED: "采纳AI结果",
    AI_RESULT_REJECTED: "拒绝AI结果"
  }[action] || action
}

function angleTagType(status) {
  return {
    APPROVED: "success",
    EXPIRING_SOON: "warning",
    EXPIRED: "danger",
    IN_REVIEW: "warning",
    NEEDS_UPDATE: "danger"
  }[status] || "info"
}

function materialReviewText(status) {
  return {
    PENDING: "待审核",
    PENDING_REVIEW: "待审核",
    APPROVED: "已通过",
    RETURNED: "需补正",
    REJECTED: "退回补正"
  }[status] || status || "待审核"
}

function materialReviewTag(status) {
  return {
    APPROVED: "success",
    PENDING_REVIEW: "info",
    RETURNED: "warning",
    REJECTED: "warning"
  }[status] || "info"
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

function jobText(status) {
  return {
    QUEUED: "排队中",
    EXTRACTING: "提取中",
    MASKING: "脱敏中",
    MAPPING: "映射中",
    PRE_REVIEWING: "初审中",
    REVIEW_PENDING: "待审核",
    PRE_REVIEW_PASSED: "待审核",
    REVIEWED: "审核已完成",
    FAILED: "处理失败"
  }[status] || status
}

function agentIssueCategory(text) {
  if (/失败|回退|异常/.test(text)) return "处理提示"
  if (/冲突/.test(text)) return "信息冲突"
  if (/证据不足|画像|评分/.test(text)) return "画像证据"
  if (/缺少|缺项|未提供|未识别|不完整/.test(text)) return "资料缺项"
  return "其他提示"
}

function agentIssueTagType(text) {
  if (/失败|异常|冲突/.test(text)) return "danger"
  if (/缺少|缺项|未提供|不完整/.test(text)) return "warning"
  return "info"
}

function validityRemainingText(item) {
  if (!item?.validUntil) return "审核通过后自动生成3个月有效期"
  const expiryText = String(item.validUntil).slice(0, 10)
  const expiry = new Date(`${expiryText}T00:00:00`)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const remainingDays = Math.ceil((expiry.getTime() - today.getTime()) / 86400000)
  if (remainingDays < 0) {
    return `有效期至 ${expiryText} · 已过期 ${Math.abs(remainingDays)} 天`
  }
  if (remainingDays === 0) return `有效期至 ${expiryText} · 今天到期`
  return `有效期至 ${expiryText} · 剩余 ${remainingDays} 天`
}

function weekdayText(day) {
  return ["", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"][day] || day
}

function serviceModeText(mode) {
  return { REMOTE: "远程", ONSITE: "现场", BOTH: "均可" }[mode] || mode
}

getList().then(() => {
  const requestedUserId = Number(route.query.userId)
  if (requestedUserId) {
    const requestedMaterialId = Number(route.query.materialId)
    openDetail(requestedUserId, requestedMaterialId || null)
  }
})
onUnmounted(clearPreview)
</script>

<style scoped lang="scss">
.review-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.batch-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.search-form {
  padding: 16px 16px 0;
  margin-bottom: 16px;
  background: #fff;
  border-radius: 8px;
}
.sub-text {
  margin-top: 4px;
  color: #8a97a3;
  font-size: 12px;
}
.review-detail {
  padding: 0 8px 24px;
}
.detail-block {
  margin-top: 16px;
}
.agent-review-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  > div {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  strong {
    color: var(--el-text-color-primary);
  }
  small {
    color: var(--el-text-color-secondary);
    font-weight: 400;
  }
}
.agent-review-summary {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-fill-color-extra-light);
  strong {
    color: var(--el-text-color-primary);
  }
  p {
    margin: 5px 0 0;
    color: var(--el-text-color-secondary);
    line-height: 1.6;
  }
  &.is-success {
    border-color: color-mix(in srgb, var(--el-color-success) 28%, var(--el-border-color-lighter));
  }
  &.is-warning {
    border-color: color-mix(in srgb, var(--el-color-warning) 32%, var(--el-border-color-lighter));
  }
  &.is-danger {
    border-color: color-mix(in srgb, var(--el-color-danger) 32%, var(--el-border-color-lighter));
  }
}
.agent-summary-mark {
  display: grid;
  flex: 0 0 30px;
  width: 30px;
  height: 30px;
  place-items: center;
  border-radius: 50%;
  color: var(--el-color-success);
  font-weight: 700;
  background: color-mix(in srgb, var(--el-color-success) 13%, transparent);
}
.agent-review-summary.is-warning .agent-summary-mark {
  color: var(--el-color-warning);
  background: color-mix(in srgb, var(--el-color-warning) 15%, transparent);
}
.agent-review-summary.is-danger .agent-summary-mark {
  color: var(--el-color-danger);
  background: color-mix(in srgb, var(--el-color-danger) 14%, transparent);
}
.agent-issue-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}
.agent-issue-item {
  display: grid;
  grid-template-columns: auto 1fr;
  align-items: start;
  gap: 10px;
  padding: 10px 12px;
  border-left: 3px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
  p {
    margin: 0;
    color: var(--el-text-color-regular);
    line-height: 1.6;
  }
}
.agent-review-empty {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 6px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
  background: var(--el-fill-color-lighter);
}
.review-comment,
.original-value {
  display: block;
  margin-top: 5px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
.review-comment {
  color: var(--el-color-warning);
}
.relation-editor {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
}
@media (max-width: 760px) {
  .relation-editor {
    grid-template-columns: 1fr;
  }
}
.dimension-tag {
  margin: 0 8px 8px 0;
}
.material-link {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 18px;
  color: #7b8794;
}
.selected-files {
  margin-top: 16px;
}
.selected-files-title {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
  color: #34495e;
  font-weight: 600;
  span {
    color: #7b8794;
    font-weight: 400;
  }
}
.cert-summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  strong {
    color: #243d55;
  }
  span {
    color: #7b8794;
  }
}
.cert-angle-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.cert-angle {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #f6f8fa;
  span {
    flex: 1;
    color: #465665;
  }
  small {
    color: #8a97a3;
  }
}
.review-buttons {
  display: flex;
  flex-direction: column;
  gap: 10px;
  .el-button {
    width: 100%;
    margin-left: 0;
  }
}
.status-submit {
  width: 100%;
  margin-top: 14px;
}
.review-lock-tip {
  margin-bottom: 16px;
}
.preview-body {
  min-height: 420px;
  background: #f5f7f9;
  border-radius: 8px;
  overflow: hidden;
}
.preview-image {
  display: block;
  max-width: 100%;
  max-height: 75vh;
  margin: 0 auto;
  object-fit: contain;
}
.preview-pdf {
  width: 100%;
  height: 75vh;
  border: 0;
  background: #fff;
}
.preview-text {
  min-height: 420px;
  max-height: 75vh;
  margin: 0;
  padding: 24px;
  overflow: auto;
  color: #26384a;
  font-family: "Microsoft YaHei", monospace;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
  background: #fff;
}
</style>
