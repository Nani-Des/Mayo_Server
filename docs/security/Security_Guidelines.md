# EMR Backend Security Guidelines

## Overview

This document outlines comprehensive security guidelines for the EMR (Electronic Medical Records) backend system. It covers authentication, authorization, data encryption, network security, compliance requirements, secure coding practices, access controls, and incident response procedures. These guidelines reference existing security configurations and certificate management practices implemented in the system.

## 1. Authentication and Authorization

### JWT Token-Based Authentication

The system uses JSON Web Tokens (JWT) for authentication, implemented through the `JwtTokenProvider` service:

- **Algorithm**: HS512 for token signing
- **Access Token Expiry**: 15 minutes (900,000 ms)
- **Refresh Token Expiry**: 7 days (604,800,000 ms)
- **Token Types**: ACCESS, REFRESH, DEVICE_ACCESS, DEVICE_REFRESH

Tokens include claims for:
- User ID (UUID)
- Email address
- User type (PATIENT, PROVIDER, ADMIN, etc.)
- Permissions set
- Device ID (for device-specific tokens)

### Role-Based Access Control (RBAC)

Authorization is enforced through the `AuthorizationService` with permission-based access control:

- **Permission Enum**: Defines granular permissions like `PATIENT_READ_OWN_RECORDS`, `PROVIDER_READ_PATIENT_RECORDS`, `ADMIN_MANAGE_USERS`
- **User Types**: PATIENT, PROVIDER, HOSPITAL_STAFF, ADMIN with hierarchical access levels
- **Resource-Based Checks**: Users can access their own resources or have role-based permissions for others

### Multi-Factor Authentication (MFA)

- Required for all administrative accounts
- Optional but recommended for healthcare providers
- Device-specific tokens for enhanced security in distributed environments

## 2. Data Encryption

### Data at Rest Encryption

Implemented via `AesEncryptionService` using AES-256-GCM:

- **Algorithm**: AES/GCM/NoPadding
- **Key Size**: 256 bits
- **Key Rotation**: Every 90 days (configurable)
- **Initialization Vector**: 12 bytes random IV per encryption
- **Authentication Tag**: 16 bytes GCM tag

Sensitive data including PHI (Protected Health Information) is encrypted before storage.

### Data in Transit Encryption

- **TLS 1.3** mandatory for all communications
- **Mutual TLS (mTLS)** for service-to-service communication
- **Certificate-based authentication** for device connections
- **Supported Ciphers**: TLS_AES_256_GCM_SHA384, TLS_AES_128_GCM_SHA256, TLS_CHACHA20_POLY1305_SHA256

### Key Management

- **HashiCorp Vault** integration for secret management
- **Database credentials** stored in Vault at `database/creds/emr-app`
- **JWT secrets** managed at `secret/emr/jwt`
- **Certificate management** via Vault PKI at `pki_int/issue/emr`

## 3. Network Security

### Web Application Firewall (WAF)

Configured in `infrastructure/security-config.yml`:

- **SQL Injection Protection**: Enabled
- **XSS Prevention**: Enabled
- **Path Traversal Protection**: Enabled
- **Command Injection Prevention**: Enabled
- **Suspicious Pattern Detection**: Enabled

### Rate Limiting and DDoS Protection

- **Replenish Rate**: 10 requests per second
- **Burst Capacity**: 20 requests
- **DDoS Protection**: Enabled with connection limits (1000 max connections, 30s timeout)

### Service Mesh Security

- **Istio/Envoy Integration**: Enabled
- **Mutual TLS**: Mandatory for service-to-service communication
- **Traffic Policies**: Strict mTLS enforcement and rate limiting

### Network Segmentation

- **API Gateway**: Central entry point with JWT validation and request filtering
- **Service Isolation**: Microservices communicate through secure channels
- **Database Isolation**: Direct database access prohibited; all access through services

## 4. Compliance Requirements

### HIPAA Compliance

