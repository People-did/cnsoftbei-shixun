<template>
  <div class="annotation-viewer">
    <!-- 顶部统计条 -->
    <div class="stats-bar">
      <div class="stat-item" v-for="s in severityStats" :key="s.label">
        <span :class="['stat-dot', `dot-${s.severity}`]"></span>
        <span class="stat-label">{{ s.label }}</span>
        <span class="stat-count">{{ s.count }}</span>
      </div>
    </div>

    <div class="viewer-body">
      <!-- 左侧：带高亮的原文 -->
      <div class="text-panel" ref="textPanelRef">
        <div class="panel-title">📄 报告原文</div>
        <div class="report-text" ref="reportTextRef">
          <template v-for="(seg, idx) in textSegments" :key="idx">
            <el-popover
              v-if="seg.annotation"
              placement="top-start"
              :width="360"
              trigger="hover"
              :show-after="150"
              popper-class="annotation-popover"
            >
              <template #reference>
                <span
                  :class="['highlighted-text', `hl-${seg.annotation.severity}`, { 'active-card': activeCardId === seg.annotation.id }]"
                  @click="scrollToCard(seg.annotation.id)"
                >
                  {{ seg.text }}
                </span>
              </template>
              <div class="popover-body">
                <div class="popover-tags">
                  <el-tag :type="severityTagType(seg.annotation.severity)" size="small">{{ severityLabel(seg.annotation.severity) }}</el-tag>
                  <el-tag type="info" size="small" v-if="seg.annotation.category">{{ seg.annotation.category }}</el-tag>
                </div>
                <div class="popover-highlight">"{{ truncateText(seg.annotation.highlightedText, 100) }}"</div>
                <p class="popover-comment">{{ seg.annotation.comment }}</p>
                <p class="popover-suggestion" v-if="seg.annotation.suggestion">
                  <strong>💡 修改建议：</strong>{{ seg.annotation.suggestion }}
                </p>
              </div>
            </el-popover>
            <span v-else>{{ seg.text }}</span>
          </template>
        </div>
      </div>

      <!-- 右侧：批注卡片列表 -->
      <div class="cards-panel" ref="cardsPanelRef">
        <div class="panel-title">📝 AI批注列表 ({{ annotations.length }}条)</div>
        <div class="cards-list" v-if="annotations.length > 0">
          <div
            v-for="ann in annotations"
            :key="ann.id"
            :ref="el => setCardRef(ann.id, el)"
            :class="['annotation-card', `card-${ann.severity}`, { 'active': activeCardId === ann.id }]"
            @click="scrollToText(ann)"
          >
            <div class="card-top">
              <el-tag :type="severityTagType(ann.severity)" size="small">{{ severityLabel(ann.severity) }}</el-tag>
              <el-tag type="info" size="small" v-if="ann.category">{{ ann.category }}</el-tag>
            </div>
            <blockquote class="card-quote">"{{ truncateText(ann.highlightedText, 80) }}"</blockquote>
            <p class="card-comment">{{ ann.comment }}</p>
            <p class="card-suggestion" v-if="ann.suggestion"><strong>💡 </strong>{{ ann.suggestion }}</p>
          </div>
        </div>
        <el-empty v-else description="暂无批注，点击 AI智能批注 按钮生成" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'

const props = defineProps({
  reportContent: { type: String, default: '' },
  annotations: { type: Array, default: () => [] }
})

const activeCardId = ref(null)
const textPanelRef = ref(null)
const reportTextRef = ref(null)
const cardsPanelRef = ref(null)
const cardRefs = {}

function setCardRef(id, el) {
  if (el) cardRefs[id] = el
}

// 严重程度统计
const severityStats = computed(() => {
  const map = { error: 0, warning: 0, info: 0, suggestion: 0 }
  props.annotations.forEach(a => { if (map[a.severity] !== undefined) map[a.severity]++ })
  return [
    { severity: 'error', label: '严重', count: map.error },
    { severity: 'warning', label: '需改进', count: map.warning },
    { severity: 'info', label: '建议', count: map.info },
    { severity: 'suggestion', label: '优化', count: map.suggestion }
  ]
})

// 将原文按批注位置切分为文本段
const textSegments = computed(() => {
  const text = props.reportContent || ''
  if (!text || !props.annotations.length) return [{ text, annotation: null }]

  // 筛选有效批注（有明确位置的）
  const validAnnotations = props.annotations
    .filter(a => a.startPos != null && a.endPos != null && a.startPos < a.endPos)
    .sort((a, b) => a.startPos - b.startPos)

  if (!validAnnotations.length) return [{ text, annotation: null }]

  const segments = []
  let cursor = 0

  // 解决重叠批注：对于每个位置，取严重程度最高的批注
  const resolved = resolveOverlaps(validAnnotations)

  for (const ann of resolved) {
    // 批注之前的普通文本
    if (ann.startPos > cursor) {
      segments.push({ text: text.substring(cursor, ann.startPos), annotation: null })
    }
    // 批注文本
    segments.push({
      text: text.substring(ann.startPos, Math.min(ann.endPos, text.length)),
      annotation: ann
    })
    cursor = Math.max(cursor, ann.endPos)
  }

  // 最后剩余的文本
  if (cursor < text.length) {
    segments.push({ text: text.substring(cursor), annotation: null })
  }

  return segments
})

