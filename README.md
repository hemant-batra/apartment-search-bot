
# 🏠 Apartment Alert Bot - Quick Setup

This project automatically scrapes apartment listings from Pararius.nl for selected cities and sends WhatsApp alerts every 5 minutes.

## 🌐 Cities
- Wageningen
- Ede
- Arnhem
- Bennekom
- Veenendaal

## 💰 Max Rent
€1200

## 🚀 Deployment Steps
1. Replace `YOUR_TWILIO_SID`, `YOUR_TWILIO_AUTH_TOKEN`, `YOUR_VERIFIED_NUMBER` in `ApartmentAlertBotApplication.java`.
2. Build & Run:
```
mvn clean install
mvn spring-boot:run
```
3. Manual API:
```
http://localhost:8080/scrape
```
4. Deploy to Render.com (Free):
- Environment: Java
- Build Command: `./mvnw clean install`
- Start Command: `java -jar target/*.jar`

5. Add Cron Job in Render:
```
curl https://your-app-url.onrender.com/scrape
Every 5 minutes
```
