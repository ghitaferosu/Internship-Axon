import { createContext } from "react";

export interface AuthContextValue {
    isAuthenticated: boolean
    userId: number | null

    roles: string[]
    login: (accessToken: string, refreshToken: string) => void
    logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
