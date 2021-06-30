package fy.cfg.entry;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import fy.cfg.visitor.MethodGraphCollect;
import fy.cfg.visitor.VarVisitor;
import fy.cfg.visitor.MethodVisitor;
import fy.structures.StmtEdge;
import fy.structures.StmtVertex;
import org.jgrapht.Graph;
import org.jgrapht.graph.DirectedMultigraph;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class MethodEntry{

    public static void gen(MethodDeclaration n, VarVisitor varVisitor, String output, Properties prop) {
        // core
//        MethodVisitor methodVisitor = new MethodVisitor(varVisitor, output, prop);
//        methodVisitor.visit(n, null);
        MethodGraphCollect collector = new MethodGraphCollect(varVisitor, output, prop);
        List<DirectedMultigraph> c = new ArrayList<>();
        collector.visit(n, c);
        System.out.println(c.get(0));

    }

    public static void main(String[] args) throws FileNotFoundException {
        String project = "D:\\my_github_projects\\fyCodeGraphs";
        String target = "src/main/java/fy/cfg/parse/ASTCreater.java";
        String output = "D:\\data\\test\\test";
        File outputDir = new File(output);
        if (!outputDir.exists()) {
            outputDir.mkdir();
        }
        Properties prop = Config.loadProperties();

        HashMap<String, Set<String>> pkg2types = SolverEntry.solve_pkg2types(project);
        VarVisitor varVisitor = SolverEntry.solveVarTypesInFile(target, pkg2types);
        CompilationUnit cu = StaticJavaParser.parse(new File(target));
        cu.findAll(MethodDeclaration.class).forEach(m -> {
            gen(m, varVisitor, output, prop);
        });
    }
}