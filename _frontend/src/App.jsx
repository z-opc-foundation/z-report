import { HashRouter, Routes, Route } from 'react-router-dom'
import Home from './pages/Home.jsx'
import ViewPage from './pages/ViewPage.jsx'
import SharePage from './pages/SharePage.jsx'
import AiPage from './pages/AiPage.jsx'

/**
 * 路由:
 * - /            模板画廊 (创建示例书)
 * - /view/:id    视图渲染页 (通用渲染器; ?bookId= 可发布)
 * - /share/:pid  匿名观看页 (?token= 必带, 整书快照)
 * - /ai          AI 一句话生成 (M3, 需后端 LLM 配置)
 * HashRouter: 静态部署 (无网关 rewrite) 也能直接用, 与分享链接构造保持一致。
 */
export default function App() {
  return (
    <HashRouter>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/view/:viewId" element={<ViewPage />} />
        <Route path="/share/:publishId" element={<SharePage />} />
        <Route path="/ai" element={<AiPage />} />
      </Routes>
    </HashRouter>
  )
}
