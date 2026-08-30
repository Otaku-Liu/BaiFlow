import http from './http'

/** 上传记录：分页 + 时间范围 + 文件名 + 来源（WEB/ANDROID）+ admin 可传 userId */
export function listUploadRecords({ start, end, fileName, source, userId, page = 1, size = 20 } = {}) {
  return http.get('/upload-records', { params: cleanParams({ start, end, fileName, source, userId, page, size }) })
}

/** 下载记录：分页 + 时间范围 + 文件名 + 来源（CLIENT/SHARE）+ admin 可传 userId */
export function listDownloadRecords({ start, end, fileName, source, userId, page = 1, size = 20 } = {}) {
  return http.get('/download-records', { params: cleanParams({ start, end, fileName, source, userId, page, size }) })
}

/** 过滤空参（undefined/null/空串不发送） */
function cleanParams(obj) {
  const out = {}
  Object.entries(obj).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') out[k] = v
  })
  return out
}
