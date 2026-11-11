import request from '@/utils/request'

// 查询专家信息列表
export function listExpertInfo(query) {
  return request({
    url: '/expert/info/list',
    method: 'get',
    params: query
  })
}

// 查询专家信息详细
export function getExpertInfo(expertId) {
  return request({
    url: '/expert/info/' + expertId,
    method: 'get'
  })
}

// 新增专家信息
export function addExpertInfo(data) {
  return request({
    url: '/expert/info',
    method: 'post',
    data: data
  })
}

// 修改专家信息
export function updateExpertInfo(data) {
  return request({
    url: '/expert/info',
    method: 'put',
    data: data
  })
}

// 删除专家信息
export function delExpertInfo(expertId) {
  return request({
    url: '/expert/info/' + expertId,
    method: 'delete'
  })
}
