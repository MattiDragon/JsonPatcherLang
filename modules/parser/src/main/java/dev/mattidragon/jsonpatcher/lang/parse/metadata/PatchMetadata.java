package dev.mattidragon.jsonpatcher.lang.parse.metadata;

import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.parse.Parser;
import dev.mattidragon.jsonpatcher.lang.parse.Token;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class PatchMetadata {
    private final Map<String, MetadataElement> values = new LinkedHashMap<>();

    public void add(String key, SourceSpan atPos, Parser parser) {
        var namePos = parser.previous().pos();

        MetadataElement element;
        if (parser.hasNext(Token.SimpleToken.SEMICOLON)) {
            element = new MetadataNull();
        } else {
            element = new JsonParser(parser).parse();
        }

        parser.setMetadata(element, MetadataKey.MAIN_POS, parser.getMetadata(element, MetadataKey.FULL_POS).orElseThrow());
        parser.setMetadata(element, MetadataKey.KEYWORD_POS, atPos);
        parser.setMetadata(element, MetadataKey.NAME_POS, namePos);
        parser.setMetadata(element, MetadataKey.FULL_POS, SourceSpan.between(atPos, parser.previous().pos()));
        values.put(key, element);
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public MetadataElement get(String key) {
        return values.get(key);
    }

    public MetadataObject getObject(String key) {
        if (values.get(key) instanceof MetadataObject object) return object;
        throw new IllegalStateException("Expected object for meta key '%s', got '%s'".formatted(key, values.get(key)));
    }

    public MetadataArray getArray(String key) {
        if (values.get(key) instanceof MetadataArray array) return array;
        throw new IllegalStateException("Expected array for meta key '%s', got '%s'".formatted(key, values.get(key)));
    }

    public String getString(String key) {
        if (values.get(key) instanceof MetadataString(var value)) return value;
        throw new IllegalStateException("Expected string for meta key '%s', got '%s'".formatted(key, values.get(key)));
    }

    public double getNumber(String key) {
        if (values.get(key) instanceof MetadataNumber(double value)) return value;
        throw new IllegalStateException("Expected number for meta key '%s', got '%s'".formatted(key, values.get(key)));
    }

    public boolean getBoolean(String key) {
        if (values.get(key) instanceof MetadataBoolean(boolean value)) return value;
        throw new IllegalStateException("Expected boolean for meta key '%s', got '%s'".formatted(key, values.get(key)));
    }

    public Map<String, MetadataElement> getAll() {
        return Collections.unmodifiableMap(values);
    }
}
