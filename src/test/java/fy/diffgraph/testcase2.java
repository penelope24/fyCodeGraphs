package fy.diffgraph;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import fy.cfg.entry.Config;
import fy.cfg.entry.MethodEntry;
import fy.cfg.entry.SolverEntry;
import fy.cfg.visitor.VarVisitor;
import fy.structures.StmtEdge;
import fy.structures.StmtVertex;
import org.jgrapht.Graph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

public class testcase2 {

    Graph<StmtVertex, StmtEdge> cg;

    @BeforeEach
    void init() throws FileNotFoundException {
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
            try {
                MethodEntry.gen(m, varVisitor, output, prop);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    @Test
    public void test() {
        System.out.println(cg);

    }
}
