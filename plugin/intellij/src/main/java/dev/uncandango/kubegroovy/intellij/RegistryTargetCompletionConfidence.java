package dev.uncandango.kubegroovy.intellij;

import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;

public final class RegistryTargetCompletionConfidence extends CompletionConfidence {
	@Override
	public ThreeState shouldSkipAutopopup(Editor editor, PsiElement element, PsiFile file, int offset) {
		return RegistryTargetCompletionContributor.isStartupRegistryTarget(element)
			? ThreeState.NO
			: ThreeState.UNSURE;
	}
}
