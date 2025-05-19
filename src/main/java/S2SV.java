import java.util.*;
import java.util.function.Predicate;

import IR.SparrowParser;
import IR.ParseException;
import IR.visitor.SparrowConstructor;
import IR.syntaxtree.Node;
import IR.token.Identifier;
import sparrow.visitor.DepthFirst;
import sparrow.*;

public class S2SV extends DepthFirst {
    private static final List<String> ALL = new ArrayList<>();
    private static final List<String> ARGS = new ArrayList<>();

    private final Program prog;
    private final LivingVars lv;

    private int line;
    private Map<String, String> reg;
    private Map<String, int[]> ranges;
    private List<String> toSave;

    // today i learned this exists
    static {
        for (int a = 2; a <= 7; a++) {
            ARGS.add("a" + a);
        }
        for (int s = 1; s <= 11; s++) {
            ALL.add("s" + s);
        }
        for (int t = 2; t <= 5; t++) {
            ALL.add("t" + t);
        }
    }

    private static boolean isSave(String reg) {
        return reg.startsWith("s");
    }

    public S2SV(Program prog) {
        this.prog = prog;
        lv = new LivingVars(prog);
    }

    public void compileAgain() {
        prog.accept(this);
    }

    @Override
    public void visit(FunctionDecl f) {
        String func = f.functionName.toString();

        StringBuilder header = new StringBuilder("func ").append(func).append('(');
        for (int i = ARGS.size(); i < f.formalParameters.size(); i++) {
            header.append(f.formalParameters.get(i)).append(' ');
        }
        header.append(')');
        System.out.println(header);

        // just shorthands, nothing much
        ranges = lv.ranges.get(func);
        final TreeSet<Integer> calls = lv.calls.get(func);

        List<String> params = f.formalParameters.stream()
                .map(Object::toString).toList();
        Set<String> free = new HashSet<>(ALL);
        reg = new HashMap<>();
        for (int i = 0; i < Math.min(params.size(), ARGS.size()); i++) {
            String param = params.get(i);
            if (ranges.containsKey(param)) {
                reg.put(param, ARGS.get(i));
            } else {
                free.add(ARGS.get(i));
            }
        }
        for (int i = params.size(); i < ARGS.size(); i++) {
            free.add(ARGS.get(i));
        }
        final int messAroundNum = free.size();

        // this is some usaco-tier code i'm ngl
        TreeSet<String> active = new TreeSet<>((v1, v2) -> {
            int e1 = ranges.get(v1)[1], e2 = ranges.get(v2)[1];
            return e1 != e2 ? e1 - e2 : v1.compareTo(v2);
        });
        ranges.entrySet().stream().sorted(Comparator.comparingInt(r -> r.getValue()[0]))
                .forEach(r -> {
                    String name = r.getKey();
                    int[] range = r.getValue();
                    if (reg.containsKey(name)) {
                        return;
                    }

                    List<String> retired = new ArrayList<>();
                    for (String a : active) {
                        if (ranges.get(a)[1] >= range[0]) {
                            break;
                        }
                        retired.add(a);
                    }
                    for (String v : retired) {
                        active.remove(v);
                        if (!reg.get(v).equals(v)) {
                            free.add(reg.get(v));
                        }
                    }

                    if (active.size() == messAroundNum) {
                        String spill = active.last();
                        if (ranges.get(spill)[1] > range[1]) {
                            reg.put(name, reg.get(spill));
                            reg.put(spill, spill);
                            active.remove(spill);
                            active.add(name);
                        } else {
                            reg.put(name, name);
                        }
                    } else {
                        NavigableSet<Integer> within = calls.headSet(range[1], true)
                                .tailSet(range[0], true);
                        Predicate<String> cond = within.isEmpty() ? s -> !S2SV.isSave(s)
                                : S2SV::isSave;

                        Optional<String> please = free.stream().filter(cond)
                                .findAny();
                        String use = please.orElse(free.iterator().next());

                        free.remove(use);
                        reg.put(name, use);
                        active.add(name);
                    }
                });

        toSave = reg.values().stream()
                .filter(S2SV::isSave).distinct().toList();
        if (func.equalsIgnoreCase("main")) {
            toSave = new ArrayList<>();
        }
        toSave.forEach(r -> prLine("old_%s = %s", r, r));

        for (int i = ARGS.size(); i < f.formalParameters.size(); i++) {
            String name = f.formalParameters.get(i).toString();
            if (reg.containsKey(name)) {
                prLine("%s = %s", reg.get(name), name);
            }
        }

        visit(f.block);
    }

