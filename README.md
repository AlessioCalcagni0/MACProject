# Running Tracker App

Mobile and Cloud Computing Project

---

## Overview

Running Tracker App is a mobile application designed to track running sessions in real-time while leveraging cloud technologies for data storage, synchronization, and multi-user interaction.

The application allows users to:
- Track their running activity
- Visualize routes on a map
- Monitor performance metrics
- Interact with other users through group runs

The system is built using:
- Android (Kotlin)
- FastAPI (Python backend)
- PostgreSQL (Cloud SQL)
- Google Cloud Run
- Firebase Authentication

---

## Architecture
Android App

↓ (HTTP REST API)

FastAPI Backend (Cloud Run)

↓

PostgreSQL (Cloud SQL)

Additional services:
- Firebase Authentication (login)
- Google Maps SDK (map visualization)
- Open-Meteo API (weather)

---

## Sequence (Run Tracking)
User starts run

↓

POST /runs/start

↓

Server creates run


During run:

↓

POST /runs/{id}/route (GPS updates)


User stops run

↓

POST /runs/{id}/end

↓

Server computes stats


---

## Key Features

### Run Tracking
- Real-time GPS tracking
- Distance, speed, duration calculation
- Calories estimation
- Goal-based runs (time, distance, calories)

---

### Interactive Map
- Google Maps integration
- Live route visualization
- Path drawing using polylines
- Real-time position updates

---

### Photo Capture
- Capture images during runs
- Preview images in UI
- Associate photos with running sessions

---

### Weather Integration
- Uses Open-Meteo API
- Displays current temperature and conditions
- Provides running advice

---

### Social & Group Runs
- Create groups
- Invite users
- Accept/decline invitations
- Organize group runs

---

### Authentication
- Firebase Authentication
- Secure user sessions
- Backend verification with ID tokens

---

## Requirements Implementation

### 1. Public Cloud Service
- Open-Meteo API used for weather data

---

### 2. Multi-user Support
- Firebase Authentication
- Each user identified by Firebase UID

---

### 3. 2D Graphics
- Google Maps UI
- Dynamic polyline rendering
- UI animations (goal reached)

---

### 4. Sensors
- GPS sensor via FusedLocationProviderClient
- Speed and movement calculation

---

### 5. GPS
- Continuous location tracking
- Route mapping on map

---

### 6. Camera
- Android camera intent
- Capture images during runs

---

### 7. Concurrency
- Kotlin Coroutines for async API calls
- Background GPS updates
- FastAPI handles concurrent requests

---

### 8. Additional Cloud Features
- Google Cloud Run (backend)
- Cloud SQL (database)
- Firebase Authentication

---

### 9. REST API
- FastAPI backend deployed on Cloud Run
- REST endpoints:


POST /runs/start

POST /runs/{id}/route

POST /runs/{id}/end

POST /group-runs/start

POST /group-invites/respond


---

### 10. Storage Service
- PostgreSQL database
- Stores:
  - users
  - runs
  - route_points
  - photos
  - groups
  - invites

---

## Project Structure
MACProject/

│

├── app/

│   ├── src/main/java/com/example/myapplication/

│   │   ├── ui/

│   │   │   ├── run/

│   │   │   │   └── RunFragment.kt

│   │   │   ├── home/

│   │   │   │   └── HomeFragment.kt

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

- Android Studio
- Python 3.10+
- PostgreSQL
- Google Cloud account
- Firebase project

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

