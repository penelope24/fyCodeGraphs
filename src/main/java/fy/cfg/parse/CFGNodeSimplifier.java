package fy.cfg.parse;

import com.github.javaparser.ast.ArrayCreationLevel;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.*;
import fy.structures.DFVarNode;
import fy.structures.GraphNode;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * 将CFG Node 原始表示按照类型进行简化
 */
public class CFGNodeSimplifier {
    /**
     * allCFGNodesMap 存储的是产生控制流时候创建的所有node信息
     */
    private HashMap<String, GraphNode> allCFGNodesMap;

    /**
     * 方法所在的包下的所有类型，当前java文件所有import点以及java文件中class 生命给成员变量
     */
    private Set<String> packageToAllType;
    private List<String> imports;
    private Set<DFVarNode> allFields;

    public CFGNodeSimplifier(HashMap<String, GraphNode> allCFGNodesMap, Set<String> packageToAllType, List<String> imports, Set<DFVarNode> allFields) {
        this.allCFGNodesMap = allCFGNodesMap;
        this.packageToAllType = packageToAllType;
        this.imports = imports;
        this.allFields = allFields;
    }

    /**
     * 简化cfg node原始代码的表示，抽象节点表示
     * @param node
     */
    public void simplifyCFGNodeStr(Node node) {
        if (node instanceof MethodDeclaration) {
            MethodDeclaration methodDeclaration = ((MethodDeclaration) node).asMethodDeclaration();
            if(methodDeclaration.getParentNode().isPresent()) {
                if (!(methodDeclaration.getParentNode().get() instanceof TypeDeclaration)) {
                    return; //专门针对于匿名对象 匿名对象的方法不处理
                }
            }
            System.out.println("********************************************");
            System.out.println("当前正在CFG基础上简化cfg节点的表示：" + methodDeclaration.getDeclarationAsString(false,false,true));
            System.out.println("********************************************");
            String label = methodDeclaration.getDeclarationAsString(false, true, true);
            int lineNum = methodDeclaration.getBegin().isPresent() ? methodDeclaration.getBegin().get().line : -1;
            //先拿到cfg的方法声明的节点 因为需要扩充cfg节点的细节信息
            GraphNode methodNode = allCFGNodesMap.get(label + ":" + lineNum);

            //整个方法为一个大范围 所以这个范围定义的变量在子范围都有效
            Set<DFVarNode> currentDefVarMap = new HashSet<>();

            //需要先找到当前方法的class 才能找到具体的类型
            String currentMethodInClass = "";
            Optional<Node> parentNode = methodDeclaration.getParentNode();
            if(parentNode.isPresent()){
                TypeDeclaration type = (TypeDeclaration) parentNode.get();
                Iterator<String> iterator = packageToAllType.iterator();
                while(iterator.hasNext()){
                    String pt = iterator.next();
                    if(pt.substring(pt.lastIndexOf(".")+1).equals(type.getNameAsString())){
                        currentMethodInClass = pt;
                        break;
                    }
                }
            }

            StringBuilder methodNameSimplyStr = new StringBuilder(currentMethodInClass+"."+methodDeclaration.getNameAsString());
            List<String> paraTypeStr = new ArrayList<>();
            // 先把方法的形参 都添加到定义的节点中
            NodeList<Parameter> parameters = methodDeclaration.getParameters();
            for(Parameter parameter:parameters){
                currentDefVarMap.add(new DFVarNode(parameter.getNameAsString(),methodNode,parseTypeToTypeStr(parameter.getType())));
                paraTypeStr.add(parseTypeToTypeStr(parameter.getType()));
            }
            //传给方法之前 形参和全局field 属于同一个作用域
            currentDefVarMap.addAll(allFields);

            //参数的全局类型搞定之后，还需要明确当前node的表示形式
            methodNode.setSimplifyCodeStr(paraTypeStr.size()==0?methodNameSimplyStr.append("()").toString():methodNameSimplyStr.append("(")+ StringUtils.join(paraTypeStr,",")+")");

            Optional<BlockStmt> body = methodDeclaration.getBody();
            if (body.isPresent()) {
                NodeList<Statement> statements = body.get().getStatements();
                for (Statement statement : statements) {
                    //开始递归创建，每处理一个statement返回一个node，这个node作为下一个state的前驱点
                    currentDefVarMap = simplifyNode(statement,currentDefVarMap);
                }
            }
        }
    }
    /**
     *
     * @param node 按照cfg的流程遍历整个方法体
     * @param parentDefVarMap 父范围内定义的变量 <变量名字,GraphNode信息,变量类型>
     */
    private Set<DFVarNode> simplifyNode(Node node,Set<DFVarNode> parentDefVarMap){
        /**
         * 先处理方法块内 无法继续递归的模块  无法递归说明就没有块结构 没有块结构那么就没有局部作用域
         */
        if (node instanceof ExpressionStmt) {
            ExpressionStmt expressionStmt = ((ExpressionStmt) node).asExpressionStmt();
            Expression expression = expressionStmt.getExpression();
            String label = expression.toString();
            int lineNum = expression.getBegin().isPresent() ? expression.getBegin().get().line : -1;
            GraphNode cfgNode = allCFGNodesMap.get(label + ":" + lineNum);

            //这个表达式的 定义边和使用边的信息
            return dealSingleRoadStmtDFG(parentDefVarMap,expression,cfgNode);

        }else if(node instanceof ReturnStmt){
            /*
            单线路
             */
            ReturnStmt returnStmt = ((ReturnStmt) node).asReturnStmt();
            String label = returnStmt.getExpression().isPresent() ? "return " + returnStmt.getExpression().get().toString() : "return";
            int lineNum = returnStmt.getBegin().isPresent() ? returnStmt.getBegin().get().line : -1;
            GraphNode cfgNode = allCFGNodesMap.get(label + ":" + lineNum);
            if (returnStmt.getExpression().isPresent()) {
                //处理 expression
                parentDefVarMap = dealSingleRoadStmtDFG(parentDefVarMap,returnStmt.getExpression().get(),cfgNode);
                cfgNode.setSimplifyCodeStr("return "+cfgNode.getSimplifyCodeStr());
            }else{
                cfgNode.setSimplifyCodeStr("return");
            }
            return parentDefVarMap;
        }else if(node instanceof AssertStmt){
            /*
            单线路
             */
            AssertStmt assertStmt = ((AssertStmt) node).asAssertStmt();

            String label = assertStmt.getMessage().isPresent() ? "assert" + assertStmt.getCheck().toString() + ";" + assertStmt.getMessage().get().toString() : "assert" + assertStmt.getCheck().toString();
            int lineNum = assertStmt.getBegin().isPresent() ? assertStmt.getBegin().get().line : -1;
            GraphNode cfgNode = allCFGNodesMap.get(label + ":" + lineNum);
            //处理 expression
            Set<DFVarNode> dfVarNodes = dealSingleRoadStmtDFG(parentDefVarMap, assertStmt.getCheck(), cfgNode);
            //这里面有两个expression 所以需要两次
            String simStr = "assert "+cfgNode.getSimplifyCodeStr();
            if(assertStmt.getMessage().isPresent()){
                dfVarNodes = dealSingleRoadStmtDFG(parentDefVarMap,assertStmt.getMessage().get(),cfgNode);
                simStr = simStr+";"+cfgNode.getSimplifyCodeStr();
            }
            cfgNode.setSimplifyCodeStr(simStr);
            return  dfVarNodes;

        }else if(node instanceof ThrowStmt){
            ThrowStmt throwStmt = ((ThrowStmt) node).asThrowStmt();

            String label = "throw " + throwStmt.getExpression();
            int lineNum = throwStmt.getBegin().isPresent() ? throwStmt.getBegin().get().line : -1;
            GraphNode cfgNode = allCFGNodesMap.get(label + ":" + lineNum);
            //处理 expression
            Set<DFVarNode> dfVarNodes = dealSingleRoadStmtDFG(parentDefVarMap, throwStmt.getExpression(), cfgNode);
            cfgNode.setSimplifyCodeStr("throw "+cfgNode.getSimplifyCodeStr());
            return dfVarNodes ;
        }else if(node instanceof IfStmt){
            /**
             * 从这里开始块单元
             */
            IfStmt tempIfStmt = ((IfStmt) node).asIfStmt(); //最开始的if节点
            Set<DFVarNode> copy = this.copy(parentDefVarMap);
            while (tempIfStmt != null) {
                String ifLabel = "if (" + tempIfStmt.getCondition().toString().replaceAll("//\\s+\\n", "") + ")";
                int ifLineNum = tempIfStmt.getBegin().isPresent() ? tempIfStmt.getBegin().get().line : -1;
                GraphNode ifCfgNode = allCFGNodesMap.get(ifLabel + ":" + ifLineNum);

                Set<DFVarNode> sonBlockDefVarSet = this.copy(copy);
                //if 条件语句中的修改 一般只有使用 没有定义 所以不用担心
                sonBlockDefVarSet = dealSingleRoadStmtDFG(sonBlockDefVarSet,tempIfStmt.getCondition(),ifCfgNode);

                ifCfgNode.setSimplifyCodeStr("if ("+ifCfgNode.getSimplifyCodeStr()+")");

                if(!tempIfStmt.getThenStmt().isBlockStmt()) {
                    sonBlockDefVarSet = simplifyNode(tempIfStmt.getThenStmt(), sonBlockDefVarSet);
                }else {
                    //先处理这个if节点和最后跳出的节点向量
                    BlockStmt thenBlockStmt = tempIfStmt.getThenStmt().asBlockStmt();
                    NodeList<Statement> statements = thenBlockStmt.getStatements();
                    //到了子模块来了 就复制一份定义集合，这样所有新的定义变量除了这个范围不影响父范围的节点
                    for (Statement statement : statements) {
                        sonBlockDefVarSet = simplifyNode(statement, sonBlockDefVarSet);
                    }
                }
                if (tempIfStmt.getElseStmt().isPresent()) {
                    if (tempIfStmt.getElseStmt().get().isIfStmt()) {
                        tempIfStmt = tempIfStmt.getElseStmt().get().asIfStmt();
                    } else { //就是blockstmt
                        Set<DFVarNode> sonBlockDefVarSet2 = this.copy(copy);
                        //else 分支的合并
                        if(!tempIfStmt.getElseStmt().get().isBlockStmt()){
                            sonBlockDefVarSet2 = simplifyNode(tempIfStmt.getElseStmt().get(), sonBlockDefVarSet2);
                        }else {
                            BlockStmt elseBlockStmt = tempIfStmt.getElseStmt().get().asBlockStmt();
                            NodeList<Statement> statements1 = elseBlockStmt.getStatements();
                            for (Statement statement : statements1) {
                                sonBlockDefVarSet2 = simplifyNode(statement, sonBlockDefVarSet2);
                            }
                        }
                        tempIfStmt = null;
                    }
                } else {
                    tempIfStmt = null;
                }
            }
            return parentDefVarMap;
        } else if (node instanceof WhileStmt) {
            WhileStmt whileStmt = ((WhileStmt) node).asWhileStmt();
            String label = "while (" + whileStmt.getCondition().toString() + ")";
            int lineNum = whileStmt.getBegin().isPresent() ? whileStmt.getBegin().get().line : -1;
            GraphNode cfgNode = allCFGNodesMap.get(label + ":" + lineNum);
            //处理ast
            parentDefVarMap = dealSingleRoadStmtDFG(parentDefVarMap,whileStmt.getCondition(),cfgNode);

            cfgNode.setSimplifyCodeStr("while ("+cfgNode.getSimplifyCodeStr()+")");
            //有新的block 就有新的局域范围
            Set<DFVarNode> sonBlockDefVarSet = this.copy(parentDefVarMap);
            if(!whileStmt.getBody().isBlockStmt()){
                sonBlockDefVarSet = simplifyNode(whileStmt.getBody(), sonBlockDefVarSet);
            }else {
                NodeList<Statement> statements = whileStmt.getBody().asBlockStmt().getStatements();
                if (statements.size() == 0) {
                    //表示如果while是空的话 直接返回当前节点
                    return parentDefVarMap;
                }
                for (Statement statement : statements) {
                    sonBlockDefVarSet = simplifyNode(statement, sonBlockDefVarSet);
                }
            }
            return parentDefVarMap;
        }else if(node instanceof ForStmt){
            List<String> forValues = new ArrayList<>();

            Set<DFVarNode> sonBlockDefVarSet = this.copy(parentDefVarMap);

            ForStmt forStmt = ((ForStmt) node).asForStmt();
            forValues.add(StringUtils.join(forStmt.getInitialization(), ","));
            if (forStmt.getCompare().isPresent()) {
                forValues.add(forStmt.getCompare().get().toString());
            }
            forValues.add(StringUtils.join(forStmt.getUpdate(), ","));
            String label = "for(" + StringUtils.join(forValues, ';') + ")";
            int lineNum = forStmt.getBegin().isPresent() ? forStmt.getBegin().get().line : -1;
            GraphNode cfgNode = allCFGNodesMap.get(label + ":" + lineNum);
            //处理 expression
            /**
             * for循环需要处理三种 expression  只是分析初始化 因为可能block用到i
             */
            forValues.clear();
            List<String> initStr = new ArrayList<>();
            NodeList<Expression> initialization = forStmt.getInitialization();
            for(Expression e:initialization){
                sonBlockDefVarSet = dealSingleRoadStmtDFG(sonBlockDefVarSet,e,cfgNode);
                initStr.add(cfgNode.getSimplifyCodeStr());
            }
            forValues.add(StringUtils.join(initStr,","));
            if (forStmt.getCompare().isPresent()) {
                sonBlockDefVarSet = dealSingleRoadStmtDFG(sonBlockDefVarSet,forStmt.getCompare().get(),cfgNode);
                forValues.add(cfgNode.getSimplifyCodeStr());
            }
            List<String> updateStr = new ArrayList<>();
            NodeList<Expression> update = forStmt.getUpdate();
            for(Expression e:update){
                sonBlockDefVarSet = dealSingleRoadStmtDFG(sonBlockDefVarSet,e,cfgNode);
                updateStr.add(cfgNode.getSimplifyCodeStr());
            }
            forValues.add(StringUtils.join(updateStr, ","));
            //这才是for的抽象表示
            cfgNode.setSimplifyCodeStr("for(" + StringUtils.join(forValues, ';') + ")");

            if(!forStmt.getBody().isBlockStmt()){
                sonBlockDefVarSet = simplifyNode(forStmt.getBody(), sonBlockDefVarSet);
            }else {
                NodeList<Statement> statements = forStmt.getBody().asBlockStmt().getStatements();
                if (statements.size() == 0) {
                    //表示如果while是空的话 直接返回当前节点
                    return parentDefVarMap;
                }
                for (Statement statement : statements) {
                    sonBlockDefVarSet = simplifyNode(statement, sonBlockDefVarSet);
                }
            }

            return parentDefVarMap;
        }else if(node instanceof ForEachStmt){
            ForEachStmt foreachStmt = ((ForEachStmt) node).asForEachStmt();
            Set<DFVarNode> sonBlockDefVarSet = this.copy(parentDefVarMap);

            String label = "for(" + foreachStmt.getVariable() + ":" + foreachStmt.getIterable() + ")";
            int lineNum = foreachStmt.getBegin().isPresent() ? foreachStmt.getBegin().get().line : -1;
            GraphNode cfgNode = allCFGNodesMap.get(label + ":" + lineNum);
            //处理 expression
            StringBuilder sb = new StringBuilder();
            sonBlockDefVarSet = dealSingleRoadStmtDFG(sonBlockDefVarSet,foreachStmt.getVariable(),cfgNode);
            sb.append(cfgNode.getSimplifyCodeStr());
            sb.append(":");
            sonBlockDefVarSet = dealSingleRoadStmtDFG(sonBlockDefVarSet,foreachStmt.getIterable(),cfgNode);
            sb.append(cfgNode.getSimplifyCodeStr());
            cfgNode.setSimplifyCodeStr("for ("+sb.toString()+")");

            if(!foreachStmt.getBody().isBlockStmt()){
                sonBlockDefVarSet = simplifyNode(foreachStmt.getBody(), sonBlockDefVarSet);
            }else {
                NodeList<Statement> statements = foreachStmt.getBody().asBlockStmt().getStatements();
                if (statements.size() == 0) {
                    //表示如果while是空的话 直接返回当前节点
                    return parentDefVarMap;
                }
                for (Statement statement : statements) {
                    sonBlockDefVarSet = simplifyNode(statement, sonBlockDefVarSet);
                }
            }
            return parentDefVarMap;
        }else if(node instanceof SwitchStmt){

            SwitchStmt switchStmt = ((SwitchStmt) node).asSwitchStmt();

            String label = "switch(" + switchStmt.getSelector().toString() + ")";
            int lineNum = switchStmt.getBegin().isPresent() ? switchStmt.getBegin().get().line : -1;
            GraphNode cfgNode = allCFGNodesMap.get(label + ":" + lineNum);
            //处理 expression
            parentDefVarMap = dealSingleRoadStmtDFG(parentDefVarMap,switchStmt.getSelector(),cfgNode);
            cfgNode.setSimplifyCodeStr("switch ("+cfgNode.getSimplifyCodeStr()+")");

            NodeList<SwitchEntry> caseEntries = switchStmt.getEntries(); //case 入口
            if (caseEntries.size() == 0) {
                //表示如果while是空的话 直接返回当前节点
                return parentDefVarMap;
            }
            //先把每一个case节点的表达式搞定
            Set<DFVarNode> copy = this.copy(parentDefVarMap);
            for (int i = 0; i < caseEntries.size(); i++) {
                String caseLabel = caseEntries.get(i).getLabels().getFirst().isPresent() ? "case " + caseEntries.get(i).getLabels().getFirst().get().toString() : "default";
                int caseLineNum = caseEntries.get(i).getBegin().isPresent()?caseEntries.get(i).getBegin().get().line:-1;
                GraphNode caseNode = allCFGNodesMap.get(caseLabel + ":" + caseLineNum);
                if(caseEntries.get(i).getLabels().getFirst().isPresent()){
                    dealSingleRoadStmtDFG(copy,caseEntries.get(i).getLabels().getFirst().get(),caseNode);
                    caseNode.setSimplifyCodeStr("case "+caseNode.getSimplifyCodeStr());
                }else{
                    caseNode.setSimplifyCodeStr("default");
                }

                Set<DFVarNode> sonBlockDefVarSet = this.copy(copy);
                NodeList<Statement> statements = caseEntries.get(i).getStatements(); //一个case下面的所有语句
                /*
                case节点 没有进行数据流分析
                 */
                for (Statement statement : statements) {
                    sonBlockDefVarSet = simplifyNode(statement,sonBlockDefVarSet);
                }
            }
            return parentDefVarMap;
        }else if(node instanceof DoStmt){
            DoStmt doStmt = ((DoStmt) node).asDoStmt();

            Set<DFVarNode> sonBlockDefVarSet = this.copy(parentDefVarMap);

            NodeList<Statement> statements = doStmt.getBody().asBlockStmt().getStatements();
            for (Statement statement : statements) {
                sonBlockDefVarSet = simplifyNode(statement, sonBlockDefVarSet);
            }

            String label = "while (" + doStmt.getCondition().toString() + ")";
            int lineNum = doStmt.getCondition().getBegin().isPresent() ? doStmt.getCondition().getBegin().get().line : -1;
            GraphNode cfgNode = allCFGNodesMap.get(label + ":" + lineNum);

            dealSingleRoadStmtDFG(sonBlockDefVarSet,doStmt.getCondition(),cfgNode);
            cfgNode.setSimplifyCodeStr("while ("+cfgNode.getSimplifyCodeStr()+")");

            return parentDefVarMap;
        }else if (node instanceof BreakStmt) {
            BreakStmt breakStmt = ((BreakStmt) node).asBreakStmt();
            String label = breakStmt.getLabel().isPresent() ? "break " + breakStmt.getLabel().get().toString() : "break";
            int lineNum = breakStmt.getBegin().isPresent()?breakStmt.getBegin().get().line:-1;
            GraphNode cfgNode = allCFGNodesMap.get(label + ":" + lineNum);
            if(breakStmt.getLabel().isPresent()){
                cfgNode.setSimplifyCodeStr("break label");
            }else{
                cfgNode.setSimplifyCodeStr("break");
            }
            return parentDefVarMap;
        }else if (node instanceof ContinueStmt){
            ContinueStmt continueStmt = ((ContinueStmt) node).asContinueStmt();
            String label = continueStmt.getLabel().isPresent() ? "continue " + continueStmt.getLabel().get().toString() : "continue";
            int lineNum = continueStmt.getBegin().isPresent()?continueStmt.getBegin().get().line:-1;
            GraphNode cfgNode = allCFGNodesMap.get(label + ":" + lineNum);
            if(continueStmt.getLabel().isPresent()){
                cfgNode.setSimplifyCodeStr("continue label");
            }else{
                cfgNode.setSimplifyCodeStr("continue");
            }
            return parentDefVarMap;
        } else if(node instanceof LabeledStmt){
            //有的节点直接能明确他的符号的，直接在cfg图上直接设置死他的简化的代码形式
            LabeledStmt labeledStmt = ((LabeledStmt) node).asLabeledStmt();
            simplifyNode(labeledStmt.getStatement(),parentDefVarMap);
        } else if(node instanceof SynchronizedStmt){
            SynchronizedStmt synchronizedStmt = ((SynchronizedStmt) node).asSynchronizedStmt();

            Set<DFVarNode> sonBlockDefVarSet = this.copy(parentDefVarMap);

            String label = "synchronized (" + synchronizedStmt.getExpression() + ")";
            int lineNum = synchronizedStmt.getBegin().isPresent() ? synchronizedStmt.getBegin().get().line : -1;
            GraphNode cfgNode = allCFGNodesMap.get(label + ":" + lineNum);
            // 处理 expression
            sonBlockDefVarSet = dealSingleRoadStmtDFG(sonBlockDefVarSet,synchronizedStmt.getExpression(),cfgNode);

            cfgNode.setSimplifyCodeStr("synchronized (" + cfgNode.getSimplifyCodeStr() + ")");

            BlockStmt body = synchronizedStmt.getBody();
            NodeList<Statement> statements = body.getStatements();
            for (Statement statement : statements) {
                //开始递归创建，每处理一个statement返回一个node，这个node作为下一个state的前驱点
                sonBlockDefVarSet = simplifyNode(statement, sonBlockDefVarSet);
            }
            return parentDefVarMap;
        }else if(node instanceof BlockStmt){
            BlockStmt blockStmt = ((BlockStmt) node).asBlockStmt();
            Set<DFVarNode> sonBlockDefVarSet = this.copy(parentDefVarMap);
            NodeList<Statement> statements = blockStmt.getStatements();
            for (Statement statement : statements) {
                //开始递归创建，每处理一个statement返回一个node，这个node作为下一个state的前驱点
                sonBlockDefVarSet = simplifyNode(statement, sonBlockDefVarSet);
            }
            return parentDefVarMap;
        }else if(node instanceof TryStmt){
            TryStmt tryStmt = ((TryStmt) node).asTryStmt();
            Set<DFVarNode> sonBlockDefVarSet = this.copy(parentDefVarMap);
            String label = tryStmt.getResources().size() == 0 ? "try" : "try(" + StringUtils.join(tryStmt.getResources(), ";") + ")";
            int lineNum = tryStmt.getBegin().isPresent() ? tryStmt.getBegin().get().line : -1;
            GraphNode cfgNode = allCFGNodesMap.get(label + ":" + lineNum);
            //处理ast
            /**
             * 处理try中的表达式
             */
            NodeList<Expression> resources = tryStmt.getResources();
            List<String> resourceStr = new ArrayList<>();
            for(Expression e:resources){
                sonBlockDefVarSet =  dealSingleRoadStmtDFG(sonBlockDefVarSet,e,cfgNode);
                resourceStr.add(cfgNode.getSimplifyCodeStr());
            }
            if(resourceStr.size()>0) {
                cfgNode.setSimplifyCodeStr("try(" + StringUtils.join(resourceStr, ";") + ")");
            }else{
                cfgNode.setSimplifyCodeStr("try");
            }
            BlockStmt tryBlock = tryStmt.getTryBlock();
            NodeList<Statement> statements = tryBlock.getStatements();
            for (Statement statement : statements) {
                sonBlockDefVarSet = simplifyNode(statement, sonBlockDefVarSet);
            }
            Optional<BlockStmt> finallyBlock = tryStmt.getFinallyBlock();
            if (finallyBlock.isPresent()) {
                //开始finllay 模块
                NodeList<Statement> finaBodyStas = finallyBlock.get().getStatements();
                for (Statement statement : finaBodyStas) {
                    //开始递归创建，每处理一个statement返回一个node，这个node作为下一个state的前驱点
                    sonBlockDefVarSet = simplifyNode(statement, sonBlockDefVarSet);
                }
            }
            return parentDefVarMap;
        }else if(node instanceof LocalClassDeclarationStmt){
            LocalClassDeclarationStmt localClassDeclarationStmt = ((LocalClassDeclarationStmt) node).asLocalClassDeclarationStmt();
            ClassOrInterfaceDeclaration classOrInterfaceDeclaration = localClassDeclarationStmt.getClassDeclaration();
            String label = classOrInterfaceDeclaration.getNameAsString();
            int lineNum = classOrInterfaceDeclaration.getBegin().isPresent()?classOrInterfaceDeclaration.getBegin().get().line:-1;
            GraphNode cfgNode = allCFGNodesMap.get(label + ":" + lineNum);
            //class定义做简化
            StringBuilder sb = new StringBuilder("class "+analysisTypeLocation(classOrInterfaceDeclaration.getNameAsString()));
            NodeList<ClassOrInterfaceType> extendedTypes = classOrInterfaceDeclaration.getExtendedTypes();
            List<String> temp = new ArrayList<>();
            if(extendedTypes.size()>0){
                sb.append(" extends ");
                for(ClassOrInterfaceType e:extendedTypes){
                    temp.add(parseTypeToTypeStr(e));
                }
               sb.append(StringUtils.join(temp,","));
            }
            NodeList<ClassOrInterfaceType> implementedTypes = classOrInterfaceDeclaration.getImplementedTypes();
            temp.clear();
            if(implementedTypes.size()>0){
                sb.append(" implements ");
                for(ClassOrInterfaceType e:implementedTypes){
                    temp.add(parseTypeToTypeStr(e));
                }
                sb.append(StringUtils.join(temp,","));
            }
            cfgNode.setSimplifyCodeStr(sb.toString());

            //这个有点特殊的地方就是 必须把这个class里面的方法还按照这个方式遍历生成图结构 所以一个方法可能多个方法体返回
//            List<MethodDeclaration> allmethods = classOrInterfaceDeclaration.findAll(MethodDeclaration.class);
//            for (MethodDeclaration methodDeclaration : allmethods) {
//                simplifyCFGNodeStr(methodDeclaration);
//            }
        }
        return parentDefVarMap;
    }

