/**
 * Copyright (c) 2023 Dassault Aviation
 *
 * SPDX-License-Identifier: MIT
 */
package edtimplementation.impl;

import edtimplementation.*;
import edtinterface.OperationType;
import edtinterface.ServiceDefinition;
import edtproject.ComponentDefinitionReference;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.notify.NotificationChain;
import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.impl.ENotificationImpl;
import org.eclipse.emf.ecore.impl.MinimalEObjectImpl;
import org.eclipse.emf.ecore.util.EObjectContainmentEList;
import org.eclipse.emf.ecore.util.InternalEList;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;


/**
 * <!-- begin-user-doc --> An implementation of the model object
 * '<em><b>Reference Of Linked Component Definition</b></em>'. <!-- end-user-doc
 * -->
 * <p>
 * The following features are implemented:
 * </p>
 * <ul>
 * <li>{@link edtimplementation.impl.ReferenceOfLinkedComponentDefinitionImpl#getOperations
 * <em>Operations</em>}</li>
 * <li>{@link edtimplementation.impl.ReferenceOfLinkedComponentDefinitionImpl#getComponentDefinitionReferenceLink
 * <em>Component Definition Reference Link</em>}</li>
 * <li>{@link edtimplementation.impl.ReferenceOfLinkedComponentDefinitionImpl#getServiceDefinitionLink
 * <em>Service Definition Link</em>}</li>
 * </ul>
 *
 * @generated
 */
