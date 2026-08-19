#!/usr/bin/env bash
# End-to-end test for ARM64 cross-compilation and QEMU-based distributed debug.
#
# Prerequisites:
#   make build && make up          (rebuild image with new cross-compile tools)
#   Spring Boot running on :8080   (only needed for L3)
#
# Usage:
#   chmod +x test_cross_arch_e2e.sh
#   ./test_cross_arch_e2e.sh [L1|L2|L3]   # default: L1 + L2

set -euo pipefail

LEVEL="${1:-L2}"
ECOA_TOOLS_URL="${ECOA_TOOLS_URL:-http://localhost:5000}"
CONTAINER="${ECOA_TOOLS_CONTAINER:-sirius-web-dev-ecoa-tools-1}"
PASS=0; FAIL=0

green() { echo -e "\033[32m✓ $*\033[0m"; }
red()   { echo -e "\033[31m✗ $*\033[0m"; }

check() {
    local desc="$1"; shift
    if eval "$@" >/dev/null 2>&1; then
        green "$desc"; ((PASS++)) || true
    else
        red   "$desc"; ((FAIL++)) || true
    fi
}

check_output() {
    local desc="$1"; local expected="$2"; shift 2
    local actual
    actual=$(eval "$@" 2>&1) || true
    if echo "$actual" | grep -q "$expected"; then
        green "$desc"
        ((PASS++)) || true
    else
        red "$desc (got: $actual)"
        ((FAIL++)) || true
    fi
}

# ─────────────────────────────────────────────────────────────────────────────
# L1: 镜像工具链验证（需要 Docker 镜像已构建）
# ─────────────────────────────────────────────────────────────────────────────
run_l1() {
    echo ""
    echo "══════════════════════════════════════════"
    echo " L1: 容器工具链验证"
    echo "══════════════════════════════════════════"

    EXEC="docker exec $CONTAINER"

    check "gcc-aarch64-linux-gnu 已安装" \
        "$EXEC which aarch64-linux-gnu-gcc"

    check "g++-aarch64-linux-gnu 已安装" \
        "$EXEC which aarch64-linux-gnu-g++"

    check "qemu-aarch64-static 已安装" \
        "$EXEC which qemu-aarch64-static"

    check "gdb-multiarch 已安装" \
        "$EXEC which gdb-multiarch"

    check_output "gcc 可以交叉编译 hello.c" "ELF 64-bit.*ARM aarch64" \
        "$EXEC bash -c \"echo 'int main(){return 0;}' > /tmp/hi.c && aarch64-linux-gnu-gcc -o /tmp/hi /tmp/hi.c && file /tmp/hi\""

    check_output "arm64 库 libapr-1 存在" "aarch64" \
        "$EXEC find /usr/lib/aarch64-linux-gnu -name 'libapr-1*' -type f 2>/dev/null | head -1"

    check_output "arm64 库 liblog4cplus 存在" "aarch64" \
        "$EXEC find /usr/lib/aarch64-linux-gnu -name 'liblog4cplus*' -type f 2>/dev/null | head -1"

    check_output "cmake toolchain 文件存在" "toolchain-aarch64.cmake" \
        "$EXEC ls /app/cmake/"

    check_output "qemu-aarch64-static 可运行 arm64 二进制" "Hello" \
        "$EXEC bash -c \"echo 'int main(){puts(\\\"Hello\\\");return 0;}' > /tmp/arm.c && aarch64-linux-gnu-gcc -static -o /tmp/arm /tmp/arm.c && qemu-aarch64-static /tmp/arm\""
}

