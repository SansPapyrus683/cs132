import java.util.*;
import minijava.ParseException;
import minijava.visitor.*;
import minijava.syntaxtree.*;
import minijava.MiniJavaParser;

public class J2S extends GJNoArguDepthFirst<String> {
    private final Goal root;
    private final BasicStuff bs;
    private final MemLayout mem;
    private final Map<String, Map<String, String>> actualFuncs = new HashMap<>();

    private int tmp = 0;
    private String currClass;
    private String currFunc;
    private Map<String, String> vars = new HashMap<>();

    public J2S(Goal root) {
        if (new Typecheck(root).bad()) {
            throw new IllegalArgumentException("code doesn't even typecheck bro");
        }
        this.root = root;
        // sure, typecheck already has this as a member variable but, uh, idc
        bs = new BasicStuff(root);
        mem = new MemLayout(bs);
        for (String type : bs.funcs.keySet()) {
            if (bs.funcs.containsKey(type)) {
                getActualFID(type);
            }
        }
    }

    private void getActualFID(String type) {
        if (actualFuncs.containsKey(type)) {
            return;
        }
        Map<String, String> resolver = new HashMap<>();
        String parent = bs.types.get(type);
        if (!type.equals(parent)) {
            getActualFID(parent);
            resolver.putAll(actualFuncs.get(parent));
        }
        for (Map.Entry<String, Func> f : bs.funcs.get(type).entrySet()) {
            resolver.put(f.getKey(), f.getValue().name);
        }
        actualFuncs.put(type, resolver);
    }

    public void compile() {
        root.accept(this);
    }

    @Override
    public String visit(MainClass n) {
        currClass = n.f1.f0.toString();
        currFunc = "main";
        System.out.println("func main()");
        vars.clear();
        super.visit(n);
        returnStuff("v0");
        currClass = currFunc = null;
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
        Func f = bs.funcType(currClass, currFunc);

        StringBuilder decl = new StringBuilder("func ");
        decl.append(BasicStuff.fid(currClass, currFunc)).append("(this ");
        for (String arg : f.binds.keySet()) {
            decl.append(arg).append(' ');
        }
        decl.setLength(decl.length() - 1);
        decl.append(')');
        System.out.println(decl);

        vars = new HashMap<>(f.binds);
        tmp = 0;
        super.visit(n);

        String ret = visit(n.f10);

        returnStuff(ret);
        currFunc = null;
        return null;
    }

    public void returnStuff(String toRet) {
        prLine("goto end");
        prLabel("null");
        prLine("error(\"null pointer\")");
        prLabel("oob");
        prLine("error(\"array index out of bounds\")");
        prLabel("end");
        prLine("return %s%n", toRet);
    }

    @Override
    public String visit(VarDeclaration n) {
        if (currFunc == null) {
            return null;
        }
        String name = n.f1.f0.toString();
        String type = bs.typeStr(n.f0);
        vars.put(name, type);
        return null;
    }

    @Override
    public String visit(AssignmentStatement n) {
        String id = n.f0.f0.toString();
        String expr = visit(n.f2);
        prLine("%s = %s", id, expr);
        return null;
    }

    @Override
    public String visit(ArrayAssignmentStatement n) {
        String arr = visit(n.f0), ind = visit(n.f2), expr = visit(n.f5);
        String ret = getAddr(arr, ind);
        prLine("[%s + 0] = %s", ret, expr);
        return null;
    }

    public String getAddr(String arr, String ind) {
        String ret = newTmp();
        prLine("%s = [%s + 0]", ret, arr); // first length of the array
        prLine("%s = %s + 1", ret, ret);
        prLine("%s = %s < %s", ret, ind, ret);
        prLine("if0 %s goto oob", ret);
        prLine("v0 = 4");
        prLine("%s = v0 * %s", ret, ind); // get byte offset
        prLine("%s = %s + %s", ret, arr, ret); // add array base
        return ret;
    }

    @Override
    public String visit(IfStatement n) {
        String cond = visit(n.f2);
        String elseLabel = newTmp();
        String doneLabel = newTmp();
        prLine("if0 %s goto %s", cond, elseLabel);
        super.visit(n.f4);
        prLine("goto %s", doneLabel);
        prLabel(elseLabel);
        super.visit(n.f6);
        prLabel(doneLabel);
        return null;
    }

    @Override
    public String visit(WhileStatement n) {
        String startLabel = newTmp();
        prLabel(startLabel);
        String cond = super.visit(n.f2);
        String doneLabel = newTmp();
        prLine("if0 %s goto %s", cond, doneLabel);
        super.visit(n.f4);
        prLabel(doneLabel);
        return null;
    }

    @Override
    public String visit(PrintStatement n) {
        String id = visit(n.f2);
        prLine("print(%s)", id);
        return null;
    }

    @Override
    public String visit(Expression n) {
        return n.f0.accept(this);
    }

    @Override
    public String visit(AndExpression n) {
        String t1 = visit(n.f0), t2 = visit(n.f2);
        String ret = newTmp();
        prLine("%s = %s + %s", ret, t1, t2);
        prLine("%s = 1 < %s", ret, ret);
        return ret;
    }

    @Override
    public String visit(CompareExpression n) {
        String t1 = visit(n.f0), t2 = visit(n.f2);
        String ret = newTmp();
        prLine("%s = %s < %s", ret, t1, t2);
        return ret;
    }

