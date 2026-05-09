import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import Layout from './components/Layout';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import HomePage from './pages/HomePage';
import PositionMedicinePage from './pages/PositionMedicinePage';
import AlertHistoryPage from './pages/AlertHistoryPage';
import ProfilePage from './pages/ProfilePage';
import AlertDetailPage from './pages/AlertDetailPage';
import DeviceManagementPage from './pages/DeviceManagementPage';
import UserSettingsPage from './pages/UserSettingsPage';
import ElderManagementPage from './pages/ElderManagementPage';
import ElderDetailPage from './pages/ElderDetailPage';
import NotificationListPage from './pages/NotificationListPage';
import LocationPage from './pages/LocationPage';
import MapPage from './pages/MapPage';
import OcrMedicinePage from './pages/OcrMedicinePage';
import MedicationPlanPage from './pages/MedicationPlanPage';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isLoggedIn, isLoading } = useAuth();
  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center"
           style={{ background: 'linear-gradient(to bottom, #FFF5F0, #FFFFFF)' }}>
        <div className="w-8 h-8 border-2 rounded-full animate-spin"
             style={{ borderColor: '#165DFF', borderTopColor: 'transparent' }} />
      </div>
    );
  }
  if (!isLoggedIn) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      {/* Auth routes */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />

      {/* Main routes — protected, with bottom nav layout */}
      <Route element={<ProtectedRoute><Layout /></ProtectedRoute>}>
        <Route path="/home" element={<HomePage />} />
        <Route path="/position-medicine" element={<PositionMedicinePage />} />
        <Route path="/alerts" element={<AlertHistoryPage />} />
        <Route path="/profile" element={<ProfilePage />} />

        {/* Sub pages */}
        <Route path="/alerts/:alertId" element={<AlertDetailPage />} />
        <Route path="/devices" element={<DeviceManagementPage />} />
        <Route path="/settings" element={<UserSettingsPage />} />
        <Route path="/elders" element={<ElderManagementPage />} />
        <Route path="/elders/:elderId" element={<ElderDetailPage />} />
        <Route path="/notifications" element={<NotificationListPage />} />
        <Route path="/location" element={<LocationPage />} />
        <Route path="/map" element={<MapPage />} />
        <Route path="/ocr" element={<OcrMedicinePage />} />
        <Route path="/medication" element={<MedicationPlanPage />} />
      </Route>

      {/* Catch all */}
      <Route path="*" element={<Navigate to="/home" replace />} />
    </Routes>
  );
}
