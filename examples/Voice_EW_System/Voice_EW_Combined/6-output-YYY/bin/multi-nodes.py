#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# Copyright (c) 2023 Dassault Aviation
# SPDX-License-Identifier: MIT

import warnings
warnings.filterwarnings(action='ignore',message='Python 3.6 is no longer supported')
from multiprocessing import Process
import os, glob
from paramiko import SSHClient
import argparse
import subprocess

def start_process(dest_dir, user, host, process):
    try:
        client = SSHClient()
        client.load_system_host_keys()
        client.connect(host, username=user)
        stdin, stdout, stderr = client.exec_command(f'cd {dest_dir} && LD_LIBRARY_PATH=. ./{process}')
        print(f"Process '{process}' running")
        print(f"Process '{process}' exit with status", stdout.channel.recv_exit_status())
    except KeyboardInterrupt:
        print(f"Closing '{process}'")
        client.exec_command(f'killall {process}')
        client.close()


class ECOA_Multi_Node:
    def __init__(self, deps_dir):
        self.app_dir = os.path.dirname(os.path.dirname(os.path.realpath(__file__)))
        self.deps_dir = deps_dir
        self.user = os.getlogin()
        self.dest_dir = "/tmp/bin"
        self.mkdir_cmd = "ssh {1}@{2} mkdir -p {0}"
        self.cmd = "scp -qpr {3} {1}@{2}:{0}"
        # Create hosts
        self.hosts = ["127.0.0.1"]
        # Create platform processes
        self.processes = [Process(target=start_process, args=(self.dest_dir, self.user, "127.0.0.1", "platform")),
                          Process(target=start_process, args=(self.dest_dir, self.user, "127.0.0.1", "PD_YYY_PD")),
                          Process(target=start_process, args=(self.dest_dir, self.user, "127.0.0.1", "PD_VoiceMgmt_PD")),
                          Process(target=start_process, args=(self.dest_dir, self.user, "127.0.0.1", "PD_LLMModel_PD")),
                          Process(target=start_process, args=(self.dest_dir, self.user, "127.0.0.1", "PD_XXX_PD"))]

    def deploy_app(self, host):
        print("Deploy ECOA application on '%s'" % host)
        l_args = ' '.join(glob.glob("%s/bin/*" % self.app_dir))
        l_args += " %s/lib/libecoa.so" % self.app_dir
        l_cmd = self.cmd.format(self.dest_dir, self.user, host, l_args)
        subprocess.run(l_cmd.split(" "))

    def deploy_deps(self, host):
        print("Deploy ECOA dependencies on '%s'" % host)
        l_args  = "%s/lib/liblog4cplus-2.0.so.3 " % self.deps_dir
        l_args += "%s/lib/libzlog.so " % self.deps_dir
        l_args += "%s/lib/libapr-1.so.0" % self.deps_dir
        l_cmd = self.cmd.format(self.dest_dir, self.user, host, l_args)
        subprocess.run(l_cmd.split(" "))

    def deploy_platform(self):
        for host in self.hosts:
            if self.deps_dir:
                l_cmd = self.mkdir_cmd.format(self.dest_dir, self.user, host)
                subprocess.run(l_cmd.split(" "))
                self.deploy_app(host)
                self.deploy_deps(host)

    def run_platform(self):
        # Start all platform processes
        for process in self.processes:
            process.start()
        for process in self.processes:
            process.join()


    def run(self):
        # Deploy ECOA application and dependencies
        self.deploy_platform()
        # Run ECOA application
        self.run_platform()


def parse_args():
    # Parse multi-nodes arguments

    cmd_parser = argparse.ArgumentParser(description="Runs ECOA platform on multiple nodes.")
    cmd_parser.add_argument('-d', '--deps-dir', action='store', default="",
                            help="Deploy ECOA application and dependencies from directory")
    # return command parser
    return cmd_parser.parse_args()


def main():
    # Parse command line
    arguments = parse_args()
    # Launch multi nodes application
    try:
        l_multi_node = ECOA_Multi_Node(arguments.deps_dir)
        l_multi_node.run()
    except KeyboardInterrupt:
        pass

if __name__ == "__main__":
    main()

