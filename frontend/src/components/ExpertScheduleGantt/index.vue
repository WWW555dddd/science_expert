<template>
  <section class="schedule-gantt">
    <div class="gantt-toolbar">
      <div>
        <h3>{{ dateMode ? "我的时间安排" : "日常可联系时段" }}</h3>
        <p v-if="dateMode">
          按日期查看日常时段、临时不可用和任务占用。日常时段仍可点击或拖动编辑；临时不可用在图表下方登记和修改。
        </p>
        <p v-else>
          维护平时方便沟通和承接服务的固定周时段；点击空白位置新增，拖动可调整时间和星期。临时请假请在下方单独登记。
        </p>
      </div>
      <div class="toolbar-actions">
        <template v-if="dateMode">
          <el-button icon="ArrowLeft" @click="shiftDateRange(-1)">上一段</el-button>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            format="YYYY-MM-DD"
            :clearable="false"
            unlink-panels
            style="width: 255px"
            @change="normalizeDateRange"
          />
          <el-button @click="shiftDateRange(1)">下一段</el-button>
          <el-button @click="selectToday">本周</el-button>
          <el-tag effect="plain">{{ dateRangeDays }} 天 · 最多31天</el-tag>
          <el-button icon="Refresh" @click="$emit('refresh-occupancy')">刷新任务</el-button>
        </template>
        <el-tag :type="overlapKeys.size ? 'warning' : 'info'" effect="plain">
          {{ rows.length }} 个时段
          <template v-if="overlapKeys.size"> · {{ overlapKeys.size }} 个重叠</template>
        </el-tag>
        <el-button type="primary" plain icon="Plus" @click="addDefaultBlock">新增时段</el-button>
      </div>
    </div>

    <div class="gantt-layout">
      <div class="gantt-panel">
        <div class="gantt-scroll">
          <div class="gantt-board" :class="{ 'date-mode': dateMode }">
            <div class="gantt-header">
              <div class="day-heading">{{ dateMode ? "时段类型" : "星期" }}</div>
              <div class="timeline-heading">
                <div v-for="hour in hours" :key="hour" class="hour-heading">
                  <span v-if="hour % 4 === 0">{{ String(hour).padStart(2, "0") }}:00</span>
                </div>
              </div>
            </div>

            <template v-if="dateMode">
              <div
                v-for="row in dateRows"
                :key="`${row.date}-${row.lane.key}`"
                class="gantt-row"
                :class="{ 'date-break': row.laneIndex === 0 }"
              >
                <div class="day-label">
                  <strong>{{ row.dateLabel }}</strong>
                  <small>{{ row.weekdayLabel }} · {{ row.lane.short }}</small>
                </div>
                <div
                  class="gantt-day-row"
                  :class="`lane-${row.lane.key}`"
                  :data-weekday="row.weekday"
                  @click="row.lane.key === 'schedule' && addAtPosition($event, row.weekday, row.date)"
                >
                  <div class="grid-lines" aria-hidden="true">
                    <span v-for="hour in 24" :key="hour" :class="{ major: hour % 4 === 1 }" />
                  </div>
                  <template v-if="row.lane.key === 'schedule'">
                    <div
                      v-for="block in scheduleBlocksForDate(row.date)"
                      :key="block._key"
                      class="schedule-block"
                      :class="[
                        `mode-${String(block.serviceMode).toLowerCase()}`,
                        {
                          selected: block._key === selectedKey,
                          overlap: overlapKeys.has(block._key)
                        }
                      ]"
                      :style="blockStyle(block)"
                      @click.stop="selectBlock(block)"
                      @pointerdown.stop="beginPointerEdit($event, block, 'move')"
                    >
                      <span
                        class="resize-handle left"
                        title="拖动调整开始时间"
                        @pointerdown.stop="beginPointerEdit($event, block, 'start')"
                      />
                      <div class="block-content">
                        <strong>{{ block.startTime }}–{{ block.endTime }}</strong>
                        <span>{{ modeText(block.serviceMode) }} · {{ block.weeklyCapacity }} 项/周</span>
                      </div>
                      <span
                        class="resize-handle right"
                        title="拖动调整结束时间"
                        @pointerdown.stop="beginPointerEdit($event, block, 'end')"
                      />
                    </div>
                    <span v-if="!scheduleBlocksForDate(row.date).length" class="lane-empty">当天没有日常可联系时段</span>
                  </template>
                  <template v-else-if="row.lane.key === 'unavailable'">
                    <div
                      v-for="period in unavailableBlocksForDate(row.date)"
                      :key="`unavailable-${period.unavailableId}`"
                      class="timeline-block unavailable-block"
                      :style="dateBlockStyle(period.startTime, period.endTime, row.date)"
                      :title="period.reason || '临时不可用'"
                    >
                      <div class="block-content">
                        <strong>临时不可用</strong>
                        <span>{{ period.reason || "未填写原因" }}</span>
                      </div>
                    </div>
                    <span v-if="!unavailableBlocksForDate(row.date).length" class="lane-empty">当天没有临时不可用时段</span>
                  </template>
                  <template v-else>
                    <div
                      v-for="period in occupancyBlocksForDate(row.date)"
                      :key="`${period.externalTaskId}-${period.serviceStart}-${period.serviceEnd}`"
                      class="timeline-block occupancy-block"
                      :class="String(period.occupancyStatus).toLowerCase()"
                      :style="dateBlockStyle(period.serviceStart, period.serviceEnd, row.date)"
                      :title="`${period.externalTaskId} · ${period.occupancyStatus}`"
                    >
                      <div class="block-content">
                        <strong>{{ period.externalTaskId }}</strong>
                        <span>{{ occupancyText(period.occupancyStatus) }}</span>
                      </div>
                    </div>
                    <span v-if="!occupancyBlocksForDate(row.date).length" class="lane-empty">当天没有任务占用</span>
                  </template>
                </div>
              </div>
            </template>
            <template v-else>
              <div
                v-for="day in weekdays"
                :key="day.value"
                class="gantt-row"
              >
                <div class="day-label">
                  <strong>{{ day.short }}</strong>
                  <small>{{ day.label }}</small>
                </div>
                <div
                  class="gantt-day-row"
                  :data-weekday="day.value"
                  @click="addAtPosition($event, day.value)"
                >
                  <div class="grid-lines" aria-hidden="true">
                    <span v-for="hour in 24" :key="hour" :class="{ major: hour % 4 === 1 }" />
                  </div>
                  <div
                    v-for="block in blocksForDay(day.value)"
                    :key="block._key"
                    class="schedule-block"
                    :class="[
                      `mode-${String(block.serviceMode).toLowerCase()}`,
                      {
                        selected: block._key === selectedKey,
                        overlap: overlapKeys.has(block._key)
                      }
                    ]"
                    :style="blockStyle(block)"
                    @click.stop="selectBlock(block)"
                    @pointerdown.stop="beginPointerEdit($event, block, 'move')"
                  >
                    <span
                      class="resize-handle left"
                      title="拖动调整开始时间"
                      @pointerdown.stop="beginPointerEdit($event, block, 'start')"
                    />
                    <div class="block-content">
                      <strong>{{ block.startTime }}–{{ block.endTime }}</strong>
                      <span>{{ modeText(block.serviceMode) }} · {{ block.weeklyCapacity }} 项/周</span>
                    </div>
                    <span
                      class="resize-handle right"
                      title="拖动调整结束时间"
                      @pointerdown.stop="beginPointerEdit($event, block, 'end')"
                    />
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>
        <div v-if="!rows.length" class="empty-overlay">
          <span>还没有维护日常可联系时段</span>
          <el-button type="primary" plain icon="Plus" @click="addDefaultBlock">新增第一个时段</el-button>
        </div>
      </div>

      <aside class="block-inspector">
        <template v-if="selectedBlock">
          <div class="inspector-heading">
            <div>
              <strong>{{ selectedBlock.startTime }}–{{ selectedBlock.endTime }}</strong>
              <span>{{ weekdays[selectedBlock.weekday - 1]?.label }}</span>
            </div>
            <el-button link type="danger" icon="Delete" @click="removeSelected">删除</el-button>
          </div>
          <el-alert
            v-if="overlapKeys.has(selectedBlock._key)"
            type="warning"
            :closable="false"
            show-icon
            title="该时段与同一天的其他时段重叠，请确认是否符合预期。"
            class="overlap-tip"
          />
          <el-form label-position="top" size="small">
            <el-form-item label="星期">
              <el-select v-model="selectedBlock.weekday" @change="commit">
                <el-option
                  v-for="day in weekdays"
                  :key="day.value"
                  :label="day.label"
                  :value="day.value"
                />
              </el-select>
            </el-form-item>
            <div class="time-fields">
              <el-form-item label="开始时间">
                <el-time-picker
                  v-model="selectedBlock.startTime"
                  value-format="HH:mm"
                  format="HH:mm"
                  :clearable="false"
                  @change="changeStartTime"
                />
              </el-form-item>
              <el-form-item label="结束时间">
                <el-time-picker
                  v-model="selectedBlock.endTime"
                  value-format="HH:mm"
                  format="HH:mm"
                  :clearable="false"
                  @change="changeEndTime"
                />
              </el-form-item>
            </div>
            <el-form-item label="服务方式">
              <el-select v-model="selectedBlock.serviceMode" @change="commit">
                <el-option label="远程" value="REMOTE" />
                <el-option label="现场" value="ONSITE" />
                <el-option label="远程或现场" value="BOTH" />
              </el-select>
            </el-form-item>
            <el-form-item label="该时段每周联系/承接上限">
              <el-input-number
                v-model="selectedBlock.weeklyCapacity"
                :min="1"
                :max="20"
                controls-position="right"
                style="width: 100%"
                @change="commit"
              />
            </el-form-item>
            <el-form-item label="生效日期">
              <el-date-picker
                v-model="selectedBlock.effectiveFrom"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="不填表示立即生效"
                style="width: 100%"
                @change="commit"
              />
            </el-form-item>
          </el-form>
          <el-button class="duplicate-button" plain icon="CopyDocument" @click="duplicateSelected">
            复制到下一个可用日期
          </el-button>
        </template>
        <el-empty v-else description="点击甘特图或新增时段后编辑" :image-size="64" />
      </aside>
    </div>
  </section>
