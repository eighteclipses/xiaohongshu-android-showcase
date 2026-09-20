// RFC-style quoted cells, CRLF, BOM and embedded newlines; no spreadsheet execution.
function parseCsv(text) {
  if (typeof text !== 'string' || Buffer.byteLength(text, 'utf8') > 1024 * 1024) throw new Error('CSV 文件不能超过 1MB');
  text = text.replace(/^\uFEFF/, '');
  const rows = []; let row = [], cell = '', quoted = false, ended = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) { if (c === '"') { if (text[i+1] === '"') { cell += '"'; i++; } else { quoted = false; ended = true; } } else cell += c; }
    else if (c === '"' && !cell && !ended) quoted = true;
    else if (c === ',') { row.push(cell); cell = ''; ended = false; }
    else if (c === '\n' || c === '\r') { if (c==='\r' && text[i+1]==='\n') i++; row.push(cell); if (row.some(Boolean)) rows.push(row); row=[];cell='';ended=false; }
    else { if (ended && c.trim()) throw new Error('CSV 引号后存在非法字符'); cell += c; }
  }
  if (quoted) throw new Error('CSV 引号未闭合');
  row.push(cell); if(row.some(Boolean)) rows.push(row);
  if(rows.length < 2) throw new Error('CSV 需包含表头和数据');
  const headers = rows.shift().map(value=>value.trim());
  if(new Set(headers).size!==headers.length) throw new Error('CSV 表头不能重复');
  if(rows.length>100) throw new Error('单次最多导入 100 行');
  return { headers, rows: rows.map((cells,index)=>({ line:index+2, cells, data:Object.fromEntries(headers.map((key,i)=>[key,cells[i] ?? ''])) })) };
}
module.exports = { parseCsv };
