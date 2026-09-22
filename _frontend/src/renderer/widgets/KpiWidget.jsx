/**
 * KPI 部件: 后端 option = {type:'kpi', value, label}。
 * 非 echarts 类型, 由通用渲染器按约定结构解释 (与后端 ChartType.KPI 注释一致)。
 */
export default function KpiWidget({ option, title }) {
  const value = option && option.value !== undefined ? option.value : '-'
  const label = (option && option.label) || title || ''
  return (
    <div
      style={{
        width: '100%',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <div style={{ fontSize: 14, color: '#666', marginBottom: 8 }}>{label}</div>
      <div style={{ fontSize: 36, fontWeight: 700, lineHeight: 1.1 }}>
        {typeof value === 'number' ? value.toLocaleString() : value}
      </div>
    </div>
  )
}
