package dev.uncandango.kubegroovy.text.action;

import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class TextActionBuilder {
	@HideFromGY
	public List<TextAction> actions = new ArrayList<>(1);

	public void dynamic(String id) {
		actions.add(new DynamicTextAction(id));
	}

	public void add(List<Component> text) {
		actions.add(new AddTextAction(text));
	}

	public void insert(int line, List<Component> text) {
		actions.add(new InsertTextAction(line, text));
	}

	public void removeLine(int line) {
		actions.add(new RemoveLineTextAction(line));
	}

	public void removeText(Component match) {
		actions.add(new RemoveTextTextAction(match));
	}

	public void removeExactText(Component match) {
		actions.add(new RemoveExactTextTextAction(match));
	}

	public void clear() {
		actions.add(ClearTextAction.INSTANCE);
	}
}
