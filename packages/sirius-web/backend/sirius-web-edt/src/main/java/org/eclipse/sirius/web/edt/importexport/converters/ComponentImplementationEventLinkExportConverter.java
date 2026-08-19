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
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import edtimplementation.DynamicTriggerEventReceiverInstance;
import edtimplementation.DynamicTriggerEventSenderInstance;
import edtimplementation.DynamicTriggerInstance;
import edtimplementation.EventDefinitionInstance;
import edtimplementation.EventLink;
import edtimplementation.EventLinkActivatableFifo;
import edtimplementation.EventLinkActivatableFifoFromTrigger;
import edtimplementation.EventLinkActivatingFifo;
import edtimplementation.EventLinkActivatingFifoFromTrigger;
import edtimplementation.EventLinkSender;
import edtimplementation.EventLinkToDefinitionOperation;
import edtimplementation.EventLinkToDefinitionOperationFromTrigger;
import edtimplementation.EventReceiverInstance;
import edtimplementation.EventSenderInstance;
import edtimplementation.ExternalSenderOperation;
import edtimplementation.ModuleInstance;
import edtimplementation.ReferenceOfLinkedComponentDefinition;
import edtimplementation.ServiceOfLinkedComponentDefinition;
import edtimplementation.TriggerInstance;
import edtimplementation.TriggerSender;
import technology.ecoa.implementation._2.OpRef;
import technology.ecoa.implementation._2.OpRefActivatableFifo;
import technology.ecoa.implementation._2.OpRefActivatingFifo;
import technology.ecoa.implementation._2.OpRefExternal;
import technology.ecoa.implementation._2.OpRefTrigger;
import technology.ecoa.implementation._2.ReceiversType;
import technology.ecoa.implementation._2.SendersType;
import technology.ecoa.implementation._2.impFactory;

/**
 * Converts EDT EventLink objects to ECOA EventLink XML elements.
 * Multiple EDT EventLink objects with the same receiver may be merged into one ECOA EventLink.
 * Based on the original ComponentImplementationEventLinkExportConverter and
 * ComponentImplementationEventLinkExportConverterHelper from edt-tmp.
 */
public class ComponentImplementationEventLinkExportConverter {

    private static final impFactory IMPFACTORY = impFactory.eINSTANCE;

    private ComponentImplementationEventLinkExportConverter() {
        // Utility class
    }

    /**
     * Convert EDT EventLinks to ECOA EventLinks, merging multiple links that share the same receiver.
     */
    public static ArrayList<technology.ecoa.implementation._2.EventLink> recreateEventLinks(
            edtimplementation.ComponentImplementation compImpl, ArrayList<EventLink> edtEventLinks) {
        ArrayList<technology.ecoa.implementation._2.EventLink> ecoaEventLinks = new ArrayList<>();
        ConcurrentHashMap<OpReceiver, ArrayList<OpSender>> eventLinkAssociationFromReceiver = new ConcurrentHashMap<>();
        ConcurrentHashMap<OpSender, ArrayList<OpReceiver>> eventLinkAssociationFromSender = new ConcurrentHashMap<>();

        for (EventLink edtOperationLink : edtEventLinks) {
            convertEventLinksToHashMap(eventLinkAssociationFromSender, eventLinkAssociationFromReceiver, edtOperationLink);
        }

        ArrayList<OpReceiver> toJump = new ArrayList<>();
        ArrayList<OpReceiver> nonUniqueReceivers = new ArrayList<>();

        for (Entry<OpReceiver, ArrayList<OpSender>> entry : eventLinkAssociationFromReceiver.entrySet()) {
            if (!toJump.contains(entry.getKey())) {
                recreateEventLinkForUniqueReceiver(compImpl, ecoaEventLinks, eventLinkAssociationFromReceiver,
                        eventLinkAssociationFromSender, nonUniqueReceivers, entry, toJump);
            }
        }

        Iterator<OpReceiver> iterator = nonUniqueReceivers.iterator();
        while (iterator.hasNext()) {
            OpReceiver opReceiver = iterator.next();
            if (!toJump.contains(opReceiver)) {
                technology.ecoa.implementation._2.EventLink ecoaEventLink = recreateEventLinkForNonUniqueReceiver(
                        compImpl, eventLinkAssociationFromReceiver, eventLinkAssociationFromSender, toJump, opReceiver);
                if (ecoaEventLink != null) {
                    ecoaEventLinks.add(ecoaEventLink);
                }
            }
        }

        return ecoaEventLinks;
    }

