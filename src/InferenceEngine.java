import java.util.*;

public class InferenceEngine {
    private final NetworkStructure network;
    private final Map<String, CPT> cpts;
    private final QueryProcessor queryProcessor;

    public InferenceEngine(NetworkStructure networkStructure,
                           Map<String, CPT> cpts,
                           QueryProcessor queryProcessor) {
        this.network = networkStructure;
        this.cpts = cpts;
        this.queryProcessor = queryProcessor;
    }

    public Result jointProbability(String query) {
        Map<String, String> assignments = queryProcessor.parseAssignments(query);
        double probability = 1.0;
        int multiplications = 0;

        for (String varName : network.getTopologicalOrder()) {
            if (!assignments.containsKey(varName)) {
                continue;
            }

            CPT cpt = cpts.get(varName);
            if (cpt == null) {
                continue;
            }

            Map<String, String> parentAssignments = new HashMap<>(cpt.getParents().size());
            for (String parent : cpt.getParents()) {
                parentAssignments.put(parent, assignments.get(parent));
            }

            double prob = cpt.getProbability(assignments.get(varName), parentAssignments);
            multiplications++;
            probability *= prob;
        }
        return new Result(probability, 0, multiplications - 1);
    }

    public Result conditionalProbability(String query, int algorithm) {
        if (queryProcessor.isDirectlyInCPT(query)) {
            return getFromCPT(query);
        }

        String[] parts = query.split("\\|");
        String queryPart = parts[0].substring(2, parts[0].length());
        String evidencePart = parts.length > 1 ? parts[1].substring(0, parts[1].length() - 1) : "";

        Map<String, String> queryAssignment = queryProcessor.parseAssignments("P(" + queryPart + ")");
        Map<String, String> evidenceAssignments = queryProcessor.parseAssignments("P(" + evidencePart + ")");

        switch (algorithm) {
            case 1: return simpleInference(queryAssignment, evidenceAssignments);
            case 2: return variableElimination(queryAssignment, evidenceAssignments, false);
            case 3: return variableElimination(queryAssignment, evidenceAssignments, true);
            default: throw new IllegalArgumentException("Invalid algorithm: " + algorithm);
        }
    }

    private Result simpleInference(Map<String, String> queryAssignment, Map<String, String> evidenceAssignments) {
        if (queryAssignment.isEmpty()) {
            throw new IllegalArgumentException("queryAssignment is empty, cannot perform inference");
        }

        String queryVar = queryAssignment.keySet().iterator().next();
        String queryVal = queryAssignment.get(queryVar);

        // Find all variables
        Set<String> allVars = new HashSet<>();
        allVars.addAll(network.getVariables().keySet());

        // Remove query and evidence variables to get hidden variables
        allVars.removeAll(queryAssignment.keySet());
        allVars.removeAll(evidenceAssignments.keySet());
        List<String> hiddenVars = new ArrayList<>(allVars);

        // Generate all possible assignments for hidden variables
        List<Map<String, String>> hiddenAssignments = queryProcessor.generateAllAssignments(hiddenVars);

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

            Result jointResult = jointProbability("P(" + queryProcessor.assignmentsToString(fullAssignment) + ")");
            numerator += jointResult.probability;
            additions++;
            multiplications += jointResult.multiplications;
        }

        // Calculate P(evidence)
        for (String queryValue : network.getVariables().get(queryVar).getValues()) {
            // Skip if this is the same as our query value - we already computed it
            if (queryValue.equals(queryVal)) {
                denominator += numerator;
//                additions++;
                continue;
            }

            Map<String, String> currentQueryAssignment = new HashMap<>();
            currentQueryAssignment.put(queryVar, queryValue);

            for (Map<String, String> hiddenAssignment : hiddenAssignments) {
                Map<String, String> fullAssignment = new HashMap<>();
                fullAssignment.putAll(currentQueryAssignment);
                fullAssignment.putAll(evidenceAssignments);
                fullAssignment.putAll(hiddenAssignment);

                Result jointResult = jointProbability("P(" + queryProcessor.assignmentsToString(fullAssignment) + ")");
                denominator += jointResult.probability;
                additions++;
                multiplications += jointResult.multiplications;
            }
        }