    /** shorthand function lmao */
    private String loc(Identifier id) {
        if (!reg.containsKey(id.toString())) {
            String err = String.format("WTF %s ISN'T MAPPED YET (line %d)", id, line);
            throw new IllegalArgumentException(err);
        }
        return reg.get(id.toString());
    }

    @Override
    public void visit(Block b) {
        line = 0;
        for (Instruction i : b.instructions) {
            i.accept(this);
            line++;
            System.out.println();  // this helped alot when debugging lol
        }

        Identifier rid = b.return_id;
        if (!loc(rid).equals(rid.toString())) { // load if not spilt
            prLine("%s = %s", rid, loc(rid));
        }

        toSave.forEach(r -> prLine("%s = old_%s", r, r));

        prLine("return %s", rid);
    }

    private void assignment(Identifier id, String rhs) {
        if (reg.containsKey(id.toString())) {
            prLine("%s = %s", loc(id), rhs);
        }
    }

    @Override
    public void visit(Call c) {
        Set<String> stackParams = new HashSet<>();
        for (int i = ARGS.size(); i < c.args.size(); i++) {
            stackParams.add(c.args.get(i).toString());
        }

        List<String[]> restore = new ArrayList<>();
        for (Map.Entry<String, String> m : reg.entrySet()) {
            String v = m.getKey();
            String r = m.getValue();
            if (v.equals(r)) {
                continue;
            }
            int[] live = ranges.get(v);

            if (isSave(r)) {
                if (stackParams.contains(v)) {
                    prLine("%s = %s", v, r);
                }
            } else if (live[0] <= line && line <= live[1]) {
                prLine("%s = %s", v, r);
                if (line < live[1] && !v.equals(c.lhs.toString())) {
                    restore.add(new String[] { v, r });
                }
            }
        }
        prLine("t1 = %s", loc(c.callee));

        Map<String, String> getFrom = new HashMap<>();
        Map<String, Integer> sendTo = new HashMap<>();
        for (int i = 0; i < Math.min(c.args.size(), ARGS.size()); i++) {
            String from = loc(c.args.get(i)), to = "a" + (2 + i);
            if (!from.equals(to)) {
                getFrom.put(to, from);
                sendTo.put(from, sendTo.getOrDefault(from, 0) + 1);
                sendTo.computeIfAbsent(to, k -> 0); // WHY CAN'T I USE AN UNDERSCORE
            }
        }

        List<String> starts = sendTo.entrySet().stream()
                .filter(e -> e.getValue() == 0)
                .map(e -> e.getKey())
                .toList();
        for (String start : starts) {
            String at = getFrom.get(start);
            List<String> path = new ArrayList<>(List.of(start, at));
            while (getFrom.containsKey(at) && sendTo.get(at) == 1) {
                sendTo.put(at, sendTo.get(at) - 1);
                at = getFrom.get(at);
                path.add(at);
            }
            sendTo.put(at, sendTo.get(at) - 1);

            for (int i = 0; i < path.size() - 1; i++) {
                prLine("%s = %s", path.get(i), path.get(i + 1));
            }
        }

        for (Map.Entry<String, Integer> cyc : sendTo.entrySet()) {
            if (cyc.getValue() == 0) {
                continue;
            }
            String start = cyc.getKey();
            sendTo.put(start, sendTo.get(start) - 1);
            String at = getFrom.get(start);
            List<String> path = new ArrayList<>(List.of(start, at));
            while (!at.equals(start)) {
                sendTo.put(at, sendTo.get(at) - 1);
                at = getFrom.get(at);
                path.add(at);
            }

            prLine("t0 = %s", path.get(0));
            for (int i = 0; i < path.size() - 2; i++) {
                prLine("%s = %s", path.get(i), path.get(i + 1));
            }
            prLine("%s = t0", path.get(path.size() - 2));
        }

        StringBuilder args = new StringBuilder();
        for (int i = ARGS.size(); i < c.args.size(); i++) {
            args.append(c.args.get(i)).append(' ');
        }
        prLine("t0 = call t1(%s)", args);

        for (String[] r : restore) {
            prLine("%s = %s", r[1], r[0]);
        }
        assignment(c.lhs, "t0");
    }

