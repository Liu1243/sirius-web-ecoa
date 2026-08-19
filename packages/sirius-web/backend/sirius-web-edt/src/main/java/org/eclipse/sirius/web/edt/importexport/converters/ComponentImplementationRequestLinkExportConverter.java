/*******************************************************************************
 * Copyright (c) 2024 Dassault Aviation.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Dassault Aviation - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.web.edt.importexport.converters;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import edtimplementation.ModuleInstance;
import edtimplementation.ReferenceOfLinkedComponentDefinition;
import edtimplementation.RequestClientInstance;
import edtimplementation.RequestLink;
import edtimplementation.RequestLinkActivatableFifo;
import edtimplementation.RequestLinkActivatingActivatableFifo;
import edtimplementation.RequestLinkActivatingToReferenceOperation;
import edtimplementation.RequestReferenceInstance;
import edtimplementation.RequestServerInstance;
import edtimplementation.RequestServiceInstance;
import edtimplementation.ServiceOfLinkedComponentDefinition;
import edtimplementation.TriggerInstance;
import edtimplementation.DynamicTriggerInstance;
import technology.ecoa.implementation._2.ClientsType;
import technology.ecoa.implementation._2.OpRefActivatable;
import technology.ecoa.implementation._2.OpRefActivatableFifo;
import technology.ecoa.implementation._2.ServerType;
import technology.ecoa.implementation._2.impFactory;

/**
 * Converts EDT RequestLink objects to ECOA RequestLink XML elements.
 * Based on the original ComponentImplementationRequestLinkExportConverter from edt-tmp.
 */
public class ComponentImplementationRequestLinkExportConverter {

    private static final impFactory IMPFACTORY = impFactory.eINSTANCE;

    private ComponentImplementationRequestLinkExportConverter() {
        // Utility class
    }

    /**
     * Convert EDT RequestLinks to ECOA RequestLinks, merging multiple links that share the same server.
     */
    public static ArrayList<technology.ecoa.implementation._2.RequestLink> recreateECOARequestLinks(
            edtimplementation.ComponentImplementation compImpl, ArrayList<RequestLink> edtRequestLinks) {
        ArrayList<technology.ecoa.implementation._2.RequestLink> ecoaRequestLinks = new ArrayList<>();
        ConcurrentHashMap<OpServer, ArrayList<OpClient>> requestLinkAssociation = new ConcurrentHashMap<>();

        for (RequestLink edtOperationLink : edtRequestLinks) {
            convertEDTRequestLinksToHashMap(requestLinkAssociation, edtOperationLink);
        }

        for (Entry<OpServer, ArrayList<OpClient>> entry : requestLinkAssociation.entrySet()) {
            OpServer server = entry.getKey();
            ArrayList<OpClient> clients = entry.getValue();

            technology.ecoa.implementation._2.RequestLink ecoaRequestLink = IMPFACTORY.createRequestLink();
            ServerType serverType = IMPFACTORY.createServerType();

            if (server instanceof OpRef op) {
                technology.ecoa.implementation._2.OpRef opRef = IMPFACTORY.createOpRef();
                if (op.id != null) {
                    ecoaRequestLink.setId(op.id);
                }
                opRef.setInstanceName(findInstanceName(compImpl, op.server));
                opRef.setOperationName(op.server.getName());
                serverType.setReference(opRef);
            } else if (server instanceof OpActivableFifo op) {
                OpRefActivatableFifo opRefActivatableFifo = IMPFACTORY.createOpRefActivatableFifo();
                if (op.id != null) {
                    ecoaRequestLink.setId(op.id);
                }
                if (op.activating != null) {
                    opRefActivatableFifo.setActivating(op.activating);
                }
                if (op.fifo != null) {
                    opRefActivatableFifo.setFifoSize(op.fifo);
                }
                opRefActivatableFifo.setInstanceName(findInstanceName(compImpl, op.server));
                opRefActivatableFifo.setOperationName(op.server.getName());
                serverType.setModuleInstance(opRefActivatableFifo);
            }

            ecoaRequestLink.setServer(serverType);
            ClientsType clientsType = IMPFACTORY.createClientsType();

            for (OpClient client : clients) {
                if (client instanceof OpActivating op) {
                    OpRefActivatable opRefActivatable = IMPFACTORY.createOpRefActivatable();
                    if (op.activating != null) {
                        opRefActivatable.setActivating(op.activating);
                    }
                    opRefActivatable.setInstanceName(findInstanceName(compImpl, op.client));
                    opRefActivatable.setOperationName(op.client.getName());
                    clientsType.getModuleInstance().add(opRefActivatable);
                } else if (client instanceof OpServ op) {
                    technology.ecoa.implementation._2.OpRef opRef = IMPFACTORY.createOpRef();
                    opRef.setInstanceName(findInstanceName(compImpl, op.client));
                    opRef.setOperationName(op.client.getName());
                    clientsType.getService().add(opRef);
                }
            }

            ecoaRequestLink.setClients(clientsType);
            ecoaRequestLinks.add(ecoaRequestLink);
        }

        return ecoaRequestLinks;
    }

