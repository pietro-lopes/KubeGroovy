package dev.uncandango.kubegroovy.recipe.filter;

import dev.uncandango.kubegroovy.error.KubeRuntimeException;
import dev.uncandango.kubegroovy.recipe.RecipesKubeEvent;
import dev.uncandango.kubegroovy.recipe.match.ReplacementMatchInfo;
import dev.uncandango.kubegroovy.script.ConsoleGY;
import dev.uncandango.kubegroovy.util.ID;
import dev.uncandango.kubegroovy.util.ListGY;
import dev.uncandango.kubegroovy.util.RegExpKGY;
import dev.uncandango.kubegroovy.script.bridge.BaseFunction;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.NativeRegExp;
import dev.uncandango.kubegroovy.script.bridge.TypeInfo;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;
import java.util.regex.Pattern;

@FunctionalInterface
public interface RecipeFilter extends Predicate<RecipeMatchContext> {
	@Override
	boolean test(RecipeMatchContext cx);

	static RecipeFilter wrap(Context cx, @Nullable Object o) {
		if (o == null || o == ConstantFilter.TRUE) {
			return ConstantFilter.TRUE;
		} else if (o == ConstantFilter.FALSE) {
			return ConstantFilter.FALSE;
		} else if (o instanceof CharSequence || o instanceof NativeRegExp || o instanceof Pattern) {
			String s = o.toString();

			if (s.equals("*")) {
				return ConstantFilter.TRUE;
			} else if (s.equals("-")) {
				return ConstantFilter.FALSE;
			} else {
				var r = RegExpKGY.wrap(s);
				return r == null ? new IDFilter(ID.mc(s)) : RegexIDFilter.of(r);
			}
		} else if (o instanceof BaseFunction func) {
			return (RecipeFilter) cx.createInterfaceAdapter(TypeInfo.of(RecipeFilter.class), func);
		}

		var list = ListGY.orSelf(o);

		if (list.isEmpty()) {
			return ConstantFilter.FALSE;
		} else if (list.size() > 1) {
			var predicate = new OrFilter();

			for (var o1 : list) {
				var p = wrap(cx, o1);

				if (p == ConstantFilter.TRUE) {
					return ConstantFilter.TRUE;
				} else if (p != ConstantFilter.FALSE) {
					predicate.list.add(p);
				}
			}

			return predicate.list.isEmpty() ? ConstantFilter.FALSE : predicate.list.size() == 1 ? predicate.list.getFirst() : predicate;
		}

		var map = cx.optionalMapOf(list.getFirst());

		if (map == null || map.isEmpty()) {
			return ConstantFilter.TRUE;
		}

		var predicate = new AndFilter();

		if (map.get("or") != null) {
			predicate.list.add(wrap(cx, map.get("or")));
		}

		if (map.get("not") != null) {
			predicate.list.add(new NotFilter(wrap(cx, map.get("not"))));
		}

		try {
			var id = map.get("id");

			if (id != null) {
				var pattern = RegExpKGY.wrap(id);
				predicate.list.add(pattern == null ? new IDFilter(ID.mc(id)) : RegexIDFilter.of(pattern));
			}

			var type = map.get("type");

			if (type != null) {
				predicate.list.add(new TypeFilter(ID.mc(type)));
			}

			var group = map.get("group");

			if (group != null) {
				predicate.list.add(new GroupFilter(group.toString()));
			}

			var mod = map.get("mod");

			if (mod != null) {
				predicate.list.add(new ModFilter(mod.toString()));
			}

			var input = map.get("input");

			if (input != null) {
				var m = ReplacementMatchInfo.wrap(cx, input, ReplacementMatchInfo.TYPE_INFO);

				if (m != ReplacementMatchInfo.NONE) {
					predicate.list.add(new InputFilter(m));
				} else {
					throw Context.reportRuntimeError("Unable to parse recipe input filter `" + input + "`", cx);
				}
			}

			var output = map.get("output");

			if (output != null) {
				var m = ReplacementMatchInfo.wrap(cx, output, ReplacementMatchInfo.TYPE_INFO);

				if (m != ReplacementMatchInfo.NONE) {
					predicate.list.add(new OutputFilter(m));
				} else {
					throw Context.reportRuntimeError("Unable to parse recipe output filter `" + output + "`", cx);
				}
			}

			NeoForge.EVENT_BUS.post(new RecipeFilterParseEvent(cx, predicate.list, map));

			if (predicate.list.isEmpty() && !map.isEmpty()) {
				throw Context.reportRuntimeError("Unable to parse recipe filter " + map, cx);
			}

			return predicate.list.isEmpty() ? ConstantFilter.TRUE : predicate.list.size() == 1 ? predicate.list.getFirst() : predicate;
		} catch (KubeRuntimeException rex) {
			ConsoleGY.getCurrent(cx).warn("", rex, RecipesKubeEvent.POST_SKIP_ERROR);
			return ConstantFilter.FALSE;
		}
	}
}