package fy.cfg.parse;

import fy.cfg.structure.EdgeTypes;
import fy.cfg.structure.GraphEdge;
import fy.cfg.structure.GraphNode;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.*;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * 产生cfg 控制流
 */
public class CFGCreator {

    /**
     * allMethodsRootNode 记录一个方法体中所有的定义的方法的图入口
     * allNodesMap 为了方便找到任意节点<源代码+代码行号，node>
     * packageToAllType 当前package下所有的类的，接口等类型
     * imports 当前java文件所有的import的信息
     */
    private List<GraphNode> allMethodsRootNode;
    private HashMap<String,GraphNode> allNodesMap;

    public CFGCreator() {
        allMethodsRootNode = new ArrayList<>();
        allNodesMap = new HashMap<>();
    }

    /**
     * 构建cfg 直接传入node即可
     *
     * @param node 输入MethodDeclaration 节点
     * @return 返回这个方法体内部所有定义的方法：因为一个方法体内部可能有class的定义 class包含很多方法
     */
    public List<GraphNode> buildMethodCFG(Node node) {
        if (node instanceof MethodDeclaration) {
            MethodDeclaration methodDeclaration = ((MethodDeclaration) node).asMethodDeclaration();
            if(methodDeclaration.getParentNode().isPresent()) {
                if (!(methodDeclaration.getParentNode().get() instanceof TypeDeclaration)) {
                    return allMethodsRootNode; //专门针对于匿名对象 匿名对象的方法不处理
                }
            }
            if (methodDeclaration.getDeclarationAsString(false,false,true).equals("boolean equals(Object obj)")){
                System.out.println("daole");
            }
            System.out.println("********************************************");
            System.out.println("当前正在生成CFG方法的名字：" + methodDeclaration.getDeclarationAsString(false,false,true));
            System.out.println("********************************************");
            //创建方法名节点
            GraphNode graphNode = new GraphNode();
            graphNode.setOriginalCodeStr(methodDeclaration.getDeclarationAsString(false, true, true));
            graphNode.setOpTypeStr(methodDeclaration.getClass().toString());
            graphNode.setCodeLineNum(methodDeclaration.getBegin().isPresent()?methodDeclaration.getBegin().get().line:-1);
            allNodesMap.put(graphNode.getOriginalCodeStr()+":"+graphNode.getCodeLineNum(),graphNode);

            //创建注解节点
//            NodeList<AnnotationExpr> annotations = methodDeclaration.getAnnotations();
//            for(AnnotationExpr annotationExpr:annotations){
//                GraphNode tempNode = new GraphNode("@"+annotationExpr.getNameAsString(), "Annotation");
//                graphNode.addAdjacentPoint(tempNode);
//                graphNode.addEdg(new GraphEdge(EdgeTypes.CFG,graphNode,tempNode));
//                tempNode.setSimplifyCodeStr("@"+annotationExpr.getNameAsString());
//            }

            Optional<BlockStmt> body = methodDeclaration.getBody();
            GraphNode tempNode = graphNode;
            allMethodsRootNode.add(graphNode); //记录第一个入口
            if (body.isPresent()) {
                NodeList<Statement> statements = body.get().getStatements();
                for (Statement statement : statements) {
                    //开始递归创建，每处理一个statement返回一个node，这个node作为下一个state的前驱点
                    tempNode = buildCFG(tempNode, graphNode, statement); //外面这个方法节点是方法体所有state的父节点
                }
            }
            if (tempNode.getOriginalCodeStr().equals("#placeholder#")) {
                tempNode.setOriginalCodeStr("return");
                tempNode.setOpTypeStr("return");
                tempNode.setSimplifyCodeStr("return");
            }
        }
        //二次过滤
        for(GraphNode methodNode:allMethodsRootNode){
            buildCFG_2(methodNode);
        }
        //三次过滤
        buildCFG_3(node);
        return allMethodsRootNode;
    }

