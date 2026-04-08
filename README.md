# Running Tracker App

Mobile and Cloud Computing Project

---

## Overview

Running Tracker App is a mobile application designed to track running sessions in real-time. By integrating mobile sensors with scalable cloud technologies, it provides a seamless, data-rich experience that motivates users to achieve their fitness goals.

The application allows users to:
- Track their running activity
- Visualize routes on a map
- Monitor performance metrics
- Run with other users through group runs

The system is built using:
- Android (Kotlin)
- FastAPI (Python backend)
- PostgreSQL (Cloud SQL)
- Google Cloud Run
- Firebase Authentication

### Target Users
The app is built for runners of all levels from beginners looking to establish a routine to advanced athletes who want detailed performance metrics, weather-based insights and performance statistics.

### How It Works
The user starts a run on the mobile app (selecting his goal from: time, distance calories), which actively pings GPS and device sensors to track their route, speed, and distance. This data is processed locally with concurrent tasks to ensure a smooth UI, while communicating asynchronously with a FastAPI backend. Photos, run history, weather data, and social interactions are saved to a PostgreSQL database hosted in the cloud, allowing users to review their performance and connect with friends.

---

## Key Features

### Run Tracking
- Real-time tracking of your run using precise GPS data
- Performance Metrics: Accurate tracking of essential data including distance, speed, duration, and calories burned estimation.
- Set custom personal targets based on time, distance, or calorie burn

---

### Interactive Map
- Google Maps integration
- Visual display of your running route on an live map
- Path drawing using polylines
- Real-time position updates

---

### Photo Capture
- Ability to capture and save photos directly within the app during a run without interrupting the tracking session
- Preview images in UI

---

### Weather Integration
- Uses Open-Meteo API
- Displays current temperature and conditions
- Provides running advice related to the weather condition

---

### Social & Group Runs
- Connect with friends, form running groups, and organize group runs to stay motivated
- Accept/decline invitations

---

### Authentication
- Firebase Authentication
- Secure user sessions
- Backend verification with ID tokens

---

## Requirements Implementation
Here is how the specific course requirements were fulfilled in this project:
1. **Public Cloud Service**: Integrated the Open-Meteo API (https://open-meteo.com/) to fetch real-time weather data and provide running advice.
2. **Multi-user Support**: Implemented Firebase Authentication to allow secure user sign-ups, logins, and session management.
3. **2D Graphics**: Developed custom 2D graphs within the Android app to visualize running performance metrics and weather trends over time.
4. **Use of Sensors**: Utilized the device's Hardware Pedometer / Accelerometer to complement GPS tracking and ensure accurate step 5. **GPS**: Leveraged Android's Location Services (GPS) to track user coordinates in real-time and trace the run on the map.
6. **Camera & Image Processing**: Integrated the Android Camera API to allow users to snap photos during their run, associating those images with specific waypoints
7. **Concurrency**: Used Kotlin Coroutines and ViewModel scopes on the frontend to ensure background location tracking and API calls do not block the main thread.
8. **Additional Cloud Features**: Deployed the backend server using Google Cloud Run (and utilized Google Maps API for route rendering).
9. **REST API on Remote Server**: Developed a FastAPI (Python) server, containerized via Docker, and hosted remotely to process all client requests
10. **Storage Service**: Connected the FastAPI backend to a Google Cloud SQL (PostgreSQL) database to persist user profiles, friends lists, goals, and run history.

---

## Project Structure
MACProject/

│

├── app/

│   ├── src/main/java/com/example/myapplication/

│   │   ├── ui/



│
│   ├── res/

│   │   ├── layout/

│   │   ├── drawable/

│

├── backend/

│   ├── main.py

│   ├── models.py

│   ├── database.py

│   ├── auth.py

│   ├── schema.sql

│   ├── Dockerfile

│

├── README.md




---


---

## How to Run

---

### Prerequisites

- **Android Studio** (latest version)
- Python 3.10+
- A running instance of PostgreSQL (local or cloud)
- Google Cloud account
- Firebase project (Follow the steps in the README in the folder path backend/backend/secrets)

---

## Mobile Setup

1. Clone repository:

```bash
git clone https://github.com/AlessioCalcagni0/MACProject

Open in Android Studio




Add:








google-services.json (Firebase)




Google Maps API key








Run app on device/emulator





 Backend Setup



Install dependencies
pip install fastapi uvicorn sqlalchemy psycopg2-binary firebase-admin

Configure environment variables
export DATABASE_URL=postgresql://USER:PASSWORD@HOST:5432/DB_NAME
export FIREBASE_CREDENTIALS_PATH=service-account.json

