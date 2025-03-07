# Cloud Resource Management System

A Spring Boot application for managing the reservation and booking of cloud/hardware resources within an organization. This system provides a RESTful API for managing resources, users, bookings, and notifications, with a focus on secure access through Keycloak integration.

## 🎯 Project Scope

This system enables organizations to:

- Manage various types of resources (servers, GPUs, network equipment, etc.)
- Track resource status (active, maintenance, unavailable)
- Allow users to book resources for specific time periods
- Prevent booking conflicts through time-slot validation
- Manage users and roles via Keycloak integration
- Send and manage notifications to users

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
- PostgreSQL (production) or H2 (development)
- Keycloak server (optional for development)

### Configuration

The application has different configuration profiles:

- **dev**: Uses H2 in-memory database and mocked Keycloak service
- **prod** (default): Uses PostgreSQL and requires a running Keycloak server

#### Development Mode

For quick setup and testing, use the `dev` profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

#### Production Mode

Create a `application-prod.yml` file with your specific configuration or update the existing `application.yml`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

### Database

The application uses:
- H2 in-memory database for development
- PostgreSQL for production

Database migrations are handled automatically through Hibernate.

### Keycloak Setup

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

Resources represent physical or virtual assets that can be booked.

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
  "name": "Server XYZ",
  "specs": "32GB RAM, 8 CPUs",
  "location": "Data Center 1",
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

Resource types categorize resources (e.g., Server, GPU, Switch).

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
  "name": "Server",
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
  "title": "Development work",
  "description": "Working on Project XYZ",
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

This project is licensed under the MIT License - see the LICENSE file for details.