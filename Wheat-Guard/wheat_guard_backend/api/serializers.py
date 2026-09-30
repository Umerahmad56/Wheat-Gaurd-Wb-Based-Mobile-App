from rest_framework import serializers
from django.contrib.auth.models import User
from django.db import transaction
from .models import UserProfile, DiseaseKnowledgeBase, IdentificationLog

class UserProfileSerializer(serializers.ModelSerializer):
    class Meta:
        model = UserProfile
        fields = ['role', 'phone_number']

class UserSerializer(serializers.ModelSerializer):
    profile = UserProfileSerializer(required=True)
    password = serializers.CharField(write_only=True)

    class Meta:
        model = User
        fields = ['id', 'username', 'email', 'password', 'profile']

    def create(self, validated_data):
        profile_data = validated_data.pop('profile')
        with transaction.atomic():
            user = User.objects.create_user(
                username=validated_data['username'],
                email=validated_data.get('email', ''),
                password=validated_data['password']
            )
            UserProfile.objects.create(user=user, **profile_data)
        return user

class DiseaseKnowledgeBaseSerializer(serializers.ModelSerializer):
    class Meta:
        model = DiseaseKnowledgeBase
        fields = '__all__'

class IdentificationLogSerializer(serializers.ModelSerializer):
    username = serializers.CharField(source='user.username', read_only=True)
    
    class Meta:
        model = IdentificationLog
        fields = ['id', 'username', 'image', 'disease_name', 'confidence', 'source', 'timestamp']
        read_only_fields = ['id', 'username', 'timestamp']