// 解决重叠批注：按严重程度优先级裁剪
function resolveOverlaps(annotations) {
  const priority = { error: 4, warning: 3, info: 2, suggestion: 1 }
  const result = []
  // 简单策略：按优先级排序，高优先级覆盖低优先级
  const sorted = [...annotations].sort((a, b) => {
    const p = (priority[b.severity] || 0) - (priority[a.severity] || 0)
    if (p !== 0) return p
    return a.startPos - b.startPos
  })

  const occupied = []
  for (const ann of sorted) {
    // 检查是否与已占用的区域重叠
    let overlapped = false
    for (const occ of occupied) {
      if (ann.startPos < occ.end && ann.endPos > occ.start) {
        overlapped = true
        break
      }
    }
    if (!overlapped) {
      result.push(ann)
      occupied.push({ start: ann.startPos, end: ann.endPos })
    }
  }
  // 按原文位置重新排序
  return result.sort((a, b) => a.startPos - b.startPos)
}

// 滚动到批注卡片
function scrollToCard(id) {
  activeCardId.value = id
  const card = cardRefs[id]
  if (card && cardsPanelRef.value) {
    card.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }
}

// 滚动到原文位置
function scrollToText(ann) {
  activeCardId.value = ann.id
  const el = reportTextRef.value
  if (!el || ann.startPos == null) return
  // 找到对应的高亮span
  const spans = el.querySelectorAll('.highlighted-text')
  // 通过文本内容匹配
  for (const span of spans) {
    if (span.textContent.includes(ann.highlightedText?.substring(0, 15) || '')) {
      span.scrollIntoView({ behavior: 'smooth', block: 'center' })
      span.classList.add('flash')
      setTimeout(() => span.classList.remove('flash'), 1500)
      return
    }
  }
}

function severityLabel(s) {
  const map = { error: '严重', warning: '需改进', info: '建议', suggestion: '优化' }
  return map[s] || '批注'
}

function severityTagType(s) {
  const map = { error: 'danger', warning: 'warning', info: 'primary', suggestion: 'success' }
  return map[s] || 'info'
}

function truncateText(t, max) {
  if (!t) return ''
  return t.length > max ? t.substring(0, max) + '...' : t
}

// 当批注更新时重置状态
watch(() => props.annotations, () => {
  activeCardId.value = null
})
</script>

<style scoped>
.annotation-viewer {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 500px;
}

/* 顶部统计条 */
.stats-bar {
  display: flex;
  gap: 24px;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 8px;
  margin-bottom: 16px;
  flex-shrink: 0;
}
.stat-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #606266;
}
.stat-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.dot-error { background: #f56c6c; }
.dot-warning { background: #e6a23c; }
.dot-info { background: #409eff; }
.dot-suggestion { background: #67c23a; }
.stat-count {
  font-weight: bold;
  color: #303133;
}

/* 主体区域 */
.viewer-body {
  display: flex;
  gap: 16px;
  flex: 1;
  min-height: 0;
}

.panel-title {
  font-size: 15px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 2px solid #e4e7ed;
  flex-shrink: 0;
}

/* 左侧原文面板 */
.text-panel {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  min-width: 0;
}
.report-text {
  font-size: 14px;
  line-height: 2;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 批注高亮 */
.highlighted-text {
  cursor: pointer;
  border-radius: 3px;
  padding: 1px 2px;
  transition: all 0.2s;
}
.hl-error {
  background: #fde2e2;
  border-bottom: 2px wavy #f56c6c;
}
.hl-warning {
  background: #fdf6ec;
  border-bottom: 2px dashed #e6a23c;
}
.hl-info {
  background: #ecf5ff;
  border-bottom: 2px solid #409eff;
}
.hl-suggestion {
  background: #f0f9eb;
  border-bottom: 2px dotted #67c23a;
}
.highlighted-text:hover {
  filter: brightness(0.92);
  transform: translateY(-1px);
}
.highlighted-text.active-card {
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.4);
  z-index: 2;
  position: relative;
}
.highlighted-text.flash {
  animation: flash-highlight 0.5s ease-in-out 3;
}
@keyframes flash-highlight {
  0%, 100% { filter: brightness(1); }
  50% { filter: brightness(0.8); }
}

/* 右侧批注面板 */
.cards-panel {
  width: 380px;
  flex-shrink: 0;
  overflow-y: auto;
  padding: 16px;
  background: #fafafa;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
}
.cards-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.annotation-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 14px;
  cursor: pointer;
  transition: all 0.2s;
}
.annotation-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  transform: translateX(-2px);
}
.annotation-card.active {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.25);
}
.card-error { border-left: 4px solid #f56c6c; }
.card-warning { border-left: 4px solid #e6a23c; }
.card-info { border-left: 4px solid #409eff; }
.card-suggestion { border-left: 4px solid #67c23a; }

.card-top {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
}
.card-quote {
  margin: 0 0 8px 0;
  padding: 6px 10px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
  color: #909399;
  font-style: italic;
  border-left: 3px solid #dcdfe6;
}
.card-comment {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
  margin: 0 0 6px 0;
}
.card-suggestion {
  font-size: 12px;
  color: #67c23a;
  line-height: 1.5;
  margin: 0;
  background: #f0f9eb;
  padding: 6px 8px;
  border-radius: 4px;
}

/* Popover 样式 */
.popover-body {
  font-size: 13px;
}
.popover-tags {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
}
.popover-highlight {
  background: #f5f7fa;
  padding: 6px 10px;
  border-radius: 4px;
  font-size: 12px;
  color: #909399;
  font-style: italic;
  margin-bottom: 8px;
  border-left: 3px solid #dcdfe6;
}
.popover-comment {
  color: #606266;
  line-height: 1.6;
  margin: 0 0 6px 0;
}
.popover-suggestion {
  color: #67c23a;
  line-height: 1.5;
  margin: 0;
  background: #f0f9eb;
  padding: 6px 8px;
  border-radius: 4px;
}
</style>