    private static void recreateEventLinkForUniqueReceiver(
            edtimplementation.ComponentImplementation compImpl,
            ArrayList<technology.ecoa.implementation._2.EventLink> ecoaEventLinks,
            ConcurrentHashMap<OpReceiver, ArrayList<OpSender>> eventLinkAssociationFromReceiver,
            ConcurrentHashMap<OpSender, ArrayList<OpReceiver>> eventLinkAssociationFromSender,
            ArrayList<OpReceiver> nonUniqueReceivers,
            Entry<OpReceiver, ArrayList<OpSender>> entry,
            ArrayList<OpReceiver> toJump) {
        OpReceiver receiver = entry.getKey();
        ArrayList<OpSender> senders = entry.getValue();
        if (receiver instanceof OpActivatableFifo) {
            ArrayList<OpReceiver> trueReceivers = new ArrayList<>();
            trueReceivers.add(receiver);
            for (OpSender sender : senders) {
                ArrayList<OpReceiver> receiversOfSender = eventLinkAssociationFromSender.get(sender);
                for (OpReceiver receiverOfSender : receiversOfSender) {
                    if (!Objects.equals(receiver, receiverOfSender) && !toJump.contains(receiverOfSender)
                            && eventLinkAssociationFromReceiver.get(receiverOfSender) != null
                            && Objects.equals(eventLinkAssociationFromReceiver.get(receiverOfSender), senders)
                            && Objects.equals(receiver.getId(), receiverOfSender.getId())) {
                        trueReceivers.add(receiverOfSender);
                        if (Objects.equals(eventLinkAssociationFromReceiver.get(receiverOfSender), senders)) {
                            eventLinkAssociationFromReceiver.remove(receiverOfSender);
                            nonUniqueReceivers.remove(receiverOfSender);
                            toJump.add(receiverOfSender);
                        } else {
                            eventLinkAssociationFromReceiver.get(receiverOfSender).removeAll(senders);
                        }
                    }
                }
            }
            if (!senders.isEmpty() && !trueReceivers.isEmpty()) {
                ecoaEventLinks.add(recreateECOAEventLink(compImpl, receiver, senders, trueReceivers));
            }
            eventLinkAssociationFromReceiver.remove(receiver);
        } else {
            nonUniqueReceivers.add(receiver);
        }
    }

