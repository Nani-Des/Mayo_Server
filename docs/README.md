# Mayo EMR Documentation Index

This document serves as the central index for all Mayo EMR project documentation. Documentation is organized by category for easy navigation.

## Table of Contents

- [Architecture Documentation](#architecture-documentation)
- [API Specifications](#api-specifications)
- [Database Documentation](#database-documentation)
- [Deployment Guides](#deployment-guides)
- [Security Guidelines](#security-guidelines)
- [Service Interaction Diagrams](#service-interaction-diagrams)

## Architecture Documentation

Core architectural documentation for the EMR system.

- [EMR Architecture Documentation](architecture/EMR_Architecture_Documentation.md) - Comprehensive overview of the EMR system architecture, including microservices design, data flow, and system components.
- [Service Interaction Diagrams](architecture/Service_Interaction_Diagrams.md) - Visual diagrams showing interactions between services, data flows, and system integration points.

## API Specifications

OpenAPI specifications for all microservices. Each service includes both YAML specification files and rendered Markdown documentation where available.

### Core Services
- [Audit Service API](api/Audit_Service_API.yaml) | [Rendered](api/Audit_Service_API.md) - API specification for audit logging and compliance tracking.
- [Auth Service API](api/Auth_Service_API.yaml) - Enhanced authentication and authorization with multi-factor support.
- [Patient Record Service API](api/Patient_Record_Service_API.yaml) - Patient data management and record operations.
- [Device Registry Service API](api/Device_Registry_Service_API.yaml) - Device pairing, registration, and management API.
- [Sync Service API](api/Sync_Service_API.yaml) - Device synchronization and conflict resolution API.

### Integration Services
- [Hospital Integration Service API](api/Hospital_Integration_Service_API.yaml) - External hospital system integration with HL7, FHIR, DICOM support.
- [Notification Service API](api/Notification_Service_API.yaml) | [Rendered](api/Notification_Service_API.md) - Notification and alerting system API.

*Cross-reference: See [EMR Architecture Documentation](EMR_Architecture_Documentation.md) for service relationships and [Security Guidelines](Security_Guidelines.md) for API security requirements.*

## Database Documentation

Database schema and data model documentation.

- [Database Schema Documentation](database/Database_Schema_Documentation.md) - Complete database schema, table structures, relationships, and data migration information.

*Cross-reference: Refer to [EMR Architecture Documentation](architecture/EMR_Architecture_Documentation.md) for data architecture patterns and [Security Guidelines](security/Security_Guidelines.md) for data protection requirements.*

## Deployment Guides

Instructions for deploying and operating the EMR system.

- [Deployment Guide](deployment/Deployment_Guide.md) - Comprehensive deployment instructions, environment setup, and operational procedures.

*Cross-reference: See [EMR Architecture Documentation](architecture/EMR_Architecture_Documentation.md) for infrastructure requirements and [Security Guidelines](security/Security_Guidelines.md) for secure deployment practices.*

## Security Guidelines

Security policies, best practices, and compliance requirements.

- [Security Guidelines](security/Security_Guidelines.md) - Security policies, encryption standards, access controls, and compliance frameworks.

*Cross-reference: Referenced throughout API specifications and deployment guides for security implementation details.*

## Service Interaction Diagrams

Visual representations of service interactions and data flows.

- [Service Interaction Diagrams](architecture/Service_Interaction_Diagrams.md) - Detailed diagrams of service communications, event flows, and integration patterns.

*Cross-reference: See [EMR Architecture Documentation](architecture/EMR_Architecture_Documentation.md) for architectural context and API specifications for endpoint details.*

---

## Quick Start

For new developers or team members:
1. Start with [EMR Architecture Documentation](architecture/EMR_Architecture_Documentation.md) for system overview
2. Review [Database Schema Documentation](database/Database_Schema_Documentation.md) for data models
3. Follow [Deployment Guide](deployment/Deployment_Guide.md) for local setup
4. Consult [Security Guidelines](security/Security_Guidelines.md) for development practices
5. Reference relevant API specifications for service integration

## Contributing to Documentation

When adding new documentation:
- Place files in the appropriate category directory under `docs/`
- Update this index with proper categorization and cross-references
- Ensure links are relative and functional
- Follow existing naming conventions

---

*Last updated: 2025-12-03*