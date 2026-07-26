# 🏦 Digital Banking System - Microservices Architecture

<p align="center">

![Java](https://img.shields.io/badge/Java-17-orange?style=for-the-badge&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/SpringBoot-3.x-brightgreen?style=for-the-badge&logo=springboot)
![Spring Cloud](https://img.shields.io/badge/SpringCloud-Gateway-blue?style=for-the-badge)
![Apache Kafka](https://img.shields.io/badge/Apache-Kafka-black?style=for-the-badge&logo=apachekafka)
![Redis](https://img.shields.io/badge/Redis-Cache-red?style=for-the-badge&logo=redis)
![MySQL](https://img.shields.io/badge/MySQL-Database-blue?style=for-the-badge&logo=mysql)
![Docker](https://img.shields.io/badge/Docker-Container-blue?style=for-the-badge&logo=docker)

</p>

## 📌 Overview

A **production-style Digital Banking System** built using **Spring Boot Microservices** following modern backend architecture principles.

The project demonstrates:

- Microservices Architecture
- API Gateway
- Event-Driven Communication
- Saga Pattern
- Apache Kafka Messaging
- Redis Rate Limiting
- Razorpay Payment Integration
- Fraud Detection
- Notification Service
- Dockerized Infrastructure

---

# 🏗 Microservices

| Service | Port | Description |
|----------|------|-------------|
| API Gateway | 8080 | Single entry point with Redis Rate Limiting |
| Account Service | 8081 | Account creation, balance management |
| Transaction Service | 8082 | Money transfer and transaction history |
| Payment Service | 8083 | Razorpay payment integration |
| Fraud Detection Service | 8084 | Detects suspicious transactions |
| Notification Service | 8085 | Sends alerts and notifications |

---

# 🚀 Technology Stack

### Backend

- Java 17
- Spring Boot
- Spring MVC
- Spring Data JPA
- Spring Cloud Gateway
- Spring Validation

### Messaging

- Apache Kafka

### Database

- MySQL

### Cache

- Redis

### Payment Gateway

- Razorpay

### Build Tool

- Maven

### Containerization

- Docker
- Docker Compose

---

# 🏛 System Architecture

```
                         Client
                            │
                            ▼
                   API Gateway (8080)
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
        ▼                   ▼                   ▼
 Account Service      Transaction Service    Payment Service
        │                   │                   │
        │                   ▼                   │
        │              Apache Kafka            │
        │                   │                   │
        ├──────────────┬────┴──────────────┐    │
        ▼              ▼                   ▼    ▼
 Fraud Detection   Notification Service  Account Updates
       (Redis)
```

---

# 🔄 Banking Workflow

## Create Account

```
Client
   │
   ▼
API Gateway
   │
   ▼
Account Service
   │
   ▼
MySQL
```

---

## Transfer Money (Saga Pattern)

```
Transfer Request
        │
        ▼
Transaction Service
        │
        ▼
Deduct Sender Balance
        │
        ▼
Publish Event
(transaction.initiated)
        │
        ▼
Fraud Detection Service
        │
        ├───────────────┐
        │               │
 Approved           Fraud Detected
        │               │
        ▼               ▼
Credit Receiver    Refund Sender
        │               │
        ▼               ▼
Notification Service
```

---

## Payment Flow

```
Client
   │
   ▼
Payment Service
   │
   ▼
Razorpay
   │
Webhook
   │
   ▼
Kafka
   │
   ▼
Notification Service
```

---

# 📡 Kafka Topics

| Topic | Producer | Consumer |
|---------|----------|----------|
| transaction.initiated | Transaction Service | Fraud Detection |
| transaction.completed | Transaction Service | Notification, Account Service |
| fraud.detected | Fraud Detection | Account Service, Notification |
| transaction.refunded | Transaction Service | Notification |
| transaction.otp.generated | Transaction Service | Notification |
| payment.completed | Payment Service | Notification |
| payment.failed | Payment Service | Notification |

---

# 🔥 Features

## API Gateway

- Centralized Routing
- Redis Rate Limiting
- Request Forwarding

---

## Account Service

- Create Account
- Get Account
- Check Balance
- Credit Balance
- Deduct Balance
- Block Account

---

## Transaction Service

- Money Transfer
- OTP Verification
- Transaction History
- Saga Pattern
- Compensation Transactions

---

## Fraud Detection Service

- Detect suspicious transactions
- Redis-based validation
- High amount detection
- Multiple transactions detection
- Automatic account blocking

---

## Payment Service

- Razorpay Order Creation
- Webhook Handling
- Kafka Event Publishing

---

## Notification Service

Consumes Kafka Events

- Transaction Success
- Payment Success
- Payment Failure
- Fraud Alerts
- OTP Notifications
- Refund Notifications

---

# 📂 Project Structure

```
digital-banking-system
│
├── api-gateway
├── account-service
├── transaction-service
├── payment-service
├── fraud-detection-service
├── notification-service
│
├── docker-compose.yml
├── README.md
└── pom.xml
```

---

# ▶ Running the Project

## 1. Clone Repository

```bash
git clone https://github.com/Prabhat-coder77/digital-banking-system.git

cd digital-banking-system
```

---

## 2. Start Infrastructure

```bash
docker-compose up -d
```

This starts:

- MySQL
- Redis
- Kafka
- Zookeeper

---

## 3. Start Microservices

### API Gateway

```bash
cd api-gateway
mvn spring-boot:run
```

### Account Service

```bash
cd account-service
mvn spring-boot:run
```

### Transaction Service

```bash
cd transaction-service
mvn spring-boot:run
```

### Payment Service

```bash
cd payment-service
mvn spring-boot:run
```

### Fraud Detection Service

```bash
cd fraud-detection-service
mvn spring-boot:run
```

### Notification Service

```bash
cd notification-service
mvn spring-boot:run
```

---

# 📮 REST APIs

## Account Service

| Method | Endpoint |
|---------|----------|
| POST | /api/v1/accounts |
| GET | /api/v1/accounts/{accountNumber} |
| GET | /api/v1/accounts/{accountNumber}/balance |
| PUT | /api/v1/accounts/{accountNumber}/credit |
| PUT | /api/v1/accounts/{accountNumber}/deduct |
| PUT | /api/v1/accounts/{accountNumber}/block |

---

## Transaction Service

| Method | Endpoint |
|---------|----------|
| POST | /api/v1/transactions/transfer |
| GET | /api/v1/transactions/{transactionId} |
| GET | /api/v1/transactions/account/{accountNumber} |
| POST | /api/v1/transactions/{transactionId}/verify |

---

## Payment Service

| Method | Endpoint |
|---------|----------|
| POST | /api/v1/payments/create-order |
| POST | /api/v1/payments/webhook |

---

# 🔐 Saga Pattern

This project follows the **Saga Pattern** for distributed transactions.

### Step 1

Deduct sender balance.

↓

### Step 2

Publish transaction event to Kafka.

↓

### Step 3

Fraud Detection validates transaction.

↓

### Step 4

If approved

- Credit receiver
- Publish completion event
- Notify users

↓

If fraud detected

- Refund sender
- Block account
- Send fraud notification

---

# 📌 Future Enhancements

- Eureka Service Discovery
- Spring Cloud Config Server
- JWT Authentication
- OpenAPI / Swagger Documentation
- Resilience4j Circuit Breaker
- Prometheus & Grafana Monitoring
- ELK Logging
- Kubernetes Deployment
- CI/CD with GitHub Actions

---

# 👨‍💻 Author

**Prabhat Gouda**

Java Backend Developer

🔗 GitHub: https://github.com/Prabhat-coder77

🔗 LinkedIn: https://linkedin.com/in/prabhat-gouda

---

# ⭐ Support

If you found this project useful:

⭐ Star the repository

🍴 Fork the repository

💬 Share it with others

---

## ⭐ If you like this project, don't forget to give it a Star!
