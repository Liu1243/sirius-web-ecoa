/**
 * Copyright (c) 2023 Dassault Aviation
 *
 * SPDX-License-Identifier: MIT
 */
package edtproject.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.emf.ecore.EObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edtdeployment.ComputingNodeConfiguration;
import edtdeployment.Deployment;
import edtdeployment.DeployedModuleInstance;
import edtdeployment.DeployedTriggerInstance;
import edtdeployment.PlatformConfiguration;
import edtdeployment.ProtectionDomain;
import edtimplementation.ComponentImplementation;
import edtimplementation.DataLinkActivatableFifo;
import edtimplementation.DataLinkToServiceOperation;
import edtimplementation.EventLink;
import edtimplementation.EventLinkActivatableFifo;
import edtimplementation.EventLinkActivatableFifoFromTrigger;
import edtimplementation.EventLinkActivatingFifo;
import edtimplementation.EventLinkActivatingFifoFromTrigger;
import edtimplementation.EventLinkReceiver;
import edtimplementation.EventLinkSender;
import edtimplementation.EventLinkToDefinitionOperation;
import edtimplementation.EventLinkToDefinitionOperationFromTrigger;
import edtimplementation.Instance;
import edtimplementation.ModuleImplementation;
import edtimplementation.ModuleInstance;
import edtimplementation.ModuleOperation;
import edtimplementation.ModuleType;
import edtimplementation.ModuleTypeProperty;
import edtimplementation.OperationInstance;
import edtimplementation.OperationLink;
import edtimplementation.PropertyValue;
import edtimplementation.RequestLink;
import edtimplementation.RequestLinkActivatableFifo;
import edtimplementation.RequestLinkActivatingActivatableFifo;
import edtimplementation.RequestLinkActivatingToReferenceOperation;
import edtimplementation.RequestLinkClient;
import edtimplementation.RequestLinkServer;
import edtimplementation.ServRefOfLinkedComponentDefinition;
import edtimplementation.VersionedDataRead;
import edtimplementation.VersionedDataWritten;
import edtinterface.Data;
import edtinterface.Event;
import edtinterface.OperationType;
import edtinterface.Parameter;
import edtinterface.RequestResponse;
import edtinterface.ServiceDefinition;
import edtlogical.LogicalComputingNode;
import edtlogical.LogicalComputingPlatform;
import edtlogical.LogicalProcessor;
import edtlogical.LogicalSystem;
import edtproject.Component;
import edtproject.ComponentDefinition;
import edtproject.ComponentDefinitionReference;
import edtproject.ComponentDefinitionService;
import edtproject.ComponentReference;
import edtproject.ComponentService;
import edtproject.Composite;
import edtproject.CompositeReference;
import edtproject.CompositeService;
import edtproject.FinalAssembly;
import edtproject.ServiceLink;
import edtproject.Step0;
import edtproject.Step1;
import edtproject.Step2;
import edtproject.Step3;
import edtproject.Step4;
import edtproject.Step5;
import edtproject.Steps;
import edttype.Array;
import edttype.Constant;
import edttype.EDTDataType;
import edttype.EnumValue;
import edttype.Field;
import edttype.FixedArray;
import edttype.Library;
import edttype.Record;
import edttype.Simple;
import edttype.Union;
import edttype.VariantRecord;

/**
 * Service to export a Steps EMF model to ECOA standard XML files in a ZIP archive.
 */
