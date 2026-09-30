\# Wheat Guard 🌾



\## Project Overview

Wheat Guard is an AI-based Android application designed to detect wheat rust disease and provide advisory recommendations to farmers.



The system uses a hybrid approach:

\- Offline prediction using TensorFlow Lite

\- Cloud-based prediction using Django REST API



\## Features



\- User registration and login

\- Wheat leaf image scanning

\- Rust disease detection

\- Disease diagnosis results

\- Disease database

\- Pesticide advisory

\- User profile management

\- Offline and online prediction support



\## Technologies Used



\### Mobile Application

\- Android Studio

\- Kotlin

\- XML



\### Backend

\- Django

\- Django REST Framework

\- REST API



\### Machine Learning

\- Convolutional Neural Network (CNN)

\- Support Vector Machine (SVM)

\- TensorFlow Lite



\### Database

\- SQLite / SQL Database



\## System Architecture



The application consists of three main components:



1\. Android Mobile Application

2\. Django Backend Server

3\. Machine Learning Model



\## Project Structure





Wheat-Guard/

│

├── wheat-guard-mobile/

│

└── wheat\_guard\_backend/





\## Installation



\### Backend Setup



1\. Clone repository:





git clone https://github.com/ozmandious4-max/Wheat-Guard.git





2\. Navigate to backend:





cd wheat\_guard\_backend





3\. Create virtual environment:





python -m venv .venv





4\. Install dependencies:





pip install -r requirements.txt





5\. Run Django server:





python manage.py runserver





\## Mobile App Setup



1\. Open `wheat-guard-mobile` in Android Studio.

2\. Sync Gradle files.

3\. Connect Android device/emulator.

4\. Run the application.



\## Future Improvements



\- Improve model accuracy

\- Add more crop diseases

\- Add multilingual support

\- Deploy scalable cloud services



\## Authors



Wheat Guard FYP Team

