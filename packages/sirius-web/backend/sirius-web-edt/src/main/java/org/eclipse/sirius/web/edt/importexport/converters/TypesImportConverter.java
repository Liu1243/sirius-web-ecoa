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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;

import edtproject.Step0;
import edttype.EDTDataType;
import edttype.EDTTypeFactory;
import technology.ecoa.types._2.Array;
import technology.ecoa.types._2.Constant;
import technology.ecoa.types._2.DataTypes;
import technology.ecoa.types._2.Enum;
import technology.ecoa.types._2.EnumValue;
import technology.ecoa.types._2.Field;
import technology.ecoa.types._2.FixedArray;
import technology.ecoa.types._2.Library;
import technology.ecoa.types._2.Record;
import technology.ecoa.types._2.Simple;
import technology.ecoa.types._2.Union;
import technology.ecoa.types._2.UseType;
import technology.ecoa.types._2.VariantRecord;

/**
 * Converts ECOA Types XML to EDT Library objects.
 * Based on the original TypesImportConverter from com.dassault.edt.import.
 */
public class TypesImportConverter {

    private static final EDTTypeFactory EDTTYPESFACTORY = EDTTypeFactory.eINSTANCE;
    private static final List<String> BASIC_TYPE_NAMES = Arrays.asList(
            "boolean8", "int8", "int16", "int32", "int64", "uint8",
            "uint16", "uint32", "uint64", "char8", "byte", "float32", "double64");

    private TypesImportConverter() {
        // Utility class
    }

    /**
     * Create an EDT Library from an ECOA Library.
     *
     * @param library                   the ECOA Library
     * @param fileName                  the file name
     * @param associatedUsedLibraries   map to store library references for later association
     * @param associatedUsedTypes       map to store type references for later association
     * @param associatedConstantToTypes list to store constant references
     * @return the EDT Library
     */
    public static edttype.Library createEDTLibrary(Library library, String fileName,
            Map<edttype.Library, ArrayList<String>> associatedUsedLibraries,
            Map<EObject, String> associatedUsedTypes,
            ArrayList<EObject> associatedConstantToTypes) {

        var edtLibrary = EDTTYPESFACTORY.createLibrary();

        // Set name from file name
        String libraryName = getObjectName(fileName, ".types.xml");
        edtLibrary.setName(libraryName.replaceAll("__", "."));

        // Store used libraries for later association
        EList<UseType> use = library.getUse();
        associatedUsedLibraries.put(edtLibrary, new ArrayList<>());
        for (UseType useType : use) {
            associatedUsedLibraries.get(edtLibrary).add(useType.getLibrary());
        }

        // Convert types
        DataTypes types = library.getTypes();

        types.getArray().forEach(type -> edtLibrary.getDataTypes()
                .add(createEDTArray(type, associatedUsedTypes, associatedConstantToTypes)));

        types.getConstant().forEach(type -> edtLibrary.getDataTypes()
                .add(createEDTConstant(type, associatedUsedTypes, associatedConstantToTypes)));

        types.getEnum().forEach(type -> edtLibrary.getDataTypes()
                .add(createEDTEnum(type, associatedUsedTypes, associatedConstantToTypes)));

        types.getFixedArray().forEach(type -> edtLibrary.getDataTypes()
                .add(createEDTFixedArray(type, associatedUsedTypes, associatedConstantToTypes)));

        types.getRecord().forEach(type -> edtLibrary.getDataTypes()
                .add(createEDTRecord(type, associatedUsedTypes)));

        types.getSimple().forEach(type -> edtLibrary.getDataTypes()
                .add(createEDTSimple(type, associatedUsedTypes, associatedConstantToTypes)));

        types.getVariantRecord().forEach(type -> edtLibrary.getDataTypes()
                .add(createEDTVariantRecord(type, associatedUsedTypes)));

        return edtLibrary;
    }

    /**
     * Extract object name from file name by removing the extension.
     */
    public static String getObjectName(String fileName, String extension) {
        if (fileName.contains("/")) {
            fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        }
        if (fileName.contains("\\")) {
            fileName = fileName.substring(fileName.lastIndexOf('\\') + 1);
        }
        return fileName.replace(extension, "");
    }

    private static edttype.Array createEDTArray(Array ecoaArray, Map<EObject, String> associatedUsedTypes,
            ArrayList<EObject> associatedConstantToTypes) {
        var edtArray = EDTTYPESFACTORY.createArray();
        edtArray.setName(ecoaArray.getName());
        edtArray.setComment(ecoaArray.getComment());
        edtArray.setMaxNumber(ecoaArray.getMaxNumber());
        if (ecoaArray.getMaxNumber() != null && ecoaArray.getMaxNumber().contains("%")) {
            associatedConstantToTypes.add(edtArray);
        }
        associatedUsedTypes.put(edtArray, ecoaArray.getItemType());
        return edtArray;
    }

