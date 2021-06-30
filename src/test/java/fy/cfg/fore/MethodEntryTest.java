package fy.cfg.fore;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.*;

class MethodEntryTest {
    String project = "D:\\my_github_projects\\fyCodeGraphs";
    String javaFile = "src/main/java/fy/cfg/visitor/GlobalVarVisitor.java";
    String output = "D:\\data\\test\\test";

    @Test
    void test_method_entry() throws FileNotFoundException {
        CompilationUnit cu = StaticJavaParser.parse(new File(javaFile));
        MethodDeclaration n = cu.findFirst(MethodDeclaration.class).get();
        MethodEntry.gen(project, n, output);
    }
}