---
name: recipe-json-schema
description: Create or update KubeGroovy JSON recipe schemas from a recipe serializer Codec or MapCodec, including reusable nested recipe components. Invoke when a user wants a recipe schema based on serializer codec source. Does not apply to ordinary recipe JSON instances or general JSON Schema documents.
---

# Recipe JSON Schemas from Serializer Codecs

Produce KubeGroovy schema resources that preserve the serializer's JSON wire format and expose useful Groovy recipe builders. These are KubeGroovy schema definitions, not JSON Schema draft documents.

## Establish the source of truth

This skill ships with the project. Follow its AGENTS.md and locate source by Java class or package name using the project's source roots or available source lookup tools. Do not assume a checkout location or source-directory layout. Read the current implementations rather than assuming this reference matches another revision:

- `dev.uncandango.kubegroovy.recipe.component.RecipeComponent` and implementations in `dev.uncandango.kubegroovy.recipe.component`: built-in configuration codecs, value codecs, wrapping, and validation. Inspect `CustomObjectRecipeComponent` for structured nested fields and `NestedRecipeComponent` for embedded complete recipes.
- `dev.uncandango.kubegroovy.plugin.builtin.BuiltinKubeGroovyPlugin.registerRecipeComponents`: which components are actually registered.
- `dev.uncandango.kubegroovy.recipe.schema.RecipeSchemaData`: accepted schema/key/constructor JSON fields.
- `dev.uncandango.kubegroovy.recipe.schema.JsonRecipeSchemaLoader`: inheritance, defaults, constructors, resource paths, and load errors.
- `dev.uncandango.kubegroovy.recipe.schema.RecipeSchemaStorage`: recursive component decoding and reusable `recipe_components.json` definitions.
- Existing `kubegroovy/recipe_schema` resources: compare cooking, shaped, and shapeless schemas as relevant.

Obtain the serializer registration ID, exact mod/Minecraft version, and its JSON `Codec`/`MapCodec`. Follow helper codecs, nested records, `xmap`/`flatXmap`, dispatch branches, validations, and default suppliers until the serialized structure is known. The network `StreamCodec` and Java constructor parameter order do not define recipe JSON. Use the available minecraft-dev skill when external Minecraft/mod source lookup is needed; avoid guessing inaccessible codec details. Request missing source or identifiers only when they block a faithful schema.

## Translate the codec

Read [the format and nested-component guide](references/format.md) for concrete syntax and the worked example.

For each serialized field, identify its exact JSON name, value shape, presence/default behavior, constraints, and input/output/other role. A mapping table is useful for complex codecs. Preserve serialized names; builder aliases belong in `function_names`, JSON read aliases in `alternative_names` only when intended.

- Match a component's **value codec** to the target codec, not merely its Java type or script convenience wrapper. Distinguish ingredients from sized ingredients, item stacks from item stack templates, IDs from registry elements, and ticks from seconds. Check enum serialized names rather than assuming Java constant names match.
- Use `item_stack_template` for `ItemStackTemplate.CODEC` and `fluid_stack_template` for NeoForge's `FluidStackTemplate.CODEC`; they expose template values, with `.create()` producing mutable stacks. When a mod codec returns the same value type but uses different fields, defaults, or constraints, use `with_codec` to delegate to that runtime codec rather than approximating it with the built-in codec or `custom_object`. For foreign value classes with corresponding typed properties, add the generic bidirectional `conversion` instead of a hardcoded adapter; omitted properties retain their names, and constructors require records, parameter metadata, or explicit property order. See the template and reflected-codec examples in [the format guide](references/format.md#templates-and-reflected-value-codecs).
- Use strings for components whose configuration codec accepts an empty map; use `{ "type": ..., ... }` for configured components. Do not invent suffix syntax such as `ingredient[]`.
- Map required `fieldOf` fields to required keys. For `optionalFieldOf(name, default)`, put that value in `optional` using the component's actual JSON representation. For optional fields without a default, use `default_optional: true` to preserve omission. This supplies a null sentinel, not a codec-derived default. Do not specify both forms.
- Place all required schema keys before optional keys. Constructor order can follow the surrounding script API (often output first) while JSON names remain faithful. Use explicit constructors only where they improve the intended API; check every referenced key and override.
- Build structured fields with inline `custom_object`, or reusable named components in `recipe_components.json`. Nested keys use `component` and boolean `optional`; top-level schema keys use `type` and a literal `optional` value. Nested default values are not supported by that declaration.
- Preserve list/map bounds, union branch behavior, and empty-value semantics. An `optional_*` component permits empty values; it does not make a schema key omissible. Explain any mismatch that cannot be represented by existing components instead of silently approximating it or changing Java APIs.

## Write and verify the resources

Locate the project's data-resource root and write `data/<serializer_namespace>/kubegroovy/recipe_schema/<serializer_path>.json` beneath the appropriate resource root. The path determines the schema ID. Follow the user's chosen data-pack or `kubegroovy/data` destination instead when requested, keeping the same namespace/resource layout. A reusable component file belongs at `data/<resource_namespace>/kubegroovy/recipe_components.json`; fully qualify its entry IDs because they do not inherit the file namespace.

Use inheritance, mappings, `override_type`, custom factories, unique-ID keys, functions, and post-processors only when needed and supported by the current loader. Reuse compatible parent schemas; check inherited constructors/defaults and merge behavior. Do not copy special factory behavior from a shaped recipe into an unrelated serializer.

Parse the written JSON, review all component IDs/defaults/constructor references, and compare representative generated recipe JSON against the target serializer codec using registry-aware ops where required. Exercise required fields, omitted optionals, nested/list/union fields, and meaningful bounds when runtime validation is available. JSON syntax alone does not prove a schema loads or a recipe decodes. Inspect schema/component and script-load errors; the loader can log a bad key and continue.

Run the repository's required checks for actual resource/code changes (`build`; runtime/scripting changes also require `test` and `runGametest` per AGENTS.md). A skill-only documentation change does not require the mod build. Report resource paths, intended builder call, verification performed, and any unresolved codec mismatch or unavailable runtime validation. Do not claim game-test success without the repository's required holder results and clean script loading.