</template>

<script setup name="ExpertScheduleGantt">
import { ElMessage } from "element-plus"

const props = defineProps({
  modelValue: {
    type: Array,
    default: () => []
  },
  dateMode: {
    type: Boolean,
    default: false
  },
  unavailablePeriods: {
    type: Array,
    default: () => []
  },
  occupancyPeriods: {
    type: Array,
    default: () => []
  }
})
const emit = defineEmits(["update:modelValue", "refresh-occupancy"])

const weekdays = [
  { label: "星期一", short: "一", value: 1 },
  { label: "星期二", short: "二", value: 2 },
  { label: "星期三", short: "三", value: 3 },
  { label: "星期四", short: "四", value: 4 },
  { label: "星期五", short: "五", value: 5 },
  { label: "星期六", short: "六", value: 6 },
  { label: "星期日", short: "日", value: 7 }
]
const hours = Array.from({ length: 24 }, (_, index) => index)
const dateLanes = [
  { key: "schedule", short: "日常", label: "可联系时段" },
  { key: "unavailable", short: "不可用", label: "临时不可用" },
  { key: "occupancy", short: "任务", label: "已占用时段" }
]
const MAX_RANGE_DAYS = 31
const rows = ref([])
const selectedKey = ref("")
const dateRange = ref(createDefaultDateRange())
let nextKey = 1

