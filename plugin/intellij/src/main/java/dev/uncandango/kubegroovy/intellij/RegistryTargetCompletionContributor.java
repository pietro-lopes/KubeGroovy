package dev.uncandango.kubegroovy.intellij;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

public final class RegistryTargetCompletionContributor extends CompletionContributor {
	private static final InsertHandler<LookupElement> INSERT_HANDLER = RegistryTargetCompletionContributor::insertRegistryString;

	public RegistryTargetCompletionContributor() {
		extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<>() {
			@Override
			protected void addCompletions(CompletionParameters parameters, ProcessingContext context, com.intellij.codeInsight.completion.CompletionResultSet result) {
				if (!isStartupRegistryTarget(parameters.getPosition())) return;
				var prefixMatcher = result.withPrefixMatcher(stringPrefix(parameters));

				for (var kind : RegistryKind.PROTOTYPE_KINDS) {
					prefixMatcher.addElement(LookupElementBuilder.create(kind.key())
						.withTypeText("String", true)
						.withTailText(" (RegistryKubeEvent<" + kind.shortElementType() + ">)", true)
						.withInsertHandler(INSERT_HANDLER));
				}
			}
		});
	}

	private static String stringPrefix(CompletionParameters parameters) {
		var literal = PsiTreeUtil.getParentOfType(parameters.getPosition(), GrLiteral.class, false);
		if (literal == null) return "";
		var literalRange = literal.getTextRange();
		var literalText = literal.getText();
		if (literalText.length() < 2 || literalText.charAt(0) != '"' && literalText.charAt(0) != '\'') return "";
		var contentEnd = literalText.length() - 1;
		var lowerCaseText = literalText.toLowerCase(java.util.Locale.ROOT);
		var dummyIdentifier = lowerCaseText.indexOf("intellijidearulezzz");
		if (dummyIdentifier >= 0) return literalText.substring(1, dummyIdentifier);
		var prefixEnd = Math.min(parameters.getOffset() - literalRange.getStartOffset(), contentEnd);
		if (prefixEnd <= 1) return "";
		return literalText.substring(1, prefixEnd);
	}

	static boolean isStartupRegistryTarget(PsiElement position) {
		var call = PsiTreeUtil.getParentOfType(position, GrCall.class, false);
		if (!(call instanceof GrMethodCallExpression methodCall)
			|| !(methodCall.getInvokedExpression() instanceof GrReferenceExpression reference)) return false;
		if (!"registry".equals(reference.getReferenceName())) return false;

		var qualifier = reference.getQualifierExpression();
		if (qualifier == null || !("$StartupEvents".equals(qualifier.getText()) || "StartupEvents".equals(qualifier.getText()))) return false;

		GrArgumentList argumentList = call.getArgumentList();
		GrExpression[] arguments = argumentList.getExpressionArguments();
		if (arguments.length == 0) return false;

		var targetRange = arguments[0].getTextRange();
		return targetRange.contains(position.getTextRange()) || targetRange.contains(position.getTextOffset());
	}

	private static void insertRegistryString(InsertionContext context, LookupElement element) {
		var kind = RegistryKind.forKey(element.getLookupString());
		if (kind == null) return;

		var literal = PsiTreeUtil.getParentOfType(context.getFile().findElementAt(context.getStartOffset()), GrLiteral.class, false);
		var replacementRange = literal == null
			? TextRange.create(context.getStartOffset(), context.getTailOffset())
			: literal.getTextRange();
		var replacementStart = replacementRange.getStartOffset();
		var expression = "\"" + kind.key() + "\"";
		context.getDocument().replaceString(replacementStart, replacementRange.getEndOffset(), expression);
		context.getEditor().getCaretModel().moveToOffset(replacementStart + expression.length());
	}
}
