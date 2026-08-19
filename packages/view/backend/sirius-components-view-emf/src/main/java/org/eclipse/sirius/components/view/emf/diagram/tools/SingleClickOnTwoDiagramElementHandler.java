/*******************************************************************************
 * Copyright (c) 2025 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.components.view.emf.diagram.tools;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.sirius.components.collaborative.diagrams.DiagramContext;
import org.eclipse.sirius.components.collaborative.diagrams.DiagramService;
import org.eclipse.sirius.components.collaborative.diagrams.api.IDiagramQueryService;
import org.eclipse.sirius.components.collaborative.diagrams.api.IDiagramService;
import org.eclipse.sirius.components.collaborative.diagrams.dto.ToolVariable;
import org.eclipse.sirius.components.collaborative.diagrams.services.ISingleClickOnTwoDiagramElementHandler;
import org.eclipse.sirius.components.core.api.Environment;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IObjectSearchService;
import org.eclipse.sirius.components.diagrams.Diagram;
import org.eclipse.sirius.components.diagrams.Edge;
import org.eclipse.sirius.components.diagrams.Node;
import org.eclipse.sirius.components.diagrams.description.EdgeDescription;
import org.eclipse.sirius.components.interpreter.AQLInterpreter;
import org.eclipse.sirius.components.representations.Failure;
import org.eclipse.sirius.components.representations.IStatus;
import org.eclipse.sirius.components.representations.VariableManager;
import org.eclipse.sirius.components.view.View;
import org.eclipse.sirius.components.view.diagram.EdgeTool;
import org.eclipse.sirius.components.view.emf.api.IViewAQLInterpreterFactory;
import org.eclipse.sirius.components.view.emf.diagram.ViewDiagramConversionData;
import org.eclipse.sirius.components.view.emf.diagram.ViewDiagramDescriptionConverter;
import org.eclipse.sirius.components.view.emf.diagram.api.IViewDiagramDescriptionSearchService;
import org.eclipse.sirius.components.view.emf.diagram.api.IViewToolFinder;
import org.eclipse.sirius.components.view.emf.diagram.tools.api.IToolExecutor;
import org.eclipse.sirius.components.view.emf.diagram.tools.api.IToolVariableHandler;
import org.eclipse.sirius.components.view.emf.editingcontext.api.IViewEditingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service to handle diagram connector tool execution.
 *
 * @author mcharfadi
 */
@Service
public class SingleClickOnTwoDiagramElementHandler implements ISingleClickOnTwoDiagramElementHandler {

    private static final Logger LOG = LoggerFactory.getLogger(SingleClickOnTwoDiagramElementHandler.class);

    private final IObjectSearchService objectSearchService;

    private final IToolVariableHandler toolVariableHandler;

    private final IDiagramQueryService diagramQueryService;

    private final IToolExecutor toolExecutor;

    private final IViewDiagramDescriptionSearchService viewDiagramDescriptionSearchService;

    private final IViewAQLInterpreterFactory aqlInterpreterFactory;

    private final IViewToolFinder viewToolFinder;

    public SingleClickOnTwoDiagramElementHandler(IObjectSearchService objectSearchService, IToolVariableHandler toolVariableHandler, IDiagramQueryService diagramQueryService, IToolExecutor toolExecutor, IViewDiagramDescriptionSearchService viewDiagramDescriptionSearchService, IViewAQLInterpreterFactory aqlInterpreterFactory, IViewToolFinder viewToolFinder) {
        this.objectSearchService = Objects.requireNonNull(objectSearchService);
        this.toolVariableHandler = Objects.requireNonNull(toolVariableHandler);
        this.diagramQueryService = Objects.requireNonNull(diagramQueryService);
        this.toolExecutor = Objects.requireNonNull(toolExecutor);
        this.viewDiagramDescriptionSearchService = Objects.requireNonNull(viewDiagramDescriptionSearchService);
        this.aqlInterpreterFactory = Objects.requireNonNull(aqlInterpreterFactory);
        this.viewToolFinder = Objects.requireNonNull(viewToolFinder);
    }