const dateRangeDays = computed(() => {
  if (!dateRange.value?.[0] || !dateRange.value?.[1]) return 0
  return daysBetween(dateRange.value[0], dateRange.value[1]) + 1
})

const dateRows = computed(() => selectedDates().flatMap(date => {
  const weekday = weekdayOf(date)
  return dateLanes.map((lane, laneIndex) => ({
    date,
    weekday,
    weekdayLabel: weekdayLabel(weekday),
    dateLabel: formatMonthDay(date),
    lane,
    laneIndex
  }))
}))

const selectedBlock = computed(() =>
  rows.value.find(item => item._key === selectedKey.value)
)
const overlapKeys = computed(() => {
  const result = new Set()
  for (let day = 1; day <= 7; day += 1) {
    const dayBlocks = blocksForDay(day)
    for (let left = 0; left < dayBlocks.length; left += 1) {
      for (let right = left + 1; right < dayBlocks.length; right += 1) {
        if (toMinutes(dayBlocks[left].startTime) < toMinutes(dayBlocks[right].endTime)
            && toMinutes(dayBlocks[right].startTime) < toMinutes(dayBlocks[left].endTime)) {
          result.add(dayBlocks[left]._key)
          result.add(dayBlocks[right]._key)
        }
      }
    }
  }
  return result
})

