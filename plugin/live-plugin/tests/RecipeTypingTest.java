package dev.uncandango.kubegroovy.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.typing.GrTypeCalculator;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.List;

public final class RecipeTypingTest extends LivePluginTestCase {
	private ClassLoader loader;
	private Path catalog;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		loader = loadScript("recipes");
		myFixture.addFileToProject("dev/uncandango/kubegroovy/recipe/RecipesKubeEvent.java", """
			package dev.uncandango.kubegroovy.recipe;
			public class RecipesKubeEvent {
			 public RecipeTypeFunction smelting;
			 public RecipeTypeFunction shaped;
			 public java.util.Map<String,Object> getRecipes() { return null; }
			}
			""");
		myFixture.addFileToProject("dev/uncandango/kubegroovy/recipe/RecipeTypeFunction.java", """
			package dev.uncandango.kubegroovy.recipe;
			public class RecipeTypeFunction { public Object call(Object... args) { return null; } public String getId() { return null; } }
			""");
		myFixture.addFileToProject("dev/uncandango/kubegroovy/recipe/NamespaceFunction.java", """
			package dev.uncandango.kubegroovy.recipe;
			public class NamespaceFunction { public Object call(Object... args) { return null; } public String getMod() { return null; } }
			""");
		myFixture.addFileToProject("dev/uncandango/kubegroovy/recipe/KubeRecipe.java", """
			package dev.uncandango.kubegroovy.recipe;
			public class KubeRecipe {
			 public KubeRecipe id(String id) { return this; }
			 public KubeRecipe group(String group) { return this; }
			 public KubeRecipe differentRecipe() { return null; }
			 public void nativeMethod() {}
			}
			""");
		catalog = Files.createTempFile("kgy-recipes", ".json");
		Files.writeString(catalog, catalogJson("xp"));
		loader.loadClass("RecipeSupport").getMethod("setConfigFile", Project.class, File.class)
			.invoke(null, getProject(), catalog.toFile());
		register(GrReferenceExpression.class, "RecipeReferenceCalculator");
		register(GrMethodCallExpression.class, "RecipeCallCalculator");
		register(GrIndexProperty.class, "RecipeIndexCalculator");
		NonCodeMembersContributor.EP_NAME.getPoint().registerExtension(
			(NonCodeMembersContributor) loader.loadClass("RecipeMembersContributor").getConstructor().newInstance(), getTestRootDisposable());
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private void register(Class expression, String name) throws Exception {
		GrTypeCalculator calculator = (GrTypeCalculator) loader.loadClass(name).getConstructor().newInstance();
		GrTypeCalculator.EP.addExplicitExtension(expression, calculator);
		Disposer.register(getTestRootDisposable(), () -> GrTypeCalculator.EP.removeExplicitExtension(expression, calculator));
	}

	@Override
	protected void tearDown() throws Exception {
		try { if (catalog != null) Files.deleteIfExists(catalog); }
		finally { super.tearDown(); }
	}

	private static String catalogJson(String setter) {
		return """
			{"schemaVersion":1,
			 "lookups":{"smelting":{"kind":"recipe","id":"minecraft:smelting"},
			            "minecraft:smelting":{"kind":"recipe","id":"minecraft:smelting"},
			            "minecraft":{"kind":"namespace","id":"minecraft"},
			            "other":{"kind":"recipe","id":"test:other"}},
			 "namespaces":{"minecraft":{"smelting":"minecraft:smelting"}},
			 "shortcuts":{"smelting":"minecraft:smelting"},
			 "recipes":{"minecraft:smelting":{"id":"minecraft:smelting",
			  "factoryType":"dev.uncandango.kubegroovy.recipe.KubeRecipe",
			  "constructors":[{"name":"call","parameters":[
			   {"name":"result","type":{"kind":"class","name":"java.lang.String","arguments":[]},"description":"Output item","defaultValue":""},
			   {"name":"ingredient","type":{"kind":"class","name":"java.lang.String","arguments":[]},"description":"Ingredient","defaultValue":""}]}],
			  "methods":[{"name":"%s","parameters":[{"name":"experience","type":{"kind":"class","name":"double","arguments":[]},"description":"Experience","defaultValue":"0"}]},
			   {"name":"cookingTime","parameters":[{"name":"ticks","type":{"kind":"class","name":"int","arguments":[]},"description":"Ticks","defaultValue":"200"}]}]},
			  "test:other":{"id":"test:other","factoryType":"missing.AddonRecipe","constructors":[{"name":"call","parameters":[]}],
			   "methods":[{"name":"temperature","parameters":[]}]}}}
			""".formatted(setter);
	}

