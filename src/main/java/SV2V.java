import java.util.*;

import IR.ParseException;
import IR.SparrowParser;
import IR.registers.Registers;
import IR.token.Label;
import IR.syntaxtree.Node;
import IR.visitor.SparrowVConstructor;
import sparrowv.*;
import sparrowv.visitor.DepthFirst;

public class SV2V extends DepthFirst {
    // lol. lmao even.
    private String STARTUP = ".equiv @sbrk, 9\n" +
            ".equiv @print_string, 4\n" +
            ".equiv @print_char, 11\n" +
            ".equiv @print_int, 1\n" +
            ".equiv @exit 10\n" +
            ".equiv @exit2, 17\n\n" +
            ".text\n" +
            "    jal Main\n" +
            "    li a0, @exit\n" +
            "    ecall\n";
    private String OTHER_STUFF = ".globl error\n" +
            "error:\n" +
            "    mv a1, a0\n" +
            "    li a0, @print_string\n" +
            "    ecall\n" +
            "    li a1, 10\n" +
            "    li a0, @print_char\n" +
            "    ecall\n" +
            "    li a0, @exit\n" +
            "    ecall\n" +
            "abort_17:\n" +
            "    j abort_17\n\n" +
            ".globl alloc\n" +
            "alloc:\n" +
            "    mv a1, a0\n" +
            "    li a0, @sbrk\n" +
            "    ecall\n" +
            "    jr ra";

    private final Program prog;
    private final AssemblyCrap ac;
    
    private String func;
    private Map<String, Integer> offsets;
    private int tempAt = 0;  // wow, didn't know i'd need one of these again

    public SV2V(Program prog) {
        this.prog = prog;
        ac = new AssemblyCrap(prog);
    }

    public void toAssembly() {
        System.out.println(STARTUP);
        prog.accept(this);
        System.out.println(OTHER_STUFF);

        System.out.println();
        System.out.println(".data");
        for (Map.Entry<String, Integer> e : ac.errors.entrySet()) {
            prLabel(".globl err%s", e.getValue());
            prLabel("err%s:", e.getValue());
            prLine(".asciiz %s", e.getKey());
            prLine(".align 2");
        }
    }

    @Override
    public void visit(FunctionDecl f) {
        func = f.functionName.toString();
        if (func.equalsIgnoreCase("Main")) {
            func = "Main";
        }
        
        prLabel(".globl %s", func);
        prLabel("%s:", func);
        prLine("sw fp, -8(sp)");
        prLine("mv fp, sp");

        offsets = ac.offsets.get(func);
        int offset = -offsets.values().stream().min(Integer::compare).get();
        prLine("li t6, %s", offset);
        prLine("sub sp, sp, t6"); // t0 should've worked too but idk
        prLine("sw ra, -4(fp)");

        visit(f.block);

        int argNum = f.formalParameters.size();

        prLine("lw ra, -4(fp)");
        prLine("lw fp, -8(fp)");
        prLine("addi sp, sp, %d", offset);
        prLine("addi sp, sp, %d", argNum * 4);
        prLine("jr ra");
        System.out.println();
    }

    @Override
    public void visit(Block b) {
        for (Instruction i : b.instructions) {
            i.accept(this);
            System.out.println();
        }

        String rid = b.return_id.toString();
        prLine("lw a0, %d(fp)", offsets.get(rid));
    }

    @Override
    public void visit(LabelInstr n) {
        prLabel("%s:", labelHack(n.label));
    }

    @Override
    public void visit(Move_Reg_Integer n) {
        prLine("li %s, %d", n.lhs, n.rhs);
    }

    @Override
    public void visit(Move_Reg_FuncName n) {
        prLine("la %s, %s", n.lhs, n.rhs);
    }

    @Override
    public void visit(Add n) {
        prLine("add %s, %s, %s", n.lhs, n.arg1, n.arg2);
    }

    @Override
    public void visit(Subtract n) {
        prLine("sub %s, %s, %s", n.lhs, n.arg1, n.arg2);
    }

    @Override
    public void visit(Multiply n) {
        prLine("mul %s, %s, %s", n.lhs, n.arg1, n.arg2);
    }

    @Override
    public void visit(LessThan n) {
        prLine("slt %s, %s, %s", n.lhs, n.arg1, n.arg2);
    }

    @Override
    public void visit(Load n) {
        prLine("lw %s, %d(%s)", n.lhs, n.offset, n.base);
    }

    @Override
    public void visit(Store n) {
        prLine("sw %s, %d(%s)", n.rhs, n.offset, n.base);
    }

    @Override
    public void visit(Move_Reg_Reg n) {
        prLine("add %s, %s, zero", n.lhs, n.rhs);
    }

    @Override
    public void visit(Move_Id_Reg n) {
        prLine("sw %s, %d(fp)", n.rhs, offsets.get(n.lhs.toString()));
    }

    @Override
    public void visit(Move_Reg_Id n) {
        prLine("lw %s, %d(fp)", n.lhs, offsets.get(n.rhs.toString()));
    }

    @Override
    public void visit(Alloc n) {
        prLine("mv a0, %s", n.size);
        prLine("jal alloc");
        prLine("mv %s, a0", n.lhs);
    }

    @Override
    public void visit(Print n) {
        prLine("mv a1, %s", n.content);
        prLine("li a0, @print_int");
        prLine("ecall");
        prLine("li a1, 10");
        prLine("li a0, @print_char");
        prLine("ecall");
    }

    @Override
    public void visit(ErrorMessage n) {
        prLine("la a0, err%d", ac.errors.get(n.msg));
        prLine("j error");
    }

    @Override
    public void visit(Goto n) {
        prLine("j %s", labelHack(n.label));
    }

    @Override
    public void visit(IfGoto n) {
        String a = newLabel(), b = newLabel(), c = newLabel();
        prLine("beqz %s, %s", n.condition, b);
        prLabel("%s:", a);
        prLine("j %s", c);
        prLabel("%s:", b);
        prLine("j %s", labelHack(n.label));
        prLabel("%s:", c);
    }

    @Override
    public void visit(Call n) {
        prLine("li t6, %d", n.args.size() * 4);
        prLine("sub sp, sp, t6");
        for (int i = 0; i < n.args.size(); i++) {
            String id = n.args.get(i).toString();
            prLine("lw t6, %d(fp)", offsets.get(id));
            prLine("sw t6, %d(sp)", i * 4);
        }
        prLine("jalr %s", n.callee);
        prLine("mv %s, a0", n.lhs);
    }

    private String newLabel() {
        return "if_hack_" + (tempAt++);
    }

    private String labelHack(Label label) {
        return "func_" + func + "_" + label;
    }

    /** yeah, here it is again LMAO */
    private void prLabel(String fmt, Object... args) {
        System.out.printf(fmt + "%n", args);
    }

    private void prLine(String fmt, Object... args) {
        prLabel("    " + fmt, args);
    }

    public static void main(String[] args) {
        try {
            Registers.SetRiscVregs();
            new SparrowParser(System.in);
            Node root = SparrowParser.Program();
            SparrowVConstructor constructor = new SparrowVConstructor();
            root.accept(constructor);
            new SV2V(constructor.getProgram()).toAssembly();
        } catch (ParseException e) {
            System.out.println(e);
        }
    }
}
