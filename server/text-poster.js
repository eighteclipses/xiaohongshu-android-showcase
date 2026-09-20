// 文字海报服务端生成：复刻 App TextToImageConverter 样式（PowerShell + GDI+ 渲染）。
// 按 内容+作者 哈希落盘复用；文件名含 text_image_ 前缀，App 端识别为生成海报（隐藏重复标题）。
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PS_SCRIPT = path.join(__dirname, 'poster', 'render-poster.ps1');
const POSTER_DIR = path.join(__dirname, 'uploads');

/**
 * 为纯文字笔记生成（或复用）文字海报。
 * @returns {string|null} 相对 URL（/uploads/text_image_xxx.png），失败返回 null
 */
function ensureTextPoster(title, content, author) {
  const text = [(title || '').trim(), (content || '').trim()].filter(Boolean).join('\n').trim();
  if (!text) return null;
  const hash = crypto.createHash('sha1').update(text + '|' + (author || '')).digest('hex').slice(0, 16);
  const fileName = 'text_image_' + hash + '.png';
  const file = path.join(POSTER_DIR, fileName);
  const url = '/uploads/' + encodeURIComponent(fileName);
  if (fs.existsSync(file) && fs.statSync(file).size > 0) return url;
  try {
    fs.mkdirSync(POSTER_DIR, { recursive: true });
    // GDI+ 渲染，同步等待（发布请求上下文，单次 1-3s，仅每种内容首次生成）
    execFileSync('powershell.exe', [
      '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', PS_SCRIPT,
      '-OutFile', file,
      '-Title', (title || '').trim(),
      '-Content', (content || '').trim(),
      '-Author', (author || '').trim()
    ], { timeout: 20000, windowsHide: true });
    if (fs.existsSync(file) && fs.statSync(file).size > 0) return url;
  } catch (error) {
    console.warn('text poster generate failed:', error.message);
  }
  return null;
}

module.exports = { ensureTextPoster };
