import { useState } from 'react'
import type { FormEvent } from 'react'
import {Link, useNavigate} from 'react-router'
import type { LoginFormValues, LoginFormErrors } from '../types/auth'
import {api, getErrorMessage} from "../api/client.ts";
import {useAuth} from "../auth/useAuth.ts";
import concertBackground from '../assets/concert-crowd.jpg'

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/



const initialValues: LoginFormValues = {
  email: '',
  password: '',
}

function validate(values: LoginFormValues): LoginFormErrors {
  const errors: LoginFormErrors = {}

  if (!values.email.trim()) {
    errors.email = 'Emailul este obligatoriu'
  } else if (!EMAIL_REGEX.test(values.email.trim())) {
    errors.email = 'Emailul nu este valid'
  }

  if (!values.password) {
    errors.password = 'Parola este obligatorie'
  }

  return errors
}

const LoginPage = () => {
  const [values, setValues] = useState<LoginFormValues>(initialValues)
  const [errors, setErrors] = useState<LoginFormErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const navigate = useNavigate()
  const {login} = useAuth()
  const handleChange = (field: keyof LoginFormValues) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setValues((prev) => ({ ...prev, [field]: e.target.value }))
  }

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()

    const validationErrors = validate(values)
    setErrors(validationErrors)

    if (Object.keys(validationErrors).length > 0) {
      return
    }

    setSubmitting(true)

    try{
      const response = await api.post('/api/auth/login',{
        email: values.email.trim(),
        password: values.password,
      })
      login(response.data.accessToken, response.data.refreshToken)
      navigate('/home', { state: { message: 'Login success'}})
    } catch (e) {
      setFormError(getErrorMessage(e))
    }finally {
      setSubmitting(false)
    }

  }

  return (
      <div
          className="flex min-h-screen items-center justify-center bg-cover bg-center px-4"
          style={{ backgroundImage: `linear-gradient(rgba(10,14,30,0.6), rgba(10,14,30,0.8)), url(${concertBackground})` }}
      >
        <div className="w-full max-w-md">

          <div className="mb-8 text-center">
            <div
                className="mx-auto flex h-12 w-12 items-center justify-center rounded-xl bg-indigo-600 shadow-lg shadow-indigo-600/30">
              <svg className="h-6 w-6 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M15 5v2m0 4v2m0 4v2M5 5a2 2 0 00-2 2v3a2 2 0 110 4v3a2 2 0 002 2h14a2 2 0 002-2v-3a2 2 0 110-4V7a2 2 0 00-2-2H5z" />
              </svg>
            </div>
            <h1 className="mt-4 text-3xl font-bold tracking-tight text-white">Bine ai revenit</h1>
          </div>

          <div className="rounded-2xl border border-slate-200 bg-white p-8 shadow-xl shadow-slate-200/60">
            <form className="space-y-5" onSubmit={handleSubmit} noValidate>

              <div>
                <label htmlFor="login-email" className="block text-sm font-medium text-slate-700">
                  Email
                </label>
                <input
                    id="login-email"
                    type="email"
                    autoComplete="email"
                    value={values.email}
                    onChange={handleChange('email')}
                    className={`mt-1.5 w-full rounded-lg border bg-white px-3.5 py-2.5 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:ring-4 ${
                        errors.email
                            ? 'border-red-400 focus:border-red-500 focus:ring-red-500/10'
                            : 'border-slate-300 focus:border-indigo-500 focus:ring-indigo-500/10'
                    }`}
                    placeholder="nume@exemplu.com"
                />
                {errors.email && <p className="mt-1.5 text-sm text-red-600">{errors.email}</p>}
              </div>

              <div>
                <label htmlFor="login-password" className="block text-sm font-medium text-slate-700">
                  Parolă
                </label>
                <input
                    id="login-password"
                    type="password"
                    autoComplete="current-password"
                    value={values.password}
                    onChange={handleChange('password')}
                    className={`mt-1.5 w-full rounded-lg border bg-white px-3.5 py-2.5 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:ring-4 ${
                        errors.password
                            ? 'border-red-400 focus:border-red-500 focus:ring-red-500/10'
                            : 'border-slate-300 focus:border-indigo-500 focus:ring-indigo-500/10'
                    }`}
                    placeholder="••••••••"
                />
                {errors.password && <p className="mt-1.5 text-sm text-red-600">{errors.password}</p>}
              </div>

              {formError && (
                  <div className="rounded-lg bg-red-50 px-3.5 py-2.5 text-sm text-red-700 ring-1 ring-red-100">
                    {formError}
                  </div>
              )}

              <button
                  type="submit"
                  disabled={submitting}
                  className="w-full rounded-lg bg-indigo-600 py-2.5 text-sm font-semibold text-white shadow-lg shadow-indigo-600/25 outline-none transition hover:bg-indigo-700 focus:ring-4 focus:ring-indigo-500/30 disabled:opacity-60"
              >
                {submitting ? 'Se conectează...' : 'Conectează-te'}
              </button>
            </form>
          </div>

          <p className="mt-6 text-center text-sm text-slate-300">
            Nu ai cont?{' '}
            <Link to="/register" className="font-semibold text-indigo-400 hover:text-indigo-300">
              Înregistrează-te
            </Link>
          </p>

          <p className="mt-2 text-center text-sm text-slate-300">
            <Link to="/events" className="font-semibold text-slate-200 hover:text-white">
              Vezi evenimentele fără cont →
            </Link>
          </p>
        </div>
      </div>
  )
}

export default LoginPage
