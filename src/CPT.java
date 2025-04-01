import java.util.List;
import java.util.Map;

class CPT {
    private String variableName;
    private List<String> parents;
    private double[] table;
    private Map<String, Variable> variables;

    public CPT(String variableName, List<String> parents, double[] table, Map<String, Variable> variables) {
        this.variableName = variableName;
        this.parents = parents;
        this.table = table;
        this.variables = variables;
    }

    public String getVariableName() {
        return variableName;
    }

    public List<String> getParents() {
        return parents;
    }

    public double[] getTable() {
        return table;
    }

    public double getProbability(String value, Map<String, String> parentValues) {
        int index = calculateIndex(value, parentValues);
        return table[index];
    }

    private int calculateIndex(String value, Map<String, String> parentValues) {
        int index = 0;
        int multiplier = 1;

        // Start with the variable itself as the fastest-changing index
        Variable var = variables.get(variableName);
        index += var.getIndex(value) * multiplier;
        multiplier *= var.getValues().size();

        // Add parent indices in reverse order (since the table is ordered with parents first)
        for (int i = parents.size() - 1; i >= 0; i--) {
            String parent = parents.get(i);
            Variable parentVar = variables.get(parent);
            index += parentVar.getIndex(parentValues.get(parent)) * multiplier;
            multiplier *= parentVar.getValues().size();
        }

        return index;
    }
}