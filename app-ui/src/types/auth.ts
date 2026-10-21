// Corespunde DTO-urilor din api-gateway (RegisterRequest / LoginRequest)

export interface LoginFormValues {
  email: string
  password: string
}

export interface RegisterFormValues {
  email: string
  password: string
  firstName: string
  lastName: string
}

export type LoginFormErrors = Partial<Record<keyof LoginFormValues, string>>
export type RegisterFormErrors = Partial<Record<keyof RegisterFormValues, string>>