**Security Rule Requirements**:
- **Access Control**: RBAC with least privilege principle
- **Audit Controls**: Comprehensive audit logging via `SecurityAuditService`
- **Integrity**: Data integrity checks and backup encryption
- **Transmission Security**: TLS 1.3 with certificate validation

**Privacy Rule Requirements**:
- **Minimum Necessary**: Permission-based data access
- **Individual Rights**: Patients can access their own records
- **Business Associate Agreements**: Required for third-party integrations

### GDPR Compliance

**Data Protection Principles**:
- **Lawfulness, Fairness, Transparency**: Clear consent mechanisms and data usage policies
- **Purpose Limitation**: Data collected only for specified healthcare purposes
- **Data Minimization**: Only necessary PHI collected and retained
- **Accuracy**: Data validation and integrity checks
- **Storage Limitation**: 7-year active retention, 24-month Elasticsearch retention
- **Integrity and Confidentiality**: AES-256 encryption and access controls
- **Accountability**: Comprehensive audit trails and compliance reporting

**Data Subject Rights**:
- **Access**: Patients can view their data via secure APIs
- **Rectification**: Data correction capabilities
- **Erasure**: Secure data deletion procedures
- **Portability**: Data export functionality
- **Restriction**: Data processing restrictions

### SOC 2 Compliance

- **Security**: Technical safeguards and access controls
- **Availability**: Redundant systems and disaster recovery
- **Processing Integrity**: Data validation and error handling
- **Confidentiality**: Encryption and access restrictions
- **Privacy**: Data handling and consent management

## 5. Secure Coding Practices

### Input Validation and Sanitization

- **Maximum Length Limits**: String (1000), Email (254), Password (128)
- **Pattern Validation**: Email regex, phone number validation
- **SQL Injection Prevention**: Parameterized queries and ORM usage
- **XSS Prevention**: Input sanitization and output encoding

### Error Handling

- **Information Disclosure**: Generic error messages without sensitive details
- **Logging**: Security events logged without exposing sensitive data
- **Exception Management**: Secure exception handling without stack trace exposure

### Secure Dependencies

- **Automated Scanning**: Daily vulnerability scans using OWASP ZAP, SonarQube, and dependency-check
- **Vulnerability Threshold**: HIGH severity alerts trigger immediate response
- **Patch Management**: Regular dependency updates and security patches

### Code Review Requirements

- **Security Review**: Mandatory for all code changes affecting security
- **Static Analysis**: Automated security scanning in CI/CD pipeline
- **Peer Review**: Two-person rule for security-critical code

## 6. Access Controls

### Principle of Least Privilege

- **Role Assignment**: Users assigned minimum permissions required for their duties
- **Permission Granularity**: Fine-grained permissions for specific operations
- **Regular Reviews**: Quarterly access review and cleanup

### Access Monitoring

- **Real-time Monitoring**: Failed authentication attempts tracked
- **Anomaly Detection**: Unusual access patterns flagged
- **Session Management**: Automatic logout on inactivity

### Device Management

- **Device Registration**: All devices must be registered and authenticated
- **Certificate-based Access**: Device certificates for secure connections
- **Device-specific Tokens**: Enhanced security for mobile and remote access

## 7. Incident Response Procedures

### Incident Classification

- **Critical**: System-wide compromise, data breach, service unavailability
- **High**: Unauthorized access, malware detection, security policy violation
- **Medium**: Failed authentication attempts, suspicious activity
- **Low**: Minor security events, configuration issues

### Response Phases

1. **Detection and Assessment** (0-15 minutes)
   - Security monitoring alerts trigger response
   - Initial assessment by on-call security team
   - Incident classification and severity determination

2. **Containment** (15-60 minutes)
   - Isolate affected systems
   - Disable compromised accounts
   - Implement emergency access controls

3. **Investigation** (1-24 hours)
   - Forensic analysis of affected systems
   - Root cause and impact assessment
   - Documentation of findings

4. **Recovery** (Hours to days)
   - Restore from clean backups
   - Verify system integrity
   - Gradual return to normal operations

