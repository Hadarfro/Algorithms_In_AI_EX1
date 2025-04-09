import java.util.*;

public class QueryProcessor {
    private final NetworkStructure networkStructure;
    private final Map<String, CPT> cpts;

    public QueryProcessor(NetworkStructure networkStructure, Map<String, CPT> cpts) {
        this.networkStructure = networkStructure;
        this.cpts = cpts;
    }

    public Map<String, String> parseAssignments(String query) {
        Map<String, String> assignments = new HashMap<>();
        String assignmentsStr = query.substring(2, query.length() - 1);

        if (assignmentsStr.isEmpty()) {
            return assignments;
        }

        String[] pairs = assignmentsStr.split(",");
        for (String pair : pairs) {
            String[] varVal = pair.split("=");
            if (varVal.length == 2) {
                assignments.put(varVal[0].trim(), varVal[1].trim());
            }
        }

        return assignments;
    }

    public String assignmentsToString(Map<String, String> assignments) {
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

    public boolean isDirectlyInCPT(String query) {
        String[] parts = query.split("\\|");
        if (parts.length != 2) return false;

        String queryPart = parts[0].trim();
        String evidencePart = parts[1].trim();

        String queryStr = queryPart.substring(2, queryPart.length() - 1);
        String[] queryVarVal = queryStr.split("=");
        String queryVar = queryVarVal[0].trim();

        String[] evidenceStrs = evidencePart.substring(0, evidencePart.length() - 1).split(",");
        Map<String, String> evidenceMap = new HashMap<>();
        for (String evidence : evidenceStrs) {
            String[] evidenceVarVal = evidence.split("=");
            evidenceMap.put(evidenceVarVal[0].trim(), evidenceVarVal[1].trim());
        }

        CPT cpt = cpts.get(queryVar);
        if (cpt == null) return false;

        List<String> parents = cpt.getParents();
        if (parents.size() != evidenceMap.size()) return false;

        return evidenceMap.keySet().containsAll(parents);
    }

    public List<Map<String, String>> generateAllAssignments(List<String> vars) {
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
        for (String value : networkStructure.getVariables().get(firstVar).getValues()) {
            for (Map<String, String> assignment : subAssignments) {
                Map<String, String> newAssignment = new HashMap<>(assignment);
                newAssignment.put(firstVar, value);
                allAssignments.add(newAssignment);
            }
        }

        return allAssignments;
    }
}