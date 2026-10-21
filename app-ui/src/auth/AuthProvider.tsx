import { type ReactNode, useState } from "react";
import { clearTokens, getAccessToken, saveTokens } from "../api/tokenStorage.ts";
import { getRoles, getUserId } from "./jwt.ts";
import { AuthContext } from "./AuthContext";

export function AuthProvider({ children }: { children: ReactNode }) {
    const [isAuthenticated, setIsAuthenticated] = useState(() => getAccessToken() !== null)
    const [userId, setUserId] = useState<number | null>(() => {
        const token = getAccessToken()
        return token ? getUserId(token) : null
    })
    const [roles, setRoles] = useState<string[]>(() => {
        const token = getAccessToken()
        return token ? getRoles(token) : []
    })

    const login = (accessToken: string, refreshToken: string) => {
        saveTokens(accessToken, refreshToken)
        setIsAuthenticated(true)
        setUserId(getUserId(accessToken))
        setRoles(getRoles(accessToken))
    }

    const logout = () => {
        clearTokens()
        setIsAuthenticated(false)
        setUserId(null)
        setRoles([])
    }

    return (
        <AuthContext.Provider value={{ isAuthenticated, userId, roles, login, logout }}>
            {children}
        </AuthContext.Provider>
    )
}