public class ReferenceOfLinkedComponentDefinitionImpl extends MinimalEObjectImpl.Container
		implements ReferenceOfLinkedComponentDefinition {
	/**
	 * The cached value of the '{@link #getOperations() <em>Operations</em>}' containment reference list.
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @see #getOperations()
	 * @generated
	 * @ordered
	 */
	protected EList<OperationInstance> operations;

	/**
	 * The cached value of the '{@link #getServiceDefinitionLink() <em>Service
	 * Definition Link</em>}' reference. <!-- begin-user-doc --> <!-- end-user-doc
	 * -->
	 *
	 * @see #getServiceDefinitionLink()
	 * @generated
	 * @ordered
	 */
	protected ServiceDefinition serviceDefinitionLink;

	/**
	 * The default value of the '{@link #getName() <em>Name</em>}' attribute. <!--
	 * begin-user-doc --> <!-- end-user-doc -->
	 *
	 * @see #getName()
	 * @generated
	 * @ordered
	 */
	protected static final String NAME_EDEFAULT = null;

	/**
	 * The cached value of the '{@link #getName() <em>Name</em>}' attribute. <!--
	 * begin-user-doc --> <!-- end-user-doc -->
	 *
	 * @see #getName()
	 * @generated
	 * @ordered
	 */
	protected String name = NAME_EDEFAULT;

	/**
	 * The cached value of the '{@link #getComponentDefinitionReferenceLink() <em>Component Definition Reference Link</em>}' reference.
	 * <!-- begin-user-doc
	 * --> <!-- end-user-doc -->
	 * @see #getComponentDefinitionReferenceLink()
	 * @generated
	 * @ordered
	 */
	protected ComponentDefinitionReference componentDefinitionReferenceLink;

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	protected ReferenceOfLinkedComponentDefinitionImpl() {
		super();
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	protected EClass eStaticClass() {
		return EdtimplementationPackage.Literals.REFERENCE_OF_LINKED_COMPONENT_DEFINITION;
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 *
	 * @generated NOT
	 */
	@SuppressWarnings("serial")
	@Override
	public EList<OperationInstance> getOperations() {
		if (operations == null) {
			operations = new EObjectContainmentEList<OperationInstance>(OperationInstance.class, this,
					EdtimplementationPackage.REFERENCE_OF_LINKED_COMPONENT_DEFINITION__OPERATIONS) {
				@Override
				public void addUnique(OperationInstance object) {
					Iterator<OperationInstance> iterator = operations.iterator();
					while (iterator.hasNext()) {
						OperationInstance operation = iterator.next();
						if (alreadyExistsInReference(object, operation)) {
							iterator.remove();

						}
					}
					super.addUnique(object);
				}

			};

		}
		return operations;

	}

	/**
	 * @param operation
	 * @param object
	 * @return
	 */
	private boolean alreadyExistsInReference(OperationInstance object, OperationInstance operation) {
		return (object instanceof VersionedDataReferenceInstance newVdr
				&& operation instanceof VersionedDataReferenceInstance opVdr
				&& (newVdr.getSDOperationRef() != null && opVdr.getSDOperationRef() != null
				&& Objects.equals(opVdr.getSDOperationRef(), newVdr.getSDOperationRef())))
				|| (object instanceof EventDefinitionInstance newEdi
				&& operation instanceof EventDefinitionInstance opEdi
				&& (newEdi.getSDOperationRef() != null && opEdi.getSDOperationRef() != null
				&& Objects.equals(opEdi.getSDOperationRef(), newEdi.getSDOperationRef())))
				|| (object instanceof RequestReferenceInstance newRri
				&& operation instanceof RequestReferenceInstance opRri
				&& (newRri.getSDOperationRef() != null && opRri.getSDOperationRef() != null
				&& Objects.equals(opRri.getSDOperationRef(), newRri.getSDOperationRef())));
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public ComponentDefinitionReference getComponentDefinitionReferenceLink() {
		if (componentDefinitionReferenceLink != null && componentDefinitionReferenceLink.eIsProxy()) {
			InternalEObject oldComponentDefinitionReferenceLink = (InternalEObject)componentDefinitionReferenceLink;
			componentDefinitionReferenceLink = (ComponentDefinitionReference)eResolveProxy(oldComponentDefinitionReferenceLink);
			if (componentDefinitionReferenceLink != oldComponentDefinitionReferenceLink) {
				if (eNotificationRequired())
					eNotify(new ENotificationImpl(this, Notification.RESOLVE, EdtimplementationPackage.REFERENCE_OF_LINKED_COMPONENT_DEFINITION__COMPONENT_DEFINITION_REFERENCE_LINK, oldComponentDefinitionReferenceLink, componentDefinitionReferenceLink));
			}
		}
		return componentDefinitionReferenceLink;
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	public ComponentDefinitionReference basicGetComponentDefinitionReferenceLink() {
		return componentDefinitionReferenceLink;
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 *
	 * @generated NOT
	 */
	@Override
	public void setComponentDefinitionReferenceLink(ComponentDefinitionReference newComponentDefinitionReferenceLink) {
		ComponentDefinitionReference oldComponentDefinitionReferenceLink = componentDefinitionReferenceLink;
		componentDefinitionReferenceLink = newComponentDefinitionReferenceLink;

		// Guard: if newComponentDefinitionReferenceLink is an unresolved EMF proxy (e.g.
		// during JSON deserialization), calling getSyntax() on it returns null — which
		// would trigger setServiceDefinitionLink(null) → createOpFromServiceDefinition()
		// → getOperations().clear(), erasing all OperationInstances already loaded from
		// JSON.  Skip the serviceDefinitionLink update for proxy values; it is already
		// loaded from JSON as a separate field.
		if (newComponentDefinitionReferenceLink instanceof InternalEObject ieo && ieo.eIsProxy()) {
			// Proxy: leave serviceDefinitionLink as-is (loaded from JSON).
		} else if (newComponentDefinitionReferenceLink != null && newComponentDefinitionReferenceLink.getSyntax() != null) {
			setServiceDefinitionLink(newComponentDefinitionReferenceLink.getSyntax());
		} else {
			setServiceDefinitionLink(null);
		}

		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET,
					EdtimplementationPackage.REFERENCE_OF_LINKED_COMPONENT_DEFINITION__COMPONENT_DEFINITION_REFERENCE_LINK,
					oldComponentDefinitionReferenceLink, componentDefinitionReferenceLink));
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public ServiceDefinition getServiceDefinitionLink() {
		if (serviceDefinitionLink != null && serviceDefinitionLink.eIsProxy()) {
			InternalEObject oldServiceDefinitionLink = (InternalEObject)serviceDefinitionLink;
			serviceDefinitionLink = (ServiceDefinition)eResolveProxy(oldServiceDefinitionLink);
			if (serviceDefinitionLink != oldServiceDefinitionLink) {
				if (eNotificationRequired())
					eNotify(new ENotificationImpl(this, Notification.RESOLVE, EdtimplementationPackage.REFERENCE_OF_LINKED_COMPONENT_DEFINITION__SERVICE_DEFINITION_LINK, oldServiceDefinitionLink, serviceDefinitionLink));
			}
		}
		return serviceDefinitionLink;
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	public ServiceDefinition basicGetServiceDefinitionLink() {
		return serviceDefinitionLink;
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 *
	 * @generated NOT
	 */
	@Override
	public void setServiceDefinitionLink(ServiceDefinition newServiceDefinitionLink) {
		ServiceDefinition oldServiceDefinitionLink = serviceDefinitionLink;
		serviceDefinitionLink = newServiceDefinitionLink;
		createOpFromServiceDefinition();
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET,
					EdtimplementationPackage.REFERENCE_OF_LINKED_COMPONENT_DEFINITION__SERVICE_DEFINITION_LINK,
					oldServiceDefinitionLink, serviceDefinitionLink));
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public String getName() {
		return name;
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setName(String newName) {
		String oldName = name;
		name = newName;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, EdtimplementationPackage.REFERENCE_OF_LINKED_COMPONENT_DEFINITION__NAME, oldName, name));
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc --> Use getOperations() instead of
	 * operations, in case operations is not initialized
	 *
	 * @generated NOT
	 */
	public void createOpFromServiceDefinition() {
		if (serviceDefinitionLink != null) {
			// Guard: if serviceDefinitionLink is still an unresolved proxy (EMF JSON
			// deserialization has not yet resolved cross-references), calling
			// serviceDefinitionLink.getOperations() returns the proxy's own empty list.
			// cleanReferenceOperations([]) would then remove ALL existing operations
			// loaded from JSON, making reference port OperationInstances disappear and
			// breaking DataLink/EventLink endpoints. Return early to preserve them.
			if (serviceDefinitionLink instanceof InternalEObject ieo && ieo.eIsProxy()) {
				return;
			}
			EList<OperationType> edtServiceDefinitionOperations = serviceDefinitionLink.getOperations();
			if (operations != null) {
				// Secondary guard: skip cleanup if any existing operation still carries an
				// unresolved proxy SDOperationRef (mid-deserialization state).
				boolean loadingFromJson = operations.stream().anyMatch(this::hasUnresolvedSDOperationRef);
				if (loadingFromJson) {
					return;
				}
				cleanReferenceOperations(edtServiceDefinitionOperations);
			}
			for (OperationType operationType : edtServiceDefinitionOperations) {
				if (operationType instanceof edtinterface.Data data) {
					VersionedDataReferenceInstance dataInstance = EdtimplementationFactory.eINSTANCE
							.createVersionedDataReferenceInstance();
					dataInstance.setSDOperationRef(data);
					dataInstance.setName(data.getName());
					getOperations().add(dataInstance);
				} else if (operationType instanceof edtinterface.Event event) {
					EventDefinitionInstance eventInstance = EdtimplementationFactory.eINSTANCE
							.createEventDefinitionInstance();
					eventInstance.setSDOperationRef(event);
					eventInstance.setName(event.getName());
					getOperations().add(eventInstance);
				} else if (operationType instanceof edtinterface.RequestResponse request) {
					RequestReferenceInstance requestResponseInstance = EdtimplementationFactory.eINSTANCE
							.createRequestReferenceInstance();
					requestResponseInstance.setSDOperationRef(request);
					requestResponseInstance.setName(request.getName());
					getOperations().add(requestResponseInstance);
				}
			}
		} else {
			getOperations().clear();
		}
	}

	/**
	 * Returns true when the given OperationInstance's SDOperationRef is still an
	 * unresolved EMF proxy. Used to detect mid-JSON-deserialization state.
	 */
	private boolean hasUnresolvedSDOperationRef(OperationInstance op) {
		Object ref = null;
		if (op instanceof VersionedDataReferenceInstance vdr) ref = vdr.getSDOperationRef();
		else if (op instanceof EventDefinitionInstance edi)   ref = edi.getSDOperationRef();
		else if (op instanceof RequestReferenceInstance rri)  ref = rri.getSDOperationRef();
		return ref instanceof InternalEObject ieo && ieo.eIsProxy();
	}

	/**
	 * @param edtServiceDefinitionOperations
	 */
	private void cleanReferenceOperations(EList<OperationType> edtServiceDefinitionOperations) {
		Iterator<OperationInstance> each = operations.iterator();
		while (each.hasNext()) {
			OperationInstance op = each.next();
			// Skip operations with unresolved proxy SDOperationRef to avoid removing
			// valid, JSON-loaded operations before cross-reference resolution completes.
			if (hasUnresolvedSDOperationRef(op)) {
				continue;
			}
			if (alreadyInherited(edtServiceDefinitionOperations, op)) {
				each.remove();
			}
		}
	}

	/**
	 * @param edtServiceDefinitionOperations
	 * @param op
	 * @return
	 */
	private boolean alreadyInherited(EList<OperationType> edtServiceDefinitionOperations, OperationInstance op) {
		return (op instanceof VersionedDataReferenceInstance data && data.getSDOperationRef() != null
				&& !edtServiceDefinitionOperations.contains(data.getSDOperationRef()))

				|| (op instanceof EventDefinitionInstance event && event.getSDOperationRef() != null
				&& !edtServiceDefinitionOperations.contains(event.getSDOperationRef()))

				|| (op instanceof RequestReferenceInstance request && request.getSDOperationRef() != null
				&& !edtServiceDefinitionOperations.contains(request.getSDOperationRef()));
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public NotificationChain eInverseRemove(InternalEObject otherEnd, int featureID, NotificationChain msgs) {
		switch (featureID) {
			case EdtimplementationPackage.REFERENCE_OF_LINKED_COMPONENT_DEFINITION__OPERATIONS:
				return ((InternalEList<?>)getOperations()).basicRemove(otherEnd, msgs);
		}
		return super.eInverseRemove(otherEnd, featureID, msgs);
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public Object eGet(int featureID, boolean resolve, boolean coreType) {
		switch (featureID) {
			case EdtimplementationPackage.REFERENCE_OF_LINKED_COMPONENT_DEFINITION__OPERATIONS:
				return getOperations();
			case EdtimplementationPackage.REFERENCE_OF_LINKED_COMPONENT_DEFINITION__SERVICE_DEFINITION_LINK:
				if (resolve) return getServiceDefinitionLink();
				return basicGetServiceDefinitionLink();
			case EdtimplementationPackage.REFERENCE_OF_LINKED_COMPONENT_DEFINITION__NAME:
				return getName();
			case EdtimplementationPackage.REFERENCE_OF_LINKED_COMPONENT_DEFINITION__COMPONENT_DEFINITION_REFERENCE_LINK:
				if (resolve) return getComponentDefinitionReferenceLink();
				return basicGetComponentDefinitionReferenceLink();
		}
		return super.eGet(featureID, resolve, coreType);
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void eSet(int featureID, Object newValue) {
		switch (featureID) {
			case EdtimplementationPackage.REFERENCE_OF_LINKED_COMPONENT_DEFINITION__OPERATIONS:
				getOperations().clear();
				getOperations().addAll((Collection<? extends OperationInstance>)newValue);
				return;
			case EdtimplementationPackage.REFERENCE_OF_LINKED_COMPONENT_DEFINITION__SERVICE_DEFINITION_LINK:
				setServiceDefinitionLink((ServiceDefinition)newValue);
				return;
			case EdtimplementationPackage.REFERENCE_OF_LINKED_COMPONENT_DEFINITION__NAME:
				setName((String)newValue);
				return;
			case EdtimplementationPackage.REFERENCE_OF_LINKED_COMPONENT_DEFINITION__COMPONENT_DEFINITION_REFERENCE_LINK:
				setComponentDefinitionReferenceLink((ComponentDefinitionReference)newValue);
				return;
		}
		super.eSet(featureID, newValue);
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void eUnset(int featureID) {
		switch (featureID) {
			case EdtimplementationPackage.REFERENCE_OF_LINKED_COMPONENT_DEFINITION__OPERATIONS:
				getOperations().clear();
				return;
			case EdtimplementationPackage.REFERENCE_OF_LINKED_COMPONENT_DEFINITION__SERVICE_DEFINITION_LINK:
				setServiceDefinitionLink((ServiceDefinition)null);
				return;
			case EdtimplementationPackage.REFERENCE_OF_LINKED_COMPONENT_DEFINITION__NAME:
				setName(NAME_EDEFAULT);
				return;
			case EdtimplementationPackage.REFERENCE_OF_LINKED_COMPONENT_DEFINITION__COMPONENT_DEFINITION_REFERENCE_LINK:
				setComponentDefinitionReferenceLink((ComponentDefinitionReference)null);
				return;
		}
		super.eUnset(featureID);
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public boolean eIsSet(int featureID) {
		switch (featureID) {
			case EdtimplementationPackage.REFERENCE_OF_LINKED_COMPONENT_DEFINITION__OPERATIONS:
				return operations != null && !operations.isEmpty();
			case EdtimplementationPackage.REFERENCE_OF_LINKED_COMPONENT_DEFINITION__SERVICE_DEFINITION_LINK:
				return serviceDefinitionLink != null;
			case EdtimplementationPackage.REFERENCE_OF_LINKED_COMPONENT_DEFINITION__NAME:
				return NAME_EDEFAULT == null ? name != null : !NAME_EDEFAULT.equals(name);
			case EdtimplementationPackage.REFERENCE_OF_LINKED_COMPONENT_DEFINITION__COMPONENT_DEFINITION_REFERENCE_LINK:
				return componentDefinitionReferenceLink != null;
		}
		return super.eIsSet(featureID);
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public String toString() {
		if (eIsProxy()) return super.toString();

		StringBuilder result = new StringBuilder(super.toString());
		result.append(" (name: ");
		result.append(name);
		result.append(')');
		return result.toString();
	}

	public EList<OperationLink> findOperationLinks() {
		EList<OperationLink> findOperationLink = new BasicEList<>();
		for (OperationInstance operationInstance : operations) {
			findOperationLink.addAll(operationInstance.findOperationLink());
		}
		return findOperationLink;

	}

} // ReferenceOfLinkedComponentDefinitionImpl