    private static edttype.Constant createEDTConstant(Constant ecoaConstant, Map<EObject, String> associatedUsedTypes,
            ArrayList<EObject> associatedConstantToTypes) {
        var edtConstant = EDTTYPESFACTORY.createConstant();
        edtConstant.setName(ecoaConstant.getName());
        edtConstant.setComment(ecoaConstant.getComment());
        edtConstant.setValue(ecoaConstant.getValue());
        if (ecoaConstant.getValue() instanceof String val && val.contains("%")) {
            associatedConstantToTypes.add(edtConstant);
        }
        associatedUsedTypes.put(edtConstant, ecoaConstant.getType());
        return edtConstant;
    }

    private static edttype.Enum createEDTEnum(Enum ecoaEnum, Map<EObject, String> associatedUsedTypes,
            ArrayList<EObject> associatedConstantToTypes) {
        var edtEnum = EDTTYPESFACTORY.createEnum();
        edtEnum.setName(ecoaEnum.getName());
        edtEnum.setComment(ecoaEnum.getComment());
        ecoaEnum.getValue().forEach(ev -> edtEnum.getValue().add(createEDTEnumValue(ev, associatedConstantToTypes)));
        associatedUsedTypes.put(edtEnum, ecoaEnum.getType());
        return edtEnum;
    }

    private static edttype.EnumValue createEDTEnumValue(EnumValue ecoaEnumValue,
            ArrayList<EObject> associatedConstantToTypes) {
        var edtEnumValue = EDTTYPESFACTORY.createEnumValue();
        edtEnumValue.setComment(ecoaEnumValue.getComment());
        edtEnumValue.setName(ecoaEnumValue.getName());
        edtEnumValue.setValnum(ecoaEnumValue.getValnum());
        if (ecoaEnumValue.getValnum() != null && ecoaEnumValue.getValnum().contains("%")) {
            associatedConstantToTypes.add(edtEnumValue);
        }
        return edtEnumValue;
    }

    private static edttype.FixedArray createEDTFixedArray(FixedArray ecoaFixedArray,
            Map<EObject, String> associatedUsedTypes, ArrayList<EObject> associatedConstantToTypes) {
        var edtFixedArray = EDTTYPESFACTORY.createFixedArray();
        edtFixedArray.setName(ecoaFixedArray.getName());
        edtFixedArray.setComment(ecoaFixedArray.getComment());
        edtFixedArray.setMaxNumber(ecoaFixedArray.getMaxNumber());
        if (ecoaFixedArray.getMaxNumber() != null && ecoaFixedArray.getMaxNumber().contains("%")) {
            associatedConstantToTypes.add(edtFixedArray);
        }
        associatedUsedTypes.put(edtFixedArray, ecoaFixedArray.getItemType());
        return edtFixedArray;
    }

    private static edttype.Record createEDTRecord(Record ecoaRecord, Map<EObject, String> associatedUsedTypes) {
        var edtRecord = EDTTYPESFACTORY.createRecord();
        edtRecord.setName(ecoaRecord.getName());
        edtRecord.setComment(ecoaRecord.getComment());
        ecoaRecord.getField().forEach(f -> edtRecord.getField().add(createEDTField(f, associatedUsedTypes)));
        return edtRecord;
    }

    private static edttype.Field createEDTField(Field ecoaField, Map<EObject, String> associatedUsedTypes) {
        var edtField = EDTTYPESFACTORY.createField();
        edtField.setComment(ecoaField.getComment());
        edtField.setName(ecoaField.getName());
        associatedUsedTypes.put(edtField, ecoaField.getType());
        return edtField;
    }

    private static edttype.Simple createEDTSimple(Simple ecoaSimple, Map<EObject, String> associatedUsedTypes,
            ArrayList<EObject> associatedConstantToTypes) {
        var edtSimple = EDTTYPESFACTORY.createSimple();
        edtSimple.setName(ecoaSimple.getName());
        edtSimple.setComment(ecoaSimple.getComment());
        edtSimple.setMaxRange(ecoaSimple.getMaxRange());
        edtSimple.setMinRange(ecoaSimple.getMinRange());
        edtSimple.setPrecision(ecoaSimple.getPrecision());
        edtSimple.setUnit(ecoaSimple.getUnit());

        if ((ecoaSimple.getMaxRange() instanceof String s && s.contains("%"))
                || (ecoaSimple.getMinRange() instanceof String s2 && s2.contains("%"))
                || (ecoaSimple.getPrecision() instanceof String s3 && s3.contains("%"))) {
            associatedConstantToTypes.add(edtSimple);
        }
        associatedUsedTypes.put(edtSimple, ecoaSimple.getType());
        return edtSimple;
    }

