import {useAuth} from "./useAuth.ts";
import {Navigate, Outlet} from "react-router";

export function RequireRole ({role}: {role:string}) {
    const {roles} = useAuth()
    return roles.includes(role) ? <Outlet /> : <Navigate to ="/home" replace />
}