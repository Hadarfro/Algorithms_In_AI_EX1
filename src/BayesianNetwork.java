import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.*;

class BayesianNetwork {
    private Map<String, Variable> variables;
    private Map<String, CPT> cpts;

    public BayesianNetwork(String xmlFileName) throws Exception {
        variables = new HashMap<>();
        cpts = new HashMap<>();
        parseXML(xmlFileName);
    }

    private void parseXML(String fileName) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new File(fileName));
        doc.getDocumentElement().normalize();

        // Parse variables
        NodeList variableNodes = doc.getElementsByTagName("VARIABLE");
        for (int i = 0; i < variableNodes.getLength(); i++) {
            Node node = variableNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String name = element.getElementsByTagName("NAME").item(0).getTextContent();
                NodeList outcomeNodes = element.getElementsByTagName("OUTCOME");
                List<String> outcomes = new ArrayList<>();
                for (int j = 0; j < outcomeNodes.getLength(); j++) {
                    outcomes.add(outcomeNodes.item(j).getTextContent());
                }
                variables.put(name, new Variable(name, outcomes));
            }
        }

        // Parse CPTs
        NodeList definitionNodes = doc.getElementsByTagName("DEFINITION");
        for (int i = 0; i < definitionNodes.getLength(); i++) {
            Node node = definitionNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String variableName = element.getElementsByTagName("FOR").item(0).getTextContent();
                List<String> parents = new ArrayList<>();
                NodeList givenNodes = element.getElementsByTagName("GIVEN");
                for (int j = 0; j < givenNodes.getLength(); j++) {
                    parents.add(givenNodes.item(j).getTextContent());
                }
                String tableStr = element.getElementsByTagName("TABLE").item(0).getTextContent();
                String[] tableValues = tableStr.trim().split("\\s+");
                double[] table = new double[tableValues.length];
                for (int j = 0; j < tableValues.length; j++) {
                    table[j] = Double.parseDouble(tableValues[j]);
                }
                cpts.put(variableName, new CPT(variableName, parents, table, variables));
            }
        }
    }

    public Result jointProbability(String query) {
        // Extract variable assignments from query
        Map<String, String> assignments = parseAssignments(query);

        double probability = 1.0;
        int multiplications = 0;

        // Calculate joint probability using the chain rule: P(X1,X2,...,Xn) = ∏ P(Xi|Parents(Xi))
        for (CPT cpt : cpts.values()) {
            String varName = cpt.getVariableName();
            String valueAssigned = assignments.get(varName);

            // Get probability from CPT
            Map<String, String> parentAssignments = new HashMap<>();
            for (String parent : cpt.getParents()) {
                parentAssignments.put(parent, assignments.get(parent));
            }

            double prob = cpt.getProbability(valueAssigned, parentAssignments);

            // Multiply probabilities (except for the first one)
            if (multiplications > 0) {
                multiplications++;
            }
            probability *= prob;
        }

        return new Result(probability, 0, multiplications);
    }

    public Result conditionalProbability(String query, int algorithm) {
        // First check if this is directly available in a CPT
        if (isDirectlyInCPT(query)) {
            return getFromCPT(query);
        }

        // Parse the query: P(Q=q|E1=e1,E2=e2,...,Ek=ek)
        String[] parts = query.split("\\|");
        String queryPart = parts[0].substring(2, parts[0].length() - 1); // Remove P( and )
        String evidencePart = parts.length > 1 ? parts[1].substring(0, parts[1].length() - 1) : "";

        Map<String, String> queryAssignment = parseAssignments("P(" + queryPart + ")");
        Map<String, String> evidenceAssignments = parseAssignments("P(" + evidencePart + ")");

        // Choose algorithm
        switch (algorithm) {
            case 1:
                return simpleInference(queryAssignment, evidenceAssignments);
            case 2:
                return variableElimination(queryAssignment, evidenceAssignments, false);
            case 3:
                return variableElimination(queryAssignment, evidenceAssignments, true);
            default:
                throw new IllegalArgumentException("Invalid algorithm: " + algorithm);
        }
    }

    private boolean isDirectlyInCPT(String query) {
        // Parse the query
        String[] parts = query.split("\\|");
        if (parts.length != 2) return false;

        String queryPart = parts[0].trim();
        String evidencePart = parts[1].trim();

        // Extract query variable and value
        String queryStr = queryPart.substring(2, queryPart.length() - 1); // Remove P( and )
        String[] queryVarVal = queryStr.split("=");
        String queryVar = queryVarVal[0].trim();
        String queryVal = queryVarVal[1].trim();

        // Extract evidence variables and values
        String[] evidenceStrs = evidencePart.substring(0, evidencePart.length() - 1).split(",");
        Map<String, String> evidenceMap = new HashMap<>();
        for (String evidence : evidenceStrs) {
            String[] evidenceVarVal = evidence.split("=");
            evidenceMap.put(evidenceVarVal[0].trim(), evidenceVarVal[1].trim());
        }

        // Check if query matches a CPT
        CPT cpt = cpts.get(queryVar);
        if (cpt == null) return false;

        // Check if parents match evidence exactly
        List<String> parents = cpt.getParents();
        if (parents.size() != evidenceMap.size()) return false;

        for (String parent : parents) {
            if (!evidenceMap.containsKey(parent)) return false;
        }

        // All parents are in evidence, and all evidence variables are parents
        return evidenceMap.keySet().containsAll(parents);
    }

    private Result getFromCPT(String query) {
        // Parse the query
        String[] parts = query.split("\\|");
        String queryPart = parts[0].trim();
        String evidencePart = parts[1].trim();

        // Extract query variable and value
        String queryStr = queryPart.substring(2, queryPart.length() - 1); // Remove P( and )
        String[] queryVarVal = queryStr.split("=");
        String queryVar = queryVarVal[0].trim();
        String queryVal = queryVarVal[1].trim();

        // Extract evidence variables and values
        String[] evidenceStrs = evidencePart.substring(0, evidencePart.length() - 1).split(",");
        Map<String, String> evidenceMap = new HashMap<>();
        for (String evidence : evidenceStrs) {
            String[] evidenceVarVal = evidence.split("=");
            evidenceMap.put(evidenceVarVal[0].trim(), evidenceVarVal[1].trim());
        }

        // Get probability directly from CPT
        CPT cpt = cpts.get(queryVar);
        double probability = cpt.getProbability(queryVal, evidenceMap);

        return new Result(probability, 0, 0);
    }

    private Result simpleInference(Map<String, String> queryAssignment, Map<String, String> evidenceAssignments) {
        String queryVar = queryAssignment.keySet().iterator().next();
        String queryVal = queryAssignment.get(queryVar);

        // Find all variables
        Set<String> allVars = new HashSet<>();
        allVars.addAll(variables.keySet());

        // Remove query and evidence variables to get hidden variables
        allVars.removeAll(queryAssignment.keySet());
        allVars.removeAll(evidenceAssignments.keySet());
        List<String> hiddenVars = new ArrayList<>(allVars);

        // Generate all possible assignments for hidden variables
        List<Map<String, String>> hiddenAssignments = generateAllAssignments(hiddenVars);

        double numerator = 0;
        double denominator = 0;
        int additions = 0;
        int multiplications = 0;

        // Calculate P(query, evidence)
        for (Map<String, String> hiddenAssignment : hiddenAssignments) {
            Map<String, String> fullAssignment = new HashMap<>();
            fullAssignment.putAll(queryAssignment);
            fullAssignment.putAll(evidenceAssignments);
            fullAssignment.putAll(hiddenAssignment);

            Result jointResult = jointProbability("P(" + assignmentsToString(fullAssignment) + ")");
            numerator += jointResult.probability;
            if (additions > 0) {
                additions++;
            }
            multiplications += jointResult.multiplications;
        }

        // Calculate P(evidence)
        for (String queryValue : variables.get(queryVar).getValues()) {
            // Skip if this is the same as our query value - we already computed it
            if (queryValue.equals(queryVal)) {
                denominator += numerator;
                if (additions > 0) {
                    additions++;
                }
                continue;
            }

            Map<String, String> currentQueryAssignment = new HashMap<>();
            currentQueryAssignment.put(queryVar, queryValue);

            for (Map<String, String> hiddenAssignment : hiddenAssignments) {
                Map<String, String> fullAssignment = new HashMap<>();
                fullAssignment.putAll(currentQueryAssignment);
                fullAssignment.putAll(evidenceAssignments);
                fullAssignment.putAll(hiddenAssignment);

                Result jointResult = jointProbability("P(" + assignmentsToString(fullAssignment) + ")");
                denominator += jointResult.probability;
                if (additions > 0) {
                    additions++;
                }
                multiplications += jointResult.multiplications;
            }
        }

        double probability = numerator / denominator;
        // Count the division as an addition in normalization
        additions++;

        return new Result(probability, additions, multiplications);
    }

    private Result variableElimination(Map<String, String> queryAssignment, Map<String, String> evidenceAssignments, boolean useHeuristic) {
        String queryVar = queryAssignment.keySet().iterator().next();

        // Create initial factors from CPTs
        List<Factor> factors = new ArrayList<>();
        for (CPT cpt : cpts.values()) {
            factors.add(new Factor(cpt, variables));
        }

        // Evidence factors
        for (Map.Entry<String, String> entry : evidenceAssignments.entrySet()) {
            String var = entry.getKey();
            String val = entry.getValue();

            List<Factor> newFactors = new ArrayList<>();
            for (Factor factor : factors) {
                if (factor.getVariables().contains(var)) {
                    newFactors.add(factor.restrict(var, val));
                } else {
                    newFactors.add(factor);
                }
            }
            factors = newFactors;
        }

        // Find variables to eliminate
        Set<String> varsToEliminate = new HashSet<>();
        for (Factor factor : factors) {
            varsToEliminate.addAll(factor.getVariables());
        }
        varsToEliminate.remove(queryVar);
        varsToEliminate.removeAll(evidenceAssignments.keySet());

        // Determine elimination order
        List<String> eliminationOrder;
        if (useHeuristic) {
            eliminationOrder = getHeuristicEliminationOrder(factors, new ArrayList<>(varsToEliminate));
        } else {
            eliminationOrder = new ArrayList<>(varsToEliminate);
            Collections.sort(eliminationOrder); // ABC order
        }

        // Counters for operations
        int additions = 0;
        int multiplications = 0;

        // Variable elimination
        for (String var : eliminationOrder) {
            // Find factors involving this variable
            List<Factor> relevantFactors = new ArrayList<>();
            List<Factor> irrelevantFactors = new ArrayList<>();

            for (Factor factor : factors) {
                if (factor.getVariables().contains(var)) {
                    relevantFactors.add(factor);
                } else {
                    irrelevantFactors.add(factor);
                }
            }

            // Sort relevant factors by size
            Collections.sort(relevantFactors, Comparator.comparing(Factor::getSize).thenComparing(factor -> {
                int sum = 0;
                for (String v : factor.getVariables()) {
                    for (char c : v.toCharArray()) {
                        sum += (int) c;
                    }
                }
                return sum;
            }));

            // Multiply relevant factors
            Factor product = relevantFactors.get(0);
            for (int i = 1; i < relevantFactors.size(); i++) {
                Factor next = relevantFactors.get(i);
                int[] opCounts = new int[2]; // [additions, multiplications]
                product = product.multiply(next, opCounts);
                additions += opCounts[0];
                multiplications += opCounts[1];
            }

            // Sum out the variable
            int[] opCounts = new int[2]; // [additions, multiplications]
            Factor summedOut = product.sumOut(var, opCounts);
            additions += opCounts[0];
            multiplications += opCounts[1];

            // Update factors
            factors = new ArrayList<>(irrelevantFactors);
            factors.add(summedOut);
        }

        // Multiply remaining factors
        Factor finalFactor = factors.get(0);
        for (int i = 1; i < factors.size(); i++) {
            int[] opCounts = new int[2]; // [additions, multiplications]
            finalFactor = finalFactor.multiply(factors.get(i), opCounts);
            additions += opCounts[0];
            multiplications += opCounts[1];
        }

        // Normalize
        String queryVal = queryAssignment.get(queryVar);
        Map<String, String> assignment = new HashMap<>();
        assignment.put(queryVar, queryVal);

        double numerator = finalFactor.getProbability(assignment);
        double denominator = 0;

        for (String value : variables.get(queryVar).getValues()) {
            Map<String, String> valueAssignment = new HashMap<>();
            valueAssignment.put(queryVar, value);
            denominator += finalFactor.getProbability(valueAssignment);
            if (value != variables.get(queryVar).getValues().get(0)) {
                additions++;
            }
        }

        double probability = numerator / denominator;
        // Count division as an addition
        additions++;

        return new Result(probability, additions, multiplications);
    }

    private List<String> getHeuristicEliminationOrder(List<Factor> factors, List<String> varsToEliminate) {
        // Implement min-weight heuristic for variable elimination order
        List<String> order = new ArrayList<>();
        Set<String> remainingVars = new HashSet<>(varsToEliminate);

        while (!remainingVars.isEmpty()) {
            String bestVar = null;
            int bestWeight = Integer.MAX_VALUE;

            for (String var : remainingVars) {
                int weight = computeEliminationWeight(var, factors);
                if (weight < bestWeight) {
                    bestWeight = weight;
                    bestVar = var;
                }
            }

            order.add(bestVar);
            remainingVars.remove(bestVar);

            // Update factors as if we've eliminated this variable
            List<Factor> relevantFactors = new ArrayList<>();
            List<Factor> irrelevantFactors = new ArrayList<>();

            for (Factor factor : factors) {
                if (factor.getVariables().contains(bestVar)) {
                    relevantFactors.add(factor);
                } else {
                    irrelevantFactors.add(factor);
                }
            }

            // Create a new factor that would result from elimination
            Set<String> newFactorVars = new HashSet<>();
            for (Factor factor : relevantFactors) {
                newFactorVars.addAll(factor.getVariables());
            }
            newFactorVars.remove(bestVar);

            // Replace relevant factors with new combined factor for heuristic purposes
            factors = new ArrayList<>(irrelevantFactors);
            if (!newFactorVars.isEmpty()) {
                factors.add(new Factor(new ArrayList<>(newFactorVars), new double[1], variables));
            }
        }

        return order;
    }

    private int computeEliminationWeight(String var, List<Factor> factors) {
        // Find all factors involving this variable
        List<Factor> relevantFactors = new ArrayList<>();

        for (Factor factor : factors) {
            if (factor.getVariables().contains(var)) {
                relevantFactors.add(factor);
            }
        }

        // If no relevant factors, weight is 0
        if (relevantFactors.isEmpty()) {
            return 0;
        }

        // Find all variables in these factors
        Set<String> involvedVars = new HashSet<>();
        for (Factor factor : relevantFactors) {
            involvedVars.addAll(factor.getVariables());
        }

        // Calculate weight as product of domain sizes
        int weight = 1;
        for (String involvedVar : involvedVars) {
            weight *= variables.get(involvedVar).getValues().size();
        }

        return weight;
    }

    private Map<String, String> parseAssignments(String query) {
        Map<String, String> assignments = new HashMap<>();

        // Extract the assignments part: P(X=x,Y=y) -> X=x,Y=y
        String assignmentsStr = query.substring(2, query.length() - 1);

        if (assignmentsStr.isEmpty()) {
            return assignments;
        }

        // Split by comma and extract variable-value pairs
        String[] pairs = assignmentsStr.split(",");
        for (String pair : pairs) {
            String[] varVal = pair.split("=");
            if (varVal.length == 2) {
                assignments.put(varVal[0].trim(), varVal[1].trim());
            }
        }

        return assignments;
    }

    private String assignmentsToString(Map<String, String> assignments) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, String> entry : assignments.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }

        return sb.toString();
    }

    private List<Map<String, String>> generateAllAssignments(List<String> vars) {
        List<Map<String, String>> allAssignments = new ArrayList<>();

        if (vars.isEmpty()) {
            allAssignments.add(new HashMap<>());
            return allAssignments;
        }

        // Take first variable
        String firstVar = vars.get(0);
        List<String> remainingVars = vars.subList(1, vars.size());

        // Generate all assignments for remaining variables
        List<Map<String, String>> subAssignments = generateAllAssignments(remainingVars);

        // Add each possible value of first variable to each assignment
        for (String value : variables.get(firstVar).getValues()) {
            for (Map<String, String> assignment : subAssignments) {
                Map<String, String> newAssignment = new HashMap<>(assignment);
                newAssignment.put(firstVar, value);
                allAssignments.add(newAssignment);
            }
        }

        return allAssignments;
    }
}