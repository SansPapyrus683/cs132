import java.util.*;
import minijava.ParseException;
import minijava.visitor.*;
import minijava.syntaxtree.*;
import minijava.MiniJavaParser;

public class Typecheck extends GJNoArguDepthFirst<String> {
    private BasicStuff bs;
    private Map<String, String> locals = new HashMap<>();
    private Set<String> assigned = new HashSet<>();

    private boolean bad = false;
    private String currFunc;
    private String currClass;
    private boolean inAssign;

    public Typecheck(Goal root) {
        try {
            bs = new BasicStuff(root);
        } catch (IllegalArgumentException e) {
            bad = true;
            return;
        }

        try {
            root.accept(this);
        } catch (IllegalArgumentException e) {
            bad = true;
        }
    }

    public boolean bad() {
        return bad;
    }

    @Override
    public String visit(MainClass n) {
        currClass = n.f1.f0.toString();
        currFunc = "main";
        locals.clear();
        assigned.clear();
        super.visit(n);
        currClass = null;
        currFunc = null;
        return null;
    }

    @Override
    public String visit(ClassDeclaration n) {
        currClass = n.f1.f0.toString();
        super.visit(n);
        currClass = null;
        return null;
    }

    @Override
    public String visit(ClassExtendsDeclaration n) {
        currClass = n.f1.f0.toString();
        super.visit(n);
        currClass = null;
        return null;
    }

    @Override
    public String visit(MethodDeclaration n) {
        currFunc = n.f2.f0.toString();
        locals.clear();
        assigned.clear();
        Func f = bs.funcType(currClass, currFunc);
        locals.putAll(f.names);
        assigned.addAll(f.names.keySet());

        super.visit(n);

        String have = visit(n.f10);
        String want = bs.funcs.get(currClass).get(currFunc).ret;
        if (!bs.canCast(have, want)) {
            throw new IllegalArgumentException();
        }

        currFunc = null;
        return null;
    }

    @Override
    public String visit(VarDeclaration n) {
        String name = n.f1.f0.toString();
        String type = bs.typeStr(n.f0);
        if (currFunc != null && locals.put(name, type) != null) {
            throw new IllegalArgumentException();
        }
        return null;
    }

    @Override
    public String visit(AssignmentStatement n) {
        inAssign = true; // AAAAAAAAAAAAAAAAA
        String id = visit(n.f0);
        inAssign = false;
        String expr = visit(n.f2);
        if (!bs.canCast(expr, id)) {
            throw new IllegalArgumentException();
        }
        assigned.add(n.f0.f0.toString());
        return null;
    }

    @Override
    public String visit(ArrayAssignmentStatement n) {
        String arr = visit(n.f0), ind = visit(n.f2), expr = visit(n.f5);
        if (!"int[]".equals(arr) || !"int".equals(ind) || !"int".equals(expr)) {
            throw new IllegalArgumentException();
        }
        return null;
    }

    @Override
    public String visit(IfStatement n) {
        String cond = visit(n.f2);
        if (!"boolean".equals(cond)) {
            throw new IllegalArgumentException();
        }
        return super.visit(n);
    }

    @Override
    public String visit(WhileStatement n) {
        String cond = visit(n.f2);
        if (!"boolean".equals(cond)) {
            throw new IllegalArgumentException();
        }
        return super.visit(n);
    }

    @Override
    public String visit(PrintStatement n) {
        String toPrint = visit(n.f2);
        if (!"int".equals(toPrint)) {
            throw new IllegalArgumentException();
        }
        return null;
    }

    @Override
    public String visit(Expression n) {
        return n.f0.accept(this);
    }

    @Override
    public String visit(AndExpression n) {
        String t1 = visit(n.f0), t2 = visit(n.f2);
        if (!"boolean".equals(t1) || !"boolean".equals(t2)) {
            throw new IllegalArgumentException();
        }
        return t1;
    }

    @Override
    public String visit(CompareExpression n) {
        String t1 = visit(n.f0), t2 = visit(n.f2);
        if (!"int".equals(t1) || !"int".equals(t2)) {
            throw new IllegalArgumentException();
        }
        return "boolean";
    }

    @Override
    public String visit(PlusExpression n) {
        String t1 = visit(n.f0), t2 = visit(n.f2);
        if (!"int".equals(t1) || !"int".equals(t2)) {
            throw new IllegalArgumentException();
        }
        return t1;
    }

