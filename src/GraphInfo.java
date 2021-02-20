import java.util.*;
import java.util.stream.Collectors;

/**
 * Holds a collection of nodes as well as all the parent-child relationships
 * for the graph.
 */
public class GraphInfo {
    
    private Map<Integer, String> nodes;
    private Map<Integer, Integer> parents;
    
    public GraphInfo() {
        nodes = new HashMap<>();
        parents = new HashMap<>();
    }
    
    /**
     * Create a new node.
     * @param name String value of node.
     * @return Node ID
     */
    public int newNode(String name) {
        int i = nodes.size();
        nodes.put(i, name);
        return i;
    }
    
    /**
     * Set a parent-child link between nodes.
     * Nodes can only have one parent, this will overwrite an existing
     * parent on a node.
     * @param parent Parent node ID
     * @param child Child node ID
     */
    public void addLink(int parent, int child) {
        parents.put(child, parent);
    }
    
    /**
     * Remove the parent from a node.
     * @param node Node ID
     * @return Parent the node was linked to or null.
     */
    public Integer unlink(int node) {
        return parents.remove(node);
    }
    
    /**
     * Delete a node and its children.
     * @param node Node ID
     * @return Parent the node was linked to or null.
     */
    public Integer delete(int node) {
        nodes.remove(node);
        Integer r = parents.remove(node);
        List<Integer> children = parents.entrySet()
                                        .stream()
                                        .filter(e -> e.getValue() == node)
                                        .map(Map.Entry::getKey)
                                        .collect(Collectors.toList());
        for (int n : children) {
            delete(n);
        }
        return r;
    }
    
    /**
     * Get the string value of a node.
     * @param node Node ID
     * @return Node string
     */
    public String getNode(int node) {
        return nodes.get(node);
    }
    
    /**
     * Returns non-terminal nodes which do not have children.
     * @return
     */
    public Set<Integer> getIncomplete() {
        Set<Integer> n = nodes.keySet()
                              .stream()
                              .filter(i -> Utils.nonEmpty(nodes.get(i), "<", ">"))
                              .collect(Collectors.toSet());
        n.removeAll(parents.values());
        return n;
    }
    
    /**
     * Get a representation of the graph that can be passed to dot to draw
     * the graph.
     * @param extra If true, displays the node ID next to nodes, and draws a
     * box around the relevant node.
     * @param full Same as extra but will also display IDs on terminal symbols.
     * If true, extra will also be true.
     * @param relevant ID of relevant node.
     * @return Graph string in dot language.
     */
    public String getGraphSpec(boolean extra, boolean full, int relevant) {
        extra |= full;
        StringBuilder builder = new StringBuilder();
        builder.append("graph G {\n");
        
        for (Map.Entry<Integer, String> entry : nodes.entrySet()) {
            String label = extra && (Utils.nonEmpty(entry.getValue(), "<", ">") || full) ? entry.getKey() + ": " + entry.getValue() : entry.getValue();
            String shape = extra && relevant >= 0 && relevant == entry.getKey() ? "box" : "plain";
            builder.append(entry.getKey())
                   .append(" [label=\"")
                   .append(label)
                   .append("\" shape=")
                   .append(shape)
                   .append("]\n");
        }
        
        for (Map.Entry<Integer, Integer> entry : parents.entrySet()) {
            builder.append(entry.getValue())
                   .append(" -- ")
                   .append(entry.getKey())
                   .append('\n');
        }
        
        builder.append("}\n");
        return builder.toString();
    }
    
}
