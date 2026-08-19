# -*- coding: utf-8 -*-
# Copyright (c) 2023 Dassault Aviation
# SPDX-License-Identifier: MIT

from collections import OrderedDict


class tcp_binding:
    """TCP ELI binding: per-platform (address, port, eli_platform_id).

    Exposes the same interface as udp_binding so that route_generator.py can
    call find_read_mcast_port / find_read_mcast_address / find_mcast_PF_id
    without being protocol-aware.  For TCP:
      read_port / sent_port  →  the per-platform TCP listen port
      read_addr / sent_addr  →  the per-platform TCP IP address
      PF_ID                  →  the ECOA ELIPlatformId (populated from logical-system after parsing)
    """
    def __init__(self, filename):
        self.filename = filename
        # platforms[name] = (address, port, eli_platform_id)
        self.platforms = OrderedDict()

    def add_platform(self, name, address, port, eli_platform_id="0"):
        self.platforms[name] = (address, str(port), str(eli_platform_id))

    # ---- native TCP accessors ------------------------------------------------
    def find_address(self, platform_name):
        if platform_name not in self.platforms:
            return "0.0.0.0"
        return self.platforms[platform_name][0]

    def find_port(self, platform_name):
        if platform_name not in self.platforms:
            return "0"
        return self.platforms[platform_name][1]

    # ---- udp_binding-compatible interface for route_generator.py --------------
    def find_read_mcast_address(self, platform_name):
        """For TCP: returns the platform IP address (maps to 'read_addr' macro)."""
        return self.find_address(platform_name)

    def find_read_mcast_port(self, platform_name):
        """For TCP: returns the platform listen port (maps to 'read_port' macro)."""
        return self.find_port(platform_name)

    def find_mcast_PF_id(self, platform_name):
        """For TCP: returns the ELI platform ID from the logical system."""
        if platform_name not in self.platforms:
            return "0"
        return self.platforms[platform_name][2]

    def find_max_channel(self, platform_name):
        """Not applicable for TCP — return minimum value."""
        return 1


class dds_binding:
    """DDS uses a shared multicast domain ID — no per-platform IP entries.

    Exposes a stub interface matching udp_binding so route_generator.py does
    not crash when DDS links are present.  The actual DDS configuration is
    handled at compile time via -DCYCLONEDDS_DOMAIN_ID and -DCMAKE_USE_DDS_PROTO=ON.
    """
    def __init__(self, filename, domain_id=0):
        self.filename = filename
        self.domain_id = domain_id
        # Intentionally empty: DDS does not route per platform address
        self.platforms = {}

    # ---- udp_binding-compatible interface stubs ------------------------------
    def find_read_mcast_address(self, platform_name):
        return "0.0.0.0"

    def find_read_mcast_port(self, platform_name):
        return "0"

    def find_mcast_PF_id(self, platform_name):
        return "0"

    def find_max_channel(self, platform_name):
        # DDS uses 1 channel per PF link for ELI message defragmentation
        return 1


class udp_binding:
    def __init__(self, filename):
        self.filename = filename
        self.platforms = OrderedDict()
        # self.max_channel = 15 # TODO : 256

    def add_platform(self, name, received_mcast_address, received_mcast_port, platform_id, max_channel):
        self.platforms[name] = (received_mcast_address, received_mcast_port, platform_id, max_channel)


    def find_read_mcast_address(self, platform_name):
        if platform_name not in self.platforms:
            return "0.0.0.0"
        else:
            return self.platforms[platform_name][0]

    def find_read_mcast_port(self, platform_name):
        if platform_name not in self.platforms:
            return "0"
        else:
            return self.platforms[platform_name][1]

    def find_mcast_PF_id(self, platform_name):
        if platform_name not in self.platforms:
            return "0"
        else:
            return self.platforms[platform_name][2]

    def find_max_channel(self, platform_name):
        if platform_name not in self.platforms:
            return 15
        else:
            return self.platforms[platform_name][3]

class PlatformLink:
    """Description of platform link

    Attributes:
        source_platform  (str): source platform name
        target_platform  (str): target platform name
        throughput       (str): mega bytes per second
        latency          (str): micro seconds
        id               (str): link ID
        protocol         (str): protocol of communication of this link
        service_syntax  (dict): Wires and corresponding syntax which are
                                mapped on this link.
                                Dictionary of list :class:`.Wire` mapped on this link
                                retrieved by :class:`.Service_Definition`.
        link_binding (:class:`udp_binding`): UDP binding
    """
    id_counter = 0

    def __init__(self, name, sp, tp, throughput, latency):
        self.name = name
        self.source_platform = sp
        self.target_platform = tp
        self.throughput = throughput
        self.latency = latency
        self.id = PlatformLink.id_counter

        self.protocol = ""
        self.link_binding = None
        self.use_dds = False      # True when dds="true" is set on transportBinding
        self.dds_domain_id = 0   # CycloneDDS domain ID (0-232)

        self.service_syntax = OrderedDict() # service_definition => list of wire

        PlatformLink.id_counter = PlatformLink.id_counter + 1

    def get_id(self):
        return self.id

    def get_name(self):
        return self.__repr__()

    def get_source_platform(self):
        return self.source_platform

    def get_target_platform(self):
        return self.target_platform

    def get_throughput(self):
        return self.throughput

    def get_latency(self):
        return self.latency

    def __repr__(self):
        return self.source_platform + ':' + \
               self.target_platform




    def find_services_wires(self, PF_wire_mapping, components, component_types, service_definitions):
        if self.name not in PF_wire_mapping:
            # no wire map on this PF link
            return

        for wire in PF_wire_mapping[self.name]:
            syntax_service = wire.find_service_syntax(components, component_types, service_definitions)
            if syntax_service not in self.service_syntax:
                self.service_syntax[syntax_service]=[]
            self.service_syntax[syntax_service].append(wire)




    def get_other_platform(self, platform_name):
        """return the other pfatform name or "" if the platform_name is neither source or target
        """

        if self.source_platform == platform_name:
            return self.target_platform
        elif self.target_platform == platform_name:
            return self.source_platform
        else:
            return ""