watch(() => props.modelValue, value => {
  const incoming = normalize(value)
  if (payloadJson(incoming) === payloadJson(rows.value)) {
    return
  }
  rows.value = incoming
  if (!rows.value.some(item => item._key === selectedKey.value)) {
    selectedKey.value = rows.value[0]?._key || ""
  }
}, { deep: true, immediate: true })

function normalize(value)
{
  return (Array.isArray(value) ? value : []).map(item => ({
    ...item,
    _key: item._key || `schedule-${nextKey++}`,
    weekday: clamp(Number(item.weekday || 1), 1, 7),
    startTime: normalizeTime(item.startTime, "09:00"),
    endTime: normalizeTime(item.endTime, "11:00"),
    serviceMode: item.serviceMode || "REMOTE",
    weeklyCapacity: Number(item.weeklyCapacity || 1),
    effectiveFrom: item.effectiveFrom || ""
  })).sort(compareBlocks)
}

function payload()
{
  return rows.value
    .map(({ _key, ...item }) => ({ ...item }))
    .sort(compareBlocks)
}

function payloadJson(value)
{
  return JSON.stringify(value
    .map(({ _key, ...item }) => item)
    .sort(compareBlocks))
}

function commit()
{
  enforceSelectedTimes()
  rows.value.sort(compareBlocks)
  emit("update:modelValue", payload())
}

function blocksForDay(day)
{
  return rows.value
    .filter(item => Number(item.weekday) === day)
    .sort(compareBlocks)
}

function scheduleBlocksForDate(date)
{
  return blocksForDay(weekdayOf(date))
    .filter(block => !block.effectiveFrom || block.effectiveFrom <= date)
}

function unavailableBlocksForDate(date)
{
  return periodsForDate(props.unavailablePeriods, "startTime", "endTime", date)
}

function occupancyBlocksForDate(date)
{
  return periodsForDate(props.occupancyPeriods, "serviceStart", "serviceEnd", date)
    .filter(item => item.occupancyStatus === "OCCUPIED"
      || item.occupancyStatus === "BLOCKED")
}

function periodsForDate(periods, startKey, endKey, date)
{
  return (Array.isArray(periods) ? periods : [])
    .filter(item => toDate(item[startKey]) && toDate(item[endKey]))
    .filter(item => toDate(item[startKey]).getTime() < dayEnd(date)
      && toDate(item[endKey]).getTime() > dayStart(date))
}

function dateBlockStyle(startValue, endValue, date)
{
  const start = toDate(startValue)
  const end = toDate(endValue)
  if (!start || !end) {
    return { display: "none" }
  }
  const clippedStart = Math.max(start.getTime(), dayStart(date))
  const clippedEnd = Math.min(end.getTime(), dayEnd(date))
  if (clippedEnd <= clippedStart) {
    return { display: "none" }
  }
  const startMinutes = (clippedStart - dayStart(date)) / 60000
  const endMinutes = (clippedEnd - dayStart(date)) / 60000
  return {
    left: `${startMinutes / 1440 * 100}%`,
    width: `${(endMinutes - startMinutes) / 1440 * 100}%`
  }
}