public class StepsExportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StepsExportService.class);
    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
    private static final String I1 = "  ";
    private static final String I2 = "    ";
    private static final String I3 = "      ";
    private static final String I4 = "        ";

    /** Export all Steps to a ZIP archive. */
    public byte[] exportToZip(Steps steps, String projectName) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            addZipEntry(zos, projectName + ".project.xml", generateProjectXml(steps, projectName));
            addTypesFiles(zos, steps);
            addServicesFiles(zos, steps);
            addComponentDefinitionsFiles(zos, steps);
            addInitialAssemblyFile(zos, steps);
            addComponentImplementationsFiles(zos, steps);
            addIntegrationFiles(zos, steps);
        } catch (IOException e) {
            LOGGER.error("Error creating Steps ZIP", e);
        }
        return baos.toByteArray();
    }

    // =========================================================================
    // project.xml
    // =========================================================================

    private String generateProjectXml(Steps steps, String projectName) {
        StringBuilder sb = new StringBuilder(XML_HEADER);
        sb.append("<ECOAProject xmlns=\"http://www.ecoa.technology/project-2.0\" name=\"")
          .append(x(projectName)).append("\">\n");

        String outDir = (steps.getOutputDirectory() != null && steps.getOutputDirectory().getName() != null)
                ? steps.getOutputDirectory().getName() : "6-output";
        sb.append(I1).append("<outputDirectory>").append(x(outDir)).append("</outputDirectory>\n");

        Step0 s0 = steps.getStep0();
        if (s0 != null && !s0.getTypes().isEmpty()) {
            sb.append(I1).append("<types>\n");
            for (Library lib : s0.getTypes())
                sb.append(I2).append("<file>0-Types\\").append(x(lib.getName())).append(".types.xml</file>\n");
            sb.append(I1).append("</types>\n");
        }

        Step1 s1 = steps.getStep1();
        if (s1 != null && !s1.getServices().isEmpty()) {
            sb.append(I1).append("<serviceDefinitions>\n");
            for (ServiceDefinition svc : s1.getServices())
                sb.append(I2).append("<file>1-Services\\").append(x(svc.getName())).append(".interface.xml</file>\n");
            sb.append(I1).append("</serviceDefinitions>\n");
        }

        Step2 s2 = steps.getStep2();
        if (s2 != null && !s2.getComponentDefinitions().isEmpty()) {
            sb.append(I1).append("<componentDefinitions>\n");
            for (ComponentDefinition cd : s2.getComponentDefinitions())
                sb.append(I2).append("<file>2-ComponentDefinitions\\").append(x(cd.getName())).append("\\").append(x(cd.getName())).append(".componentType</file>\n");
            sb.append(I1).append("</componentDefinitions>\n");
        }

        Step3 s3 = steps.getStep3();
        if (s3 != null && s3.getInitialAssembly() != null)
            sb.append(I1).append("<initialAssembly>3-InitialAssembly\\").append(x(s3.getInitialAssembly().getName())).append(".composite</initialAssembly>\n");

        Step4 s4 = steps.getStep4();
        if (s4 != null && !s4.getComponentImplementations().isEmpty()) {
            sb.append(I1).append("<componentImplementations>\n");
            for (ComponentImplementation impl : s4.getComponentImplementations())
                sb.append(I2).append("<file>4-ComponentImplementations\\").append(x(impl.getName())).append("\\").append(x(impl.getName())).append(".impl.xml</file>\n");
            sb.append(I1).append("</componentImplementations>\n");
        }

        Step5 s5 = steps.getStep5();
        if (s5 != null) {
            FinalAssembly fa = s5.getFinalAssembly();
            if (fa != null && fa.getFinalAssembly() != null)
                sb.append(I1).append("<implementationAssembly>5-Integration\\").append(x(fa.getFinalAssembly().getName())).append(".impl.composite</implementationAssembly>\n");
            LogicalSystem ls = s5.getLogicalSystem();
            if (ls != null)
                sb.append(I1).append("<logicalSystem>5-Integration\\").append(x(ls.getId())).append(".logical-system.xml</logicalSystem>\n");
            Deployment dep = s5.getDeployment();
            if (dep != null)
                sb.append(I1).append("<deploymentSchema>5-Integration\\").append(x(dep.getName())).append(".deployment.xml</deploymentSchema>\n");
        }

        sb.append("</ECOAProject>\n");
        return sb.toString();
    }

    // =========================================================================
    // Step0: types.xml
    // =========================================================================

    private void addTypesFiles(ZipOutputStream zos, Steps steps) throws IOException {
        Step0 s0 = steps.getStep0();
        if (s0 == null) return;
        for (Library lib : s0.getTypes())
            addZipEntry(zos, "0-Types/" + lib.getName() + ".types.xml", generateTypesXml(lib));
    }

    private String generateTypesXml(Library lib) {
        StringBuilder sb = new StringBuilder(XML_HEADER);
        sb.append("<library xmlns=\"http://www.ecoa.technology/types-2.0\">\n");
        for (Library u : lib.getUsedLibraries())
            sb.append(I1).append("<use library=\"").append(x(u.getName())).append("\"/>\n");
        if (!lib.getDataTypes().isEmpty()) {
            sb.append(I1).append("<types>\n");
            for (EDTDataType dt : lib.getDataTypes())
                appendDataType(sb, dt, I2);
            sb.append(I1).append("</types>\n");
        }
        sb.append("</library>\n");
        return sb.toString();
    }

    private void appendDataType(StringBuilder sb, EDTDataType dt, String indent) {
        if (dt instanceof Simple s) {
            sb.append(indent).append("<simple name=\"").append(x(s.getName())).append("\"");
            if (s.getType() != null) sb.append(" type=\"").append(x(s.getType().getFullName())).append("\"");
            if (s.getUnit() != null && !s.getUnit().isEmpty()) sb.append(" unit=\"").append(x(s.getUnit())).append("\"");
            if (s.getMinRange() != null) sb.append(" minRange=\"").append(s.getMinRange()).append("\"");
            if (s.getMaxRange() != null) sb.append(" maxRange=\"").append(s.getMaxRange()).append("\"");
            if (s.getPrecision() != null) sb.append(" precision=\"").append(s.getPrecision()).append("\"");
            appendComment(sb, s.getComment());
            sb.append("/>\n");

        } else if (dt instanceof Record r) {
            sb.append(indent).append("<record name=\"").append(x(r.getName())).append("\"");
            appendComment(sb, r.getComment());
            sb.append(">\n");
            for (Field f : r.getField()) {
                sb.append(indent).append(I1).append("<field name=\"").append(x(f.getName())).append("\"");
                if (f.getType() != null) sb.append(" type=\"").append(x(f.getType().getFullName())).append("\"");
                appendComment(sb, f.getComment());
                sb.append("/>\n");
            }
            sb.append(indent).append("</record>\n");

        } else if (dt instanceof edttype.Enum e) {
            sb.append(indent).append("<enum name=\"").append(x(e.getName())).append("\"");
            if (e.getType() != null) sb.append(" type=\"").append(x(e.getType().getFullName())).append("\"");
            appendComment(sb, e.getComment());
            sb.append(">\n");
            for (EnumValue v : e.getValue()) {
                sb.append(indent).append(I1).append("<value name=\"").append(x(v.getName())).append("\"");
                if (v.getValnum() != null) sb.append(" valnum=\"").append(x(v.getValnum())).append("\"");
                appendComment(sb, v.getComment());
                sb.append("/>\n");
            }
            sb.append(indent).append("</enum>\n");

        } else if (dt instanceof Array a) {
            sb.append(indent).append("<array name=\"").append(x(a.getName())).append("\"");
            if (a.getItemType() != null) sb.append(" itemType=\"").append(x(a.getItemType().getFullName())).append("\"");
            if (a.getMaxNumber() != null) sb.append(" maxNumber=\"").append(x(a.getMaxNumber())).append("\"");
            appendComment(sb, a.getComment());
            sb.append("/>\n");

        } else if (dt instanceof FixedArray fa) {
            sb.append(indent).append("<fixedArray name=\"").append(x(fa.getName())).append("\"");
            if (fa.getItemType() != null) sb.append(" itemType=\"").append(x(fa.getItemType().getFullName())).append("\"");
            sb.append(" maxNumber=\"").append(fa.getMaxNumber()).append("\"");
            appendComment(sb, fa.getComment());
            sb.append("/>\n");

        } else if (dt instanceof VariantRecord vr) {
            sb.append(indent).append("<variantRecord name=\"").append(x(vr.getName())).append("\"");
            if (vr.getSelectName() != null && !vr.getSelectName().isEmpty()) sb.append(" selectName=\"").append(x(vr.getSelectName())).append("\"");
            if (vr.getSelectType() != null) sb.append(" selectType=\"").append(x(vr.getSelectType().getFullName())).append("\"");
            appendComment(sb, vr.getComment());
            sb.append(">\n");
            for (Field f : vr.getField()) {
                sb.append(indent).append(I1).append("<field name=\"").append(x(f.getName())).append("\"");
                if (f.getType() != null) sb.append(" type=\"").append(x(f.getType().getFullName())).append("\"");
                appendComment(sb, f.getComment());
                sb.append("/>\n");
            }
            for (Union u : vr.getUnion()) {
                sb.append(indent).append(I1).append("<union name=\"").append(x(u.getName())).append("\"");
                if (u.getType() != null) sb.append(" type=\"").append(x(u.getType().getFullName())).append("\"");
                if (u.getWhen() != null) sb.append(" when=\"").append(x(u.getWhen())).append("\"");
                appendComment(sb, u.getComment());
                sb.append("/>\n");
            }
            sb.append(indent).append("</variantRecord>\n");

        } else if (dt instanceof Constant c) {
            sb.append(indent).append("<constant name=\"").append(x(c.getName())).append("\"");
            if (c.getType() != null) sb.append(" type=\"").append(x(c.getType().getFullName())).append("\"");
            if (c.getValue() != null) sb.append(" value=\"").append(x(c.getValue().toString())).append("\"");
            appendComment(sb, c.getComment());
            sb.append("/>\n");
        }
    }

    // =========================================================================
    // Step1: interface.xml
    // =========================================================================

    private void addServicesFiles(ZipOutputStream zos, Steps steps) throws IOException {
        Step1 s1 = steps.getStep1();
        if (s1 == null) return;
        for (ServiceDefinition svc : s1.getServices())
            addZipEntry(zos, "1-Services/" + svc.getName() + ".interface.xml", generateInterfaceXml(svc));
    }

    private String generateInterfaceXml(ServiceDefinition svc) {
        StringBuilder sb = new StringBuilder(XML_HEADER);
        sb.append("<serviceDefinition xmlns=\"http://www.ecoa.technology/interface-2.0\">\n");
        for (Library u : svc.getUsedLibraries())
            sb.append(I1).append("<use library=\"").append(x(u.getName())).append("\"/>\n");
        if (!svc.getOperations().isEmpty()) {
            sb.append(I1).append("<operations>\n");
            for (OperationType op : svc.getOperations())
                appendInterfaceOperation(sb, op, I2);
            sb.append(I1).append("</operations>\n");
        }
        sb.append("</serviceDefinition>\n");
        return sb.toString();
    }

    private void appendInterfaceOperation(StringBuilder sb, OperationType op, String indent) {
        if (op instanceof Data d) {
            sb.append(indent).append("<data name=\"").append(x(d.getName())).append("\"");
            if (d.getType() != null) sb.append(" type=\"").append(x(d.getType().getFullName())).append("\"");
            sb.append("/>\n");
        } else if (op instanceof Event e) {
            sb.append(indent).append("<event name=\"").append(x(e.getName())).append("\"");
            if (e.getDirection() != null) sb.append(" direction=\"").append(e.getDirection().getLiteral()).append("\"");
            if (e.getInput().isEmpty()) {
                sb.append("/>\n");
            } else {
                sb.append(">\n");
                for (Parameter p : e.getInput())
                    appendParam(sb, p, indent + I1, "input");
                sb.append(indent).append("</event>\n");
            }
        } else if (op instanceof RequestResponse rr) {
            sb.append(indent).append("<requestResponse name=\"").append(x(rr.getName())).append("\">\n");
            for (Parameter p : rr.getInput()) appendParam(sb, p, indent + I1, "input");
            for (Parameter p : rr.getOutput()) appendParam(sb, p, indent + I1, "output");
            sb.append(indent).append("</requestResponse>\n");
        }
    }

    private void appendParam(StringBuilder sb, Parameter p, String indent, String tag) {
        sb.append(indent).append("<").append(tag).append(" name=\"").append(x(p.getName())).append("\"");
        if (p.getType() != null) sb.append(" type=\"").append(x(p.getType().getFullName())).append("\"");
        sb.append("/>\n");
    }

    // =========================================================================
    // Step2: .componentType
    // =========================================================================

    private void addComponentDefinitionsFiles(ZipOutputStream zos, Steps steps) throws IOException {
        Step2 s2 = steps.getStep2();
        if (s2 == null) return;
        for (ComponentDefinition cd : s2.getComponentDefinitions())
            addZipEntry(zos, "2-ComponentDefinitions/" + cd.getName() + "/" + cd.getName() + ".componentType",
                    generateComponentTypeXml(cd));
    }

    private String generateComponentTypeXml(ComponentDefinition cd) {
        StringBuilder sb = new StringBuilder(XML_HEADER);
        sb.append("<csa:componentType xmlns:csa=\"http://docs.oasis-open.org/ns/opencsa/sca/200912\"")
          .append(" xmlns:ecoa-sca=\"http://www.ecoa.technology/sca-extension-2.0\">\n");
        for (ComponentDefinitionReference ref : cd.getReferences()) {
            sb.append(I1).append("<csa:reference name=\"").append(x(ref.getName())).append("\">\n");
            if (ref.getSyntax() != null)
                sb.append(I2).append("<ecoa-sca:interface syntax=\"").append(x(ref.getSyntax().getName())).append("\"/>\n");
            sb.append(I1).append("</csa:reference>\n");
        }
        for (ComponentDefinitionService svc : cd.getServices()) {
            sb.append(I1).append("<csa:service name=\"").append(x(svc.getName())).append("\">\n");
            if (svc.getSyntax() != null)
                sb.append(I2).append("<ecoa-sca:interface syntax=\"").append(x(svc.getSyntax().getName())).append("\"/>\n");
            sb.append(I1).append("</csa:service>\n");
        }
        sb.append("</csa:componentType>\n");
        return sb.toString();
    }

    // =========================================================================
    // Step3: InitialAssembly .composite
    // =========================================================================

    private void addInitialAssemblyFile(ZipOutputStream zos, Steps steps) throws IOException {
        Step3 s3 = steps.getStep3();
        if (s3 == null || s3.getInitialAssembly() == null) return;
        Composite c = s3.getInitialAssembly();
        addZipEntry(zos, "3-InitialAssembly/" + c.getName() + ".composite", generateCompositeXml(c, false));
    }

    private String generateCompositeXml(Composite composite, boolean isImpl) {
        StringBuilder sb = new StringBuilder(XML_HEADER);
        String ns = composite.getTargetNamespace() != null ? composite.getTargetNamespace() : "http://www.ecoa.technology/sca-extension-2.0";
        sb.append("<csa:composite xmlns:csa=\"http://docs.oasis-open.org/ns/opencsa/sca/200912\"")
          .append(" xmlns:ecoa-sca=\"http://www.ecoa.technology/sca-extension-2.0\"")
          .append(" name=\"").append(x(composite.getName())).append("\"")
          .append(" targetNamespace=\"").append(x(ns)).append("\">\n");

        for (CompositeService cs : composite.getServices())
            sb.append(I1).append("<csa:service name=\"").append(x(cs.getName())).append("\"/>\n");
        for (CompositeReference cr : composite.getReferences())
            sb.append(I1).append("<csa:reference name=\"").append(x(cr.getName())).append("\"/>\n");

        for (Component comp : composite.getComponents()) {
            sb.append(I1).append("<csa:component name=\"").append(x(comp.getName())).append("\">\n");
            if (comp.getComponentDefinition() != null) {
                if (isImpl && comp.getComponentImplementation() != null) {
                    sb.append(I2).append("<ecoa-sca:instance componentType=\"").append(x(comp.getComponentDefinition().getName())).append("\">\n");
                    sb.append(I3).append("<ecoa-sca:implementation name=\"").append(x(comp.getComponentImplementation().getName())).append("\"/>\n");
                    sb.append(I2).append("</ecoa-sca:instance>\n");
                } else {
                    sb.append(I2).append("<ecoa-sca:instance componentType=\"").append(x(comp.getComponentDefinition().getName())).append("\"/>\n");
                }
            }
            for (ComponentReference ref : comp.getComponentReferences())
                sb.append(I2).append("<csa:reference name=\"").append(x(ref.getName())).append("\"/>\n");
            for (ComponentService svc : comp.getComponentServices())
                sb.append(I2).append("<csa:service name=\"").append(x(svc.getName())).append("\"/>\n");
            sb.append(I1).append("</csa:component>\n");
        }

        for (ServiceLink link : composite.getServiceLinks()) {
            if (link.getSource() != null && link.getTarget() != null) {
                ComponentReference src = link.getSource();
                ComponentService tgt = link.getTarget();
                String srcComp = src.eContainer() instanceof Component c ? c.getName() : "";
                String tgtComp = tgt.eContainer() instanceof Component c ? c.getName() : "";
                sb.append(I1).append("<csa:wire source=\"").append(x(srcComp)).append("/").append(x(src.getName())).append("\"")
                  .append(" target=\"").append(x(tgtComp)).append("/").append(x(tgt.getName())).append("\"/>\n");
            }
        }

        sb.append("</csa:composite>\n");
        return sb.toString();
    }

    // =========================================================================
    // Step4: .impl.xml
    // =========================================================================

    private void addComponentImplementationsFiles(ZipOutputStream zos, Steps steps) throws IOException {
        Step4 s4 = steps.getStep4();
        if (s4 == null) return;
        for (ComponentImplementation impl : s4.getComponentImplementations())
            addZipEntry(zos, "4-ComponentImplementations/" + impl.getName() + "/" + impl.getName() + ".impl.xml",
                    generateImplXml(impl));
    }

    private String generateImplXml(ComponentImplementation impl) {
        StringBuilder sb = new StringBuilder(XML_HEADER);
        sb.append("<componentImplementation xmlns=\"http://www.ecoa.technology/implementation-2.0\"");
        if (impl.getComponentDefinition() != null)
            sb.append(" componentDefinition=\"").append(x(impl.getComponentDefinition().getName())).append("\"");
        sb.append(">\n");

        for (Library lib : impl.getUsedLibraries())
            sb.append(I1).append("<use library=\"").append(x(lib.getName())).append("\"/>\n");

        for (ModuleType mt : impl.getModuleTypes())
            appendModuleType(sb, mt);

        for (ModuleImplementation mi : impl.getModuleImplementations()) {
            sb.append(I1).append("<moduleImplementation");
            if (mi.isSetLanguage()) sb.append(" language=\"").append(mi.getLanguage().getLiteral()).append("\"");
            if (mi.getModuleType() != null) sb.append(" moduleType=\"").append(x(mi.getModuleType().getName())).append("\"");
            sb.append(" name=\"").append(x(mi.getName())).append("\"/>\n");
        }

        for (Instance inst : impl.getInstances()) {
            if (inst instanceof ModuleInstance mi)
                appendModuleInstance(sb, mi);
        }

        for (OperationLink link : impl.getOperationLinks())
            appendOperationLink(sb, link);

        sb.append("</componentImplementation>\n");
        return sb.toString();
    }

    private void appendModuleType(StringBuilder sb, ModuleType mt) {
        sb.append(I1).append("<moduleType name=\"").append(x(mt.getName())).append("\">\n");
        if (!mt.getProperties().isEmpty()) {
            sb.append(I2).append("<properties>\n");
            for (ModuleTypeProperty p : mt.getProperties()) {
                sb.append(I3).append("<property name=\"").append(x(p.getName())).append("\"");
                if (p.getType() != null) sb.append(" type=\"").append(x(p.getType().getFullName())).append("\"");
                sb.append("/>\n");
            }
            sb.append(I2).append("</properties>\n");
        }
        if (!mt.getOperations().isEmpty()) {
            sb.append(I2).append("<operations>\n");
            for (ModuleOperation op : mt.getOperations())
                appendModuleOperation(sb, op, I3);
            sb.append(I2).append("</operations>\n");
        }
        sb.append(I1).append("</moduleType>\n");
    }

    private void appendModuleOperation(StringBuilder sb, ModuleOperation op, String indent) {
        if (op instanceof VersionedDataRead vdr) {
            sb.append(indent).append("<dataRead name=\"").append(x(vdr.getName())).append("\"");
            if (vdr.getType() != null) sb.append(" type=\"").append(x(vdr.getType().getFullName())).append("\"");
            if (vdr.isSetNotifying()) sb.append(" notifying=\"").append(vdr.isNotifying()).append("\"");
            sb.append("/>\n");
        } else if (op instanceof VersionedDataWritten vdw) {
            sb.append(indent).append("<dataWritten name=\"").append(x(vdw.getName())).append("\"");
            if (vdw.getType() != null) sb.append(" type=\"").append(x(vdw.getType().getFullName())).append("\"");
            sb.append("/>\n");
        } else if (op instanceof edtimplementation.EventReceived er) {
            sb.append(indent).append("<eventReceived name=\"").append(x(er.getName())).append("\"/>\n");
        } else if (op instanceof edtimplementation.EventSent es) {
            sb.append(indent).append("<eventSent name=\"").append(x(es.getName())).append("\"/>\n");
        } else if (op instanceof edtimplementation.RequestReceived rr) {
            sb.append(indent).append("<requestReceived name=\"").append(x(rr.getName())).append("\"/>\n");
        } else if (op instanceof edtimplementation.RequestSent rs) {
            sb.append(indent).append("<requestSent name=\"").append(x(rs.getName())).append("\"/>\n");
        }
    }

    private void appendModuleInstance(StringBuilder sb, ModuleInstance mi) {
        sb.append(I1).append("<moduleInstance name=\"").append(x(mi.getName())).append("\"");
        if (mi.getRelativePriority() != null) sb.append(" relativePriority=\"").append(mi.getRelativePriority()).append("\"");
        if (mi.getModuleImplementation() != null) sb.append(" implementationName=\"").append(x(mi.getModuleImplementation().getName())).append("\"");
        List<PropertyValue> pvs = mi.getPropertyValues();
        if (pvs == null || pvs.isEmpty()) {
            sb.append("/>\n");
        } else {
            sb.append(">\n");
            sb.append(I2).append("<propertyValues>\n");
            for (PropertyValue pv : pvs) {
                sb.append(I3).append("<propertyValue name=\"").append(x(pv.getName())).append("\">");
                if (pv.getValue() != null) sb.append(x(pv.getValue()));
                sb.append("</propertyValue>\n");
            }
            sb.append(I2).append("</propertyValues>\n");
            sb.append(I1).append("</moduleInstance>\n");
        }
    }

    private void appendOperationLink(StringBuilder sb, OperationLink link) {
        if (link instanceof DataLinkActivatableFifo dl) {
            sb.append(I1).append("<dataLink>\n");
            sb.append(I2).append("<writers>\n");
            appendDataWriter(sb, dl.getWriter(), I3);
            sb.append(I2).append("</writers>\n");
            sb.append(I2).append("<readers>\n");
            appendDataReader(sb, dl.getReader(), I3);
            sb.append(I2).append("</readers>\n");
            sb.append(I1).append("</dataLink>\n");

        } else if (link instanceof DataLinkToServiceOperation dl) {
            sb.append(I1).append("<dataLink>\n");
            sb.append(I2).append("<writers>\n");
            appendDataWriter(sb, dl.getWriter(), I3);
            sb.append(I2).append("</writers>\n");
            sb.append(I2).append("<readers>\n");
            appendDataReader(sb, dl.getReader(), I3);
            sb.append(I2).append("</readers>\n");
            sb.append(I1).append("</dataLink>\n");

        } else if (link instanceof EventLink el) {
            EventLinkSender sender = EventLink.getEventLinkSender(el);
            EventLinkReceiver receiver = EventLink.getEventLinkReceiver(el);
            sb.append(I1).append("<eventLink>\n");
            sb.append(I2).append("<senders>\n");
            if (sender != null) appendEventSender(sb, sender, I3);
            sb.append(I2).append("</senders>\n");
            sb.append(I2).append("<receivers>\n");
            if (receiver != null) appendEventReceiver(sb, receiver, I3);
            sb.append(I2).append("</receivers>\n");
            sb.append(I1).append("</eventLink>\n");

        } else if (link instanceof RequestLinkActivatableFifo rl) {
            sb.append(I1).append("<requestLink>\n");
            sb.append(I2).append("<clients>\n");
            appendRequestClient(sb, rl.getClient(), I3);
            sb.append(I2).append("</clients>\n");
            sb.append(I2).append("<server>\n");
            appendRequestServer(sb, rl.getServer(), I3);
            sb.append(I2).append("</server>\n");
            sb.append(I1).append("</requestLink>\n");

        } else if (link instanceof RequestLinkActivatingActivatableFifo rl) {
            sb.append(I1).append("<requestLink>\n");
            sb.append(I2).append("<clients>\n");
            appendRequestClient(sb, rl.getClient(), I3);
            sb.append(I2).append("</clients>\n");
            sb.append(I2).append("<server>\n");
            appendRequestServer(sb, rl.getServer(), I3);
            sb.append(I2).append("</server>\n");
            sb.append(I1).append("</requestLink>\n");

        } else if (link instanceof RequestLinkActivatingToReferenceOperation rl) {
            sb.append(I1).append("<requestLink>\n");
            sb.append(I2).append("<clients>\n");
            appendRequestClient(sb, rl.getClient(), I3);
            sb.append(I2).append("</clients>\n");
            sb.append(I2).append("<server>\n");
            appendRequestServer(sb, rl.getServer(), I3);
            sb.append(I2).append("</server>\n");
            sb.append(I1).append("</requestLink>\n");
        }
    }

    private void appendDataWriter(StringBuilder sb, EObject writer, String indent) {
        if (writer == null) return;
        String tag = resolveInstanceTag(writer);
        String instanceName = resolveInstanceName(writer);
        String opName = writer instanceof OperationInstance oi ? oi.getName() : null;
        sb.append(indent).append("<").append(tag).append(" instanceName=\"").append(x(instanceName)).append("\"");
        if (opName != null) sb.append(" operationName=\"").append(x(opName)).append("\"");
        sb.append("/>\n");
    }

    private void appendDataReader(StringBuilder sb, EObject reader, String indent) {
        if (reader == null) return;
        String tag = resolveInstanceTag(reader);
        String instanceName = resolveInstanceName(reader);
        String opName = reader instanceof OperationInstance oi ? oi.getName() : null;
        sb.append(indent).append("<").append(tag).append(" instanceName=\"").append(x(instanceName)).append("\"");
        if (opName != null) sb.append(" operationName=\"").append(x(opName)).append("\"");
        sb.append("/>\n");
    }

    private void appendEventSender(StringBuilder sb, EventLinkSender sender, String indent) {
        String tag = resolveInstanceTag(sender);
        String instanceName = resolveInstanceName(sender);
        sb.append(indent).append("<").append(tag).append(" instanceName=\"").append(x(instanceName)).append("\"")
          .append(" operationName=\"").append(x(sender.getName())).append("\"/>\n");
    }

    private void appendEventReceiver(StringBuilder sb, EventLinkReceiver receiver, String indent) {
        String tag = resolveInstanceTag(receiver);
        String instanceName = resolveInstanceName(receiver);
        sb.append(indent).append("<").append(tag).append(" instanceName=\"").append(x(instanceName)).append("\"")
          .append(" operationName=\"").append(x(receiver.getName())).append("\"/>\n");
    }

    private void appendRequestClient(StringBuilder sb, RequestLinkClient client, String indent) {
        if (client == null) return;
        String tag = resolveInstanceTag(client);
        String instanceName = resolveInstanceName(client);
        sb.append(indent).append("<").append(tag).append(" instanceName=\"").append(x(instanceName)).append("\"")
          .append(" operationName=\"").append(x(client.getName())).append("\"/>\n");
    }

    private void appendRequestServer(StringBuilder sb, RequestLinkServer server, String indent) {
        if (server == null) return;
        String tag = resolveInstanceTag(server);
        String instanceName = resolveInstanceName(server);
        sb.append(indent).append("<").append(tag).append(" instanceName=\"").append(x(instanceName)).append("\"")
          .append(" operationName=\"").append(x(server.getName())).append("\"/>\n");
    }

    /** Returns XML element tag name based on OperationInstance type */
    private String resolveInstanceTag(EObject elem) {
        if (elem instanceof ServRefOfLinkedComponentDefinition s) {
            return s.eClass().getName().contains("Reference") ? "reference" : "service";
        }
        return "moduleInstance";
    }

    /** Resolves the parent Instance name for an OperationInstance */
    private String resolveInstanceName(EObject oi) {
        EObject parent = oi.eContainer();
        if (parent instanceof Instance inst) return inst.getName() != null ? inst.getName() : "";
        if (parent instanceof ServRefOfLinkedComponentDefinition s) return s.getName() != null ? s.getName() : "";
        if (oi instanceof OperationInstance o) return o.getName() != null ? o.getName() : "";
        return "";
    }

    // =========================================================================
    // Step5: Integration
    // =========================================================================

    private void addIntegrationFiles(ZipOutputStream zos, Steps steps) throws IOException {
        Step5 s5 = steps.getStep5();
        if (s5 == null) return;

        LogicalSystem ls = s5.getLogicalSystem();
        if (ls != null)
            addZipEntry(zos, "5-Integration/" + ls.getId() + ".logical-system.xml", generateLogicalSystemXml(ls));

        Deployment dep = s5.getDeployment();
        if (dep != null)
            addZipEntry(zos, "5-Integration/" + dep.getName() + ".deployment.xml", generateDeploymentXml(dep));

        FinalAssembly fa = s5.getFinalAssembly();
        if (fa != null && fa.getFinalAssembly() != null) {
            Composite implComposite = fa.getFinalAssembly();
            addZipEntry(zos, "5-Integration/" + implComposite.getName() + ".impl.composite",
                    generateCompositeXml(implComposite, true));
        }
    }

    private String generateLogicalSystemXml(LogicalSystem ls) {
        StringBuilder sb = new StringBuilder(XML_HEADER);
        sb.append("<ecoa:logicalSystem xmlns:ecoa=\"http://www.ecoa.technology/logicalsystem-2.0\" id=\"")
          .append(x(ls.getId())).append("\">\n");

        for (LogicalComputingPlatform lcp : ls.getLogicalComputingPlatforms()) {
            sb.append(I1).append("<logicalComputingPlatform id=\"").append(x(lcp.getId())).append("\"");
            if (lcp.isSetELIPlatformId()) sb.append(" ELIPlatformId=\"").append(lcp.getELIPlatformId()).append("\"");
            sb.append(">\n");

            for (LogicalComputingNode lcn : lcp.getLogicalComputingNodes()) {
                sb.append(I2).append("<logicalComputingNode id=\"").append(x(lcn.getId())).append("\">\n");
                if (lcn.getEndianessType() != null)
                    sb.append(I3).append("<endianess type=\"").append(lcn.getEndianessType().getLiteral()).append("\"/>\n");
                for (LogicalProcessor lp : lcn.getLogicalProcessors()) {
                    sb.append(I3).append("<logicalProcessors");
                    if (lp.getNumber() != null) sb.append(" number=\"").append(lp.getNumber()).append("\"");
                    if (lp.getType() != null && !lp.getType().isEmpty()) sb.append(" type=\"").append(x(lp.getType())).append("\"");
                    sb.append(" nanoSeconds=\"").append(lp.getStepDurationNanoSeconds()).append("\"");
                    sb.append("/>\n");
                }
                if (lcn.isSetOsName())
                    sb.append(I3).append("<os name=\"").append(lcn.getOsName().getLiteral()).append("\"/>\n");
                if (lcn.getAvailableMemoryGigaBytes() != null)
                    sb.append(I3).append("<availableMemory gigaBytes=\"").append(lcn.getAvailableMemoryGigaBytes()).append("\"/>\n");
                if (lcn.getModuleSwitchTimeMicroSeconds() != null)
                    sb.append(I3).append("<moduleSwitchTime microSeconds=\"").append(lcn.getModuleSwitchTimeMicroSeconds()).append("\"/>\n");
                sb.append(I2).append("</logicalComputingNode>\n");
            }
            sb.append(I1).append("</logicalComputingPlatform>\n");
        }
        sb.append("</ecoa:logicalSystem>\n");
        return sb.toString();
    }

    private String generateDeploymentXml(Deployment dep) {
        StringBuilder sb = new StringBuilder(XML_HEADER);
        sb.append("<deployment xmlns=\"http://www.ecoa.technology/deployment-2.0\"");
        if (dep.getFinalAssembly() != null) sb.append(" finalAssembly=\"").append(x(dep.getFinalAssembly().getName())).append("\"");
        if (dep.getLogicalSystem() != null) sb.append(" logicalSystem=\"").append(x(dep.getLogicalSystem().getId())).append("\"");
        sb.append(">\n");

        for (ProtectionDomain pd : dep.getProtectionDomains()) {
            sb.append(I1).append("<protectionDomain name=\"").append(x(pd.getName())).append("\">\n");
            LogicalComputingNode executeOnNode = pd.getExecuteOnComputingNode();
            edtlogical.LogicalComputingPlatform executeOnPlatform = pd.getExecuteOnComputingPlatform();
            sb.append(I2).append("<executeOn");
            if (executeOnNode != null)
                sb.append(" computingNode=\"").append(x(executeOnNode.getId())).append("\"");
            if (executeOnPlatform != null)
                sb.append(" computingPlatform=\"").append(x(executeOnPlatform.getId())).append("\"");
            sb.append("/>\n");
            for (DeployedModuleInstance dmi : pd.getDeployedModuleInstances()) {
                sb.append(I2).append("<deployedModuleInstance");
                if (dmi.getComponent() != null) sb.append(" componentName=\"").append(x(dmi.getComponent().getName())).append("\"");
                if (dmi.getModuleInstance() != null) sb.append(" moduleInstanceName=\"").append(x(dmi.getModuleInstance().getName())).append("\"");
                if (dmi.getModulePriority() != null) sb.append(" modulePriority=\"").append(dmi.getModulePriority()).append("\"");
                sb.append("/>\n");
            }
            for (DeployedTriggerInstance dti : pd.getDeployedTriggerInstances()) {
                sb.append(I2).append("<deployedTriggerInstance");
                if (dti.getComponent() != null) sb.append(" componentName=\"").append(x(dti.getComponent().getName())).append("\"");
                if (dti.getTriggerInstance() != null) sb.append(" triggerInstanceName=\"").append(x(dti.getTriggerInstance().getName())).append("\"");
                if (dti.getTriggerPriority() != null) sb.append(" triggerPriority=\"").append(dti.getTriggerPriority()).append("\"");
                sb.append("/>\n");
            }
            sb.append(I1).append("</protectionDomain>\n");
        }

        for (PlatformConfiguration pc : dep.getPlatformConfigurations()) {
            sb.append(I1).append("<platformConfiguration");
            if (pc.getComputingPlatform() != null) sb.append(" computingPlatform=\"").append(x(pc.getComputingPlatform().getId())).append("\"");
            if (pc.getFaultHandlerNotificationMaxNumber() != null) sb.append(" faultHandlerNotificationMaxNumber=\"").append(pc.getFaultHandlerNotificationMaxNumber()).append("\"");
            sb.append(">\n");
            for (ComputingNodeConfiguration cnc : pc.getComputingNodeConfigurations()) {
                sb.append(I2).append("<computingNodeConfiguration");
                if (cnc.getComputingNode() != null) sb.append(" computingNode=\"").append(x(cnc.getComputingNode().getId())).append("\"");
                sb.append("/>\n");
            }
            sb.append(I1).append("</platformConfiguration>\n");
        }
        sb.append("</deployment>\n");
        return sb.toString();
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private void addZipEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    /** XML-escape a string. */
    private String x(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private void appendComment(StringBuilder sb, Object comment) {
        if (comment == null) return;
        String cs = comment.toString();
        if (!cs.isEmpty()) sb.append(" comment=\"").append(x(cs)).append("\"");
    }
}
