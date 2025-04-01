import java.util.*;

class Factor {
    private List<String> variables;
    private double[] table;
    private Map<String, Variable> variableObjects;
    private List<Integer> domainSizes;

    public Factor(CPT cpt, Map<String, Variable> variableObjects) {
        this.variables = new ArrayList<>();
        this.variableObjects = variableObjects;

        // Add the variable
        variables.add(cpt.getVariableName());

        // Add its parents
        variables.addAll(cpt.getParents());

        // Copy the table
        this.table = new double[cpt.getTable().length];
        System.arraycopy(cpt.getTable(), 0, this.table, 0, cpt.getTable().length);

        // Calculate domain sizes
        this.domainSizes = new ArrayList<>();
        for (String var : variables) {
            domainSizes.add(variableObjects.get(var).getValues().size());
        }
    }

    public Factor(List<String> variables, double[] table, Map<String, Variable> variableObjects) {
        this.variables = new ArrayList<>(variables);
        this.table = table;
        this.variableObjects = variableObjects;

        // Calculate domain sizes
        this.domainSizes = new ArrayList<>();
        for (String var : variables) {
            domainSizes.add(variableObjects.get(var).getValues().size());
        }
    }

    public List<String> getVariables() {
        return variables;
    }

    public int getSize() {
        return table.length;
    }

    public double getProbability(Map<String, String> assignment) {
        // If assignment doesn't include all variables, return 0
        for (String var : variables) {
            if (!assignment.containsKey(var)) {
                return 0;
            }
        }

        int index = calculateIndex(assignment);
        return table[index];
    }

    private int calculateIndex(Map<String, String> assignment) {
        int index = 0;
        int multiplier = 1;

        for (int i = 0; i < variables.size(); i++) {
            String var = variables.get(i);
            Variable varObj = variableObjects.get(var);
            index += varObj.getIndex(assignment.get(var)) * multiplier;
            multiplier *= varObj.getValues().size();
        }

        return index;
    }

    public Factor restrict(String variable, String value) {
        if (!variables.contains(variable)) {
            return this;
        }

        // Find all variables except the restricted one
        List<String> newVars = new ArrayList<>(variables);
        int varIndex = newVars.indexOf(variable);
        newVars.remove(varIndex);

        // Calculate size of new table
        int newSize = 1;
        for (String var : newVars) {
            newSize *= variableObjects.get(var).getValues().size();
        }

        double[] newTable = new double[newSize];

        // Fill in the new table
        for (int i = 0; i < newTable.length; i++) {
            // Convert i to an assignment for the new variables
            Map<String, String> assignment = new HashMap<>();
            int temp = i;
            int mult = 1;

            for (int j = 0; j < newVars.size(); j++) {
                String var = newVars.get(j);
                Variable varObj = variableObjects.get(var);
                int valueIndex = (temp / mult) % varObj.getValues().size();
                assignment.put(var, varObj.getValues().get(valueIndex));
                mult *= varObj.getValues().size();
            }

            // Add the restricted variable
            assignment.put(variable, value);

            // Get probability from original table
            newTable[i] = getProbability(assignment);
        }

        return new Factor(newVars, newTable, variableObjects);
    }

    public Factor multiply(Factor other, int[] opCounts) {
        // Find all variables in the result
        Set<String> resultVars = new HashSet<>(variables);
        resultVars.addAll(other.variables);
        List<String> newVars = new ArrayList<>(resultVars);

        // Calculate size of new table
        int newSize = 1;
        for (String var : newVars) {
            newSize *= variableObjects.get(var).getValues().size();
        }

        double[] newTable = new double[newSize];

        // Fill in the new table
        for (int i = 0; i < newTable.length; i++) {
            // Convert i to an assignment for all variables
            Map<String, String> assignment = new HashMap<>();
            int temp = i;
            int mult = 1;

            for (int j = 0; j < newVars.size(); j++) {
                String var = newVars.get(j);
                Variable varObj = variableObjects.get(var);
                int valueIndex = (temp / mult) % varObj.getValues().size();
                assignment.put(var, varObj.getValues().get(valueIndex));
                mult *= varObj.getValues().size();
            }

            // Get probabilities from both factors and multiply
            double prob1 = getProbability(assignment);
            double prob2 = other.getProbability(assignment);
            newTable[i] = prob1 * prob2;
            opCounts[1]++; // Count multiplication
        }

        return new Factor(newVars, newTable, variableObjects);
    }

    public Factor sumOut(String variable, int[] opCounts) {
        if (!variables.contains(variable)) {
            return this;
        }

        // Find all variables except the summed out one
        List<String> newVars = new ArrayList<>(variables);
        int varIndex = newVars.indexOf(variable);
        newVars.remove(varIndex);

        // If no variables left, return a factor with a single value that sums to 1
        if (newVars.isEmpty()) {
            double sum = 0;
            for (double value : table) {
                sum += value;
                if (sum > value) {
                    opCounts[0]++; // Count addition
                }
            }
            return new Factor(newVars, new double[]{sum}, variableObjects);
        }

        // Calculate size of new table
        int newSize = 1;
        for (String var : newVars) {
            newSize *= variableObjects.get(var).getValues().size();
        }

        double[] newTable = new double[newSize];

        // Variable to sum out
        Variable varObj = variableObjects.get(variable);
        int varDomainSize;

    }
}