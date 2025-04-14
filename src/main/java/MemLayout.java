import java.util.*;

public class MemLayout {
    private final BasicStuff bs;
    public Map<String, Map<String, Integer>> funcLoc = new HashMap<>();
    public Map<String, Map<String, Integer>> attrLoc = new HashMap<>();

    public MemLayout(BasicStuff bs) {
        this.bs = bs;

        for (String type : bs.types.keySet()) {
            if (bs.funcs.containsKey(type)) {
                procType(type);
            }
        }
    }

    private void procType(String type) {
        if (funcLoc.containsKey(type)) {
            return;
        }
        HashMap<String, Integer> fl = new HashMap<>();
        HashMap<String, Integer> al = new HashMap<>();

        String parent = bs.types.get(type);
        if (!type.equals(parent)) {
            procType(parent);
            fl.putAll(funcLoc.get(parent));
            al.putAll(attrLoc.get(parent));
        }

        for (String f : bs.funcs.get(type).keySet()) {
            if (!fl.containsKey(f)) {
                fl.put(f, fl.size());
            }
        }
        for (String a : bs.attrs.get(type).keySet()) {
            if (!al.containsKey(a)) {
                al.put(a, al.size() + 1);
            }
        }

        funcLoc.put(type, fl);
        attrLoc.put(type, al);
    }
}
