<template>
  <div class="page-container">
    <h2 class="page-title">统计报表</h2>
    
    <div class="search-form">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="选择班级">
          <el-select v-model="searchForm.classId" placeholder="请选择班级" filterable @change="handleClassChange" style="width: 260px">
            <el-option v-for="c in classes" :key="c.id" :label="c.className" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadStatistics">查询</el-button>
          <el-button type="success" @click="handleExport" :loading="exporting">
            <el-icon><Download /></el-icon>
            导出成绩
          </el-button>
        </el-form-item>
      </el-form>
    </div>
    
    <el-row :gutter="20" v-if="statistics">
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon" style="background: #409eff">
            <el-icon><User /></el-icon>
          </div>
          <div class="stat-info">
            <p class="stat-label">班级人数</p>
            <p class="stat-value">{{ statistics.totalStudents }}</p>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon" style="background: #67c23a">
            <el-icon><Document /></el-icon>
          </div>
          <div class="stat-info">
            <p class="stat-label">提交报告</p>
            <p class="stat-value">{{ statistics.totalReports }}</p>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon" style="background: #e6a23c">
            <el-icon><Star /></el-icon>
          </div>
          <div class="stat-info">
            <p class="stat-label">已评价</p>
            <p class="stat-value">{{ statistics.evaluatedReports }}</p>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon" style="background: #f56c6c">
            <el-icon><TrendCharts /></el-icon>
          </div>
          <div class="stat-info">
            <p class="stat-label">评价完成率</p>
            <p class="stat-value">{{ completionRate }}%</p>
          </div>
        </div>
      </el-col>
    </el-row>
    
    <el-row :gutter="20" style="margin-top: 20px" v-if="statistics">
      <el-col :span="6">
        <div class="score-stat-card">
          <p class="label">平均分</p>
          <p class="value primary">{{ statistics.avgScore }}</p>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="score-stat-card">
          <p class="label">最高分</p>
          <p class="value success">{{ statistics.maxScore }}</p>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="score-stat-card">
          <p class="label">最低分</p>
          <p class="value danger">{{ statistics.minScore }}</p>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="score-stat-card">
          <p class="label">优良率</p>
          <p class="value warning">{{ excellentRate }}%</p>
        </div>
      </el-col>
    </el-row>
    
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="12">
        <div class="chart-container">
          <div class="chart-title">分数段分布</div>
          <div ref="barChartRef" style="height: 300px"></div>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="chart-container">
          <div class="chart-title">成绩分布饼图</div>
          <div ref="pieChartRef" style="height: 300px"></div>
        </div>
      </el-col>
    </el-row>
    
    <!-- 学生端：多维度能力雷达图 -->
    <div class="radar-container" style="margin-top: 20px" v-if="isStudent && radarData">
      <div class="chart-title" style="display: flex; align-items: center; gap: 8px;">
        <el-icon color="#7c3aed" :size="20"><DataAnalysis /></el-icon>
        多维度能力雷达图
        <el-tag size="small" type="info">基于 {{ radarData.evaluatedReports }} 份AI评价生成</el-tag>
      </div>
      <div class="radar-chart-wrapper">
        <div ref="radarChartRef" style="height: 420px; width: 100%;"></div>
        <div class="radar-legend">
          <div class="legend-item" v-for="dim in radarData.dimensions" :key="dim.name">
            <div class="legend-dot" :style="{ background: getRadarColor(dim.name) }"></div>
            <span class="legend-name">{{ dim.name }}</span>
            <span class="legend-score">{{ dim.score }}分</span>
          </div>
        </div>
      </div>
    </div>
    <div class="radar-container radar-loading" style="margin-top: 20px" v-if="isStudent && radarLoading">
      <div class="chart-title" style="display: flex; align-items: center; gap: 8px;">
        <el-icon color="#7c3aed" :size="20"><DataAnalysis /></el-icon>
        多维度能力雷达图
      </div>
      <div style="text-align: center; padding: 40px 0; color: #909399;">
        <el-icon class="is-loading" :size="32"><Loading /></el-icon>
        <p style="margin-top: 12px;">正在生成能力画像...</p>
      </div>
    </div>
    
    <div class="table-container" style="margin-top: 20px" v-if="statistics">
      <div class="table-title">成绩明细</div>
      <el-table :data="scoreList" stripe>
        <el-table-column prop="student_name" label="学生" />
        <el-table-column prop="class_name" label="班级" />
        <el-table-column prop="report_title" label="报告" show-overflow-tooltip />
        <el-table-column label="完整性" prop="completeness_score" width="100" />
        <el-table-column label="规范性" prop="specification_score" width="100" />
        <el-table-column label="知识点" prop="knowledge_score" width="100" />
        <el-table-column label="总分" prop="total_score" width="100">
          <template #default="{ row }">
            <el-tag :type="getScoreType(row.total_score)">{{ row.total_score }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="evaluate_time" label="评价时间" width="180" />
      </el-table>
    </div>
    
    <el-empty v-if="!statistics" description="请选择班级查看统计信息" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { getClassStatistics, exportScores, getStudentRadar } from '../api/statistics'
import { getAllClasses } from '../api/class'
import { useUserStoreHook } from '../stores'

const userStore = useUserStoreHook()
const isStudent = computed(() => userStore.userInfo?.role === 3)

const barChartRef = ref()
const pieChartRef = ref()
const radarChartRef = ref()
let barChart = null
let pieChart = null
let radarChart = null

const loading = ref(false)
const exporting = ref(false)
const classes = ref([])
const statistics = ref(null)
const scoreList = ref([])
const radarData = ref(null)
const radarLoading = ref(false)

const searchForm = reactive({ classId: null })

const completionRate = computed(() => {
  if (!statistics.value || !statistics.value.totalReports) return 0
  return Math.round(statistics.value.evaluatedReports / statistics.value.totalReports * 100)
})

const excellentRate = computed(() => {
  if (!statistics.value || !statistics.value.evaluatedReports) return 0
  const excellent = (statistics.value.scoreDistribution || [])
    .filter(d => ['80-90分', '90-100分'].includes(d.range))
    .reduce((sum, d) => sum + d.count, 0)
  return Math.round(excellent / statistics.value.evaluatedReports * 100)
})

const getScoreType = (score) => {
  if (!score) return 'info'
  if (score >= 90) return 'success'
  if (score >= 80) return ''
  if (score >= 60) return 'warning'
  return 'danger'
}

const initBarChart = () => {
  if (!barChartRef.value || !statistics.value) return
  
  barChart = echarts.init(barChartRef.value)
  const distribution = statistics.value.scoreDistribution || []
  
  const option = {
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: distribution.map(d => d.range)
    },
    yAxis: { type: 'value', name: '人数' },
    series: [{
      type: 'bar',
      data: distribution.map(d => d.count),
      itemStyle: {
        color: (params) => {
          const colors = ['#f56c6c', '#e6a23c', '#409eff', '#67c23a', '#909399']
          return colors[params.dataIndex]
        }
      },
      barWidth: '50%',
      label: { show: true, position: 'top' }
    }]
  }
  barChart.setOption(option)
}

