import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截器
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  response => {
    // 对于 blob 类型响应（如文件下载），直接返回完整的 response，不做 JSON 解析
    if (response.config.responseType === 'blob') {
      return response
    }
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      if (res.code === 401) {
        localStorage.clear()
        router.push('/login')
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  error => {
    // 对于 blob 类型响应，尝试解析错误 body 中的 JSON 消息
    if (error.config?.responseType === 'blob' && error.response?.data instanceof Blob) {
      // 先不在这里弹错误消息，让调用方自行处理
      return Promise.reject(error)
    }
    if (error.response?.status === 401) {
      localStorage.clear()
      router.push('/login')
    }
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default request
