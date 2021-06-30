package fy.cfg.entry;


import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import fy.cfg.visitor.VarVisitor;
import fy.cfg.visitor.MethodVisitor;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class SingleFileEntry {

    public static void gen(HashMap<String, Set<String>> pkg2types, String target, String output, Properties prop) throws FileNotFoundException {
        VarVisitor varVisitor = SolverEntry.solveVarTypesInFile(target, pkg2types);
        CompilationUnit cu = StaticJavaParser.parse(new File(target));
        // core
        MethodVisitor methodVisitor = new MethodVisitor(varVisitor, output, prop);
        methodVisitor.visit(cu, null);
    }

    public static void main(String[] args) throws FileNotFoundException {
        String project = "D:\\my_github_projects\\fyCodeGraphs";
        String target1 = "src/main/java/fy/cfg/parse/ASTCreater.java";
        String target2 = "src/main/java/fy/cfg/parse/CFGCreator.java";
        String output = "D:\\data\\test\\test";
        File outputDir = new File(output);
        if (!outputDir.exists()) {
            outputDir.mkdir();
        }
        Properties prop = Config.loadProperties();
        HashMap<String, Set<String>> pkg2types = SolverEntry.solve_pkg2types(project);
        SingleFileEntry.gen(pkg2types, target1, output, prop);
        SingleFileEntry.gen(pkg2types, target2, output, prop);
    }
}
