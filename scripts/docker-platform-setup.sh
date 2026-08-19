#!/bin/bash
# =============================================================================
# Docker 平台自动检测与配置脚本
# 根据宿主机的架构自动设置 Docker 平台参数
# =============================================================================

set -e

# 检测宿主机的架构
ARCH=$(uname -m)
OS=$(uname -s)

# 默认平台
PLATFORM="linux/amd64"

echo "=========================================="
echo "Docker 平台自动检测"
echo "=========================================="
echo "检测到操作系统: $OS"
echo "检测到架构: $ARCH"

# 根据架构设置平台
 case "$ARCH" in
    "arm64"|"aarch64")
        PLATFORM="linux/arm64"
        echo "✓ 检测到 ARM64 架构 (Apple Silicon / ARM64 Linux)"
        echo "✓ 将使用原生 ARM64 镜像以获得最佳性能"
        ;;
    "x86_64"|"amd64")
        PLATFORM="linux/amd64"
        echo "✓ 检测到 AMD64 架构 (Intel / AMD)"
        ;;
    *)
        PLATFORM="linux/amd64"
        echo "⚠ 未知架构: $ARCH，默认使用 linux/amd64"
        ;;
esac

# 检查是否在 Mac 上运行
if [ "$OS" = "Darwin" ]; then
    echo "✓ 检测到 macOS 系统"
    echo "✓ host.docker.internal 应该自动可用"
fi

# 更新 .env 文件中的 DOCKER_PLATFORM
ENV_FILE=".env"

if [ -f "$ENV_FILE" ]; then
    # 如果存在 DOCKER_PLATFORM 行，则更新它
    if grep -q "^DOCKER_PLATFORM=" "$ENV_FILE"; then
        sed -i.bak "s/^DOCKER_PLATFORM=.*/DOCKER_PLATFORM=$PLATFORM/" "$ENV_FILE"
        rm -f "$ENV_FILE.bak"
        echo ""
        echo "✓ 已更新 $ENV_FILE: DOCKER_PLATFORM=$PLATFORM"
    else
        # 如果不存在，添加到文件末尾
        echo "" >> "$ENV_FILE"
        echo "# 自动检测的 Docker 平台配置" >> "$ENV_FILE"
        echo "DOCKER_PLATFORM=$PLATFORM" >> "$ENV_FILE"
        echo ""
        echo "✓ 已添加 DOCKER_PLATFORM=$PLATFORM 到 $ENV_FILE"
    fi
else
    echo "⚠ 警告: 未找到 $ENV_FILE 文件"
fi

echo ""
echo "=========================================="
echo "配置完成！使用以下命令启动服务："
echo "  docker compose up -d"
echo "  或"
echo "  docker compose -f docker-compose.dev.yml up -d"
echo "=========================================="