function dayStart(date)
{
  return new Date(`${date}T00:00:00`).getTime()
}

function dayEnd(date)
{
  return dayStart(date) + 24 * 60 * 60 * 1000
}

function toDate(value)
{
  if (!value) return null
  const date = new Date(String(value).replace(" ", "T"))
  return Number.isNaN(date.getTime()) ? null : date
}

function createDefaultDateRange()
{
  const start = new Date()
  const end = new Date(start)
  end.setDate(end.getDate() + 6)
  return [formatDateValue(start), formatDateValue(end)]
}

function normalizeDateRange(value)
{
  if (!value?.[0] || !value?.[1]) {
    dateRange.value = createDefaultDateRange()
    return
  }
  if (daysBetween(value[0], value[1]) + 1 > MAX_RANGE_DAYS) {
    const end = addDays(value[0], MAX_RANGE_DAYS - 1)
    dateRange.value = [value[0], formatDateValue(end)]
    ElMessage.warning("时间范围最多31天，已自动截断")
  }
}

function shiftDateRange(offset)
{
  const [start, end] = dateRange.value
  const span = daysBetween(start, end) + 1
  dateRange.value = [
    formatDateValue(addDays(start, offset * span)),
    formatDateValue(addDays(end, offset * span))
  ]
}

function selectToday()
{
  dateRange.value = createDefaultDateRange()
}

function selectedDates()
{
  const [start, end] = dateRange.value || []
  if (!start || !end) return []
  const result = []
  let cursor = new Date(`${start}T00:00:00`)
  const last = new Date(`${end}T00:00:00`)
  while (cursor <= last && result.length < MAX_RANGE_DAYS) {
    result.push(formatDateValue(cursor))
    cursor = addDays(cursor, 1)
  }
  return result
}

function daysBetween(start, end)
{
  return Math.round((new Date(`${end}T00:00:00`) - new Date(`${start}T00:00:00`))
    / (24 * 60 * 60 * 1000))
}

function addDays(value, days)
{
  const date = value instanceof Date ? new Date(value) : new Date(`${value}T00:00:00`)
  date.setDate(date.getDate() + days)
  return date
}

function weekdayOf(date)
{
  const day = new Date(`${date}T00:00:00`).getDay()
  return day === 0 ? 7 : day
}

function weekdayLabel(value)
{
  return weekdays.find(item => item.value === value)?.label || ""
}

function formatMonthDay(value)
{
  return String(value || "").slice(5)
}

function formatDateValue(date)
{
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, "0")
  const day = String(date.getDate()).padStart(2, "0")
  return `${year}-${month}-${day}`
}

function occupancyText(value)
{
  return {
    OCCUPIED: "已有任务",
    BLOCKED: "暂不可安排",
    FREE: "可以安排"
  }[value] || "任务占用"
}

function compareBlocks(left, right)
{
  return Number(left.weekday) - Number(right.weekday)
    || toMinutes(left.startTime) - toMinutes(right.startTime)
}

function blockStyle(block)
{
  const start = toMinutes(block.startTime)
  const end = Math.max(start + 15, toMinutes(block.endTime))
  return {
    left: `${start / 1440 * 100}%`,
    width: `${(end - start) / 1440 * 100}%`
  }
}

function selectBlock(block)
{
  selectedKey.value = block._key
}

function addDefaultBlock()
{
  if (props.dateMode) {
    const date = dateRange.value[0]
    addBlock(weekdayOf(date), 9 * 60, date)
    return
  }
  const day = weekdays
    .map(item => ({
      weekday: item.value,
      count: blocksForDay(item.value).length
    }))
    .sort((left, right) => left.count - right.count)[0].weekday
  addBlock(day, 9 * 60)
}

function addAtPosition(event, weekday, effectiveDate = null)
{
  const rect = event.currentTarget.getBoundingClientRect()
  const rawMinutes = (event.clientX - rect.left) / rect.width * 1440
  addBlock(weekday, Math.floor(rawMinutes / 30) * 30, effectiveDate)
}

