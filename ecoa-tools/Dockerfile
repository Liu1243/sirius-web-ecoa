# ECOA Tools Development Environment
FROM docker.1ms.run/ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive
WORKDIR /app

# Enable ARM64 multiarch so apt can install :arm64 packages for cross-compilation.
# amd64 cross-compilation from ARM64 is intentionally not supported here — the
# gcc-x86-64-linux-gnu toolchain creates unresolvable apt dependency conflicts on
# ARM64 hosts.  On x86_64 servers, native gcc handles x86_64 and
# gcc-aarch64-linux-gnu handles arm64 cross-compilation.
RUN dpkg --add-architecture arm64

# -------------------------------------------------------------------
# 系统依赖安装
# 使用 BuildKit 缓存挂载：apt 包缓存存于构建机本地，缓存失效时不重复下载
# 注意：--mount=type=cache 挂载的内容不会写入镜像层，无需 apt-get clean
# -------------------------------------------------------------------
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    sed -i 's|archive.ubuntu.com|mirrors.tuna.tsinghua.edu.cn|g' /etc/apt/sources.list && \
    sed -i 's|security.ubuntu.com|mirrors.tuna.tsinghua.edu.cn|g' /etc/apt/sources.list && \
    sed -i 's|ports.ubuntu.com|mirrors.tuna.tsinghua.edu.cn|g' /etc/apt/sources.list && \
    apt-get update && apt-get install -y --no-install-recommends \
    python3 \
    python3-venv \
    python3-dev \
    build-essential \
    gcc \
    g++ \
    gdb \
    gdbserver \
    cmake \
    make \
    bison \
    flex \
    libapr1-dev \
    libaprutil1-dev \
    libcunit1-dev \
    liblog4cplus-dev \
    libxml2-dev \
    libxslt1-dev \
    pkg-config \
    ca-certificates \
    cyclonedds-dev \
    cyclonedds-tools \
    curl \
    docker.io \
    docker-compose-v2 \
    gcc-aarch64-linux-gnu \
    g++-aarch64-linux-gnu \
    binutils-aarch64-linux-gnu \
    gdb-multiarch \
    qemu-user-static \
    qemu-user \
    file \
    liblog4cplus-dev:arm64 \
    libapr1-dev:arm64 \
    libcunit1-dev:arm64

# Verify CycloneDDS is at the expected 0.8.x release (Ubuntu 22.04 ships 0.8.2).
# If apt installs a different major/minor, fail the build early so the
# version bump is noticed and tested before it reaches CI.
# To upgrade: change the grep pattern and re-test the DDS build.
RUN dpkg-query -W -f='${Version}' cyclonedds-dev | grep -qE '^0\.8\.' \
    || { echo "ERROR: expected CycloneDDS 0.8.x but got $(dpkg-query -W -f='${Version}' cyclonedds-dev)" >&2; exit 1; }

# -------------------------------------------------------------------
# 创建 venv + 配置 pip 镜像源（单独一层，极少变化）
# -------------------------------------------------------------------
RUN python3 -m venv /app/.venv && \
    mkdir -p /root/.pip && \
    printf "[global]\nindex-url = https://pypi.tuna.tsinghua.edu.cn/simple\nextra-index-url = https://mirrors.aliyun.com/pypi/simple/\ntimeout = 120\nretries = 5\n" > /root/.pip/pip.conf

ENV VIRTUAL_ENV=/app/.venv
ENV PATH="/app/.venv/bin:$PATH"

# pip / setuptools 升级（单独层，极少变化）
RUN --mount=type=cache,target=/root/.cache/pip \
    python -m pip install --timeout 120 --upgrade "pip>=23" "setuptools<82" wheel

# -------------------------------------------------------------------
# 【层 A】安装 requirements.txt 的第三方依赖
# 只要 requirements.txt 不变此层命中缓存，与 as6-tools 代码无关
# -------------------------------------------------------------------
COPY requirements.txt /app/requirements.txt
RUN --mount=type=cache,target=/root/.cache/pip \
    python -m pip install --timeout 120 -r requirements.txt

# -------------------------------------------------------------------
# 【层 B】安装 as6-tools ECOA 工具链（可编辑模式）
# 与上层分离：as6-tools 变化时只重跑此层，requirements 层不受影响
# 使用 pip 缓存挂载：即使缓存失效也只从本地缓存装包（15-30s），不走网络
# -------------------------------------------------------------------
COPY as6-tools/ /app/as6-tools/
RUN --mount=type=cache,target=/root/.cache/pip \
    for d in \
        ecoa-toolset \
        ecoa-exvt \
        ecoa-csmgvt \
        ecoa-mscigt \
        ecoa-asctg \
        ecoa-ldp; do \
        if [ -d "/app/as6-tools/$d" ]; then \
            pip install --timeout 120 -e "/app/as6-tools/$d"; \
        fi; \
    done

# -------------------------------------------------------------------
# 【最后】COPY 应用代码（频繁变化，放最后保证上层缓存不失效）
# 精确拷贝，不重复拷贝上面已有的 requirements.txt / as6-tools/
# -------------------------------------------------------------------
COPY app/ /app/app/
COPY main.py config.yaml cmake_config.cmake /app/
COPY cmake/ /app/cmake/

# The Ubuntu arm64 log4cplus cmake config computes INTERFACE_INCLUDE_DIRECTORIES
# as "/include" (cmake path-computation bug: 4× PATH-up from the file's location
# lands on "/" and _IMPORT_PREFIX is set to "").  cmake refuses to configure when
# the declared include path does not exist.
#
# Fix: create /include and symlink log4cplus headers there so cmake validation
# passes.  The compiler finds the actual headers via the symlink.
RUN mkdir -p /include && ln -sf /usr/include/log4cplus /include/log4cplus

EXPOSE 5000
CMD ["python", "main.py"]
