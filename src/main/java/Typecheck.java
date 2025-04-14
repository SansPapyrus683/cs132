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
        locals.putAll(f.binds);
        assigned.addAll(f.binds.keySet());

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
        if (!"int[]".equals(arr)) {
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

        if (args.size() != f.types.size()) {
            throw new IllegalArgumentException();
        }
        for (int i = 0; i < f.types.size(); i++) {
            if (!bs.canCast(args.get(i), f.types.get(i))) {
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