function addBlock(weekday, startMinute, effectiveDate = null)
{
  let start = clamp(startMinute, 0, 23 * 60)
  const duration = 120
  let end = Math.min(24 * 60, start + duration)
  if (end - start < 30) {
    start = Math.max(0, end - 60)
    end = Math.min(24 * 60, start + 60)
  }
  const block = {
    _key: `schedule-${nextKey++}`,
    weekday,
    startTime: minutesToTime(start),
    endTime: minutesToTime(end),
    serviceMode: "REMOTE",
    weeklyCapacity: 1,
    effectiveFrom: effectiveDate || new Date().toISOString().slice(0, 10)
  }
  rows.value.push(block)
  selectedKey.value = block._key
  commit()
  return block
}

function beginPointerEdit(event, block, mode)
{
  if (event.button !== 0) return
  event.preventDefault()
  selectedKey.value = block._key
  const row = event.currentTarget.closest(".gantt-day-row")
  if (!row) return
  const rect = row.getBoundingClientRect()
  const originX = event.clientX
  const originStart = toMinutes(block.startTime)
  const originEnd = toMinutes(block.endTime)
  const duration = originEnd - originStart
  const onMove = moveEvent => {
    const delta = snapMinutes((moveEvent.clientX - originX) / rect.width * 1440)
    if (mode === "move") {
      const nextStart = clamp(originStart + delta, 0, 1440 - duration)
      block.startTime = minutesToTime(nextStart)
      block.endTime = minutesToTime(nextStart + duration)
      const targetRow = document.elementFromPoint(moveEvent.clientX, moveEvent.clientY)
        ?.closest(".gantt-day-row")
      if (targetRow?.dataset.weekday) {
        block.weekday = Number(targetRow.dataset.weekday)
      }
    }
    else if (mode === "start") {
      block.startTime = minutesToTime(
        clamp(originStart + delta, 0, Math.max(0, originEnd - 30))
      )
    }
    else {
      block.endTime = minutesToTime(
        clamp(originEnd + delta, Math.min(1440, originStart + 30), 1440)
      )
    }
  }
  const onUp = () => {
    window.removeEventListener("pointermove", onMove)
    window.removeEventListener("pointerup", onUp)
    document.body.classList.remove("gantt-dragging")
    commit()
  }
  document.body.classList.add("gantt-dragging")
  window.addEventListener("pointermove", onMove)
  window.addEventListener("pointerup", onUp, { once: true })
}

function changeStartTime(value)
{
  if (!selectedBlock.value) return
  selectedBlock.value.startTime = value
  if (toMinutes(value) >= toMinutes(selectedBlock.value.endTime)) {
    selectedBlock.value.endTime = minutesToTime(Math.min(1440, toMinutes(value) + 60))
  }
  commit()
}

function changeEndTime(value)
{
  if (!selectedBlock.value) return
  selectedBlock.value.endTime = value
  if (toMinutes(value) <= toMinutes(selectedBlock.value.startTime)) {
    selectedBlock.value.startTime = minutesToTime(Math.max(0, toMinutes(value) - 60))
  }
  commit()
}

function enforceSelectedTimes()
{
  for (const block of rows.value) {
    block.startTime = normalizeTime(block.startTime, "09:00")
    block.endTime = normalizeTime(block.endTime, "11:00")
    if (toMinutes(block.startTime) >= toMinutes(block.endTime)) {
      block.endTime = minutesToTime(Math.min(1440, toMinutes(block.startTime) + 30))
      if (toMinutes(block.startTime) >= toMinutes(block.endTime)) {
        block.startTime = minutesToTime(Math.max(0, toMinutes(block.endTime) - 30))
      }
    }
    block.weeklyCapacity = clamp(Number(block.weeklyCapacity || 1), 1, 20)
  }
}

function duplicateSelected()
{
  if (!selectedBlock.value) return
  const source = selectedBlock.value
  const used = new Set(rows.value.map(item => Number(item.weekday)))
  const weekday = weekdays
    .map(item => item.value)
    .find(day => !used.has(day)) || source.weekday
  const created = addBlock(weekday, toMinutes(source.startTime))
  created.endTime = source.endTime
  created.serviceMode = source.serviceMode
  created.weeklyCapacity = source.weeklyCapacity
  created.effectiveFrom = source.effectiveFrom
  selectedKey.value = created._key
  commit()
}

