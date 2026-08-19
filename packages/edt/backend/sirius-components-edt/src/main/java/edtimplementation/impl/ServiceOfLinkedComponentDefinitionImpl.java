/**
 * Copyright (c) 2023 Dassault Aviation
 *
 * SPDX-License-Identifier: MIT
 */
package edtimplementation.impl;

import edtimplementation.*;
import edtinterface.OperationType;
import edtinterface.ServiceDefinition;
import edtproject.ComponentDefinitionService;
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
 * <!-- begin-user-doc --> An implementation of the model object '<em><b>Service
 * Of Linked Component Definition</b></em>'. <!-- end-user-doc -->
 * <p>
 * The following features are implemented:
 * </p>
 * <ul>
 *   <li>{@link edtimplementation.impl.ServiceOfLinkedComponentDefinitionImpl#getOperations <em>Operations</em>}</li>
 *   <li>{@link edtimplementation.impl.ServiceOfLinkedComponentDefinitionImpl#getServiceDefinitionLink <em>Service Definition Link</em>}</li>
 *   <li>{@link edtimplementation.impl.ServiceOfLinkedComponentDefinitionImpl#getName <em>Name</em>}</li>
 *   <li>{@link edtimplementation.impl.ServiceOfLinkedComponentDefinitionImpl#getComponentDefinitionServiceLink <em>Component Definition Service Link</em>}</li>
 * </ul>
 *
 * @generated
 */
public class ServiceOfLinkedComponentDefinitionImpl extends MinimalEObjectImpl.Container
		implements ServiceOfLinkedComponentDefinition {
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
	 * The cached value of the '{@link #getComponentDefinitionServiceLink() <em>Component Definition Service Link</em>}' reference.
	 * <!-- begin-user-doc
	 * --> <!-- end-user-doc -->
	 * @see #getComponentDefinitionServiceLink()
	 * @generated
	 * @ordered
	 */
	protected ComponentDefinitionService componentDefinitionServiceLink;

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	protected ServiceOfLinkedComponentDefinitionImpl() {
		super();
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	protected EClass eStaticClass() {
		return EdtimplementationPackage.Literals.SERVICE_OF_LINKED_COMPONENT_DEFINITION;
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
					EdtimplementationPackage.SERVICE_OF_LINKED_COMPONENT_DEFINITION__OPERATIONS) {
				@Override
				public void addUnique(OperationInstance object) {
					Iterator<OperationInstance> iterator = operations.iterator();
					while (iterator.hasNext()) {
						OperationInstance operation = iterator.next();
						if (alreadyExistsInService(object, operation)) {
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
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public ComponentDefinitionService getComponentDefinitionServiceLink() {
		if (componentDefinitionServiceLink != null && componentDefinitionServiceLink.eIsProxy()) {
			InternalEObject oldComponentDefinitionServiceLink = (InternalEObject)componentDefinitionServiceLink;
			componentDefinitionServiceLink = (ComponentDefinitionService)eResolveProxy(oldComponentDefinitionServiceLink);
			if (componentDefinitionServiceLink != oldComponentDefinitionServiceLink) {
				if (eNotificationRequired())
					eNotify(new ENotificationImpl(this, Notification.RESOLVE, EdtimplementationPackage.SERVICE_OF_LINKED_COMPONENT_DEFINITION__COMPONENT_DEFINITION_SERVICE_LINK, oldComponentDefinitionServiceLink, componentDefinitionServiceLink));
			}
		}
		return componentDefinitionServiceLink;
	}

	/**
	 * @param operation
	 * @param object
	 * @return
	 */
	private boolean alreadyExistsInService(OperationInstance object, OperationInstance operation) {
		return (object instanceof VersionedDataServiceInstance newVds
				&& operation instanceof VersionedDataServiceInstance opVds
				&& (newVds.getSDOperationRef() != null && opVds.getSDOperationRef() != null
				&& Objects.equals(opVds.getSDOperationRef(), newVds.getSDOperationRef())))
				|| (object instanceof EventDefinitionInstance newEdi
				&& operation instanceof EventDefinitionInstance opEdi
				&& (newEdi.getSDOperationRef() != null && opEdi.getSDOperationRef() != null
				&& Objects.equals(opEdi.getSDOperationRef(), newEdi.getSDOperationRef())))
				|| (object instanceof RequestServiceInstance newRsi && operation instanceof RequestServiceInstance opRsi
				&& (newRsi.getSDOperationRef() != null && opRsi.getSDOperationRef() != null
				&& Objects.equals(opRsi.getSDOperationRef(), newRsi.getSDOperationRef())));
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	public ComponentDefinitionService basicGetComponentDefinitionServiceLink() {
		return componentDefinitionServiceLink;
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 *
	 * @generated NOT
	 */
	@Override
	public void setComponentDefinitionServiceLink(ComponentDefinitionService newComponentDefinitionServiceLink) {
		ComponentDefinitionService oldComponentDefinitionServiceLink = componentDefinitionServiceLink;
		componentDefinitionServiceLink = newComponentDefinitionServiceLink;
		// Guard: if newComponentDefinitionServiceLink is an unresolved EMF proxy (e.g.
		// during JSON deserialization), calling getSyntax() on it returns null — which
		// would trigger setServiceDefinitionLink(null) → createOpFromServiceDefinition()
		// → getOperations().clear(), erasing all OperationInstances already loaded from
		// JSON.  Skip the serviceDefinitionLink update for proxy values; it is already
		// loaded from JSON as a separate field.
		if (newComponentDefinitionServiceLink instanceof InternalEObject ieo && ieo.eIsProxy()) {
			// Proxy: leave serviceDefinitionLink as-is (loaded from JSON).
		} else if (newComponentDefinitionServiceLink != null && newComponentDefinitionServiceLink.getSyntax() != null) {
			setServiceDefinitionLink(newComponentDefinitionServiceLink.getSyntax());
		} else {
			setServiceDefinitionLink(null);
		}
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET,
					EdtimplementationPackage.SERVICE_OF_LINKED_COMPONENT_DEFINITION__COMPONENT_DEFINITION_SERVICE_LINK,
					oldComponentDefinitionServiceLink, componentDefinitionServiceLink));
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
					eNotify(new ENotificationImpl(this, Notification.RESOLVE, EdtimplementationPackage.SERVICE_OF_LINKED_COMPONENT_DEFINITION__SERVICE_DEFINITION_LINK, oldServiceDefinitionLink, serviceDefinitionLink));
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
					EdtimplementationPackage.SERVICE_OF_LINKED_COMPONENT_DEFINITION__SERVICE_DEFINITION_LINK,
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
			eNotify(new ENotificationImpl(this, Notification.SET, EdtimplementationPackage.SERVICE_OF_LINKED_COMPONENT_DEFINITION__NAME, oldName, name));
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
			// deserialization has not resolved cross-references yet), calling
			// serviceDefinitionLink.getOperations() returns the proxy's own empty list.
			// cleanServiceOperations([]) would then remove ALL existing operations loaded
			// from JSON, stripping the service port OperationInstances and making their
			// DataLink / EventLink endpoints dangling. Return early to preserve operations
			// until proxy resolution completes and setServiceDefinitionLink() is not
			// called again on a live, resolved reference.
			if (serviceDefinitionLink instanceof InternalEObject ieo && ieo.eIsProxy()) {
				return;
			}
			EList<OperationType> edtServiceDefinitionOperations = serviceDefinitionLink.getOperations();
			if (operations != null) {
				// Secondary guard: some existing operations may themselves carry unresolved
				// proxy SDOperationRefs. Skip the cleanup in that case too.
				boolean loadingFromJson = operations.stream().anyMatch(this::hasUnresolvedSDOperationRef);
				if (loadingFromJson) {
					return;
				}
				cleanServiceOperations(edtServiceDefinitionOperations);
			}
			for (OperationType operationType : edtServiceDefinitionOperations) {
				if (operationType instanceof edtinterface.Data data) {
					VersionedDataServiceInstance dataInstance = EdtimplementationFactory.eINSTANCE
							.createVersionedDataServiceInstance();
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
					RequestServiceInstance requestResponseInstance = EdtimplementationFactory.eINSTANCE
							.createRequestServiceInstance();
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
	 * unresolved EMF proxy. Used to detect mid-JSON-deserialization state where
	 * cross-references have not been resolved yet.
	 */
	private boolean hasUnresolvedSDOperationRef(OperationInstance op) {
		Object ref = null;
		if (op instanceof VersionedDataServiceInstance vds) ref = vds.getSDOperationRef();
		else if (op instanceof EventDefinitionInstance edi)    ref = edi.getSDOperationRef();
		else if (op instanceof RequestServiceInstance rsi)     ref = rsi.getSDOperationRef();
		return ref instanceof InternalEObject ieo && ieo.eIsProxy();
	}

	/**
	 * @param edtServiceDefinitionOperations
	 */
	private void cleanServiceOperations(EList<OperationType> edtServiceDefinitionOperations) {
		Iterator<OperationInstance> each = operations.iterator();
		while (each.hasNext()) {
			OperationInstance op = each.next();
			// Skip operations whose SDOperationRef is an unresolved proxy so we do not
			// incorrectly discard valid, JSON-loaded operations before resolution.
			if (hasUnresolvedSDOperationRef(op)) {
				continue;
			}
			if ((op instanceof VersionedDataServiceInstance data && data.getSDOperationRef() != null
					&& !edtServiceDefinitionOperations.contains(data.getSDOperationRef()))

					|| (op instanceof EventDefinitionInstance event && event.getSDOperationRef() != null
					&& !edtServiceDefinitionOperations.contains(event.getSDOperationRef()))

					|| (op instanceof RequestServiceInstance request && request.getSDOperationRef() != null
					&& !edtServiceDefinitionOperations.contains(request.getSDOperationRef()))) {

				each.remove();
			}
		}
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public NotificationChain eInverseRemove(InternalEObject otherEnd, int featureID, NotificationChain msgs) {
		switch (featureID) {
			case EdtimplementationPackage.SERVICE_OF_LINKED_COMPONENT_DEFINITION__OPERATIONS:
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
			case EdtimplementationPackage.SERVICE_OF_LINKED_COMPONENT_DEFINITION__OPERATIONS:
				return getOperations();
			case EdtimplementationPackage.SERVICE_OF_LINKED_COMPONENT_DEFINITION__SERVICE_DEFINITION_LINK:
				if (resolve) return getServiceDefinitionLink();
				return basicGetServiceDefinitionLink();
			case EdtimplementationPackage.SERVICE_OF_LINKED_COMPONENT_DEFINITION__NAME:
				return getName();
			case EdtimplementationPackage.SERVICE_OF_LINKED_COMPONENT_DEFINITION__COMPONENT_DEFINITION_SERVICE_LINK:
				if (resolve) return getComponentDefinitionServiceLink();
				return basicGetComponentDefinitionServiceLink();
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
			case EdtimplementationPackage.SERVICE_OF_LINKED_COMPONENT_DEFINITION__OPERATIONS:
				getOperations().clear();
				getOperations().addAll((Collection<? extends OperationInstance>)newValue);
				return;
			case EdtimplementationPackage.SERVICE_OF_LINKED_COMPONENT_DEFINITION__SERVICE_DEFINITION_LINK:
				setServiceDefinitionLink((ServiceDefinition)newValue);
				return;
			case EdtimplementationPackage.SERVICE_OF_LINKED_COMPONENT_DEFINITION__NAME:
				setName((String)newValue);
				return;
			case EdtimplementationPackage.SERVICE_OF_LINKED_COMPONENT_DEFINITION__COMPONENT_DEFINITION_SERVICE_LINK:
				setComponentDefinitionServiceLink((ComponentDefinitionService)newValue);
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
			case EdtimplementationPackage.SERVICE_OF_LINKED_COMPONENT_DEFINITION__OPERATIONS:
				getOperations().clear();
				return;
			case EdtimplementationPackage.SERVICE_OF_LINKED_COMPONENT_DEFINITION__SERVICE_DEFINITION_LINK:
				setServiceDefinitionLink((ServiceDefinition)null);
				return;
			case EdtimplementationPackage.SERVICE_OF_LINKED_COMPONENT_DEFINITION__NAME:
				setName(NAME_EDEFAULT);
				return;
			case EdtimplementationPackage.SERVICE_OF_LINKED_COMPONENT_DEFINITION__COMPONENT_DEFINITION_SERVICE_LINK:
				setComponentDefinitionServiceLink((ComponentDefinitionService)null);
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
			case EdtimplementationPackage.SERVICE_OF_LINKED_COMPONENT_DEFINITION__OPERATIONS:
				return operations != null && !operations.isEmpty();
			case EdtimplementationPackage.SERVICE_OF_LINKED_COMPONENT_DEFINITION__SERVICE_DEFINITION_LINK:
				return serviceDefinitionLink != null;
			case EdtimplementationPackage.SERVICE_OF_LINKED_COMPONENT_DEFINITION__NAME:
				return NAME_EDEFAULT == null ? name != null : !NAME_EDEFAULT.equals(name);
			case EdtimplementationPackage.SERVICE_OF_LINKED_COMPONENT_DEFINITION__COMPONENT_DEFINITION_SERVICE_LINK:
				return componentDefinitionServiceLink != null;
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

} // ServiceOfLinkedComponentDefinitionImpl