    @Override
    public boolean canHandle(IEditingContext editingContext, Diagram diagram, String toolId, String sourceDiagramElementId, String targetDiagramElementId) {
        Optional<String> optionalDiagramElementDescriptionId = Optional.of(sourceDiagramElementId)
                .filter(elementId -> sourceDiagramElementId.equals(diagram.getId()))
                .map(elementId -> diagram.getDescriptionId())
                .or(() -> this.diagramQueryService.findNodeById(diagram, sourceDiagramElementId).map(Node::getDescriptionId))
                .or(() -> this.diagramQueryService.findEdgeById(diagram, sourceDiagramElementId).map(Edge::getDescriptionId));

        if (optionalDiagramElementDescriptionId.isPresent()) {
            var optionalEdgeTool = this.viewToolFinder.findEdgeTool(editingContext, diagram.getDescriptionId(), optionalDiagramElementDescriptionId.get(), toolId);
            return optionalEdgeTool.isPresent();
        }
        return false;
    }

    @Override
    public IStatus execute(IEditingContext editingContext, Diagram diagram, String toolId, String sourceDiagramElementId, String targetDiagramElementId, List<ToolVariable> variables) {
        IStatus result = new Failure("");
        DiagramContext diagramContext = new DiagramContext(diagram);

        Optional<String> optionalDiagramElementDescriptionId = Optional.of(sourceDiagramElementId)
                .filter(elementId -> sourceDiagramElementId.equals(diagram.getId()))
                .map(elementId -> diagram.getDescriptionId())
                .or(() -> this.diagramQueryService.findNodeById(diagram, sourceDiagramElementId).map(Node::getDescriptionId))
                .or(() -> this.diagramQueryService.findEdgeById(diagram, sourceDiagramElementId).map(Edge::getDescriptionId));

        var sourceDiagramElement = this.diagramQueryService.findDiagramElementById(diagram, sourceDiagramElementId);
        var targetDiagramElement = this.diagramQueryService.findDiagramElementById(diagram, targetDiagramElementId);
        Optional<Object> source = Optional.empty();
        Optional<Object> target = Optional.empty();

        if (optionalDiagramElementDescriptionId.isPresent() && sourceDiagramElement.isPresent() && targetDiagramElement.isPresent()) {
            var optionalEdgeTool = this.viewToolFinder.findEdgeTool(editingContext, diagram.getDescriptionId(), optionalDiagramElementDescriptionId.get(), toolId);
            if (sourceDiagramElement.get() instanceof Node node) {
                source = this.objectSearchService.getObject(editingContext, node.getTargetObjectId());
            }
            if (sourceDiagramElement.get() instanceof Edge edge) {
                source = this.objectSearchService.getObject(editingContext, edge.getTargetObjectId());
            }

            if (targetDiagramElement.get() instanceof Node node) {
                target = this.objectSearchService.getObject(editingContext, node.getTargetObjectId());
            }
            if (targetDiagramElement.get() instanceof Edge edge) {
                target = this.objectSearchService.getObject(editingContext, edge.getTargetObjectId());
            }

            if (source.isPresent() && target.isPresent() && optionalEdgeTool.isPresent()) {
                VariableManager variableManager = new VariableManager();
                variableManager.put(DiagramContext.DIAGRAM_CONTEXT, diagramContext);
                variableManager.put(IEditingContext.EDITING_CONTEXT, editingContext);
                variableManager.put(Environment.ENVIRONMENT, new Environment(Environment.SIRIUS_COMPONENTS));
                variableManager.put(IDiagramService.DIAGRAM_SERVICES, new DiagramService(diagramContext));
                variableManager.put(EdgeDescription.SEMANTIC_EDGE_SOURCE, source.get());
                variableManager.put(EdgeDescription.SEMANTIC_EDGE_TARGET, target.get());
                variableManager.put(EdgeDescription.EDGE_SOURCE, sourceDiagramElement.get());
                variableManager.put(EdgeDescription.EDGE_TARGET, targetDiagramElement.get());

                this.toolVariableHandler.addToolVariablesInVariableManager(editingContext, variableManager, variables);

                result = this.executeTool(editingContext, diagram.getDescriptionId(), variableManager, optionalEdgeTool.get());

                if (result instanceof Failure failure) {
                    LOG.warn(
                            "Edge tool execution returned Failure: toolId={} toolName='{}' diagramDescriptionId={} sourceDiagramElementId={} targetDiagramElementId={} sourceSemanticType={} targetSemanticType={} messages={}",
                            toolId,
                            optionalEdgeTool.get().getName(),
                            diagram.getDescriptionId(),
                            sourceDiagramElementId,
                            targetDiagramElementId,
                            safeSemanticType(source.get()),
                            safeSemanticType(target.get()),
                            failure.getMessages()
                    );
                }
            } else if (LOG.isInfoEnabled()) {
                LOG.info(
                        "Edge tool execution preconditions not met: toolId={} diagramDescriptionId={} sourceDiagramElementId={} targetDiagramElementId={} sourceSemanticPresent={} targetSemanticPresent={} edgeToolPresent={}",
                        toolId,
                        diagram.getDescriptionId(),
                        sourceDiagramElementId,
                        targetDiagramElementId,
                        source.isPresent(),
                        target.isPresent(),
                        optionalEdgeTool.isPresent()
                );
            }
        } else if (LOG.isInfoEnabled()) {
            LOG.info(
                    "Edge tool execution could not start (missing context): toolId={} diagramDescriptionId={} sourceDiagramElementId={} targetDiagramElementId={} descriptionIdPresent={} sourceViewPresent={} targetViewPresent={}",
                    toolId,
                    diagram.getDescriptionId(),
                    sourceDiagramElementId,
                    targetDiagramElementId,
                    optionalDiagramElementDescriptionId.isPresent(),
                    sourceDiagramElement.isPresent(),
                    targetDiagramElement.isPresent()
            );
        }
        return result;
    }

