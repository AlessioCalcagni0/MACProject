# Pacemate App

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
- Local Caching: Runs are saved locally using an SQLite (Room) database if the user loses internet connection during a run.

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
├── app/                                    <-- Android Application
│   ├── google-services.json
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/myapplication/
│       │   │   ├── MyApplication.kt
│       │   │   │
│       │   │   ├── data/                   
│       │   │   │   ├── auth/
│       │   │   │   ├── home/
│       │   │   │   ├── run/                
│       │   │   │   ├── social/
│       │   │   │   ├── stats/
│       │   │   │   ├── AppDatabase.kt      
│       │   │   │   └── RetrofitClient.kt
│       │   │   │
│       │   │   ├── domain/                 
│       │   │   │   ├── auth/               
│       │   │   │   ├── home/
│       │   │   │   ├── run/
│       │   │   │   ├── social/
│       │   │   │   └── stats/
│       │   │   │
│       │   │   ├── ui/                    
│       │   │   │   ├── auth/
│       │   │   │   ├── home/
│       │   │   │   ├── profile/
│       │   │   │   ├── run/                
│       │   │   │   ├── social/
│       │   │   │   ├── splash/
│       │   │   │   ├── stats/
│       │   │   │   └── theme/
│       │   │   │
│       │   │   └── utils/
│       │   │       └── NetworkMonitor.kt   
│       │   │
│       │   └── res/                       
│       │       ├── drawable/
│       │       ├── layout/
│       │       ├── menu/
│       │       └── values/
│       │
│       ├── androidTest/                    
│       └── test/                           
│
├── backend/                                <-- Python Backend Setup
│   ├── docker-compose.yml                  
│   ├── cloudrun.env.yaml                   
│   │
│   └── backend/                            <-- FastAPI Source Code
│       ├── Dockerfile
│       ├── requirements.txt
│       ├── app/                           
│       │   ├── main.py
│       │   ├── database.py
│       │   ├── models.py                   
│       │   ├── schemas.py                  
│       │   └── firebase_auth.py            
│       │
│       └── secrets/                        <-- Secure key storage
│           ├── maccproject-...-adminsdk.json
│           └── README.md
│
├── documentation/                          
│   ├── Application_architecture.png
│   ├── NavGraph.png
│   └── Storyboard.png
│
├── build.gradle.kts
├── settings.gradle.kts
└── README.md




---


---

## How to Run

---

### Prerequisites

- **Android Studio** (latest version)
- Python 3.10+
- A running instance of PostgreSQL (local or cloud)
- Google Cloud account
- Git (to clone the repository)
- Firebase project (Follow the steps in the README in the folder path backend/backend/secrets)

---

## Mobile Setup

1. Clone repository:
Open your terminal and clone the project to your local machine:

```bash
git clone https://github.com/AlessioCalcagni0/MACProject
```

2. Open in Android Studio
- Open Android Studio.
- Click on File > Open (or "Open an existing project").
- Navigate to the MACProject folder and select it. Wait for Gradle to finish its initial sync.
   
3. Add Firebase Configuration
To enable Firebase Authentication and Cloud Storage, you need your project-specific configuration file.
- Download the google-services.json file from your Firebase Console.
- Place this file directly inside the app/ directory of your project:

MACProject/
└── app/
    └── google-services.json  <-- Place it here

4. Configure the Google Maps API Key
The app uses the Google Maps SDK to track runs. You need to provide a valid API key.
Obtain an API key from the Google Cloud Console with the Maps SDK for Android enabled.
- Open MACProject/app/src/main/AndroidManifest.xml.
- Locate the meta-data tag for the Maps API and insert your key:

```bash
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="YOUR_GOOGLE_MAPS_API_KEY_HERE" />
```
5. Build and Run
- Sync project with Gradle files (Click the "Sync" icon in the top right).
- Connect a physical Android device via USB/Wi-Fi, or start an Android Emulator.
- Click the green Run 'app' button in the toolbar.

## Backend Setup
You have two options for running the backend: Locally (Manual) or via Docker.
- Navigate to the Backend Directory
```bash
cd MACProject/backend/
```

### Manual Local Setup
1. Install dependencies
You can install the dependencies using the provided requirements file:
```bash
pip install -r requirements.txt
```
2. Configure Firebase Admin SDK
The backend needs the Firebase service account key to verify user tokens.
- Download your service account JSON file from Firebase Console (Project Settings > Service Accounts > Generate New Private Key).
- Place it in the secrets folder (e.g., MACProject/backend/backend/secrets/service-account.json).

3. Configure environment variables
Export the necessary environment variables so the FastAPI app can connect to your database and Firebase.
```bash
export DATABASE_URL="postgresql://USER:PASSWORD@HOST:5432/DB_NAME"
export FIREBASE_CREDENTIALS_PATH="./secrets/service-account.json"
```
4. Run the server
Start the FastAPI server using Uvicorn:
```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```
Your API will now be live at http://127.0.0.1:8000.

### Docker Setup
1. Ensure your Firebase JSON is in the backend/secrets/ folder.
2. Start the containers in detached mode:
```bash
docker-compose up --build -d
```
Docker will automatically pull the PostgreSQL image, build your FastAPI application, link them together, and expose the API on port 8000.

Connecting the App to the Local Backend:
If testing on a physical device, ensure your phone and computer are on the same Wi-Fi network. In your Android app's RetrofitClient, change the BASE_URL from localhost to your computer's local IP address (e.g., http://192.168.1.X:8000/). If using the Android Emulator, use http://10.0.2.2:8000/.

