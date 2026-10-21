import { useState } from 'react'
import type { FormEvent } from 'react'
import type { RegisterFormValues, RegisterFormErrors } from '../types/auth'
import { Link, useNavigate } from 'react-router'
import { api, getErrorMessage } from '../api/client'
import concertBackground from '../assets/concert-crowd.jpg'


const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const PASSWORD_MIN_LENGTH = 8
const PASSWORD_MAX_LENGTH = 50



const initialValues: RegisterFormValues = {
  email: '',
  password: '',
  firstName: '',
  lastName: '',
}

function validate(values: RegisterFormValues): RegisterFormErrors {
  const errors: RegisterFormErrors = {}

  if (!values.email.trim()) {
    errors.email = 'Emailul este obligatoriu'
  } else if (!EMAIL_REGEX.test(values.email.trim())) {
    errors.email = 'Emailul nu este valid'
  }

  if (!values.password) {
    errors.password = 'Parola este obligatorie'
  } else if (values.password.length < PASSWORD_MIN_LENGTH || values.password.length > PASSWORD_MAX_LENGTH) {
    errors.password = `Parola trebuie să aibă între ${PASSWORD_MIN_LENGTH} și ${PASSWORD_MAX_LENGTH} de caractere`
  }

  if (!values.firstName.trim()) {
    errors.firstName = 'Prenumele este obligatoriu'
  }

  if (!values.lastName.trim()) {
    errors.lastName = 'Numele este obligatoriu'
  }

  return errors
}

const RegisterPage = () => {
  const [values, setValues] = useState<RegisterFormValues>(initialValues)
  const [errors, setErrors] = useState<RegisterFormErrors>({})
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const navigate = useNavigate()

  const handleChange = (field: keyof RegisterFormValues) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setValues((prev) => ({ ...prev, [field]: e.target.value }))
  }

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setFormError(null)

    const validationErrors = validate(values)
    setErrors(validationErrors)

    if (Object.keys(validationErrors).length > 0) {
      return
    }

    setSubmitting(true)
    try {
      await api.post('/api/auth/register', {
        email: values.email.trim(),
        password: values.password,
        firstName: values.firstName.trim(),
        lastName: values.lastName.trim(),
      })
      navigate('/login', { state: { message: 'Cont creat. Te poti autentifica.' } })
    } catch (error) {
      setFormError(getErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  return (
      <div
          className="flex min-h-screen items-center justify-center bg-cover bg-center px-4 py-10"
          style={{ backgroundImage: `linear-gradient(rgba(10,14,30,0.6), rgba(10,14,30,0.8)), url(${concertBackground})` }}
      >
        <div className="w-full max-w-md">

          <div className="mb-8 text-center">
            <div
                className="mx-auto flex h-12 w-12 items-center justify-center rounded-xl bg-indigo-600 shadow-lg shadow-indigo-600/30">
              <svg className="h-6 w-6 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round"
                      d="M15 5v2m0 4v2m0 4v2M5 5a2 2 0 00-2 2v3a2 2 0 110 4v3a2 2 0 002 2h14a2 2 0 002-2v-3a2 2 0 110-4V7a2 2 0 00-2-2H5z"/>
              </svg>
            </div>
            <h1 className="mt-4 text-3xl font-bold tracking-tight text-white">Creează cont</h1>
            <p className="mt-2 text-sm text-slate-300">Completează datele pentru a începe</p>
          </div>

          <div className="rounded-2xl border border-slate-200 bg-white p-8 shadow-xl shadow-slate-200/60">
            <form className="space-y-5" onSubmit={handleSubmit} noValidate>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label htmlFor="register-firstName" className="block text-sm font-medium text-slate-700">
                    Prenume
                  </label>
                  <input
                      id="register-firstName"
                      type="text"
                      autoComplete="given-name"
                      value={values.firstName}
                      onChange={handleChange('firstName')}
                      className={`mt-1.5 w-full rounded-lg border bg-white px-3.5 py-2.5 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:ring-4 ${
                          errors.firstName
                              ? 'border-red-400 focus:border-red-500 focus:ring-red-500/10'
                              : 'border-slate-300 focus:border-indigo-500 focus:ring-indigo-500/10'
                      }`}
                      placeholder="Ana"
                  />
                  {errors.firstName && <p className="mt-1.5 text-sm text-red-600">{errors.firstName}</p>}
                </div>

                <div>
                  <label htmlFor="register-lastName" className="block text-sm font-medium text-slate-700">
                    Nume
                  </label>
                  <input
                      id="register-lastName"
                      type="text"
                      autoComplete="family-name"
                      value={values.lastName}
                      onChange={handleChange('lastName')}
                      className={`mt-1.5 w-full rounded-lg border bg-white px-3.5 py-2.5 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:ring-4 ${
                          errors.lastName
                              ? 'border-red-400 focus:border-red-500 focus:ring-red-500/10'
                              : 'border-slate-300 focus:border-indigo-500 focus:ring-indigo-500/10'
                      }`}
                      placeholder="Popescu"
                  />
                  {errors.lastName && <p className="mt-1.5 text-sm text-red-600">{errors.lastName}</p>}
                </div>
              </div>

              <div>
                <label htmlFor="register-email" className="block text-sm font-medium text-slate-700">
                  Email
                </label>
                <input
                    id="register-email"
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
                <label htmlFor="register-password" className="block text-sm font-medium text-slate-700">
                  Parolă
                </label>
                <input
                    id="register-password"
                    type="password"
                    autoComplete="new-password"
                    value={values.password}
                    onChange={handleChange('password')}
                    className={`mt-1.5 w-full rounded-lg border bg-white px-3.5 py-2.5 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:ring-4 ${
                        errors.password
                            ? 'border-red-400 focus:border-red-500 focus:ring-red-500/10'
                            : 'border-slate-300 focus:border-indigo-500 focus:ring-indigo-500/10'
                    }`}
                    placeholder="Minim 8 caractere"
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
                {submitting ? 'Se creează contul...' : 'Creează cont'}
              </button>
            </form>
          </div>

          <p className="mt-6 text-center text-sm text-slate-300">
            Ai deja cont?{' '}
            <Link to="/login" className="font-semibold text-indigo-400 hover:text-indigo-300">
              Conectează-te
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

export default RegisterPage
