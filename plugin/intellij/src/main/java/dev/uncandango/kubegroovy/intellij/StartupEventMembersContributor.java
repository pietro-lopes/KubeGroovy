package dev.uncandango.kubegroovy.intellij;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.PsiManager;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.GrDynamicImplicitProperty;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

public final class StartupEventMembersContributor extends NonCodeMembersContributor {
	private static final String EVENT_GROUP_WRAPPER = "dev.uncandango.kubegroovy.event.EventGroupWrapper";
	private static final String STARTUP_EVENTS = "dev.uncandango.kubegroovy.plugin.builtin.event.StartupEvents";
	private static final String RESOURCE_KEY = "net.minecraft.resources.ResourceKey";
	private static final String REGISTRY = "net.minecraft.core.Registry";
	private static final String EVENT_TYPE = "dev.uncandango.kubegroovy.registry.RegistryKubeEvent";

	@Override
	public void processDynamicElements(PsiType qualifierType, PsiScopeProcessor processor, PsiElement place, ResolveState state) {
		if (!(place instanceof GrReferenceExpression reference)) return;

		var nameHint = processor.getHint(NameHint.KEY);
		var referenceName = reference.getReferenceName();
		if (reference.getQualifierExpression() == null
			&& ("$StartupEvents".equals(referenceName) || "StartupEvents".equals(referenceName))
			&& (nameHint == null || referenceName.equals(nameHint.getName(state)))
			&& accepts(processor, ElementClassHint.DeclarationKind.VARIABLE, ElementClassHint.DeclarationKind.FIELD)) {
			var property = new GrDynamicImplicitProperty(PsiManager.getInstance(place.getProject()), referenceName, EVENT_GROUP_WRAPPER, EVENT_GROUP_WRAPPER);
			ResolveUtil.processElement(processor, property, state);
			return;
		}

		if (!"registry".equals(referenceName)
			|| nameHint != null && !"registry".equals(nameHint.getName(state))
			|| !accepts(processor, ElementClassHint.DeclarationKind.METHOD)) return;
		var qualifier = reference.getQualifierExpression();
		if (qualifier == null || !("$StartupEvents".equals(qualifier.getText()) || "StartupEvents".equals(qualifier.getText()))) return;
		if (!qualifierType.equalsToText(EVENT_GROUP_WRAPPER)) return;

		var project = place.getProject();
		var wrapperClass = JavaPsiFacade.getInstance(project).findClass(EVENT_GROUP_WRAPPER, place.getResolveScope());
		var source = JavaPsiFacade.getInstance(project).findClass(STARTUP_EVENTS, place.getResolveScope());
		var navigationElement = source == null ? null : source.findFieldByName("REGISTRY", true);
		var factory = JavaPsiFacade.getElementFactory(project);

		var resourceKeyMethod = new GrLightMethodBuilder(place.getManager(), "registry");
		resourceKeyMethod.addTypeParameter("T");
		resourceKeyMethod.setReturnType(PsiType.VOID);
		resourceKeyMethod.addParameter(new GrLightParameter("target", factory.createTypeFromText(
			RESOURCE_KEY + "<" + REGISTRY + "<T>>", resourceKeyMethod), resourceKeyMethod));
		resourceKeyMethod.addParameter(new GrLightParameter("handler", factory.createTypeFromText(
			"java.util.function.Consumer<" + EVENT_TYPE + "<T>>", resourceKeyMethod), resourceKeyMethod));
		if (wrapperClass != null) resourceKeyMethod.setContainingClass(wrapperClass);
		if (navigationElement != null) resourceKeyMethod.setNavigationElement(navigationElement);
		processor.execute(resourceKeyMethod, state);

		var stringMethod = new GrLightMethodBuilder(place.getManager(), "registry");
		stringMethod.setReturnType(PsiType.VOID);
		stringMethod.addParameter(new GrLightParameter("target", PsiType.getJavaLangString(place.getManager(), place.getResolveScope()), stringMethod));
		stringMethod.addParameter(new GrLightParameter("handler", factory.createTypeFromText(
			"java.util.function.Consumer<" + EVENT_TYPE + "<?>>", stringMethod), stringMethod));
		if (wrapperClass != null) stringMethod.setContainingClass(wrapperClass);
		if (navigationElement != null) stringMethod.setNavigationElement(navigationElement);
		processor.execute(stringMethod, state);
	}

	private static boolean accepts(PsiScopeProcessor processor, ElementClassHint.DeclarationKind... kinds) {
		var hint = processor.getHint(ElementClassHint.KEY);
		if (hint == null) return true;
		for (var kind : kinds) {
			if (hint.shouldProcess(kind)) return true;
		}
		return false;
	}
}
