/**
 * 格式化日期为 yyyy-MM-dd HH:mm:ss。
 * @param {string|Date|null|undefined} val ISO 时间字符串或 Date 对象
 * @returns {string} 格式化后的时间字符串，空值返回 '-'
 *
 * 约定：DB 存 UTC+8 墙钟，后端返回「无时区」ISO 字符串（YYYY-MM-DDTHH:mm:ss）。
 * 无时区字符串**直接按字面 +8 墙钟格式化，不经过 new Date**——避免浏览器把无时区
 * 字符串按 UTC 解析导致 +8 偏移；带时区（Z/+08:00）的值才交给 Date 转本地时间。
 */
export function formatDateTime(val) {
  if (!val) return '-'
  if (val instanceof Date) {
    if (isNaN(val.getTime())) return '-'
    return formatFromDate(val)
  }
  const s = String(val)
  // 无时区 ISO：YYYY-MM-DD[ T]HH:mm:ss[.fff]
  const m = s.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})(\.\d+)?(Z|[+-]\d{2}:?\d{2})?$/)
  if (m && !m[8]) {
    return `${m[1]}-${m[2]}-${m[3]} ${m[4]}:${m[5]}:${m[6]}`
  }
  // 带时区或其它格式：交给 Date 处理
  const d = new Date(val)
  if (isNaN(d.getTime())) return '-'
  return formatFromDate(d)
}

function formatFromDate(d) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/**
 * 格式化文件大小。
 * @param {number|null|undefined} bytes 字节数
 * @returns {string} 可读的文件大小字符串
 */
export function formatSize(bytes) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return size.toFixed(i === 0 ? 0 : 1) + ' ' + units[i]
}

/**
 * 格式化传输速度。
 * @param {number|null|undefined} bytesPerSecond 每秒字节数
 * @returns {string} 可读的速度字符串
 */
export function formatSpeed(bytesPerSecond) {
  return formatSize(bytesPerSecond) + '/s'
}