    private static final java.util.Map<edtimplementation.OperationInstance, edtimplementation.OperationInstance> fallbackAssignments = new java.util.WeakHashMap<>();

    private static String findInstanceName(edtimplementation.ComponentImplementation compImpl, edtimplementation.OperationInstance op) {
        if (op.eContainer() instanceof edtimplementation.Instance inst) return inst.getName();
        if (op.eContainer() instanceof ServiceOfLinkedComponentDefinition serv) return serv.getName();
        if (op.eContainer() instanceof ReferenceOfLinkedComponentDefinition ref) return ref.getName();
        if (compImpl != null) {
            org.eclipse.emf.common.util.URI targetURI = org.eclipse.emf.ecore.util.EcoreUtil.getURI(op);
            for (edtimplementation.Instance inst : compImpl.getInstances()) {
                if (inst instanceof ModuleInstance mod) {
                    for (edtimplementation.OperationInstance modOp : mod.getOperations()) {
                        if (modOp == op || (targetURI != null && targetURI.equals(org.eclipse.emf.ecore.util.EcoreUtil.getURI(modOp)))) return mod.getName();
                    }
                }
                if (inst instanceof TriggerInstance trig) {
                    edtimplementation.OperationInstance trigOp = trig.getOperations();
                    if (trigOp != null && (trigOp == op || (targetURI != null && targetURI.equals(org.eclipse.emf.ecore.util.EcoreUtil.getURI(trigOp))))) return trig.getName();
                }
                if (inst instanceof DynamicTriggerInstance dyn) {
                    for (edtimplementation.OperationInstance dynOp : dyn.getOperations()) {
                        if (dynOp == op || (targetURI != null && targetURI.equals(org.eclipse.emf.ecore.util.EcoreUtil.getURI(dynOp)))) return dyn.getName();
                    }
                }
            }
            for (ServiceOfLinkedComponentDefinition serv : compImpl.getComponentDefinitionServices()) {
                for (edtimplementation.OperationInstance servOp : serv.getOperations()) {
                    if (servOp == op || (targetURI != null && targetURI.equals(org.eclipse.emf.ecore.util.EcoreUtil.getURI(servOp)))) return serv.getName();
                }
            }
            for (ReferenceOfLinkedComponentDefinition ref : compImpl.getComponentDefinitionReferences()) {
                for (edtimplementation.OperationInstance refOp : ref.getOperations()) {
                    if (refOp == op || (targetURI != null && targetURI.equals(org.eclipse.emf.ecore.util.EcoreUtil.getURI(refOp)))) return ref.getName();
                }
            }
            // FALLBACK: Semantic Matching for objects detached by EMF ModuleType updates
            for (edtimplementation.Instance inst : compImpl.getInstances()) {
                if (inst instanceof ModuleInstance mod) {
                    for (edtimplementation.OperationInstance modOp : mod.getOperations()) {
                        if (modOp.getClass().equals(op.getClass()) && Objects.equals(modOp.getName(), op.getName())) {
                            synchronized (fallbackAssignments) {
                                edtimplementation.OperationInstance assignedOp = fallbackAssignments.get(modOp);
                                if (assignedOp == null || assignedOp == op) {
                                    fallbackAssignments.put(modOp, op);
                                    return mod.getName();
                                }
                            }
                        }
                    }
                } else if (inst instanceof TriggerInstance trig) {
                    edtimplementation.OperationInstance trigOp = trig.getOperations();
                    if (trigOp != null && trigOp.getClass().equals(op.getClass()) && Objects.equals(trigOp.getName(), op.getName())) {
                        synchronized (fallbackAssignments) {
                            edtimplementation.OperationInstance assignedOp = fallbackAssignments.get(trigOp);
                            if (assignedOp == null || assignedOp == op) {
                                fallbackAssignments.put(trigOp, op);
                                return trig.getName();
                            }
                        }
                    }
                } else if (inst instanceof DynamicTriggerInstance dyn) {
                    for (edtimplementation.OperationInstance dynOp : dyn.getOperations()) {
                        if (dynOp.getClass().equals(op.getClass()) && Objects.equals(dynOp.getName(), op.getName())) {
                            synchronized (fallbackAssignments) {
                                edtimplementation.OperationInstance assignedOp = fallbackAssignments.get(dynOp);
                                if (assignedOp == null || assignedOp == op) {
                                    fallbackAssignments.put(dynOp, op);
                                    return dyn.getName();
                                }
                            }
                        }
                    }
                }
            }
            for (ServiceOfLinkedComponentDefinition serv : compImpl.getComponentDefinitionServices()) {
                for (edtimplementation.OperationInstance servOp : serv.getOperations()) {
                    if (servOp.getClass().equals(op.getClass()) && Objects.equals(servOp.getName(), op.getName())) {
                        synchronized (fallbackAssignments) {
                            edtimplementation.OperationInstance assignedOp = fallbackAssignments.get(servOp);
                            if (assignedOp == null || assignedOp == op) {
                                fallbackAssignments.put(servOp, op);
                                return serv.getName();
                            }
                        }
                    }
                }
            }
            for (ReferenceOfLinkedComponentDefinition ref : compImpl.getComponentDefinitionReferences()) {
                for (edtimplementation.OperationInstance refOp : ref.getOperations()) {
                    if (refOp.getClass().equals(op.getClass()) && Objects.equals(refOp.getName(), op.getName())) {
                        synchronized (fallbackAssignments) {
                            edtimplementation.OperationInstance assignedOp = fallbackAssignments.get(refOp);
                            if (assignedOp == null || assignedOp == op) {
                                fallbackAssignments.put(refOp, op);
                                return ref.getName();
                            }
                        }
                    }
                }
            }
        }
        System.err.println("DEBUG FIND_INSTANCE_NAME FAILED (RequestLink): op=" + op + ", class=" + op.getClass().getName() + ", targetURI=" + org.eclipse.emf.ecore.util.EcoreUtil.getURI(op) + ", compImpl=" + (compImpl != null ? compImpl.getName() : "null"));
        return "";
    }