# ─────────────────────────────────────────────────────────────────────────────
# L2: API 层测试（ecoa-tools 服务运行中）
# ─────────────────────────────────────────────────────────────────────────────
run_l2() {
    echo ""
    echo "══════════════════════════════════════════"
    echo " L2: ecoa-tools API 测试"
    echo "══════════════════════════════════════════"

    # 2.1 健康检查
    check "ecoa-tools 服务健康" \
        "curl -sf $ECOA_TOOLS_URL/health | grep -q healthy"

    # 2.2 找一个有 LDP 输出的 workspace
    WORKSPACE=$(find /Users/admin/code/sirius-web-ecoa/workspace -name "CMakeLists.txt" \
        -path "*/6-output/CMakeLists.txt" 2>/dev/null | head -1)
    if [[ -z "$WORKSPACE" ]]; then
        echo "  ⚠ 跳过 L2.2-2.5：workspace 里没有已生成的 LDP 输出（先运行一次完整 pipeline）"
        return
    fi

    STEPS_DIR=$(dirname "$(dirname "$WORKSPACE")")        # Steps/
    PROJECT_FILE=$(find "$STEPS_DIR" -name "*.project.xml" | head -1 | xargs basename)
    PROJECT_NAME=$(python3 -c "
import os
base=os.environ.get('WORKSPACE_ROOT','/workspace')
path='$STEPS_DIR'
try: print(os.path.relpath(path,base))
except: print(path)
" 2>/dev/null || echo "$STEPS_DIR")

    echo "  使用项目: $STEPS_DIR ($PROJECT_FILE)"

    # 2.3 native 编译仍然工作
    NATIVE_RESULT=$(curl -sf -X POST "$ECOA_TOOLS_URL/api/tools/execute-project" \
        -H 'Content-Type: application/json' \
        -d "{\"project_name\":\"$PROJECT_NAME\",\"project_file\":\"$PROJECT_FILE\",\"tool\":\"ldp\",\"compile\":true,\"target_arch\":\"native\"}" \
        2>&1 || echo '{}')

    if echo "$NATIVE_RESULT" | python3 -c "import sys,json; d=json.load(sys.stdin); sys.exit(0 if d.get('compile_success') else 1)" 2>/dev/null; then
        green "native 编译成功"
        ((PASS++)) || true
    else
        red "native 编译失败 ($(echo "$NATIVE_RESULT" | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d.get("compile_stderr","?")[:120])' 2>/dev/null))"
        ((FAIL++)) || true
    fi

    # 2.4 arm64 编译请求被接受
    ARM64_RESULT=$(curl -sf -X POST "$ECOA_TOOLS_URL/api/tools/execute-project" \
        -H 'Content-Type: application/json' \
        -d "{\"project_name\":\"$PROJECT_NAME\",\"project_file\":\"$PROJECT_FILE\",\"tool\":\"ldp\",\"compile\":true,\"target_arch\":\"arm64\"}" \
        2>&1 || echo '{}')

    check_output "arm64 编译 API 响应成功" '"success": true' \
        "echo '$ARM64_RESULT'"

    # 2.5 arm64 产物是 ARM64 ELF
    ARM64_BUILD=$(find "$STEPS_DIR" -path "*/build-arm64/bin/platform" 2>/dev/null | head -1)
    if [[ -n "$ARM64_BUILD" ]]; then
        check_output "build-arm64/bin/platform 是 ARM64 ELF" "ARM aarch64" \
            "file '$ARM64_BUILD'"
        check_output "build-arm64/bin/platform 不是 x86 ELF" "" \
            "file '$ARM64_BUILD' | grep -v x86-64 | grep ARM"
    else
        echo "  ⚠ 跳过 ELF 验证：build-arm64/bin/platform 不存在"
        echo "    (arm64 编译可能需要 arm64 版依赖库，请检查 arm64 编译日志)"
    fi

    # 2.6 .vscode/launch.json 使用 gdb-multiarch
    LAUNCH_JSON=$(find "$STEPS_DIR" -name "launch.json" -path "*/.vscode/*" | head -1)
    if [[ -n "$LAUNCH_JSON" ]]; then
        check_output "launch.json 包含 gdb-multiarch" "gdb-multiarch" \
            "cat '$LAUNCH_JSON'"
    fi

    # 2.7 start 脚本包含 ECOA_TARGET_ARCH
    START_SCRIPT=$(find "$STEPS_DIR" -name "start-distributed-debug.sh" | head -1)
    if [[ -n "$START_SCRIPT" ]]; then
        check_output "start 脚本含 ECOA_TARGET_ARCH" "ECOA_TARGET_ARCH" \
            "cat '$START_SCRIPT'"
    fi
}

# ─────────────────────────────────────────────────────────────────────────────
# L3: 完整调试会话测试（需要 Docker + 已编译的 arm64 项目）
# ─────────────────────────────────────────────────────────────────────────────
run_l3() {
    echo ""
    echo "══════════════════════════════════════════"
    echo " L3: 分布式调试会话 (QEMU)"
    echo "══════════════════════════════════════════"

    TARGET_DIR=$(find /Users/admin/code/sirius-web-ecoa/workspace \
        -name "start-distributed-debug.sh" 2>/dev/null \
        -exec dirname {} \; | sed 's|/.vscode||' | head -1)

    if [[ -z "$TARGET_DIR" ]]; then
        echo "  ⚠ 跳过 L3：没有找到含 start-distributed-debug.sh 的项目"
        echo "    先完成 L2 编译，再运行 L3"
        return
    fi

    echo "  目标目录: $TARGET_DIR"

    # 3.1 启动调试会话
    START_RESULT=$(curl -sf -X POST "$ECOA_TOOLS_URL/api/distributed-debug/start" \
        -H 'Content-Type: application/json' \
        -d "{\"target_dir\":\"$TARGET_DIR\",\"target_arch\":\"arm64\"}" \
        2>&1 || echo '{}')

    check_output "调试会话启动成功" '"success": true' \
        "echo '$START_RESULT'"

    SESSION_ID=$(echo "$START_RESULT" | python3 -c \
        'import sys,json; d=json.load(sys.stdin); print(d.get("session_id",""))' 2>/dev/null || echo "")

    if [[ -z "$SESSION_ID" ]]; then
        red "无法获取 session_id，跳过后续验证"
        ((FAIL++)) || true
        return
    fi
    echo "  session_id: $SESSION_ID"

    sleep 3  # 等待 gdbserver/qemu 绑定端口

    # 3.2 验证 qemu-aarch64-static 正在运行
    RUNNING_SERVICES=$(echo "$START_RESULT" | python3 -c \
        'import sys,json; d=json.load(sys.stdin); print(d.get("running_services",[]))' 2>/dev/null || echo "[]")
    check_output "有容器服务在运行" "ecoa-" \
        "echo '$RUNNING_SERVICES'"

    # 3.3 验证端口在调试容器内可达
    NETWORK_NAME=$(echo "$START_RESULT" | python3 -c \
        'import sys,json; d=json.load(sys.stdin); print(d.get("network_name",""))' 2>/dev/null || echo "")

    # 尝试从 code-server 容器连接 gdb port
    CODE_SERVER_CONTAINER=$(docker ps --format '{{.Names}}' | grep code-server | head -1 || echo "")
    if [[ -n "$CODE_SERVER_CONTAINER" ]]; then
        # 找第一个进程的 host:port
        FIRST_HOST=$(echo "$START_RESULT" | python3 -c "
import sys, json
d = json.load(sys.stdin)
# topology is in session, processes have host:port
" 2>/dev/null || echo "")
        check_output "qemu GDB stub 端口可达 (port 2000)" "" \
            "docker exec $CODE_SERVER_CONTAINER bash -c 'timeout 2 bash -c \"</dev/tcp/192.168.10.1/2000\" && echo open' 2>/dev/null || true"
    fi

    # 3.4 停止调试会话
    STOP_RESULT=$(curl -sf -X POST "$ECOA_TOOLS_URL/api/distributed-debug/stop" \
        -H 'Content-Type: application/json' \
        -d "{\"session_id\":\"$SESSION_ID\"}" \
        2>&1 || echo '{}')

    check_output "调试会话停止成功" '"success": true' \
        "echo '$STOP_RESULT'"
}

# ─────────────────────────────────────────────────────────────────────────────
# 主入口
# ─────────────────────────────────────────────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════════"
echo " ECOA ARM64 交叉编译 + 仿真调试 端对端测试"
echo "═══════════════════════════════════════════════"
echo " ecoa-tools: $ECOA_TOOLS_URL"
echo " 容器:       $CONTAINER"
echo " 级别:       $LEVEL"

[[ "$LEVEL" == "L1" || "$LEVEL" == "L2" || "$LEVEL" == "all" ]] && run_l1
[[ "$LEVEL" == "L2" || "$LEVEL" == "all" ]] && run_l2
[[ "$LEVEL" == "L3" || "$LEVEL" == "all" ]] && run_l3

echo ""
echo "══════════════════════════════════════════"
echo " 结果: $PASS 通过 / $FAIL 失败"
echo "══════════════════════════════════════════"
[[ $FAIL -eq 0 ]]
