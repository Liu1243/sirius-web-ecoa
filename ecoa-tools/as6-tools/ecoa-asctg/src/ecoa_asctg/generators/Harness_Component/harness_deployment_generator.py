# -*- coding: utf-8 -*-
# Copyright (c) 2023 Dassault Aviation
# SPDX-License-Identifier: MIT

import os
from xml.etree import ElementTree
from ecoa.utilities.logs import info, debug

from ecoa.utilities.namespaces import ECOS_DE

from .harness_utils import get_harness_mod_str, write_xml_file

def __remove_components_instance(pd, harness_comp, instance_string, keep):
    l_keep = keep
    for c_item in pd.findall(ECOS_DE+instance_string):
        if c_item.attrib['componentName'] not in harness_comp:
            pd.remove(c_item)
            debug("Remove Trigger '%s'" % c_item.attrib["componentName"])
        else:
            debug("Keep Trigger '%s'" % c_item.attrib["componentName"])
            l_keep = True
    return l_keep

def __remove_components_replaced(deployment_root, harness_comp):
    for c_pd in deployment_root.findall(ECOS_DE+"protectionDomain"):
        debug("Protection domain '%s'" % c_pd.attrib['name'])

        # Remove Triggers
        l_keep = __remove_components_instance(c_pd, harness_comp, "deployedTriggerInstance", False)

        # Remove Modules
        l_keep = __remove_components_instance(c_pd, harness_comp, "deployedModuleInstance", l_keep)

        if not l_keep:
            debug("Remove Protection domain '%s'" % c_pd.attrib['name'])
            deployment_root.remove(c_pd)

def __update_wire_mappings(deployment_root, harness_comp_name, harness_comps, new_wires):
    """Update wireMapping elements to match the new wires in the harness composite.

    Original wireMappings reference components that have been replaced by HARNESS.
    This function removes old wireMappings and creates new ones for the harness
    wires that cross platform boundaries.

    The platform link (mappedOnLinkId) for each new wire is inherited from the
    original wireMapping that shared the same non-HARNESS endpoint.
    """
    # Build lookup from original wireMapping: endpoint_str -> linkId
    # Using separate lookups for source and target endpoints
    source_to_link = {}  # "comp/service" -> linkId
    target_to_link = {}  # "comp/service" -> linkId

    for wm in deployment_root.findall(ECOS_DE + "wireMapping"):
        source = wm.attrib.get("source", "")
        target = wm.attrib.get("target", "")
        link_id = wm.attrib.get("mappedOnLinkId", "")
        if source:
            source_to_link[source] = link_id
        if target:
            target_to_link[target] = link_id

    # Remove all existing wireMapping elements
    for wm in deployment_root.findall(ECOS_DE + "wireMapping"):
        deployment_root.remove(wm)

    # Add new wireMappings for new_wires that cross platforms
    for w in new_wires:
        link_id = None

        if w.source_component == harness_comp_name:
            # Wire: HARNESS -> surviving_component
            # Find original wireMapping by the surviving component's target endpoint
            target_endpoint = w.target_component + "/" + w.target_service
            if target_endpoint in target_to_link:
                link_id = target_to_link[target_endpoint]
        elif w.target_component == harness_comp_name:
            # Wire: surviving_component -> HARNESS
            # Find original wireMapping by the surviving component's source endpoint
            source_endpoint = w.source_component + "/" + w.source_service
            if source_endpoint in source_to_link:
                link_id = source_to_link[source_endpoint]

        if link_id:
            debug("Add wireMapping for new wire '%s/%s -> %s/%s' on link '%s'" % (
                w.source_component, w.source_service,
                w.target_component, w.target_service, link_id))
            ElementTree.SubElement(
                deployment_root,
                ECOS_DE + "wireMapping",
                attrib={
                    "mappedOnLinkId": link_id,
                    "source": w.source_component + "/" + w.source_service,
                    "target": w.target_component + "/" + w.target_service
                }
            )

def update_deployment_file(global_config, harness_comp_name, harness_comp , deployment_filename,
                             computing_PF_name, computing_node_name, new_wires=None):

    if new_wires is None:
        new_wires = []

    deployment_xml_tree = ElementTree.parse(deployment_filename)
    ElementTree.register_namespace("", ECOS_DE[1:-1])
    deployment_root = deployment_xml_tree.getroot()

    # remove components replaced by HARNESS
    __remove_components_replaced(deployment_root, harness_comp)

    # new pd:
    if deployment_root.find(ECOS_DE+"protectionDomain[@name='%s_PD']" % harness_comp_name) is None:
        new_pd_node = ElementTree.Element(ECOS_DE+"protectionDomain", attrib={"name": "%s_PD" % harness_comp_name})
        deployment_root.insert(0, new_pd_node)
        ElementTree.SubElement(new_pd_node, ECOS_DE+"executeOn", attrib={"computingNode": computing_node_name,
                                                                         "computingPlatform": computing_PF_name})

        # module
        ElementTree.SubElement(new_pd_node,
                               ECOS_DE+"deployedModuleInstance",
                               attrib={"componentName": harness_comp_name,
                                       "moduleInstanceName": get_harness_mod_str() + "_HARNESS_mod_inst",
                                       "modulePriority": "30"})
        # trigger
        ElementTree.SubElement(new_pd_node,
                               ECOS_DE+"deployedTriggerInstance",
                               attrib={"componentName": harness_comp_name,
                                       "triggerInstanceName": get_harness_mod_str() + "_HARNESS_trigger",
                                       "triggerPriority": "30"})

    # update wireMappings to match the harness composite wires
    __update_wire_mappings(deployment_root, harness_comp_name, harness_comp, new_wires)

    write_xml_file(global_config, global_config.m_harness_deployment_filename, deployment_root)
    info("Deployment File '%s' has been updated" % global_config.m_harness_deployment_filename)