    private static technology.ecoa.implementation._2.EventLink recreateEventLinkForNonUniqueReceiver(
            edtimplementation.ComponentImplementation compImpl,
            ConcurrentHashMap<OpReceiver, ArrayList<OpSender>> eventLinkAssociationFromReceiver,
            ConcurrentHashMap<OpSender, ArrayList<OpReceiver>> eventLinkAssociationFromSender,
            ArrayList<OpReceiver> toJump, OpReceiver opReceiver) {
        ArrayList<OpSender> senders = eventLinkAssociationFromReceiver.get(opReceiver);
        ArrayList<OpReceiver> trueReceivers = new ArrayList<>();
        trueReceivers.add(opReceiver);
        for (OpSender sender : senders) {
            ArrayList<OpReceiver> receiversOfSender = eventLinkAssociationFromSender.get(sender);
            for (OpReceiver receiverOfSender : receiversOfSender) {
                if (!Objects.equals(opReceiver, receiverOfSender) && !toJump.contains(receiverOfSender)
                        && eventLinkAssociationFromReceiver.get(receiverOfSender) != null
                        && Objects.equals(eventLinkAssociationFromReceiver.get(receiverOfSender), senders)
                        && Objects.equals(opReceiver.getId(), receiverOfSender.getId())) {
                    trueReceivers.add(receiverOfSender);
                    if (Objects.equals(eventLinkAssociationFromReceiver.get(receiverOfSender), senders)) {
                        eventLinkAssociationFromReceiver.remove(receiverOfSender);
                        toJump.add(receiverOfSender);
                    } else {
                        eventLinkAssociationFromReceiver.get(receiverOfSender).removeAll(senders);
                    }
                }
            }
        }
        eventLinkAssociationFromReceiver.remove(opReceiver);
        if (senders.isEmpty() || trueReceivers.isEmpty()) {
            return null;
        }
        return recreateECOAEventLink(compImpl, opReceiver, senders, trueReceivers);
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
                if (inst instanceof edtimplementation.TriggerInstance trig) {
                    edtimplementation.OperationInstance trigOp = trig.getOperations();
                    if (trigOp != null && (trigOp == op || (targetURI != null && targetURI.equals(org.eclipse.emf.ecore.util.EcoreUtil.getURI(trigOp))))) return trig.getName();
                }
                if (inst instanceof edtimplementation.DynamicTriggerInstance dyn) {
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
        System.err.println("DEBUG FIND_INSTANCE_NAME FAILED (EventLink): op=" + op + ", class=" + op.getClass().getName() + ", targetURI=" + org.eclipse.emf.ecore.util.EcoreUtil.getURI(op) + ", compImpl=" + (compImpl != null ? compImpl.getName() : "null"));
        return "";
    }

    private static technology.ecoa.implementation._2.EventLink recreateECOAEventLink(
            edtimplementation.ComponentImplementation compImpl,
            OpReceiver receiver, ArrayList<OpSender> senders, ArrayList<OpReceiver> trueReceivers) {
        technology.ecoa.implementation._2.EventLink ecoaEventLink = IMPFACTORY.createEventLink();
        SendersType sendersType = IMPFACTORY.createSendersType();
        ReceiversType receiversType = IMPFACTORY.createReceiversType();
        if (receiver.getId() != null) {
            ecoaEventLink.setId(receiver.getId());
        }

        for (OpReceiver opReceiver : trueReceivers) {
            if (opReceiver instanceof OpActivatableFifo op) {
                OpRefActivatableFifo opRefActivatableFifo = IMPFACTORY.createOpRefActivatableFifo();
                if (op.activating != null) opRefActivatableFifo.setActivating(op.activating);
                if (op.fifo != null) opRefActivatableFifo.setFifoSize(op.fifo);
                opRefActivatableFifo.setInstanceName(findInstanceName(compImpl, op.receiver));
                opRefActivatableFifo.setOperationName(op.receiver.getName());
                receiversType.getModuleInstance().add(opRefActivatableFifo);
            } else if (opReceiver instanceof OpDef op) {
                OpRef opRef = IMPFACTORY.createOpRef();
                opRef.setOperationName(op.receiver.getName());
                opRef.setInstanceName(findInstanceName(compImpl, op.receiver));
                if (compImpl != null) {
                    boolean isService = compImpl.getComponentDefinitionServices().stream().anyMatch(s -> s.getOperations().contains(op.receiver));
                    if (isService || op.receiver.eContainer() instanceof ServiceOfLinkedComponentDefinition) {
                        receiversType.getService().add(opRef);
                    } else {
                        receiversType.getReference().add(opRef);
                    }
                }
            } else if (opReceiver instanceof OpDynamicReceiver op) {
                OpRefActivatingFifo opRefActivatingFifo = IMPFACTORY.createOpRefActivatingFifo();
                if (op.fifo != null) opRefActivatingFifo.setFifoSize(op.fifo);
                opRefActivatingFifo.setInstanceName(findInstanceName(compImpl, op.receiver));
                opRefActivatingFifo.setOperationName(op.receiver.getName());
                receiversType.getDynamicTrigger().add(opRefActivatingFifo);
            }
        }

        for (OpSender opSender : senders) {
            if (opSender instanceof OpTriggerSender op) {
                OpRefTrigger opRefTrigger = IMPFACTORY.createOpRefTrigger();
                if (op.period != null) opRefTrigger.setPeriod(op.period);
                opRefTrigger.setInstanceName(findInstanceName(compImpl, op.sender));
                sendersType.getTrigger().add(opRefTrigger);
            } else if (opSender instanceof OpAnySender op) {
                if (op.sender instanceof EventSenderInstance opMI) {
                    OpRef opRef = IMPFACTORY.createOpRef();
                    opRef.setOperationName(op.sender.getName());
                    opRef.setInstanceName(findInstanceName(compImpl, opMI));
                    sendersType.getModuleInstance().add(opRef);
                } else if (op.sender instanceof EventDefinitionInstance opDef) {
                    OpRef opRef = IMPFACTORY.createOpRef();
                    opRef.setOperationName(opDef.getName());
                    opRef.setInstanceName(findInstanceName(compImpl, opDef));
                    if (compImpl != null) {
                        boolean isService = compImpl.getComponentDefinitionServices().stream().anyMatch(s -> s.getOperations().contains(opDef));
                        if (isService || opDef.eContainer() instanceof ServiceOfLinkedComponentDefinition) {
                            sendersType.getService().add(opRef);
                        } else {
                            sendersType.getReference().add(opRef);
                        }
                    }
                } else if (op.sender instanceof ExternalSenderOperation opExt) {
                    OpRefExternal opRefExternal = IMPFACTORY.createOpRefExternal();
                    if (opExt.isSetLanguage()) opRefExternal.setLanguage(opExt.getLanguage());
                    opRefExternal.setOperationName(opExt.getName());
                    sendersType.getExternal().add(opRefExternal);
                } else if (op.sender instanceof DynamicTriggerEventSenderInstance opDynamic) {
                    OpRef opRef = IMPFACTORY.createOpRef();
                    opRef.setInstanceName(findInstanceName(compImpl, opDynamic));
                    opRef.setOperationName(opDynamic.getName());
                    sendersType.getDynamicTrigger().add(opRef);
                }
            }
        }

        ecoaEventLink.setSenders(sendersType);
        ecoaEventLink.setReceivers(receiversType);
        return ecoaEventLink;
    }

    // ---- Helper to build the sender/receiver hash maps ----

    private static void convertEventLinksToHashMap(
            ConcurrentHashMap<OpSender, ArrayList<OpReceiver>> eventLinkAssociationFromSender,
            ConcurrentHashMap<OpReceiver, ArrayList<OpSender>> eventLinkAssociationFromReceiver,
            EventLink edtOperationLink) {
        Integer id = edtOperationLink.isSetId() ? edtOperationLink.getId() : null;

        if (edtOperationLink instanceof EventLinkToDefinitionOperation eventLink
                && eventLink.getSender() != null && eventLink.getReceiver() != null) {
            EventLinkSender sender = eventLink.getSender();
            EventDefinitionInstance receiver = eventLink.getReceiver();
            OpAnySender opAnySender = new OpAnySender(sender, id);
            OpDef opDef = new OpDef(receiver, id);
            addSenderReceiver(eventLinkAssociationFromSender, eventLinkAssociationFromReceiver, opAnySender, opDef);

        } else if (edtOperationLink instanceof EventLinkActivatableFifo eventLink
                && eventLink.getSender() != null && eventLink.getReceiver() != null) {
            EventLinkSender sender = eventLink.getSender();
            EventReceiverInstance receiver = eventLink.getReceiver();
            BigInteger receiverFifoSize = eventLink.isSetReceiverFifoSize() ? eventLink.getReceiverFifoSize() : null;
            Boolean activating = eventLink.isSetReceiverActivating() ? eventLink.isReceiverActivating() : null;
            OpAnySender opAnySender = new OpAnySender(sender, id);
            OpActivatableFifo opActivatableFifo = new OpActivatableFifo(receiverFifoSize, activating, receiver, id);
            addSenderReceiver(eventLinkAssociationFromSender, eventLinkAssociationFromReceiver, opAnySender, opActivatableFifo);

        } else if (edtOperationLink instanceof EventLinkActivatingFifo eventLink
                && eventLink.getSender() != null && eventLink.getReceiver() != null) {
            EventLinkSender sender = eventLink.getSender();
            DynamicTriggerEventReceiverInstance receiver = eventLink.getReceiver();
            BigInteger receiverFifoSize = eventLink.isSetReceiverFifoSize() ? eventLink.getReceiverFifoSize() : null;
            OpAnySender opAnySender = new OpAnySender(sender, id);
            OpDynamicReceiver opDynamicReceiver = new OpDynamicReceiver(receiver, receiverFifoSize, id);
            addSenderReceiver(eventLinkAssociationFromSender, eventLinkAssociationFromReceiver, opAnySender, opDynamicReceiver);

        } else if (edtOperationLink instanceof EventLinkActivatingFifoFromTrigger eventLink
                && eventLink.getSender() != null && eventLink.getReceiver() != null) {
            TriggerSender sender = eventLink.getSender();
            DynamicTriggerEventReceiverInstance receiver = eventLink.getReceiver();
            BigInteger receiverFifoSize = eventLink.isSetReceiverFifoSize() ? eventLink.getReceiverFifoSize() : null;
            Double period = eventLink.getTriggerPeriod();
            OpTriggerSender opTriggerSender = new OpTriggerSender(sender, period, id);
            OpDynamicReceiver opDynamicReceiver = new OpDynamicReceiver(receiver, receiverFifoSize, id);
            addSenderReceiver(eventLinkAssociationFromSender, eventLinkAssociationFromReceiver, opTriggerSender, opDynamicReceiver);

        } else if (edtOperationLink instanceof EventLinkActivatableFifoFromTrigger eventLink
                && eventLink.getSender() != null && eventLink.getReceiver() != null) {
            TriggerSender sender = eventLink.getSender();
            EventReceiverInstance receiver = eventLink.getReceiver();
            Double period = eventLink.getTriggerPeriod();
            BigInteger receiverFifoSize = eventLink.isSetReceiverFifoSize() ? eventLink.getReceiverFifoSize() : null;
            Boolean activating = eventLink.isSetReceiverActivating() ? eventLink.isReceiverActivating() : null;
            OpTriggerSender opTriggerSender = new OpTriggerSender(sender, period, id);
            OpActivatableFifo opActivatableFifo = new OpActivatableFifo(receiverFifoSize, activating, receiver, id);
            addSenderReceiver(eventLinkAssociationFromSender, eventLinkAssociationFromReceiver, opTriggerSender, opActivatableFifo);

        } else if (edtOperationLink instanceof EventLinkToDefinitionOperationFromTrigger eventLink
                && eventLink.getSender() != null && eventLink.getReceiver() != null) {
            TriggerSender sender = eventLink.getSender();
            Double period = eventLink.getTriggerPeriod();
            EventDefinitionInstance receiver = eventLink.getReceiver();
            OpTriggerSender opTriggerSender = new OpTriggerSender(sender, period, id);
            OpDef opDef = new OpDef(receiver, id);
            addSenderReceiver(eventLinkAssociationFromSender, eventLinkAssociationFromReceiver, opTriggerSender, opDef);
        }
    }

    private static <S extends OpSender, R extends OpReceiver> void addSenderReceiver(
            ConcurrentHashMap<OpSender, ArrayList<OpReceiver>> fromSender,
            ConcurrentHashMap<OpReceiver, ArrayList<OpSender>> fromReceiver,
            S sender, R receiver) {
        fromSender.putIfAbsent(sender, new ArrayList<>());
        if (!fromSender.get(sender).contains(receiver)) fromSender.get(sender).add(receiver);
        fromReceiver.putIfAbsent(receiver, new ArrayList<>());
        if (!fromReceiver.get(receiver).contains(sender)) fromReceiver.get(receiver).add(sender);
    }

    // ---- Inner helper classes ----

    interface OpReceiver {
        Integer getId();
    }

    static class OpActivatableFifo implements OpReceiver {
        protected Boolean activating;
        protected BigInteger fifo;
        protected EventReceiverInstance receiver;
        protected Integer id;

        public OpActivatableFifo(BigInteger fifo, Boolean activating, EventReceiverInstance receiver, Integer id) {
            this.activating = activating;
            this.id = id;
            this.fifo = fifo;
            this.receiver = receiver;
        }

        @Override
        public int hashCode() { return Objects.hash(activating, fifo, id, receiver); }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            OpActivatableFifo other = (OpActivatableFifo) obj;
            return Objects.equals(activating, other.activating) && Objects.equals(fifo, other.fifo)
                    && Objects.equals(id, other.id) && Objects.equals(receiver, other.receiver);
        }

        @Override
        public Integer getId() { return id; }
    }

