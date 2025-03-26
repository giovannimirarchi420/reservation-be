Implementation of Federation System: Frontend Integration Guide
Overview
We've enhanced the backend to support a federation-based system that allows for organizational hierarchy and more granular permissions. This document outlines the changes made and the integration required on the frontend.
Key Concepts
1. Federations
Federations are organizational units (e.g., universities, departments) that group users and resources. They are represented as Keycloak Groups.
2. New Role Structure
We've implemented a new role hierarchy:

GLOBAL_ADMIN: Can manage all federations, resources, and users
FEDERATION_ADMIN: Can manage resources and users within their specific federation(s)
USER: Regular user with booking capabilities, now associated with federation(s)

3. Federation-Scoped Resources
Resources and resource types now belong to specific federations, and access is controlled based on federation membership and roles.
API Changes
New Endpoints
1. Federation Management
GET /federations                   - Get all federations (scoped by user access)
GET /federations/{id}              - Get a specific federation
POST /federations                  - Create a new federation (GLOBAL_ADMIN only)
PUT /federations/{id}              - Update a federation (GLOBAL_ADMIN only)
DELETE /federations/{id}           - Delete a federation (GLOBAL_ADMIN only)
GET /federations/{id}/users        - Get users in a federation
POST /federations/{id}/users/{userId} - Add a user to a federation
DELETE /federations/{id}/users/{userId} - Remove a user from a federation

2. Federation Admin Management
GET /federations/{id}/admins       - Get federation admins
POST /federations/{id}/admins/{userId} - Add a federation admin
DELETE /federations/{id}/admins/{userId} - Remove a federation admin

Modified Endpoints
1. Resource Management
The resource endpoints now consider federation context and permissions:
GET /resources                     - Now returns only resources from federations the user belongs to
GET /resources?federationId={id}   - Filter resources by federation
POST /resources                    - Creation now requires federation ID and permission check
PUT /resources/{id}                - Update now enforces federation permissions
DELETE /resources/{id}             - Delete now enforces federation permissions

2. Resource Type Management
GET /resource-types                - Now returns only types from federations the user belongs to
GET /resource-types?federationId={id} - Filter types by federation
POST /resource-types               - Creation now requires federation ID and permission check
PUT /resource-types/{id}           - Update now enforces federation permissions
DELETE /resource-types/{id}        - Delete now enforces federation permissions

3. User Management
GET /users                         - For federation admins, only returns users in their federation
POST /users                        - User creation now requires federation assignment

Model Changes
1. Federation DTO
interface Federation {
  id: string;
  name: string;
  description: string;
  adminIds?: string[];
  adminNames?: string[];
}

2. Updated Resource DTO
interface Resource {
  // Existing fields
  federationId: string;
  federationName: string;
}
3. Updated ResourceType DTO
interface ResourceType {
  // Existing fields
  federationId: string;
  federationName: string;
}
4. Updated User DTO
interface User {
  // Existing fields
  federations: Federation[];
  federationIds: string[];
}

UI Implementation Requirements
1. Federation Management

Add a new "Federations" section to the admin dashboard (visible only to GLOBAL_ADMINs)
Create federation management screens (list, create, edit, delete)
Add user-to-federation assignment interface
Implement federation admin assignment interface

2. Federation Context Selector

Add a federation dropdown in the header/navigation for users who belong to multiple federations
Selected federation should filter the view to show only resources from that federation
For GLOBAL_ADMIN, add an "All Federations" option

3. Resource Creation/Editing

Add federation selection to resource and resource type creation forms
For FEDERATION_ADMINs, pre-select and disable their federation
For GLOBAL_ADMINs, provide a dropdown of all federations

4. User Management

Enhance user creation to include federation assignment
Add federation membership display to user details
Allow federation management for appropriate admin roles

5. Role-Based UI Adjustments

Adjust UI visibility based on the new role structure:

GLOBAL_ADMIN: Show all options
FEDERATION_ADMIN: Show only their federation options
USER: Hide administrative options



Authentication Changes
The current JWT token now includes:

User roles (GLOBAL_ADMIN, FEDERATION_ADMIN, USER)
Federation memberships in the groups claim

Your authentication service should extract these values for permission checking:
// Example token parsing
const userRoles = decodedToken.realm_access.roles;
const userFederations = decodedToken.groups.map(group => group.id);

const isGlobalAdmin = userRoles.includes('GLOBAL_ADMIN');
const isFederationAdmin = userRoles.includes('FEDERATION_ADMIN');

Integration Timeline

Update your API client to handle the new endpoints and parameters
Implement the federation selector in the UI
Update resource and user management screens
Add federation management screens

We recommend first updating the context handling (federation selector) and then progressively enhancing each section of the application.
Testing Scenarios

Global Admin: Can manage all federations, resources, and users
Federation Admin: Can only manage resources and users within their federation
Multi-Federation User: Can view resources across their federations
Single-Federation User: Can only view resources in their federation

Please refer to the API documentation for details on each endpoint and its parameters.