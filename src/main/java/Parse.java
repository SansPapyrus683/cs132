import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/** thought this would be a one and done thing so code isn't the cleanest lmao */
public class Parse {
    private static int at = 0;
    private static List<String> tokens;

    public static void main(String[] args) throws IOException {
        BufferedReader read = new BufferedReader(new InputStreamReader(System.in));
        Lexer lex = new Lexer(read.lines().collect(Collectors.joining("\n")));
        if (lex.bad) {
            System.out.println("Parse error");
            return;
        }
        tokens = lex.tokens;
        System.out.println(s() && at == tokens.size() ? "Program parsed successfully" : "Parse error");
    }

    private static boolean match(String... what) {
        if (at >= tokens.size()) {
            return false;
        }
        String curr = tokens.get(at);
        for (String m : what) {
            if (m.equals(curr)) {
                at++;
                return true;
            }
        }
        return false;
    }

    private static boolean s() {
        if (match("{")) {
            return l() && match("}");
        } else if (match("System.out.println")) {
            return match("(") && e() && match(")") && match(";");
        } else if (match("if")) {
            return match("(") && e() && match(")") && s() && match("else") && s();
        } else if (match("while")) {
            return match("(") && e() && match(")") && s();
        }
        return false;
    }

    private static boolean l() {
        if (at >= tokens.size()) {
            return true;
        }
        int reset = at;
        if (s()) {
            return l();
        }
        at = reset;
        return true;
    }

    private static boolean e() {
        if (match("true", "false")) {
            return true;
        } else if (match("!")) {
            return e();
        }
        return false;
    }
}

// this could probably be straight in main but idc
class Lexer {
    public final List<String> tokens = new ArrayList<>();
    public boolean bad = false;

    private int at = 0;
    private static Set<Character> JUST_ONE = new HashSet<>(Arrays.asList('{', '}', '(', ')', ';', '!'));
    private static Set<String> STRS = new HashSet<>(
            Arrays.asList("System.out.println", "if", "else", "while", "true", "false"));

    public Lexer(String str) {
        str = str + ' ';
        while (at < str.length()) {
            char curr = str.charAt(at);
            if (JUST_ONE.contains(curr)) {
                tokens.add(Character.toString(curr));
            } else if (Character.isAlphabetic(curr)) {
                int to = at;
                for (; Character.isAlphabetic(str.charAt(to)) || str.charAt(to) == '.'; to++)
                    ;
                String sub = str.substring(at, to);
                if (STRS.contains(sub)) {
                    tokens.add(sub);
                } else {
                    bad = true;
                    return;
                }
                at = to - 1;
            } else if (!Character.isWhitespace(curr)) {
                bad = true;
                return;
            }
            at++;
        }
    }
}
