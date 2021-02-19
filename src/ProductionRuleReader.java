import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ProductionRuleReader extends StringConverter<List<Rule>> {
    
    /**
     * Get a string representation of production rules.
     * @param object List of production rules.
     * @return String representation.
     */
    @Override
    public String toString(List<Rule> object) {
        if (object == null) return "";
        return object.stream().filter(Objects::nonNull).map(Rule::toString).collect(Collectors.joining("\n"));
    }
    
    /**
     * Get a list of production rules from a string.
     * A rule is ended once a blank line is encountered or the start of a new
     * rule is reached.
     * @param string Production rules string.
     * @return List of production rules.
     */
    @Override
    public List<Rule> fromString(String string) {
        List<Rule> rules = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        boolean started = false;
        int line = -1;
        String[] split = string.split("\n");
        for (int i = 0, splitLength = split.length; i < splitLength; i++) {
            String s = split[i];
            if (s.trim().isEmpty() && started) {
                rules.add(Rule.parse(line, builder.toString()));
                builder.setLength(0);
                started = false;
            }
            if (s.contains("::=")) {
                if (started) {
                    rules.add(Rule.parse(line, builder.toString()));
                    builder.setLength(0);
                }
                else started = true;
                line = i + 1;
            }
            if (started) builder.append(s);
        }
        if (started) {
            rules.add(Rule.parse(line, builder.toString()));
        }
        return rules;
    }
    
    /**
     * Combine all production rules with the given name.
     * @param rule Production rule including angle brackets.
     * @param rules List of production rules.
     * @return Combined production rules.
     */
    public static Rule getFullRule(String rule, List<Rule> rules) {
        List<String> fullRules = rules.stream()
                                      .filter(Objects::nonNull)
                                      .filter(r -> r.getFullName().equals(rule))
                                      .flatMap(r -> r.getParts().stream())
                                      .collect(Collectors.toList());
        if (fullRules.size() == 0) return null;
        return new Rule(-1, rule.substring(1, rule.length() - 1), fullRules);
    }
    
}
