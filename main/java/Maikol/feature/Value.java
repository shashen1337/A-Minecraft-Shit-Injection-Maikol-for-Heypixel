package Maikol.feature;

public class Value<T> {
    private final String name;
    private T value;

    public Value(String name, T value) {
        this.name = name;
        this.value = value;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public String getName() {
        return name;
    }
}