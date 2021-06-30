package fy.cfg.issues;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import fy.cfg.fore.SingleFileEntry;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;

public class Issue1 {

    @Test
    void reproduce() throws FileNotFoundException {
        String buggy = "// \n" +
                "fieldInfo.getMember() != null && !ASMUtils.checkName(fieldInfo.getMember().getName())";
        String fix = buggy.replaceAll("//\\s+\\n", "");
        System.out.println(fix);
    }

    @Test
    void test() throws FileNotFoundException {
        String javaFile = "src/main/java/fy/cfg/visitor/GlobalVarVisitor.java";
        CompilationUnit cu = StaticJavaParser.parse(new File(javaFile));
        MethodDeclaration n = cu.findFirst(MethodDeclaration.class).get();
        System.out.println(n.findRootNode().getClass());
    }
}
