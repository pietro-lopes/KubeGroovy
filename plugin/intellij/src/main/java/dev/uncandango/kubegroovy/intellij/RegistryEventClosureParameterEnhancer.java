package dev.uncandango.kubegroovy.intellij;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrVariableEnhancer;

public final class RegistryEventClosureParameterEnhancer extends GrVariableEnhancer {
	private static final String EVENT_TYPE = "dev.uncandango.kubegroovy.registry.RegistryKubeEvent";
	private static final String RESOURCE_KEY = "net.minecraft.resources.ResourceKey";
	private static final String REGISTRY = "net.minecraft.core.Registry";

	@Override
	public PsiType getVariableType(GrVariable variable) {
		if (!(variable instanceof GrParameter parameter)) return null;

		var closure = PsiTreeUtil.getParentOfType(variable, GrClosableBlock.class, false);
		if (closure == null) return null;
		var parameters = closure.getAllParameters();
		if (parameters.length == 0 || parameters[0] != parameter) return null;

		var call = PsiTreeUtil.getParentOfType(closure, GrCall.class, false);
		if (!(call instanceof GrMethodCallExpression methodCall) || !isStartupRegistryCall(methodCall)) return null;

		GrExpression[] arguments = methodCall.getArgumentList().getExpressionArguments();
		if (arguments.length == 0) return null;
		var kind = registryKind(arguments[0]);
		if (kind == null) return null;

		var project = variable.getProject();
		var scope = variable.getResolveScope();
		var eventClass = JavaPsiFacade.getInstance(project).findClass(EVENT_TYPE, scope);
		var elementClass = JavaPsiFacade.getInstance(project).findClass(kind.elementType(), scope);
		if (eventClass == null || elementClass == null) return null;
		return JavaPsiFacade.getElementFactory(project).createType(eventClass, JavaPsiFacade.getElementFactory(project).createType(elementClass));
	}

	private static boolean isStartupRegistryCall(GrMethodCallExpression call) {
		if (!(call.getInvokedExpression() instanceof GrReferenceExpression reference)
			|| !"registry".equals(reference.getReferenceName())) return false;
		var qualifier = reference.getQualifierExpression();
		if (qualifier == null) return false;
		return "$StartupEvents".equals(qualifier.getText()) || "StartupEvents".equals(qualifier.getText());
	}

	private static RegistryKind registryKind(GrExpression target) {
		var targetType = target.getType();
		if (targetType instanceof PsiClassType classType
			&& RESOURCE_KEY.equals(classType.resolve() == null ? null : classType.resolve().getQualifiedName())) {
			PsiType[] arguments = classType.getParameters();
			if (arguments.length == 1 && arguments[0] instanceof PsiClassType valueType && valueType.resolve() != null) {
				var valueClass = valueType.resolve();
				if (REGISTRY.equals(valueClass.getQualifiedName())) {
					PsiType[] registryArguments = valueType.getParameters();
					if (registryArguments.length == 1 && registryArguments[0] instanceof PsiClassType elementType && elementType.resolve() != null) {
						var kind = RegistryKind.forElementType(elementType.resolve().getQualifiedName());
						if (kind != null) return kind;
					}
				} else {
					var kind = RegistryKind.forElementType(valueClass.getQualifiedName());
					if (kind != null) return kind;
				}
			}
		}

		var literal = target instanceof GrLiteral grLiteral
			? grLiteral
			: PsiTreeUtil.findChildOfType(target, GrLiteral.class);
		if (literal == null) return null;
		if (literal.getValue() instanceof String key) return RegistryKind.forKey(key);

		var text = literal.getText();
		if (text.length() < 2) return null;
		var quote = text.charAt(0);
		if ((quote != '\'' && quote != '"') || text.charAt(text.length() - 1) != quote) return null;
		var key = text.substring(1, text.length() - 1);
		return key.indexOf('\\') >= 0 ? null : RegistryKind.forKey(key);
	}
}
