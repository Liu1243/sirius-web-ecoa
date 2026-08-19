## ============================================================
##  sirius-web-ecoa 开发环境快捷命令
##  用法：make <target>
## ============================================================

COMPOSE_FILE := docker-compose.dev.yml
COMPOSE      := docker compose -f $(COMPOSE_FILE)

.PHONY: help up down build rebuild logs ps clean

## 默认目标：打印帮助
help:
	@echo ""
	@echo "  make up        启动所有服务（按正确顺序构建镜像后启动）"
	@echo "  make down      停止并移除所有容器"
	@echo "  make build     仅构建镜像（不启动）"
	@echo "  make rebuild   强制重新构建所有镜像（--no-cache）后启动"
	@echo "  make logs      实时查看所有服务日志"
	@echo "  make ps        查看服务运行状态"
	@echo "  make clean     停止容器并删除所有相关 volume"
	@echo ""

## 按正确顺序构建并启动
## 必须先构建 ecoa-tools，code-server 才能以它为 FROM 基础镜像
up: build
	$(COMPOSE) up -d

## 按正确顺序构建镜像
##   1. ecoa-tools（被 code-server Dockerfile 作为 FROM 基础）
##   2. code-server（依赖上一步产出的本地镜像）
build:
	@echo ">>> [1/2] 构建 ecoa-tools 镜像..."
	$(COMPOSE) build ecoa-tools
	@echo ">>> [2/2] 构建 code-server 镜像..."
	$(COMPOSE) build code-server

## 强制无缓存重新构建后启动
rebuild:
	@echo ">>> [1/2] 重新构建 ecoa-tools 镜像（--no-cache）..."
	$(COMPOSE) build --no-cache ecoa-tools
	@echo ">>> [2/2] 重新构建 code-server 镜像（--no-cache）..."
	$(COMPOSE) build --no-cache code-server
	$(COMPOSE) up -d

## 停止并移除容器（保留 volume）
down:
	$(COMPOSE) down

## 实时日志
logs:
	$(COMPOSE) logs -f

## 服务状态
ps:
	$(COMPOSE) ps

## 停止容器并删除 volume（谨慎！数据库数据也会清除）
clean:
	$(COMPOSE) down -v