const initPieChart = () => {
  if (!pieChartRef.value || !statistics.value) return
  
  pieChart = echarts.init(pieChartRef.value)
  const distribution = statistics.value.scoreDistribution || []
  
  const option = {
    tooltip: { trigger: 'item', formatter: '{b}: {c}人 ({d}%)' },
    legend: { bottom: '5%', left: 'center' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      avoidLabelOverlap: false,
      label: { show: true, formatter: '{b}\n{c}人' },
      data: distribution.map((d, i) => {
        const colors = ['#f56c6c', '#e6a23c', '#409eff', '#67c23a', '#909399']
        return { value: d.count, name: d.range, itemStyle: { color: colors[i] } }
      })
    }]
  }
  pieChart.setOption(option)
}

const RADAR_COLORS = {
  '代码能力': '#3b82f6',
  '文档能力': '#10b981',
  '设计能力': '#f59e0b',
  '测试能力': '#8b5cf6',
  '团队协作': '#06b6d4',
  '综合总分': '#ef4444'
}

const getRadarColor = (name) => {
  return RADAR_COLORS[name] || '#7c3aed'
}

const loadRadarData = async () => {
  if (!isStudent.value) return
  
  radarLoading.value = true
  try {
    const res = await getStudentRadar()
    if (res.data && res.data.dimensions) {
      radarData.value = res.data
      await nextTick()
      initRadarChart()
    }
  } catch (error) {
    console.error('加载雷达图数据失败:', error)
  } finally {
    radarLoading.value = false
  }
}

