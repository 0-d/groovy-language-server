////////////////////////////////////////////////////////////////////////////////
// Copyright 2019 Prominic.NET, Inc.
// 
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// 
// http://www.apache.org/licenses/LICENSE-2.0 
// 
// Unless required by applicable law or agreed to in writing, software 
// distributed under the License is distributed on an "AS IS" BASIS, 
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and 
// limitations under the License
// 
// Author: Prominic.NET, Inc.
// No warranty of merchantability or fitness of any kind. 
// Use this software at your own risk.
////////////////////////////////////////////////////////////////////////////////
package net.prominic.groovyls.providers;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.eclipse.lsp4j.CompletionContext;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import net.prominic.groovyls.compiler.ast.ASTNodeVisitor;
import net.prominic.groovyls.compiler.util.GroovyASTUtils;
import net.prominic.groovyls.util.GroovyLanguageServerUtils;

public class CompletionProvider {
	private ASTNodeVisitor ast;

	public CompletionProvider(ASTNodeVisitor ast) {
		this.ast = ast;
	}

	public CompletableFuture<Either<List<CompletionItem>, CompletionList>> provideCompletion(
			TextDocumentIdentifier textDocument, Position position, CompletionContext context) {
		URI uri = URI.create(textDocument.getUri());
		ASTNode offsetNode = ast.getNodeAtLineAndColumn(uri, position.getLine(), position.getCharacter());
		if (offsetNode == null) {
			return CompletableFuture.completedFuture(Either.forLeft(Collections.emptyList()));
		}
		ASTNode parentNode = ast.getParent(offsetNode);

		PropertyExpression propExpr = null;

		if (parentNode instanceof PropertyExpression) {
			propExpr = (PropertyExpression) parentNode;
		}

		List<CompletionItem> items = new ArrayList<>();
		if (propExpr != null) {
			Expression objectExpr = propExpr.getObjectExpression();
			String propertyPrefix = propExpr.getPropertyAsString();

			List<PropertyNode> properties = GroovyASTUtils.getPropertiesForLeftSideOfPropertyExpression(objectExpr,
					ast);
			List<CompletionItem> propItems = properties.stream().filter(property -> {
				return property.getName().startsWith(propertyPrefix);
			}).map(property -> {
				CompletionItem item = new CompletionItem();
				item.setLabel(property.getName());
				item.setKind(GroovyLanguageServerUtils.astNodeToCompletionItemKind(property));
				return item;
			}).collect(Collectors.toList());
			items.addAll(propItems);

			List<MethodNode> methods = GroovyASTUtils.getMethodsForLeftSideOfPropertyExpression(objectExpr, ast);
			Set<String> foundMethods = new HashSet<>();
			List<CompletionItem> methodItems = methods.stream().filter(method -> {
				String methodName = method.getName();
				//overloads can cause duplicates
				if (methodName.startsWith(propertyPrefix) && !foundMethods.contains(methodName)) {
					foundMethods.add(methodName);
					return true;
				}
				return false;
			}).map(method -> {
				CompletionItem item = new CompletionItem();
				item.setLabel(method.getName());
				item.setKind(GroovyLanguageServerUtils.astNodeToCompletionItemKind(method));
				return item;
			}).collect(Collectors.toList());
			items.addAll(methodItems);
		}

		return CompletableFuture.completedFuture(Either.forLeft(items));
	}
}