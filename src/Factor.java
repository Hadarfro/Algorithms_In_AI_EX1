import java.util.*;

class Factor {
    private List<String> variables;
    private double[] table;
    private Map<String, Variable> variableObjects;
    private List<Integer> domainSizes;

    public Factor(CPT cpt, Map<String, Variable> variableObjects) {
        this.variables = new ArrayList<>();
        this.variableObjects = variableObjects;

        // Add its parents
        variables.addAll(cpt.getParents());

        // Add the variable
        variables.add(cpt.getVariableName());

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
        int index = 0;
        int multiplier = 1;

        // Loop through the variables in the correct order
        for (int i = variables.size() - 1; i >= 0; i--) {
            String varName = variables.get(i);
            String value = assignment.get(varName);
            Variable var = variableObjects.get(varName);
            index += var.getIndex(value) * multiplier;
            multiplier *= var.getValues().size();
        }

        return table[index];
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
            Map<String, String> assignment = indexToAssignment(i, newVars);

            // Add the restricted variable
            assignment.put(variable, value);

            // Get probability from original table
            newTable[i] = getProbability(assignment);
        }

        return new Factor(newVars, newTable, variableObjects);
    }

    public Factor multiply(Factor other, int[] opCounts) {
        // Determine union of variables from both factors
        Set<String> allVars = new HashSet<>(this.variables);
        allVars.addAll(other.variables);
        List<String> newVars = new ArrayList<>(allVars);
        Collections.reverse(newVars);

        // Find variables that exist in both factors (needs proper alignment)
        List<String> commonVars = new ArrayList<>(this.variables);
        commonVars.retainAll(other.variables);

        // Calculate size of new probability table
        int newSize = 1;
        for (String var : newVars) {
            newSize *= variableObjects.get(var).getValues().size();
        }

        double[] newTable = new double[newSize];

        // Iterate through all possible assignments of the new variables
        for (int i = 0; i < newSize; i++) {
            Map<String, String> assignment = indexToAssignment(i, newVars);

            double prob1 = this.getProbability(filterAssignment(assignment, this.variables));
            double prob2 = other.getProbability(filterAssignment(assignment, other.variables));

            // Multiply probabilities
            newTable[i] = prob1 * prob2;
            opCounts[1]++; // Count one multiplication per entry
        }
        return new Factor(newVars, newTable, variableObjects);
    }

    private Map<String, String> filterAssignment(Map<String, String> fullAssignment, List<String> factorVars) {
        Map<String, String> filtered = new HashMap<>();
        for (String var : factorVars) {
            filtered.put(var, fullAssignment.get(var));
        }
        return filtered;
    }

    private Map<String, String> indexToAssignment(int index, List<String> vars) {
        Map<String, String> assignment = new HashMap<>();
        int remaining = index;

        // Traverse in reverse order so the last variable is the fastest-changing
        List<String> reversedVars = new ArrayList<>(vars);
        Collections.reverse(reversedVars);

        for (String var : reversedVars) {
            Variable varObj = variableObjects.get(var);
            int numValues = varObj.getValues().size();
            int valueIndex = remaining % numValues;
            assignment.put(var, varObj.getValues().get(valueIndex));
            remaining /= numValues;
        }

        return assignment;
    }

    // Generate all consistent assignments based on the shared variables
    private List<Map<String, String>> generateConsistentAssignments(Map<String, String> assignment, Factor other) {
        List<Map<String, String>> assignments = new ArrayList<>();
        assignments.add(new HashMap<>(assignment)); // Start with the current assignment

        // Check for variables missing in either factor
        for (String var : other.variables) {
            if (!assignment.containsKey(var)) {
                List<Map<String, String>> expandedAssignments = new ArrayList<>();

                // Expand all current assignments with possible values of the missing variable
                for (Map<String, String> currentAssignment : assignments) {
                    for (String value : variableObjects.get(var).getValues()) {
                        Map<String, String> newAssignment = new HashMap<>(currentAssignment);
                        newAssignment.put(var, value);
                        expandedAssignments.add(newAssignment);
                    }
                }

                assignments = expandedAssignments;
            }
        }

        return assignments;
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

        // Calculate strides for efficient indexing
        int[] oldStrides = new int[variables.size()];
        int stride = 1;
        for (int i = variables.size() - 1; i >= 0; i--) {
            oldStrides[i] = stride;
            stride *= variableObjects.get(variables.get(i)).getValues().size();
        }

        int[] newStrides = new int[newVars.size()];
        stride = 1;
        for (int i = newVars.size() - 1; i >= 0; i--) {
            newStrides[i] = stride;
            stride *= variableObjects.get(newVars.get(i)).getValues().size();
        }

        // Initialize the new table with zeros
        Arrays.fill(newTable, 0.0);

        // For each assignment to the variables
        for (int oldIndex = 0; oldIndex < table.length; oldIndex++) {
            // Calculate the indices
            int[] assignment = new int[variables.size()];
            int tempIndex = oldIndex;
            for (int i = 0; i < variables.size(); i++) {
                assignment[i] = tempIndex / oldStrides[i];
                tempIndex %= oldStrides[i];
            }

            // Calculate the new index
            int newIndex = 0;
            for (int i = 0, j = 0; i < variables.size(); i++) {
                if (i != varIndex) {
                    newIndex += assignment[i] * newStrides[j];
                    j++;
                }
            }

            // Add the value to the new table
            newTable[newIndex] += table[oldIndex];
            opCounts[0]++; // Count addition
        }

        return new Factor(newVars, newTable, variableObjects);
    }

    public void normalize() {
        double sum = 0;

        // First, calculate the sum of all entries
        for (double value : table) {
            sum += value;
        }

        // Avoid division by zero
        if (sum == 0) {
            throw new ArithmeticException("Cannot normalize factor: total probability is zero.");
        }

        // Then divide each entry by the sum to normalize
        for (int i = 0; i < table.length; i++) {
            table[i] /= sum;
        }
    }

    public void printFactorTable() {
        List<String> variables = new ArrayList<>(this.getVariables()); // Assuming getVariables returns a list of variable names
        List<List<String>> possibleAssignments = generateAllAssignments(variables); // Assuming generateAllAssignments generates all possible variable assignments

        // Print table header
        StringBuilder header = new StringBuilder();
        for (String var : variables) {
            header.append(var).append("\t");
        }
        header.append("Probability");
        System.out.println(header.toString());

        // Print table rows
        for (List<String> assignment : possibleAssignments) {
            Map<String, String> assignmentMap = new HashMap<>();
            for (int i = 0; i < variables.size(); i++) {
                assignmentMap.put(variables.get(i), assignment.get(i));
            }

            // Get the probability for the current assignment
            double probability = this.getProbability(assignmentMap);

            // Print the row
            StringBuilder row = new StringBuilder();
            for (String value : assignment) {
                row.append(value).append("\t");
            }
            row.append(probability);
            System.out.println(row.toString());
        }
    }

    // Helper function to generate all possible assignments for a list of variables
    private List<List<String>> generateAllAssignments(List<String> variables) {
        List<List<String>> assignments = new ArrayList<>();
        int n = variables.size();
        List<String> values = Arrays.asList("T", "F"); // Assuming the values are "T" and "F"

        // Generate all possible assignments
        generateAssignmentsRecursive(assignments, new ArrayList<>(), variables, values, 0);
        return assignments;
    }

    // Recursive helper function for generating assignments
    private void generateAssignmentsRecursive(List<List<String>> assignments, List<String> current, List<String> variables, List<String> values, int index) {
        if (index == variables.size()) {
            assignments.add(new ArrayList<>(current));
            return;
        }

        for (String value : values) {
            current.add(value);
            generateAssignmentsRecursive(assignments, current, variables, values, index + 1);
            current.remove(current.size() - 1);
        }
    }


}