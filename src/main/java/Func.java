import java.util.*;

public class Func {
    public String ret;
    public List<String> args;
    public Map<String, String> names;

    public Func(String ret, List<String> args, Map<String, String> names) {
        this.ret = ret;
        this.args = args;
        this.names = names;
    }

    @Override
    public String toString() {
        return String.format("%s -> %s", args, ret);
    }
}
