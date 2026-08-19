# -*- coding: utf-8 -*-
# Copyright (c) 2023 Dassault Aviation
# SPDX-License-Identifier: MIT

import os
from xml.etree.ElementTree import ElementTree
from ..models.platform import Platform
from ..models.platform_link import PlatformLink, udp_binding, tcp_binding, dds_binding
from ..models.node import Node
from ..models.node_link import Link
from ..models.logical_system import Logical_System
from ..utilities.logs import debug, error, warning
from ..utilities.xml_utils import validate_XML_file

def parse_all_logicalsystem(xsd_directory, filename_list,):
    new_logical_system = None
    for file in filename_list:
        #TODO useless loop because only a unique logical system is supported

        if os.path.exists(file) is False:
            error("File '%s' does not exist" % file)
            return False

        if validate_XML_file(file, xsd_directory + "/Schemas_ecoa/ecoa-logicalsystem-2.0.xsd") == -1:
            return False

        logical_system_name = os.path.basename(file)
        if logical_system_name.endswith(".logical-system.xml"):
            # normal case. filename = #name#.logical-system.xml
            logical_system_name= logical_system_name.replace(".logical-system.xml", "")
        else:
            # support non-compliant filename
            warning("Logical system '%s' isn't a compliant filename, it should be '#name#.logical-system.xml'"%
                    (os.path.basename(file)))
            logical_system_name = logical_system_name.replace(".xml", "")

        new_logical_system = Logical_System(logical_system_name)
        parse_logicalsystem(file, new_logical_system, xsd_directory)

    return new_logical_system # currently only one logical system is supported

