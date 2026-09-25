package dev.uncandango.kubegroovy.intellij;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

public final class RegistryTargetTypedHandler extends TypedHandlerDelegate {
	@Override
	public Result charTyped(char charTyped, Project project, Editor editor, PsiFile file) {
		if (!Character.isLetterOrDigit(charTyped) && charTyped != '_' && charTyped != '-') return Result.CONTINUE;

		var offset = editor.getCaretModel().getOffset();
		if (offset == 0) return Result.CONTINUE;
		var position = file.findElementAt(offset - 1);
		if (position != null && RegistryTargetCompletionContributor.isStartupRegistryTarget(position)) {
			AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
		}
		return Result.CONTINUE;
	}
}
