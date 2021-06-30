package fy.structures;

import java.util.ArrayList;
import java.util.List;

public class NodeAttrs {
    private final String dotNum;

    private String label;
    private int lineNum;
    private int astNode = -1;

    public NodeAttrs(String label, int lineNum, String dotNum) {
        this.label = label;
        this.lineNum = lineNum;
        this.dotNum = dotNum;
    }

    public String keyString() {
        return dotNum;
    }

    public void setAstNode(int astNode) {
        this.astNode = astNode;
    }

}