    /**
     * 给一个ast节点的入口：就能递归的构建cfg
     *
     * @param node        ast节点的一个入口
     * @param predecessor 前驱节点
     * @param parentNode  父节点：意思是根据代码结构划分的父子块级别
     * @return 返回的是新创建的node，也就是最后生成的node
     */
    private GraphNode buildCFG(GraphNode predecessor, GraphNode parentNode, Node node) {
        if (node instanceof ExpressionStmt) {
            ExpressionStmt exStmt = ((ExpressionStmt) node).asExpressionStmt();
            Expression expression = exStmt.getExpression();
            GraphNode graphNode = new GraphNode();
            if (!predecessor.getOriginalCodeStr().equals("#placeholder#")) {
                graphNode.setOriginalCodeStr(expression.toString());
                graphNode.setOpTypeStr(expression.getClass().toString());
                //先驱节点连上这个节点
                predecessor.addAdjacentPoint(graphNode);
                // 同时添加一条边进去
                predecessor.addEdg(new GraphEdge(EdgeTypes.CFG, predecessor, graphNode));
                graphNode.addPreAdjacentPoints(predecessor); // graphNode 添加前驱点
                graphNode.setCodeLineNum(expression.getBegin().isPresent()?expression.getBegin().get().line:-1);
            } else {
                predecessor.setOriginalCodeStr(expression.toString());
                predecessor.setOpTypeStr(expression.getClass().toString());
                predecessor.setCodeLineNum(expression.getBegin().isPresent()?expression.getBegin().get().line:-1);
                graphNode = predecessor;
            }
            graphNode.setParentNode(parentNode);
            allNodesMap.put(graphNode.getOriginalCodeStr()+":"+graphNode.getCodeLineNum(),graphNode);
            /*
            暂留 exception可能还需要根据具体的expression进行划分
             */
            return graphNode;
        } else if (node instanceof IfStmt) {
            // 能够改变控制流的结构
            IfStmt tempIfStmt = ((IfStmt) node).asIfStmt(); //最开始的if节点
            GraphNode placeholderNode = new GraphNode("#placeholder#", "placeholder");
            if(tempIfStmt.getEnd().isPresent()){
                placeholderNode.setCodeLineNum(tempIfStmt.getEnd().get().line+1);
            }else{
                Random random = new Random();
                placeholderNode.setCodeLineNum(random.nextInt(10000));
            }
            while (tempIfStmt != null) {
                GraphNode graphNode = new GraphNode(); //就是当前的ifStmt的Node
                if (!predecessor.getOriginalCodeStr().equals("#placeholder#")) {
                    graphNode.setOriginalCodeStr("if (" + tempIfStmt.getCondition().toString() + ")");
                    graphNode.setOpTypeStr(tempIfStmt.getClass().toString());
                    //先驱节点连上这个节点
                    predecessor.addAdjacentPoint(graphNode);
                    // 同时添加一条边进去
                    predecessor.addEdg(new GraphEdge(EdgeTypes.CFG, predecessor, graphNode));
                    graphNode.addPreAdjacentPoints(predecessor);
                    graphNode.setCodeLineNum(tempIfStmt.getBegin().isPresent()?tempIfStmt.getBegin().get().line:-1);
                } else {
                    predecessor.setOriginalCodeStr("if (" + tempIfStmt.getCondition().toString() + ")");
                    predecessor.setOpTypeStr(tempIfStmt.getClass().toString());
                    predecessor.setCodeLineNum(tempIfStmt.getBegin().isPresent()?tempIfStmt.getBegin().get().line:-1);
                    graphNode = predecessor;
                }
                graphNode.setParentNode(parentNode);
                allNodesMap.put(graphNode.getOriginalCodeStr()+":"+graphNode.getCodeLineNum(),graphNode);

                //先处理这个if节点和最后跳出的节点向量
                GraphNode tempNode = graphNode;
                if(!tempIfStmt.getThenStmt().isBlockStmt()){
                    // if后面没有括号 只有一行
                    tempNode = buildCFG(tempNode,graphNode,tempIfStmt.getThenStmt());
                }else{
                    BlockStmt thenBlockStmt = tempIfStmt.getThenStmt().asBlockStmt();
                    NodeList<Statement> statements = thenBlockStmt.getStatements();
                    for (Statement statement : statements) {
                        tempNode = buildCFG(tempNode, graphNode, statement);
                    }
                }

                if(tempNode.getOriginalCodeStr().equals("#placeholder#")){
                    //前驱
                    List<GraphNode> preAdjacentPoints = tempNode.getPreAdjacentPoints();
                    //所有前驱都连上这个placeholder
                    for(GraphNode before:preAdjacentPoints){
                        before.addAdjacentPoint(placeholderNode);
                        before.addEdg(new GraphEdge(EdgeTypes.CFG, before, placeholderNode));
                        placeholderNode.addPreAdjacentPoints(before);
                        //删除点和边是原子操作
                        before.getAdjacentPoints().remove(tempNode);//前驱节点把刚才的tempnode 移除
                        before.removeEdges(tempNode);
                    }
                }else {
                    tempNode.addAdjacentPoint(placeholderNode);
                    tempNode.addEdg(new GraphEdge(EdgeTypes.CFG, tempNode, placeholderNode));
                    placeholderNode.addPreAdjacentPoints(tempNode);
                }

                if (tempIfStmt.getElseStmt().isPresent()) {
                    if (tempIfStmt.getElseStmt().get().isIfStmt()) {
                        tempIfStmt = tempIfStmt.getElseStmt().get().asIfStmt();
                        predecessor = graphNode;
                    } else { //就是blockstmt
                        GraphNode elseNode = new GraphNode();
                        elseNode.setOriginalCodeStr("else");
                        elseNode.setOpTypeStr("else");
                        elseNode.setCodeLineNum(tempIfStmt.getElseStmt().get().getBegin().isPresent()?tempIfStmt.getElseStmt().get().getBegin().get().line:-1);
                        elseNode.setSimplifyCodeStr("else");
                        //if 需要连接上else
                        graphNode.addAdjacentPoint(elseNode);
                        graphNode.addEdg(new GraphEdge(EdgeTypes.CFG, graphNode, elseNode));
                        elseNode.addPreAdjacentPoints(graphNode);
                        elseNode.setParentNode(parentNode);
                        allNodesMap.put(elseNode.getOriginalCodeStr()+":"+elseNode.getCodeLineNum(),elseNode);

                        if(!tempIfStmt.getElseStmt().get().isBlockStmt()){
                            //if后面没有花括号的情况
                            tempNode = buildCFG(tempNode, elseNode, tempIfStmt.getElseStmt().get());
                        }else {
                            BlockStmt elseBlockStmt = tempIfStmt.getElseStmt().get().asBlockStmt();
                            NodeList<Statement> statements1 = elseBlockStmt.getStatements();
                            tempNode = elseNode;
                            for (Statement statement : statements1) {
                                tempNode = buildCFG(tempNode, elseNode, statement);
                            }
                        }
                        //tempNode 可能还是placeholder 所以把tempNode上面的前驱后继边信息都放过来
                        if(tempNode.getOriginalCodeStr().equals("#placeholder#")){
                            //前驱
                            List<GraphNode> preAdjacentPoints = tempNode.getPreAdjacentPoints();
                            //所有前驱都连上这个placeholder
                            for(GraphNode before:preAdjacentPoints){
                                before.addAdjacentPoint(placeholderNode);
                                before.addEdg(new GraphEdge(EdgeTypes.CFG, before, placeholderNode));
                                placeholderNode.addPreAdjacentPoints(before);
                                before.getAdjacentPoints().remove(tempNode);//前驱节点把刚才的tempnode 移除
                                before.removeEdges(tempNode);
                            }
                        }else {
                            tempNode.addAdjacentPoint(placeholderNode);
                            tempNode.addEdg(new GraphEdge(EdgeTypes.CFG, tempNode, placeholderNode));
                            placeholderNode.addPreAdjacentPoints(tempNode);
                        }
                        tempIfStmt = null;
                    }
                } else {
                    //if没有else情况
                    graphNode.addAdjacentPoint(placeholderNode);
                    graphNode.addEdg(new GraphEdge(EdgeTypes.CFG, graphNode, placeholderNode));
                    placeholderNode.addPreAdjacentPoints(graphNode);
                    tempIfStmt = null;
                }
            }
            return placeholderNode;

        } else if (node instanceof WhileStmt) {
            // 能够改变控制流的结构
            WhileStmt whileStmt = ((WhileStmt) node).asWhileStmt();
            GraphNode graphNode = new GraphNode();
            if (!predecessor.getOriginalCodeStr().equals("#placeholder#")) {
                graphNode.setOriginalCodeStr("while (" + whileStmt.getCondition().toString() + ")");
                graphNode.setOpTypeStr(whileStmt.getClass().toString());
                //先驱节点连上这个节点
                predecessor.addAdjacentPoint(graphNode);
                // 同时添加一条边进去
                predecessor.addEdg(new GraphEdge(EdgeTypes.CFG, predecessor, graphNode));
                graphNode.addPreAdjacentPoints(predecessor);
                graphNode.setCodeLineNum(whileStmt.getBegin().isPresent()?whileStmt.getBegin().get().line:-1);
            } else {
                predecessor.setOriginalCodeStr("while (" + whileStmt.getCondition().toString() + ")");
                predecessor.setOpTypeStr(whileStmt.getClass().toString());
                predecessor.setCodeLineNum(whileStmt.getBegin().isPresent()?whileStmt.getBegin().get().line:-1);
                graphNode = predecessor;
            }
            graphNode.setParentNode(parentNode);
            allNodesMap.put(graphNode.getOriginalCodeStr()+":"+graphNode.getCodeLineNum(),graphNode);
            GraphNode tempNode = graphNode;
            if(!whileStmt.getBody().isBlockStmt()){
                tempNode = buildCFG(tempNode, graphNode, whileStmt.getBody());
            }else {
                NodeList<Statement> statements = whileStmt.getBody().asBlockStmt().getStatements();
                if (statements.size() == 0) {
                    //表示如果while是空的话 直接返回当前节点
                    return graphNode;
                }
                for (Statement statement : statements) {
                    tempNode = buildCFG(tempNode, graphNode, statement);
                }
            }
            //最后出来的tempnode 就是最后一个点了，这个点应该继续返回到条件那个Node，因为是while 不是if
            tempNode.addAdjacentPoint(graphNode);
            tempNode.addEdg(new GraphEdge(EdgeTypes.CFG, tempNode, graphNode));
            graphNode.addPreAdjacentPoints(tempNode);
            return graphNode;// 这个方法体才是最后的节点，也就是下一步的开始节点
        } else if (node instanceof ForStmt) {
            // 能够改变控制流的结构
            List<String> forValues = new ArrayList<>();
            ForStmt forStmt = ((ForStmt) node).asForStmt();
            GraphNode graphNode = new GraphNode();
            forValues.add(StringUtils.join(forStmt.getInitialization(), ","));
            if (forStmt.getCompare().isPresent()) {
                forValues.add(forStmt.getCompare().get().toString());
            }
            forValues.add(StringUtils.join(forStmt.getUpdate(), ","));
            if (!predecessor.getOriginalCodeStr().equals("#placeholder#")) {
                graphNode.setOriginalCodeStr("for(" + StringUtils.join(forValues, ';') + ")");
                graphNode.setOpTypeStr(forStmt.getClass().toString());
                //先驱节点连上这个节点
                predecessor.addAdjacentPoint(graphNode);
                // 同时添加一条边进去
                predecessor.addEdg(new GraphEdge(EdgeTypes.CFG, predecessor, graphNode));
                graphNode.addPreAdjacentPoints(predecessor);
                graphNode.setCodeLineNum(forStmt.getBegin().isPresent()?forStmt.getBegin().get().line:-1);
            } else {
                predecessor.setOriginalCodeStr("for(" + StringUtils.join(forValues, ';') + ")");
                predecessor.setOpTypeStr(forStmt.getClass().toString());
                predecessor.setCodeLineNum(forStmt.getBegin().isPresent()?forStmt.getBegin().get().line:-1);
                graphNode = predecessor;
            }
            graphNode.setParentNode(parentNode);
            allNodesMap.put(graphNode.getOriginalCodeStr()+":"+graphNode.getCodeLineNum(),graphNode);
            GraphNode tempNode = graphNode;
            if(!forStmt.getBody().isBlockStmt()){
                tempNode = buildCFG(tempNode, graphNode, forStmt.getBody());
            }else {
                NodeList<Statement> statements = forStmt.getBody().asBlockStmt().getStatements();
                if (statements.size() == 0) {
                    //表示如果while是空的话 直接返回当前节点
                    return graphNode;
                }
                for (Statement statement : statements) {
                    tempNode = buildCFG(tempNode, graphNode, statement);
                }
            }
            //最后出来的tempnode 就是最后一个点了，这个点应该继续返回到条件那个Node，因为是while 不是if
            tempNode.addAdjacentPoint(graphNode);
            tempNode.addEdg(new GraphEdge(EdgeTypes.CFG, tempNode, graphNode));
            graphNode.addPreAdjacentPoints(tempNode);
            return graphNode;// 这个方法体才是最后的节点，也就是下一步的开始节点
        } else if (node instanceof ForEachStmt) {
            // 能够改变控制流的结构
            ForEachStmt foreachStmt = ((ForEachStmt) node).asForEachStmt();
            GraphNode graphNode = new GraphNode();
            if (!predecessor.getOriginalCodeStr().equals("#placeholder#")) {
                graphNode.setOriginalCodeStr("for(" + foreachStmt.getVariable() + ":" + foreachStmt.getIterable() + ")");
                graphNode.setOpTypeStr(foreachStmt.getClass().toString());
                //先驱节点连上这个节点
                predecessor.addAdjacentPoint(graphNode);
                // 同时添加一条边进去
                predecessor.addEdg(new GraphEdge(EdgeTypes.CFG, predecessor, graphNode));
                graphNode.addPreAdjacentPoints(predecessor);
                graphNode.setCodeLineNum(foreachStmt.getBegin().isPresent()?foreachStmt.getBegin().get().line:-1);
            } else {
                predecessor.setOriginalCodeStr("for(" + foreachStmt.getVariable() + ":" + foreachStmt.getIterable() + ")");
                predecessor.setOpTypeStr(foreachStmt.getClass().toString());
                predecessor.setCodeLineNum(foreachStmt.getBegin().isPresent()?foreachStmt.getBegin().get().line:-1);
                graphNode = predecessor;
            }
            graphNode.setParentNode(parentNode);
            allNodesMap.put(graphNode.getOriginalCodeStr()+":"+graphNode.getCodeLineNum(),graphNode);
            GraphNode tempNode = graphNode;
            if(!foreachStmt.getBody().isBlockStmt()){
                tempNode = buildCFG(tempNode, graphNode, foreachStmt.getBody());
            }else {
                NodeList<Statement> statements = foreachStmt.getBody().asBlockStmt().getStatements();
                if (statements.size() == 0) {
                    //表示如果while是空的话 直接返回当前节点
                    return graphNode;
                }
                for (Statement statement : statements) {
                    tempNode = buildCFG(tempNode, graphNode, statement);
                }
            }
            //最后出来的tempnode 就是最后一个点了，这个点应该继续返回到条件那个Node，因为是while 不是if
            tempNode.addAdjacentPoint(graphNode);
            tempNode.addEdg(new GraphEdge(EdgeTypes.CFG, tempNode, graphNode));
            graphNode.addPreAdjacentPoints(tempNode);
            return graphNode;// 这个方法体才是最后的节点，也就是下一步的开始节点
        } else if (node instanceof SwitchStmt) {
            // 能够改变控制流的结构
            SwitchStmt switchStmt = ((SwitchStmt) node).asSwitchStmt();
            GraphNode graphNode = new GraphNode();
            if (!predecessor.getOriginalCodeStr().equals("#placeholder#")) {
                graphNode.setOriginalCodeStr("switch(" + switchStmt.getSelector().toString() + ")");
                graphNode.setOpTypeStr(switchStmt.getClass().toString());
                //先驱节点连上这个节点
                predecessor.addAdjacentPoint(graphNode);
                // 同时添加一条边进去
                predecessor.addEdg(new GraphEdge(EdgeTypes.CFG, predecessor, graphNode));
                graphNode.addPreAdjacentPoints(predecessor);
                graphNode.setCodeLineNum(switchStmt.getBegin().isPresent()?switchStmt.getBegin().get().line:-1);
            } else {
                predecessor.setOriginalCodeStr("switch(" + switchStmt.getSelector().toString() + ")");
                predecessor.setOpTypeStr(switchStmt.getClass().toString());
                predecessor.setCodeLineNum(switchStmt.getBegin().isPresent()?switchStmt.getBegin().get().line:-1);
                graphNode = predecessor;
            }
            graphNode.setParentNode(parentNode);
            allNodesMap.put(graphNode.getOriginalCodeStr()+":"+graphNode.getCodeLineNum(),graphNode);

            NodeList<SwitchEntry> caseEntries = switchStmt.getEntries(); //case 入口
            if (caseEntries.size() == 0) {
                //表示如果while是空的话 直接返回当前节点
                return graphNode;
            }
            List<GraphNode> caseNode = new ArrayList<>();
            GraphNode placeholderNode = new GraphNode("#placeholder#", "placeholder");
            placeholderNode.setParentNode(parentNode);
            if(switchStmt.getEnd().isPresent()){
                placeholderNode.setCodeLineNum(switchStmt.getEnd().get().line+1);
            }else{
                Random random = new Random();
                placeholderNode.setCodeLineNum(random.nextInt(10000));
            }
            for (int i = 0; i < caseEntries.size(); i++) {
                GraphNode temp = new GraphNode(caseEntries.get(i).getLabels().getFirst().isPresent() ? "case " + caseEntries.get(i).getLabels().getFirst().get().toString() : "default", caseEntries.get(i).getClass().toString());
                temp.setCodeLineNum(caseEntries.get(i).getBegin().isPresent()?caseEntries.get(i).getBegin().get().line:-1);
                temp.setParentNode(graphNode);
                graphNode.addAdjacentPoint(temp);
                graphNode.addEdg(new GraphEdge(EdgeTypes.CFG, graphNode, temp)); // swich 连上各个case节点
                temp.addPreAdjacentPoints(graphNode);
                allNodesMap.put(temp.getOriginalCodeStr()+":"+temp.getCodeLineNum(),temp);
                //获取每个node下面的statement 为一体
                caseNode.add(temp); //这个节点加入list 中，因为等会我还需要进行递归和没有break的case进行连接
            }
            for (int i = 0; i < caseEntries.size(); i++) {
                NodeList<Statement> statements = caseEntries.get(i).getStatements(); //一个case下面的所有语句
                GraphNode tempNode = caseNode.get(i);
                for (Statement statement : statements) {
                    tempNode = buildCFG(tempNode,caseNode.get(i), statement);
                }
                //最后一个节点 按照case正常写法就是break，所以我要判断tempNode是不是break；如果不是那就case和下一个case有连接
                //如果是，那这个节点直接连接到最后的placeholder节点
                if (tempNode.getOriginalCodeStr().contains("break") || tempNode.getOriginalCodeStr().contains("return") || i == caseEntries.size() - 1) {
                    tempNode.addAdjacentPoint(placeholderNode);
                    tempNode.addEdg(new GraphEdge(EdgeTypes.CFG, tempNode, placeholderNode));
                    placeholderNode.addPreAdjacentPoints(tempNode);
                } else {
                    tempNode.addAdjacentPoint(caseNode.get(i + 1));
                    tempNode.addEdg(new GraphEdge(EdgeTypes.CFG, tempNode, caseNode.get(i + 1)));
                    caseNode.get(i + 1).addPreAdjacentPoints(tempNode);
                }
            }
            //然后需要判断是否switch 里面有default这个case：如果没有还需要switch 直接指向placeholder
            boolean defaultIsTrue = false;
            for (SwitchEntry switchEntryStmt : caseEntries) {
                if (!switchEntryStmt.getLabels().getFirst().isPresent()) {
                    //表示default存在
                    defaultIsTrue = true;
                    break;
                }
            }
            if (!defaultIsTrue) { //直接switch指向placeholder
                graphNode.addAdjacentPoint(placeholderNode);
                graphNode.addEdg(new GraphEdge(EdgeTypes.CFG, graphNode, placeholderNode));
                placeholderNode.addPreAdjacentPoints(graphNode);
            }
            return placeholderNode;

        } else if (node instanceof DoStmt) {
            // 能够改变控制流的结构
            DoStmt doStmt = ((DoStmt) node).asDoStmt();
            GraphNode graphNode = new GraphNode();
            if (!predecessor.getOriginalCodeStr().equals("#placeholder#")) {
                graphNode.setOriginalCodeStr("do");
                graphNode.setOpTypeStr(doStmt.getClass().toString());
                //先驱节点连上这个节点
                predecessor.addAdjacentPoint(graphNode);
                // 同时添加一条边进去
                predecessor.addEdg(new GraphEdge(EdgeTypes.CFG, predecessor, graphNode));
                graphNode.addPreAdjacentPoints(predecessor);
                graphNode.setCodeLineNum(doStmt.getBegin().isPresent()?doStmt.getBegin().get().line:-1);
            } else {
                predecessor.setOriginalCodeStr("do");
                predecessor.setOpTypeStr(doStmt.getClass().toString());
                predecessor.setCodeLineNum(doStmt.getBegin().isPresent()?doStmt.getBegin().get().line:-1);
                graphNode = predecessor;
            }
            graphNode.setParentNode(parentNode);
            graphNode.setSimplifyCodeStr("do");
            allNodesMap.put(graphNode.getOriginalCodeStr()+":"+graphNode.getCodeLineNum(),graphNode);

            //whileNode
            GraphNode whileNode = new GraphNode();
            whileNode.setOriginalCodeStr("while (" + doStmt.getCondition().toString() + ")");
            whileNode.setOpTypeStr(WhileStmt.class.toString());
            whileNode.setCodeLineNum(doStmt.getCondition().getBegin().isPresent()?doStmt.getCondition().getBegin().get().line:-1);
            whileNode.setParentNode(parentNode);
            allNodesMap.put(whileNode.getOriginalCodeStr()+":"+whileNode.getCodeLineNum(),whileNode);

            NodeList<Statement> statements = doStmt.getBody().asBlockStmt().getStatements();
            GraphNode tempNode = graphNode;
            for (Statement statement : statements) {
                tempNode = buildCFG(tempNode, whileNode, statement);
            }
            if(statements.size()!=0) {
                whileNode.addAdjacentPoint(graphNode);
                whileNode.addEdg(new GraphEdge(EdgeTypes.CFG, whileNode, graphNode));
                graphNode.addPreAdjacentPoints(whileNode);
            }
            tempNode.addAdjacentPoint(whileNode);
            tempNode.addEdg(new GraphEdge(EdgeTypes.CFG, tempNode, whileNode));
            whileNode.addPreAdjacentPoints(tempNode);
            return whileNode;// 这个方法体才是最后的节点，也就是下一步的开始节点
        } else if (node instanceof BreakStmt) {
            //这个节点 只能通过最后构造好的图遍历来处理，因为跳出的过程中可能后面的节点还没有创建
            BreakStmt breakStmt = ((BreakStmt) node).asBreakStmt();
            GraphNode graphNode = new GraphNode();
            if (!predecessor.getOriginalCodeStr().equals("#placeholder#")) {
                graphNode.setOriginalCodeStr(breakStmt.getLabel().isPresent() ? "break " + breakStmt.getLabel().get().toString() : "break");
                graphNode.setOpTypeStr(breakStmt.getClass().toString());
                //先驱节点连上这个节点
                predecessor.addAdjacentPoint(graphNode);
                // 同时添加一条边进去
                predecessor.addEdg(new GraphEdge(EdgeTypes.CFG, predecessor, graphNode));
                graphNode.addPreAdjacentPoints(predecessor);
                graphNode.setCodeLineNum(breakStmt.getBegin().isPresent()?breakStmt.getBegin().get().line:-1);
            } else {
                predecessor.setOriginalCodeStr(breakStmt.getLabel().isPresent() ? "break " + breakStmt.getLabel().get().toString() : "break");
                predecessor.setOpTypeStr(breakStmt.getClass().toString());
                predecessor.setCodeLineNum(breakStmt.getBegin().isPresent()?breakStmt.getBegin().get().line:-1);
                graphNode = predecessor;
            }
            graphNode.setParentNode(parentNode);
            allNodesMap.put(graphNode.getOriginalCodeStr()+":"+graphNode.getCodeLineNum(),graphNode);

            return graphNode;
        } else if (node instanceof ContinueStmt) {
            //这个节点 只能通过最后构造好的图遍历来处理，因为跳出的过程中可能后面的节点还没有创建
            ContinueStmt continueStmt = ((ContinueStmt) node).asContinueStmt();
            GraphNode graphNode = new GraphNode();
            if (!predecessor.getOriginalCodeStr().equals("#placeholder#")) {
                graphNode.setOriginalCodeStr(continueStmt.getLabel().isPresent() ? "continue " + continueStmt.getLabel().get().toString() : "continue");
                graphNode.setOpTypeStr(continueStmt.getClass().toString());
                //先驱节点连上这个节点
                predecessor.addAdjacentPoint(graphNode);
                // 同时添加一条边进去
                predecessor.addEdg(new GraphEdge(EdgeTypes.CFG, predecessor, graphNode));
                graphNode.addPreAdjacentPoints(predecessor);
                graphNode.setCodeLineNum(continueStmt.getBegin().isPresent()?continueStmt.getBegin().get().line:-1);
            } else {
                predecessor.setOriginalCodeStr(continueStmt.getLabel().isPresent() ? "continue " + continueStmt.getLabel().get().toString() : "continue");
                predecessor.setOpTypeStr(continueStmt.getClass().toString());
                predecessor.setCodeLineNum(continueStmt.getBegin().isPresent()?continueStmt.getBegin().get().line:-1);
                graphNode = predecessor;
            }
            graphNode.setParentNode(parentNode);
            allNodesMap.put(graphNode.getOriginalCodeStr()+":"+graphNode.getCodeLineNum(),graphNode);

            return graphNode;
        } else if (node instanceof LabeledStmt) {
            LabeledStmt labeledStmt = ((LabeledStmt) node).asLabeledStmt();
            GraphNode placeholderNode = new GraphNode("#placeholder#", "placeholder");
            placeholderNode.setParentNode(parentNode);
            if(labeledStmt.getEnd().isPresent()){
                placeholderNode.setCodeLineNum(labeledStmt.getEnd().get().line+1);
            }else{
                Random random = new Random();
                placeholderNode.setCodeLineNum(random.nextInt(10000));
            }
            GraphNode graphNode = new GraphNode();
            if (!predecessor.getOriginalCodeStr().equals("#placeholder#")) {
                graphNode.setOriginalCodeStr(labeledStmt.getLabel().toString());
                graphNode.setOpTypeStr(labeledStmt.getClass().toString());
                //先驱节点连上这个节点
                predecessor.addAdjacentPoint(graphNode);
                // 同时添加一条边进去
                predecessor.addEdg(new GraphEdge(EdgeTypes.CFG, predecessor, graphNode));
                graphNode.addPreAdjacentPoints(predecessor);
                graphNode.setCodeLineNum(labeledStmt.getBegin().isPresent()?labeledStmt.getBegin().get().line:-1);
            } else {
                predecessor.setOriginalCodeStr(labeledStmt.getLabel().toString());
                predecessor.setOpTypeStr(labeledStmt.getClass().toString());
                predecessor.setCodeLineNum(labeledStmt.getBegin().isPresent()?labeledStmt.getBegin().get().line:-1);
                graphNode = predecessor;
            }
            graphNode.setParentNode(parentNode);
            allNodesMap.put(graphNode.getOriginalCodeStr()+":"+graphNode.getCodeLineNum(),graphNode);
            graphNode.setSimplifyCodeStr("label");

            //看整个label下都有没有break label的，只要有一个，那就是label直接连接结束
            List<BreakStmt> allBreakStmts = labeledStmt.findAll(BreakStmt.class);
            boolean labelJoinplace = false;
            for (BreakStmt b : allBreakStmts) {
                if (b.getLabel().isPresent()) {
                    labelJoinplace = true;
                }
            }
            if (labelJoinplace) {
                //如果label 下面包含break 那么label 会直接到结束点，如果是continue没关系
                graphNode.addAdjacentPoint(placeholderNode);
                graphNode.addEdg(new GraphEdge(EdgeTypes.CFG, graphNode, placeholderNode));
                placeholderNode.addPreAdjacentPoints(graphNode);
            }

            GraphNode stateNode = buildCFG(graphNode, graphNode, labeledStmt.getStatement());
            if (!stateNode.getOriginalCodeStr().contains("break") && !stateNode.getOriginalCodeStr().equals("#placeholder#")) { //如果最后一个是break 则不能和placeholder相连
                stateNode.addAdjacentPoint(placeholderNode);
                stateNode.addEdg(new GraphEdge(EdgeTypes.CFG, stateNode, placeholderNode));
                placeholderNode.addPreAdjacentPoints(stateNode);
            }
            if (stateNode.getOriginalCodeStr().equals("#placeholder#")) {
                return stateNode;
            }
            return placeholderNode;
        } else if (node instanceof ReturnStmt) {
            ReturnStmt returnStmt = ((ReturnStmt) node).asReturnStmt();
            GraphNode graphNode = new GraphNode();
            if (!predecessor.getOriginalCodeStr().equals("#placeholder#")) {
                graphNode.setOriginalCodeStr(returnStmt.getExpression().isPresent() ? "return " + returnStmt.getExpression().get().toString() : "return");
                graphNode.setOpTypeStr(returnStmt.getClass().toString());
                //先驱节点连上这个节点
                predecessor.addAdjacentPoint(graphNode);
                // 同时添加一条边进去
                predecessor.addEdg(new GraphEdge(EdgeTypes.CFG, predecessor, graphNode));
                graphNode.addPreAdjacentPoints(predecessor);
                graphNode.setCodeLineNum(returnStmt.getBegin().isPresent()?returnStmt.getBegin().get().line:-1);
            } else {
                predecessor.setOriginalCodeStr(returnStmt.getExpression().isPresent() ? "return " + returnStmt.getExpression().get().toString() : "return");
                predecessor.setOpTypeStr(returnStmt.getClass().toString());
                predecessor.setCodeLineNum(returnStmt.getBegin().isPresent()?returnStmt.getBegin().get().line:-1);
                graphNode = predecessor;
            }
            graphNode.setParentNode(parentNode);
            allNodesMap.put(graphNode.getOriginalCodeStr()+":"+graphNode.getCodeLineNum(),graphNode);

            return graphNode;
        } else if (node instanceof EmptyStmt) {
            EmptyStmt emptyStmt = ((EmptyStmt) node).asEmptyStmt();
            GraphNode graphNode = new GraphNode();
            if (!predecessor.getOriginalCodeStr().equals("#placeholder#")) {
                graphNode.setOriginalCodeStr(emptyStmt.toString());
                graphNode.setOpTypeStr(emptyStmt.getClass().toString());
                //先驱节点连上这个节点
                predecessor.addAdjacentPoint(graphNode);
                // 同时添加一条边进去
                predecessor.addEdg(new GraphEdge(EdgeTypes.CFG, predecessor, graphNode));
                graphNode.addPreAdjacentPoints(predecessor);
                graphNode.setCodeLineNum(emptyStmt.getBegin().isPresent()?emptyStmt.getBegin().get().line:-1);
            } else {
                predecessor.setOriginalCodeStr(emptyStmt.toString());
                predecessor.setOpTypeStr(emptyStmt.getClass().toString());
                predecessor.setCodeLineNum(emptyStmt.getBegin().isPresent()?emptyStmt.getBegin().get().line:-1);
                graphNode = predecessor;
            }
            graphNode.setParentNode(parentNode);
            allNodesMap.put(graphNode.getOriginalCodeStr()+":"+graphNode.getCodeLineNum(),graphNode);
            graphNode.setSimplifyCodeStr("empty");

            return graphNode;
        } else if (node instanceof AssertStmt) {
            AssertStmt assertStmt = ((AssertStmt) node).asAssertStmt();
            GraphNode graphNode = new GraphNode();
            if (!predecessor.getOriginalCodeStr().equals("#placeholder#")) {
                graphNode.setOriginalCodeStr(assertStmt.getMessage().isPresent() ? "assert" + assertStmt.getCheck().toString() + ";" + assertStmt.getMessage().get().toString() : "assert" + assertStmt.getCheck().toString());
                graphNode.setOpTypeStr(assertStmt.getClass().toString());
                //先驱节点连上这个节点
                predecessor.addAdjacentPoint(graphNode);
                // 同时添加一条边进去
                predecessor.addEdg(new GraphEdge(EdgeTypes.CFG, predecessor, graphNode));
                graphNode.addPreAdjacentPoints(predecessor);
                graphNode.setCodeLineNum(assertStmt.getBegin().isPresent()?assertStmt.getBegin().get().line:-1);
            } else {
                predecessor.setOriginalCodeStr(assertStmt.getMessage().isPresent() ? "assert" + assertStmt.getCheck().toString() + ";" + assertStmt.getMessage().get().toString() : "assert" + assertStmt.getCheck().toString());
                predecessor.setOpTypeStr(assertStmt.getClass().toString());
                predecessor.setCodeLineNum(assertStmt.getBegin().isPresent()?assertStmt.getBegin().get().line:-1);
                graphNode = predecessor;
            }
            graphNode.setParentNode(parentNode);
            allNodesMap.put(graphNode.getOriginalCodeStr()+":"+graphNode.getCodeLineNum(),graphNode);

            return graphNode;
        } else if (node instanceof ExplicitConstructorInvocationStmt) { //可能用不到
            ExplicitConstructorInvocationStmt explicitConstructorInvocationStmt = ((ExplicitConstructorInvocationStmt) node).asExplicitConstructorInvocationStmt();
            GraphNode graphNode = new GraphNode();
            if (!predecessor.getOriginalCodeStr().equals("#placeholder#")) {
                graphNode.setOriginalCodeStr(explicitConstructorInvocationStmt.toString());
                graphNode.setOpTypeStr(explicitConstructorInvocationStmt.getClass().toString());
                //先驱节点连上这个节点
                predecessor.addAdjacentPoint(graphNode);
                // 同时添加一条边进去
                predecessor.addEdg(new GraphEdge(EdgeTypes.CFG, predecessor, graphNode));
                graphNode.addPreAdjacentPoints(predecessor);
                graphNode.setCodeLineNum(explicitConstructorInvocationStmt.getBegin().isPresent()?explicitConstructorInvocationStmt.getBegin().get().line:-1);
            } else {
                predecessor.setOriginalCodeStr(explicitConstructorInvocationStmt.toString());
                predecessor.setOpTypeStr(explicitConstructorInvocationStmt.getClass().toString());
                predecessor.setCodeLineNum(explicitConstructorInvocationStmt.getBegin().isPresent()?explicitConstructorInvocationStmt.getBegin().get().line:-1);
                graphNode = predecessor;
            }
            graphNode.setParentNode(parentNode);
            allNodesMap.put(graphNode.getOriginalCodeStr()+":"+graphNode.getCodeLineNum(),graphNode);

            return graphNode;
        } else if (node instanceof ThrowStmt) {
            ThrowStmt throwStmt = ((ThrowStmt) node).asThrowStmt();
            GraphNode graphNode = new GraphNode();
            if (!predecessor.getOriginalCodeStr().equals("#placeholder#")) {
                graphNode.setOriginalCodeStr("throw " + throwStmt.getExpression());
                graphNode.setOpTypeStr(throwStmt.getClass().toString());
                //先驱节点连上这个节点
                predecessor.addAdjacentPoint(graphNode);
                // 同时添加一条边进去
                predecessor.addEdg(new GraphEdge(EdgeTypes.CFG, predecessor, graphNode));
                graphNode.addPreAdjacentPoints(predecessor);
                graphNode.setCodeLineNum(throwStmt.getBegin().isPresent()?throwStmt.getBegin().get().line:-1);
            } else {
                predecessor.setOriginalCodeStr("throw " + throwStmt.getExpression());
                predecessor.setOpTypeStr(throwStmt.getClass().toString());
                predecessor.setCodeLineNum(throwStmt.getBegin().isPresent()?throwStmt.getBegin().get().line:-1);
                graphNode = predecessor;
            }
            graphNode.setParentNode(parentNode);
            allNodesMap.put(graphNode.getOriginalCodeStr()+":"+graphNode.getCodeLineNum(),graphNode);

            return graphNode;
        } else if (node instanceof SwitchEntry) {
            SwitchEntry switchEntryStmt = ((SwitchEntry) node);
            GraphNode graphNode = new GraphNode();
            if (!predecessor.getOriginalCodeStr().equals("#placeholder#")) {
                graphNode.setOriginalCodeStr(switchEntryStmt.getLabels().getFirst().isPresent() ? switchEntryStmt.getLabels().getFirst().toString() : "");
                graphNode.setOpTypeStr(switchEntryStmt.getClass().toString());
                //先驱节点连上这个节点
                predecessor.addAdjacentPoint(graphNode);
                // 同时添加一条边进去
                predecessor.addEdg(new GraphEdge(EdgeTypes.CFG, predecessor, graphNode));
                graphNode.addPreAdjacentPoints(predecessor);
                graphNode.setCodeLineNum(switchEntryStmt.getBegin().isPresent()?switchEntryStmt.getBegin().get().line:-1);
            } else {
                predecessor.setOriginalCodeStr(switchEntryStmt.getLabels().getFirst().isPresent() ? switchEntryStmt.getLabels().getFirst().toString() : "");
                predecessor.setOpTypeStr(switchEntryStmt.getClass().toString());
                predecessor.setCodeLineNum(switchEntryStmt.getBegin().isPresent()?switchEntryStmt.getBegin().get().line:-1);
                graphNode = predecessor;
            }
            graphNode.setParentNode(parentNode);
            allNodesMap.put(graphNode.getOriginalCodeStr()+":"+graphNode.getCodeLineNum(),graphNode);

            return graphNode;
        } else if (node instanceof SynchronizedStmt) {
            SynchronizedStmt synchronizedStmt = ((SynchronizedStmt) node).asSynchronizedStmt();
            GraphNode graphNode = new GraphNode();
            if (!predecessor.getOriginalCodeStr().equals("#placeholder#")) {
                graphNode.setOriginalCodeStr("synchronized (" + synchronizedStmt.getExpression() + ")");
                graphNode.setOpTypeStr(synchronizedStmt.getClass().toString());
                //先驱节点连上这个节点
                predecessor.addAdjacentPoint(graphNode);
                // 同时添加一条边进去
                predecessor.addEdg(new GraphEdge(EdgeTypes.CFG, predecessor, graphNode));
                graphNode.addPreAdjacentPoints(predecessor);
                graphNode.setCodeLineNum(synchronizedStmt.getBegin().isPresent()?synchronizedStmt.getBegin().get().line:-1);
            } else {
                predecessor.setOriginalCodeStr("synchronized (" + synchronizedStmt.getExpression() + ")");
                predecessor.setOpTypeStr(synchronizedStmt.getClass().toString());
                predecessor.setCodeLineNum(synchronizedStmt.getBegin().isPresent()?synchronizedStmt.getBegin().get().line:-1);
                graphNode = predecessor;
            }
            graphNode.setParentNode(parentNode); //方法节点的父节点 就认为是整个图的节点！
            allNodesMap.put(graphNode.getOriginalCodeStr()+":"+graphNode.getCodeLineNum(),graphNode);

            BlockStmt body = synchronizedStmt.getBody();
            GraphNode tempNode = graphNode;
            NodeList<Statement> statements = body.getStatements();
            for (Statement statement : statements) {
                //开始递归创建，每处理一个statement返回一个node，这个node作为下一个state的前驱点
                tempNode = buildCFG(tempNode, graphNode, statement); //外面这个方法节点是方法体所有state的父节点
            }
            return tempNode;
        } else if (node instanceof BlockStmt) {
            BlockStmt blockStmt = ((BlockStmt) node).asBlockStmt();
            GraphNode tempNode = predecessor;
            NodeList<Statement> statements = blockStmt.getStatements();
            for (Statement statement : statements) {
                //开始递归创建，每处理一个statement返回一个node，这个node作为下一个state的前驱点
                tempNode = buildCFG(tempNode, parentNode, statement); //外面这个方法节点是方法体所有state的父节点
            }
            return tempNode;
        } else if (node instanceof TryStmt) { //舍弃catch模块
            TryStmt tryStmt = ((TryStmt) node).asTryStmt();
            GraphNode graphNode = new GraphNode();
            if (!predecessor.getOriginalCodeStr().equals("#placeholder#")) {
                graphNode.setOriginalCodeStr(tryStmt.getResources().size()==0?"try":"try("+ StringUtils.join(tryStmt.getResources(),";")+")");
                graphNode.setOpTypeStr(tryStmt.getClass().toString());
                //先驱节点连上这个节点
                predecessor.addAdjacentPoint(graphNode);
                // 同时添加一条边进去
                predecessor.addEdg(new GraphEdge(EdgeTypes.CFG, predecessor, graphNode));
                graphNode.addPreAdjacentPoints(predecessor);
                graphNode.setCodeLineNum(tryStmt.getBegin().isPresent()?tryStmt.getBegin().get().line:-1);
            } else {
                predecessor.setOriginalCodeStr(tryStmt.getResources().size()==0?"try":"try("+ StringUtils.join(tryStmt.getResources(),";")+")");
                predecessor.setOpTypeStr(tryStmt.getClass().toString());
                predecessor.setCodeLineNum(tryStmt.getBegin().isPresent()?tryStmt.getBegin().get().line:-1);
                graphNode = predecessor;
            }
            graphNode.setParentNode(parentNode); //方法节点的父节点 就认为是整个图的节点！
            allNodesMap.put(graphNode.getOriginalCodeStr()+":"+graphNode.getCodeLineNum(),graphNode);

            BlockStmt tryBlock = tryStmt.getTryBlock();
            GraphNode tempNode = graphNode;
            NodeList<Statement> statements = tryBlock.getStatements();
            for (Statement statement : statements) {
                //开始递归创建，每处理一个statement返回一个node，这个node作为下一个state的前驱点
                tempNode = buildCFG(tempNode, graphNode, statement); //外面这个方法节点是方法体所有state的父节点
            }
            Optional<BlockStmt> finallyBlock = tryStmt.getFinallyBlock();
            if (finallyBlock.isPresent()) {
                //开始finllay 模块
                GraphNode finallayNode = new GraphNode();
                finallayNode.setParentNode(parentNode);
                finallayNode.setOriginalCodeStr("finallay");
                finallayNode.setSimplifyCodeStr("finallay");
                finallayNode.setOpTypeStr(finallayNode.getClass().toString());
                finallayNode.setCodeLineNum(tryStmt.getFinallyBlock().get().getBegin().isPresent()?tryStmt.getFinallyBlock().get().getBegin().get().line:-1);
                allNodesMap.put(finallayNode.getOriginalCodeStr()+":"+finallayNode.getCodeLineNum(),finallayNode);

                tempNode.addAdjacentPoint(finallayNode);
                tempNode.addEdg(new GraphEdge(EdgeTypes.CFG,tempNode,finallayNode));
                finallayNode.addPreAdjacentPoints(tempNode);

                NodeList<Statement> finaBodyStas = finallyBlock.get().getStatements();
                tempNode = finallayNode;
                for (Statement statement : finaBodyStas) {
                    //开始递归创建，每处理一个statement返回一个node，这个node作为下一个state的前驱点
                    tempNode = buildCFG(tempNode, finallayNode, statement); //外面这个方法节点是方法体所有state的父节点
                }
            }
            return tempNode;
        } else if (node instanceof LocalClassDeclarationStmt) {
            //这是一个局部class 声明
            LocalClassDeclarationStmt localClassDeclarationStmt = ((LocalClassDeclarationStmt) node).asLocalClassDeclarationStmt();
            ClassOrInterfaceDeclaration classOrInterfaceDeclaration = localClassDeclarationStmt.getClassDeclaration();
            GraphNode graphNode = new GraphNode();
            if (!predecessor.getOriginalCodeStr().equals("#placeholder#")) {
                graphNode.setOriginalCodeStr(classOrInterfaceDeclaration.getNameAsString());
                graphNode.setOpTypeStr(classOrInterfaceDeclaration.getClass().toString());
                //先驱节点连上这个节点
                predecessor.addAdjacentPoint(graphNode);
                // 同时添加一条边进去
                predecessor.addEdg(new GraphEdge(EdgeTypes.CFG, predecessor, graphNode));
                graphNode.addPreAdjacentPoints(predecessor);
                graphNode.setCodeLineNum(classOrInterfaceDeclaration.getBegin().isPresent()?classOrInterfaceDeclaration.getBegin().get().line:-1);
            } else {
                predecessor.setOriginalCodeStr(classOrInterfaceDeclaration.getNameAsString());
                predecessor.setOpTypeStr(classOrInterfaceDeclaration.getClass().toString());
                predecessor.setCodeLineNum(classOrInterfaceDeclaration.getBegin().isPresent()?classOrInterfaceDeclaration.getBegin().get().line:-1);
                graphNode = predecessor;
            }
            graphNode.setParentNode(parentNode);
            allNodesMap.put(graphNode.getOriginalCodeStr()+":"+graphNode.getCodeLineNum(),graphNode);

//            //这个有点特殊的地方就是 必须把这个class里面的方法还按照这个方式遍历生成图结构 所以一个方法可能多个方法体返回
//            List<MethodDeclaration> allmethods = classOrInterfaceDeclaration.findAll(MethodDeclaration.class);
//            for (MethodDeclaration methodDeclaration : allmethods) {
//                buildMethodCFG(methodDeclaration);
//            }
            return graphNode;
        }
        return new GraphNode();
    }

