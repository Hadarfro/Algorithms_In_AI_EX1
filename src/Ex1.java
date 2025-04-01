import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;


public class Ex1 {
    public static void main(String[] args) {
        try {
            // Read input file
            BufferedReader reader = new BufferedReader(new FileReader("input.txt"));
            String xmlFileName = reader.readLine();
            List<String> queries = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    queries.add(line);
                }
            }
            reader.close();

            // Parse XML and build the Bayesian network
            BayesianNetwork network = new BayesianNetwork(xmlFileName);

            // Process queries and write output
            BufferedWriter writer = new BufferedWriter(new FileWriter("output.txt"));
            for (String query : queries) {
                Result result = processQuery(query, network);
                DecimalFormat df = new DecimalFormat("0.00000");
                writer.write(df.format(result.probability) + "," +
                        result.additions + "," +
                        result.multiplications);
                writer.newLine();
            }
            writer.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Result processQuery(String query, BayesianNetwork network) {
        if (query.contains("|")) {
            // Conditional probability query with algorithm
            String[] parts = query.split(",");
            int algorithm = Integer.parseInt(parts[1]);
            String conditionalQuery = parts[0];

            return network.conditionalProbability(conditionalQuery, algorithm);
        }
        else {
            // Joint probability query
            return network.jointProbability(query);
        }
    }
}