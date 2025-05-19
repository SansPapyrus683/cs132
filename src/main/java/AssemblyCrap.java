import java.util.*;
import IR.token.Identifier;
import sparrowv.visitor.DepthFirst;
import sparrowv.*;

public class AssemblyCrap extends DepthFirst {
    public final Map<String, Map<String, Integer>> offsets = new HashMap<>();
    public final Map<String, Integer> errors = new HashMap<>();

    private Map<String, Integer> curr;
    private int offsetAt;

    public AssemblyCrap(Program prog) {
        prog.accept(this);
    }

    @Override
    public void visit(FunctionDecl func) {
        String name = func.functionName.toString();
        if (name.equalsIgnoreCase("Main")) {
            name = "Main";
        }
        curr = new HashMap<>();
        offsets.put(name, curr);

        for (int i = 0; i < func.formalParameters.size(); i++) {
            String param = func.formalParameters.get(i).toString();
            curr.put(param, i * 4);
        }

        // we need some extra bytes for the return address & the frame pointer
        offsetAt = -12;
        visit(func.block);
    }

    private void addVar(Identifier var) {
        if (!curr.containsKey(var.toString())) {
            // some extra space just to be safe
            curr.put(var.toString(), offsetAt);
            offsetAt -= 4;
        }
    }

    @Override
    public void visit(Move_Id_Reg i) {
        addVar(i.lhs);
    }

    @Override
    public void visit(Move_Reg_Id i) {
        addVar(i.rhs);
    }

    @Override
    public void visit(ErrorMessage i) {
        if (!errors.containsKey(i.msg)) {
            errors.put(i.msg, errors.size());
        }
    }
}
