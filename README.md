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

