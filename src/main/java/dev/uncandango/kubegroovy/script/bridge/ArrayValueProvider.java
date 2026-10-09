package dev.uncandango.kubegroovy.script.bridge;

import dev.uncandango.kubegroovy.error.KubeRuntimeException;
import dev.uncandango.kubegroovy.script.type.TypeInfo;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public interface ArrayValueProvider {
	ArrayValueProvider EMPTY = new ArrayValueProvider() {
		@Override
		public int getLength() {
			var cx = Context.getCurrent();
			return 0;
		}

		@Override
		public Object getArrayValue(int index) {
			var cx = Context.getCurrent();
			return null;
		}

		@Override
		public Object getErrorSource() {
			var cx = Context.getCurrent();
			return null;
		}
	};

	int getLength();

	Object getArrayValue(int index);

	Object getErrorSource();

	default Object createArray(TypeInfo target) {
		var cx = Context.getCurrent();
		int len = getLength();
		var arr = target.newArray(len);

		for (int i = 0; i < len; i++) {
			try {
				Array.set(arr, i, cx.gyToJava(getArrayValue(i), target));
			} catch (KubeRuntimeException ee) {
				return cx.reportConversionError(getErrorSource(), target);
			}
		}

		return arr;
	}

	default Object createList(TypeInfo target) {
		var cx = Context.getCurrent();
		int len = getLength();

		if (len == 0) {
			return List.of();
		} else if (len == 1) {
			try {
				return List.of(cx.gyToJava(getArrayValue(0), target));
			} catch (KubeRuntimeException ee) {
				return cx.reportConversionError(getErrorSource(), target);
			}
		}

		var list = new ArrayList<>(len);

		for (int i = 0; i < len; i++) {
			try {
				list.add(cx.gyToJava(getArrayValue(i), target));
			} catch (KubeRuntimeException ee) {
				return cx.reportConversionError(getErrorSource(), target);
			}
		}

		return list;
	}

	default Object createSet(TypeInfo target) {
		var cx = Context.getCurrent();
		int len = getLength();

		if (len == 0) {
			return Set.of();
		} else if (len == 1) {
			try {
				return Set.of(cx.gyToJava(getArrayValue(0), target));
			} catch (KubeRuntimeException ee) {
				return cx.reportConversionError(getErrorSource(), target);
			}
		}

		var set = new HashSet<>(len);

		for (int i = 0; i < len; i++) {
			try {
				set.add(cx.gyToJava(getArrayValue(i), target));
			} catch (KubeRuntimeException ee) {
				return cx.reportConversionError(getErrorSource(), target);
			}
		}

		return set;
	}

	record FromObject(Object object) implements ArrayValueProvider {
		public static final FromObject FROM_NULL = new FromObject(null);

		@Override
		public int getLength() {
			var cx = Context.getCurrent();
			return 1;
		}

		@Override
		public Object getArrayValue(int index) {
			var cx = Context.getCurrent();
			return object;
		}

		@Override
		public Object getErrorSource() {
			var cx = Context.getCurrent();
			return object;
		}
	}

	record FromJavaArray(Object array, int length) implements ArrayValueProvider {
		@Override
		public int getLength() {
			var cx = Context.getCurrent();
			return length;
		}

		@Override
		public Object getArrayValue(int index) {
			var cx = Context.getCurrent();
			return Array.get(array, index);
		}

		@Override
		public Object getErrorSource() {
			var cx = Context.getCurrent();
			return array;
		}
	}

	record FromPlainJavaArray(Object[] array) implements ArrayValueProvider {
		@Override
		public int getLength() {
			var cx = Context.getCurrent();
			return array.length;
		}

		@Override
		public Object getArrayValue(int index) {
			var cx = Context.getCurrent();
			return array[index];
		}

		@Override
		public Object getErrorSource() {
			var cx = Context.getCurrent();
			return array;
		}
	}

	static ArrayValueProvider fromJavaList(List<?> list, Object errorSource) {
		return list.isEmpty() ? EMPTY : new FromJavaList(list, errorSource);
	}

	record FromJavaList(List<?> list, Object errorSource) implements ArrayValueProvider {
		@Override
		public int getLength() {
			var cx = Context.getCurrent();
			return list.size();
		}

		@Override
		public Object getArrayValue(int index) {
			var cx = Context.getCurrent();
			return list.get(index);
		}

		@Override
		public Object getErrorSource() {
			var cx = Context.getCurrent();
			return errorSource;
		}
	}

	static ArrayValueProvider fromIterable(Iterable<?> iterable) {
		int len;

		if (iterable instanceof Collection<?> c) {
			len = c.size();
		} else {
			len = 0;

			for (var ignored : iterable) {
				len++;
			}
		}

		return len == 0 ? EMPTY : new FromIterator(len, iterable.iterator(), iterable);
	}

	record FromIterator(int length, Iterator<?> iterator, Object errorSource) implements ArrayValueProvider {
		@Override
		public int getLength() {
			var cx = Context.getCurrent();
			return length;
		}

		@Override
		public Object getArrayValue(int index) {
			var cx = Context.getCurrent();
			return iterator.next();
		}

		@Override
		public Object getErrorSource() {
			var cx = Context.getCurrent();
			return errorSource;
		}
	}
}