def parse_logicalsystem(filename, logical_system, xsd_directory):
    tree = ElementTree()
    tree.parse(filename)

    # Platforms
    for p in tree.iterfind("logicalComputingPlatform"):
        pid = p.get("id")
        ELI_ID = p.get("ELIPlatformId", default="")
        if pid in logical_system.platforms:
            debug("The platform %s has already been declared" % (pid))
            continue

        platform = Platform(pid, ELI_ID)

        for n in p.iterfind("logicalComputingNode"):
            nid = n.get("id")
            node = Node(nid)
            for lp in n.iterfind("logicalProcessors"):
                number = lp.get("number")
                p_type = lp.get("type")
                d = lp.find("stepDuration").get("nanoSeconds")
                node.add_logical_processors(int(number), p_type, int(d))

            number = n.find("moduleSwitchTime").get("microSeconds")
            node.set_module_switch_time(int(number))

            if (nid in platform.nodes) is True:
                warning("The node %s is declared two times in platform %s" % (nid, pid))
            platform.add_logical_node(node)

        for links_set in p.iterfind("logicalComputingNodeLinks"):
            for ln in links_set.iterfind("link"):
                from_p = ln.get("from")
                to_p = ln.get("to")
                throughput_node = ln.find("throughput")
                latency_node = ln.find("latency")
                tv = throughput_node.get("megaBytesPerSecond") if throughput_node != None else "-1"
                lv = latency_node.get("microSeconds") if latency_node != None else "-1"
                link = Link(from_p, to_p, int(tv), int(lv))
                platform.add_node_link(link)
        logical_system.platforms[pid] = platform

        for nid, n in platform.nodes.items():
            debug("Name: %s %d %d %d %d" % \
                  (nid, n.get_id(), n.get_processors_number(),
                   n.get_mean_step_duration(),
                   n.get_module_switch_time()))

        for link in platform.node_links:
            debug("Link %s => %s:%s - %d:%d" % \
                  (link.get_id(), link.get_source_node(), link.get_target_node(),
                   link.get_throughput(), link.get_latency()))

        check_node_links(platform.nodes, platform.node_links)


    for pid, p in logical_system.platforms.items():
        debug("Platform: %s %d %d %d %d" % \
              (pid, p.get_id(), p.get_processors_number(),
               p.get_mean_step_duration(),
               p.get_mean_module_switch_time()))

    # Platform links
    for links_set in tree.iterfind("logicalComputingPlatformLinks"):
        for ln in links_set.iterfind("link"):
            from_p = ln.get("from")
            to_p = ln.get("to")
            id_link = ln.get("id")
            throughput_node = ln.find("throughput")
            latency_node = ln.find("latency")

            tv = throughput_node.get("megaBytesPerSecond") if throughput_node != None else "-1"
            lv = latency_node.get("microSeconds") if latency_node != None else "-1"

            if id_link in logical_system.platform_links:
                warning("platform links id '%s' already exist" %(id_link))
                continue

            link = PlatformLink(id_link, from_p, to_p, int(tv), int(lv))

            transportBinding_node = ln.find("transportBinding")
            if transportBinding_node != None:
                protocol = transportBinding_node.get("protocol")
                param = transportBinding_node.get("parameters")
                # New format: dds="true" attribute overlays DDS middleware on TCP/UDP transport
                use_dds = transportBinding_node.get("dds", "false").lower() == "true"
                dds_domain_id_str = transportBinding_node.get("ddsDomainId", "0")
                link.protocol = protocol
                if protocol == "UDP":
                    ## parse specific transport binding file
                    binding_file = os.path.join(os.path.dirname(filename), param)
                    link.link_binding = parse_udp_binding_file(binding_file, xsd_directory)
                elif protocol == "TCP":
                    binding_file = os.path.join(os.path.dirname(filename), param)
                    link.link_binding = parse_tcp_params_file(binding_file)
                    # Populate ELIPlatformId from the already-parsed logical system platforms.
                    # route_generator.py calls find_mcast_PF_id() to emit *_PF_ID macros,
                    # which must contain the ECOA ELIPlatformId for ELI framing.
                    if link.link_binding is not None:
                        for pf_name in list(link.link_binding.platforms.keys()):
                            pf = logical_system.platforms.get(pf_name)
                            eli_id = str(pf.ELI_platform_ID) if pf and pf.ELI_platform_ID else "0"
                            addr, port, _ = link.link_binding.platforms[pf_name]
                            link.link_binding.platforms[pf_name] = (addr, port, eli_id)
                elif protocol == "DDS":
                    ## DDS binding: domain-scoped multicast; treat as valid, no per-platform IP check
                    binding_file = os.path.join(os.path.dirname(filename), param)
                    link.link_binding = parse_dds_binding_file(binding_file)
                else:
                    warning("platform link '%s' uses an unknown protocol '%s'" % (id_link, protocol))
                # When dds="true" is set, record the DDS overlay on the link object so
                # that distributed_debug.py can activate DDS compilation flags while
                # still using the TCP/UDP binding file for container IP assignment.
                if use_dds and protocol in ("TCP", "UDP"):
                    link.use_dds = True
                    try:
                        link.dds_domain_id = int(dds_domain_id_str)
                    except (ValueError, TypeError):
                        link.dds_domain_id = 0
            else:
                warning("platform link '%s' has no transport binding" % (id_link))

            logical_system.platform_links[id_link]=link

    for link in logical_system.platform_links.values():
        debug("Link %s => %s:%s - %d:%d" % \
              (link.get_id(), link.get_source_platform(), link.get_target_platform(),
               link.get_throughput(), link.get_latency()))

    check_platform_links(logical_system)
    for pf in tree.iterfind("logicalComputingPlatform"):
            pid = pf.get("id")
    return tree

def parse_udp_binding_file(binding_file, xsd_directory):
    if os.path.exists(binding_file) is False:
        error("File '%s' does not exist" % binding_file)
        return udp_binding(binding_file)

    tree = ElementTree()
    tree.parse(binding_file)
    binding = udp_binding(binding_file)

    UDP_BINDING_SPACE = '{http://www.ecoa.technology/udpbinding-2.0}'
    root = tree.getroot()

    # Accept both namespaced and bare element names so the file is readable
    # even when the user has not yet populated platform entries in the model.
    candidates = list(root.iterfind(UDP_BINDING_SPACE + "platform"))
    if not candidates:
        candidates = list(root.iterfind("platform"))

    for pf_node in candidates:
        pf_name = pf_node.get("name")
        pf_id = pf_node.get("platformId")
        mcast_addr = pf_node.get("receivingMulticastAddress")
        port = pf_node.get("receivingPort")
        max_channel = int(pf_node.get("maxChannels", default="15"))
        binding.add_platform(pf_name, mcast_addr, port, pf_id, max_channel)

    if not candidates:
        warning("UDP binding file '%s' has no <platform> entries — "
                "check_platform_links will report missing platforms" % binding_file)

    return binding

