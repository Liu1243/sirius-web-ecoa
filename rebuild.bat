@echo off
REM ============================================================
REM  ECOA Sirius Web — 重建并重启脚本 (Windows)
REM  用法: rebuild.bat
REM ============================================================
setlocal

set COMPOSE_FILE=docker-compose.dev.yml

cd /d "%~dp0"

echo ============================================================
echo   停止并移除所有容器...
echo ============================================================
docker compose -f %COMPOSE_FILE% down

echo.
echo [1/2] 构建 ecoa-tools 镜像...
docker compose -f %COMPOSE_FILE% build --no-cache ecoa-tools

echo.
echo [2/2] 构建 code-server 镜像...
docker compose -f %COMPOSE_FILE% build --no-cache code-server

echo.
echo 启动所有服务...
docker compose -f %COMPOSE_FILE% up -d

echo.
echo 完成! 服务状态:
docker compose -f %COMPOSE_FILE% ps

endlocal
