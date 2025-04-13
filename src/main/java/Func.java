import java.util.*;

public class Func {
    public String name;
    public String ret;
    public List<String> types;
    public Map<String, String> binds;

    public Func(String name, String ret, List<String> types, Map<String, String> binds) {
        this.name = name;
        this.ret = ret;
        this.types = types;
        this.binds = binds;
    }

    @Override
    public String toString() {
        return String.format("%s -> %s", types, ret);
    }
}
