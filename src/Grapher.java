import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Grapher extends Application {
    
    private Stage stage;
    private Scene scene;
    
    private BorderPane main;
    
    private HBox dotInfo;
    private Label dotLabel;
    private TextField dotPath;
    
    private HBox defArea;
    private TextArea count;
    private TextArea defs;
    private ProductionRuleReader ruleReader;
    
    private TextField input;
    
    private ScrollPane view;
    private ImageView image;
    
    public static final int DEFAULT = 0;
    public static final int SELECT = 1;
    private int state = DEFAULT;
    
    private GraphInfo graphInfo;
    private String originalRules;
    private Rule selecting;
    private int parent;
    private Deque<Integer> relevant = new LinkedList<>();
    
    private Map<String, FileChooser.ExtensionFilter> fileTypes;
    
    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("ProductionGrapher");
        
        initView();
        initEvents();
        
        stage.setScene(scene);
        stage.show();
    }
    
    private void initView() {
        main = new BorderPane();
        scene = new Scene(main);
        
        loadExtensions();
        
        // Place to enter the path to the graph-drawing tool
        dotInfo = new HBox(10);
        dotLabel = new Label("Path to dot:");
        dotPath = new TextField(getDotPath());
        dotInfo.getChildren().addAll(dotLabel, dotPath);
        main.setTop(dotInfo);
        
        // Where the production rules are entered
        defArea = new HBox();
        count = new TextArea();
        count.setFont(new Font("Courier New", 12));
        count.setPrefColumnCount(3);
        count.setEditable(false);
        defs = new TextArea();
        defs.setFont(new Font("Courier New", 12));
        count.prefHeightProperty().bind(defs.heightProperty());
        count.scrollTopProperty().bindBidirectional(defs.scrollTopProperty());
        defArea.getChildren().addAll(count, defs);
        main.setLeft(defArea);
        
        // Where commands are entered
        input = new TextField();
        input.setFont(new Font("Courier New", 12));
        main.setBottom(input);
        
        // Where the graph is shown
        view = new ScrollPane();
        view.setMinWidth(300);
        view.setPrefHeight(300);
        image = new ImageView();
        view.setContent(image);
        main.setCenter(view);
    }
    
    /**
     * Return the content of dot.txt if it exists or else the empty string.
     * @return Default value of dot text box.
     */
    private String getDotPath() {
        // Load path from parameter
        Map<String, String> named = getParameters().getNamed();
        if (named.containsKey("dot")) return named.get("dot");
        // Load path from file
        File file = new File("dot.txt");
        if (file.exists()) {
            try {
                return new String(Files.readAllBytes(file.toPath())).trim();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return "";
    }
    
    private void loadExtensions() {
        fileTypes = new HashMap<>();
        fileTypes.put(".png", new FileChooser.ExtensionFilter("PNG Image", "*.png"));
        fileTypes.put(".jpeg", new FileChooser.ExtensionFilter("JPEG Image", "*.jpeg"));
        fileTypes.put(".gif", new FileChooser.ExtensionFilter("GIF", "*.gif"));
        fileTypes.put(".bmp", new FileChooser.ExtensionFilter("Windows Bitmap", "*.bmp"));
        fileTypes.put(".svg", new FileChooser.ExtensionFilter("SVG", "*.svg"));
    }
    
    private void initEvents() {
        ruleReader = new ProductionRuleReader();
        graphInfo = new GraphInfo();
        relevant = new LinkedList<>();
        
        // Show line counts in the sidebar for production rules
        defs.textProperty().addListener((observable, oldValue, newValue) -> {
            if (state != DEFAULT) return;
            List<Integer> lines = ruleReader.fromString(newValue)
                                            .stream()
                                            .filter(Objects::nonNull)
                                            .map(Rule::getLine)
                                            .collect(Collectors.toList());
            updateCount(lines);
        });
        
        // All possible commands
        input.setOnKeyPressed(event -> {
            // Quick: right arrow selects last option
            if (event.getCode() == KeyCode.RIGHT) {
                if (state == SELECT && input.getText().isEmpty()) {
                    makeSelection(selecting.getParts().size());
                    redraw(true);
                }
            }
            // Quick: left arrow select first option
            else if (event.getCode() == KeyCode.LEFT) {
                if (state == SELECT && input.getText().isEmpty()) {
                    makeSelection(1);
                    redraw(true);
                }
            }
            // Up arrow cancels selection or moves to next relevant node
            else if (event.getCode() == KeyCode.UP) {
                if (state == SELECT) {
                    makeSelection(-1);
                }
                else {
                    if (relevant.size() > 0) {
                        relevant.removeFirst();
                    }
                    redraw(true);
                }
            }
            // Tab tries to quick-select option
            else if (event.getCode() == KeyCode.TAB) {
                event.consume();
                String text = input.getText().trim();
                if (state == SELECT && !text.isEmpty()) {
                    List<Integer> options = new LinkedList<>();
                    for (int i = 0; i < selecting.getParts().size(); i++) {
                        if (matchesSearch(text, selecting.getParts().get(i))) {
                            options.add(i);
                        }
                        // Old method of searching
//                        if (getRuleParts(selecting.getParts().get(i)).anyMatch(s -> s.equals(text))) {
//                            options.add(i);
//                        }
                    }
                    if (options.size() == 1) {
                        input.setText("");
                        makeSelection(options.get(0) + 1);
                        redraw(true);
                    }
                }
                else if (state == DEFAULT && !text.isEmpty()) {
                    input.setText("");
                    simplePath(getRelevant(), text);
                }
            }
            else if (event.getCode() == KeyCode.DOWN) {
                String text = input.getText().trim().toLowerCase();
                String[] parts = text.split(" +");
                // If no command in box, down arrow selects relevant node.
                // If making a selection it selects the last option.
                if (text.isEmpty()) {
                    if (state == DEFAULT) {
                        startSelection(getRelevant());
                    }
                    else if (state == SELECT) {
                        makeSelection(selecting.getParts().size());
                    }
                    redraw(true);
                }
                // Find simple path from relevant node to specified value
                else {
                    event.consume();
                    input.setText("");
                    simplePath(getRelevant(), String.join(" ", parts));
                }
            }
            else if (event.getCode() == KeyCode.ENTER) {
                event.consume();
                String text = input.getText().trim().toLowerCase();
                String[] parts = text.split(" +");
                input.setText("");
                if (text.isEmpty()) return;
    
                if (state == SELECT) {
                    // Minus will cancel selection
                    if (text.startsWith("-")) {
                        // Cancel select
                        makeSelection(-1);
                        return;
                    }
                    // Select node
                    int line = Integer.parseInt(text);
                    makeSelection(line);
                    redraw(true);
                    return;
                }
    
                // Check prefixes first
                if (text.equals("r")) {
                    // Redraw with node numbers
                    redraw(true);
                }
                else if (text.equals("o")) {
                    // Draw without node numbers
                    redraw(false);
                }
                else if (text.equals("f")) {
                    // Draw with node numbers (including terminal symbols)
                    redraw(true, true);
                }
                else if (text.startsWith("+")) {
                    // New node
                    text = text.substring(1).trim();
                    if (text.isEmpty()) return;
                    int r = Integer.parseInt(text);
                    Rule rule = getRule(r);
                    if (rule == null) {
                        error("Invalid Index", "Could not get production rule " + r);
                        return;
                    }
                    relevant.addLast(graphInfo.newNode(rule.getFullName()));
                    redraw(true);
                }
                else if (text.startsWith("~")) {
                    // Unlink node
                    text = text.substring(1).trim();
                    if (text.isEmpty()) return;
                    int n = Integer.parseInt(text);
                    addRelevant(graphInfo.unlink(n));
                    redraw(true);
                }
                else if (text.startsWith("=")) {
                    // Set relevant node
                    text = text.substring(1).trim();
                    if (text.isEmpty()) return;
                    int n = Integer.parseInt(text);
                    if (n < 0) return;
                    relevant.remove(n);
                    relevant.addFirst(n);
                    redraw(true);
                }
                else if (text.startsWith("-")) {
                    // Delete node (and children)
                    text = text.substring(1).trim();
                    if (text.isEmpty()) return;
                    int n = Integer.parseInt(text);
                    relevant.remove(n);
                    addRelevant(graphInfo.delete(n));
                    redraw(true);
                }
                else if (text.startsWith("*")) {
                    // Recalculate relevant nodes
                    relevant.clear();
                    graphInfo.getIncomplete().forEach(this::addRelevant);
                    redraw(true);
                }
                else if (parts.length == 1) {
                    // Select node
                    int n = Integer.parseInt(parts[0]);
                    startSelection(n);
                }
                else if (parts.length == 2) {
                    // Set parent-child link
                    int parent = Integer.parseInt(parts[0]);
                    int child = Integer.parseInt(parts[1]);
                    relevant.remove(parent);
                    graphInfo.addLink(parent, child);
                    redraw(true);
                }
                else if (parts.length == 3) {
                    if (parts[0].equals("s")) {
                        // Find a simple path between a node and value
                        int n = Integer.parseInt(parts[1]);
                        simplePath(n, parts[2]);
                    }
                    else {
                        // Set parent -- child link
                        if (parts[1].equals("--") || parts[1].equals("->")) {
                            int parent = Integer.parseInt(parts[0]);
                            int child = Integer.parseInt(parts[2]);
                            relevant.remove(parent);
                            graphInfo.addLink(parent, child);
                            redraw(true);
                        }
                    }
                }
            }
        });
        
        view.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                event.consume();
                saveImage();
            }
        });
    }
    
    /**
     * Adds a relevant node if non-null and >= 0
     * @param i Next relevant node
     */
    private void addRelevant(Integer i) {
        if (i == null || i < 0) return;
        relevant.addLast(i);
    }
    
    /**
     * Get the next relevant node.
     * @return Relevant node number or -1.
     */
    private int getRelevant() {
        if (relevant.size() > 0) return relevant.peekFirst();
        return -1;
    }
    
    /**
     * Displays number counts on the lines specified by given line numbers.
     * The numbers on the given lines will start at 1 and increment with each
     * line to display on.
     * @param lines
     */
    private void updateCount(List<Integer> lines) {
        StringBuilder builder = new StringBuilder();
        int count = 1;
        int last = 0;
        for (int i : lines) {
            i = i - 1;
            for (int j = last; j < i; j++) {
                builder.append('\n');
            }
            last = i;
            builder.append(count++);
        }
        this.count.setText(builder.toString());
    }
    
    /**
     * Gets the rule with the given number. The number is the line number
     * displayed in the counting text area, which makes it 1-indexed.
     * @param number Line number of the rule to add.
     * @return Rule on the given line number.
     */
    private Rule getRule(int number) {
        List<Rule> rules = ruleReader.fromString(defs.getText());
        int realIndex = 0;
        for (Rule rule : rules) {
            if (rule != null) {
                realIndex++;
                if (realIndex == number) return rule;
            }
        }
        return null;
    }
    
    /**
     * Get the rule for the given non-terminal.
     * Multiple definitions of the same production rule will be combined.
     * @param prod Production rule, including the angle brackets.
     * @return Full production rule.
     */
    private Rule getRule(String prod) {
        List<Rule> rules = ruleReader.fromString(defs.getText());
        return ProductionRuleReader.getFullRule(prod, rules);
    }
    
    /**
     * Select a node to add children to.
     * If the node does not have a production rule defined, an error will display.
     * @param n Number of the node to select.
     */
    private void startSelection(int n) {
        if (n < 0) return;
        String node = graphInfo.getNode(n);
        if (node == null) {
            error("Invalid node", "There is no production rule for node " + n);
            return;
        }
        Rule rule = getRule(node);
        if (rule == null) {
            error("Invalid node", "There is no production rule for node " + n);
            return;
        }
        select(n, rule);
    }
    
    /**
     * Change to selection mode.
     * An option will be selected from the given production rule and appended
     * as children to the specified node.
     * @param node Node to append to.
     * @param rule Rule to select options from.
     */
    private void select(int node, Rule rule) {
        state = SELECT;
        originalRules = defs.getText();
        selecting = rule;
        parent = node;
        defs.setText(String.join("\n", rule.getParts()));
        List<Integer> lines = IntStream.range(1, rule.getParts().size() + 1).boxed().collect(Collectors.toList());
        updateCount(lines);
    }
    
    /**
     * Select the option on the specified line.
     * @param i Line number (1-indexed) to select.
     */
    private void makeSelection(int i) {
        state = DEFAULT;
        if (i < 1) {
            // Cancel selection
            defs.setText(originalRules);
            return;
        }
        i--;
        if (i >= selecting.getParts().size()) {
            state = SELECT;
            error("Invalid", "Invalid line number.");
            return;
        }
        // Add elements from the selection to the graph
        String part = selecting.getParts().get(i);
        addRuleParts(part, parent);
        // Exit selection mode
        relevant.remove(parent);
        defs.setText(originalRules);
        originalRules = null;
        selecting = null;
    }
    
    private void addRuleParts(String part, int parent) {
        getRuleParts(part).map(s -> graphInfo.newNode(s))
                          .collect(Collectors.toCollection(LinkedList::new))
                          .descendingIterator()
                          .forEachRemaining(n -> {
                              graphInfo.addLink(parent, n);
                              if (nonEmpty(graphInfo.getNode(n), "<", ">")) relevant.addFirst(n);
                          });
    }
    
    /**
     * Get the elements from the given branch of a production rule that
     * will be added to the graph.
     *
     * Elements are either split by spaces or at non-terminal symbols.
     * @param part Selected part of production rule.
     * @return Stream of pieces to add.
     */
    private Stream<String> getRuleParts(String part) {
        return Arrays.stream(part.replaceAll("<.+?>", " $0 ").split(" +")).filter(s -> !s.isEmpty());
    }
    
    /**
     * Find a simple path from the given node to the given value.
     * This will only search for a simple linear path through production
     * rules to the specified value. If there is any ambiguity or multiple
     * paths to the value, the search is likely to fail.
     *
     * If a path is found the nodes from the current node to found value are added.
     * @param node Node number to search from.
     * @param to Value to reach.
     */
    private void simplePath(int node, String to) {
        if (node < 0) return;
        List<Rule> rules = ruleReader.fromString(defs.getText());
        String target = graphInfo.getNode(node);
        Set<String> checked = new HashSet<>();
        Queue<String> path = new LinkedList<>();
        boolean first = true;
        // Search backwards from the value to the starting node.
        while (!to.equals(target)) {
            if (first) {
                first = false;
                String start = to;
                Optional<String> startRule = rules.stream()
                                                  .flatMap(rule -> rule.getParts().stream())
                                                  .filter(s -> matchesSearch(start, s))
                                                  .findFirst();
                if (startRule.isPresent()) {
                    path.add(startRule.get());
                    to = startRule.get();
                }
                else {
                    error("No node.", "Cannot find a node that matches the pattern.");
                    return;
                }
            }
            else path.add(to);
            boolean found = false;
            for (Rule rule : rules) {
                if (checked.contains(rule.getFullName())) continue;
                String finalTo = to;
                if (rule.getParts().stream().noneMatch(s -> s.equals(finalTo))) continue;
                found = true;
                checked.add(rule.getFullName());
                to = rule.getFullName();
                break;
            }
            if (!found) {
                error("No path", "There is no simple path to the specified value.");
                return;
            }
        }
        if (path.size() == 0) return;
        // Add the path of nodes
        int last = -1;
        String base = path.remove();
        int baseNum = node;
        while (path.size() > 0) {
            int n = graphInfo.newNode(path.remove());
            if (last > -1) graphInfo.addLink(n, last);
            else {
                String newNode = graphInfo.getNode(n);
                if (nonEmpty(newNode, "<", ">")) relevant.addFirst(n);
            }
            last = n;
            if (baseNum == node) baseNum = last;
        }
        addRuleParts(base, baseNum);
        if (last != -1) graphInfo.addLink(node, last);
        relevant.remove(node);
        redraw(true);
    }
    
    /**
     * Returns whether the given string starts with the given prefix, ends with
     * the given suffix, and has some content in between.
     * @param s String to test
     * @param prefix String prefix
     * @param suffix String suffix
     * @return True if the string is non-empty within the prefix and suffix.
     */
    public static boolean nonEmpty(String s, String prefix, String suffix) {
        return s.length() > prefix.length() + suffix.length() &&
                s.startsWith(prefix) && s.endsWith(suffix);
    }
    
    /**
     * Test a given pattern against a string.
     * The string matches if it contains all characters from the pattern
     * anywhere as long as they are in the same order.
     *
     * If the pattern is enclosed in single quotes, the string matches if it
     * contains the substring of the string in quotes.
     *
     * If the pattern is enclosed in double quotes, the string matches if it
     * is exactly the string enclosed in quotes.
     * @param pattern Pattern string.
     * @param search Search string.
     * @return True if the string contains the pattern.
     */
    private boolean matchesSearch(String pattern, String search) {
        if (pattern.length() > 2 && pattern.startsWith("'") && pattern.endsWith("'")) {
            return search.contains(pattern.substring(1, pattern.length() - 1));
        }
        if (pattern.length() > 2 && pattern.startsWith("\"") && pattern.endsWith("\"")) {
            return search.equals(pattern.substring(1, pattern.length() - 1));
        }
        int look = 0;
        for (char c : search.toCharArray()) {
            if (c == pattern.charAt(look)) look++;
            if (look == pattern.length()) return true;
        }
        return false;
    }
    
    /**
     * Execute the dot program to get the current visual of the graph.
     * @param extra Whether to display extras like node IDs and the relevant node.
     */
    private void redraw(boolean extra) {
        redraw(extra, false);
    }
    
    /**
     * Execute the dot program to get the current visual of the graph.
     * @param extra Whether to display extras like node IDs and the relevant node.
     * @param full If true, will also display extras on terminal symbols.
     */
    private void redraw(boolean extra, boolean full) {
        Image i = executeDot(extra, full, "png", Image::new);
        image.setImage(i);
    }
    
    /**
     * Save the image to the file chosen in a file dialog.
     */
    private void saveImage() {
        FileChooser fc = new FileChooser();
        fileTypes.values().forEach(fc.getExtensionFilters()::add);
        fc.setInitialFileName("tree.png");
        File file = fc.showSaveDialog(stage);
        if (file == null) return;
        int index = file.getName().lastIndexOf('.');
        if (index < 0) {
            error("Unknown format.", "Unknown file format.");
            return;
        }
        String extension = file.getName().substring(index);
        if (!fileTypes.containsKey(extension)) {
            error("Unknown format.", "Unknown file format.");
            return;
        }
        String type = extension.substring(1);
        executeDot(false, false, type, inputStream -> {
            try {
                Files.copy(inputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        });
    }
    
    /**
     * Execute the dot program to get the current visual of the graph.
     * @param extra Whether to display extras like node IDs and the relevant node.
     * @param full Same as extra but will also display IDs on terminal symbols.
     * If true, extra will also be true.
     * @param function Function that accepts the output from dot and returns a value.
     * @param <T> Type to create from the inputstream.
     * @return Function applied to dot output.
     */
    private <T> T executeDot(boolean extra, boolean full, String type, Function<InputStream, T> function) {
        String path = dotPath.getText();
        if (path.length() > 2 && path.startsWith("\"") && path.endsWith("\"")) {
            path = path.substring(1, path.length() - 1);
        }
        if (!(new File(path).exists())) {
            error("Invalid dot path.", "Invalid path to DOT.");
            return null;
        }
        String spec = graphInfo.getGraphSpec(extra, full, getRelevant());
        // Execute dot and send output to an Image.
        // -Tpng tells dot to output the graph as a png image.
        ProcessBuilder pb = new ProcessBuilder(path, "-T" + type);
        try {
            Process process = pb.start();
            process.getOutputStream().write(spec.getBytes());
            process.getOutputStream().close();
            T t = function.apply(process.getInputStream());
            process.waitFor();
            return t;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * Display an error dialog.
     * @param header Header message.
     * @param msg Message content.
     */
    private void error(String header, String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(msg);
        alert.showAndWait();
    }
    
}