function removeSelected()
{
  if (!selectedBlock.value) return
  if (rows.value.length <= 1) {
    ElMessage.warning("至少保留一个日常可联系时段")
    return
  }
  const index = rows.value.findIndex(item => item._key === selectedKey.value)
  if (index < 0) return
  rows.value.splice(index, 1)
  selectedKey.value = rows.value[Math.min(index, rows.value.length - 1)]?._key || ""
  commit()
}

function modeText(mode)
{
  return {
    REMOTE: "远程",
    ONSITE: "现场",
    BOTH: "远程/现场"
  }[mode] || mode
}

function normalizeTime(value, fallback)
{
  const minutes = toMinutes(value)
  return Number.isFinite(minutes) ? minutesToTime(minutes) : fallback
}

function toMinutes(value)
{
  if (typeof value !== "string" || !value.includes(":")) return Number.NaN
  const [hour, minute] = value.split(":").map(Number)
  if (Number.isNaN(hour) || Number.isNaN(minute)) return Number.NaN
  return clamp(hour * 60 + minute, 0, 1440)
}

function minutesToTime(value)
{
  const minutes = clamp(Number(value || 0), 0, 1440)
  const hour = Math.floor(minutes / 60)
  const minute = minutes % 60
  return `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`
}

function snapMinutes(value)
{
  return Math.round(value / 15) * 15
}

function clamp(value, min, max)
{
  return Math.min(max, Math.max(min, value))
}
</script>

<style scoped lang="scss">
.schedule-gantt {
  padding: 18px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color);
}
.gantt-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 18px;
  margin-bottom: 16px;
  h3 {
    margin: 0 0 6px;
  }
  p {
    margin: 0;
    color: var(--el-text-color-secondary);
    line-height: 1.6;
  }
}
.toolbar-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  flex-shrink: 0;
}
.gantt-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 16px;
  align-items: start;
}
.gantt-panel {
  position: relative;
  min-width: 0;
}
.gantt-scroll {
  overflow-x: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}
.gantt-board {
  min-width: 760px;
}
.gantt-header,
.gantt-row {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
}
.gantt-board.date-mode {
  .gantt-header,
  .gantt-row {
    grid-template-columns: 112px minmax(0, 1fr);
  }
  .gantt-row {
    min-height: 56px;
  }
  .gantt-day-row {
    min-height: 56px;
  }
  .gantt-row.date-break {
    border-top: 2px solid var(--el-border-color);
  }
}
.gantt-header {
  min-height: 42px;
  border-bottom: 1px solid var(--el-border-color);
  background: var(--el-fill-color-lighter);
}
.day-heading,
.day-label {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 0 12px;
  border-right: 1px solid var(--el-border-color);
}
.day-heading {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.timeline-heading {
  display: grid;
  grid-template-columns: repeat(24, minmax(0, 1fr));
}
.hour-heading {
  position: relative;
  min-width: 0;
  border-left: 1px solid var(--el-border-color-lighter);
  span {
    position: absolute;
    top: 12px;
    left: 3px;
    color: var(--el-text-color-secondary);
    font-size: 10px;
    white-space: nowrap;
  }
}
.gantt-row {
  min-height: 62px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  &:last-child {
    border-bottom: 0;
  }
}
.day-label {
  background: var(--el-fill-color-extra-light);
  strong {
    font-size: 15px;
  }
  small {
    color: var(--el-text-color-secondary);
    line-height: 1.4;
  }
}
.gantt-day-row {
  position: relative;
  min-width: 0;
  min-height: 62px;
  overflow: hidden;
  cursor: crosshair;
}
.grid-lines {
  position: absolute;
  inset: 0;
  display: grid;
  grid-template-columns: repeat(24, minmax(0, 1fr));
  pointer-events: none;
  span {
    border-left: 1px solid var(--el-border-color-lighter);
    &.major {
      border-left-color: var(--el-border-color);
    }
  }
}
.schedule-block {
  position: absolute;
  top: 9px;
  bottom: 9px;
  z-index: 2;
  min-width: 22px;
  display: flex;
  align-items: stretch;
  overflow: hidden;
  border-radius: 7px;
  color: #17384c;
  background: color-mix(in srgb, var(--el-color-primary) 24%, var(--el-bg-color));
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--el-color-primary) 42%, transparent);
  cursor: grab;
  touch-action: none;
  &.mode-onsite {
    color: #5f3c0b;
    background: color-mix(in srgb, var(--el-color-warning) 28%, var(--el-bg-color));
    box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--el-color-warning) 48%, transparent);
  }
  &.mode-both {
    color: #1f513c;
    background: color-mix(in srgb, var(--el-color-success) 25%, var(--el-bg-color));
    box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--el-color-success) 45%, transparent);
  }
  &.selected {
    z-index: 3;
    box-shadow:
      inset 0 0 0 2px var(--el-color-primary),
      0 4px 12px color-mix(in srgb, var(--el-color-primary) 22%, transparent);
  }
  &.overlap::after {
    content: "";
    position: absolute;
    inset: 0;
    pointer-events: none;
    background: repeating-linear-gradient(
      135deg,
      transparent 0 7px,
      color-mix(in srgb, var(--el-color-warning) 24%, transparent) 7px 10px
    );
  }
}

