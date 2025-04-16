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
        int memAt = al.isEmpty() ? 0 : Collections.max(al.values());
        for (String a : bs.attrs.get(type).keySet()) {
            al.put(a, ++memAt);
        }

        funcLoc.put(type, fl);
        attrLoc.put(type, al);
    }
}
