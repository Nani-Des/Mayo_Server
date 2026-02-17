# PowerShell script to generate TLS 1.3 certificates for EMR services
# Requires OpenSSL to be installed

param(
    [string]$OutputDir = "certs",
    [string]$CADir = "$OutputDir/ca",
    [string]$ServerDir = "$OutputDir/server",
    [string]$DeviceDir = "$OutputDir/devices"
)

# Create directories
New-Item -ItemType Directory -Force -Path $CADir, $ServerDir, $DeviceDir

Write-Host "Generating CA certificate..."

# Generate CA private key
openssl genrsa -out "$CADir/ca.key" 4096

# Generate CA certificate
openssl req -new -x509 -days 3650 -key "$CADir/ca.key" -sha256 -out "$CADir/ca.crt" -subj "/C=US/ST=State/L=City/O=Mayo Clinic/OU=EMR/CN=Mayo EMR CA"

Write-Host "Generating server certificate..."

# Generate server private key
openssl genrsa -out "$ServerDir/server.key" 2048

# Generate server certificate signing request
openssl req -subj "/C=US/ST=State/L=City/O=Mayo Clinic/OU=EMR/CN=localhost" -new -key "$ServerDir/server.key" -out "$ServerDir/server.csr"

# Create server certificate extensions file
@"
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, nonRepudiation, keyEncipherment, dataEncipherment
extendedKeyUsage = serverAuth
subjectAltName = @alt_names

[alt_names]
DNS.1 = localhost
DNS.2 = gateway
DNS.3 = auth-service
DNS.4 = patient-record-service
DNS.5 = sync-service
DNS.6 = audit-service
DNS.7 = notification-service
IP.1 = 127.0.0.1
"@ | Out-File -FilePath "$ServerDir/server.ext" -Encoding ASCII

# Generate server certificate
openssl x509 -req -days 365 -in "$ServerDir/server.csr" -CA "$CADir/ca.crt" -CAkey "$CADir/ca.key" -out "$ServerDir/server.crt" -sha256 -CAcreateserial -extfile "$ServerDir/server.ext"

# Create PKCS12 keystore for Java applications
openssl pkcs12 -export -in "$ServerDir/server.crt" -inkey "$ServerDir/server.key" -out "$ServerDir/keystore.p12" -name "server" -CAfile "$CADir/ca.crt" -caname "root" -password pass:changeit

Write-Host "Generating device certificate template..."

# Generate device certificate template
openssl genrsa -out "$DeviceDir/device.key" 2048
openssl req -subj "/C=US/ST=State/L=City/O=Mayo Clinic/OU=EMR/CN=device-template" -new -key "$DeviceDir/device.key" -out "$DeviceDir/device.csr"

@"
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, nonRepudiation, keyEncipherment, dataEncipherment
extendedKeyUsage = clientAuth
"@ | Out-File -FilePath "$DeviceDir/device.ext" -Encoding ASCII

openssl x509 -req -days 365 -in "$DeviceDir/device.csr" -CA "$CADir/ca.crt" -CAkey "$CADir/ca.key" -out "$DeviceDir/device.crt" -sha256 -CAcreateserial -extfile "$DeviceDir/device.ext"

Write-Host "Certificates generated successfully!"
Write-Host "CA Certificate: $CADir/ca.crt"
Write-Host "Server Keystore: $ServerDir/keystore.p12"
Write-Host "Device Certificate: $DeviceDir/device.crt"
Write-Host ""
Write-Host "For production, replace 'changeit' with a secure password and distribute certificates securely."