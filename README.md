# Cloud Resource Management System

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-23-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](https://www.docker.com/)

A Spring Boot application for managing the reservation and booking of any type of resources within an organization. This system provides a RESTful API for managing resources, users, bookings, and notifications, with a focus on secure access through Keycloak integration.

> **Note:** This backend is part of a larger project available at [cloud-resource-reservation](https://github.com/giovannimirarchi420/cloud-resource-reservation), which includes the frontend, Keycloak configuration, and Docker Compose setup to run the complete system.

## 🎯 Project Scope

This system enables organizations to:

- Manage any type of resources, which are configurable through resource types
- Track resource status (active, maintenance, unavailable)
- Allow users to book resources for specific time periods
- Prevent booking conflicts through time-slot validation
- Manage users and roles via Keycloak integration
- Send and manage notifications to users
- Support hierarchical resource structures with parent-child relationships
- Store and manage SSH public keys for accessing resources

## 🏗️ Architecture

The application follows a standard Spring Boot architecture:

- **Controllers**: REST API endpoints
- **Services**: Business logic layer
- **Repositories**: Data access layer
- **Models**: Data entities
- **DTOs**: Data transfer objects
- **Configuration**: System configuration

## 🔑 Security

The application uses OAuth2/OpenID Connect authentication through Keycloak:

- JWT-based authentication
- Role-based authorization (ADMIN and USER roles)
- Fine-grained method-level security
- Development mode with simplified authentication

## 🚀 Getting Started

### Prerequisites

- Java 17+
- Maven
- Database (H2 for dev, PostgreSQL for test, Oracle for production)
- Keycloak server (optional for development)

### Configuration

The application has different configuration profiles:

- **dev**: Uses H2 in-memory database and mocked Keycloak service for quick local development
- **test**: Uses PostgreSQL and Keycloak, designed for running in Docker Compose environment
- **pro**: Uses Oracle database and requires a running Keycloak server for production deployment

Maven profiles are aligned with Spring profiles to ensure proper dependency management:

- **dev** (default): Includes H2 dependencies
- **test**: Includes PostgreSQL dependencies
- **pro**: Includes Oracle JDBC dependencies

#### Development Mode

For quick setup and testing on your local machine, use the `dev` profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev -Pdev
```

#### Test Mode with Docker Compose

To run the application in a containerized environment with PostgreSQL and Keycloak, use the `test` profile. 
The Docker Compose project for running the entire stack is available at:

https://github.com/giovannimirarchi420/cloud-resource-reservation

Run with:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=test -Ptest
```

#### Production Mode

For production deployment with Oracle database and Keycloak integration:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=pro -Ppro
```

### Database

The application supports multiple databases based on profile:
- H2 in-memory database for development (`dev` profile)
- PostgreSQL for testing in Docker Compose (`test` profile)
- Oracle for production (`pro` profile)

Database migrations are handled automatically through Hibernate.

### Keycloak Setup

When using the Docker Compose setup from the [parent repository](https://github.com/giovannimirarchi420/cloud-resource-reservation), the Keycloak configuration is performed automatically with pre-configured realm, client, and roles.

If you're setting up Keycloak manually:

1. Install and run Keycloak server
2. Create a new realm called `resource-management`
3. Create a client called `resource-management-app`
4. Configure the client settings according to your environment
5. Create the required roles: `ADMIN` and `USER`

## 🔌 API Reference

### Authentication

All API endpoints (except for swagger documentation) require authentication. The application uses OAuth2 with JWT tokens from Keycloak.

### Common Response Format

Most endpoints return data in a standard format:

```json
{
  "success": true|false,
  "message": "Operation result message",
  "data": { ... } // Optional response data
}
```

### Resources

Resources represent any physical or virtual assets that can be booked, configurable through resource types.

#### Get All Resources
```
GET /resources
```

Optional query parameters:
- `status`: Filter by resource status (ACTIVE, MAINTENANCE, UNAVAILABLE)
- `typeId`: Filter by resource type ID

#### Get Resource by ID
```
GET /resources/{id}
```

#### Create Resource (Admin only)
```
POST /resources
```

```json
{
  "name": "Resource XYZ",
  "specs": "Specifications for the resource",
  "location": "Location identifier",
  "status": "ACTIVE",
  "typeId": 1
}
```

#### Update Resource (Admin only)
```
PUT /resources/{id}
```

#### Update Resource Status (Admin only)
```
PATCH /resources/{id}/status?status=MAINTENANCE
```

#### Delete Resource (Admin only)
```
DELETE /resources/{id}
```

#### Search Resources
```
GET /resources/search?query=term
```

### Resource Types

Resource types categorize resources and are fully configurable.

#### Get All Resource Types
```
GET /resource-types
```

#### Get Resource Type by ID
```
GET /resource-types/{id}
```

#### Create Resource Type (Admin only)
```
POST /resource-types
```

```json
{
  "name": "Custom Resource Type",
  "color": "#1976d2"
}
```

#### Update Resource Type (Admin only)
```
PUT /resource-types/{id}
```

#### Delete Resource Type (Admin only)
```
DELETE /resource-types/{id}
```

### Events (Bookings)

Events represent resource bookings for a specific time period.

#### Get All Events
```
GET /events
```

Optional query parameters:
- `resourceId`: Filter by resource ID
- `startDate`: Filter by start date
- `endDate`: Filter by end date

#### Get Current User's Events
```
GET /events/my-events
```

#### Get Event by ID
```
GET /events/{id}
```

#### Create Event
```
POST /events
```

```json
{
  "title": "Resource reservation",
  "description": "Purpose of booking",
  "resourceId": 1,
  "start": "2023-04-01T09:00:00",
  "end": "2023-04-01T17:00:00"
}
```

#### Update Event
```
PUT /events/{id}
```

#### Delete Event
```
DELETE /events/{id}
```

#### Check for Conflicts
```
GET /events/check-conflicts?resourceId=1&start=2023-04-01T09:00:00&end=2023-04-01T17:00:00
```

#### Check Resource Availability
```
GET /events/check-resource-availability?resourceId=1
```

### Users

Provides user management capabilities.

#### Get All Users (Admin only)
```
GET /users
```

#### Get User by ID (Admin only)
```
GET /users/{id}
```

#### Get Current User's Profile
```
GET /users/me
```

#### Create User (Admin only)
```
POST /users
```

```json
{
  "username": "johndoe",
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "password": "securePassword",
  "roles": ["USER"]
}
```

#### Update User (Admin only)
```
PUT /users/{id}
```

#### Update Current User's Profile
```
PUT /users/me
```

```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "avatar": "JD"
}
```

#### Delete User (Admin only)
```
DELETE /users/{id}
```

#### Get Users by Role (Admin only)
```
GET /users/by-role/{role}
```

#### Get Current User's SSH Public Key
```
GET /users/me/ssh-key
```

#### Update Current User's SSH Public Key
```
PUT /users/me/ssh-key
```

```json
{
  "sshPublicKey": "ssh-rsa AAAAB3NzaC1yc2EAAAADAQ...user@example.com"
}
```

#### Delete Current User's SSH Public Key
```
DELETE /users/me/ssh-key
```

### Notifications

System notifications to users.

#### Get Current User's Notifications
```
GET /notifications
```

Optional query parameters:
- `unreadOnly`: Filter to show only unread notifications (default: false)

#### Get Unread Notification Count
```
GET /notifications/unread-count
```

#### Mark Notification as Read
```
PATCH /notifications/{id}/mark-read
```

#### Mark All Notifications as Read
```
PATCH /notifications/mark-all-read
```

#### Delete Notification
```
DELETE /notifications/{id}
```

#### Send Notification to User (Admin only)
```
POST /notifications/send?userId=1&message=Your+message&type=INFO
```

## 📚 Documentation

API documentation is available through Swagger UI:

```
GET /api/swagger-ui.html
```

## 📋 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details