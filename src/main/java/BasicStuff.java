import java.util.*;
import minijava.visitor.*;
import minijava.syntaxtree.*;

public class BasicStuff extends DepthFirstVisitor {
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
                        || func.types.stream().anyMatch(t -> !types.containsKey(t))) {
                    throw new IllegalArgumentException();
                }

                String at = c.getKey();
                while (!types.get(at).equals(at)) {
                    at = types.get(at);
                    Func up = funcs.get(at).get(f.getKey());
                    if (up != null && (!up.types.equals(func.types)
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

    private String myName() {
        // ok if your class name screws this up that's on you sorry
        return String.format("%sFUNC%s", currClass, currFunc);
    }

    @Override
    public void visit(MainClass n) {
        String name = n.f1.f0.toString();
        newClass(name, name);
        currFunc = "main";
        Func f = new Func(myName(), LMAO, new ArrayList<>(Arrays.asList(LMAO)),
                new HashMap<>());
        f.binds.put(n.f11.f0.toString(), LMAO);
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
        Func f = new Func(myName(), ret, new ArrayList<>(), new HashMap<>());
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
        if (f.binds.put(name, type) != null) {
            throw new IllegalArgumentException();
        }
        f.types.add(type);
        super.visit(n);
    }
}
