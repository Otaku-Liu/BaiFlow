import http from './http'

/** 分页列出笔记，支持关键字搜索（标题/正文） */
export function listNotes({ page = 1, size = 50, keyword }, viewUserId) {
  const params = { page, size }
  if (keyword) params.keyword = keyword
  if (viewUserId) params.viewUserId = viewUserId
  return http.get('/notes', { params })
}

/** 新建笔记 */
export function createNote({ title, content }) {
  return http.post('/notes', { title, content })
}

/** 查询笔记详情（含 Markdown 正文） */
export function getNote(id, viewUserId) {
  const params = {}
  if (viewUserId) params.viewUserId = viewUserId
  return http.get(`/notes/${id}`, { params })
}

/** 更新笔记（baseUpdatedAt 为乐观并发依据：早于服务端当前值则返回 NOTE_CONFLICT） */
export function updateNote(id, { title, content, baseUpdatedAt }, viewUserId) {
  const params = {}
  if (viewUserId) params.viewUserId = viewUserId
  const body = { title, content }
  if (baseUpdatedAt) body.baseUpdatedAt = baseUpdatedAt
  return http.patch(`/notes/${id}`, body, { params })
}

/** 删除笔记（软删除） */
export function deleteNote(id, viewUserId) {
  const params = {}
  if (viewUserId) params.viewUserId = viewUserId
  return http.delete(`/notes/${id}`, { params })
}

/** 查询笔记阅读进度 */
export function getNoteProgress(id) {
  return http.get(`/notes/${id}/progress`)
}

/** 保存笔记阅读进度（滚动百分比 0~1） */
export function saveNoteProgress(id, positionValue) {
  return http.put(`/notes/${id}/progress`, { positionValue })
}
