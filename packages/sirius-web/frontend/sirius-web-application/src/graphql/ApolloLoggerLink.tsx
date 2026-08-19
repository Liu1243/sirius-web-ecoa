/*******************************************************************************
 * Copyright (c) 2024, 2025 Obeo.
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
import { ApolloLink, FetchResult, NextLink, Observable, Operation } from '@apollo/client';
import { DefinitionNode, Kind, OperationDefinitionNode } from 'graphql/language';

const LOG_PREFIX = '[ECOA-TRACE]';

const isOperationDefinitionNode = (definitionNode: DefinitionNode): definitionNode is OperationDefinitionNode =>
  definitionNode.kind === Kind.OPERATION_DEFINITION;

export class ApolloLoggerLink extends ApolloLink {
  override request(operation: Operation, forward: NextLink): Observable<FetchResult> | null {
    const node = operation.query.definitions.find((definitionNode) => isOperationDefinitionNode(definitionNode));

    const operationKind: string = isOperationDefinitionNode(node) ? node.operation : 'unknown kind';
    const operationName: string = isOperationDefinitionNode(node) ? node.name?.value : 'unknwown name';
    const forwarded = forward(operation);
    if (!forwarded) {
      console.error(`${LOG_PREFIX} ${operationKind} ${operationName}: no forward link available`);
      return null;
    }

    return new Observable<FetchResult>((observer) => {
      const subscription = forwarded.subscribe({
        next: (fetchResult) => {
          if (fetchResult.errors && fetchResult.errors.length > 0) {
            console.error(`${LOG_PREFIX} ${operationKind} ${operationName}: graphql errors`, {
              variables: operation.variables,
              errors: fetchResult.errors,
              data: fetchResult.data,
            });
          }
          observer.next(fetchResult);
        },
        error: (networkError) => {
          console.error(`${LOG_PREFIX} ${operationKind} ${operationName}: network error`, {
            variables: operation.variables,
            error: networkError,
          });
          observer.error(networkError);
        },
        complete: () => {
          observer.complete();
        },
      });

      return () => subscription.unsubscribe();
    });
  }
}
