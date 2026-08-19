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

import edtimplementation.DataLink;
import edtimplementation.DataLinkActivatableFifo;
import edtimplementation.DataLinkToServiceOperation;
import edtimplementation.DataLinkWriter;
import edtimplementation.DataReaderInstance;
import edtimplementation.DataWriterInstance;
import edtimplementation.ModuleInstance;
import edtimplementation.TriggerInstance;
import edtimplementation.DynamicTriggerInstance;
import edtimplementation.ReferenceOfLinkedComponentDefinition;
import edtimplementation.ServiceOfLinkedComponentDefinition;
import edtimplementation.VersionedDataReferenceInstance;
import edtimplementation.VersionedDataServiceInstance;
import technology.ecoa.implementation._2.OpRef;
import technology.ecoa.implementation._2.OpRefActivatableFifo;
import technology.ecoa.implementation._2.ReadersType;
import technology.ecoa.implementation._2.WritersType;
import technology.ecoa.implementation._2.impFactory;

/**
 * Converts EDT DataLink objects to ECOA DataLink XML elements.
 * Multiple EDT DataLink objects with the same writer may be merged into one ECOA DataLink.
 * Based on the original ComponentImplementationDataLinkExportConverter from edt-tmp.
 */
public class ComponentImplementationDataLinkExportConverter {

    private static final impFactory IMPFACTORY = impFactory.eINSTANCE;

    private ComponentImplementationDataLinkExportConverter() {
        // Utility class
    }

    /**
     * Convert EDT DataLinks to ECOA DataLinks, merging multiple links that share the same writer.
     */
    public static ArrayList<technology.ecoa.implementation._2.DataLink> recreateDataLinks(
            edtimplementation.ComponentImplementation compImpl, ArrayList<DataLink> edtDataLinks) {
        ArrayList<technology.ecoa.implementation._2.DataLink> ecoaDataLinks = new ArrayList<>();
        ConcurrentHashMap<OpWriter, ArrayList<OpReader>> dataLinkAssociationFromWriter = new ConcurrentHashMap<>();
        ConcurrentHashMap<OpReader, ArrayList<OpWriter>> dataLinkAssociationFromReader = new ConcurrentHashMap<>();

        for (DataLink edtOperationLink : edtDataLinks) {
            convertDataLinksToHashMap(dataLinkAssociationFromWriter, dataLinkAssociationFromReader, edtOperationLink);
        }

        ArrayList<OpWriter> toJump = new ArrayList<>();
        ArrayList<OpWriter> nonUniqueWriters = new ArrayList<>();

        for (Entry<OpWriter, ArrayList<OpReader>> entry : dataLinkAssociationFromWriter.entrySet()) {
            OpWriter writer = entry.getKey();
            if (!toJump.contains(writer)) {
                ArrayList<OpReader> readers = entry.getValue();
                if (writer.writer instanceof DataWriterInstance) {
                    ArrayList<OpWriter> trueWriters = new ArrayList<>();
                    trueWriters.add(writer);
                    for (OpReader reader : readers) {
                        ArrayList<OpWriter> writersOfReader = dataLinkAssociationFromReader.get(reader);
                        for (OpWriter writerOfReader : writersOfReader) {
                            if (!Objects.equals(writer, writerOfReader) && !toJump.contains(writerOfReader)
                                    && dataLinkAssociationFromWriter.get(writerOfReader).containsAll(readers)
                                    && Objects.equals(writer.id, writerOfReader.id)
                                    && Objects.equals(writer.controlled, writerOfReader.controlled)) {
                                trueWriters.add(writerOfReader);
                                if (Objects.equals(dataLinkAssociationFromWriter.get(writerOfReader), readers)) {
                                    dataLinkAssociationFromWriter.remove(writerOfReader);
                                    nonUniqueWriters.remove(writerOfReader);
                                    toJump.add(writerOfReader);
                                } else {
                                    dataLinkAssociationFromWriter.get(writerOfReader).removeAll(readers);
                                }
                            }
                        }
                    }
                    if (!readers.isEmpty() && !trueWriters.isEmpty()) {
                        ecoaDataLinks.add(recreateECOADataLink(compImpl, writer, readers, trueWriters));
                    }
                } else {
                    nonUniqueWriters.add(writer);
                }
            }
        }

        Iterator<OpWriter> iterator = nonUniqueWriters.iterator();
        while (iterator.hasNext()) {
            OpWriter opWriter = iterator.next();
            if (!toJump.contains(opWriter)) {
                ArrayList<OpReader> readers = dataLinkAssociationFromWriter.get(opWriter);
                ArrayList<OpWriter> trueWriters = new ArrayList<>();
                trueWriters.add(opWriter);
                for (OpReader reader : readers) {
                    ArrayList<OpWriter> writersOfReader = dataLinkAssociationFromReader.get(reader);
                    for (OpWriter writerOfReader : writersOfReader) {
                        if (!Objects.equals(opWriter, writerOfReader)
                                && dataLinkAssociationFromWriter.get(writerOfReader) != null
                                && dataLinkAssociationFromWriter.get(writerOfReader).containsAll(readers)
                                && Objects.equals(opWriter.id, writerOfReader.id)
                                && Objects.equals(opWriter.controlled, writerOfReader.controlled)) {
                            trueWriters.add(writerOfReader);
                            if (Objects.equals(dataLinkAssociationFromWriter.get(writerOfReader), readers)) {
                                dataLinkAssociationFromWriter.remove(writerOfReader);
                                toJump.add(writerOfReader);
                            } else {
                                dataLinkAssociationFromWriter.get(writerOfReader).removeAll(readers);
                            }
                        }
                    }
                }
                ecoaDataLinks.add(recreateECOADataLink(compImpl, opWriter, readers, trueWriters));
            }
        }

        return ecoaDataLinks;
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
        System.err.println("DEBUG FIND_INSTANCE_NAME FAILED (DataLink): op=" + op + ", class=" + op.getClass().getName() + ", targetURI=" + org.eclipse.emf.ecore.util.EcoreUtil.getURI(op) + ", compImpl=" + (compImpl != null ? compImpl.getName() : "null"));
        return "";
    }