    /**
     * 分析数据流的时候 就不是在statement级别上了，就需要在expression级别上了
     * @param expression
     */
    private void expressionAnalysis(Expression expression){
        if(expression instanceof ArrayAccessExpr){
            /*
             ArrayAccessExpr 就是获取数组值表达式 比如a = 12; datas[a]
             */
            ArrayAccessExpr arrayAccessExpr = expression.asArrayAccessExpr();
            Expression name = arrayAccessExpr.getName();
            Expression index = arrayAccessExpr.getIndex();
            expressionAnalysis(name);

        }else if(expression instanceof ClassExpr){
            /*
            ClassExpr Object.class 一个类获取class对象
             */
            ClassExpr classExpr = expression.asClassExpr();
        }else if(expression instanceof ArrayCreationExpr){
            /*
            ArrayCreationExpr new int[5] 5 可能变成其他变量 所以可能改变数据流
             */
            ArrayCreationExpr arrayCreationExpr = expression.asArrayCreationExpr();

        }else if(expression instanceof LambdaExpr){
            /*
            lambda (a, b) -> a+b 这是定义函数的方式 所以对于数据流没有任何帮助
             */
            LambdaExpr lambdaExpr = expression.asLambdaExpr();

        }else if(expression instanceof  ConditionalExpr){
            /*
             条件表达式 比如 if(a) 也就是里面有用
             */
            ConditionalExpr conditionalExpr = expression.asConditionalExpr();
        }else if(expression instanceof MethodCallExpr){
            /*
            MethodCallExpr System.out.println("true");
             */
            MethodCallExpr methodCallExpr = expression.asMethodCallExpr();

        }else if(expression instanceof  AnnotationExpr){
            /*
            对数据流没有任何影响 这是方法的注解
             */
            AnnotationExpr annotationExpr = expression.asAnnotationExpr();

        }else if(expression instanceof AssignExpr){
            /*
            赋值表达式   datas[0] = 1;
             */
            AssignExpr assignExpr = expression.asAssignExpr();
            System.out.println(assignExpr+"当前："+AssignExpr.class);
            expressionAnalysis(assignExpr.getTarget());

        }else if(expression instanceof InstanceOfExpr){
            /*
            instance of 对于数据流没有任何影响
             */
            InstanceOfExpr instanceOfExpr = expression.asInstanceOfExpr();
            System.out.println(instanceOfExpr+"当前："+InstanceOfExpr.class);

        }else if(expression instanceof CastExpr){
            /*
            caseExpr  对于数据流没有任何影响 (long)15 long数字
             */
            CastExpr castExpr = expression.asCastExpr();
            System.out.println(castExpr+"当前："+CastExpr.class);

        }else if(expression instanceof NameExpr){
            /*
            变量的名字  switch(a) 里面的a
             */
            NameExpr nameExpr = expression.asNameExpr();
            System.out.println(nameExpr+"当前："+NameExpr.class);

        }else if(expression instanceof ThisExpr){
            /*
            this 字符 对于数据流没有任何影响
             */
            ThisExpr thisExpr = expression.asThisExpr();
            System.out.println(thisExpr+"当前："+ThisExpr.class);

        }else if(expression instanceof EnclosedExpr){
            /*
              括号内的表达式 (1+1)
             */
            EnclosedExpr enclosedExpr = expression.asEnclosedExpr();
            System.out.println(enclosedExpr+"当前："+EnclosedExpr.class);

        }else if(expression instanceof MethodReferenceExpr){
            /*
             方法引用 左边是对象 System.out::println 的println
             */
            MethodReferenceExpr methodReferenceExpr = expression.asMethodReferenceExpr();
            System.out.println(methodReferenceExpr+"当前："+MethodReferenceExpr.class);

        }else if(expression instanceof VariableDeclarationExpr){
            /*
            VariableDeclarator 是 VariableDeclarationExpr 更细的粒度
            int[] datas = { 1, 2, 3, 4 } int a = 10
             */
            VariableDeclarationExpr variableDeclarationExpr = expression.asVariableDeclarationExpr();
            System.out.println(variableDeclarationExpr+"当前："+VariableDeclarationExpr.class);
            NodeList<VariableDeclarator> variables = variableDeclarationExpr.getVariables();
            for(VariableDeclarator var:variables){


            }


        }else if(expression instanceof LiteralExpr){
            /*
            文字表达式 也就是null true 等数值 对于数据流毫无影响
             */
            LiteralExpr literalExpr = expression.asLiteralExpr();
            System.out.println(literalExpr+"当前："+LiteralExpr.class);

        }else if(expression instanceof ObjectCreationExpr){
            /*
            new Exception("yichang") 声明变量的后一半
             */
            ObjectCreationExpr objectCreationExpr = expression.asObjectCreationExpr();
            System.out.println(objectCreationExpr+"当前："+ObjectCreationExpr.class);

        }else if(expression instanceof UnaryExpr){
            /*
            一元运算符 i++
             */
            UnaryExpr unaryExpr = expression.asUnaryExpr();
            System.out.println(unaryExpr+"当前："+UnaryExpr.class);
        }else if(expression instanceof SuperExpr){
            /*
               SuperExpr  super 这个字符 对于数据流影响
             */
            SuperExpr superExpr = expression.asSuperExpr();
            System.out.println(superExpr+"当前："+SuperExpr.class);

        }else if(expression instanceof BinaryExpr){
            /*
            二元操作符表达式 比如if 条件中 a==10
             */
            BinaryExpr binaryExpr = expression.asBinaryExpr();
            System.out.println(binaryExpr+"当前："+BinaryExpr.class);

        }else if(expression instanceof TypeExpr){
            /*
            方法引用 World::greet 的world 就是类型 类名字
             */
            TypeExpr typeExpr = expression.asTypeExpr();
            System.out.println(typeExpr+"当前："+TypeExpr.class);

        }else if(expression instanceof ArrayInitializerExpr){
            /*
            new int[][] {{1, 1}, {2, 2}}
             */
            ArrayInitializerExpr arrayInitializerExpr = expression.asArrayInitializerExpr();
            System.out.println(arrayInitializerExpr+"当前："+ArrayInitializerExpr.class);

        }else if(expression instanceof FieldAccessExpr){
            /*
            对象获取属性 FieldAccessExpr person.name
             */
            FieldAccessExpr fieldAccessExpr = expression.asFieldAccessExpr();
            System.out.println(fieldAccessExpr+"当前："+FieldAccessExpr.class);
        }
    }

