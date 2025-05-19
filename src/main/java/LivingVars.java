import java.util.*;
import java.util.stream.Collectors;

import IR.token.Identifier;
import sparrow.visitor.DepthFirst;
import sparrow.*;

public class LivingVars extends DepthFirst {
    public final Map<String, Map<String, int[]>> ranges = new HashMap<>();
    public final Map<String, TreeSet<Integer>> calls = new HashMap<>();

    private final Map<String, List<List<Integer>>> to = new HashMap<>();
    private final Map<String, List<List<Integer>>> from = new HashMap<>();
    private final Map<String, List<Set<String>>> use = new HashMap<>();
    // each line can define at most one
    private final Map<String, List<String>> def = new HashMap<>();

    private Map<Integer, String> gotos;
    private Map<String, Integer> labels;
    private String func;
    private int line;

    public LivingVars(Program prog) {
        prog.accept(this);
    }

    @Override
    public void visit(FunctionDecl f) {
        func = f.functionName.toString();
        int lineNum = f.block.instructions.size();
        calls.put(func, new TreeSet<>());
        to.put(func, new ArrayList<>());
        from.put(func, new ArrayList<>());
        use.put(func, new ArrayList<>());
        def.put(func, new ArrayList<>());

        // DIE!! DIE!! DIE IN A PLANE CRASH!!!!
        for (int l = 0; l <= lineNum; l++) {
            to.get(func).add(new ArrayList<>());
            from.get(func).add(new ArrayList<>());
            use.get(func).add(new HashSet<>());
            def.get(func).add(null);
        }

        visit(f.block);

        Set<String> params = f.formalParameters.stream()
                .map(Identifier::toString)
                .collect(Collectors.toSet());

        // THEN SOMETHING JUST SNAPPED!!
        Map<String, int[]> thisRanges = new HashMap<>();
        for (int l = 0; l <= lineNum; l++) {
            Set<Integer> visited = new HashSet<>(List.of(l));
            Deque<Integer> frontier = new ArrayDeque<>(List.of(l));
            while (!frontier.isEmpty()) {
                int curr = frontier.poll();
                for (int p : from.get(func).get(curr)) {
                    if (visited.add(p)) {
                        frontier.add(p);
                    }
                }
            }
            Set<String> defThru = visited.stream()
                    .map(i -> def.get(func).get(i))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            defThru.addAll(params);  // surely the start is always reachable

            visited = new HashSet<>(List.of(l));
            frontier = new ArrayDeque<>(List.of(l));
            while (!frontier.isEmpty()) {
                int curr = frontier.poll();
                for (int p : to.get(func).get(curr)) {
                    if (visited.add(p)) {
                        frontier.add(p);
                    }
                }
            }
            Set<String> useThru = visited.stream()
                    .flatMap(i -> use.get(func).get(i).stream())
                    .collect(Collectors.toSet());

            useThru.retainAll(defThru);
            for (String var : useThru) {
                int[] range = thisRanges.getOrDefault(var, new int[] {l, l});
                range[0] = Math.min(range[0], l);
                range[1] = Math.max(range[1], l);
                thisRanges.put(var, range);
            }
        }
        ranges.put(func, thisRanges);
    }

    @Override
    public void visit(Block b) {
        gotos = new HashMap<>();
        labels = new HashMap<>();

        line = 0;
        for (Instruction i : b.instructions) {
            i.accept(this);
            line++;
        }
        for (Map.Entry<Integer, String> gt : gotos.entrySet()) {
            addEdge(gt.getKey(), labels.get(gt.getValue()));
        }

        // that's why i added one more spot
        use.get(func).get(b.instructions.size()).add(b.return_id.toString());
    }

    private void addEdge(int lineFrom, int lineTo) {
        to.get(func).get(lineFrom).add(lineTo);
        from.get(func).get(lineTo).add(lineFrom);
    }

    private void addUse(Identifier... vars) {
        Arrays.stream(vars).forEach(v -> use.get(func).get(line).add(v.toString()));
    }

    private void setDef(Identifier var) {
        def.get(func).set(line, var.toString());
    }

    @Override
    public void visit(LabelInstr n) {
        labels.put(n.label.toString(), line);
        addEdge(line, line + 1);
    }

    @Override
    public void visit(Move_Id_Integer n) {
        addEdge(line, line + 1);
        setDef(n.lhs);
    }

    @Override
    public void visit(Move_Id_FuncName n) {
        addEdge(line, line + 1);
        setDef(n.lhs);
    }

    @Override
    public void visit(Move_Id_Id n) {
        addEdge(line, line + 1);
        addUse(n.rhs);
        setDef(n.lhs);
    }

    @Override
    public void visit(Add n) {
        addEdge(line, line + 1);
        addUse(n.arg1, n.arg2);
        setDef(n.lhs);
    }

    @Override
    public void visit(Subtract n) {
        addEdge(line, line + 1);
        addUse(n.arg1, n.arg2);
        setDef(n.lhs);
    }

    @Override
    public void visit(Multiply n) {
        addEdge(line, line + 1);
        addUse(n.arg1, n.arg2);
        setDef(n.lhs);
    }

    @Override
    public void visit(LessThan n) {
        addEdge(line, line + 1);
        addUse(n.arg1, n.arg2);
        setDef(n.lhs);
    }

    @Override
    public void visit(Load n) {
        addEdge(line, line + 1);
        addUse(n.base);
        setDef(n.lhs);
    }

    @Override
    public void visit(Store n) {
        addEdge(line, line + 1);
        addUse(n.base, n.rhs);
    }

    @Override
    public void visit(Alloc n) {
        addEdge(line, line + 1);
        addUse(n.size);
        setDef(n.lhs);
    }

    @Override
    public void visit(Print n) {
        addEdge(line, line + 1);
        addUse(n.content);
    }

    @Override
    public void visit(Goto n) {
        gotos.put(line, n.label.toString());
    }

    @Override
    public void visit(IfGoto n) {
        addEdge(line, line + 1);
        gotos.put(line, n.label.toString());
        addUse(n.condition);
    }

    @Override
    public void visit(Call n) {
        addEdge(line, line + 1);
        addUse(n.callee);
        calls.get(func).add(line);
        n.args.stream().forEach(id -> addUse(id));
        setDef(n.lhs);
    }
}
