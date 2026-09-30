from django.urls import path
from .views import SignUpView, LoginView, DiseaseListView, ScanHistoryView, ScanView

urlpatterns = [
    path('auth/signup/', SignUpView.as_view(), name='signup'),
    path('auth/login/', LoginView.as_view(), name='login'),
    path('scan/', ScanView.as_view(), name='scan'),
    path('history/', ScanHistoryView.as_view(), name='history'),
    path('knowledge-base/', DiseaseListView.as_view(), name='knowledge-base'),
]