    /**
     * 解析表达式得到这个表达式所使用的变量，定义的变量直接通过创建来的 List<HashMap<String,GraphNode>> parentDefVarMap
     * 存储起来，所以只需要返回这个expression 所用过的变量的信息
     */
    private String analysisExprForVar(Expression expression,Set<DFVarNode> parentDefVarMap,GraphNode node){
        if(expression instanceof ArrayAccessExpr){
            /*
             ArrayAccessExpr 就是获取数组值表达式 比如datas[a]
             */
            ArrayAccessExpr arrayAccessExpr = expression.asArrayAccessExpr();
            Expression name = arrayAccessExpr.getName();
            Expression index = arrayAccessExpr.getIndex();
            arrayAccessExpr.setName(new NameExpr(analysisExprForVar(name,parentDefVarMap,node)));
            arrayAccessExpr.setIndex(new NameExpr( analysisExprForVar(index,parentDefVarMap,node)));
            return arrayAccessExpr.toString();
        }else if(expression instanceof ClassExpr){
            /*
            ClassExpr Object.class 一个类获取class对象
             */
            ClassExpr classExpr = expression.asClassExpr();
            classExpr.setType(replaceTypePointToF88F(parseTypeToTypeStr(classExpr.getType())));
            return  classExpr.toString();
        }else if(expression instanceof ArrayCreationExpr){
            /*
            ArrayCreationExpr new int[5]{ 1,2,3} 5 可能变成其他变量 所以可能改变数据流
             */
            ArrayCreationExpr arrayCreationExpr = expression.asArrayCreationExpr();
            //设置类型
            arrayCreationExpr.setElementType(replaceTypePointToF88F(parseTypeToTypeStr(arrayCreationExpr.getElementType())));
            //先把数组的level的样式改变了
            NodeList<ArrayCreationLevel> levels = arrayCreationExpr.getLevels();
            for(ArrayCreationLevel a:levels){
                //把数组创建中维度是变量的节点信息记住！
                if(a.getDimension().isPresent()){
                   a.setDimension(new NameExpr(analysisExprForVar(a.getDimension().get(),parentDefVarMap,node)));
                }
            }
            //修改数值
            if(arrayCreationExpr.getInitializer().isPresent()){
                ArrayInitializerExpr arrayInitializerExpr = arrayCreationExpr.getInitializer().get();
                NodeList<Expression> values = arrayInitializerExpr.getValues();
                NodeList<Expression> newValues = new NodeList<>();
                for(Expression expression1:values){
                    newValues.add(new NameExpr(analysisExprForVar(expression1,parentDefVarMap,node)));
                }
                arrayInitializerExpr.setValues(newValues);
            }
            return arrayCreationExpr.toString();

        }else if(expression instanceof LambdaExpr){
            /*
            lambda (a, b) -> a+b 这是定义函数的方式 所以对于数据流没有任何帮助
             */
            LambdaExpr lambdaExpr = expression.asLambdaExpr();
            return lambdaExpr.toString();

        }else if(expression instanceof  ConditionalExpr){
            /*
             条件表达式 比如 if(a) 也就是里面有用 ifstmt中的if 包含在这个expr里面
             */
            ConditionalExpr conditionalExpr = expression.asConditionalExpr();
            conditionalExpr.setCondition(new NameExpr(analysisExprForVar(conditionalExpr.getCondition(),parentDefVarMap,node)));
            conditionalExpr.setThenExpr(new NameExpr(analysisExprForVar(conditionalExpr.getThenExpr(),parentDefVarMap,node)));
            conditionalExpr.setElseExpr(new NameExpr(analysisExprForVar(conditionalExpr.getElseExpr(),parentDefVarMap,node)));
            return conditionalExpr.toString();
        }else if(expression instanceof MethodCallExpr){
            /*
            MethodCallExpr System.out.println("true");
             */
            MethodCallExpr methodCallExpr = expression.asMethodCallExpr();
            //这个是得到方法的调用变量
            if(methodCallExpr.getScope().isPresent()){
                methodCallExpr.setScope(new NameExpr(analysisExprForVar(methodCallExpr.getScope().get(),parentDefVarMap,node)));
            }
            //继续拿到方法的参数变量名字
            NodeList<Expression> arguments = methodCallExpr.getArguments();
            NodeList<Expression> newArguments = new NodeList<>();
            for(Expression expression1:arguments){
               newArguments.add(new NameExpr(analysisExprForVar(expression1,parentDefVarMap,node)));
            }
            methodCallExpr.setArguments(newArguments);
            return methodCallExpr.toString();
        }else if(expression instanceof  AnnotationExpr){
            /*
            对数据流没有任何影响 这是方法的注解
             */
            AnnotationExpr annotationExpr = expression.asAnnotationExpr();
            return annotationExpr.toString();
        }else if(expression instanceof AssignExpr){
            /*
            赋值表达式   datas[0] = 1; 不会影响任何简化操作
             */
            AssignExpr assignExpr = expression.asAssignExpr();
            assignExpr.setTarget(new NameExpr(analysisExprForVar(assignExpr.getTarget(),parentDefVarMap,node)));
            assignExpr.setValue(new NameExpr(analysisExprForVar(assignExpr.getValue(),parentDefVarMap,node)));
            return assignExpr.toString();
        }else if(expression instanceof InstanceOfExpr){
            /*
            instance of 对于数据流没有任何影响
             */
            InstanceOfExpr instanceOfExpr = expression.asInstanceOfExpr();
            instanceOfExpr.setExpression(new NameExpr(analysisExprForVar(instanceOfExpr.getExpression(),parentDefVarMap,node)));
            instanceOfExpr.setType(replaceTypePointToF88F(parseTypeToTypeStr(instanceOfExpr.getType())));
            return instanceOfExpr.toString();
        }else if(expression instanceof CastExpr){
            /*
            caseExpr  对于数据流没有任何影响 (long)15 long数字
             */
            CastExpr castExpr = expression.asCastExpr();
            castExpr.setExpression(new NameExpr(analysisExprForVar(castExpr.getExpression(),parentDefVarMap,node)));
            castExpr.setType(replaceTypePointToF88F(parseTypeToTypeStr(castExpr.getType())));
            return castExpr.toString();
        }else if(expression instanceof NameExpr){
            /*
            变量的名字  switch(a) 里面的a
             */
            NameExpr nameExpr = expression.asNameExpr();
            //默认解析到NameExpr的时候 都是来源于使用的，对于Assign单独处理
            Iterator<DFVarNode> iterator = parentDefVarMap.iterator();
            while(iterator.hasNext()){
                DFVarNode next = iterator.next();
                if(next.getVarName().equals(nameExpr.getNameAsString())){
                    nameExpr.setName(next.getVarType()); //变量名字用类型替代
                    break;
                }
            }
            return nameExpr.toString();
        }else if(expression instanceof ThisExpr){
            /*
            this 字符 对于数据流没有任何影响
             */
            ThisExpr thisExpr = expression.asThisExpr();
            if(thisExpr.getTypeName().isPresent()) {
                thisExpr.setTypeName(new Name(analysisExprForVar(thisExpr.asClassExpr(), parentDefVarMap, node)));
            }
            return thisExpr.toString();
        }else if(expression instanceof EnclosedExpr){
            /*
              括号内的表达式 (1+1)
             */
            EnclosedExpr enclosedExpr = expression.asEnclosedExpr();
            enclosedExpr.setInner(new NameExpr(analysisExprForVar(enclosedExpr.getInner(),parentDefVarMap,node)));
            return  enclosedExpr.toString();

        }else if(expression instanceof MethodReferenceExpr){
            /*
             方法引用 左边是对象 System.out::println 的println
             */
            MethodReferenceExpr methodReferenceExpr = expression.asMethodReferenceExpr();
            return  analysisExprForVar(methodReferenceExpr.getScope(),parentDefVarMap,node)+"."+methodReferenceExpr.getIdentifier();
        }else if(expression instanceof VariableDeclarationExpr){
            /*
            VariableDeclarator 是 VariableDeclarationExpr 更细的粒度
            int[] datas = { 1, 2, 3, 4 } int a = 10
             */
            //只有这个节点才是变量定义节点 所以需要记录当前节点的变量定义信息！
            VariableDeclarationExpr variableDeclarationExpr = expression.asVariableDeclarationExpr();
            NodeList<VariableDeclarator> variables = variableDeclarationExpr.getVariables();
            for(VariableDeclarator var:variables){
                //先把之前的相同名字变量定义去除
                parentDefVarMap.removeIf(dfVarNode ->{
                   if(dfVarNode.getVarName().equals(var.getNameAsString())){
                       return true;
                   }else{
                       return false;
                   }
                });
                //数据流更新
                parentDefVarMap.add(new DFVarNode(var.getNameAsString(),node,replaceTypePointToF88F(parseTypeToTypeStr(var.getType()))));
                var.setName("var");//变量名统一用var处理
                var.setType(replaceTypePointToF88F(parseTypeToTypeStr(var.getType())));//类型统一用具体的类型
                if(var.getInitializer().isPresent()){
                   var.setInitializer(analysisExprForVar(var.getInitializer().get(),parentDefVarMap,node));
                }
            }

            return variableDeclarationExpr.toString();
        }else if(expression instanceof LiteralExpr){
            /*
            文字表达式 也就是null true 等数值 对于数据流毫无影响
             */
            LiteralExpr literalExpr = expression.asLiteralExpr();
            if(literalExpr instanceof NullLiteralExpr){
                 return "NullLiteral";
            }else if(literalExpr instanceof BooleanLiteralExpr){
                 return  "BooleanLiteral";
            }else if(literalExpr instanceof CharLiteralExpr){
                return "CharLiteral";
            }else if(literalExpr instanceof DoubleLiteralExpr){
                return "DoubleLiteral";
            }else if(literalExpr instanceof LongLiteralExpr){
                return "LongLiteral";
            }else if(literalExpr instanceof StringLiteralExpr){
                return "StringLiteral";
            }else if(literalExpr instanceof IntegerLiteralExpr){
                return "IntegerLiteral";
            }
        }else if(expression instanceof ObjectCreationExpr){
            /*
            new Exception("yichang") 声明变量的后一半
             */
            ObjectCreationExpr objectCreationExpr = expression.asObjectCreationExpr();
            if(objectCreationExpr.getScope().isPresent()){
                objectCreationExpr.setScope(new NameExpr(analysisExprForVar(objectCreationExpr.getScope().get(),parentDefVarMap,node)));
            }

            objectCreationExpr.setType(replaceTypePointToF88F(parseTypeToTypeStr(objectCreationExpr.getType())));

            NodeList<Expression> arguments = objectCreationExpr.getArguments();
            NodeList<Expression> newArguments = new NodeList<>();
            for(Expression expression1:arguments){
                newArguments.add(new NameExpr(analysisExprForVar(expression1,parentDefVarMap,node)));
            }
            objectCreationExpr.setArguments(newArguments);
            //对象创建的方法体都去除
            objectCreationExpr.setAnonymousClassBody(new NodeList<>());
            return objectCreationExpr.toString().replaceAll("\\{","").replaceAll("\\}","");
        }else if(expression instanceof UnaryExpr){
            /*
            一元运算符 i++
             */
            UnaryExpr unaryExpr = expression.asUnaryExpr();
            unaryExpr.setExpression(new NameExpr(analysisExprForVar(unaryExpr.getExpression(),parentDefVarMap,node)));

            return unaryExpr.toString();
        }else if(expression instanceof SuperExpr){
            /*
               SuperExpr  super 这个字符 对于数据流影响
             */
            SuperExpr superExpr = expression.asSuperExpr();
            if(superExpr.getTypeName().isPresent()) {
                superExpr.setTypeName(new Name(analysisExprForVar(superExpr.asClassExpr(), parentDefVarMap, node)));
            }
            return superExpr.toString();
        }else if(expression instanceof BinaryExpr){
            /*
            二元操作符表达式 比如if 条件中 a==10
             */
            BinaryExpr binaryExpr = expression.asBinaryExpr();
            binaryExpr.setLeft(new NameExpr(analysisExprForVar(binaryExpr.getLeft(),parentDefVarMap,node)));
            binaryExpr.setRight(new NameExpr(analysisExprForVar(binaryExpr.getRight(),parentDefVarMap,node)));
            return binaryExpr.toString();
        }else if(expression instanceof TypeExpr){
            /*
            方法引用 World::greet 的world 就是类型 类名字
             */
            TypeExpr typeExpr = expression.asTypeExpr();
            typeExpr.setType(replaceTypePointToF88F(parseTypeToTypeStr(typeExpr.getType())));
            return typeExpr.toString();
        }else if(expression instanceof ArrayInitializerExpr){
            /*
            new int[][] {{1, 1}, {2, 2}}
             */
            ArrayInitializerExpr arrayInitializerExpr = expression.asArrayInitializerExpr();
            NodeList<Expression> values = arrayInitializerExpr.getValues();
            NodeList<Expression> newValues = new NodeList<>();
            for(Expression expression1:values){
                newValues.add(new NameExpr(analysisExprForVar(expression1,parentDefVarMap,node)));
            }
            arrayInitializerExpr.setValues(newValues);
            return arrayInitializerExpr.toString();

        }else if(expression instanceof FieldAccessExpr){
            /*
            对象获取属性 FieldAccessExpr person.name
             */
            FieldAccessExpr fieldAccessExpr = expression.asFieldAccessExpr();
            fieldAccessExpr.setScope(new NameExpr(analysisExprForVar(fieldAccessExpr.getScope(),parentDefVarMap,node)));
            fieldAccessExpr.setTypeArguments(new NodeList<>());
            return fieldAccessExpr.toString();
        }
        return expression.toString();
    }

