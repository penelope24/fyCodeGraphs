package fy.structures;


import java.util.HashMap;

public class NodeAttrs {
    private final String dotNum;
    private final String label;
    private final int lineNum;
    private final HashMap<String, String> dict = new HashMap<>();
    private final String attrs;

    private int astNode = -1;

    public NodeAttrs(String label, int lineNum, String dotNum) {
        this.label = label;
        this.lineNum = lineNum;
        this.dotNum = dotNum;
        this.attrs = label + "$$" + lineNum;
        this.dict.put(dotNum, attrs);
    }

    public String keyString() {
        return dotNum;
    }

    public String getDotNum() {
        return dotNum;
    }

    public String getLabel() {
        return label;
    }

    public int getLineNum() {
        return lineNum;
    }

    public HashMap<String, String> getDict() {
        return dict;
    }

    public String getAttrs() {
        return attrs;
    }

    public int getAstNode() {
        return astNode;
    }

    public void setAstNode(int astNode) {
        this.astNode = astNode;
    }
}
