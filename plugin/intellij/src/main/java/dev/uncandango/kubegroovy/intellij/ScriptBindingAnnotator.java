package dev.uncandango.kubegroovy.intellij;

import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

public final class ScriptBindingAnnotator implements Annotator {
	static final TextAttributesKey BINDING_ATTRIBUTES = TextAttributesKey.createTextAttributesKey(
		"KUBEGROOVY_SCRIPT_BINDING", DefaultLanguageHighlighterColors.CLASS_NAME);

	@Override
	public void annotate(PsiElement element, AnnotationHolder holder) {
		if (!(element instanceof GrReferenceExpression reference)
			|| reference.getQualifierExpression() != null
			|| !("$StartupEvents".equals(reference.getReferenceName()) || "StartupEvents".equals(reference.getReferenceName()))) return;

		var nameElement = reference.getReferenceNameElement();
		if (nameElement != null) {
			holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
				.range(nameElement.getTextRange())
				.textAttributes(BINDING_ATTRIBUTES)
				.create();
		}
	}
}
