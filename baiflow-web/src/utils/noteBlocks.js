import showdown from 'showdown'
import TurndownService from 'turndown'

/**
 * 笔记块结构 ↔ Markdown 转换（与 Android MarkdownParser/MarkdownEmitter 格式严格对齐）。
 *
 * 存储格式仍是 Markdown（服务端 bf_note.content 不透明字符串、同步零改动）；
 * 编辑器用块结构渲染，文本块存的是「行内 markdown 源」原样透传，媒体块是真正的组件。
 *
 * 块模型：
 *   { type:'p', text }            段落（text 为行内 markdown 源）
 *   { type:'h', level:1-3, text } 标题
 *   { type:'bullet', items:[..] } 无序列表
 *   { type:'ordered', items:[..] }有序列表
 *   { type:'code', language, code } 围栏代码（兜底）
 *   { type:'image', url, alt }    图片
 *   { type:'audio', url, duration } 录音（url 带 ?mediaType=audio[&duration=ms]）
 */

const HEADING_RE = /^(#{1,3})\s+(.*)$/
// 整行媒体（图片 / 录音链接）
const LINE_IMAGE_RE = /^!\[([^\]]*)\]\(([^)\s]+)\)$/
const LINE_AUDIO_RE = /^\[([^\]]*)\]\(([^)\s]+\?mediaType=audio[^)]*)\)$/

/** 解析整篇 Markdown 为块序列（空行视为块间分隔，不生成块） */
export function markdownToBlocks(md) {
  const lines = (md || '').split('\n')
  const blocks = []
  let i = 0
  while (i < lines.length) {
    const line = lines[i]

    // 空行（分隔）
    if (line.trim() === '') { i++; continue }

    // 标题
    let m = line.match(HEADING_RE)
    if (m) { blocks.push({ type: 'h', level: m[1].length, text: m[2] }); i++; continue }

    // 普通段落：连续非特殊行合并（引用行归入段落，作为原始 markdown 文本）
    const pLines = []
    while (i < lines.length) {
      const l = lines[i]
      if (l.trim() === '' || l.match(HEADING_RE)) break
      pLines.push(l)
      i++
    }
    if (pLines.length) {
      blocks.push(...splitMediaLines(pLines.join('\n')))
    } else {
      blocks.push({ type: 'p', text: line })
      i++
    }
  }
  return blocks
}

/** 把段落里的整行媒体拆成独立媒体块；普通行保留为文本块（软换行合并） */
function splitMediaLines(text) {
  const out = []
  for (const ln of text.split('\n')) {
    let m = ln.match(LINE_IMAGE_RE)
    if (m) { out.push({ type: 'image', url: m[2], alt: m[1] || '' }); continue }
    m = ln.match(LINE_AUDIO_RE)
    if (m) { out.push({ type: 'audio', url: m[2], duration: audioDurationFrom(m[2]) }); continue }
    out.push({ type: 'p', text: ln })
  }
  // 合并连续文本行为一个段落块（保持软换行）
  const merged = []
  for (const b of out) {
    const last = merged[merged.length - 1]
    if (b.type === 'p' && last && last.type === 'p') last.text += '\n' + b.text
    else merged.push(b)
  }
  return merged
}

function audioDurationFrom(url) {
  const m = url.match(/duration=(\d+)/)
  return m ? parseInt(m[1], 10) : 0
}

/** 序列化块为 Markdown（块间以空行分隔，与 Android 解析兼容） */
export function blocksToMarkdown(blocks) {
  const out = []
  for (const b of blocks || []) {
    switch (b.type) {
      case 'p': out.push(b.text || ''); break
      case 'h': out.push('#'.repeat(Math.max(1, Math.min(3, b.level || 1))) + ' ' + (b.text || '')); break
      case 'bullet': out.push((b.items || []).map((it) => '- ' + it).join('\n')); break
      case 'ordered': out.push((b.items || []).map((it, idx) => (idx + 1) + '. ' + it).join('\n')); break
      case 'quote': out.push('> ' + (b.text || '')); break
      case 'code': out.push(emitCode(b)); break
      case 'image': out.push('![' + (b.alt || '') + '](' + b.url + ')'); break
      case 'audio': out.push(emitAudio(b)); break
      default: break
    }
  }
  return out.join('\n\n')
}

