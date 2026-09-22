import ChartWidget from './widgets/ChartWidget.jsx'
import KpiWidget from './widgets/KpiWidget.jsx'
import TableWidget from './widgets/TableWidget.jsx'

/**
 * 通用渲染器 (方案决策③): schema 驱动, 前端零业务逻辑。
 * <p>
 * 输入 = 后端 GET /api/render/view/{id} 响应:
 * {
 *   viewId, title, version,
 *   layout: [{widgetId, x, y, w, h}],           // 12 列网格坐标
 *   options: { [widgetId]: option + _widgetType/_title }
 * }
 * 分发规则 (与后端 EChartsRenderService 对齐):
 * - LINE/BAR/PIE → ChartWidget (原生 echarts option)
 * - KPI          → KpiWidget  ({type:'kpi', value, label})
 * - TABLE        → TableWidget ({type:'table', columns, rows})
 * <p>
 * 布局: CSS Grid, 12 列; 行高 ROW_H; x/y/w/h 直接来自后端 layout (拖拽编辑器 M4
 * 产出同结构, 渲染与编辑共用布局协议)。
 */
const GRID_COLUMNS = 12
const ROW_H = 60 // px, layout 单位行高

export default function UniversalRenderer({ renderData }) {
  if (!renderData) {
    return <div style={{ padding: 24 }}>加载中...</div>
  }
  const { title, layout, options } = renderData
  const widgets = (layout || [])
    .map((l) => {
      const option = (options || {})[l.widgetId]
      if (!option) return null // 后端渲染失败的 widget 不产生 option, 跳过不阻塞整页
      const type = option._widgetType || 'TABLE'
      const inner =
        type === 'KPI' ? (
          <KpiWidget option={option} title={option._title} />
        ) : type === 'TABLE' ? (
          <TableWidget option={option} title={option._title} />
        ) : (
          <ChartWidget option={option} title={option._title} />
        )
      return (
        <div
          key={l.widgetId}
          style={{
            gridColumn: `${(l.x || 0) + 1} / span ${Math.max(1, l.w || 4)}`,
            gridRow: `${(l.y || 0) + 1} / span ${Math.max(1, l.h || 2)}`,
            background: '#fff',
            border: '1px solid #f0f0f0',
            borderRadius: 8,
            overflow: 'hidden',
          }}
        >
          {inner}
        </div>
      )
    })
    .filter(Boolean)

  // 行数由最深的 y+h 决定 (显式 grid-template-rows 保证空行高度正确)
  const totalRows = (layout || []).reduce((m, l) => Math.max(m, (l.y || 0) + (l.h || 2)), 0)

  return (
    <div style={{ padding: 16 }}>
      {title ? (
        <h2 style={{ margin: '0 0 12px', fontSize: 18 }}>{title}</h2>
      ) : null}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: `repeat(${GRID_COLUMNS}, 1fr)`,
          gridTemplateRows: `repeat(${totalRows}, ${ROW_H}px)`,
          gap: 12,
        }}
      >
        {widgets}
      </div>
      {widgets.length === 0 ? <div style={{ color: '#999' }}>该视图没有可渲染的部件</div> : null}
    </div>
  )
}
