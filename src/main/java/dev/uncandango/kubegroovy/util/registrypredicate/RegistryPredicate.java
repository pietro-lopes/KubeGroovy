package dev.uncandango.kubegroovy.util.registrypredicate;

import com.google.gson.JsonPrimitive;
import dev.uncandango.kubegroovy.registry.RegistryType;
import dev.uncandango.kubegroovy.util.ID;
import dev.uncandango.kubegroovy.util.RegExpKGY;
import dev.uncandango.kubegroovy.script.bridge.BaseFunction;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.NativeRegExp;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@FunctionalInterface
@SuppressWarnings({"rawtypes", "unchecked"})
public interface RegistryPredicate<T> extends Predicate<Holder<T>> {
	static RegistryPredicate<?> of(Context cx, @Nullable Object from, TypeInfo target) {
		return switch (from) {
			case null -> EntireRegistryPredicate.FALSE;
			case RegistryPredicate<?> p -> p;
			case Pattern pattern -> new RegistryRegExpPredicate<>(pattern);
			case NativeRegExp pattern -> new RegistryRegExpPredicate<>(RegExpKGY.wrap(pattern));
			case CharSequence cs -> ofString(target, cs.toString());
			case JsonPrimitive p -> ofString(target, p.getAsString());
			case BaseFunction fn -> {
				var t = target.param(0);
				var predicate = (Predicate) cx.gyToJava(fn, t.shouldConvert() ? TypeInfo.RAW_PREDICATE.withParams(t) : TypeInfo.RAW_PREDICATE);
				yield predicate::test;
			}
			case Iterable itr -> {
				List<RegistryPredicate<?>> results = new ArrayList<>();

				for (var elem : itr) {
					results.add(of(cx, elem, target));
				}

				yield switch (results.size()) {
					case 0 -> EntireRegistryPredicate.FALSE;
					case 1 -> results.getFirst();
					default -> new AnyRegistryPredicate(results);
				};
			}
			default -> new RegistryHolderPredicate<>(Holder.direct(from));
		};
	}

	private static RegistryPredicate<?> ofString(TypeInfo target, String s) {
		return switch (s) {
			case "*" -> EntireRegistryPredicate.TRUE;
			case "-", "" -> EntireRegistryPredicate.FALSE;
			case String tag when tag.charAt(0) == '#' -> {
				var reg = RegistryType.ofType(target.param(0));
				var registryTag = ID.mc(tag.substring(1));

				yield reg != null
					? new RegistryTagKeyPredicate<>(TagKey.create(reg.key(), registryTag))
					: new RegistryTagIDPredicate<>(registryTag);
			}
			case String namespace when namespace.charAt(0) == '@' -> new RegistryNamespacePredicate<>(namespace.substring(1));
			default -> {
				var pattern = RegExpKGY.wrap(s);

				if (pattern != null) {
					yield new RegistryRegExpPredicate<>(pattern);
				}

				var reg = RegistryType.ofType(target.param(0));
				var id = ID.mc(s);

				if (reg != null) {
					Registry registry = BuiltInRegistries.REGISTRY.getValue((ResourceKey) reg.key());

					if (registry != null) {
						var opt = registry.get(id);

						if (opt.isPresent()) {
							yield new RegistryHolderPredicate<>((Holder) opt.get());
						}
					}
				}

				yield new RegistryIDPredicate<>(id);
			}
		};
	}

	default List<Holder.Reference<T>> getHolders(Registry<T> registry) {
		return registry.listElements().filter(this).toList();
	}

	default List<T> getValues(Registry<T> registry) {
		return registry.listElements().filter(this).map(Holder::value).toList();
	}
}
