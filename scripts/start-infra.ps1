# Start all Docker infra from project root (Windows PowerShell)
$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

Write-Host "Starting all Docker services..." -ForegroundColor Cyan
docker compose up -d --build

Write-Host ""
Write-Host "Service status:" -ForegroundColor Cyan
docker compose ps

Write-Host ""
Write-Host "MySQL replication auto-configures when mt-mysql-slave starts." -ForegroundColor Green
Write-Host "Kafka UI:          http://localhost:8080" -ForegroundColor Green
Write-Host "Redis Commander:   http://localhost:8081" -ForegroundColor Green
Write-Host "Adminer (MySQL):   http://localhost:8089" -ForegroundColor Green
