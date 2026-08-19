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

import edttype.EDTDataType;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import technology.ecoa.types._2.*;
import technology.ecoa.types._2.Enum;
import technology.ecoa.types._2.Record;

import java.util.Objects;

/**
 * Converts EDT Library objects to ECOA Types XML format.
 * Based on the original TypesExportConverter from edt-tmp.
 */
public class TypesExportConverter {

    private static final String ECOA_NAMESPACE = "ECOA:";
    private static final typFactory TYPFACTORY = typFactory.eINSTANCE;

    private TypesExportConverter() {
        // Utility class
    }

    /**
     * Convert EDT Library to ECOA Library in DocumentRoot.
     *
     * @param edtLibrary the EDT Library to convert
     * @return ECOA DocumentRoot containing the Library
     */
    public static technology.ecoa.types._2.DocumentRoot recreateLibrary(edttype.Library edtLibrary) {
        var ecoaLibrary = TYPFACTORY.createLibrary();

        // Recreate Used Libraries
        EList<edttype.Library> usedLibraries = edtLibrary.getUsedLibraries();
        usedLibraries.forEach(lib -> ecoaLibrary.getUse().add(recreateUseType(lib)));

        // Recreate DataTypes
        var types = TYPFACTORY.createDataTypes();
        EList<EDTDataType> dataTypes = edtLibrary.getDataTypes();
        for (EDTDataType edtDataType : dataTypes) {
            if (edtDataType instanceof edttype.Array type) {
                types.getArray().add(recreateArray(type));
            } else if (edtDataType instanceof edttype.Constant type) {
                types.getConstant().add(recreateConstant(type));
            } else if (edtDataType instanceof edttype.Enum type) {
                types.getEnum().add(recreateEnum(type));
            } else if (edtDataType instanceof edttype.FixedArray type) {
                types.getFixedArray().add(recreateFixedArray(type));
            } else if (edtDataType instanceof edttype.Record type) {
                types.getRecord().add(recreateRecord(type));
            } else if (edtDataType instanceof edttype.Simple type) {
                types.getSimple().add(recreateSimple(type));
            } else if (edtDataType instanceof edttype.VariantRecord type) {
                types.getVariantRecord().add(recreateVariantRecord(type));
            }
        }
        ecoaLibrary.setTypes(types);

        var documentRoot = TYPFACTORY.createDocumentRoot();
        documentRoot.setLibrary(ecoaLibrary);
        return documentRoot;
    }

    private static UseType recreateUseType(edttype.Library usedLibrary) {
        UseType useType = TYPFACTORY.createUseType();
        useType.setLibrary(usedLibrary.getName().replaceAll("\\.", "__"));
        return useType;
    }

    private static Array recreateArray(edttype.Array edtArray) {
        var ecoaArray = TYPFACTORY.createArray();
        ecoaArray.setName(edtArray.getName());
        if (edtArray.getComment() != null && !edtArray.getComment().isBlank()) {
            ecoaArray.setComment(edtArray.getComment());
        }
        if (edtArray.getMaxNumber() != null && !edtArray.getMaxNumber().isBlank()) {
            ecoaArray.setMaxNumber(edtArray.getMaxNumber());
        }
        EDTDataType itemType = edtArray.getItemType();
        if (itemType != null) {
            ecoaArray.setItemType(recreateTypeAssociation(edtArray, itemType));
        }
        return ecoaArray;
    }

    private static FixedArray recreateFixedArray(edttype.FixedArray edtFixedArray) {
        var ecoaFixedArray = TYPFACTORY.createFixedArray();
        ecoaFixedArray.setName(edtFixedArray.getName());
        if (edtFixedArray.getComment() != null && !edtFixedArray.getComment().isBlank()) {
            ecoaFixedArray.setComment(edtFixedArray.getComment());
        }
        if (edtFixedArray.getMaxNumber() != null && !edtFixedArray.getMaxNumber().isBlank()) {
            ecoaFixedArray.setMaxNumber(edtFixedArray.getMaxNumber());
        }
        EDTDataType itemType = edtFixedArray.getItemType();
        if (itemType != null) {
            ecoaFixedArray.setItemType(recreateTypeAssociation(edtFixedArray, itemType));
        }
        return ecoaFixedArray;
    }