        double probability = numerator / denominator;
        // Count the division as an addition in normalization
//        additions++;

        return new Result(probability, additions - 1, multiplications);
    }

    private Result variableElimination(Map<String, String> queryAssignment, Map<String, String> evidenceAssignments, boolean useHeuristic) {
        String queryVar = queryAssignment.keySet().iterator().next();

        int additions = 0;
        int multiplications = 0;
        Map<String, Variable> variables = network.getVariables();

        // Creating initial factors from CPTs
        List<Factor> factors = new ArrayList<>();
        for (CPT cpt : cpts.values()) {
            Factor factor = new Factor(cpt, new HashMap<>(cpt.getVariables()));

            // Restrict factor based on evidence
            for (Map.Entry<String, String> evidence : evidenceAssignments.entrySet()) {
                if (factor.getVariables().contains(evidence.getKey())) {
                    factor = factor.restrict(evidence.getKey(), evidence.getValue());
                }
            }

            // Discard if one-valued (evidence removed all variability)
            if (factor.getSize() > 1) {
                factors.add(factor);
            }
        }

        // Determine hidden variables to eliminate
        Set<String> varsToEliminate = new HashSet<>(network.getVariables().keySet());
        varsToEliminate.remove(queryVar);
        varsToEliminate.removeAll(evidenceAssignments.keySet());

        // Determine elimination order
        List<String> eliminationOrder;
        if (useHeuristic) {
            eliminationOrder = getHeuristicEliminationOrder(factors, new ArrayList<>(varsToEliminate));
        }
        else {
            eliminationOrder = new ArrayList<>(varsToEliminate);
            Collections.sort(eliminationOrder); // ABC order
        }


        // Eliminate variables
        for (String var : eliminationOrder) {
            List<Factor> relevantFactors = new ArrayList<>();
            List<Factor> irrelevantFactors = new ArrayList<>();

            // Separate relevant and irrelevant factors
            for (Factor factor : factors) {
                if (factor.getVariables().contains(var)) {
                    relevantFactors.add(factor);
                }
                else {
                    irrelevantFactors.add(factor);
                }
            }

            // If no relevant factors, skip
            if (relevantFactors.isEmpty()) {
                continue;
            }

            relevantFactors.sort(Comparator.comparingInt(Factor::getSize));

            // Join relevant factors
            Factor product = relevantFactors.get(0);
            for (int i = 1; i < relevantFactors.size(); i++) {
                int[] opCounts = new int[2]; // [additions, multiplications]
                product = product.multiply(relevantFactors.get(i), opCounts);
//                additions += opCounts[0];
                multiplications += opCounts[1];
            }

            // Eliminate (sum out) variable
            int[] opCountsSum = new int[2];
            Factor summedOut = product.sumOut(var, opCountsSum);
            additions += opCountsSum[0];
//            multiplications += opCountsSum[1];

            // Discard one-valued factors
            if (summedOut.getSize() > 1) {
                irrelevantFactors.add(summedOut);
            }

            factors = irrelevantFactors;

        }

        if (factors.isEmpty()) {
            throw new RuntimeException("No remaining factors after elimination");
        }

        // Join all remaining factors
        Factor finalFactor = factors.get(0);
        for (int i = 1; i < factors.size(); i++) {
            int[] opCounts = new int[2];
            finalFactor = finalFactor.multiply(factors.get(i), opCounts);
//            additions += opCounts[0];
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
        finalFactor.normalize();

        double probability = numerator / denominator;
        additions++;

        return new Result(probability, additions, multiplications);
    }


    private List<String> getHeuristicEliminationOrder(List<Factor> factors, List<String> varsToEliminate) {
        // Implement min-weight heuristic for variable elimination order
        List<String> order = new ArrayList<>();
        Set<String> remainingVars = new HashSet<>(varsToEliminate);
        Map<String, Variable> variables = network.getVariables();

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
        Map<String, Variable> variables = network.getVariables();

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
}