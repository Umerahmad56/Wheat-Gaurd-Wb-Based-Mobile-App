import base64
from io import BytesIO
from PIL import Image
from django.urls import reverse
from django.contrib.auth.models import User
from rest_framework import status
from rest_framework.test import APITestCase
from api.models import DiseaseKnowledgeBase, IdentificationLog

class WheatGuardAPITests(APITestCase):

    def setUp(self):
        # Create seed data for testing
        self.yellow_rust = DiseaseKnowledgeBase.objects.create(
            name="Yellow Rust",
            name_ur="پیلا زنگ",
            pesticide_name="Tilt 250 EC",
            pesticide_name_ur="ٹلٹ 250 ای سی",
            dosage="200ml per acre",
            dosage_ur="200 ملی لیٹر فی ایکڑ",
            precautions="Wear mask.",
            precautions_ur="ماسک پہنیں",
            description="Yellow stripes",
            description_ur="پیلی دھاریاں"
        )
        
        self.signup_url = reverse('signup')
        self.login_url = reverse('login')
        self.scan_url = reverse('scan')
        self.history_url = reverse('history')
        self.kb_url = reverse('knowledge-base')
        
        self.user_data = {
            "username": "testfarmer",
            "password": "securepassword123",
            "email": "farmer@example.com",
            "profile": {
                "role": "FARMER",
                "phone_number": "+923001234567"
            }
        }

    def test_user_signup_and_login(self):
        # 1. Signup test
        response = self.client.post(self.signup_url, self.user_data, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertIn('token', response.data)
        self.assertEqual(response.data['role'], 'FARMER')
        
        # 2. Login test
        login_data = {
            "username": "testfarmer",
            "password": "securepassword123"
        }
        response = self.client.post(self.login_url, login_data, format='json')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn('token', response.data)
        
    def test_knowledge_base_retrieval(self):
        # Register and login to get token
        signup_resp = self.client.post(self.signup_url, self.user_data, format='json')
        token = signup_resp.data['token']
        self.client.credentials(HTTP_AUTHORIZATION='Token ' + token)
        
        response = self.client.get(self.kb_url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(response.data[0]['name'], "Yellow Rust")

    def test_scan_and_history(self):
        # Register and login to get token
        signup_resp = self.client.post(self.signup_url, self.user_data, format='json')
        token = signup_resp.data['token']
        self.client.credentials(HTTP_AUTHORIZATION='Token ' + token)
        
        # Create a valid green image using PIL
        img = Image.new("RGB", (100, 100), color="green")
        buf = BytesIO()
        img.save(buf, format="JPEG")
        b64_image = base64.b64encode(buf.getvalue()).decode('utf-8')
        
        scan_data = {
            "image": b64_image,
            "source": "ONLINE"
        }
        
        # Send scan request
        response = self.client.post(self.scan_url, scan_data, format='json')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn('disease_name', response.data)
        self.assertIn('confidence', response.data)
        self.assertIn('pesticide_name', response.data)
        
        # Verify it got saved in the logs
        history_resp = self.client.get(self.history_url)
        self.assertEqual(history_resp.status_code, status.HTTP_200_OK)
        self.assertEqual(len(history_resp.data), 1)
        self.assertEqual(history_resp.data[0]['disease_name'], response.data['disease_name'])