    /**
     * 一次遍历ast得到的cfg控制流 可能不满足需求，需要二次过滤
     * 1.消除placeholder
     * @param root 第二次过滤就不需要ast的信息了，只需要图结构
     */
    private void buildCFG_2(GraphNode root){
        List<GraphNode> visited = new ArrayList<>(); // 已经被访问过的元素
        Queue<GraphNode> dealingNodes = new LinkedList<>();
        dealingNodes.add(root);
        while(!dealingNodes.isEmpty()) {
            GraphNode tempNode = dealingNodes.poll();
            //节点的后继节点
            List<GraphNode> adjacentPoints = tempNode.getAdjacentPoints();
            //判断这个点是不是placeholder点
            if (tempNode.getOriginalCodeStr().equals("#placeholder#")) {
                //前驱节点
                List<GraphNode> preAdjacentPoints = tempNode.getPreAdjacentPoints();
                for(GraphNode preNode:preAdjacentPoints){
                    for(GraphNode lastNode:adjacentPoints){
                        //前驱节点都和后继节点相连
                        preNode.addAdjacentPoint(lastNode);
                        preNode.addEdg(new GraphEdge(EdgeTypes.CFG,preNode,lastNode));
                    }
                   preNode.removeAdjacentPoint(tempNode);//placeholder 抹去
                   preNode.removeEdges(tempNode);
                }
            }
            visited.add(tempNode);
            //添加没有访问的子节点
            for(GraphNode lastNode:adjacentPoints){
                if(!visited.contains(lastNode)){
                    //没有访问过这个点
                    if (!dealingNodes.contains(lastNode)) {
                        dealingNodes.add(lastNode);
                    }
                }
            }
        }
    }

