import {HashRouter, Route, Routes} from 'react-router-dom'
import {routes} from '@yuku123/z-report-component/pages'

export default function App() {
    return (
        <HashRouter>
            <Routes>
                {routes.map(r => <Route key={r.path} path={r.path ? '/' + r.path : '/'} element={<r.Component/>}/>)}
            </Routes>
        </HashRouter>
    )
}
