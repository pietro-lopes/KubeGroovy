package dev.uncandango.kubegroovy.script;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.uncandango.kubegroovy.codec.KubeGroovyCodecs;
import dev.uncandango.kubegroovy.error.KubeRuntimeException;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.DirectTypeWrapperFactory;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import dev.uncandango.kubegroovy.script.bridge.TypeWrapperFactory;
import dev.uncandango.kubegroovy.script.bridge.TypeWrapperValidator;
import dev.uncandango.kubegroovy.script.bridge.TypeWrappers;
import dev.uncandango.kubegroovy.script.groovy.KubeGroovyMetaClassCreationHandle;
import dev.uncandango.kubegroovy.util.Cast;
import dev.uncandango.kubegroovy.util.RegistryAccessContainer;
import net.minecraft.util.StringRepresentable;
import org.codehaus.groovy.runtime.typehandling.GroovyCastException;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Function;

public class TypeWrapperRegistry {
	public interface ContextFromFunction<T> extends BiFunction<Context, Object, T> {
	}

	public interface RegistriesFromFunction<T> extends BiFunction<RegistryAccessContainer, Object, T> {
	}

	private final ScriptType type;
	private final TypeWrappers typeWrappers;

	public TypeWrapperRegistry(ScriptType type, TypeWrappers typeWrappers) {
		this.type = type;
		this.typeWrappers = typeWrappers;
	}

	public ScriptType scriptType() {
		return type;
	}

	public <T> boolean hasTypeWrapper(Class<T> target) {
		return typeWrappers.hasWrapper(target.getName());
	}

	/** Entry point used by the sandbox-rewritten {@code value as Target} casts. */
	public static Object coerce(Object value, TypeInfo target) {
		var cx = Context.getCurrent();
		if (!(cx instanceof KubeGroovyContext kgy)) {
			throw new GroovyCastException(value, target.asClass());
		}
		Object result = kgy.kgyFactory.getTypeWrappers().coerce(value, target);
		// A wrapper may return a vanilla value whose script-facing methods are
		// supplied by an injected KGY interface.  Refresh that concrete class so
		// the same aliases/properties are available immediately after an explicit
		// Groovy cast.
		if (result != null) {
			KubeGroovyMetaClassCreationHandle.refreshIfNeeded(result);
		}
		return result;
	}

	/** Entry point used by the sandbox-rewritten {@code value as Target} casts. */
	public static Object coerce(Object value, Class<?> target) {
		var cx = Context.getCurrent();
		if (!(cx instanceof KubeGroovyContext kgy)) {
			throw new GroovyCastException(value, target);
		}
		Object result = kgy.kgyFactory.getTypeWrappers().coerce(value, target);
		// A wrapper may return a vanilla value whose script-facing methods are
		// supplied by an injected KGY interface.  Refresh that concrete class so
		// the same aliases/properties are available immediately after an explicit
		// Groovy cast.
		if (result != null) {
			KubeGroovyMetaClassCreationHandle.refreshIfNeeded(result);
		}
		return result;
	}

	/** Used by the compiler customizer while a script class is being compiled. */
	public static boolean hasTypeWrapper(String targetName) {
		var cx = Context.getCurrent();
		return cx instanceof KubeGroovyContext kgy && kgy.kgyFactory.getTypeWrappers().hasWrapper(targetName);
	}

	public <T> void register(Class<T> target, TypeWrapperValidator validator, TypeWrapperFactory<T> factory, boolean coercesCharSequence) {
		typeWrappers.register(target, validator, factory, coercesCharSequence);
	}

	public <T> void register(Class<T> target, TypeWrapperFactory<T> factory, boolean coercesCharSequence) {
		typeWrappers.register(target, factory, coercesCharSequence);
	}

	public <T> void register(Class<T> target, TypeWrapperValidator validator, ContextFromFunction<T> factory, boolean coercesCharSequence) {
		typeWrappers.register(target, validator, (from, t) -> { var cx = Context.getCurrent(); return factory.apply(cx, from); }, coercesCharSequence);
	}

	public <T> void register(Class<T> target, ContextFromFunction<T> factory, boolean coercesCharSequence) {
		typeWrappers.register(target, (from, t) -> { var cx = Context.getCurrent(); return factory.apply(cx, from); }, coercesCharSequence);
	}

	public <T> void register(Class<T> target, RegistriesFromFunction<T> factory, boolean coercesCharSequence) {
		typeWrappers.register(target, (from, t) -> {
			try {
				return factory.apply(RegistryAccessContainer.of(), from);
			} catch (KubeRuntimeException ex) {
				throw ex.source(SourceLine.of());
			}
		}, coercesCharSequence);
	}

	public <T> void register(Class<T> target, TypeWrapperValidator validator, DirectTypeWrapperFactory<T> factory, boolean coercesCharSequence) {
		typeWrappers.registerDirect(target, validator, factory, coercesCharSequence);
	}

	public <T> void register(Class<T> target, DirectTypeWrapperFactory<T> factory, boolean coercesCharSequence) {
		typeWrappers.registerDirect(target, factory, coercesCharSequence);
	}

	public <T extends Enum<T> & StringRepresentable> void registerEnumFromStringCodec(Class<T> target, Codec<T> codec, @Nullable T defaultValue, boolean forceLowerCase) {
		register(target, o -> {

			if (o == null) {
				return defaultValue;
			} else if (target.isInstance(o)) {
				return Cast.to(o);
			} else {
				var s = o.toString();
				return s.isEmpty() ? defaultValue : KubeGroovyCodecs.byName(codec, forceLowerCase ? s.toLowerCase(Locale.ROOT) : s);
			}
		}, true);
	}

	public <T extends Enum<T> & StringRepresentable> void registerEnumFromStringCodec(Class<T> target, Codec<T> codec) {
		registerEnumFromStringCodec(target, codec, null, true);
	}

	public <T> void registerCodec(Class<T> target, Codec<T> codec, @Nullable T defaultValue) {
		typeWrappers.register(target, new CodecTypeWrapper<>(target, codec, defaultValue), false);
	}

	public <T> void registerCodec(Class<T> target, Codec<T> codec) {
		registerCodec(target, codec, null);
	}

	public <T> void registerMapCodec(Class<T> target, MapCodec<T> codec, @Nullable T defaultValue) {
		typeWrappers.register(target, new MapCodecTypeWrapper<>(target, codec, defaultValue), false);
	}

	public <T> void registerMapCodec(Class<T> target, MapCodec<T> codec) {
		registerMapCodec(target, codec, null);
	}

	public <F, T> void registerAlias(Class<T> target, Class<F> from, Function<F, T> converter, boolean coercesCharSequence) {
		registerAlias(target, TypeInfo.of(from), converter, coercesCharSequence);
	}

	public <F, T> void registerAlias(Class<T> target, TypeInfo from, @Nullable Function<F, T> converter, boolean coercesCharSequence) {
		if (converter != null) {
			typeWrappers.register(target, (f, typeInfo) -> {
				var cx = Context.getCurrent();
				var o1 = cx.gyToJava(f, from);
				return o1 == null ? null : Cast.to(converter.apply(Cast.to(o1)));
			}, coercesCharSequence);
		}
	}
}