    /**
     * 三次过滤
     * 1.解决break指向问题 switch中的break就是直接连接后面
     * break 存在的地方 1.for 2.foreach 3.while 4.do while 5.switch 6.label
     * continue 存在的地方 1.for 2.foreach 3.while 4.do while 5.switch 6.label
     * 2.解决throw 指向问题 throw会直接执行方法体结束的地方 也就是直接抛到方法名节点
     * @param node 整个的MethodDeclaration
     */
    private boolean buildCFG_3(Node node){
         if(node instanceof ForStmt){
             //先处理当前stmt中有没有break节点
             ForStmt forStmt = ((ForStmt) node).asForStmt();
             //先看看forStmt直接有没有break节点
             NodeList<Statement> aimStatement = new NodeList<>();//用于存储所有不是循环的statement
             if(!forStmt.getBody().isBlockStmt()){
                 boolean b = buildCFG_3(forStmt.getBody());
                 if(!b){
                     aimStatement.add(forStmt.getBody());
                 }
             }else {
                 NodeList<Statement> statements = forStmt.getBody().asBlockStmt().getStatements();
                 if (statements.size() == 0) {
                     return true;
                 }
                 //先递归把最里面的for循环的break搞定
                 Iterator<Statement> iterator = statements.iterator();
                 while (iterator.hasNext()) {
                     Statement next = iterator.next();
                     boolean b = buildCFG_3(next);
                     if (!b) {
                         aimStatement.add(next);
                     }
                 }
             }
             //没有循环了就要看看有没有break；
             for(Statement statement:aimStatement){
                 List<BreakStmt> allBreakStmts = statement.findAll(BreakStmt.class);
                 for(BreakStmt breakStmt:allBreakStmts){
                     String label = breakStmt.getLabel().isPresent() ? "break " + breakStmt.getLabel().get().toString() : "break";
                     int num = breakStmt.getBegin().isPresent()?breakStmt.getBegin().get().line:-1;
                     GraphNode breakNode = allNodesMap.get(label+":"+num);
                     if(breakNode!=null) {
                         //不管是什么break 后面都不能有边出去
                         breakNode.getAdjacentPoints().clear();
                         breakNode.getEdgs().clear();
                         if (breakStmt.getLabel().isPresent()) { //这个是break label;语句 所以需要这个break直接和label相连
                             //只能通过breakStmt通过parent回溯了
                             GraphNode aimParentNode = findAimParentNode(breakNode, breakStmt.getLabel().get().toString());
                             breakNode.addAdjacentPoint(aimParentNode);
                             breakNode.addEdg(new GraphEdge(EdgeTypes.CFG, breakNode, aimParentNode));
                         } else {
                             //只是普通的break
                             List<String> forValues = new ArrayList<>();
                             forValues.add(StringUtils.join(forStmt.getInitialization(), ","));
                             if (forStmt.getCompare().isPresent()) {
                                 forValues.add(forStmt.getCompare().get().toString());
                             }
                             forValues.add(StringUtils.join(forStmt.getUpdate(), ","));
                             GraphNode aimParentNode = findAimParentNode(breakNode, "for(" + StringUtils.join(forValues, ';') + ")");
                             List<GraphNode> adjacentPoints = aimParentNode.getAdjacentPoints(); //break是直接跳到for的下一个点
                             breakNode.addAdjacentPoint(adjacentPoints.get(adjacentPoints.size() - 1));
                             breakNode.addEdg(new GraphEdge(EdgeTypes.CFG, breakNode, adjacentPoints.get(adjacentPoints.size() - 1)));
                         }
                     }
                 }
             }

             //没有循环了 就看看有没有continue;
             for(Statement statement:aimStatement){
                 List<ContinueStmt> allContinueStmts = statement.findAll(ContinueStmt.class);
                 for(ContinueStmt continueStmt:allContinueStmts){
                     String label = continueStmt.getLabel().isPresent() ? "continue " + continueStmt.getLabel().get().toString() : "continue";
                     int num = continueStmt.getBegin().isPresent()?continueStmt.getBegin().get().line:-1;
                     GraphNode continueNode = allNodesMap.get(label+":"+num);
                     if(continueNode!=null) {
                         //不管是什么break 后面都不能有边出去
                         continueNode.getAdjacentPoints().clear();
                         continueNode.getEdgs().clear();
                         if (continueStmt.getLabel().isPresent()) { //这个是break label;语句 所以需要这个break直接和label相连
                             //只能通过breakStmt通过parent回溯了
                             GraphNode aimParentNode = findAimParentNode(continueNode, continueStmt.getLabel().get().toString());
                             continueNode.addAdjacentPoint(aimParentNode);
                             continueNode.addEdg(new GraphEdge(EdgeTypes.CFG, continueNode, aimParentNode));
                         } else {
                             //只是普通的continue
                             List<String> forValues = new ArrayList<>();
                             forValues.add(StringUtils.join(forStmt.getInitialization(), ","));
                             if (forStmt.getCompare().isPresent()) {
                                 forValues.add(forStmt.getCompare().get().toString());
                             }
                             forValues.add(StringUtils.join(forStmt.getUpdate(), ","));
                             GraphNode aimParentNode = findAimParentNode(continueNode, "for(" + StringUtils.join(forValues, ';') + ")");
                             continueNode.addAdjacentPoint(aimParentNode);
                             continueNode.addEdg(new GraphEdge(EdgeTypes.CFG, continueNode, aimParentNode));
                         }
                     }
                 }
             }
             return true;

         }else if(node instanceof ForEachStmt){
             //存在
             ForEachStmt foreachStmt = ((ForEachStmt) node).asForEachStmt();
             NodeList<Statement> aimStatement = new NodeList<>();//用于存储所有不是循环的statement
             if(!foreachStmt.getBody().isBlockStmt()){
                 boolean b = buildCFG_3(foreachStmt.getBody());
                 if(!b){
                     aimStatement.add(foreachStmt.getBody());
                 }
             }else {
                 //先看看forStmt直接有没有break节点
                 NodeList<Statement> statements = foreachStmt.getBody().asBlockStmt().getStatements();
                 if (statements.size() == 0) {
                     return true;
                 }
                 //先递归把最里面的for循环的break搞定
                 Iterator<Statement> iterator = statements.iterator();
                 while (iterator.hasNext()) {
                     Statement next = iterator.next();
                     boolean b = buildCFG_3(next);
                     if (!b) {
                         aimStatement.add(next);
                     }
                 }
             }
             //没有循环了就要看看有没有break；
             for(Statement statement:aimStatement){
                 List<BreakStmt> allBreakStmts = statement.findAll(BreakStmt.class);
                 for(BreakStmt breakStmt:allBreakStmts){
                     String label = breakStmt.getLabel().isPresent() ? "break " + breakStmt.getLabel().get().toString() : "break";
                     int num = breakStmt.getBegin().isPresent()?breakStmt.getBegin().get().line:-1;
                     GraphNode breakNode = allNodesMap.get(label+":"+num);
                     if(breakNode!=null) {
                         //不管是什么break 后面都不能有边出去
                         breakNode.getAdjacentPoints().clear();
                         breakNode.getEdgs().clear();
                         if (breakStmt.getLabel().isPresent()) { //这个是break label;语句 所以需要这个break直接和label相连
                             //只能通过breakStmt通过parent回溯了
                             GraphNode aimParentNode = findAimParentNode(breakNode, breakStmt.getLabel().get().toString());
                             breakNode.addAdjacentPoint(aimParentNode);
                             breakNode.addEdg(new GraphEdge(EdgeTypes.CFG, breakNode, aimParentNode));
                         } else {
                             //只是普通的break
                             GraphNode aimParentNode = findAimParentNode(breakNode, "for(" + foreachStmt.getVariable() + ":" + foreachStmt.getIterable() + ")");
                             List<GraphNode> adjacentPoints = aimParentNode.getAdjacentPoints(); //break是直接跳到for的下一个点
                             breakNode.addAdjacentPoint(adjacentPoints.get(adjacentPoints.size() - 1));
                             breakNode.addEdg(new GraphEdge(EdgeTypes.CFG, breakNode, adjacentPoints.get(adjacentPoints.size() - 1)));
                         }
                     }
                 }
             }

             //没有循环了 就看看有没有continue;
             for(Statement statement:aimStatement){
                 List<ContinueStmt> allContinueStmts = statement.findAll(ContinueStmt.class);
                 for(ContinueStmt continueStmt:allContinueStmts){
                     String label = continueStmt.getLabel().isPresent() ? "continue " + continueStmt.getLabel().get().toString() : "continue";
                     int num = continueStmt.getBegin().isPresent()?continueStmt.getBegin().get().line:-1;
                     GraphNode continueNode = allNodesMap.get(label+":"+num);
                     if(continueNode!=null) {
                         //不管是什么break 后面都不能有边出去
                         continueNode.getAdjacentPoints().clear();
                         continueNode.getEdgs().clear();
                         if (continueStmt.getLabel().isPresent()) { //这个是break label;语句 所以需要这个break直接和label相连
                             //只能通过breakStmt通过parent回溯了
                             GraphNode aimParentNode = findAimParentNode(continueNode, continueStmt.getLabel().get().toString());
                             continueNode.addAdjacentPoint(aimParentNode);
                             continueNode.addEdg(new GraphEdge(EdgeTypes.CFG, continueNode, aimParentNode));
                         } else {
                             //只是普通的continue
                             GraphNode aimParentNode = findAimParentNode(continueNode, "for(" + foreachStmt.getVariable() + ":" + foreachStmt.getIterable() + ")");
                             continueNode.addAdjacentPoint(aimParentNode);
                             continueNode.addEdg(new GraphEdge(EdgeTypes.CFG, continueNode, aimParentNode));
                         }
                     }
                 }
             }
             return true;

         }else if(node instanceof WhileStmt){
             //存在
             WhileStmt whileStmt = ((WhileStmt) node).asWhileStmt();
             NodeList<Statement> aimStatement = new NodeList<>();//用于存储所有不是循环的statement
             //先看看forStmt直接有没有break节点
             if(!whileStmt.getBody().isBlockStmt()){
                 boolean b = buildCFG_3(whileStmt.getBody());
                 if(!b){
                     aimStatement.add(whileStmt.getBody());
                 }
             }else {
                 NodeList<Statement> statements = whileStmt.getBody().asBlockStmt().getStatements();
                 if (statements.size() == 0) {
                     return true;
                 }
                 //先递归把最里面的for循环的break搞定
                 Iterator<Statement> iterator = statements.iterator();
                 while (iterator.hasNext()) {
                     Statement next = iterator.next();
                     boolean b = buildCFG_3(next);
                     if (!b) {
                         aimStatement.add(next);
                     }
                 }
             }

             //没有循环了就要看看有没有break；
             for(Statement statement:aimStatement){
                 List<BreakStmt> allBreakStmts = statement.findAll(BreakStmt.class);
                 for(BreakStmt breakStmt:allBreakStmts){
                     String label = breakStmt.getLabel().isPresent() ? "break " + breakStmt.getLabel().get().toString() : "break";
                     int num = breakStmt.getBegin().isPresent()?breakStmt.getBegin().get().line:-1;
                     GraphNode breakNode = allNodesMap.get(label+":"+num);
                     if(breakNode!=null){
                         //不管是什么break 后面都不能有边出去
                         breakNode.getAdjacentPoints().clear();
                         breakNode.getEdgs().clear();
                         if(breakStmt.getLabel().isPresent()){ //这个是break label;语句 所以需要这个break直接和label相连
                             //只能通过breakStmt通过parent回溯了
                             GraphNode aimParentNode = findAimParentNode(breakNode, breakStmt.getLabel().get().toString());
                             breakNode.addAdjacentPoint(aimParentNode);
                             breakNode.addEdg(new GraphEdge(EdgeTypes.CFG,breakNode,aimParentNode));
                         }else{
                             //只是普通的break
                             GraphNode aimParentNode = findAimParentNode(breakNode, "while (" + whileStmt.getCondition().toString() + ")");
                             List<GraphNode> adjacentPoints = aimParentNode.getAdjacentPoints(); //break是直接跳到for的下一个点
                             breakNode.addAdjacentPoint(adjacentPoints.get(adjacentPoints.size() - 1));
                             breakNode.addEdg(new GraphEdge(EdgeTypes.CFG,breakNode,adjacentPoints.get(adjacentPoints.size() - 1)));
                         }
                     }
                 }
             }

             //没有循环了 就看看有没有continue;
             for(Statement statement:aimStatement){
                 List<ContinueStmt> allContinueStmts = statement.findAll(ContinueStmt.class);
                 for(ContinueStmt continueStmt:allContinueStmts){
                     String label = continueStmt.getLabel().isPresent() ? "continue " + continueStmt.getLabel().get().toString() : "continue";
                     int num = continueStmt.getBegin().isPresent()?continueStmt.getBegin().get().line:-1;
                     GraphNode continueNode = allNodesMap.get(label+":"+num);
                     if(continueNode!=null) {
                         //不管是什么break 后面都不能有边出去
                         continueNode.getAdjacentPoints().clear();
                         continueNode.getEdgs().clear();
                         if (continueStmt.getLabel().isPresent()) { //这个是break label;语句 所以需要这个break直接和label相连
                             //只能通过breakStmt通过parent回溯了
                             GraphNode aimParentNode = findAimParentNode(continueNode, continueStmt.getLabel().get().toString());
                             continueNode.addAdjacentPoint(aimParentNode);
                             continueNode.addEdg(new GraphEdge(EdgeTypes.CFG, continueNode, aimParentNode));
                         } else {
                             //只是普通的continue
                             GraphNode aimParentNode = findAimParentNode(continueNode, "while (" + whileStmt.getCondition().toString() + ")");
                             continueNode.addAdjacentPoint(aimParentNode);
                             continueNode.addEdg(new GraphEdge(EdgeTypes.CFG, continueNode, aimParentNode));
                         }
                     }
                 }
             }
             return true;

         }else if(node instanceof DoStmt){
             //存在
             DoStmt doStmt = ((DoStmt) node).asDoStmt();
             //先看看forStmt直接有没有break节点
             NodeList<Statement> statements = doStmt.getBody().asBlockStmt().getStatements();
             if(statements.size()==0){
                 return true;
             }
             //先递归把最里面的for循环的break搞定
             Iterator<Statement> iterator = statements.iterator();
             NodeList<Statement> aimStatement = new NodeList<>();//用于存储所有不是循环的statement
             while(iterator.hasNext()){
                 Statement next = iterator.next();
                 boolean b = buildCFG_3(next);
                 if(!b){
                     aimStatement.add(next);
                 }
             }
             //没有循环了就要看看有没有break；
             for(Statement statement:aimStatement){
                 List<BreakStmt> allBreakStmts = statement.findAll(BreakStmt.class);
                 for(BreakStmt breakStmt:allBreakStmts){
                     String label = breakStmt.getLabel().isPresent() ? "break " + breakStmt.getLabel().get().toString() : "break";
                     int num = breakStmt.getBegin().isPresent()?breakStmt.getBegin().get().line:-1;
                     GraphNode breakNode = allNodesMap.get(label+":"+num);
                     if(breakNode!=null){
                         //不管是什么break 后面都不能有边出去
                         breakNode.getAdjacentPoints().clear();
                         breakNode.getEdgs().clear();
                         if(breakStmt.getLabel().isPresent()){ //这个是break label;语句 所以需要这个break直接和label相连
                             //只能通过breakStmt通过parent回溯了
                             GraphNode aimParentNode = findAimParentNode(breakNode, breakStmt.getLabel().get().toString());
                             breakNode.addAdjacentPoint(aimParentNode);
                             breakNode.addEdg(new GraphEdge(EdgeTypes.CFG,breakNode,aimParentNode));
                         }else{
                             //只是普通的break
                             GraphNode aimParentNode = findAimParentNode(breakNode, "while (" + doStmt.getCondition().toString() + ")");
                             List<GraphNode> adjacentPoints = aimParentNode.getAdjacentPoints(); //break是直接跳到for的下一个点
                             breakNode.addAdjacentPoint(adjacentPoints.get(adjacentPoints.size() - 1));
                             breakNode.addEdg(new GraphEdge(EdgeTypes.CFG,breakNode,adjacentPoints.get(adjacentPoints.size() - 1)));
                         }
                     }
                 }
             }

             //没有循环了 就看看有没有continue;
             for(Statement statement:aimStatement){
                 List<ContinueStmt> allContinueStmts = statement.findAll(ContinueStmt.class);
                 for(ContinueStmt continueStmt:allContinueStmts){
                     String label = continueStmt.getLabel().isPresent() ? "continue " + continueStmt.getLabel().get().toString() : "continue";
                     int num = continueStmt.getBegin().isPresent()?continueStmt.getBegin().get().line:-1;
                     GraphNode continueNode = allNodesMap.get(label+":"+num);
                     if(continueNode!=null) {
                         //不管是什么break 后面都不能有边出去
                         continueNode.getAdjacentPoints().clear();
                         continueNode.getEdgs().clear();
                         if (continueStmt.getLabel().isPresent()) { //这个是break label;语句 所以需要这个break直接和label相连
                             //只能通过breakStmt通过parent回溯了
                             GraphNode aimParentNode = findAimParentNode(continueNode, continueStmt.getLabel().get().toString());
                             continueNode.addAdjacentPoint(aimParentNode);
                             continueNode.addEdg(new GraphEdge(EdgeTypes.CFG, continueNode, aimParentNode));
                         } else {
                             //只是普通的continue
                             GraphNode aimParentNode = findAimParentNode(continueNode, "while (" + doStmt.getCondition().toString() + ")");
                             continueNode.addAdjacentPoint(aimParentNode);
                             continueNode.addEdg(new GraphEdge(EdgeTypes.CFG, continueNode, aimParentNode));
                         }
                     }
                 }
             }
             return true;

         }else if(node instanceof LabeledStmt){
             LabeledStmt labeledStmt = ((LabeledStmt) node).asLabeledStmt();
             //先看看forStmt直接有没有break节点
             Statement statement = labeledStmt.getStatement();
             //先递归把最里面的for循环的break搞定
             boolean b = buildCFG_3(statement);//继续递归一直到最后面那个for循环！
             if(statement.isBreakStmt()){
                 BreakStmt breakStmt = statement.asBreakStmt();
                 String label = breakStmt.getLabel().isPresent() ? "break " + breakStmt.getLabel().get().toString() : "break";
                 int num = breakStmt.getBegin().isPresent()?breakStmt.getBegin().get().line:-1;
                 GraphNode breakNode = allNodesMap.get(label+":"+num);
                 if(breakNode!=null) {
                     //不管是什么break 后面都不能有边出去
                     breakNode.getAdjacentPoints().clear();
                     breakNode.getEdgs().clear();
                     GraphNode aimParentNode = findAimParentNode(breakNode, labeledStmt.getLabel().toString());
                     breakNode.addAdjacentPoint(aimParentNode);
                     breakNode.addEdg(new GraphEdge(EdgeTypes.CFG, breakNode, aimParentNode));
                 }
             }else if(statement.isContinueStmt()){
                 ContinueStmt continueStmt = statement.asContinueStmt();
                 String label = continueStmt.getLabel().isPresent() ? "continue " + continueStmt.getLabel().get().toString() : "continue";
                 int num = continueStmt.getBegin().isPresent()?continueStmt.getBegin().get().line:-1;
                 GraphNode continueNode = allNodesMap.get(label+":"+num);
                 if(continueNode!=null) {
                     //不管是什么break 后面都不能有边出去
                     continueNode.getAdjacentPoints().clear();
                     continueNode.getEdgs().clear();
                     GraphNode aimParentNode = findAimParentNode(continueNode, labeledStmt.getLabel().toString());
                     continueNode.addAdjacentPoint(aimParentNode);
                     continueNode.addEdg(new GraphEdge(EdgeTypes.CFG, continueNode, aimParentNode));
                 }
             }
             return true;
         }else if (node instanceof MethodDeclaration) {
             MethodDeclaration methodDeclaration = ((MethodDeclaration) node).asMethodDeclaration();
             if(methodDeclaration.getParentNode().isPresent()) {
                 if (!(methodDeclaration.getParentNode().get() instanceof TypeDeclaration)) {
                     return false; //专门针对于匿名对象 匿名对象的方法不处理
                 }
             }
             Optional<BlockStmt> body = methodDeclaration.getBody();
             if (body.isPresent()) {
                 NodeList<Statement> statements = body.get().getStatements();
                 for(Statement statement:statements){
                     List<ThrowStmt> allThrowStmts = statement.findAll(ThrowStmt.class);
                     for(ThrowStmt throwStmt:allThrowStmts){
                         String label = "throw " + throwStmt.getExpression();
                         int lineNum = throwStmt.getBegin().isPresent() ? throwStmt.getBegin().get().line : -1;
                         GraphNode cfgNode = allNodesMap.get(label + ":" + lineNum);
                         if(cfgNode!=null) {
                             //清楚所有后面的边
                             cfgNode.getAdjacentPoints().clear();
                             cfgNode.getEdgs().clear();
                             GraphNode aimParentNode = findAimParentNode(cfgNode, methodDeclaration.getDeclarationAsString(false, true, true));
                             cfgNode.addAdjacentPoint(aimParentNode);
                             cfgNode.addEdg(new GraphEdge(EdgeTypes.CFG, cfgNode, aimParentNode));
                         }
                     }
                 }

                 for (Statement statement : statements) {
                     //开始递归创建，每处理一个statement返回一个node，这个node作为下一个state的前驱点
                     buildCFG_3(statement);
                 }
             }
         }else if (node instanceof IfStmt) {
             // 能够改变控制流的结构
             IfStmt tempIfStmt = ((IfStmt) node).asIfStmt(); //最开始的if节点
             while (tempIfStmt != null) {
                 //先处理这个if节点和最后跳出的节点向量
                 if(!tempIfStmt.getThenStmt().isBlockStmt()){
                     buildCFG_3(tempIfStmt.getThenStmt());
                 }else {
                     BlockStmt thenBlockStmt = tempIfStmt.getThenStmt().asBlockStmt();
                     NodeList<Statement> statements = thenBlockStmt.getStatements();
                     for (Statement statement : statements) {
                         buildCFG_3(statement);
                     }
                 }
                 if (tempIfStmt.getElseStmt().isPresent()) {
                     if (tempIfStmt.getElseStmt().get().isIfStmt()) {
                         tempIfStmt = tempIfStmt.getElseStmt().get().asIfStmt();
                     } else { //就是blockstmt
                         if(!tempIfStmt.getElseStmt().get().isBlockStmt()){
                             buildCFG_3(tempIfStmt.getElseStmt().get());
                         }else {
                             BlockStmt elseBlockStmt = tempIfStmt.getElseStmt().get().asBlockStmt();
                             NodeList<Statement> statements1 = elseBlockStmt.getStatements();
                             for (Statement statement : statements1) {
                                 buildCFG_3(statement);
                             }
                         }
                         tempIfStmt = null;
                     }
                 } else {
                     tempIfStmt = null;
                 }
             }
         }else if (node instanceof SwitchStmt) {
             // 能够改变控制流的结构
             SwitchStmt switchStmt = ((SwitchStmt) node).asSwitchStmt();
             NodeList<SwitchEntry> caseEntries = switchStmt.getEntries(); //case 入口
             if (caseEntries.size() == 0) {
                 //表示如果while是空的话 直接返回当前节点
                 return false;
             }
             for (int i = 0; i < caseEntries.size(); i++) {
                 NodeList<Statement> statements = caseEntries.get(i).getStatements(); //一个case下面的所有语句
                 for (Statement statement : statements) {
                     buildCFG_3(statement);
                 }
             }
             return true;
         }else if (node instanceof SynchronizedStmt) {
             SynchronizedStmt synchronizedStmt = ((SynchronizedStmt) node).asSynchronizedStmt();
             BlockStmt body = synchronizedStmt.getBody();
             NodeList<Statement> statements = body.getStatements();
             for (Statement statement : statements) {
                buildCFG_3(statement);
             }
         } else if (node instanceof BlockStmt) {
             BlockStmt blockStmt = ((BlockStmt) node).asBlockStmt();
             NodeList<Statement> statements = blockStmt.getStatements();
             for (Statement statement : statements) {
                 //开始递归创建，每处理一个statement返回一个node，这个node作为下一个state的前驱点
                 buildCFG_3(statement);
             }
         } else if (node instanceof TryStmt) { //舍弃catch模块
             TryStmt tryStmt = ((TryStmt) node).asTryStmt();
             BlockStmt tryBlock = tryStmt.getTryBlock();
             NodeList<Statement> statements = tryBlock.getStatements();
             for (Statement statement : statements) {
                 buildCFG_3(statement);
             }
             Optional<BlockStmt> finallyBlock = tryStmt.getFinallyBlock();
             if (finallyBlock.isPresent()) {
                 //开始finllay 模块
                 NodeList<Statement> finaBodyStas = finallyBlock.get().getStatements();
                 for (Statement statement : finaBodyStas) {
                     buildCFG_3(statement);
                 }
             }
         }
        return false;
    }

    private GraphNode findAimParentNode(GraphNode node,String stopCondition){
        //通过一个node不断回溯去找目标父节点，停止条件就是stopCondition
        while(node!=null){
            if(node.getOriginalCodeStr().equals(stopCondition)){
                return node;
            }
            node = node.getParentNode();
        }
        return node;
    }

    public HashMap<String, GraphNode> getAllNodesMap() {
        return allNodesMap;
    }

    public void setAllNodesMap(HashMap<String, GraphNode> allNodesMap) {
        this.allNodesMap = allNodesMap;
    }

}