5. **Lessons Learned** (Post-incident)
   - Incident debriefing
   - Security control updates
   - Preventive measure implementation

### Communication Procedures

- **Internal**: Immediate team notification, regular updates, 24-hour post-incident report
- **External**: Customer notification within 72 hours for breaches, regulatory reporting as required

### Technical Response Procedures

- **Authentication Incidents**: Account disabling, forced password resets, log review
- **Malware Detection**: System isolation, antivirus scans, signature updates
- **Data Breach**: Containment, exposure assessment, individual notification
- **DDoS Attack**: Mitigation service activation, rate limiting, traffic filtering

## 8. Certificate Management

### Certificate Types

- **CA Certificate**: 10-year validity for root certificate authority
- **Server Certificates**: 1-year validity with auto-renewal
- **Client Certificates**: 1-year validity for device authentication

### Certificate Lifecycle

- **Generation**: Automated via HashiCorp Vault PKI
- **Renewal**: Automatic renewal 30 days before expiry
- **Revocation**: Immediate revocation for compromised certificates
- **Distribution**: Secure distribution to authorized devices and services

### Certificate Validation

- **Chain Validation**: Full certificate chain verification
- **CRL/OCSP**: Certificate revocation checking
- **Hostname Verification**: Certificate hostname matching

## 9. Monitoring and Alerting

### Security Monitoring

- **Audit Events**: All security events published to Kafka topic `audit.events`
- **Metrics Collection**: Failed authentications, blocked requests, security incidents
- **Real-time Alerts**: PagerDuty, Slack, and email notifications for critical events

### Log Management

- **Centralized Logging**: All security events aggregated
- **Retention Policies**: 7-year active retention, 24-month search retention
- **Log Integrity**: Cryptographic hashing to prevent tampering

### Security Dashboard

- **Grafana Integration**: Real-time security metrics visualization
- **Alert Thresholds**: Configurable thresholds for automated responses
- **Compliance Reporting**: Automated compliance status reports

## 10. Backup and Recovery

### Backup Security

- **Encryption**: All backups encrypted with AES-256
- **Secure Transport**: TLS-encrypted backup transmission
- **Integrity Checks**: Cryptographic verification of backup integrity

### Recovery Procedures

- **Clean Recovery**: Restore from verified, encrypted backups
- **System Verification**: Post-recovery security control validation
- **Gradual Rollout**: Phased system restoration to minimize risk

## 11. Security Testing

### Automated Security Scanning

- **Schedule**: Daily security scans at 2 AM
- **Tools**: OWASP ZAP, SonarQube, dependency-check
- **Threshold**: HIGH severity vulnerabilities trigger alerts

### Penetration Testing

- **Frequency**: Quarterly external penetration testing
- **Scope**: All external-facing systems and APIs
- **Reporting**: Detailed findings with remediation plans

### Vulnerability Management

- **Assessment**: Continuous vulnerability scanning
- **Prioritization**: CVSS-based severity scoring
- **Remediation**: 30-day SLA for critical vulnerabilities

## 12. Security Training and Awareness

### Personnel Training

- **Security Awareness**: Annual mandatory training for all staff
- **Role-specific Training**: Specialized training for administrators and developers
- **Incident Response Drills**: Regular simulation exercises

### Developer Security Training

- **Secure Coding**: OWASP guidelines and best practices
- **Code Review Training**: Security-focused code review processes
- **Threat Modeling**: Application threat modeling workshops

## References

- `infrastructure/security-config.yml`: Main security configuration file
- `infrastructure/operational-runbooks/Security_Incident_Response_Procedures.md`: Detailed incident response procedures
- `common/security/jwt/JwtTokenProvider.java`: JWT token implementation
- `common/security/encryption/AesEncryptionService.java`: Data encryption service
- `common/security/authorization/AuthorizationService.java`: Authorization logic
- `common/security/audit/SecurityAuditService.java`: Security audit logging
- `certs/`: Certificate storage directory structure
- `monitoring/grafana/security-dashboard.json`: Security monitoring dashboard configuration