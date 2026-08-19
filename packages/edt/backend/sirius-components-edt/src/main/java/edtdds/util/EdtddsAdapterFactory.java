/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package edtdds.util;

import edtdds.DDSBinding;
import edtdds.EdtddsPackage;
import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.common.notify.impl.AdapterFactoryImpl;
import org.eclipse.emf.ecore.EObject;

public class EdtddsAdapterFactory extends AdapterFactoryImpl {
    protected static EdtddsPackage modelPackage;
    public EdtddsAdapterFactory() { if (modelPackage == null) modelPackage = EdtddsPackage.eINSTANCE; }
    @Override public boolean isFactoryForType(Object o) {
        if (o == modelPackage) return true;
        if (o instanceof EObject) return ((EObject) o).eClass().getEPackage() == modelPackage;
        return false;
    }
    protected EdtddsSwitch<Adapter> modelSwitch = new EdtddsSwitch<>() {
        @Override public Adapter caseDDSBinding(DDSBinding o) { return createDDSBindingAdapter(); }
        @Override public Adapter defaultCase(EObject o)       { return createEObjectAdapter(); }
    };
    @Override public Adapter createAdapter(Notifier t) { return modelSwitch.doSwitch((EObject) t); }
    public Adapter createDDSBindingAdapter() { return null; }
    public Adapter createEObjectAdapter()    { return null; }
}
