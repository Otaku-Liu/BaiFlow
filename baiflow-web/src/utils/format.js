/**
 * 格式化日期为 yyyy-MM-dd HH:mm:ss。
 * @param {string|Date|null|undefined} val ISO 时间字符串或 Date 对象
 * @returns {string} 格式化后的时间字符串，空值返回 '-'
 */
export function formatDateTime(val) {
  if (!val) return '-'
  const d = val instanceof Date ? val : new Date(val)
  if (isNaN(d.getTime())) return '-'
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
