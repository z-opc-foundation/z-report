/**
 * 表格部件: 后端 option = {type:'table', columns:[{key,label}], rows:[{col:val}]}。
 * 数据量可能大 (maxRows 上限由后端源层控制), 超过 200 行仅渲染前 200 行 (坑清单 P-09:
 * 分页/虚拟滚动由同伴在 M2.5 补, 需要后端 rows 切片 API 配合)。
 */
const MAX_RENDER_ROWS = 200

export default function TableWidget({ option, title }) {
  if (!option || !option.columns) {
    return <div style={{ padding: 16 }}>暂无数据</div>
  }
  const columns = option.columns
  const rows = (option.rows || []).slice(0, MAX_RENDER_ROWS)
  return (
    <div style={{ width: '100%', height: '100%', padding: 8, boxSizing: 'border-box', overflow: 'auto' }}>
      {title ? <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 4 }}>{title}</div> : null}
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
        <thead>
          <tr>
            {columns.map((c) => (
              <th
                key={c.key}
                style={{
                  position: 'sticky',
                  top: 0,
                  background: '#fafafa',
                  borderBottom: '1px solid #e8e8e8',
                  padding: '6px 10px',
                  textAlign: 'left',
                }}
              >
                {c.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i} style={{ borderBottom: '1px solid #f0f0f0' }}>
              {columns.map((c) => (
                <td key={c.key} style={{ padding: '6px 10px' }}>
                  {r[c.key] === null || r[c.key] === undefined ? '-' : String(r[c.key])}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      {(option.rows || []).length > MAX_RENDER_ROWS ? (
        <div style={{ color: '#999', fontSize: 12, marginTop: 4 }}>
          仅展示前 {MAX_RENDER_ROWS} 行 (共 {(option.rows || []).length} 行)
        </div>
      ) : null}
    </div>
  )
}
