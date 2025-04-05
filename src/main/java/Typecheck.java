import java.io.*;
import java.util.*;

import minijava.ParseException;
import minijava.visitor.*;
import minijava.syntaxtree.*;
import minijava.MiniJavaParser;

public class Typecheck {
    public static void main(String[] args) {
        try {
            Node root = new MiniJavaParser(System.in).Goal();
            System.out.println("Program parsed successfully");
            root.accept(new GJNoArguDepthFirst());
        }
        catch (ParseException e) {
            System.out.println(e.toString());
        }
    }
}
