import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Rule {
    
    private int line;
    private String name;
    private List<String> parts;
    
    public Rule(int line, String name, List<String> parts) {
        this.line = line;
        this.name = name;
        this.parts = parts;
    }
    
    /**
     * Read a single production rule.
     * Null will be returned for an invalid rule definition.
     *
     * No validation is done on the rule, it must only be in the form of
     * &lt;name&gt; ::= ...
     * Where ... is some non-empty value.
     *
     * Rules will be split at isolated pipes (|), isolated meaning it is not
     * next to another pipe symbol. Pipes can be escaped with a backslash.
     * @param line Line number for this rule, used to know which line on which
     * to display a number for this rule.
     * @param rule Raw production rule
     * @return Rule object, or null for an invalid rule.
     */
    public static Rule parse(int line, String rule) {
        String[] r = rule.split("::=");
        if (r.length != 2) return null;
        String name = r[0].trim();
        if (!Grapher.nonEmpty(name, "<", ">")) return null;
        name = name.substring(1, name.length() - 1);
        List<String> parts = Arrays.stream(r[1].split("(?<![|\\\\])\\|(?!\\|)"))
                                   .map(String::trim)
                                   .map(s -> s.replace("\\|", "|"))
                                   .collect(Collectors.toList());
        if (parts.size() < 1 || parts.get(0).isEmpty()) return null;
        return new Rule(line, name, parts);
    }
    
    public int getLine() {
        return line;
    }
    
    public String getName() {
        return name;
    }
    
    public String getFullName() {
        return "<" + name + ">";
    }
    
    public List<String> getParts() {
        return parts;
    }
    
    @Override
    public String toString() {
        return "<" + name + ">" + " ::= " + String.join(" | ", parts);
    }
    
}