const initRadarChart = () => {
  if (!radarChartRef.value || !radarData.value) return
  
  if (radarChart) radarChart.dispose()
  radarChart = echarts.init(radarChartRef.value)
  
  const dimensions = radarData.value.dimensions || []
  const indicator = dimensions.map(d => ({
    name: d.name,
    max: d.fullMark || 100
  }))
  const scores = dimensions.map(d => d.score)
  
  const option = {
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        if (params.name) {
          return `<b>${params.name}</b><br/>得分: ${params.value} / ${params.name === '综合总分' ? '100' : '100'}`
        }
        return ''
      }
    },
    legend: {
      bottom: 0,
      data: ['能力画像'],
      textStyle: { color: '#606266' }
    },
    radar: {
      center: ['50%', '48%'],
      radius: '65%',
      indicator: indicator,
      axisName: {
        color: '#303133',
        fontSize: 13,
        fontWeight: 'bold',
        borderRadius: 3,
        padding: [3, 5]
      },
      splitArea: {
        areaStyle: {
          color: ['rgba(124, 58, 237, 0.02)', 'rgba(124, 58, 237, 0.04)', 
                  'rgba(124, 58, 237, 0.06)', 'rgba(124, 58, 237, 0.08)']
        }
      },
      splitLine: {
        lineStyle: { color: 'rgba(124, 58, 237, 0.2)' }
      },
      axisLine: {
        lineStyle: { color: 'rgba(124, 58, 237, 0.3)' }
      }
    },
    series: [{
      type: 'radar',
      name: '能力画像',
      data: [{
        value: scores,
        name: '能力画像',
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(124, 58, 237, 0.35)' },
              { offset: 1, color: 'rgba(59, 130, 246, 0.15)' }
            ]
          }
        },
        lineStyle: {
          color: '#7c3aed',
          width: 2.5
        },
        itemStyle: {
          color: '#7c3aed',
          borderColor: '#fff',
          borderWidth: 2
        },
        symbol: 'circle',
        symbolSize: 8
      }]
    }]
  }
  radarChart.setOption(option)
}

const loadStatistics = async () => {
  if (!searchForm.classId) {
    ElMessage.warning('请选择班级')
    return
  }
  
  loading.value = true
  try {
    const res = await getClassStatistics(searchForm.classId)
    statistics.value = res.data
    scoreList.value = res.data.scoreList || []
    
    setTimeout(() => {
      initBarChart()
      initPieChart()
    }, 100)
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
  
  // 学生端：同时加载雷达图
  if (isStudent.value) {
    loadRadarData()
  }
}

const handleClassChange = () => {
  if (searchForm.classId) {
    loadStatistics()
  }
}

const handleExport = async () => {
  if (!searchForm.classId) {
    ElMessage.warning('请先选择班级')
    return
  }
  
  exporting.value = true
  try {
    const res = await exportScores(searchForm.classId)
    const blobData = res.data || res
    const blob = new Blob([blobData], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${statistics.value.className}_成绩单.xlsx`
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

const loadClasses = async () => {
  try {
    if (isStudent.value) {
      // 学生端：从 userInfo 获取自己班级，然后从班级列表匹配名称
      const classId = userStore.userInfo?.classId
      if (classId) {
        const res = await getAllClasses()
        const allClasses = res.data || []
        const myClass = allClasses.find(c => c.id === classId)
        if (myClass) {
          classes.value = [myClass]
        } else {
          classes.value = [{ id: classId, className: '我的班级' }]
        }
        searchForm.classId = classId
        loadStatistics()
      } else {
        classes.value = []
      }
    } else {
      const res = await getAllClasses()
      classes.value = res.data || []
    }
  } catch (error) {
    console.error(error)
  }
}

onMounted(() => {
  loadClasses()
  window.addEventListener('resize', () => {
    barChart?.resize()
    pieChart?.resize()
    radarChart?.resize()
  })
})

onUnmounted(() => {
  barChart?.dispose()
  pieChart?.dispose()
  radarChart?.dispose()
})
</script>

<style scoped>
.stat-card {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.stat-icon {
  width: 50px;
  height: 50px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
  
  .el-icon {
    font-size: 24px;
    color: #fff;
  }
}

.stat-info {
  .stat-label {
    font-size: 14px;
    color: #909399;
    margin-bottom: 4px;
  }
  
  .stat-value {
    font-size: 24px;
    font-weight: bold;
    color: #303133;
  }
}

.score-stat-card {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
  text-align: center;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  
  .label {
    font-size: 14px;
    color: #909399;
    margin-bottom: 8px;
  }
  
  .value {
    font-size: 32px;
    font-weight: bold;
    
    &.primary { color: #409eff; }
    &.success { color: #67c23a; }
    &.warning { color: #e6a23c; }
    &.danger { color: #f56c6c; }
  }
}

.chart-container {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.chart-title, .table-title {
  font-size: 16px;
  font-weight: 500;
  margin-bottom: 16px;
  color: #303133;
}

.table-container {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.radar-container {
  background: #fff;
  padding: 24px;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.radar-chart-wrapper {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-top: 8px;
}

.radar-chart-wrapper > div:first-child {
  flex: 1;
}

.radar-legend {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 130px;
  padding-left: 8px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.legend-name {
  font-size: 13px;
  color: #606266;
  flex: 1;
}

.legend-score {
  font-size: 13px;
  font-weight: bold;
  color: #303133;
  min-width: 40px;
  text-align: right;
}

.radar-loading {
  .chart-title {
    margin-bottom: 0;
  }
}
</style>