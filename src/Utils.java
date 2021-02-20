import javafx.scene.control.Alert;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Utils {
    
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
    public static boolean matchesSearch(String pattern, String search) {
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
     * Get the elements from the given branch of a production rule that
     * will be added to the graph.
     *
     * Elements are either split by spaces or at non-terminal symbols.
     * @param part Selected part of production rule.
     * @return Stream of pieces to add.
     */
    public static Stream<String> getRuleParts(String part) {
        return Arrays.stream(part.replaceAll("<.+?>", " $0 ").split(" +")).filter(s -> !s.isEmpty());
    }
    
    /**
     * Return a production value formatted so that equivalent productions
     * are equivalent by String.equals().
     * @param value Production value.
     * @return Formatted value.
     */
    public static String formatRuleValue(String value) {
        return getRuleParts(value).collect(Collectors.joining());
    }
    
    /**
     * Returns the single value in the stream if it exists or empty if the
     * stream is empty or contains more than one value.
     * @param stream Stream to check.
     * @param <T> Stream type
     * @return Optional of single value in stream.
     */
    public static <T> Optional<T> single(Stream<T> stream) {
        Iterator<T> it = stream.iterator();
        if (!it.hasNext()) return Optional.empty();
        Optional<T> op = Optional.ofNullable(it.next());
        return op.filter(t -> !it.hasNext());
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
     * Display an error dialog.
     * @param header Header message.
     * @param msg Message content.
     */
    public static void error(String header, String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(msg);
        alert.showAndWait();
    }
    
}
