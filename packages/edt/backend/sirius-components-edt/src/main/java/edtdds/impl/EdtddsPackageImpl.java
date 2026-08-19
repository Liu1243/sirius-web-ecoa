/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package edtdds.impl;

import edtdds.DDSBinding;
import edtdds.EdtddsFactory;
import edtdds.EdtddsPackage;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.impl.EPackageImpl;
import org.eclipse.emf.ecore.xml.type.XMLTypePackage;

public class EdtddsPackageImpl extends EPackageImpl implements EdtddsPackage {

    private EClass ddsBindingEClass = null;

    private EdtddsPackageImpl() { super(eNS_URI, EdtddsFactory.eINSTANCE); }

    private static boolean isInited = false;

    public static EdtddsPackage init() {
        if (isInited) return (EdtddsPackage) EPackage.Registry.INSTANCE.getEPackage(EdtddsPackage.eNS_URI);

        Object registered = EPackage.Registry.INSTANCE.get(eNS_URI);
        EdtddsPackageImpl the = registered instanceof EdtddsPackageImpl
                ? (EdtddsPackageImpl) registered : new EdtddsPackageImpl();
        isInited = true;

        // Register immediately so initializePackageContents() registry lookups find us.
        EPackage.Registry.INSTANCE.put(EdtddsPackage.eNS_URI, the);

        // DDSBinding only uses XMLTypePackage/EcorePackage; no EDT interdependencies
        // needed here. EDTProjectPackageImpl.init() drives the full cascade and calls
        // our create/init methods afterward (guarded by isCreated/isInitialized).
        XMLTypePackage.eINSTANCE.eClass();

        the.createPackageContents();
        the.initializePackageContents();
        the.freeze();
        return the;
    }

    @Override public EClass getDDSBinding()              { return ddsBindingEClass; }
    @Override public EAttribute getDDSBinding_Name()      { return (EAttribute) ddsBindingEClass.getEStructuralFeatures().get(0); }
    @Override public EAttribute getDDSBinding_DomainId()  { return (EAttribute) ddsBindingEClass.getEStructuralFeatures().get(1); }
    @Override public EAttribute getDDSBinding_TopicName() { return (EAttribute) ddsBindingEClass.getEStructuralFeatures().get(2); }
    @Override public EdtddsFactory getEdtddsFactory()    { return (EdtddsFactory) getEFactoryInstance(); }

    private boolean isCreated = false;
    public void createPackageContents() {
        if (isCreated) return; isCreated = true;
        ddsBindingEClass = createEClass(DDS_BINDING);
        createEAttribute(ddsBindingEClass, DDS_BINDING__NAME);
        createEAttribute(ddsBindingEClass, DDS_BINDING__DOMAIN_ID);
        createEAttribute(ddsBindingEClass, DDS_BINDING__TOPIC_NAME);
    }

    private boolean isInitialized = false;
    public void initializePackageContents() {
        if (isInitialized) return; isInitialized = true;
        setName(eNAME); setNsPrefix(eNS_PREFIX); setNsURI(eNS_URI);
        XMLTypePackage xml = (XMLTypePackage) EPackage.Registry.INSTANCE.getEPackage(XMLTypePackage.eNS_URI);

        initEClass(ddsBindingEClass, DDSBinding.class, "DDSBinding", !IS_ABSTRACT, !IS_INTERFACE, IS_GENERATED_INSTANCE_CLASS);
        initEAttribute(getDDSBinding_Name(),      xml.getString(), "name",      null,  1, 1, DDSBinding.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
        initEAttribute(getDDSBinding_DomainId(),  ecorePackage.getEInt(), "domainId", "0", 1, 1, DDSBinding.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
        initEAttribute(getDDSBinding_TopicName(), xml.getString(), "topicName", null,  0, 1, DDSBinding.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);

        createResource(eNS_URI);
    }

} // EdtddsPackageImpl
