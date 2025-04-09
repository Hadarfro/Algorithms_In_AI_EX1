import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.*;

public class NetworkParser {
    private final Map<String, Variable> variables;
    private final Map<String, CPT> cpts;

    public NetworkParser() {
        this.variables = new HashMap<>();
        this.cpts = new HashMap<>();
    }

    public void parse(String fileName) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new File(fileName));
        doc.getDocumentElement().normalize();

        parseVariables(doc);
        parseCPTs(doc);
    }

    private void parseVariables(Document doc) {
        NodeList variableNodes = doc.getElementsByTagName("VARIABLE");
        for (int i = 0; i < variableNodes.getLength(); i++) {
            Node node = variableNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String name = element.getElementsByTagName("NAME").item(0).getTextContent().trim();
                NodeList outcomeNodes = element.getElementsByTagName("OUTCOME");
                List<String> outcomes = new ArrayList<>(outcomeNodes.getLength());
                for (int j = 0; j < outcomeNodes.getLength(); j++) {
                    outcomes.add(outcomeNodes.item(j).getTextContent().trim());
                }
                variables.put(name, new Variable(name, outcomes));
            }
        }
    }

    private void parseCPTs(Document doc) {
        NodeList definitionNodes = doc.getElementsByTagName("DEFINITION");
        for (int i = 0; i < definitionNodes.getLength(); i++) {
            Node node = definitionNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String variableName = element.getElementsByTagName("FOR").item(0).getTextContent().trim();
                NodeList givenNodes = element.getElementsByTagName("GIVEN");
                List<String> parents = new ArrayList<>(givenNodes.getLength());
                for (int j = 0; j < givenNodes.getLength(); j++) {
                    parents.add(givenNodes.item(j).getTextContent().trim());
                }

                String tableStr = element.getElementsByTagName("TABLE").item(0).getTextContent().trim();
                String[] tableValues = tableStr.split("\\s+");
                double[] table = new double[tableValues.length];
                for (int j = 0; j < tableValues.length; j++) {
                    table[j] = Double.parseDouble(tableValues[j]);
                }
                cpts.put(variableName, new CPT(variableName, parents, table, variables));
            }
        }
    }

    public Map<String, Variable> getVariables() {
        return variables;
    }

    public Map<String, CPT> getCPTs() {
        return cpts;
    }
}