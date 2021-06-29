package fy.cfg.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ASTCreaterTest {

    @Test
    void debug(){
        String buggy = "// \n" +
                "fieldInfo.getMember() != null && !ASMUtils.checkName(fieldInfo.getMember().getName())";
        String fix = buggy.replaceAll("//\\s+\\n", "");
        System.out.println(fix);
    }

}