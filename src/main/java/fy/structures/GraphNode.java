package fy.structures;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 图节点实体类
 */
public class GraphNode {

    /**
     * originalCodeStr 存储改节点对应的源代码操作 int a = 10
     * opTypeStr  节点的操作类型  Assignment
     * simplifyCodeStr  简化后的代码,抹去对于明确这个操作的变量名信息 int  = 10
     * isExceptionLabel 这个节点是否是有运行时异常的可能
     * parentNode 按照代码结构，包含这块代码的最近代码就是这个代码的父代码
     * adjacentPoints 和这个点相连的其他所有出度的节点，所以不用担心会有环路
     * edgs 每个连接点对应的边的信息，边可以有类型，权重等信息
     * codeLineNum 记录这个节点的源代码的行号信息
     * dotNum 记录这个节点在dot文件的编号
     * preAdjacentPoints 前驱节点 记录一个点的前驱节点信息
     */
    private String originalCodeStr;
    private String opTypeStr;
    private String simplifyCodeStr;
    private int codeLineNum;
    private String dotNum;


    private GraphNode parentNode;
    private List<GraphNode> adjacentPoints; //这是cfg构建的点 后继节点
    private List<GraphEdge> edgs; //这都是后继的边
    private List<GraphNode> preAdjacentPoints;//前驱节点 前驱节点没有边对应
    private AstNode astRootNode; //连接改graphnode的Ast树入口

    {
        adjacentPoints = new ArrayList<>();
        edgs = new ArrayList<>();
        preAdjacentPoints = new ArrayList<>();
    }

    public GraphNode() {
    }

    public GraphNode(String originalCodeStr, String opTypeStr) {
        this.originalCodeStr = originalCodeStr;
        this.opTypeStr = opTypeStr;
    }

    public GraphNode(String originalCodeStr, String opTypeStr, String simplifyCodeStr) {
        this.originalCodeStr = originalCodeStr;
        this.opTypeStr = opTypeStr;
        this.simplifyCodeStr = simplifyCodeStr;
    }

    public String getOriginalCodeStr() {
        return originalCodeStr;
    }

    public void setOriginalCodeStr(String originalCodeStr) {
        this.originalCodeStr = originalCodeStr;
    }

    public String getOpTypeStr() {
        return opTypeStr;
    }

    public void setOpTypeStr(String opTypeStr) {
        this.opTypeStr = opTypeStr;
    }

    public String getSimplifyCodeStr() {
        return simplifyCodeStr;
    }

    public void setSimplifyCodeStr(String simplifyCodeStr) {
        this.simplifyCodeStr = simplifyCodeStr;
    }

    public List<GraphNode> getAdjacentPoints() {
        return adjacentPoints;
    }

    //为了保证有序，而且不重复 所以需要排序重复的点
    public void addAdjacentPoint(GraphNode adjacentPoint) {
        if(!this.adjacentPoints.contains(adjacentPoint)){ //邻接点不应该重复
            this.adjacentPoints.add(adjacentPoint);
        }
    }

    //删除节点
    public void removeAdjacentPoint(GraphNode adjacentPoint) {
        this.adjacentPoints.remove(adjacentPoint);
    }

    public List<GraphEdge> getEdgs() {
        return edgs;
    }

    public void addEdg(GraphEdge edge) {
        //一个点的到宁外一个点的边的类型不应该重复，也就是A到B只需要一条cfg即可
        boolean insertFlug = true;
        for(GraphEdge e:this.edgs){
            if(e.getAimNode().equals(edge.getAimNode()) && e.getType().getColor().equals(edge.getType().getColor())){
                insertFlug = false;
                break;
            }
        }
        if(insertFlug){
            this.edgs.add(edge);
        }
    }

    public void removeEdges(GraphNode aimNode){
        //有时候只是知道目标点 需要移除是目标点的边
        Iterator<GraphEdge> iterator = this.edgs.iterator();
        while(iterator.hasNext()){
            GraphEdge next = iterator.next();
            if(next.getAimNode().equals(aimNode)){
                iterator.remove();
            }
        }
    }

    public int getCodeLineNum() {
        return codeLineNum;
    }

    public void setCodeLineNum(int codeLineNum) {
        this.codeLineNum = codeLineNum;
    }

    public GraphNode getParentNode() {
        return parentNode;
    }

    public void setParentNode(GraphNode parentNode) {
        this.parentNode = parentNode;
    }

    public AstNode getAstRootNode() {
        return astRootNode;
    }

    public void setAstRootNode(AstNode astRootNode) {
        this.astRootNode = astRootNode;
    }

    public String getDotNum() {
        return dotNum;
    }

    public void setDotNum(String dotNum) {
        this.dotNum = dotNum;
    }

    public List<GraphNode> getPreAdjacentPoints() {
        return preAdjacentPoints;
    }

    //添加前驱节点
    public void addPreAdjacentPoints(GraphNode preAdjacentPoint) {
        if(!this.preAdjacentPoints.contains(preAdjacentPoint)){ //邻接点不应该重复
            this.preAdjacentPoints.add(preAdjacentPoint);
        }
    }

    //删除前驱节点
    public void removePreAdjacentPoints(GraphNode preAdjacentPoint) {
        this.preAdjacentPoints.remove(preAdjacentPoint);
    }

    @Override
    public boolean equals(Object obj) {
        if(this==obj){
            return true;
        }
        if(obj==null){
            return false;
        }
        //必须内容相同：行号+源码
        if(obj instanceof GraphNode){
            GraphNode node = (GraphNode) obj;
            if(this.originalCodeStr.equals(node.getOriginalCodeStr()) && this.codeLineNum==node.codeLineNum){
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + codeLineNum;
        result = prime * result + ((originalCodeStr == null) ? 0 : originalCodeStr.hashCode());
        return result;
    }

//    @Override
//    public String toString() {
//        return "GraphNode{" +
//                "originalCodeStr='" + originalCodeStr + '\'' +
//                ", opTypeStr='" + opTypeStr + '\'' +
//                ", simplifyCodeStr='" + simplifyCodeStr + '\'' +
//                ", codeLineNum=" + codeLineNum +
//                ", dotNum='" + dotNum + '\'' +
//                ", parentNode=" + parentNode +
//                ", adjacentPoints=" + adjacentPoints +
//                ", edgs=" + edgs +
//                ", preAdjacentPoints=" + preAdjacentPoints +
//                ", astRootNode=" + astRootNode +
//                '}';
//    }
}
