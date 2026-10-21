import { Link, Navigate, useNavigate } from 'react-router'
import { useAuth } from '../auth/useAuth'
import concertBackground from '../assets/concert-crowd.jpg'

const HomePage = () => {
    const { logout, roles } = useAuth()
    const navigate = useNavigate()

    if (!roles.includes('ROLE_ORGANIZER')) {
        return <Navigate to="/events" replace />
    }

    const handleLogout = () => {
        logout()
        navigate('/login', { replace: true })
    }

    return (
        <div
            className="flex min-h-screen items-center justify-center bg-cover bg-center px-4"
            style={{ backgroundImage: `linear-gradient(rgba(10,14,30,0.6), rgba(10,14,30,0.8)), url(${concertBackground})` }}
        >
            <div className="w-full max-w-sm rounded-xl bg-white p-8 shadow-sm ring-1 ring-slate-200 text-center">
                <h1 className="text-2xl font-semibold text-slate-900">Ești conectat</h1>

                <Link
                    to="/events"
                    className="mt-6 block w-full rounded-md bg-indigo-600 py-2 text-sm font-medium text-white transition hover:bg-indigo-700"
                >
                    Vezi evenimentele
                </Link>

                <Link
                    to="/admin"
                    className="mt-3 block w-full rounded-md bg-white py-2 text-sm font-medium text-slate-700 ring-1 ring-slate-300 transition hover:bg-slate-50"
                >
                    Administrare date
                </Link>

                <button
                    type="button"
                    onClick={handleLogout}
                    className="mt-3 w-full rounded-md bg-slate-900 py-2 text-sm font-medium text-white transition hover:bg-slate-700"
                >
                    Deconectare
                </button>
            </div>
        </div>
    )
}

export default HomePage