import React from 'react'
import { createRoot } from 'react-dom/client'
import App from './App.jsx'

// 全局最小样式: 白底深字, 报表阅读场景
const style = document.createElement('style')
style.textContent = `
  * { box-sizing: border-box; }
  body { margin: 0; background: #f5f6f8; color: #222;
         font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif; }
`
document.head.appendChild(style)

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
