from django.db import models
from django.contrib.auth.models import User

class UserProfile(models.Model):
    ROLE_CHOICES = (
        ('FARMER', 'Farmer'),
        ('ADVISOR', 'Agricultural Advisor'),
    )
    user = models.OneToOneField(User, on_delete=models.CASCADE, related_name='profile')
    role = models.CharField(max_length=10, choices=ROLE_CHOICES, default='FARMER')
    phone_number = models.CharField(max_length=20, blank=True, null=True)

    def __str__(self):
        return f"{self.user.username} ({self.role})"

class DiseaseKnowledgeBase(models.Model):
    name = models.CharField(max_length=100, unique=True) # e.g. "Yellow Rust"
    name_ur = models.CharField(max_length=100) # e.g. "پیلا زنگ"
    pesticide_name = models.CharField(max_length=100)
    pesticide_name_ur = models.CharField(max_length=100)
    dosage = models.CharField(max_length=100) # e.g., "250ml per acre"
    dosage_ur = models.CharField(max_length=100) # e.g. "250 ملی لیٹر فی ایکڑ"
    precautions = models.TextField()
    precautions_ur = models.TextField()
    description = models.TextField()
    description_ur = models.TextField()

    def __str__(self):
        return self.name

class IdentificationLog(models.Model):
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='logs')
    image = models.TextField() # Storing base64-encoded representation of leaf image
    disease_name = models.CharField(max_length=100) # Yellow Rust, Brown Rust, Black Rust, Healthy
    confidence = models.DecimalField(max_digits=5, decimal_places=2) # Probability percentage (e.g. 92.50)
    source = models.CharField(max_length=10, default='ONLINE') # ONLINE or OFFLINE
    timestamp = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"{self.user.username} - {self.disease_name} ({self.timestamp})"
