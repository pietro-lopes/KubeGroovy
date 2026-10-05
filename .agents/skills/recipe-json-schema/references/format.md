# KubeGroovy schema format and nested components

This guide describes the implementation inspected when the skill was created. Recheck the current Java codecs before using it for a different revision.

## Component configuration versus recipe values

`RecipeSchemaStorage.COMPONENT_CODEC` first tries a component ID with an empty configuration map, then a map dispatched on `type`. Built-in IDs without a namespace resolve to `kubegroovy`, so `"ingredient"` means `"kubegroovy:ingredient"`. Schema IDs such as `parent` use `Identifier.CODEC` and its Minecraft namespace default; fully qualify them to avoid ambiguity.

The configurations below go inside a schema key's `type`, not into the recipe's serialized value:

| Target value shape | Component configuration |
| --- | --- |
| Boolean/string/character/identifier | `"boolean"`, `"string"`, `"character"`, `"id"` |
| Numeric scalar | `"int"`, `"long"`, `"float"`, `"double"` |
| Bounded number | `{ "type": "int", "min": 1, "max": 64 }` (also long/float/double) |
| Ingredient/sized ingredient | `"ingredient"`, `"sized_ingredient"` |
| Item stack/template | `"item_stack"`, `"item_stack_template"` |
| Fluid stack/template | `"fluid_stack"`, `"fluid_stack_template"` |
| Fluid ingredient/sized ingredient | `"fluid_ingredient"`, `"sized_fluid_ingredient"` |
| Existing component with a runtime value codec | `{ "type": "with_codec", "component": "item_stack_template", "codec": "fully.qualified.ValueClass" }` |
| List | `{ "type": "list", "component": "ingredient", "min": 1, "max": 9 }` |
| List or single value | `{ "type": "list", "component": "ingredient", "can_write_self": true }` |
| String-keyed map | `{ "type": "map", "key": "string", "component": "ingredient" }` |
| Crafting symbol map | `{ "type": "pattern", "component": "optional_ingredient" }` |
| Union | `{ "type": "either", "left": "int", "right": "string" }` |
| Structured object | `{ "type": "custom_object", "keys": [...] }` |
| Embedded complete recipe | `"nested_recipe"` (requires a recipe with `type`; not an arbitrary nested record) |
| Registry entry | `{ "type": "registry_element", "registry": "minecraft:item" }` |
| Resource key | `{ "type": "resource_key", "registry": "minecraft:dimension" }` |
| Tag key | `{ "type": "tag", "registry": "minecraft:item", "hashed": false }` |

Verify registered IDs and value codecs before choosing a row. `string` uses a nonempty-string codec; use `optional_string` for unrestricted `Codec.STRING` (including empty strings). Specialized block/state/time/book-category components and optional variants are available among the implementations of `RecipeComponent` in `dev.uncandango.kubegroovy.recipe.component`; inspect them only as needed. Unit tag components such as `item_tag` and `hashed_item_tag` may be a better fit than configured tags.

List/map `min` and `max` are fields on the same object as `type`, not inside a `bounds` object. `IntBounds` defaults to minimum 1 and maximum `Integer.MAX_VALUE`; set `min: 0` when empty collections are allowed. Bounds and component emptiness checks are distinct: verify validation permits the intended empty value. `conditional` is a list flag for NeoForge conditional elements, and `spread` is an additional component configuration; do not add either without corresponding codec/API needs.

`either` uses `Codec.either`, with left/right order significant. Groovy wrapping also uses component `hasPriority`, so test ambiguous shapes. Generic enum configuration is `{ "type": "enum", "enum": "fully.qualified.EnumClass" }`; inspect class accessibility, its `StringRepresentable` requirement, and case/remapping behavior before assuming equivalence to the serializer's enum codec.

## Templates and reflected value codecs

`item_stack_template` and `fluid_stack_template` store actual `ItemStackTemplate` and NeoForge `FluidStackTemplate` values, respectively. Scripts call `.create()` to obtain an `ItemStack` or `FluidStack`. Their script wrappers accept existing templates, non-empty stacks, and the corresponding item/fluid strings; recipe output replacements preserve the original count or amount. Templates do not support empty values; schema omission is a separate decision.

The built-in value codecs differ:

| Component | Recipe JSON value | Defaults and constraints |
| --- | --- | --- |
| `item_stack_template` | Item ID string, or object with `id`, `count`, `components` | Object `count` defaults to 1 and ranges from 1 to 99; `components` defaults to an empty patch |
| `fluid_stack_template` | Fluid ID string, or object with `id`, `amount`, `components` | String defaults to `FluidType.BUCKET_VOLUME`; object `amount` is required and positive; `components` defaults to an empty patch |