function emitCode(b) {
  // 围栏长度取内容中连续反引号最大长度 + 1（与 Android MarkdownEmitter 一致），避免内容误闭合
  const fence = '`'.repeat(Math.max(3, maxBacktickRun(b.code || '') + 1))
  let s = fence + (b.language || '') + '\n' + (b.code || '')
  if (b.code && !b.code.endsWith('\n')) s += '\n'
  return s + fence
}

function emitAudio(b) {
  let url = b.url || ''
  // 音频 URL 必须带 mediaType=audio，否则重进无法识别为音频块
  if (!url.includes('mediaType=audio')) {
    url += (url.includes('?') ? '&' : '?') + 'mediaType=audio'
  }
  if (b.duration && !url.includes('duration=')) {
    url += '&duration=' + b.duration
  }
  return '[录音](' + url + ')'
}

function maxBacktickRun(s) {
  let max = 0
  let run = 0
  for (const c of s) {
    if (c === '`') { run++; max = Math.max(max, run) } else run = 0
  }
  return max
}

// ==================== 预览渲染（块 → HTML，供「预览」模式使用） ====================
// 文本块/标题块内是「行内 markdown 源」，交给 showdown 渲染（加粗/斜体/删除线/行内码/
// 链接/图片/列表/引用/围栏都原生支持）；下划线 `<u>` 用占位符绕过转义后还原；
// 媒体 URL 在最后统一加会话 token（`<img>/<audio>` 无法带 Authorization 头）。

const U_OPEN = '\uE000'
const U_CLOSE = '\uE001'

/** 行内转换器：软换行 → <br>，~~删除线~~，下划线不作强调，HTML 注入由 inlineToHtml 预转义 */
const INLINE_CONVERTER = new showdown.Converter({
  literalMidWordUnderscores: true,
  noHeaderId: true,
  simpleLineBreaks: true,
  strikethrough: true
})

/** 行内 markdown → HTML；`<`/`&` 预转义防注入（保留块引用 `>`），仅放开下划线 */
export function inlineToHtml(text) {
  const guarded = String(text ?? '')
    .replace(/<u>/g, U_OPEN)
    .replace(/<\/u>/g, U_CLOSE)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
  let html = INLINE_CONVERTER.makeHtml(guarded)
  html = html.replace(new RegExp(U_OPEN, 'g'), '<u>').replace(new RegExp(U_CLOSE, 'g'), '</u>')
  // 删除线统一渲染成 <strike>：execCommand('strikeThrough') 对 <strike>/<s> 的选中状态识别可靠，
  // showdown 默认输出 <del>，会导致「选中删除线文字再点 S 取消」失效
  html = html.replace(/<del>/g, '<strike>').replace(/<\/del>/g, '</strike>')
  // makeHtml 会把单行包成 <p>…</p>，剥掉外皮只留行内内容
  return html.replace(/^<p>/, '').replace(/<\/p>$/, '')
}

// ==================== HTML → Markdown（contenteditable 所见即所得回写） ====================
// 编辑时 contenteditable 的 innerHTML 通过 turndown 转回 markdown 存 b.text；
// 下划线保留为 <u>、删除线 → ~~、软换行 → \n、列表用单空格标记并保留序号。

const TURNDOWN = new TurndownService({
  headingStyle: 'atx',
  codeBlockStyle: 'fenced',
  bulletListMarker: '-',
  emDelimiter: '*',
  strongDelimiter: '**'
})
TURNDOWN.addRule('underline', { filter: ['u'], replacement: (content) => '<u>' + content + '</u>' })
TURNDOWN.addRule('strikethrough', {
  filter: ['del', 's', 'strike'],
  replacement: (content) => '~~' + content + '~~'
})
TURNDOWN.addRule('br', { filter: ['br'], replacement: () => '\n' })
TURNDOWN.addRule('listItem', {
  filter: 'li',
  replacement: (content, node) => {
    const parent = node.parentNode
    const ordered = parent && parent.nodeName === 'OL'
    content = content.replace(/\n/g, '\n    ')
    if (ordered) {
      // 用 li 在 ol 中的实际序号还原「n. 」编号（支持 start 属性）
      const idx = Array.prototype.indexOf.call(parent.children, node)
      const start = parseInt(parent.getAttribute('start') || '1', 10) || 1
      return (start + idx) + '. ' + content + '\n'
    }
    return '- ' + content + '\n'
  }
})

/** contenteditable 内容 → markdown（输入/失焦时回写 b.text，空内容归一为空串） */
export function htmlToMarkdown(html) {
  return TURNDOWN.turndown(html || '').trim()
}