    /**
     * 代码中很多单线路的stmt 这些stmt的更新方式就先先把expression的引用的变量连上边，然后就是更新定义变量的信息
     * @param expression 需要分析的源码表达式
     * @param node 被分析源码所在的node的信息
     * @return 更新之后的定义的变量信息
     */
    private Set<DFVarNode> dealSingleRoadStmtDFG(Set<DFVarNode> parentDefVarMap,Expression expression,GraphNode node){
        //处理 expression
        String s = analysisExprForVar(expression, parentDefVarMap,node);

        //更新节点的表示 list中只有第一个存储具体的表示
        node.setSimplifyCodeStr(restoreTypeF88FToPoint(s));

        return parentDefVarMap;
    }
    /**
     * 每一个局部作用域 都有自己的作用范围 超过这个范围，在该范围内定义的变量都会被消除
     * @param in 父范围定义的变量信息
     * @return  返回只是copy这个父范围的变量信息的一样的数据结构对象
     */
    private Set<DFVarNode> copy( Set<DFVarNode> in){
        return new HashSet<>(in);
    }

    /**
     * 解析java parser的type 到具体的java类型package.class
     * @return
     */
    private String parseTypeToTypeStr(Type type){
        if(type.isUnknownType()){
            // lambda 中用到的未知参数
            UnknownType unknownType = type.asUnknownType();
            return "unknownType";
        }else if(type.isUnionType()){
            // catch中的联合类型
            List<String> typeStr = new ArrayList<>();
            UnionType unionType = type.asUnionType();
            NodeList<ReferenceType> elements = unionType.getElements();
            for(Type referenceType:elements){
                typeStr.add(parseTypeToTypeStr(referenceType));
            }
            return StringUtils.join(typeStr,";");
        }else if(type.isVarType()){
            VarType varType = type.asVarType();
            return varType.asString();
        }else if(type.isClassOrInterfaceType()){
            ClassOrInterfaceType classOrInterfaceType = type.asClassOrInterfaceType();
            return analysisTypeLocation(classOrInterfaceType.getNameAsString());
        }else if(type.isArrayType()){
            //array type 就是一个[]
            ArrayType arrayType = type.asArrayType();
            String s = parseTypeToTypeStr(arrayType.getComponentType());
            return s+"[]";
        }else if(type.isTypeParameter()){
            //这个是泛型中用到的类型 这个目前节点没有关注
            TypeParameter typeParameter = type.asTypeParameter();
            return analysisTypeLocation(typeParameter.getNameAsString());
        }else if(type.isPrimitiveType()){
            //jdk自带的类型
            PrimitiveType primitiveType = type.asPrimitiveType();
            return "java.lang."+primitiveType.getType().asString();
        }else if(type.isWildcardType()){
            WildcardType wildcardType = type.asWildcardType();
            StringBuilder sb = new StringBuilder();
            if(wildcardType.getExtendedType().isPresent()){
                sb.append(parseTypeToTypeStr(wildcardType.getExtendedType().get()));
                sb.append(";");
            }
            if(wildcardType.getSuperType().isPresent()){
                sb.append(parseTypeToTypeStr(wildcardType.getSuperType().get()));
            }
            return sb.toString();
        }else if(type.isVoidType()){
            VoidType voidType = type.asVoidType();
            return voidType.asString();
        }else if(type.isIntersectionType()){
            IntersectionType intersectionType = type.asIntersectionType();
            List<String> typeStr = new ArrayList<>();
            for(Type t:intersectionType.getElements()){
                typeStr.add(parseTypeToTypeStr(t));
            }
            return StringUtils.join(typeStr,";");
        }
        return "";
    }