    @Override
    public void visit(LabelInstr l) {
        System.out.printf("  %s:%n", l.label);
    }

    @Override
    public void visit(Move_Id_Id i) {
        prLine("t0 = %s", loc(i.rhs));
        assignment(i.lhs, "t0");
    }

    @Override
    public void visit(Move_Id_FuncName i) {
        prLine("t0 = @%s", i.rhs);
        assignment(i.lhs, "t0");
    }

    @Override
    public void visit(Move_Id_Integer i) {
        prLine("t0 = %d", i.rhs);
        assignment(i.lhs, "t0");
    }

    @Override
    public void visit(Add i) {
        prLine("t0 = %s", loc(i.arg1));
        prLine("t1 = %s", loc(i.arg2));
        prLine("t0 = t0 + t1");
        assignment(i.lhs, "t0");
    }

    @Override
    public void visit(Subtract i) {
        prLine("t0 = %s", loc(i.arg1));
        prLine("t1 = %s", loc(i.arg2));
        prLine("t0 = t0 - t1");
        assignment(i.lhs, "t0");
    }

    @Override
    public void visit(Multiply i) {
        prLine("t0 = %s", loc(i.arg1));
        prLine("t1 = %s", loc(i.arg2));
        prLine("t0 = t0 * t1");
        assignment(i.lhs, "t0");
    }

    @Override
    public void visit(LessThan i) {
        prLine("t0 = %s", loc(i.arg1));
        prLine("t1 = %s", loc(i.arg2));
        prLine("t0 = t0 < t1");
        assignment(i.lhs, "t0");
    }

    @Override
    public void visit(Alloc i) {
        prLine("t0 = %s", loc(i.size));
        prLine("t0 = alloc(t0)");
        assignment(i.lhs, "t0");
    }

    @Override
    public void visit(Load i) {
        prLine("t0 = %s", loc(i.base));
        prLine("t0 = [t0 + %d]", i.offset);
        assignment(i.lhs, "t0");
    }

    @Override
    public void visit(Store i) {
        prLine("t0 = %s", loc(i.base));
        prLine("t1 = %s", loc(i.rhs));
        prLine("[t0 + %d] = t1", i.offset);
    }

    @Override
    public void visit(Goto i) {
        prLine("goto %s", i.label);
    }

    @Override
    public void visit(IfGoto i) {
        prLine("t0 = %s", loc(i.condition));
        prLine("if0 t0 goto %s", i.label);
    }

    @Override
    public void visit(ErrorMessage i) {
        prLine("error(%s)", i.msg);
    }

    @Override
    public void visit(Print i) {
        prLine("t0 = %s", loc(i.content));
        prLine("print(t0)");
    }

    /** well, this was useful in j2s, ig it is here too */
    private void prLine(String fmt, Object... args) {
        System.out.printf("    " + fmt + "%n", args);
    }

    public static void main(String[] args) {
        try {
            new SparrowParser(System.in);
            Node root = SparrowParser.Program();
            SparrowConstructor constructor = new SparrowConstructor();
            root.accept(constructor);
            new S2SV(constructor.getProgram()).compileAgain();
        } catch (ParseException e) {
            System.out.println(e);
        }
    }
}