    private static technology.ecoa.implementation._2.DataLink recreateECOADataLink(
            edtimplementation.ComponentImplementation compImpl, OpWriter writer,
            ArrayList<OpReader> readers, ArrayList<OpWriter> trueWriters) {
        technology.ecoa.implementation._2.DataLink ecoaDataLink = IMPFACTORY.createDataLink();
        WritersType writersType = IMPFACTORY.createWritersType();
        ReadersType readersType = IMPFACTORY.createReadersType();

        if (writer.id != null) {
            ecoaDataLink.setId(writer.id);
        }
        if (writer.controlled != null) {
            ecoaDataLink.setControlled(writer.controlled);
        }

        for (OpWriter opWriter : trueWriters) {
            if (opWriter.writer instanceof VersionedDataReferenceInstance versionedData) {
                OpRef opRef = IMPFACTORY.createOpRef();
                opRef.setInstanceName(findInstanceName(compImpl, versionedData));
                opRef.setOperationName(versionedData.getName());
                writersType.getReference().add(opRef);
            } else if (opWriter.writer instanceof DataWriterInstance dataInstance) {
                OpRef opRef = IMPFACTORY.createOpRef();
                opRef.setInstanceName(findInstanceName(compImpl, dataInstance));
                opRef.setOperationName(dataInstance.getName());
                writersType.getModuleInstance().add(opRef);
            }
        }

        for (OpReader opReader : readers) {
            if (opReader instanceof OpActivatableFifo dataReaderInstance) {
                OpRefActivatableFifo opRefActivatableFifo = IMPFACTORY.createOpRefActivatableFifo();
                if (dataReaderInstance.activating != null) {
                    opRefActivatableFifo.setActivating(dataReaderInstance.activating);
                }
                if (dataReaderInstance.fifo != null) {
                    opRefActivatableFifo.setFifoSize(dataReaderInstance.fifo);
                }
                opRefActivatableFifo.setInstanceName(findInstanceName(compImpl, dataReaderInstance.reader));
                opRefActivatableFifo.setOperationName(dataReaderInstance.reader.getName());
                readersType.getModuleInstance().add(opRefActivatableFifo);
            } else if (opReader instanceof OpServ versionedData) {
                OpRef opRef = IMPFACTORY.createOpRef();
                opRef.setInstanceName(findInstanceName(compImpl, versionedData.reader));
                opRef.setOperationName(versionedData.reader.getName());
                readersType.getService().add(opRef);
            }
        }

        ecoaDataLink.setWriters(writersType);
        ecoaDataLink.setReaders(readersType);
        return ecoaDataLink;
    }