    /**
     * 代码里面的type都是简单名，没有加上绝对路径 所以需要根据简单名字去判断import 和 package 还是java.lang
     * 有个问题就是当前所有类型在设置到exprssion之前需要把点全部变成F88F
     * @param typeName 代码中的名字
     */
    private String analysisTypeLocation(String typeName){
        //首先判断当前typeName 在不在import中
        for(String str:imports){
            if(typeName.equals(str.substring(str.lastIndexOf(".")+1))){
                return str;
            }
        }
        //如果不是import 就看是不是package里面新定义的类型
        for(String packType:packageToAllType){
            if(typeName.equals(packType.substring(packType.lastIndexOf(".")+1))){
                return packType;
            }
        }
        //既不是import 又不是package 那就是java.lang
        return "java.lang."+typeName;
    }

    /**
     * 替换类型中的点到F88F java.lang==> javaF88Flang
     * @return
     */
    private String replaceTypePointToF88F(String containPointStr){
        return containPointStr.replaceAll("\\.","F88F").replaceAll(";","F99F");
    }

    /**
     * 替换类型中的F88F到点 javaF88Flang==> java.lang
     * @return
     */
    private String restoreTypeF88FToPoint(String containPointStr){
        return containPointStr.replaceAll("F88F","\\.").replaceAll("F99F","\\|");
    }

}