For example, a fluid result key can declare `{ "name": "result", "role": "output", "type": "fluid_stack_template" }`, with a recipe value of `{ "id": "minecraft:water", "amount": 250 }`. Choose this only when the target serializer actually uses the template codec, rather than `FluidStack.CODEC` or a fixed-amount variant.

`with_codec` uses `RecipeComponentWithCodec` to retain a parent's script type, wrapping, matching, replacement, validation, and unique IDs while replacing its value codec. Use it when the target codec returns a supported value type but has different serialized names or rules. For example, a mod's `MapCodec<ItemStackTemplate>` with an `item` field instead of `id`, any positive `count`, and `.orElse(1)` fallback should delegate to that actual codec:

```json
{
  "name": "result",
  "role": "output",
  "type": {
    "type": "with_codec",
    "component": "item_stack_template",
    "codec": {
      "class": "com.example.mod.RecipeSerializer",
      "field": "RESULT_CODEC"
    }
  }
}
```

`codec.class` names the class declaring the field, using its binary name (`Outer$Inner` for a nested class). `codec.field` is optional and defaults to `CODEC`. `"codec": "com.example.mod.Result"` is shorthand for that default. The field must be static and declared as `Codec` or `MapCodec`; private fields are supported when runtime access permits, and a `MapCodec` is adapted with `.codec()`. Codec-field resolution reads the typed field without exposing reflection handles. An optional conversion invokes its selected property readers and constructors. Reading a valid static codec field may initialize its declaring class. Missing classes/fields, non-static fields, wrong declared types, inaccessible fields, null codecs, and initialization failures produce component decode errors.

Choose `component` from the reflected codec's actual value type: use `fluid_stack_template` for a `Codec<FluidStackTemplate>`, for example. Java generics are erased, so field resolution cannot prove that the codec's value type matches its parent. Inspect the source and verify representative values. The class must be present in the running mod environment; a codec field on a recipe class may decode a whole recipe rather than its result. Do not guess the class or field from the serializer ID.

The parent can itself be a component configuration or reusable ID. A `with_codec` configuration can be nested in lists/maps or stored as a fully qualified entry in `recipe_components.json`. Encoding the component definition retains its class/field reference for reload. Schema key defaults use the replacement codec's wire format, so an `optional` object for the example above uses `item`, not `id`. Verify default/fallback behavior against the reflected codec, not the parent codec; `.orElse(1)` can also handle invalid input, unlike merely omitting a field with a default.

For runtime verification, decode the component configuration with `RecipeSchemaStorage.COMPONENT_CODEC`, exercise value encode/decode through its `.codec()`, and check script conversion and replacements. Include template count/amount and component patches, omitted/defaulted fields, and meaningful invalid inputs. Keep JSON objects with JSON ops and Java maps with Java ops when constructing nested verification fixtures.

## Foreign codec value conversion

When the codec returns a mod-specific class, keep a useful built-in component as the script-facing parent and add `conversion` to `with_codec`:

```json
{
  "type": "with_codec",
  "component": "sized_ingredient",
  "codec": {"class": "com.example.mod.IngredientStack", "field": "ITEM_CODEC"},
  "conversion": {
    "class": "com.example.mod.IngredientStack$Item",
    "properties": {"count": "amount"},
    "parent_constructor": ["ingredient", "count"]
  }
}
```

`conversion.class` is the codec value class, which can differ from the class declaring its codec field. The parent class comes from the component's type information. Properties map parent names to foreign names; omitted entries use the same name. Readers try an exact-name, no-argument instance method before an instance field. An override can be a string alias or `{"field": "amount"}` / `{"method": "amount"}` to choose explicitly, including private members when accessible.

Constructors use record canonical parameter names, or retained parameter-name metadata on ordinary classes, and must have one compatible candidate. When metadata is absent or inference is ambiguous, provide `parent_constructor` and/or `codec_constructor`: ordered arrays of canonical parent property names, not foreign aliases. Each constructor must consume the same mapped property set. Explicit order still requires a unique type-compatible constructor. There is no numeric coercion or positional guessing. Unknown, duplicate, static, missing, inaccessible, or incompatible properties fail component decoding.

For a custom sized fluid ingredient with `ingredient` and `amount`, use `sized_fluid_ingredient`; both property names match, so omit `properties`. Set `parent_constructor` to `["ingredient", "amount"]` if the parent lacks parameter-name metadata. Conversion is generic and is not restricted to ingredients. It resolves reflection once on configuration load, reads properties and constructs typed values in both directions through `flatXmap`, preserves the foreign codec's wire format/defaults/validation, and retains the parent's script wrapping, matching, replacement, and spread behavior. Constructor/accessor exceptions or an unexpected decoded value type become codec errors. Encoding a component definition preserves conversion metadata for reload.