.timeline-block {
  position: absolute;
  top: 12px;
  bottom: 12px;
  z-index: 2;
  min-width: 24px;
  display: flex;
  overflow: hidden;
  border-radius: 7px;
  box-shadow: inset 0 0 0 1px currentColor;
  .block-content {
    padding: 0 8px;
  }
  strong {
    font-size: 11px;
  }
  span {
    font-size: 10px;
  }
}
.unavailable-block {
  color: #8a4b10;
  background: color-mix(in srgb, var(--el-color-danger) 16%, var(--el-bg-color));
}
.occupancy-block {
  color: #174f7a;
  background: color-mix(in srgb, var(--el-color-primary) 18%, var(--el-bg-color));
  &.blocked {
    color: #8a4b10;
    background: color-mix(in srgb, var(--el-color-warning) 20%, var(--el-bg-color));
  }
}
.lane-empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: var(--el-text-color-placeholder);
  font-size: 11px;
  pointer-events: none;
}
.block-content {
  min-width: 0;
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: center;
  padding: 0 9px;
  pointer-events: none;
  strong,
  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  strong {
    font-size: 11px;
  }
  span {
    margin-top: 2px;
    font-size: 10px;
    opacity: .8;
  }
}
.resize-handle {
  position: relative;
  z-index: 2;
  width: 7px;
  flex-shrink: 0;
  cursor: ew-resize;
  background: color-mix(in srgb, currentColor 16%, transparent);
  opacity: 0;
  transition: opacity .15s ease;
}
.schedule-block:hover .resize-handle,
.schedule-block.selected .resize-handle {
  opacity: 1;
}
.block-inspector {
  padding: 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
}
.inspector-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  strong,
  span {
    display: block;
  }
  span {
    margin-top: 4px;
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
}
.overlap-tip {
  margin-bottom: 12px;
}
.time-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  :deep(.el-date-editor) {
    width: 100%;
  }
}
.duplicate-button {
  width: 100%;
}
.empty-overlay {
  position: absolute;
  inset: 42px 0 0 88px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--el-text-color-secondary);
  pointer-events: none;
  .el-button {
    pointer-events: auto;
  }
}
:global(body.gantt-dragging) {
  cursor: grabbing;
  user-select: none;
}
@media (max-width: 1100px) {
  .gantt-layout {
    grid-template-columns: 1fr;
  }
  .block-inspector {
    max-width: none;
  }
}
@media (max-width: 720px) {
  .schedule-gantt {
    padding: 12px;
  }
  .gantt-toolbar {
    flex-direction: column;
  }
  .toolbar-actions {
    flex-wrap: wrap;
  }
}
</style>