    private static Constant recreateConstant(edttype.Constant edtConstant) {
        var ecoaConstant = TYPFACTORY.createConstant();
        ecoaConstant.setName(edtConstant.getName());
        if (edtConstant.getComment() != null && !edtConstant.getComment().isBlank()) {
            ecoaConstant.setComment(edtConstant.getComment());
        }
        ecoaConstant.setValue(edtConstant.getValue());
        EDTDataType type = edtConstant.getType();
        if (type != null) {
            ecoaConstant.setType(recreateTypeAssociation(edtConstant, type));
        }
        return ecoaConstant;
    }

    private static Simple recreateSimple(edttype.Simple edtSimple) {
        var ecoaSimple = TYPFACTORY.createSimple();
        ecoaSimple.setName(edtSimple.getName());
        if (edtSimple.getComment() != null && !edtSimple.getComment().isBlank()) {
            ecoaSimple.setComment(edtSimple.getComment());
        }
        ecoaSimple.setMaxRange(edtSimple.getMaxRange());
        ecoaSimple.setMinRange(edtSimple.getMinRange());
        ecoaSimple.setPrecision(edtSimple.getPrecision());
        if (edtSimple.getUnit() != null && !edtSimple.getUnit().isBlank()) {
            ecoaSimple.setUnit(edtSimple.getUnit());
        }
        EDTDataType type = edtSimple.getType();
        if (type != null) {
            ecoaSimple.setType(recreateTypeAssociation(edtSimple, type));
        }
        return ecoaSimple;
    }

    private static Enum recreateEnum(edttype.Enum edtEnum) {
        var ecoaEnum = TYPFACTORY.createEnum();
        ecoaEnum.setName(edtEnum.getName());
        if (edtEnum.getComment() != null && !edtEnum.getComment().isBlank()) {
            ecoaEnum.setComment(edtEnum.getComment());
        }
        EList<edttype.EnumValue> edtValues = edtEnum.getValue();
        edtValues.forEach(ev -> ecoaEnum.getValue().add(recreateEnumValue(ev)));
        EDTDataType type = edtEnum.getType();
        if (type != null) {
            ecoaEnum.setType(recreateTypeAssociation(edtEnum, type));
        }
        return ecoaEnum;
    }

    private static EnumValue recreateEnumValue(edttype.EnumValue edtEnumValue) {
        EnumValue ecoaEnumValue = TYPFACTORY.createEnumValue();
        if (edtEnumValue.getComment() != null && !edtEnumValue.getComment().isBlank()) {
            ecoaEnumValue.setComment(edtEnumValue.getComment());
        }
        ecoaEnumValue.setName(edtEnumValue.getName());
        if (edtEnumValue.getValnum() != null && !edtEnumValue.getValnum().isBlank()) {
            ecoaEnumValue.setValnum(edtEnumValue.getValnum());
        }
        return ecoaEnumValue;
    }

    private static Record recreateRecord(edttype.Record edtRecord) {
        var ecoaRecord = TYPFACTORY.createRecord();
        ecoaRecord.setName(edtRecord.getName());
        if (edtRecord.getComment() != null && !edtRecord.getComment().isBlank()) {
            ecoaRecord.setComment(edtRecord.getComment());
        }
        EList<edttype.Field> fields = edtRecord.getField();
        for (edttype.Field field : fields) {
            ecoaRecord.getField().add(recreateField(field));
        }
        return ecoaRecord;
    }