    private static void convertEDTRequestLinksToHashMap(
            ConcurrentHashMap<OpServer, ArrayList<OpClient>> requestLinkAssociation,
            RequestLink edtOperationLink) {

        Integer id = edtOperationLink.isSetId() ? edtOperationLink.getId() : null;

        if (edtOperationLink instanceof RequestLinkActivatingToReferenceOperation requestLink
                && requestLink.getClient() != null && requestLink.getServer() != null) {
            RequestReferenceInstance server = requestLink.getServer();
            OpRef opRef = new OpRef(id, server);
            RequestClientInstance client = requestLink.getClient();
            Boolean activating = requestLink.isSetClientActivating() ? requestLink.isClientActivating() : null;
            OpActivating opActivating = new OpActivating(activating, client);
            requestLinkAssociation.putIfAbsent(opRef, new ArrayList<>());
            requestLinkAssociation.get(opRef).add(opActivating);

        } else if (edtOperationLink instanceof RequestLinkActivatableFifo requestLink
                && requestLink.getClient() != null && requestLink.getServer() != null) {
            RequestServerInstance server = requestLink.getServer();
            BigInteger serverFifoSize = requestLink.isSetServerFifoSize() ? requestLink.getServerFifoSize() : null;
            Boolean activating = requestLink.isSetServerActivating() ? requestLink.isServerActivating() : null;
            OpActivableFifo opActivableFifo = new OpActivableFifo(id, serverFifoSize, activating, server);
            RequestServiceInstance client = requestLink.getClient();
            OpServ opServ = new OpServ(client);
            requestLinkAssociation.putIfAbsent(opActivableFifo, new ArrayList<>());
            requestLinkAssociation.get(opActivableFifo).add(opServ);

        } else if (edtOperationLink instanceof RequestLinkActivatingActivatableFifo requestLink
                && requestLink.getClient() != null && requestLink.getServer() != null) {
            RequestServerInstance server = requestLink.getServer();
            BigInteger serverFifoSize = requestLink.isSetServerFifoSize() ? requestLink.getServerFifoSize() : null;
            Boolean serverActivating = requestLink.isSetServerActivating() ? requestLink.isServerActivating() : null;
            OpActivableFifo opActivableFifo = new OpActivableFifo(id, serverFifoSize, serverActivating, server);
            RequestClientInstance client = requestLink.getClient();
            Boolean clientActivating = requestLink.isSetClientActivating() ? requestLink.isClientActivating() : null;
            OpActivating opActivating = new OpActivating(clientActivating, client);
            requestLinkAssociation.putIfAbsent(opActivableFifo, new ArrayList<>());
            requestLinkAssociation.get(opActivableFifo).add(opActivating);
        }
    }

