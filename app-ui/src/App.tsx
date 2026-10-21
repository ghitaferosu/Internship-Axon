import { Navigate, Route, Routes } from 'react-router'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import HomePage from './pages/HomePage'
import SeatMapPage from './pages/SeatMapPage'
import EventsPage from './pages/EventsPage'
import EventBookingPage from './pages/EventBookingPage'
import AdminPage from './pages/AdminPage'
import {RequireAuth} from "./auth/RequireAuth.tsx";
import {RequireRole} from "./auth/RequireRole.tsx";
import MyTicketsPage from "./pages/MyTicketsPage.tsx";

const App = () => {
  return (
      <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/events" element={<EventsPage />} />
          <Route path="/events/:eventId" element={<EventBookingPage />} />
          <Route element={<RequireAuth />}>
              <Route path="/home" element={<HomePage />} />
              <Route element={<RequireRole role="ROLE_ORGANIZER" />}>
                  <Route path="/seat-map" element={<SeatMapPage />} />
                  <Route path="/admin" element={<AdminPage />} />
              </Route>
              <Route path="/my-tickets" element={<MyTicketsPage />}/>
          </Route>
          <Route path="*" element={<Navigate to="/events" replace />} />
      </Routes>
  )
}

export default App