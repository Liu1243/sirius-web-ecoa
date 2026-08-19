#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# Copyright (c) 2023 Dassault Aviation
# SPDX-License-Identifier: MIT

import warnings
warnings.filterwarnings(action='ignore', message='Python 3.6 is no longer supported')
from multiprocessing import Process
import os
import argparse
import subprocess


def run_cmd(cmd):
    return subprocess.run(cmd, check=True)


def start_process(dest_dir, container, process):
    try:
        cmd = [
            "docker", "exec", container, "bash", "-lc",
            f"cd {dest_dir} && chmod +x ./{process} && LD_LIBRARY_PATH=. ./{process}"
        ]
        result = run_cmd(cmd)
        print(f"Process '{process}' in '{container}' exit with status {result.returncode}")
    except KeyboardInterrupt:
        print(f"Closing '{process}' in '{container}'")
        run_cmd(["docker", "exec", container, "pkill", "-f", process])


class ECOA_Multi_Node:
    def __init__(self, deps_dir, dest_dir):
        self.app_dir = os.path.dirname(os.path.dirname(os.path.realpath(__file__)))
        self.deps_dir = deps_dir
        self.dest_dir = dest_dir

        self.hosts = [
            "192.168.248.129",
            "192.168.248.130",
            "192.168.248.131",
            "192.168.248.132",
            "192.168.248.133",
        ]
        self.host_to_container = {
            "192.168.248.129": "ecoa-node0",
            "192.168.248.130": "ecoa-node1",
            "192.168.248.131": "ecoa-node2",
            "192.168.248.132": "ecoa-node3",
            "192.168.248.133": "ecoa-node4",
        }

        self.processes = [
            Process(target=start_process, args=(self.dest_dir, self.host_to_container["192.168.248.129"], "platform")),
            Process(target=start_process, args=(self.dest_dir, self.host_to_container["192.168.248.129"], "PD_Writer_PD")),
            Process(target=start_process, args=(self.dest_dir, self.host_to_container["192.168.248.130"], "PD_Reader0_PD")),
            Process(target=start_process, args=(self.dest_dir, self.host_to_container["192.168.248.131"], "PD_Reader1_PD")),
            Process(target=start_process, args=(self.dest_dir, self.host_to_container["192.168.248.132"], "PD_Reader2_PD")),
            Process(target=start_process, args=(self.dest_dir, self.host_to_container["192.168.248.133"], "PD_Reader3_Finisher_PD")),
        ]

    def ensure_container_ready(self, container):
        inspect_cmd = ["docker", "inspect", "-f", "{{.State.Running}}", container]
        proc = subprocess.run(inspect_cmd, check=False, capture_output=True, text=True)
        if proc.returncode != 0 or proc.stdout.strip() != "true":
            raise RuntimeError(f"Container '{container}' is not running")

    def deploy_app(self, host):
        container = self.host_to_container[host]
        print("Deploy ECOA application on '%s' (container: %s)" % (host, container))
        run_cmd(["docker", "exec", container, "mkdir", "-p", self.dest_dir])
        run_cmd(["docker", "cp", f"{self.app_dir}/bin/.", f"{container}:{self.dest_dir}/"])
        lib_path = f"{self.app_dir}/lib/libecoa.so"
        if os.path.exists(lib_path):
            run_cmd(["docker", "cp", lib_path, f"{container}:{self.dest_dir}/"])

    def deploy_deps(self, host):
        container = self.host_to_container[host]
        print("Deploy ECOA dependencies on '%s' (container: %s)" % (host, container))
        libs = [
            "liblog4cplus-2.0.so.3",
            "libzlog.so",
            "libapr-1.so.0",
        ]
        for lib in libs:
            src = f"{self.deps_dir}/lib/{lib}"
            if os.path.exists(src):
                run_cmd(["docker", "cp", src, f"{container}:{self.dest_dir}/"])

    def deploy_platform(self):
        for host in self.hosts:
            container = self.host_to_container[host]
            self.ensure_container_ready(container)
            self.deploy_app(host)
            if self.deps_dir:
                self.deploy_deps(host)

    def run_platform(self):
        for process in self.processes:
            process.start()
        for process in self.processes:
            process.join()

    def run(self):
        self.deploy_platform()
        self.run_platform()


def parse_args():
    cmd_parser = argparse.ArgumentParser(description="Runs ECOA platform on docker multi-nodes.")
    cmd_parser.add_argument(
        '-d', '--deps-dir', action='store', default="",
        help="Deploy ECOA application and dependencies from directory"
    )
    cmd_parser.add_argument(
        '--dest-dir', action='store', default="/opt/ecoa/bin",
        help="Destination directory inside containers"
    )
    return cmd_parser.parse_args()


def main():
    arguments = parse_args()
    try:
        l_multi_node = ECOA_Multi_Node(arguments.deps_dir, arguments.dest_dir)
        l_multi_node.run()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