	private PsiFile script(String body) {
		myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject("kubegroovy/server_scripts/recipes" + System.nanoTime() + ".groovy",
			"import dev.uncandango.kubegroovy.recipe.RecipesKubeEvent\nRecipesKubeEvent event\n" + body).getVirtualFile());
		return myFixture.getFile();
	}

	private GrReferenceExpression reference(PsiFile file, String name) {
		return PsiTreeUtil.findChildrenOfType(file, GrReferenceExpression.class).stream()
			.filter(ref -> name.equals(ref.getReferenceName())).reduce((a, b) -> b).orElseThrow();
	}

	private PsiClass typeClass(PsiType type) {
		assertNotNull("Expected an inferred type", type);
		assertInstanceOf(type, PsiClassType.class);
		PsiClass cls = ((PsiClassType) type).resolve();
		assertNotNull("Expected a resolvable light type: " + type, cls);
		return cls;
	}

	public void testSavedCallableAndFluentResult() {
		PsiFile file = script("""
			def recipes = event.recipes
			def smelting = recipes.smelting
			def recipe = smelting('glass', 'sand')
			def chained = recipe.id('example:glass').group('example').xp(0.35).cookingTime(200)
			chained
			""");
		PsiClass cls = typeClass(reference(file, "chained").getType());
		assertEquals(1, cls.findMethodsByName("xp", false).length);
		assertEquals(0, cls.findMethodsByName("temperature", false).length);
		assertNotNull(reference(file, "xp").resolve());
		assertNotNull(reference(file, "cookingTime").resolve());
		PsiClass callable = typeClass(reference(file, "smelting").getType());
		PsiType declared = JavaPsiFacade.getElementFactory(getProject()).createTypeByFQClassName(
			"dev.uncandango.kubegroovy.recipe.RecipeTypeFunction", file.getResolveScope());
		assertTrue("Callable identity must remain assignable to its runtime class",
			com.intellij.psi.util.TypeConversionUtil.isAssignable(declared, reference(file, "smelting").getType()));
		PsiMethod[] constructors = callable.findMethodsByName("call", false);
		assertEquals(1, constructors.length);
		assertEquals(List.of("result", "ingredient"), Arrays.stream(constructors[0].getParameterList().getParameters()).map(PsiParameter::getName).toList());
	}

	public void testReadableResultNames() throws Exception {
		PsiFile file = script("def recipe = event.smelting('glass','sand')\nrecipe");
		assertEquals("kubegroovy.ide.KgyRecipeMinecraftSmelting", typeClass(reference(file, "recipe").getType()).getQualifiedName());
		var naming = loader.loadClass("RecipeSupport").getMethod("readableId", String.class);
		assertEquals("TheurgyAccumulation", naming.invoke(null, "theurgy:accumulation"));
		assertEquals("TestCampfireCooking", naming.invoke(null, "test:campfire_cooking"));
	}

	public void testNormalizedResultNameCollisionsRemainDistinct() throws Exception {
		Files.writeString(catalog, catalogJson("xp").replace("test:other", "minecraft:Smelting"));
		PsiFile file = script("def first = event.smelting('glass','sand')\ndef second = event.recipes.other()\nfirst\nsecond");
		PsiClass first = typeClass(reference(file, "first").getType());
		PsiClass second = typeClass(reference(file, "second").getType());
		assertTrue(first.getQualifiedName().startsWith("kubegroovy.ide.KgyRecipeMinecraftSmelting_"));
		assertFalse(first.getQualifiedName().equals(second.getQualifiedName()));
		assertEquals(0, second.findMethodsByName("xp", false).length);
	}

	private void addSpecializedFactory() {
		myFixture.addFileToProject("example/ShapedKubeRecipe.java", """
			package example;
			public class ShapedKubeRecipe extends dev.uncandango.kubegroovy.recipe.KubeRecipe {
			 public ShapedKubeRecipe pattern(String pattern) { return this; }
			 public double xp(double experience) { return experience; }
			}
			""");
	}

	public void testSpecializedFactoryUsesNativeClassAndScopedSchemaMethods() throws Exception {
		addSpecializedFactory();
		Files.writeString(catalog, catalogJson("xp").replace(
			"\"factoryType\":\"dev.uncandango.kubegroovy.recipe.KubeRecipe\"", "\"factoryType\":\"example.ShapedKubeRecipe\""));
		PsiFile file = script("""
			def function = event.smelting
			def recipe = function('glass','sand')
			def chained = recipe.id('example:glass').group('example').cookingTime(200).pattern('AA')
			recipe.xp(0.35)
			chained
			""");
		assertEquals("example.ShapedKubeRecipe", typeClass(reference(file, "recipe").getType()).getQualifiedName());
		assertEquals("example.ShapedKubeRecipe", typeClass(reference(file, "chained").getType()).getQualifiedName());
		PsiMethod setter = (PsiMethod) reference(file, "cookingTime").resolve();
		assertNotNull(setter);
		assertEquals("example.ShapedKubeRecipe", setter.getContainingClass().getQualifiedName());
		assertEquals("ticks", setter.getParameterList().getParameters()[0].getName());
		assertEquals("double", ((PsiMethod) reference(file, "xp").resolve()).getReturnType().getCanonicalText());
		assertNotNull(reference(file, "pattern").resolve());
		myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject("ordinary.groovy",
			"example.ShapedKubeRecipe recipe\nrecipe.cookingTime(200)").getVirtualFile());
		assertNull(reference(myFixture.getFile(), "cookingTime").resolve());
	}

	public void testShapedShortcutUsesNativeClassForSharedSchemaAliases() throws Exception {
		addSpecializedFactory();
		Object parser = loader.loadClass("groovy.json.JsonSlurper").getConstructor().newInstance();
		@SuppressWarnings("unchecked")
		var json = (java.util.Map<String, Object>) parser.getClass().getMethod("parseText", String.class).invoke(parser, catalogJson("xp"));
		@SuppressWarnings("unchecked")
		var recipes = (java.util.Map<String, Object>) json.get("recipes");
		@SuppressWarnings("unchecked")
		var original = (java.util.Map<String, Object>) recipes.remove("minecraft:smelting");
		original.put("factoryType", "example.ShapedKubeRecipe");
		for (String id : List.of("minecraft:shaped", "minecraft:crafting_shaped", "kubegroovy:shaped")) {
			var alias = new java.util.LinkedHashMap<>(original);
			alias.put("id", id);
			recipes.put(id, alias);
		}
		json.put("lookups", java.util.Map.of("shaped", java.util.Map.of("kind", "recipe", "id", "kubegroovy:shaped")));
		json.put("namespaces", java.util.Map.of());
		json.put("shortcuts", java.util.Map.of("shaped", "kubegroovy:shaped", "vanillaShaped", "minecraft:crafting_shaped"));
		Files.writeString(catalog, (String) loader.loadClass("groovy.json.JsonOutput").getMethod("toJson", Object.class).invoke(null, json));
		PsiFile file = script("""
			def recipe = event.shaped('glass', 'sand')
			def chained = recipe.id('example:glass').group('example').cookingTime(200)
			def viaMap = event.recipes.shaped('glass', 'sand')
			chained
			viaMap
			""");
		assertEquals("example.ShapedKubeRecipe", typeClass(reference(file, "recipe").getType()).getQualifiedName());
		assertEquals("example.ShapedKubeRecipe", typeClass(reference(file, "chained").getType()).getQualifiedName());
		assertEquals("example.ShapedKubeRecipe", typeClass(reference(file, "viaMap").getType()).getQualifiedName());
		assertNotNull(reference(file, "cookingTime").resolve());
	}

	public void testSharedFactoryKeepsDistinctSchemaIdentity() throws Exception {
		addSpecializedFactory();
		Files.writeString(catalog, catalogJson("xp")
			.replace("dev.uncandango.kubegroovy.recipe.KubeRecipe", "example.ShapedKubeRecipe")
			.replace("missing.AddonRecipe", "example.ShapedKubeRecipe"));
		PsiFile file = script("def recipe = event.recipes.other()\nrecipe");
		PsiClass cls = typeClass(reference(file, "recipe").getType());
		assertEquals("kubegroovy.ide.KgyRecipeTestOther", cls.getQualifiedName());
		assertEquals(1, cls.findMethodsByName("temperature", false).length);
		assertEquals(0, cls.findMethodsByName("cookingTime", false).length);
	}

	public void testUnrelatedFactoryClassKeepsSyntheticResult() throws Exception {
		myFixture.addFileToProject("example/Unrelated.java", "package example; public class Unrelated {}");
		Files.writeString(catalog, catalogJson("xp").replace("missing.AddonRecipe", "example.Unrelated"));
		PsiFile file = script("def recipe = event.recipes.other()\nrecipe");
		assertEquals("kubegroovy.ide.KgyRecipeTestOther", typeClass(reference(file, "recipe").getType()).getQualifiedName());
	}

	public void testLiteralLookupShapesAndExplicitCall() {
		for (String expression : List.of("event.smelting", "event.getRecipes().smelting",
			"event.recipes['minecraft:smelting']", "event.recipes.get('minecraft:smelting')",
			"event.recipes.minecraft('smelting')", "event.recipes.minecraft.call('smelting')", "event.recipes.minecraft.smelting")) {
			PsiFile file = script("def function = " + expression + "\ndef recipe = function.call('glass', 'sand')\nrecipe");
			assertEquals(expression, 1, typeClass(reference(file, "recipe").getType()).findMethodsByName("xp", false).length);
		}
	}

	public void testDirectCallsAndNativeMembers() {
		for (String expression : List.of("event.smelting('glass', 'sand')", "event.recipes.smelting('glass', 'sand')", "event.recipes.minecraft.smelting('glass', 'sand')")) {
			PsiFile file = script("def recipe = " + expression + "\nrecipe.nativeMethod()\nrecipe");
			assertNotNull(reference(file, "nativeMethod").resolve());
			assertEquals(1, typeClass(reference(file, "recipe").getType()).findMethodsByName("xp", false).length);
		}
	}

	public void testDottedNamespaceCompletionAndConstructorHints() {
		PsiFile file = script("""
			def namespace = event.recipes.minecraft
			def recipe = namespace.smelting('glass','sand')
			def chained = recipe.id('example:glass').xp(0.35).cookingTime(200)
			chained
			""");
		PsiMethod method = (PsiMethod) reference(file, "smelting").resolve();
		assertNotNull(method);
		assertEquals(List.of("result", "ingredient"), Arrays.stream(method.getParameterList().getParameters()).map(PsiParameter::getName).toList());
		assertEquals("kubegroovy.ide.KgyRecipeMinecraftSmelting", typeClass(reference(file, "chained").getType()).getQualifiedName());
		script("event.recipes.minecraft.smel<caret>");
		var variants = myFixture.completeBasic();
		assertNotNull(variants);
		assertTrue(Arrays.stream(variants).anyMatch(item -> item.getLookupString().equals("smelting")));
	}

	public void testNamespaceNativeCollisionsAndUnknownMembers() throws Exception {
		Files.writeString(catalog, catalogJson("xp").replace(
			"\"minecraft\":{\"smelting\":\"minecraft:smelting\"}",
			"\"minecraft\":{\"smelting\":\"minecraft:smelting\",\"mod\":\"minecraft:smelting\",\"getMod\":\"minecraft:smelting\"}"));
		PsiFile file = script("def namespace = event.recipes.minecraft\ndef mod = namespace.mod\ndef name = namespace.getMod()\nmod\nname\nnamespace.missing('argument')");
		assertEquals("java.lang.String", reference(file, "mod").getType().getCanonicalText());
		assertEquals("java.lang.String", reference(file, "name").getType().getCanonicalText());
		assertNull(reference(file, "missing").resolve());
		assertNotNull(reference(file, "getMod").resolve());
	}

	public void testDifferentRecipeAndDynamicKeysStayUnspecialized() {
		PsiFile file = script("def result = event.smelting('glass','sand').differentRecipe()\nresult\ndef key = 'smelting'\ndef lookup = event.recipes[key]\nlookup");
		assertEquals("dev.uncandango.kubegroovy.recipe.KubeRecipe", typeClass(reference(file, "result").getType()).getQualifiedName());
		assertFalse(reference(file, "lookup").getType() instanceof PsiClassType type && type.resolve() != null && type.resolve().getQualifiedName().startsWith("kubegroovy.ide."));
	}

	public void testMissingFactoryAndNoRecipeLeakage() {
		PsiFile file = script("def recipe = event.recipes.other()\nrecipe.nativeMethod()\nrecipe");
		PsiClass cls = typeClass(reference(file, "recipe").getType());
		assertEquals(1, cls.findMethodsByName("temperature", false).length);
		assertEquals(0, cls.findMethodsByName("xp", false).length);
		assertNotNull(reference(file, "nativeMethod").resolve());
	}

	public void testCatalogReloadAndMalformedUpdate() throws Exception {
		PsiFile file = script("def recipe = event.smelting('glass','sand')\nrecipe");
		assertEquals(1, typeClass(reference(file, "recipe").getType()).findMethodsByName("xp", false).length);
		PsiClass savedType = typeClass(reference(file, "recipe").getType());
		Files.writeString(catalog, catalogJson("experience"));
		Files.setLastModifiedTime(catalog, FileTime.fromMillis(System.currentTimeMillis() + 2000));
		assertEquals(0, savedType.findMethodsByName("xp", false).length);
		assertEquals(1, savedType.findMethodsByName("experience", false).length);
		// Query the actual provider directly, avoiding IDEA's previously memoized expression type.
		Object config = loader.loadClass("RecipeSupport").getMethod("load", PsiElement.class).invoke(null, file);
		PsiType type = (PsiType) loader.loadClass("RecipeSupport").getMethod("type", String.class, String.class, PsiElement.class, loader.loadClass("RecipeConfig"))
			.invoke(null, "result", "minecraft:smelting", file, config);
		assertEquals(0, typeClass(type).findMethodsByName("xp", false).length);
		assertEquals(1, typeClass(type).findMethodsByName("experience", false).length);
		Files.writeString(catalog, "{ broken");
		Files.setLastModifiedTime(catalog, FileTime.fromMillis(System.currentTimeMillis() + 4000));
		assertSame(config, loader.loadClass("RecipeSupport").getMethod("load", PsiElement.class).invoke(null, file));
	}

	public void testCoercionSignaturesKeepSuggestionMetadataAndDocumentation() throws Exception {
		myFixture.addFileToProject("example/ItemStack.java", "package example; public class ItemStack {}");
		Path overloads = Files.createTempFile("kgy-recipe-overloads", ".json");
		Path suggestions = Files.createTempFile("kgy-recipe-suggestions", ".json");
		try {
			Files.writeString(overloads, "{\"targets\":[\"example.ItemStack\"]}");
			Files.writeString(suggestions, "{\"schemaVersion\":1,\"values\":{\"example.ItemStack\":[\"minecraft:glass\"]},\"delegates\":[],\"genericDelegates\":{}}");
			Files.writeString(catalog, catalogJson("xp").replace("\"name\":\"java.lang.String\"", "\"name\":\"example.ItemStack\""));
			loader.loadClass("RecipeSupport").getMethod("setOverloadFile", Project.class, File.class).invoke(null, getProject(), overloads.toFile());
			PsiFile file = script("def function = event.smelting\ndef recipe = function.call('glass','sand')\nrecipe.xp(0.35)");
			GrMethodCallExpression call = PsiTreeUtil.findChildrenOfType(file, GrMethodCallExpression.class).stream()
				.filter(item -> item.getText().startsWith("function.call")).findFirst().orElseThrow();
			PsiMethod method = call.resolveMethod();
			assertNotNull("String arguments should select a coercion overload", method);
			assertEquals("java.lang.Object", method.getParameterList().getParameters()[0].getType().getCanonicalText());
			ClassLoader suggestionLoader = loadScript("type-suggestions");
			setSuggestionConfig(suggestionLoader, suggestions.toFile());
			Object resolver = suggestionLoader.loadClass("TypeSuggestionPsi").getConstructor().newInstance();
			int offset = file.getText().indexOf("glass") + 1;
			String spec = (String) resolver.getClass().getMethod("specForParam", PsiMethod.class, int.class, PsiSubstitutor.class, PsiElement.class, PsiElement.class, int.class)
				.invoke(resolver, method, 0, null, call.getExpressionArguments()[0], file.findElementAt(offset), offset);
			assertEquals("example.ItemStack", spec);
			assertEquals(List.of("minecraft:glass"), suggestions(suggestionLoader, spec));
			var descriptor = new com.intellij.openapi.extensions.DefaultPluginDescriptor(com.intellij.openapi.extensions.PluginId.getId("LivePlugin"), suggestionLoader);
			com.intellij.openapi.extensions.ExtensionPointName.<com.intellij.codeInsight.completion.CompletionContributorEP>create("com.intellij.completion.contributor").getPoint()
				.registerExtension(new com.intellij.codeInsight.completion.CompletionContributorEP("Groovy", "TypeSuggestionCompletionContributor", descriptor),
					com.intellij.openapi.extensions.LoadingOrder.FIRST, getTestRootDisposable());
			boolean autoComplete = com.intellij.codeInsight.CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION;
			com.intellij.codeInsight.CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;
			Disposer.register(getTestRootDisposable(), () -> com.intellij.codeInsight.CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = autoComplete);
			script("def function = event.smelting\nfunction.call('mine<caret>', 'sand')");
			var items = myFixture.completeBasic();
			assertNotNull(items);
			var choice = Arrays.stream(items).filter(item -> item.getLookupString().equals("minecraft:glass")).findFirst().orElseThrow();
			myFixture.getLookup().setCurrentItem(choice);
			myFixture.finishLookup('\n');
			assertTrue(myFixture.getFile().getText(), myFixture.getFile().getText().matches("(?s).*call\\(['\"]minecraft:glass['\"],.*"));
			var docs = (com.intellij.lang.documentation.AbstractDocumentationProvider) loader.loadClass("RecipeDocumentationProvider").getConstructor().newInstance();
			String doc = docs.generateDoc(method, call);
			assertTrue(doc, doc.contains("minecraft:smelting") && doc.contains("Output item"));
		} finally {
			Files.deleteIfExists(overloads);
			Files.deleteIfExists(suggestions);
		}
	}

	@SuppressWarnings("unchecked")
	public void testRecursiveCollectionAndUnionInputs() throws Exception {
		myFixture.addFileToProject("example/ItemStack.java", "package example; public class ItemStack {}");
		Path overloads = Files.createTempFile("kgy-recipe-collection", ".json");
		try {
			Files.writeString(overloads, "{\"targets\":[\"example.ItemStack\"]}");
			loader.loadClass("RecipeSupport").getMethod("setOverloadFile", Project.class, File.class).invoke(null, getProject(), overloads.toFile());
			PsiFile file = script("event.recipes");
			Object config = loader.loadClass("RecipeSupport").getMethod("load", PsiElement.class).invoke(null, file);
			var inputTypes = loader.loadClass("RecipeSupport").getMethod("inputTypes", java.util.Map.class, PsiElement.class, loader.loadClass("RecipeConfig"));
			var item = java.util.Map.of("kind", "class", "name", "example.ItemStack", "arguments", List.of());
			var string = java.util.Map.of("kind", "class", "name", "java.lang.String", "arguments", List.of());
			var list = java.util.Map.of("kind", "class", "name", "java.util.List", "arguments", List.of(item));
			var map = java.util.Map.of("kind", "class", "name", "java.util.Map", "arguments", List.of(string, list));
			List<PsiType> rewritten = (List<PsiType>) inputTypes.invoke(null, map, file, config);
			assertTrue(rewritten.toString(), rewritten.stream().anyMatch(type -> type.getCanonicalText().equals("java.util.Map<java.lang.String,java.util.List<example.ItemStack>>")));
			assertTrue(rewritten.toString(), rewritten.stream().anyMatch(type -> type.getCanonicalText().equals("java.util.Map<java.lang.String,java.util.List<java.lang.Object>>")));
			var union = java.util.Map.of("kind", "union", "name", "", "arguments", List.of(string, item));
			List<PsiType> variants = (List<PsiType>) inputTypes.invoke(null, union, file, config);
			assertEquals(List.of("java.lang.String", "example.ItemStack", "java.lang.Object"), variants.stream().map(PsiType::getCanonicalText).toList());
		} finally { Files.deleteIfExists(overloads); }
	}

	public void testMapOperationsAndNativeMethodCollisions() throws Exception {
		Files.writeString(catalog, catalogJson("nativeMethod").replace("\"other\":{", "\"size\":{\"kind\":\"recipe\",\"id\":\"minecraft:smelting\"},\"other\":{"));
		PsiFile file = script("def recipes = event.recipes\ndef size = recipes.size()\ndef recipe = event.smelting('glass','sand')\nrecipe.nativeMethod()\nsize");
		GrMethodCallExpression call = PsiTreeUtil.findChildrenOfType(file, GrMethodCallExpression.class).stream()
			.filter(item -> item.getText().startsWith("recipe.nativeMethod")).findFirst().orElseThrow();
		assertEquals("void", call.resolveMethod().getReturnType().getCanonicalText());
		assertEquals("int", reference(file, "size").getType().getCanonicalText());
	}

	public void testRecipeEventClosureInference() throws Exception {
		myFixture.addFileToProject("dev/uncandango/kubegroovy/typing/EventGroupBinding.java",
			"package dev.uncandango.kubegroovy.typing; public class EventGroupBinding {}");
		Path events = Files.createTempFile("kgy-recipe-events", ".json");
		Path bindings = Files.createTempFile("kgy-recipe-bindings", ".json");
		try {
			Files.writeString(events, """
				{"server":{"$ServerEvents":[{"name":"recipes","returns":"void","params":[
				 {"name":"handler","type":"java.util.function.Consumer<dev.uncandango.kubegroovy.recipe.RecipesKubeEvent>"}]}]}}
				""");
			ClassLoader eventLoader = loadScript("events");
			eventLoader.loadClass("EventMethodSupport").getMethod("setConfigFile", File.class).invoke(null, events.toFile());
			NonCodeMembersContributor.EP_NAME.getPoint().registerExtension(
				(NonCodeMembersContributor) eventLoader.loadClass("EventMethodsContributor").getConstructor().newInstance(), getTestRootDisposable());
			Files.writeString(bindings, "{\"server\":[{\"name\":\"$ServerEvents\",\"type\":\"dev.uncandango.kubegroovy.typing.EventGroupBinding\",\"eventGroup\":true}]}");
			ClassLoader bindingLoader = loadScript("bindings");
			bindingLoader.loadClass("BindingsSupport").getMethod("setConfigFile", File.class).invoke(null, bindings.toFile());
			NonCodeMembersContributor.EP_NAME.getPoint().registerExtension(
				(NonCodeMembersContributor) bindingLoader.loadClass("BindingsContributor").getConstructor().newInstance(), getTestRootDisposable());
			PsiFile file = script("""
				$ServerEvents.recipes { inferredEvent ->
				 def recipes = inferredEvent.recipes
				 def smelting = recipes.smelting
				 def recipe = smelting('glass','sand')
				 recipe.id('example:glass').xp(0.35).cookingTime(200)
				}
				""");
			assertEquals("dev.uncandango.kubegroovy.recipe.RecipesKubeEvent", reference(file, "inferredEvent").getType().getCanonicalText());
			assertNotNull(reference(file, "xp").resolve());
			assertNotNull(reference(file, "cookingTime").resolve());
		} finally {
			Files.deleteIfExists(events);
			Files.deleteIfExists(bindings);
		}
	}

	public void testMissingCatalogAndProjectIsolation() throws Exception {
		PsiFile file = script("event.recipes");
		Object own = loader.loadClass("RecipeSupport").getMethod("load", PsiElement.class).invoke(null, file);
		Project other = com.intellij.openapi.project.ProjectManager.getInstance().getDefaultProject();
		PsiFile otherFile = PsiFileFactory.getInstance(other).createFileFromText("other.groovy", org.jetbrains.plugins.groovy.GroovyLanguage.INSTANCE, "");
		Object empty = loader.loadClass("RecipeSupport").getMethod("load", PsiElement.class).invoke(null, otherFile);
		assertNotSame(own, empty);
		assertTrue(((java.util.Map<?, ?>) empty.getClass().getMethod("getData").invoke(empty)).get("recipes") instanceof java.util.Map<?, ?> recipes && recipes.isEmpty());
		Files.delete(catalog);
		assertSame(own, loader.loadClass("RecipeSupport").getMethod("load", PsiElement.class).invoke(null, file));
	}

	public void testRecipeMapDoesNotEagerlyBuildEveryRecipeType() throws Exception {
		PsiFile file = script("event");
		Object config = loader.loadClass("RecipeSupport").getMethod("load", PsiElement.class).invoke(null, file);
		PsiType map = (PsiType) loader.loadClass("RecipeSupport").getMethod("type", String.class, String.class, PsiElement.class, loader.loadClass("RecipeConfig"))
			.invoke(null, "map", "", file, config);
		java.util.Map<?, ?> identities = (java.util.Map<?, ?>) config.getClass().getMethod("getIdentities").invoke(config);
		assertEquals(1, identities.size());
		PsiClass cls = typeClass(map);
		PsiField[] fields = cls.getFields();
		assertEquals("Map fields should retain lazy callable types", 1, identities.size());
		PsiField smelting = Arrays.stream(fields).filter(field -> field.getName().equals("smelting")).findFirst().orElseThrow();
		assertNotNull(smelting.getType());
		assertEquals(2, identities.size());
	}

	public void testScopeAndCompletionInsertion() {
		myFixture.configureByText("outside.groovy", "import dev.uncandango.kubegroovy.recipe.RecipesKubeEvent\nRecipesKubeEvent event\nevent.recipes");
		assertEquals("java.util.Map", typeClass(reference(myFixture.getFile(), "recipes").getType()).getQualifiedName());
		script("def recipe = event.smelting('glass','sand')\nrecipe.co<caret>");
		myFixture.completeBasic();
		assertTrue(myFixture.getLookupElementStrings().toString(), myFixture.getLookupElementStrings().contains("cookingTime"));
		myFixture.getLookup().setCurrentItem(Arrays.stream(myFixture.getLookupElements()).filter(item -> item.getLookupString().equals("cookingTime")).findFirst().orElseThrow());
		myFixture.finishLookup('\n');
		assertTrue(myFixture.getFile().getText(), myFixture.getFile().getText().contains("recipe.cookingTime("));
	}
}