    private IStatus executeTool(IEditingContext editingContext, String diagramDescriptionId, VariableManager variableManager, EdgeTool edgeTool) {
        var optionalViewDiagramDescription = this.viewDiagramDescriptionSearchService.findById(editingContext, diagramDescriptionId);
        if (optionalViewDiagramDescription.isPresent() && optionalViewDiagramDescription.get().eContainer() instanceof View view) {
            AQLInterpreter interpreter = this.aqlInterpreterFactory.createInterpreter(editingContext, view);

            VariableManager childVariableManager = variableManager.createChild();

            var optionalViewConversionData = Optional.of(editingContext)
                    .filter(IViewEditingContext.class::isInstance)
                    .map(IViewEditingContext.class::cast)
                    .map(IViewEditingContext::getViewConversionData);

            optionalViewConversionData.map(viewConversionData -> viewConversionData.get(diagramDescriptionId))
                    .filter(Objects::nonNull)
                    .filter(ViewDiagramConversionData.class::isInstance)
                    .map(ViewDiagramConversionData.class::cast)
                    .ifPresent(viewDiagramConversionData -> childVariableManager.put(ViewDiagramDescriptionConverter.CONVERTED_NODES_VARIABLE, viewDiagramConversionData.convertedNodes()));

            return this.toolExecutor.executeTool(edgeTool, interpreter, childVariableManager);
        }
        return new Failure("");
    }

    private static String safeSemanticType(Object semantic) {
        try {
            if (semantic == null) {
                return "null";
            }
            // Most semantic objects are EMF EObjects.
            if (semantic instanceof org.eclipse.emf.ecore.EObject eObject && eObject.eClass() != null) {
                return eObject.eClass().getName();
            }
            return semantic.getClass().getName();
        } catch (RuntimeException e) {
            return semantic == null ? "null" : semantic.getClass().getName();
        }
    }
}
