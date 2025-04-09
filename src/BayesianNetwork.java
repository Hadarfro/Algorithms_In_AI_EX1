

public class BayesianNetwork {
    private final NetworkParser parser;
    private final NetworkStructure structure;
    private final QueryProcessor queryProcessor;
    private final InferenceEngine inferenceEngine;

    public BayesianNetwork(String xmlFileName) throws Exception {
        this.parser = new NetworkParser();
        parser.parse(xmlFileName);

        this.structure = new NetworkStructure(parser.getVariables(), parser.getCPTs());
        this.queryProcessor = new QueryProcessor(structure, parser.getCPTs());
        this.inferenceEngine = new InferenceEngine(structure, parser.getCPTs(), queryProcessor);
    }

    public Result jointProbability(String query) {
        return inferenceEngine.jointProbability(query);
    }

    public Result conditionalProbability(String query, int algorithm) {
        return inferenceEngine.conditionalProbability(query, algorithm);
    }
}