    @Override
    public String visit(MinusExpression n) {
        String t1 = visit(n.f0), t2 = visit(n.f2);
        if (!"int".equals(t1) || !"int".equals(t2)) {
            throw new IllegalArgumentException();
        }
        return t1;
    }

    @Override
    public String visit(TimesExpression n) {
        String t1 = visit(n.f0), t2 = visit(n.f2);
        if (!"int".equals(t1) || !"int".equals(t2)) {
            throw new IllegalArgumentException();
        }
        return t1;
    }

    @Override
    public String visit(ArrayLookup n) {
        String arr = visit(n.f0);
        String ind = visit(n.f2);
        if (!"int[]".equals(arr) || !"int".equals(ind)) {
            throw new IllegalArgumentException();
        }
        return "int";
    }

    @Override
    public String visit(ArrayLength n) {
        String arr = visit(n.f0);
        if ("int[]".equals(arr)) {
            throw new IllegalArgumentException();
        }
        return "int";
    }

    @Override
    public String visit(MessageSend n) {
        String obj = visit(n.f0);
        Func f = bs.funcType(obj, n.f2.f0.toString());
        if (f == null) {
            throw new IllegalArgumentException();
        }

        List<String> args = new ArrayList<>();
        if (n.f4.present()) {
            ExpressionList el = (ExpressionList) n.f4.node;
            args.add(visit(el.f0));
            for (int i = 0; i < el.f1.size(); i++) {
                ExpressionRest er = (ExpressionRest) el.f1.elementAt(i);
                args.add(visit(er.f1));
            }
        }

        if (args.size() != f.args.size()) {
            throw new IllegalArgumentException();
        }
        for (int i = 0; i < f.args.size(); i++) {
            if (!bs.canCast(args.get(i), f.args.get(i))) {
                throw new IllegalArgumentException();
            }
        }

        return f.ret;
    }

    @Override
    public String visit(PrimaryExpression n) {
        return n.f0.accept(this);
    }

    @Override
    public String visit(IntegerLiteral n) {
        return "int";
    }

    @Override
    public String visit(TrueLiteral n) {
        return "boolean";
    }

    @Override
    public String visit(FalseLiteral n) {
        return "boolean";
    }

    @Override
    public String visit(Identifier n) {
        String name = n.f0.toString();
        List<String> cand = new ArrayList<>(Arrays.asList(locals.get(name)));
        if (currClass != null) {
            cand.add(bs.attrType(currClass, name));
        }

        for (int i = 0; i < cand.size(); i++) {
            String type = cand.get(i);
            if (type != null) {
                if (i == 0) {
                    return assigned.contains(name) || inAssign ? type : null;
                }
                return type;
            }
        }
        return null;
    }

    @Override
    public String visit(ThisExpression n) {
        return currClass;
    }

    @Override
    public String visit(ArrayAllocationExpression n) {
        String ret = visit(n.f3);
        if (!"int".equals(ret)) {
            throw new IllegalArgumentException();
        }
        return "int[]";
    }

    @Override
    public String visit(AllocationExpression n) {
        String className = n.f1.f0.toString();
        if (!bs.types.containsKey(className)) {
            throw new IllegalArgumentException();
        }
        return className;
    }

    @Override
    public String visit(NotExpression n) {
        String ret = visit(n.f1);
        if (!"boolean".equals(ret)) {
            throw new IllegalArgumentException();
        }
        return ret;
    }

    @Override
    public String visit(BracketExpression n) {
        return visit(n.f1);
    }

    public static void main(String[] args) {
        try {
            Goal root = new MiniJavaParser(System.in).Goal();
            System.out.println(
                    new Typecheck(root).bad() ? "Type error"
                            : "Program type checked successfully");
        } catch (ParseException e) {
            System.out.println(e.toString());
        }
    }
}

class BasicStuff extends DepthFirstVisitor {
    public static final String LMAO = "StupidTypeIPutHereOnlyForMain";
    private static final String[] WHICH_TYPE = new String[] {
            "int[]", "boolean", "int", LMAO
    };

    // all types and the type they extend (it's themself if nothing)
    public Map<String, String> types = new HashMap<>() {
        {
            put("int", "int");
            put("int[]", "int[]");
            put("boolean", "boolean");
            put(LMAO, LMAO);
        }
    };
    public Map<String, Map<String, String>> attrs = new HashMap<>();
    public Map<String, Map<String, Func>> funcs = new HashMap<>();

    private String currClass;
    private String currFunc;

