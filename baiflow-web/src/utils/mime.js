/**
 * 文件扩展名 → MIME 类型映射表。
 * 当数据库中的 mimeType 为空或不准确时，通过扩展名回退。
 */
const EXT_MIME = {
  // 图片
  jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png', gif: 'image/gif',
  webp: 'image/webp', svg: 'image/svg+xml', bmp: 'image/bmp', ico: 'image/x-icon',
  avif: 'image/avif', tiff: 'image/tiff', tif: 'image/tiff', heic: 'image/heic',
  // 视频
  mp4: 'video/mp4', webm: 'video/webm', ogv: 'video/ogg', ogg: 'video/ogg',
  mkv: 'video/x-matroska', avi: 'video/x-msvideo', mov: 'video/quicktime',
  wmv: 'video/x-ms-wmv', flv: 'video/x-flv', ts: 'video/mp2t', '3gp': 'video/3gpp',
  // 音频
  mp3: 'audio/mpeg', wav: 'audio/wav', flac: 'audio/flac', aac: 'audio/aac',
  m4a: 'audio/mp4', opus: 'audio/opus', wma: 'audio/x-ms-wma',
  // 文档
  pdf: 'application/pdf',
  doc: 'application/msword', docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  xls: 'application/vnd.ms-excel', xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  ppt: 'application/vnd.ms-powerpoint', pptx: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  odt: 'application/vnd.oasis.opendocument.text',
  ods: 'application/vnd.oasis.opendocument.spreadsheet',
  odp: 'application/vnd.oasis.opendocument.presentation',
  // 文本/代码
  txt: 'text/plain', csv: 'text/csv', json: 'application/json',
  xml: 'text/xml', html: 'text/html', htm: 'text/html',
  css: 'text/css', md: 'text/markdown', yaml: 'text/yaml', yml: 'text/yaml',
  log: 'text/plain', env: 'text/plain', ini: 'text/plain', cfg: 'text/plain', toml: 'text/plain',
  js: 'text/javascript', ts: 'text/typescript', jsx: 'text/javascript', tsx: 'text/typescript',
  py: 'text/x-python', java: 'text/x-java', kt: 'text/x-kotlin',
  go: 'text/x-go', rs: 'text/x-rust', c: 'text/x-c', cpp: 'text/x-c++', h: 'text/x-c',
  sql: 'text/x-sql', sh: 'text/x-sh', bash: 'text/x-sh',
  vue: 'text/x-vue', svelte: 'text/x-svelte',
  // 压缩包
  zip: 'application/zip', rar: 'application/x-rar-compressed',
  '7z': 'application/x-7z-compressed', tar: 'application/x-tar',
  gz: 'application/gzip', bz2: 'application/x-bzip2', xz: 'application/x-xz',
  // 电子书
  epub: 'application/epub+zip', mobi: 'application/x-mobipocket-ebook'
}

/** 根据文件名推断 MIME 类型 */
export function mimeFromName(filename) {
  if (!filename) return 'application/octet-stream'
  const dot = filename.lastIndexOf('.')
  if (dot < 0) return 'application/octet-stream'
  const ext = filename.substring(dot + 1).toLowerCase()
  return EXT_MIME[ext] || 'application/octet-stream'
}

/** 判断 MIME 主类型 */
export function mimeCategory(mime) {
  if (!mime) return 'unknown'
  if (mime.startsWith('image/')) return 'image'
  if (mime.startsWith('video/')) return 'video'
  if (mime.startsWith('audio/')) return 'audio'
  if (mime === 'application/pdf') return 'pdf'
  // Office 文档（doc/docx/xls/xlsx/ppt/pptx/odt/ods/odp）暂不支持在线预览，归为 unknown → 降级为下载
  if (mime === 'text/markdown') return 'markdown'
  if (mime.startsWith('text/') || mime === 'application/json' || mime === 'application/xml') return 'text'
  if (mime === 'application/zip') return 'zip'
  return 'unknown'
}

/** 是否支持在线预览 */
export function canPreview(mime) {
  return mimeCategory(mime) !== 'unknown'
}

/** 根据 MIME 类别获取进度类型 */
export function progressTypeForCategory(category) {
  if (category === 'video' || category === 'audio') return 'SECONDS'
  if (category === 'pdf') return 'PAGE'
  if (category === 'text' || category === 'markdown') return 'SCROLL_PERCENT'
  return null
}

// ---- 文件类型图标（与 Android 端 FilesFragment.iconFor 保持一致，图标复用 Android 资源） ----

/** 图标基础路径（Vite public 目录，构建后复制到 dist 根） */
const ICON_DIR = '/icons/'

/** 文件夹图标 */
export const folderIconPath = `${ICON_DIR}ic_folder.png`

/**
 * 根据文件名/MIME 返回文件类型图标路径。
 * md 优先按扩展名识别：服务端存的 mime 是上传方 Content-Type，.md 可能不是 text/markdown。
 */
export function fileIconPath(name, mime) {
  if (isMarkdown(name, mime)) return `${ICON_DIR}ic_type_md.png`
  if (!mime) return `${ICON_DIR}ic_type_file.png`
  if (mime.startsWith('image/')) return `${ICON_DIR}ic_type_image.png`
  if (mime.startsWith('video/')) return `${ICON_DIR}ic_type_video.png`
  if (mime.startsWith('audio/')) return `${ICON_DIR}ic_type_audio.png`
  if (mime === 'application/pdf') return `${ICON_DIR}ic_type_pdf.png`
  if (mime.endsWith('json')) return `${ICON_DIR}ic_type_json.png`
  if (mime.endsWith('xml')) return `${ICON_DIR}ic_type_xml.png`
  if (mime.startsWith('text/')) return `${ICON_DIR}ic_type_file.png`
  if (mime.includes('msword') || mime.includes('wordprocessingml')) return `${ICON_DIR}ic_type_word.png`
  if (mime.includes('ms-excel') || mime.includes('spreadsheetml')) return `${ICON_DIR}ic_type_excel.png`
  if (mime.includes('ms-powerpoint') || mime.includes('presentationml')) return `${ICON_DIR}ic_type_ppt.png`
  if (mime.includes('zip') || mime.includes('compressed') || mime.includes('x-tar')
      || mime.includes('gzip')) return `${ICON_DIR}ic_type_archive.png`
  return `${ICON_DIR}ic_type_file.png`
}

/** 是否 Markdown 文件：扩展名 .md/.markdown，或 MIME 含 markdown */
function isMarkdown(name, mime) {
  if (name) {
    const lower = name.toLowerCase()
    if (lower.endsWith('.md') || lower.endsWith('.markdown')) return true
  }
  return !!(mime && mime.includes('markdown'))
}
