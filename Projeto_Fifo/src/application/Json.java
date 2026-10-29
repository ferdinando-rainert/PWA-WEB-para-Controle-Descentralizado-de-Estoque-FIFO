package application;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {

	private Json() {
	}

	static Object parse(String input) {
		Parser parser = new Parser(input);
		Object value = parser.value();
		parser.whitespace();
		if (!parser.atEnd()) {
			throw new IllegalArgumentException("JSON contém caracteres após o valor.");
		}
		return value;
	}

	static String stringify(Object value) {
		StringBuilder output = new StringBuilder();
		write(value, output);
		return output.toString();
	}

	private static void write(Object value, StringBuilder output) {
		if (value == null) {
			output.append("null");
		} else if (value instanceof String || value instanceof Character || value instanceof TemporalAccessor
				|| value instanceof Enum<?>) {
			string(value.toString(), output);
		} else if (value instanceof Boolean) {
			output.append(value);
		} else if (value instanceof Number) {
			if (value instanceof Double && !Double.isFinite((Double) value)
					|| value instanceof Float && !Float.isFinite((Float) value)) {
				throw new IllegalArgumentException("JSON não aceita números não finitos.");
			}
			output.append(value);
		} else if (value instanceof Map<?, ?>) {
			output.append('{');
			boolean first = true;
			for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
				if (!(entry.getKey() instanceof String)) {
					throw new IllegalArgumentException("Chaves de objeto JSON devem ser textos.");
				}
				if (!first) {
					output.append(',');
				}
				first = false;
				string((String) entry.getKey(), output);
				output.append(':');
				write(entry.getValue(), output);
			}
			output.append('}');
		} else if (value instanceof Iterable<?>) {
			output.append('[');
			boolean first = true;
			for (Object item : (Iterable<?>) value) {
				if (!first) {
					output.append(',');
				}
				first = false;
				write(item, output);
			}
			output.append(']');
		} else if (value.getClass().isArray()) {
			output.append('[');
			for (int i = 0; i < Array.getLength(value); i++) {
				if (i > 0) {
					output.append(',');
				}
				write(Array.get(value, i), output);
			}
			output.append(']');
		} else {
			throw new IllegalArgumentException("Tipo não suportado na resposta JSON.");
		}
	}

	private static void string(String value, StringBuilder output) {
		output.append('"');
		for (int i = 0; i < value.length(); i++) {
			char character = value.charAt(i);
			switch (character) {
			case '"':
				output.append("\\\"");
				break;
			case '\\':
				output.append("\\\\");
				break;
			case '\b':
				output.append("\\b");
				break;
			case '\f':
				output.append("\\f");
				break;
			case '\n':
				output.append("\\n");
				break;
			case '\r':
				output.append("\\r");
				break;
			case '\t':
				output.append("\\t");
				break;
			default:
				if (character < 0x20) {
					output.append(String.format("\\u%04x", (int) character));
				} else {
					output.append(character);
				}
			}
		}
		output.append('"');
	}

	private static final class Parser {
		private final String input;
		private int position;

		private Parser(String input) {
			if (input == null) {
				throw new IllegalArgumentException("Corpo JSON ausente.");
			}
			this.input = input;
		}

		private boolean atEnd() {
			return position >= input.length();
		}

		private void whitespace() {
			while (!atEnd() && Character.isWhitespace(input.charAt(position))) {
				position++;
			}
		}

		private Object value() {
			whitespace();
			if (atEnd()) {
				throw invalid();
			}
			char character = input.charAt(position);
			if (character == '{') {
				return object();
			}
			if (character == '[') {
				return array();
			}
			if (character == '"') {
				return string();
			}
			if (character == 't') {
				return literal("true", Boolean.TRUE);
			}
			if (character == 'f') {
				return literal("false", Boolean.FALSE);
			}
			if (character == 'n') {
				return literal("null", null);
			}
			if (character == '-' || Character.isDigit(character)) {
				return number();
			}
			throw invalid();
		}

		private Map<String, Object> object() {
			position++;
			LinkedHashMap<String, Object> result = new LinkedHashMap<>();
			whitespace();
			if (consume('}')) {
				return result;
			}
			while (true) {
				whitespace();
				if (atEnd() || input.charAt(position) != '"') {
					throw invalid();
				}
				String key = string();
				whitespace();
				if (!consume(':')) {
					throw invalid();
				}
				result.put(key, value());
				whitespace();
				if (consume('}')) {
					return result;
				}
				if (!consume(',')) {
					throw invalid();
				}
			}
		}

		private List<Object> array() {
			position++;
			List<Object> result = new ArrayList<>();
			whitespace();
			if (consume(']')) {
				return result;
			}
			while (true) {
				result.add(value());
				whitespace();
				if (consume(']')) {
					return result;
				}
				if (!consume(',')) {
					throw invalid();
				}
			}
		}

		private String string() {
			position++;
			StringBuilder result = new StringBuilder();
			while (!atEnd()) {
				char character = input.charAt(position++);
				if (character == '"') {
					return result.toString();
				}
				if (character < 0x20) {
					throw invalid();
				}
				if (character != '\\') {
					result.append(character);
					continue;
				}
				if (atEnd()) {
					throw invalid();
				}
				char escaped = input.charAt(position++);
				switch (escaped) {
				case '"':
				case '\\':
				case '/':
					result.append(escaped);
					break;
				case 'b':
					result.append('\b');
					break;
				case 'f':
					result.append('\f');
					break;
				case 'n':
					result.append('\n');
					break;
				case 'r':
					result.append('\r');
					break;
				case 't':
					result.append('\t');
					break;
				case 'u':
					if (position + 4 > input.length()) {
						throw invalid();
					}
					try {
						result.append((char) Integer.parseInt(input.substring(position, position + 4), 16));
					} catch (NumberFormatException exception) {
						throw invalid();
					}
					position += 4;
					break;
				default:
					throw invalid();
				}
			}
			throw invalid();
		}

		private Object number() {
			int start = position;
			consume('-');
			if (consume('0')) {
				if (!atEnd() && Character.isDigit(input.charAt(position))) {
					throw invalid();
				}
			} else {
				digits();
			}
			if (consume('.')) {
				digits();
			}
			if (consume('e') || consume('E')) {
				if (!consume('+')) {
					consume('-');
				}
				digits();
			}
			try {
				return new BigDecimal(input.substring(start, position));
			} catch (NumberFormatException exception) {
				throw invalid();
			}
		}

		private void digits() {
			int start = position;
			while (!atEnd() && Character.isDigit(input.charAt(position))) {
				position++;
			}
			if (start == position) {
				throw invalid();
			}
		}

		private Object literal(String text, Object value) {
			if (!input.startsWith(text, position)) {
				throw invalid();
			}
			position += text.length();
			return value;
		}

		private boolean consume(char expected) {
			if (!atEnd() && input.charAt(position) == expected) {
				position++;
				return true;
			}
			return false;
		}

		private IllegalArgumentException invalid() {
			return new IllegalArgumentException("JSON inválido na posição " + position + ".");
		}
	}
}
