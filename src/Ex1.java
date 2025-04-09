import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.DecimalFormat;
import java.util.*;


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
            // Split on the last comma to correctly handle conditional queries
            int lastCommaIndex = query.lastIndexOf(",");

            if (lastCommaIndex == -1) {
                throw new IllegalArgumentException("Invalid query format: " + query);
            }

            // Extract parts correctly
            String conditionalQuery = query.substring(0, lastCommaIndex);
            String algorithmString = query.substring(lastCommaIndex + 1).trim();


            try {
                int algorithm = Integer.parseInt(algorithmString);
                return network.conditionalProbability(conditionalQuery, algorithm);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Algorithm must be a valid integer: " + algorithmString);
            }
        } else {
            // Joint probability query
            return network.jointProbability(query);
        }
    }
}