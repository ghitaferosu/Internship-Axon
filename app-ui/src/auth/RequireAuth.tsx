import {Navigate, Outlet} from 'react-router'
import { useAuth } from './useAuth'

export function RequireAuth() {
    const { isAuthenticated } = useAuth()

    return isAuthenticated ? <Outlet /> : <Navigate to ="/login" replace/>
}