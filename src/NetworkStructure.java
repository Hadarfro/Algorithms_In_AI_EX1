import java.util.*;

public class NetworkStructure {
    private final Map<String, Variable> variables;
    private final Map<String, CPT> cpts;
    private final Map<String, List<String>> childrenMap;
    private List<String> topologicalOrder;

    public NetworkStructure(Map<String, Variable> variables, Map<String, CPT> cpts) {
        this.variables = variables;
        this.cpts = cpts;
        this.childrenMap = new HashMap<>();
        buildChildrenMap();
        buildTopologicalOrder();
    }

    private void buildChildrenMap() {
        for (String var : variables.keySet()) {
            childrenMap.put(var, new ArrayList<>());
        }

        for (CPT cpt : cpts.values()) {
            for (String parent : cpt.getParents()) {
                childrenMap.get(parent).add(cpt.getVariableName());
            }
        }
    }

    private void buildTopologicalOrder() {
        List<String> order = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> tempMarked = new HashSet<>();

        for (String var : variables.keySet()) {
            if (!visited.contains(var)) {
                topologicalSort(var, visited, tempMarked, order);
            }
        }

        Collections.reverse(order);
        this.topologicalOrder = order;
    }

    private void topologicalSort(String var, Set<String> visited, Set<String> tempMarked, List<String> order) {
        if (tempMarked.contains(var)) {
            throw new IllegalStateException("Network contains a cycle");
        }

        if (visited.contains(var)) {
            return;
        }

        tempMarked.add(var);
        for (String child : childrenMap.get(var)) {
            topologicalSort(child, visited, tempMarked, order);
        }
        tempMarked.remove(var);
        visited.add(var);
        order.add(var);
    }

    public List<String> getTopologicalOrder() {
        return topologicalOrder;
    }

    public Map<String, Variable> getVariables(){
        return variables;
    }

    public List<String> getChildren(String variable) {
        return childrenMap.getOrDefault(variable, Collections.emptyList());
    }

    public List<String> getParents(String variable) {
        CPT cpt = cpts.get(variable);
        return cpt != null ? cpt.getParents() : Collections.emptyList();
    }
}