import Home from './pages/Home'
import ViewPage from './pages/ViewPage'
import SharePage from './pages/SharePage'
import AiPage from './pages/AiPage'

/** 路由清单（lead 005 §8.2 manifest，HashRouter）。 */
export const routes = [
    {path: '', Component: Home},
    {path: 'view/:viewId', Component: ViewPage},
    {path: 'share/:publishId', Component: SharePage},
    {path: 'ai', Component: AiPage},
]
export {default as Home} from './pages/Home'
export {default as ViewPage} from './pages/ViewPage'
export {default as SharePage} from './pages/SharePage'
export {default as AiPage} from './pages/AiPage'
