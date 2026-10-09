import {HashRouter, Route, Routes} from 'react-router-dom'
import {routeTable} from '@yuku123/z-report-component/pages'

export default function App() {
    return (
        <HashRouter>
            <Routes>
                {routeTable.map(r => <Route key={r.path} path={r.path ? '/' + r.path : '/'} element={<r.Component/>}/>)}
            </Routes>
        </HashRouter>
    )
}
