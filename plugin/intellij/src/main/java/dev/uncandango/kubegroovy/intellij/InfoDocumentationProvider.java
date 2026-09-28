package dev.uncandango.kubegroovy.intellij;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralValue;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// Renders `dev.uncandango.kubegroovy.typings.Info` annotation values as IDE
/// documentation (completion popup, quick-doc, hover) for annotated methods,
/// fields, and classes.
///
/// The mod documents its script-facing API through `@Info` (readable at
/// runtime by document generation) instead of `/** */` comments, so without
/// this provider those members show a bare signature and no docs. Returning
/// null whenever the target carries no usable `@Info` leaves every other
/// member to the platform's default providers.
public final class InfoDocumentationProvider extends AbstractDocumentationProvider {
	static final String INFO_ANNOTATION = "dev.uncandango.kubegroovy.typings.Info";

	@Override
	public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
		return infoDoc(element);
	}

	@Override
	public @Nullable String generateHoverDoc(PsiElement element, @Nullable PsiElement originalElement) {
		return infoDoc(element);
	}

	private static @Nullable String infoDoc(@Nullable PsiElement element) {
		if (!(element instanceof PsiModifierListOwner owner)
			|| !(owner instanceof PsiMethod || owner instanceof PsiField || owner instanceof PsiClass)) return null;

		var modifierList = owner.getModifierList();
		if (modifierList == null) return null;
		PsiAnnotation info = modifierList.findAnnotation(INFO_ANNOTATION);
		if (info == null) return null;

		String description = stringAttribute(info, "value");
		List<ParamDoc> params = paramDocs(owner, info);
		if ((description == null || description.isBlank()) && params.isEmpty()) return null;

		var doc = new StringBuilder();
		doc.append(DocumentationMarkup.DEFINITION_START).append(definition(owner)).append(DocumentationMarkup.DEFINITION_END);
		doc.append(DocumentationMarkup.CONTENT_START);
		if (description != null && !description.isBlank()) {
			doc.append("<p>").append(multiline(description)).append("</p>");
		}
		if (!params.isEmpty()) {
			doc.append("<table>");
			for (var param : params) {
				doc.append("<tr><td><code>").append(escape(param.name())).append("</code></td><td>")
					.append(multiline(param.description())).append("</td></tr>");
			}
			doc.append("</table>");
		}
		doc.append(DocumentationMarkup.CONTENT_END);
		return doc.toString();
	}

	/// Builds the `paramDocs` for an `@Info`: entries map onto method
	/// parameters by order (the `@Param` contract), with `name()` overriding
	/// the displayed name; stray entries with an explicit name are appended.
	private static List<ParamDoc> paramDocs(PsiModifierListOwner owner, PsiAnnotation info) {
		var raw = info.findAttributeValue("params");
		if (!(raw instanceof PsiArrayInitializerMemberValue array)) return List.of();

		var entries = new ArrayList<ParamDoc>();
		for (PsiAnnotationMemberValue initializer : array.getInitializers()) {
			if (!(initializer instanceof PsiAnnotation entry)
				|| !"dev.uncandango.kubegroovy.typings.Param".equals(entry.getQualifiedName())) continue;
			String name = stringAttribute(entry, "name");
			String value = stringAttribute(entry, "value");
			if (value == null || value.isBlank()) continue;
			entries.add(new ParamDoc(name == null ? "" : name, value));
		}
		if (entries.isEmpty()) return List.of();

		var docs = new ArrayList<ParamDoc>();
		if (owner instanceof PsiMethod method) {
			var parameters = method.getParameterList().getParameters();
			for (int i = 0; i < parameters.length && i < entries.size(); i++) {
				var explicit = entries.get(i).name();
				docs.add(new ParamDoc(explicit.isBlank() ? parameters[i].getName() : explicit, entries.get(i).description()));
			}
			for (int i = parameters.length; i < entries.size(); i++) {
				if (!entries.get(i).name().isBlank()) docs.add(entries.get(i));
			}
		} else {
			for (var entry : entries) {
				if (!entry.name().isBlank()) docs.add(entry);
			}
		}
		return docs;
	}

	private static @Nullable String stringAttribute(PsiAnnotation annotation, String name) {
		PsiAnnotationMemberValue value = annotation.findAttributeValue(name);
		if (value instanceof PsiLiteralValue literal && literal.getValue() instanceof String text) return text;
		return null;
	}

	private static String definition(PsiModifierListOwner owner) {
		if (owner instanceof PsiMethod method) {
			var signature = new StringBuilder();
			signature.append(escape(method.getName())).append('(');
			var parameters = method.getParameterList().getParameters();
			for (int i = 0; i < parameters.length; i++) {
				if (i > 0) signature.append(", ");
				signature.append(escape(parameters[i].getType().getPresentableText()))
					.append(' ').append(escape(parameters[i].getName()));
			}
			signature.append("): ").append(escape(method.getReturnType() == null ? "void" : method.getReturnType().getPresentableText()));
			var containing = method.getContainingClass();
			if (containing != null && containing.getName() != null) {
				return escape(containing.getName()) + "." + signature;
			}
			return signature.toString();
		}
		if (owner instanceof PsiField field) {
			return escape(field.getType().getPresentableText()) + " " + escape(field.getName());
		}
		var named = (PsiClass) owner;
		String kind = named.isInterface() ? "interface " : named.isEnum() ? "enum " : named.isRecord() ? "record " : named.isAnnotationType() ? "@interface " : "class ";
		return kind + escape(named.getName() == null ? "anonymous" : named.getName());
	}

	private static String multiline(String text) {
		return escape(text).replace("\r\n", "\n").replace('\r', '\n').replace("\n", "<br>");
	}

	private static String escape(String text) {
		String escaped = StringUtil.escapeXmlEntities(text);
		return escaped == null ? "" : escaped;
	}

	private record ParamDoc(String name, String description) {
	}
}