    @Override
    public String visit(PlusExpression n) {
        String t1 = visit(n.f0), t2 = visit(n.f2);
        String ret = newTmp();
        prLine("%s = %s + %s", ret, t1, t2);
        return ret;
    }

    @Override
    public String visit(MinusExpression n) {
        String t1 = visit(n.f0), t2 = visit(n.f2);
        String ret = newTmp();
        prLine("%s = %s - %s", ret, t1, t2);
        return ret;
    }

    @Override
    public String visit(TimesExpression n) {
        String t1 = visit(n.f0), t2 = visit(n.f2);
        String ret = newTmp();
        prLine("%s = %s * %s", ret, t1, t2);
        return ret;
    }

    @Override
    public String visit(ArrayLookup n) {
        String arr = visit(n.f0);
        String ind = visit(n.f2);
        String ret = getAddr(arr, ind);
        prLine("%s = [%s + 0]", ret, ret);  // actually access it
        return ret;
    }

    @Override
    public String visit(MessageSend n) {
        String callOn = visit(n.f0);
        prLine("if0 %s goto null", callOn);
        String type = callOn.equals("this") ? currClass : vars.get(callOn);

        StringBuilder args = new StringBuilder(callOn).append(' ');
        if (n.f4.present()) {
            ExpressionList el = (ExpressionList) n.f4.node;
            args.append(visit(el.f0)).append(' ');
            for (int i = 0; i < el.f1.size(); i++) {
                ExpressionRest er = (ExpressionRest) el.f1.elementAt(i);
                args.append(visit(er.f1)).append(' ');
            }
        }
        args.setLength(args.length() - 1);

        String ret = newTmp();
        prLine("%s = [%s + 0]", ret, callOn);
        int ind = mem.funcLoc.get(type).get(n.f2.f0.toString());
        prLine("%s = [%s + %d]", ret, ret, ind * 4);
        prLine("%s = call %s(%s)", ret, ret, args.toString());
        return ret;
    }

    @Override
    public String visit(PrimaryExpression n) {
        return n.f0.accept(this);
    }

    @Override
    public String visit(IntegerLiteral n) {
        String tmp = newTmp();
        prLine("%s = %s", tmp, n.f0.toString());
        return tmp;
    }

    @Override
    public String visit(TrueLiteral n) {
        String tmp = newTmp();
        prLine("%s = 1", tmp);
        return tmp;
    }

    @Override
    public String visit(FalseLiteral n) {
        String tmp = newTmp();
        prLine("%s = 0", tmp);
        return tmp;
    }

    @Override
    public String visit(Identifier n) {
        String id = n.f0.toString();
        if (vars.containsKey(id)) {
            return id;
        }
        Map<String, Integer> lookup = mem.attrLoc.get(currClass);
        if (!lookup.containsKey(id)) {
            return null;
        }
        String ret = newTmp();
        prLine("%s = [this + %d]", ret, lookup.get(id) * 4);
        return ret;
    }

    @Override
    public String visit(ThisExpression n) {
        return "this";
    }

    @Override
    public String visit(ArrayAllocationExpression n) {
        String len = visit(n.f3);
        String tmp = newTmp();
        prLine("v1 = 4");
        prLine("%s = %s * v1", tmp, len);
        prLine("%s = %s + v1", tmp, tmp);
        prLine("%s = alloc(%s)", tmp, tmp);
        return tmp;
    }

    @Override
    public String visit(AllocationExpression n) {
        String ret = newTmp();
        String className = n.f1.f0.toString();

        int num = mem.attrLoc.get(className).size() + 1;
        prLine("v1 = %d", num * 4);
        prLine("%s = alloc(v1)", ret);

        String funcTable = newTmp();
        prLine("v1 = %d", mem.funcLoc.get(className).size() * 4);
        prLine("%s = alloc(v1)", funcTable);
        prLine("[%s + 0] = %s", ret, funcTable);
        Map<String, String> funcs = actualFuncs.get(className);
        String tmp = newTmp();
        for (Map.Entry<String, Integer> ml : mem.funcLoc.get(className).entrySet()) {
            prLine("%s = @%s", tmp, funcs.get(ml.getKey()));
            prLine("[%s + %d] = %s", funcTable, ml.getValue() * 4, tmp);
        }

        vars.put(ret, className);
        return ret;
    }

    @Override
    public String visit(NotExpression n) {
        String toNot = visit(n.f1);
        String ret = newTmp();
        prLine("v2 = 1");
        prLine("%s = v2 - %s", ret, toNot);
        return ret;
    }

    @Override
    public String visit(BracketExpression n) {
        return visit(n.f1);
    }

    @Override
    public String visit(ArrayLength n) {
        String arr = visit(n.f0);
        String ret = newTmp();
        prLine("%s = [%s + 0]", ret, arr);
        return ret;
    }

    private String newTmp() {
        return String.format("asdf%d", tmp++);
    }

    private void prLabel(String label) {
        System.out.printf("  %s:%n", label);
    }

    private void prLine(String fmt, Object... args) {
        System.out.printf("    " + fmt + "%n", args);
    }

    public static void main(String[] args) {
        try {
            Goal root = new MiniJavaParser(System.in).Goal();
            new J2S(root).compile();
        } catch (ParseException e) {
            System.out.println(e.toString());
        }
    }
}