    private static edttype.VariantRecord createEDTVariantRecord(VariantRecord ecoaVariantRecord,
            Map<EObject, String> associatedUsedTypes) {
        var edtVariantRecord = EDTTYPESFACTORY.createVariantRecord();
        edtVariantRecord.setName(ecoaVariantRecord.getName());
        edtVariantRecord.setComment(ecoaVariantRecord.getComment());
        edtVariantRecord.setSelectName(ecoaVariantRecord.getSelectName());
        ecoaVariantRecord.getField().forEach(f -> edtVariantRecord.getField().add(createEDTField(f, associatedUsedTypes)));
        ecoaVariantRecord.getUnion().forEach(u -> edtVariantRecord.getUnion().add(createEDTUnion(u, associatedUsedTypes)));
        associatedUsedTypes.put(edtVariantRecord, ecoaVariantRecord.getSelectType());
        return edtVariantRecord;
    }

    private static edttype.Union createEDTUnion(Union ecoaUnion, Map<EObject, String> associatedUsedTypes) {
        var edtUnion = EDTTYPESFACTORY.createUnion();
        edtUnion.setComment(ecoaUnion.getComment());
        edtUnion.setName(ecoaUnion.getName());
        edtUnion.setWhen(ecoaUnion.getWhen());
        associatedUsedTypes.put(edtUnion, ecoaUnion.getType());
        return edtUnion;
    }

    /**
     * Find and associate used libraries after all libraries are created.
     */
    public static void findAndAssociateUsedLibraries(Map<edttype.Library, ArrayList<String>> associatedUsedLibraries,
            Step0 step0) {
        for (var entry : associatedUsedLibraries.entrySet()) {
            var edtLibrary = entry.getKey();
            var libraryNames = entry.getValue();
            for (String libraryName : libraryNames) {
                if ("ECOA".equals(libraryName)) {
                    continue;
                }
                edttype.Library found = step0.findLibrary(libraryName);
                if (found != null) {
                    edtLibrary.getUsedLibraries().add(found);
                }
            }
        }
    }

    /**
     * Find and associate types after all types are created.
     */
    public static void findAndAssociateTypes(Map<EObject, String> associatedUsedTypes, Step0 step0) {
        for (var entry : associatedUsedTypes.entrySet()) {
            String typeName = entry.getValue();
            EObject edtDataType = entry.getKey();

            if (typeName == null) {
                continue;
            }

            EDTDataType foundType = null;

            if (typeName.contains(":")) {
                String[] split = typeName.split(":");
                if (split.length == 2) {
                    foundType = step0.findInAllLibraries(split[0], split[1]);
                }
            } else if (edtDataType instanceof EDTDataType type) {
                edttype.Library edtLibrary = (edttype.Library) edtDataType.eContainer();
                if (type.getName() != null && type.getName().equals(typeName)) {
                    foundType = step0.findBasicType(typeName);
                } else {
                    foundType = findAndAssociateTypesInSameLibraryOrBasicTypes(typeName, edtLibrary.getDataTypes(), step0);
                }
            } else {
                // Field, Union
                edttype.Library edtLibrary = (edttype.Library) edtDataType.eContainer().eContainer();
                foundType = findAndAssociateTypesInSameLibraryOrBasicTypes(typeName, edtLibrary.getDataTypes(), step0);
            }

            if (foundType != null) {
                associateEDTType(edtDataType, foundType);
            }
        }
    }

    private static EDTDataType findAndAssociateTypesInSameLibraryOrBasicTypes(String nameOfAssociatedType,
            EList<EDTDataType> dataTypes, Step0 step0) {
        for (EDTDataType dataTypeToCheck : dataTypes) {
            if (nameOfAssociatedType.equals(dataTypeToCheck.getName())) {
                return dataTypeToCheck;
            }
        }
        return step0.findBasicType(nameOfAssociatedType);
    }

    /**
     * Find EDT data type for non-type elements (e.g., parameters).
     */
    public static EDTDataType findEDTDataTypeForNonTypes(Step0 step0, String typeName) {
        if (typeName.contains(":")) {
            String[] split = typeName.split(":");
            if (split.length == 2) {
                return step0.findInAllLibraries(split[0], split[1]);
            }
            return null;
        } else {
            return step0.findBasicType(typeName);
        }
    }

    private static void associateEDTType(EObject edtDataType, EDTDataType typeToAssociate) {
        if (edtDataType instanceof edttype.Array type) {
            type.setItemType(typeToAssociate);
        } else if (edtDataType instanceof edttype.Constant type) {
            type.setType(typeToAssociate);
        } else if (edtDataType instanceof edttype.Enum type) {
            type.setType(typeToAssociate);
        } else if (edtDataType instanceof edttype.FixedArray type) {
            type.setItemType(typeToAssociate);
        } else if (edtDataType instanceof edttype.Simple type) {
            type.setType(typeToAssociate);
        } else if (edtDataType instanceof edttype.VariantRecord type) {
            type.setSelectType(typeToAssociate);
        } else if (edtDataType instanceof edttype.Field type) {
            type.setType(typeToAssociate);
        } else if (edtDataType instanceof edttype.Union type) {
            type.setType(typeToAssociate);
        }
    }
}
