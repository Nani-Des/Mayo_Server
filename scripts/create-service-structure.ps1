# Mayo_Server Service Generator Script
# This script generates a specific microservice with complete structure

param(
    [Parameter(Mandatory=$true)]
    [string]$ServiceName
)

$BASE_DIR = "c:\raved\Mayo_Server"

Write-Host "Generating Mayo EMR Microservice: $ServiceName..." -ForegroundColor Cyan

# Service definitions
$services = @{
    "patient-service" = @{
        port = 8082
        package = "patient"
        appName = "PatientServiceApplication"
        schema = "patient"
    }
    "medical-record-service" = @{
        port = 8083
        package = "records"
        appName = "MedicalRecordApplication"
        schema = "medical_record"
    }
    "hospital-service" = @{
        port = 8084
        package = "hospital"
        appName = "HospitalServiceApplication"
        schema = "hospital"
    }
    "sync-service" = @{
        port = 8085
        package = "sync"
        appName = "SyncServiceApplication"
        schema = "sync"
    }
    "audit-service" = @{
        port = 8086
        package = "audit"
        appName = "AuditServiceApplication"
        schema = "audit"
    }
    "notification-service" = @{
        port = 8087
        package = "notification"
        appName = "NotificationApplication"
        schema = "notification"
    }
    "hospital-integration-service" = @{
        port = 8088
        package = "hospitalintegration"
        appName = "HospitalIntegrationServiceApplication"
        schema = "hospital_integration"
    }
}

if (-not $services.ContainsKey($ServiceName)) {
    Write-Host "Service '$ServiceName' not found in definitions." -ForegroundColor Red
    exit 1
}

$service = $services[$ServiceName]
$serviceDir = Join-Path $BASE_DIR "services\$ServiceName"

Write-Host "Creating $ServiceName..." -ForegroundColor Yellow

# Create directory structure
$dirs = @(
    "$serviceDir\src\main\java\com\mayo\$($service.package)\controller",
    "$serviceDir\src\main\java\com\mayo\$($service.package)\service",
    "$serviceDir\src\main\java\com\mayo\$($service.package)\repository",
    "$serviceDir\src\main\java\com\mayo\$($service.package)\entity",
    "$serviceDir\src\main\java\com\mayo\$($service.package)\dto",
    "$serviceDir\src\main\java\com\mayo\$($service.package)\config",
    "$serviceDir\src\main\resources\db\migration",
    "$serviceDir\src\test\java\com\mayo\$($service.package)"
)

foreach ($dir in $dirs) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
}

Write-Host "Created directory structure for $ServiceName" -ForegroundColor Green

Write-Host "Service directory created successfully!" -ForegroundColor Green