    private static Field recreateField(edttype.Field edtField) {
        var ecoaField = TYPFACTORY.createField();
        if (edtField.getComment() != null && !edtField.getComment().isBlank()) {
            ecoaField.setComment(edtField.getComment());
        }
        ecoaField.setName(edtField.getName());
        EDTDataType type = edtField.getType();
        if (type != null) {
            EObject container = type.eContainer();
            if (EDTDataType.isBasicOrPredefined(type)) {
                ecoaField.setType(ECOA_NAMESPACE + type.getName());
            } else {
                String namespace = ((edttype.Library) container).getName();
                ecoaField.setType(namespace + ":" + type.getName());
            }
        }
        return ecoaField;
    }

    private static VariantRecord recreateVariantRecord(edttype.VariantRecord edtVariantRecord) {
        var ecoaVariantRecord = TYPFACTORY.createVariantRecord();
        ecoaVariantRecord.setName(edtVariantRecord.getName());
        if (edtVariantRecord.getComment() != null && !edtVariantRecord.getComment().isBlank()) {
            ecoaVariantRecord.setComment(edtVariantRecord.getComment());
        }
        if (edtVariantRecord.getSelectName() != null && !edtVariantRecord.getSelectName().isBlank()) {
            ecoaVariantRecord.setSelectName(edtVariantRecord.getSelectName());
        }
        EList<edttype.Field> fields = edtVariantRecord.getField();
        for (edttype.Field field : fields) {
            ecoaVariantRecord.getField().add(recreateField(field));
        }
        EList<edttype.Union> unions = edtVariantRecord.getUnion();
        for (edttype.Union union : unions) {
            ecoaVariantRecord.getUnion().add(recreateUnion(union));
        }
        EDTDataType selectType = edtVariantRecord.getSelectType();
        if (selectType != null) {
            ecoaVariantRecord.setSelectType(recreateTypeAssociation(edtVariantRecord, selectType));
        }
        return ecoaVariantRecord;
    }

    private static Union recreateUnion(edttype.Union edtUnion) {
        var ecoaUnion = TYPFACTORY.createUnion();
        if (edtUnion.getComment() != null && !edtUnion.getComment().isBlank()) {
            ecoaUnion.setComment(edtUnion.getComment());
        }
        ecoaUnion.setName(edtUnion.getName());
        if (edtUnion.getWhen() != null && !edtUnion.getWhen().isBlank()) {
            ecoaUnion.setWhen(edtUnion.getWhen());
        }
        EDTDataType type = edtUnion.getType();
        if (type != null) {
            EObject container = type.eContainer();
            if (EDTDataType.isBasicOrPredefined(type)) {
                ecoaUnion.setType(ECOA_NAMESPACE + type.getName());
            } else {
                String namespace = ((edttype.Library) container).getName();
                ecoaUnion.setType(namespace + ":" + type.getName());
            }
        }
        return ecoaUnion;
    }

    /**
     * Convert EDT DataType reference to ECOA type string.
     */
    private static String recreateTypeAssociation(EDTDataType edtType, EDTDataType edtTypeAssociated) {
        if (edtTypeAssociated != null) {
            EObject associatedContainer = edtTypeAssociated.eContainer();

            if (Objects.equals(associatedContainer, edtType.eContainer())) {
                // Same library - no namespace needed
                return edtTypeAssociated.getName();
            } else if (EDTDataType.isBasicOrPredefined(edtTypeAssociated)) {
                return ECOA_NAMESPACE + edtTypeAssociated.getName();
            } else {
                String namespace = ((edttype.Library) associatedContainer).getName();
                return namespace + ":" + edtTypeAssociated.getName();
            }
        }
        return null;
    }

    /**
     * Create type name string for non-type elements (parameters, properties).
     */
    public static String recreateDataTypeNameForNonTypes(EDTDataType type) {
        if (EDTDataType.isBasicOrPredefined(type)) {
            return ECOA_NAMESPACE + type.getName();
        } else {
            String namespace = ((edttype.Library) type.eContainer()).getName();
            return namespace + ":" + type.getName();
        }
    }
}
