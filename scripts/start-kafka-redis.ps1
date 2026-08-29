# Start Kafka + Redis from project root (Windows PowerShell)
$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

Write-Host "Starting Kafka + Redis..." -ForegroundColor Cyan
docker compose up -d redis zookeeper kafka kafka-ui redis-commander

Write-Host ""
Write-Host "Service status:" -ForegroundColor Cyan
docker compose ps redis zookeeper kafka kafka-ui redis-commander

Write-Host ""
Write-Host "Kafka UI:          http://localhost:8080" -ForegroundColor Green
Write-Host "Redis Commander:   http://localhost:8081" -ForegroundColor Green
Write-Host "Redis:             localhost:6379 (password: redis123)" -ForegroundColor Green
Write-Host "Kafka:             localhost:9092" -ForegroundColor Green