Verify both directions with registry-aware ops, constructor fallback, omitted foreign defaults, rejected values, and parent operations. Do not use this mechanism when the two classes have different semantics or require transformations beyond corresponding typed properties.

## Schema keys and builders

`RecipeSchemaData.RecipeKeyData` accepts:

| Field | Meaning |
| --- | --- |
| `name` | Required serialized field name |
| `type` | Required component string or configuration object |
| `role` | `input`, `output`, or `other` (default) |
| `optional` | Literal default JSON parsed through the value codec; false/0/empty string are values, not flags |
| `default_optional` | Boolean, default false; true chooses the null/omission sentinel |
| `alternative_names` | Additional JSON names read for this key |
| `function_names` | Builder method names (loader only applies a nonempty list) |
| `excluded` | Excludes optional key from generated constructors; does not remove it from recipe JSON |
| `always_write` | Populates/writes optional values even when unchanged; use only when required |

`optional_ingredient`, `optional_item_stack`, etc. describe component empty-value support, not schema presence. Top-level `optional: true` is a literal boolean default, valid only for a component whose value codec accepts it. Omitted defaults normally remain omitted from emitted JSON; the recipe serializer can apply its own default on decode.

Top-level fields are `parent`, `override_type`, `recipe_factory`, `keys`, `constructors`, `functions`, `override_keys`, `hidden`, `mappings`, `unique`, `post_processors`, and `merge`. Constructor entries have `keys` (ordered names) and optional `overrides` (name-to-literal-value map). `override_keys` also maps names to literal values, not replacement key definitions. Constructor names, override names, and `unique` names must resolve to schema keys.

Inherited keys/constructors/unique/post-processors are replaced by supplied child lists by default, or inherited when omitted. Use `merge: { "keys": true }` and corresponding `constructors`, `unique`, `post_processors` flags to combine lists. Functions gather parent-first with child names replacing parent names. Merged keys are required-first; a parent's optional key cannot become required. Verify parent existence and avoid cycles. `override_type` changes the emitted serializer ID; `mappings` provides script-name mappings. Neither is necessary when the schema path already matches the serializer.

## Reusable nested objects

`RecipeSchemaStorage.fireEvents` registers built-ins, discovers `kubegroovy/**/recipe_components.json`, registers memoized definitions, bakes them, then loads schemas. Each file maps component IDs to configurations. Entries use `ID.kgy(entryName)`, so an unqualified name becomes `kubegroovy:<name>` regardless of the file's resource namespace. Use explicit IDs; avoid duplicate definitions and reference cycles.

For an illustrative serializer `example:pressing`, suppose its JSON codec has:

```java
RecordCodecBuilder.mapCodec(instance -> instance.group(
    Ingredient.CODEC.fieldOf("ingredient").forGetter(PressingRecipe::ingredient),
    Yield.CODEC.fieldOf("result").forGetter(PressingRecipe::result),
    Codec.INT.optionalFieldOf("duration", 100).forGetter(PressingRecipe::duration)
).apply(instance, PressingRecipe::new));
// Yield.CODEC has required string "item", required intRange(1, 64) "count",
// and optional string "label" with no default.
```

Write `data/example/kubegroovy/recipe_components.json`:

```json
{
  "example:pressing_yield": {
    "type": "custom_object",
    "keys": [
      { "name": "item", "component": "optional_string" },
      { "name": "count", "component": { "type": "int", "min": 1, "max": 64 } },
      { "name": "label", "component": "optional_string", "optional": true }
    ]
  }
}
```

Write `data/example/kubegroovy/recipe_schema/pressing.json`:

```json
{
  "keys": [
    { "name": "result", "role": "output", "type": "example:pressing_yield" },
    { "name": "ingredient", "role": "input", "type": "ingredient" },
    { "name": "duration", "type": "int", "optional": 100 }
  ]
}
```

The intended Groovy call is:

```groovy
event.recipes.example.pressing([item: 'minecraft:iron_ingot', count: 2], 'minecraft:iron_ore')
    .duration(80)
```

This hypothetical codec deliberately uses a string-valued result item; real item-stack codecs must use their corresponding components and JSON shape. The `custom_object` configuration may instead be placed inline in the result key's `type`.

Nested key syntax is **`component`**, not `type`. Nested `optional` is a **boolean** marking an omissible field, unlike a schema key's literal default. Nested keys accept `always_write`, but the current object codec encodes supplied entries directly and does not synthesize defaults; inspect implementation before relying on this metadata. `custom_object` rejects unknown nested fields. Its decode/wrap/validate methods process supplied entries rather than guaranteeing every required field exists; check the final recipe with the target codec. If a nested target codec provides a default, mark the nested field optional so omission can reach that codec, and disclose that the component does not materialize the default into the script object. Do not invent a nested `default` property.