    private static void convertDataLinksToHashMap(
            ConcurrentHashMap<OpWriter, ArrayList<OpReader>> dataLinkAssociationFromWriter,
            ConcurrentHashMap<OpReader, ArrayList<OpWriter>> dataLinkAssociationFromReader,
            DataLink edtOperationLink) {

        Integer id = edtOperationLink.isSetId() ? edtOperationLink.getId() : null;
        Boolean controlled = edtOperationLink.isSetControlled() ? edtOperationLink.isControlled() : null;

        if (edtOperationLink instanceof DataLinkToServiceOperation dataLink
                && dataLink.getWriter() != null && dataLink.getReader() != null) {
            DataWriterInstance writer = dataLink.getWriter();
            OpWriter opServWrite = new OpWriter(id, controlled, writer);
            VersionedDataServiceInstance reader = dataLink.getReader();
            OpServ opServ = new OpServ(reader, id);
            dataLinkAssociationFromWriter.putIfAbsent(opServWrite, new ArrayList<>());
            if (!dataLinkAssociationFromWriter.get(opServWrite).contains(opServ)) {
                dataLinkAssociationFromWriter.get(opServWrite).add(opServ);
            }
            dataLinkAssociationFromReader.putIfAbsent(opServ, new ArrayList<>());
            if (!dataLinkAssociationFromReader.get(opServ).contains(opServWrite)) {
                dataLinkAssociationFromReader.get(opServ).add(opServWrite);
            }
        } else if (edtOperationLink instanceof DataLinkActivatableFifo dataLink
                && dataLink.getWriter() != null && dataLink.getReader() != null) {
            DataLinkWriter writer = dataLink.getWriter();
            OpWriter opServWrite = new OpWriter(id, controlled, writer);
            BigInteger readerFifoSize = dataLink.isSetReaderFifoSize() ? dataLink.getReaderFifoSize() : null;
            Boolean activating = dataLink.isSetReaderActivating() ? dataLink.isReaderActivating() : null;
            DataReaderInstance reader = dataLink.getReader();
            OpActivatableFifo opRef = new OpActivatableFifo(readerFifoSize, activating, reader, id);
            dataLinkAssociationFromWriter.putIfAbsent(opServWrite, new ArrayList<>());
            dataLinkAssociationFromWriter.get(opServWrite).add(opRef);
            dataLinkAssociationFromReader.putIfAbsent(opRef, new ArrayList<>());
            dataLinkAssociationFromReader.get(opRef).add(opServWrite);
        }
    }

    // --- Inner helper classes ---

    static class OpActivatableFifo implements OpReader {
        protected Integer id;
        protected Boolean activating;
        protected BigInteger fifo;
        protected DataReaderInstance reader;

        public OpActivatableFifo(BigInteger fifo, Boolean activating, DataReaderInstance reader, Integer id) {
            this.activating = activating;
            this.fifo = fifo;
            this.reader = reader;
            this.id = id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(activating, fifo, id, reader);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            OpActivatableFifo other = (OpActivatableFifo) obj;
            return Objects.equals(activating, other.activating) && Objects.equals(fifo, other.fifo)
                    && Objects.equals(id, other.id) && Objects.equals(reader, other.reader);
        }

        @Override
        public Integer getId() { return id; }
    }

    static class OpServ implements OpReader {
        protected VersionedDataServiceInstance reader;
        protected Integer id;

        public OpServ(VersionedDataServiceInstance reader, Integer id) {
            this.reader = reader;
            this.id = id;
        }

        @Override
        public int hashCode() { return Objects.hash(id, reader); }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            OpServ other = (OpServ) obj;
            return Objects.equals(id, other.id) && Objects.equals(reader, other.reader);
        }

        @Override
        public Integer getId() { return id; }
    }

    interface OpReader {
        Integer getId();
    }

    static class OpWriter {
        protected Boolean controlled;
        protected Integer id;
        protected DataLinkWriter writer;

        public OpWriter(Integer id, Boolean controlled, DataLinkWriter writer) {
            this.controlled = controlled;
            this.id = id;
            this.writer = writer;
        }

        @Override
        public int hashCode() { return Objects.hash(controlled, id, writer); }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            OpWriter other = (OpWriter) obj;
            return Objects.equals(controlled, other.controlled) && Objects.equals(id, other.id)
                    && Objects.equals(writer, other.writer);
        }
    }
}