def parse_tcp_params_file(binding_file):
    """Parse a tcp-params.xml file and return a tcp_binding object.

    Format (namespace optional):
      <TCPBinding xmlns="http://www.ecoa.technology/tcpbinding">
        <platform name="Platform_A" address="192.168.1.1" port="30000"/>
        <platform name="Platform_B" address="192.168.1.2" port="30001"/>
      </TCPBinding>
    """
    if not os.path.exists(binding_file):
        error("File '%s' does not exist" % binding_file)
        return tcp_binding(binding_file)

    tree = ElementTree()
    tree.parse(binding_file)
    binding = tcp_binding(binding_file)

    TCP_NS = '{http://www.ecoa.technology/tcpbinding}'
    root = tree.getroot()

    # Try namespaced elements first, then fall back to bare tag names
    candidates = list(root.iterfind(TCP_NS + "platform")) or list(root.iterfind("platform"))

    for pf_node in candidates:
        name    = pf_node.get("name")
        address = pf_node.get("address", "")
        port    = pf_node.get("port", "0")
        if name:
            binding.add_platform(name, address, port)

    return binding


def parse_dds_binding_file(binding_file):
    """Parse a dds-binding.xml file.

    DDS uses a shared multicast domain ID — there are no per-platform IP entries.
    Returns a dds_binding sentinel so check_platform_links can skip address validation.
    """
    if not os.path.exists(binding_file):
        error("File '%s' does not exist" % binding_file)
        return dds_binding(binding_file)

    tree = ElementTree()
    tree.parse(binding_file)
    root = tree.getroot()

    domain_id = 0
    # Try namespaced then bare element name
    domain_el = root.find("{http://www.ecoa.technology/ddsbinding}domain")
    if domain_el is None:
        domain_el = root.find("domain")
    if domain_el is not None:
        try:
            domain_id = int(domain_el.get("id", "0"))
        except (ValueError, TypeError):
            pass

    return dds_binding(binding_file, domain_id)


def check_double_platform_links(links):
    lset = []  # List
    for l in links:
        for ll in links:
            if (l != ll) and (ll not in lset):
                if ll.get_source_platform() == l.get_source_platform()\
                        and ll.get_target_platform() == l.get_target_platform():
                    if l not in lset:
                        lset.append(l)
                    lset.append(ll)
                    warning("Link %s is declared double times" % l.__repr__())
    return lset

def check_platform_links(logical_system):
    """Check_platform_links :

        - check if platforms exists
        - check that two links do not link both pairs of platforms
        - check binding :
            * source and target PF must be defined in binding file
            * PF link should be find in binding file
    """
    for l in logical_system.platform_links.values():
        sp = l.get_source_platform()
        if sp not in logical_system.platforms:
            error("Source node %s for link %s does not exist" % \
                  (sp, l.__repr__()))
        tp = l.get_target_platform()
        if tp not in logical_system.platforms:
            error("Target node %s for link %s does not exist" % \
                  (tp, l.__repr__()))
        if l.link_binding is not None:
            # DDS uses a shared domain ID — no per-platform address entries to check
            if not isinstance(l.link_binding, dds_binding):
                if sp not in l.link_binding.platforms:
                    warning("In PF link '%s', source platform '%s' is not defined in binding" %(l.name, sp))
                if tp not in l.link_binding.platforms:
                    warning("In PF link '%s', target platform '%s' is not defined in binding" %(l.name, tp))
        else:
            error("PF link '%s' has no binding" %(l.name))




#TODO: to remove ? :
def check_double_node_links(links):
    lset = []  # List
    for l in links:
        for ll in links:
            if (l != ll) and (ll not in lset):
                if ll.get_source_node() == l.get_source_node() and \
                                ll.get_target_node() == l.get_target_node():
                    if l not in lset:
                        lset.append(l)
                    lset.append(ll)
                    warning("Link %s is declared double times" % l.__repr__())
    return lset

def check_node_links(nodes, node_links):
    """ Check_node_links:

        * check if nodes exist
        * check that two links do not link both pairs of nodes
    """
    for l in node_links:
        sp = l.get_source_node()
        if sp not in nodes:
            error("Source node %s for link %s does not exist" % \
                  (sp, l.__repr__()))
        tp = l.get_target_node()
        if tp not in nodes:
            error("Target node %s for link %s does not exist" % \
                  (tp, l.__repr__()))
    check_double_node_links(node_links)
