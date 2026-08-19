/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package edtdds.util;

import edtdds.DDSBinding;
import edtdds.EdtddsPackage;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.util.Switch;

public class EdtddsSwitch<T> extends Switch<T> {
    protected static EdtddsPackage modelPackage;
    public EdtddsSwitch() { if (modelPackage == null) modelPackage = EdtddsPackage.eINSTANCE; }
    @Override protected boolean isSwitchFor(EPackage p) { return p == modelPackage; }
    @Override protected T doSwitch(int id, EObject obj) {
        switch (id) {
            case EdtddsPackage.DDS_BINDING: { T r = caseDDSBinding((DDSBinding) obj); return r != null ? r : defaultCase(obj); }
            default: return defaultCase(obj);
        }
    }
    public T caseDDSBinding(DDSBinding o) { return null; }
    @Override public T defaultCase(EObject o) { return null; }
}
