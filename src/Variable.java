import java.util.List;

class Variable {
    private String name;
    private List<String> values;

    public Variable(String name, List<String> values) {
        this.name = name;
        this.values = values;
    }

    public String getName() {
        return name;
    }

    public List<String> getValues() {
        return values;
    }

    public int getIndex(String value) {
        return values.indexOf(value);
    }
}