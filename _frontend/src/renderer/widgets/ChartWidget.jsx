import { useEffect, useRef } from 'react'
import * as echarts from 'echarts'

/**
 * 原生 ECharts 图表部件: option 即后端渲染产物 (LINE/BAR/PIE)。
 * 后端已按 schema + 数据算好完整 option, 前端零业务逻辑 (通用渲染器核心约定)。
 */
export default function ChartWidget({ option, title }) {
  const ref = useRef(null)
  const chartRef = useRef(null)

  useEffect(() => {
    if (!ref.current) return undefined
    chartRef.current = echarts.init(ref.current)
    const onResize = () => chartRef.current && chartRef.current.resize()
    window.addEventListener('resize', onResize)
    // ResizeObserver 兜底 grid 容器尺寸变化
    const ro = new ResizeObserver(onResize)
    ro.observe(ref.current)
    return () => {
      ro.disconnect()
      window.removeEventListener('resize', onResize)
      if (chartRef.current) chartRef.current.dispose()
      chartRef.current = null
    }
  }, [])

  useEffect(() => {
    if (chartRef.current && option) {
      chartRef.current.setOption(option, true) // notMerge: schema 变更整树替换
    }
  }, [option])

  return (
    <div style={{ width: '100%', height: '100%', padding: 8, boxSizing: 'border-box' }}>
      {title ? (
        <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 4 }}>{title}</div>
      ) : null}
      <div ref={ref} style={{ width: '100%', height: title ? 'calc(100% - 26px)' : '100%' }} />
    </div>
  )
}