    static class OpDef implements OpReceiver {
        protected EventDefinitionInstance receiver;
        protected Integer id;

        public OpDef(EventDefinitionInstance receiver, Integer id) {
            this.receiver = receiver;
            this.id = id;
        }

        @Override
        public int hashCode() { return Objects.hash(id, receiver); }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            OpDef other = (OpDef) obj;
            return Objects.equals(id, other.id) && Objects.equals(receiver, other.receiver);
        }

        @Override
        public Integer getId() { return id; }
    }

    static class OpDynamicReceiver implements OpReceiver {
        protected DynamicTriggerEventReceiverInstance receiver;
        protected BigInteger fifo;
        protected Integer id;

        public OpDynamicReceiver(DynamicTriggerEventReceiverInstance receiver, BigInteger fifo, Integer id) {
            this.receiver = receiver;
            this.fifo = fifo;
            this.id = id;
        }

        @Override
        public int hashCode() { return Objects.hash(fifo, id, receiver); }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            OpDynamicReceiver other = (OpDynamicReceiver) obj;
            return Objects.equals(fifo, other.fifo) && Objects.equals(id, other.id)
                    && Objects.equals(receiver, other.receiver);
        }

        @Override
        public Integer getId() { return id; }
    }

    interface OpSender {
        Integer getId();
    }

    static class OpTriggerSender implements OpSender {
        protected TriggerSender sender;
        protected Double period;
        protected Integer id;

        public OpTriggerSender(TriggerSender sender, Double period, Integer id) {
            this.sender = sender;
            this.period = period;
            this.id = id;
        }

        @Override
        public int hashCode() { return Objects.hash(id, period, sender); }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            OpTriggerSender other = (OpTriggerSender) obj;
            return Objects.equals(id, other.id) && Objects.equals(period, other.period)
                    && Objects.equals(sender, other.sender);
        }

        @Override
        public Integer getId() { return id; }
    }

    static class OpAnySender implements OpSender {
        protected EventLinkSender sender;
        protected Integer id;

        public OpAnySender(EventLinkSender sender, Integer id) {
            this.sender = sender;
            this.id = id;
        }

        @Override
        public int hashCode() { return Objects.hash(id, sender); }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            OpAnySender other = (OpAnySender) obj;
            return Objects.equals(id, other.id) && Objects.equals(sender, other.sender);
        }

        @Override
        public Integer getId() { return id; }
    }
}
