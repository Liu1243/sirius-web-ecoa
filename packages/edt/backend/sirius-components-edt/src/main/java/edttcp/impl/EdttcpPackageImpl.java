/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package edttcp.impl;

import edttcp.EdttcpFactory;
import edttcp.EdttcpPackage;
import edttcp.TCPBinding;
import edttcp.TCPPlatform;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.impl.EPackageImpl;
import org.eclipse.emf.ecore.xml.type.XMLTypePackage;

public class EdttcpPackageImpl extends EPackageImpl implements EdttcpPackage {

    private EClass tcpBindingEClass = null;
    private EClass tcpPlatformEClass = null;

    private EdttcpPackageImpl() {
        super(eNS_URI, EdttcpFactory.eINSTANCE);
    }

    private static boolean isInited = false;

    public static EdttcpPackage init() {
        if (isInited) return (EdttcpPackage) EPackage.Registry.INSTANCE.getEPackage(EdttcpPackage.eNS_URI);

        Object registered = EPackage.Registry.INSTANCE.get(eNS_URI);
        EdttcpPackageImpl theEdttcpPackage = registered instanceof EdttcpPackageImpl
                ? (EdttcpPackageImpl) registered : new EdttcpPackageImpl();

        isInited = true;

        // Register immediately: other packages' initializePackageContents() look us up
        // from the registry before our full cascade completes.
        EPackage.Registry.INSTANCE.put(EdttcpPackage.eNS_URI, theEdttcpPackage);

        // TCPBinding/TCPPlatform reference only XMLTypePackage and EcorePackage, so
        // we can initialize this package's own contents without pulling in the full EDT
        // interdependency group (which would cause circular-init NPE). The EDT group
        // init (EDTProjectPackageImpl.init) calls our create/init methods afterward;
        // those calls are no-ops because isCreated/isInitialized guards block re-entry.
        XMLTypePackage.eINSTANCE.eClass();

        theEdttcpPackage.createPackageContents();
        theEdttcpPackage.initializePackageContents();
        theEdttcpPackage.freeze();
        return theEdttcpPackage;
    }

    @Override public EClass getTCPBinding()  { return tcpBindingEClass; }
    @Override public EAttribute getTCPBinding_Name()     { return (EAttribute) tcpBindingEClass.getEStructuralFeatures().get(0); }
    @Override public EReference getTCPBinding_Platform() { return (EReference) tcpBindingEClass.getEStructuralFeatures().get(1); }

    @Override public EClass getTCPPlatform()  { return tcpPlatformEClass; }
    @Override public EAttribute getTCPPlatform_Name()    { return (EAttribute) tcpPlatformEClass.getEStructuralFeatures().get(0); }
    @Override public EAttribute getTCPPlatform_Address() { return (EAttribute) tcpPlatformEClass.getEStructuralFeatures().get(1); }
    @Override public EAttribute getTCPPlatform_Port()    { return (EAttribute) tcpPlatformEClass.getEStructuralFeatures().get(2); }

    @Override public EdttcpFactory getEdttcpFactory() { return (EdttcpFactory) getEFactoryInstance(); }

    private boolean isCreated = false;

    public void createPackageContents() {
        if (isCreated) return;
        isCreated = true;

        tcpBindingEClass = createEClass(TCP_BINDING);
        createEAttribute(tcpBindingEClass, TCP_BINDING__NAME);
        createEReference(tcpBindingEClass, TCP_BINDING__PLATFORM);

        tcpPlatformEClass = createEClass(TCP_PLATFORM);
        createEAttribute(tcpPlatformEClass, TCP_PLATFORM__NAME);
        createEAttribute(tcpPlatformEClass, TCP_PLATFORM__ADDRESS);
        createEAttribute(tcpPlatformEClass, TCP_PLATFORM__PORT);
    }

    private boolean isInitialized = false;

    public void initializePackageContents() {
        if (isInitialized) return;
        isInitialized = true;

        setName(eNAME);
        setNsPrefix(eNS_PREFIX);
        setNsURI(eNS_URI);

        XMLTypePackage xmlTypePackage = (XMLTypePackage) EPackage.Registry.INSTANCE.getEPackage(XMLTypePackage.eNS_URI);

        initEClass(tcpBindingEClass, TCPBinding.class, "TCPBinding", !IS_ABSTRACT, !IS_INTERFACE, IS_GENERATED_INSTANCE_CLASS);
        initEAttribute(getTCPBinding_Name(), xmlTypePackage.getString(), "name", null, 1, 1, TCPBinding.class,
                !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
        initEReference(getTCPBinding_Platform(), getTCPPlatform(), null, "platform", null, 0, -1, TCPBinding.class,
                !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, IS_COMPOSITE, !IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);

        initEClass(tcpPlatformEClass, TCPPlatform.class, "TCPPlatform", !IS_ABSTRACT, !IS_INTERFACE, IS_GENERATED_INSTANCE_CLASS);
        initEAttribute(getTCPPlatform_Name(),    xmlTypePackage.getString(), "name",    null, 1, 1, TCPPlatform.class,
                !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
        initEAttribute(getTCPPlatform_Address(), xmlTypePackage.getString(), "address", null, 1, 1, TCPPlatform.class,
                !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
        initEAttribute(getTCPPlatform_Port(), ecorePackage.getEInt(), "port", "0", 1, 1, TCPPlatform.class,
                !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);

        createResource(eNS_URI);
    }

} // EdttcpPackageImpl