    public BasicStuff(Goal root) {
        root.accept(this);
        for (String c : types.keySet()) {
            String at = c;
            while (!types.get(at).equals(at)) {
                at = types.get(at);
                if (at == c || !types.containsKey(at)) {
                    throw new IllegalArgumentException();
                }
            }
        }

        for (Map.Entry<String, Map<String, Func>> c : funcs.entrySet()) {
            for (Map.Entry<String, Func> f : c.getValue().entrySet()) {
                Func func = f.getValue();
                if (!types.containsKey(func.ret)
                        || func.args.stream().anyMatch(t -> !types.containsKey(t))) {
                    throw new IllegalArgumentException();
                }

                String at = c.getKey();
                while (!types.get(at).equals(at)) {
                    at = types.get(at);
                    Func up = funcs.get(at).get(f.getKey());
                    if (up != null && (!up.args.equals(func.args)
                            || !up.ret.equals(func.ret))) {
                        throw new IllegalArgumentException();
                    }
                }
            }
        }

        for (Map<String, String> a : attrs.values()) {
            if (a.values().stream().anyMatch(n -> !types.containsKey(n))) {
                throw new IllegalArgumentException();
            }
        }
    }

    public String typeStr(Type n) {
        NodeChoice nc = n.f0;
        if (nc.which == 3) {
            return ((Identifier) nc.choice).f0.toString();
        }
        return WHICH_TYPE[nc.which];
    }

    public boolean canCast(String have, String want) {
        if (have == null || want == null) {
            return false;
        }
        if (Objects.equals(have, want)) {
            return true;
        }
        while (!Objects.equals(types.get(have), have)) {
            have = types.get(have);
            if (Objects.equals(have, want)) {
                return true;
            }
        }
        return false;
    }

    public String attrType(String myClass, String attr) {
        if (myClass == null) {
            return null;
        }
        String res = attrs.get(myClass).get(attr);
        if (res != null) {
            return res;
        }
        while (!Objects.equals(types.get(myClass), myClass)) {
            myClass = types.get(myClass);
            res = attrs.get(myClass).get(attr);
            if (res != null) {
                return res;
            }
        }
        return null;
    }

    public Func funcType(String myClass, String func) {
        if (funcs.get(myClass) == null) {
            return null;
        }
        Func res = funcs.get(myClass).get(func);
        if (res != null) {
            return res;
        }
        while (!Objects.equals(types.get(myClass), myClass)) {
            myClass = types.get(myClass);
            res = funcs.get(myClass).get(func);
            if (res != null) {
                return res;
            }
        }
        return null;
    }

    @Override
    public void visit(MainClass n) {
        String name = n.f1.f0.toString();
        newClass(name, name);
        currFunc = "main";
        Func f = new Func(currFunc, new ArrayList<>(Arrays.asList(LMAO)),
                new HashMap<>());
        f.names.put(n.f11.f0.toString(), LMAO);
        super.visit(n);
        currFunc = null;
    }

    @Override
    public void visit(ClassDeclaration n) {
        String name = n.f1.f0.toString();
        newClass(name, name);
        super.visit(n);
    }

    @Override
    public void visit(ClassExtendsDeclaration n) {
        newClass(n.f1.f0.toString(), n.f3.f0.toString());
        super.visit(n);
    }

    @Override
    public void visit(VarDeclaration n) {
        String name = n.f1.f0.toString();
        String type = typeStr(n.f0);
        if (currFunc == null) {
            if (attrs.get(currClass).put(name, type) != null) {
                throw new IllegalArgumentException();
            }
        }
        super.visit(n);
    }

    private void newClass(String name, String par) {
        funcs.put(name, new HashMap<>());
        attrs.put(name, new HashMap<>());
        currClass = name;
        if (types.put(name, par) != null) {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public void visit(MethodDeclaration n) {
        String name = n.f2.f0.toString();
        currFunc = name;
        String ret = typeStr(n.f1);
        Func f = new Func(ret, new ArrayList<>(), new HashMap<>());
        if (funcs.get(currClass).put(name, f) != null) {
            throw new IllegalArgumentException();
        }
        super.visit(n);
        currFunc = null;
    }

    @Override
    public void visit(FormalParameter n) {
        String name = n.f1.f0.toString();
        String type = typeStr(n.f0);
        Func f = funcs.get(currClass).get(currFunc);
        if (f.names.put(name, type) != null) {
            throw new IllegalArgumentException();
        }
        f.args.add(type);
        super.visit(n);
    }
}

class Func {
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