    // --- Inner helper classes ---

    interface OpClient {}

    static class OpActivating implements OpClient {
        protected Boolean activating;
        protected RequestClientInstance client;

        public OpActivating(Boolean activating, RequestClientInstance client) {
            this.activating = activating;
            this.client = client;
        }

        @Override
        public int hashCode() { return Objects.hash(activating, client); }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            OpActivating other = (OpActivating) obj;
            return Objects.equals(activating, other.activating) && Objects.equals(client, other.client);
        }
    }

    static class OpServ implements OpClient {
        protected RequestServiceInstance client;

        public OpServ(RequestServiceInstance client) { this.client = client; }

        @Override
        public int hashCode() { return Objects.hash(client); }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            return Objects.equals(client, ((OpServ) obj).client);
        }
    }

    interface OpServer {}

    static class OpActivableFifo implements OpServer {
        protected Integer id;
        protected RequestServerInstance server;
        protected Boolean activating;
        protected BigInteger fifo;

        public OpActivableFifo(Integer id, BigInteger fifo, Boolean activating, RequestServerInstance server) {
            this.activating = activating;
            this.fifo = fifo;
            this.id = id;
            this.server = server;
        }

        @Override
        public int hashCode() { return Objects.hash(activating, fifo, id, server); }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            OpActivableFifo other = (OpActivableFifo) obj;
            return Objects.equals(activating, other.activating) && Objects.equals(fifo, other.fifo)
                    && Objects.equals(id, other.id) && Objects.equals(server, other.server);
        }
    }

    static class OpRef implements OpServer {
        protected Integer id;
        protected RequestReferenceInstance server;

        public OpRef(Integer id, RequestReferenceInstance server) {
            this.id = id;
            this.server = server;
        }

        @Override
        public int hashCode() { return Objects.hash(id, server); }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            OpRef other = (OpRef) obj;
            return Objects.equals(id, other.id) && Objects.equals(server, other.server);
        }
    }
}
